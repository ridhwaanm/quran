package com.ridhwaanmayet.quran.ui

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.ridhwaanmayet.quran.model.AyahPosition
import com.ridhwaanmayet.quran.model.Bookmark
import com.ridhwaanmayet.quran.model.QuranRepository
import com.ridhwaanmayet.quran.utils.CoordinateMapper
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced AyahInteractionHandler that supports multiple display modes:
 * - Portrait mode
 * - Landscape single page mode
 * - Landscape dual page mode
 *
 * Uses the enhanced CoordinateMapper for percentage-based coordinate mapping.
 */
class AyahInteractionHandler(
        private val context: Context,
        private val quranRepository: QuranRepository,
        private val coordinateMapper: CoordinateMapper = CoordinateMapper()
) : Parcelable {
    // Scope for long press detection
    private val longPressScope = MainScope()

    // Map to store bookmark safe zones
    private val bookmarkSafeZones = mutableMapOf<String, List<android.graphics.Rect>>()

    // Define gesture states using a state machine approach
    enum class GestureState {
        IDLE, // No gesture in progress
        TOUCH_DOWN, // Initial touch detected
        POTENTIAL_TAP, // Touch detected, might become a tap
        DRAGGING, // Significant movement detected, likely a swipe
        LONG_PRESS_DETECTED, // Long press was detected and handled
        COMPLETED // Gesture cycle complete
    }

    // Thread-safe state holder
    private val currentState = AtomicReference(GestureState.IDLE)

    constructor(parcel: Parcel) : this(
        TODO("context"),
        TODO("quranRepository"),
        TODO("coordinateMapper")
    ) {
    }

    // Add TAG for logging
    companion object CREATOR : Parcelable.Creator<AyahInteractionHandler> {
        private const val TAG = "AyahInteractionHandler"

        // Minimum distance for a swipe to be registered
        const val MIN_SWIPE_DISTANCE = 50f

        // Maximum distance for a tap to be registered
        const val TAP_THRESHOLD = 20f

        // Minimum duration for a long press in milliseconds
        const val LONG_PRESS_DURATION = 500L

        // Maximum vertical movement allowed for horizontal swipe
        const val MAX_VERTICAL_RATIO = 0.5f

        // Threshold for fling velocity in pixels per millisecond
        const val FLING_VELOCITY_THRESHOLD = 0.5f

        // Parcelable.Creator implementation
        override fun createFromParcel(parcel: Parcel): AyahInteractionHandler {
            return AyahInteractionHandler(parcel)
        }

        override fun newArray(size: Int): Array<AyahInteractionHandler?> {
            return arrayOfNulls(size)
        }
    }

    /** Initialize ayah position data by extracting from HTML files */
    suspend fun initializeAyahPositions(
        forceUpdate: Boolean = false,
        progressCallback: ((Int, Int) -> Unit)? = null
    ) =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Initializing ayah positions, forceUpdate=$forceUpdate")

            // Check if we already have positions data and skip if not forcing update
            val existingPositions = quranRepository.getAyahPositions()
            Log.d(TAG, "Existing positions: ${existingPositions.size}")

            if (!forceUpdate && existingPositions.isNotEmpty()) {
                Log.d(TAG, "Using existing positions, skipping initialization")
                return@withContext
            }

            val metadata = quranRepository.loadQuranMetadata()
            val totalPages = metadata.totalPages
            Log.d(TAG, "Starting extraction for $totalPages pages")

            // Extract positions from HTML files
            val positions =
                coordinateMapper.extractAyahPositions(context, totalPages, progressCallback)
            Log.d(TAG, "Extracted ${positions.size} ayah positions")

            // Save to repository
            quranRepository.addAyahPositions(positions)
            Log.d(TAG, "Saved positions to repository")
        }

    /**
     * Find which ayah (if any) was tapped based on coordinates
     * @param page Current page number
     * @param x X coordinate of touch point
     * @param y Y coordinate of touch point
     * @param containerSize Size of the container (screen or partial screen)
     * @param pageImageSize Actual size of the page image
     * @param pageOffset Offset of the page image within its container
     * @return The ayah reference as a string (e.g. "2:255"), or null if no ayah was found
     */
    suspend fun findAyahAtPosition(
        page: Int,
        x: Float,
        y: Float,
        containerSize: Size,
        pageImageSize: Size? = null,
        pageOffset: Offset = Offset.Zero
    ): String? {
        // Use the provided page image size or fall back to container size
        val sizeToUse = pageImageSize ?: containerSize

        Log.d(TAG, "Finding ayah at position: page=$page, x=$x, y=$y")
        Log.d(TAG, "Container size: $containerSize")
        Log.d(TAG, "Page image size (if available): $pageImageSize")
        Log.d(TAG, "Size used for mapping: $sizeToUse")
        Log.d(TAG, "Page offset: $pageOffset")

        val result = coordinateMapper.findAyahAt(context, page, x, y, sizeToUse, pageOffset)
        Log.d(TAG, "Ayah at position result: $result")
        return result
    }

    /** Store an ayah position in the repository for future reference */
    private suspend fun saveAyahPosition(ayahRef: String, page: Int) =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Saving ayah position: ayahRef=$ayahRef, page=$page")

            // Parse the ayah reference
            val parts = ayahRef.split(":")
            if (parts.size == 2) {
                val surahNumber = parts[0].toIntOrNull()
                val ayahNumber = parts[1].toIntOrNull()

                Log.d(TAG, "Parsed reference: surah=$surahNumber, ayah=$ayahNumber")

                if (surahNumber != null && ayahNumber != null) {
                    val position =
                        AyahPosition(
                            surahNumber = surahNumber,
                            ayahNumber = ayahNumber,
                            startPage = page
                        )
                    quranRepository.addAyahPosition(position)
                    Log.d(TAG, "Saved position to repository")
                } else {
                    Log.e(TAG, "Failed to parse surah or ayah number from $ayahRef")
                }
            } else {
                Log.e(TAG, "Invalid ayah reference format: $ayahRef")
            }
        }

    /** Navigate to a specific ayah */
    suspend fun navigateToAyah(ayahRef: String): Int? =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Navigating to ayah: $ayahRef")

            // First try to get from repository
            var startPage = quranRepository.getAyahStartPageByRef(ayahRef)
            if (startPage != null) {
                Log.d(TAG, "Found page in repository: $startPage")
                return@withContext startPage
            }

            // If not found in repository, try to compute it
            val parts = ayahRef.split(":")
            if (parts.size != 2) {
                Log.e(TAG, "Invalid ayah reference format: $ayahRef")
                return@withContext null
            }

            val surahNumber = parts[0].toIntOrNull() ?: return@withContext null
            val verseNumber = parts[1].toIntOrNull() ?: return@withContext null
            Log.d(TAG, "Parsed reference: surah=$surahNumber, verse=$verseNumber")

            // Get the surah info to know its page range
            val surah = quranRepository.getSurahByNumber(surahNumber)
            if (surah == null) {
                Log.e(TAG, "Surah not found: $surahNumber")
                return@withContext null
            }

            // Get the next surah to determine the end page of the current surah
            val metadata = quranRepository.loadQuranMetadata()
            val nextSurah = metadata.surahs.find { it.number == surahNumber + 1 }
            val endPage = nextSurah?.startPage?.minus(1) ?: metadata.totalPages
            Log.d(TAG, "Searching in page range: ${surah.startPage} to $endPage")

            // Load ayah areas for all relevant pages
            val pagesAreas =
                coordinateMapper.loadAyahAreasForPages(context, surah.startPage, endPage)
            Log.d(TAG, "Loaded areas for ${pagesAreas.size} pages")

            // Scan through pages to find the ayah
            for (pageEntry in pagesAreas) {
                val page = pageEntry.key
                val areas = pageEntry.value
                Log.d(TAG, "Page $page has ${areas.size} areas")

                if (areas.any { it.surahNumber == surahNumber && it.verseNumber == verseNumber }
                ) {
                    // Found the ayah on this page
                    Log.d(TAG, "Found ayah on page $page")

                    // Save for future use
                    saveAyahPosition(ayahRef, page)

                    return@withContext page
                }
            }

            // Fallback to the start of the surah if ayah not found
            Log.d(TAG, "Ayah not found, falling back to surah start page: ${surah.startPage}")
            return@withContext surah.startPage
        }

    // Clean up coroutine scope when no longer needed
    fun cleanup() {
        Log.d(TAG, "Cleaning up resources")
        longPressScope.cancel()
        coordinateMapper.clearCache()
    }

    /**
     * Get coordinates for a specific ayah on a page Enhanced to support multiple display modes
     *
     * @param context Android context
     * @param pageNumber Page number
     * @param ayahRef Ayah reference (e.g. "2:30")
     * @param containerSize Size of the container
     * @param pageImageSize Actual size of the page image
     * @param pageOffset Offset of the page image within its container
     * @return List of coordinate rectangles for the ayah
     */
    suspend fun getAyahRects(
        context: Context,
        pageNumber: Int,
        ayahRef: String,
        containerSize: Size,
        pageImageSize: Size? = null,
        pageOffset: Offset = Offset.Zero
    ): List<android.graphics.Rect> {
        val sizeToUse = pageImageSize ?: containerSize
        return coordinateMapper.getAyahRects(context, pageNumber, ayahRef, sizeToUse, pageOffset)
    }

    /**
     * Get the center point for a specific ayah on a page Useful for placing bookmark indicators
     *
     * @param context Android context
     * @param pageNumber Page number
     * @param ayahRef Ayah reference (e.g. "2:30")
     * @param containerSize Size of the container
     * @param pageImageSize Actual size of the page image
     * @param pageOffset Offset of the page image within its container
     * @return Center point as an Offset, or null if ayah not found
     */
    suspend fun getAyahCenterPoint(
        context: Context,
        pageNumber: Int,
        ayahRef: String,
        containerSize: Size,
        pageImageSize: Size? = null,
        pageOffset: Offset = Offset.Zero
    ): Offset? {
        val sizeToUse = pageImageSize ?: containerSize
        return coordinateMapper.getAyahCenterPoint(
            context,
            pageNumber,
            ayahRef,
            sizeToUse,
            pageOffset
        )
    }

    // Compute and store the hit areas for bookmarks
    private suspend fun updateBookmarkSafeZones(
        currentPage: Int,
        bookmarks: List<Bookmark>,
        displaySize: Size,
        imageSize: Size
    ) {
        bookmarkSafeZones.clear()

        val ayahBookmarks = bookmarks.filter { it.page == currentPage && it.ayahRef != null }
        for (bookmark in ayahBookmarks) {
            bookmark.ayahRef?.let { ayahRef ->
                // Get rects for this ayah
                val rects = getAyahRects(context, currentPage, ayahRef, displaySize, imageSize)
                if (rects.isNotEmpty()) {
                    // Store with some padding to create a "safe zone" around the bookmark
                    val paddedRects =
                        rects.map { rect ->
                            val paddedRect = android.graphics.Rect(rect)
                            paddedRect.inset(-40, -40) // Add 40px padding all around
                            paddedRect
                        }
                    bookmarkSafeZones[ayahRef] = paddedRects
                }
            }
        }

        Log.d(TAG, "Updated bookmark safe zones with ${bookmarkSafeZones.size} bookmarks")
    }

    // Check if a touch point is within a bookmark safe zone
    private fun isTouchingBookmark(touchPoint: Offset): Boolean {
        for ((_, rects) in bookmarkSafeZones) {
            for (rect in rects) {
                if (rect.contains(touchPoint.x.toInt(), touchPoint.y.toInt())) {
                    return true
                }
            }
        }
        return false
    }

    /** Creates a Modifier that handles all touch interactions for the Quran page */
    override fun writeToParcel(parcel: Parcel, flags: Int) {

    }

    override fun describeContents(): Int {
        return 0
    }


    /** Creates a Modifier that handles all touch interactions for the Quran page */
    fun handleAyahInteractions(
        modifier: Modifier = Modifier,
        currentPage: Int,
        displaySize: Size,
        onTap: () -> Unit,
        onSwipeLeft: () -> Unit,
        onSwipeRight: () -> Unit,
        onAyahLongPress: (String) -> Unit,
        actualImageSize: Size? = null,
        pageOffset: Offset = Offset.Zero,
        pageBookmarks: List<Bookmark> = emptyList()
    ): Modifier {
        // Combine the pointer input for swipes, taps, and long presses with the detection for flings
        return modifier
            .pointerInput(
                currentPage,
                displaySize,
                actualImageSize,
                pageOffset,
                pageBookmarks
            ) {
                Log.d(
                    TAG,
                    "Setting up pointer input handler for page $currentPage, display size: $displaySize"
                )

                if (actualImageSize != null) {
                    Log.d(TAG, "Actual rendered image size: $actualImageSize")
                }

                Log.d(TAG, "Page offset: $pageOffset")

                // Update bookmark safe zones - cache the hit areas for bookmarks
                updateBookmarkSafeZones(
                    currentPage,
                    pageBookmarks,
                    displaySize,
                    actualImageSize ?: displaySize
                )

                awaitEachGesture {
                    // Reset state machine for each new gesture
                    currentState.set(GestureState.IDLE)
                    Log.d(TAG, "Starting new gesture cycle, state=${currentState.get()}")

                    try {
                        // Handle initial touch down
                        val down = awaitFirstDown(requireUnconsumed = false)
                        Log.d(TAG, "Pointer down at ${down.position}")

                        // Check if we're tapping near a bookmark indicator
                        val touchingBookmark = isTouchingBookmark(down.position)
                        if (touchingBookmark) {
                            Log.d(TAG, "Touch is in bookmark area, skipping long press detection")
                            // We let the pointer event continue but won't start long press detection
                            currentState.set(GestureState.POTENTIAL_TAP)
                            // Wait for pointer up
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull()
                                if (change == null || change.changedToUp()) break
                            }
                            // Trigger tap anyway to ensure navigation controls work
                            onTap()
                            return@awaitEachGesture
                        }

                        // Move to TOUCH_DOWN state
                        currentState.set(GestureState.TOUCH_DOWN)

                        // Initial position
                        val startPos = down.position
                        var currentPos = startPos
                        var finalPos = startPos

                        // Track velocity for fling detection
                        var lastUpdateTime = System.currentTimeMillis()
                        var velocityX = 0f

                        // Create a separate job for long press detection
                        val longPressJob =
                            longPressScope.launch {
                                try {
                                    Log.d(TAG, "Starting long press detection")
                                    delay(LONG_PRESS_DURATION)

                                    // Only proceed if we're still in a valid state for long press
                                    val state = currentState.get()
                                    if (state == GestureState.TOUCH_DOWN ||
                                        state == GestureState.POTENTIAL_TAP
                                    ) {
                                        Log.d(
                                            TAG,
                                            "Long press timeout reached, current state=$state"
                                        )

                                        // Try to atomically transition to LONG_PRESS_DETECTED
                                        if (currentState.compareAndSet(
                                                state,
                                                GestureState.LONG_PRESS_DETECTED
                                            )
                                        ) {
                                            Log.d(
                                                TAG,
                                                "Long press detected at $startPos, transitioning to LONG_PRESS_DETECTED"
                                            )

                                            // Find which ayah was pressed
                                            val ayahRef =
                                                findAyahAtPosition(
                                                    currentPage,
                                                    startPos.x,
                                                    startPos.y,
                                                    displaySize,
                                                    actualImageSize,
                                                    pageOffset
                                                )

                                            // Trigger long press callback and save ayah position
                                            if (ayahRef != null) {
                                                Log.d(TAG, "Long press on ayah: $ayahRef")
                                                // Save the ayah position to the repository
                                                saveAyahPosition(ayahRef, currentPage)
                                                // Trigger callback
                                                onAyahLongPress(ayahRef)
                                            } else {
                                                Log.d(
                                                    TAG,
                                                    "Long press did not find an ayah at position"
                                                )
                                            }
                                        } else {
                                            Log.d(
                                                TAG,
                                                "State changed during long press detection, aborting long press"
                                            )
                                        }
                                    } else {
                                        Log.d(
                                            TAG,
                                            "State changed to $state, aborting long press"
                                        )
                                    }
                                } catch (e: CancellationException) {
                                    Log.d(TAG, "Long press detection cancelled")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in long press detection: ${e.message}")
                                }
                            }

                        // Move to the POTENTIAL_TAP state after starting long press job
                        if (currentState.compareAndSet(
                                GestureState.TOUCH_DOWN,
                                GestureState.POTENTIAL_TAP
                            )
                        ) {
                            Log.d(TAG, "Transitioning to POTENTIAL_TAP state")
                        }

                        // Track movement until pointer is up
                        var change: PointerInputChange? = null
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                change = event.changes.firstOrNull()

                                // No pointer found, break the loop
                                if (change == null) {
                                    Log.d(TAG, "No pointer found, breaking tracking loop")
                                    break
                                }

                                val prevPos = currentPos
                                currentPos = change.position

                                // Calculate velocity
                                val now = System.currentTimeMillis()
                                val timeDelta = now - lastUpdateTime
                                if (timeDelta > 0) {
                                    val positionDelta = currentPos.x - prevPos.x
                                    val instantVelocity = positionDelta / timeDelta
                                    // Apply some smoothing
                                    velocityX = 0.7f * velocityX + 0.3f * instantVelocity
                                    lastUpdateTime = now
                                }

                                // Check if we've moved beyond the tap threshold
                                val distance = (currentPos - startPos).getDistance()
                                val currentGestureState = currentState.get()

                                if (distance > TAP_THRESHOLD &&
                                    (currentGestureState ==
                                            GestureState.POTENTIAL_TAP ||
                                            currentGestureState ==
                                            GestureState.TOUCH_DOWN)
                                ) {
                                    Log.d(TAG, "Movement detected beyond threshold: $distance")

                                    // Try to transition to DRAGGING state
                                    if (currentState.compareAndSet(
                                            currentGestureState,
                                            GestureState.DRAGGING
                                        )
                                    ) {
                                        Log.d(TAG, "Transitioning to DRAGGING state")
                                        // Cancel long press detection as we're now dragging
                                        longPressJob.cancel()
                                    } else {
                                        Log.d(
                                            TAG,
                                            "Failed to transition to DRAGGING, current state=${currentState.get()}"
                                        )
                                    }
                                }

                                // If pointer is up, end tracking
                                if (change.changedToUp()) {
                                    Log.d(TAG, "Pointer up at $currentPos")
                                    finalPos = currentPos
                                    break
                                }
                            }
                        } finally {
                            // Always ensure we cancel the long press job when tracking ends
                            longPressJob.cancel()
                        }

                        // Process the gesture based on final state
                        when (currentState.get()) {
                            GestureState.POTENTIAL_TAP -> {
                                // This was a simple tap (no drag detected, no long press)
                                Log.d(TAG, "Gesture ended in POTENTIAL_TAP state, triggering tap")
                                currentState.set(GestureState.COMPLETED)
                                onTap()
                            }

                            GestureState.DRAGGING -> {
                                // Calculate the drag distance and direction
                                val deltaX = finalPos.x - startPos.x
                                val deltaY = abs(finalPos.y - startPos.y)
                                Log.d(
                                    TAG,
                                    "Gesture ended in DRAGGING state: deltaX=$deltaX, deltaY=$deltaY, velocityX=$velocityX"
                                )

                                // Determine if it's a valid horizontal swipe or fling
                                val isHorizontalSwipe =
                                    abs(deltaX) > MIN_SWIPE_DISTANCE &&
                                            deltaY < abs(deltaX) * MAX_VERTICAL_RATIO

                                // Consider a high velocity drag as a fling (even if distance is shorter)
                                val isHorizontalFling = abs(velocityX) > FLING_VELOCITY_THRESHOLD &&
                                        deltaY < abs(deltaX) * MAX_VERTICAL_RATIO

                                currentState.set(GestureState.COMPLETED)

                                if (isHorizontalSwipe || isHorizontalFling) {
                                    if (deltaX > 0) {
                                        // Swipe right (RTL: next page)
                                        Log.d(
                                            TAG,
                                            "Swipe/fling right detected (velocityX=$velocityX)"
                                        )
                                        onSwipeRight()
                                    } else {
                                        // Swipe left (RTL: previous page)
                                        Log.d(
                                            TAG,
                                            "Swipe/fling left detected (velocityX=$velocityX)"
                                        )
                                        onSwipeLeft()
                                    }
                                } else {
                                    // If not a clear directional swipe, it might be a tap
                                    val totalDistance = (finalPos - startPos).getDistance()
                                    if (totalDistance < TAP_THRESHOLD) {
                                        Log.d(TAG, "Small movement treated as tap")
                                        onTap()
                                    } else {
                                        Log.d(TAG, "Indeterminate gesture, not processing")
                                    }
                                }
                            }

                            GestureState.LONG_PRESS_DETECTED -> {
                                // Long press was already handled in the long press job
                                Log.d(
                                    TAG,
                                    "Gesture ended in LONG_PRESS_DETECTED state, already handled"
                                )
                                currentState.set(GestureState.COMPLETED)
                            }

                            else -> {
                                // Unexpected end state, log it
                                Log.d(
                                    TAG,
                                    "Gesture ended in unexpected state: ${currentState.get()}"
                                )
                                currentState.set(GestureState.COMPLETED)
                            }
                        }
                    } catch (e: CancellationException) {
                        // Gesture tracking was cancelled, clean up
                        Log.d(TAG, "Gesture tracking cancelled")
                        currentState.set(GestureState.COMPLETED)
                    } catch (e: Exception) {
                        // Add general exception handling
                        Log.e(TAG, "Error in gesture handling: ${e.message}")
                        currentState.set(GestureState.COMPLETED)
                    } finally {
                        // Always clean up and reset state
                        Log.d(TAG, "Gesture cycle complete, final state=${currentState.get()}")
                    }
                }
            }
    }
}
