package com.ridhwaanmayet.quran.ui

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.ridhwaanmayet.quran.utils.CoordinateMapper
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

/**
 * Handles the ayah interaction logic, including loading ayah coordinates from HTML files and
 * detecting taps, long presses, and swipes on ayahs.
 */
class AyahInteractionHandler(private val context: Context) {
    // Cache for the parsed ayah regions to avoid re-parsing HTML files
    private val ayahRegionsCache = ConcurrentHashMap<Int, List<AyahRegion>>()

    // Scope for long press detection
    private val longPressScope = MainScope()

    // Threshold values for gesture detection
    companion object {
        // Minimum distance for a swipe to be registered
        const val MIN_SWIPE_DISTANCE = 50f
        // Maximum distance for a tap to be registered
        const val TAP_THRESHOLD = 20f
        // Minimum duration for a long press in milliseconds
        const val LONG_PRESS_DURATION = 500L
        // Maximum vertical movement allowed for horizontal swipe
        const val MAX_VERTICAL_RATIO = 0.5f
    }

    /** Represents a rectangular region on the screen corresponding to an ayah */
    data class AyahRegion(
            val coords: List<Int>, // [x1, y1, x2, y2]
            val ayahRef: String // Reference to the ayah (e.g., "001001" for Surah 1, Ayah 1)
    ) {
        fun contains(x: Float, y: Float): Boolean {
            return x >= coords[0] && x <= coords[2] && y >= coords[1] && y <= coords[3]
        }
    }

    /** Loads ayah regions for a specific page from the HTML map file */
    suspend fun loadAyahRegions(page: Int): List<AyahRegion> {
        // Return from cache if available
        ayahRegionsCache[page]?.let {
            return it
        }

        return try {
            val htmlFileName = String.format("%03d.html", page)
            val htmlContent =
                    context.assets.open("Html/$htmlFileName").bufferedReader().use { it.readText() }

            // Parse HTML with JSoup
            val document = Jsoup.parse(htmlContent)
            val areaElements = document.select("area[shape=rect]")

            val regions =
                    areaElements.mapNotNull { element ->
                        val coords = element.attr("coords").split(",").map { it.toInt() }
                        val ayahRef = element.attr("rel")

                        if (coords.size >= 4 && ayahRef.isNotEmpty()) {
                            AyahRegion(coords, ayahRef)
                        } else null
                    }

            // Cache the results
            ayahRegionsCache[page] = regions
            regions
        } catch (e: Exception) {
            // If there's an error, return an empty list
            emptyList()
        }
    }

    /** Find which ayah (if any) was tapped based on coordinates */
    fun findAyahAtPosition(
            regions: List<AyahRegion>,
            x: Float,
            y: Float,
            displaySize: Size
    ): AyahRegion? {
        return regions.find { region ->
            CoordinateMapper.isPointInMappedRect(x, y, region.coords, displaySize)
        }
    }

    // Clean up coroutine scope when no longer needed
    fun cleanup() {
        longPressScope.cancel()
    }

    /** Creates a Modifier that handles all touch interactions for the Quran page */
    fun handleAyahInteractions(
            modifier: Modifier = Modifier,
            currentPage: Int,
            displaySize: Size,
            onTap: () -> Unit,
            onSwipeLeft: () -> Unit,
            onSwipeRight: () -> Unit,
            onAyahLongPress: (String) -> Unit
    ): Modifier =
            modifier.pointerInput(currentPage, displaySize) {
                // Load ayah regions for the current page
                val regions = loadAyahRegions(currentPage)

                // Replace deprecated forEachGesture with awaitEachGesture
                awaitEachGesture {
                    // Handle initial touch down
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // Initial position
                    val startPos = down.position
                    var currentPos = startPos
                    var finalPos = startPos

                    // States
                    var isLongPressDetected = false
                    var isDragging = false
                    var hasMoved = false

                    // Define a separate scope for long press detection that can be cancelled
                    val currentLongPressScope = CoroutineScope(longPressScope.coroutineContext)

                    try {
                        // Start long press detection in a separate coroutine scope
                        currentLongPressScope.launch {
                            delay(LONG_PRESS_DURATION)
                            if (!hasMoved) {
                                isLongPressDetected = true
                                // Find which ayah was pressed
                                val ayahRegion =
                                        findAyahAtPosition(
                                                regions,
                                                startPos.x,
                                                startPos.y,
                                                displaySize
                                        )

                                // Trigger long press callback
                                ayahRegion?.let { onAyahLongPress(it.ayahRef) }
                            }
                        }

                        // Track movement until pointer is up
                        var change: PointerInputChange? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            change = event.changes.firstOrNull()

                            // No pointer found, break the loop
                            if (change == null) break

                            currentPos = change.position

                            // Check if we've moved beyond the tap threshold
                            val distance = (currentPos - startPos).getDistance()
                            if (distance > TAP_THRESHOLD && !isLongPressDetected) {
                                hasMoved = true
                                isDragging = true
                                // Cancel long press detection
                                currentLongPressScope.cancel()
                            }

                            // If pointer is up, end tracking
                            if (change.changedToUp()) {
                                finalPos = currentPos
                                break
                            }
                        }

                        // Cancel long press detection if still active
                        currentLongPressScope.cancel()

                        // If not a long press, handle swipe or tap
                        if (!isLongPressDetected) {
                            // Only process if we're dragging
                            if (isDragging) {
                                // Calculate the drag distance and direction
                                val deltaX = finalPos.x - startPos.x
                                val deltaY = abs(finalPos.y - startPos.y)

                                // Determine if it's a valid horizontal swipe
                                val isHorizontalSwipe =
                                        abs(deltaX) > MIN_SWIPE_DISTANCE &&
                                                deltaY < abs(deltaX) * MAX_VERTICAL_RATIO

                                if (isHorizontalSwipe) {
                                    if (deltaX > 0) {
                                        // Swipe right (RTL: next page)
                                        onSwipeRight()
                                    } else {
                                        // Swipe left (RTL: previous page)
                                        onSwipeLeft()
                                    }
                                } else {
                                    // If not a clear directional swipe, it might be a tap
                                    val totalDistance = (finalPos - startPos).getDistance()
                                    if (totalDistance < TAP_THRESHOLD) {
                                        onTap()
                                    }
                                }
                            } else {
                                // This was a simple tap (no drag detected)
                                onTap()
                            }
                        }
                    } catch (e: CancellationException) {
                        // Gesture tracking was cancelled, clean up
                        currentLongPressScope.cancel()
                    }
                }
            }
}
