package com.ridhwaan.quran.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.ridhwaan.quran.model.AyahPosition
import com.ridhwaan.quran.model.QuranRepository
import com.ridhwaan.quran.utils.CoordinateMapper
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
 * Handles user interactions with the Quran pages including taps, long presses, and swipes. Uses
 * CoordinateMapper for HTML parsing and coordinate transformations. Handles repository operations
 * for ayah position management.
 */
class AyahInteractionHandler(
        private val context: Context,
        private val quranRepository: QuranRepository,
        private val coordinateMapper: CoordinateMapper = CoordinateMapper()
) {
    // Scope for long press detection
    private val longPressScope = MainScope()

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

    // Add TAG for logging
    companion object {
        private const val TAG = "AyahInteractionHandler"

        // Minimum distance for a swipe to be registered
        const val MIN_SWIPE_DISTANCE = 50f
        // Maximum distance for a tap to be registered
        const val TAP_THRESHOLD = 20f
        // Minimum duration for a long press in milliseconds
        const val LONG_PRESS_DURATION = 500L
        // Maximum vertical movement allowed for horizontal swipe
        const val MAX_VERTICAL_RATIO = 0.5f
    }

    /** Initialize ayah position data by extracting from HTML files */
    suspend fun initializeAyahPositions(
            forceUpdate: Boolean = false,
            progressCallback: ((Int, Int) -> Unit)? = null
    ) =
            withContext(Dispatchers.IO) {
                // Implementation unchanged
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

    /** Find which ayah (if any) was tapped based on coordinates */
    private suspend fun findAyahAtPosition(
            page: Int,
            x: Float,
            y: Float,
            displaySize: Size,
            imageActualSize: Size? = null
    ): String? {
        // Implementation unchanged
        val sizeToUse = imageActualSize ?: displaySize

        Log.d(TAG, "Finding ayah at position: page=$page, x=$x, y=$y")
        Log.d(TAG, "Display container size: $displaySize")
        Log.d(TAG, "Image actual size (if available): $imageActualSize")
        Log.d(TAG, "Size used for mapping: $sizeToUse")

        val result = coordinateMapper.findAyahAt(context, page, x, y, sizeToUse)
        Log.d(TAG, "Ayah at position result: $result")
        return result
    }

    /** Store an ayah position in the repository for future reference */
    private suspend fun saveAyahPosition(ayahRef: String, page: Int) =
            withContext(Dispatchers.IO) {
                // Implementation unchanged
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
                // Implementation unchanged
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

    /** Creates a Modifier that handles all touch interactions for the Quran page */
    fun handleAyahInteractions(
            modifier: Modifier = Modifier,
            currentPage: Int,
            displaySize: Size,
            onTap: () -> Unit,
            onSwipeLeft: () -> Unit,
            onSwipeRight: () -> Unit,
            onAyahLongPress: (String) -> Unit,
            actualImageSize: Size? = null
    ): Modifier =
            modifier.pointerInput(currentPage, displaySize) {
                Log.d(
                        TAG,
                        "Setting up pointer input handler for page $currentPage, display size: $displaySize"
                )

                if (actualImageSize != null) {
                    Log.d(TAG, "Actual rendered image size: $actualImageSize")
                }

                awaitEachGesture {
                    // Reset state machine for each new gesture
                    currentState.set(GestureState.IDLE)
                    Log.d(TAG, "Starting new gesture cycle, state=${currentState.get()}")

                    try {
                        // Handle initial touch down
                        val down = awaitFirstDown(requireUnconsumed = false)
                        Log.d(TAG, "Pointer down at ${down.position}")

                        // Move to TOUCH_DOWN state
                        currentState.set(GestureState.TOUCH_DOWN)

                        // Initial position
                        val startPos = down.position
                        var currentPos = startPos
                        var finalPos = startPos

                        // Create a separate job for long press detection
                        val longPressJob =
                                longPressScope.launch {
                                    try {
                                        Log.d(TAG, "Starting long press detection")
                                        delay(LONG_PRESS_DURATION)

                                        // Only proceed if we're still in a valid state for long
                                        // press
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
                                                                actualImageSize
                                                        )

                                                // Trigger long press callback and save ayah
                                                // position
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

                                currentPos = change.position

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
                                        "Gesture ended in DRAGGING state: deltaX=$deltaX, deltaY=$deltaY"
                                )

                                // Determine if it's a valid horizontal swipe
                                val isHorizontalSwipe =
                                        abs(deltaX) > MIN_SWIPE_DISTANCE &&
                                                deltaY < abs(deltaX) * MAX_VERTICAL_RATIO

                                currentState.set(GestureState.COMPLETED)

                                if (isHorizontalSwipe) {
                                    if (deltaX > 0) {
                                        // Swipe right (RTL: next page)
                                        Log.d(TAG, "Swipe right detected")
                                        onSwipeRight()
                                    } else {
                                        // Swipe left (RTL: previous page)
                                        Log.d(TAG, "Swipe left detected")
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

    /**
     * Get rectangles for a specific ayah on the current page
     * @param context Android context
     * @param pageNumber The page number
     * @param ayahRef The ayah reference (e.g. "2:30")
     * @param displaySize Current display size
     * @return List of rectangles where the ayah appears on the page
     */
    suspend fun getAyahRects(
            context: Context,
            pageNumber: Int,
            ayahRef: String,
            displaySize: Size
    ): List<android.graphics.Rect> {
        return coordinateMapper.getAyahRects(context, pageNumber, ayahRef, displaySize)
    }
}
