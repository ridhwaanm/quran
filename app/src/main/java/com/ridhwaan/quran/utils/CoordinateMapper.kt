package com.ridhwaan.quran.utils

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.ridhwaan.quran.model.AyahPosition
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class to map HTML image map coordinates to actual screen coordinates. This class handles
 * HTML parsing, coordinate mapping, and maintains a cache for performance. It does NOT directly
 * interact with the repository for ayah position management.
 */
class CoordinateMapper {
    companion object {
        private const val TAG = "CoordinateMapper"

        // Original dimensions of the image maps (from HTML files)
        private const val ORIGINAL_WIDTH = 720.0f
        private const val ORIGINAL_HEIGHT = 1057.0f

        // Correction offset for HTML coordinate mapping
        private const val COORDINATE_OFFSET = 9f

        /** Maps coordinates from the original image size to the displayed size */
        fun mapRect(coords: List<Float>, displaySize: Size): Rect {
            val widthRatio = displaySize.width / (ORIGINAL_WIDTH + COORDINATE_OFFSET)
            val heightRatio = displaySize.height / (ORIGINAL_HEIGHT + COORDINATE_OFFSET)

            val mappedRect =
                    Rect(
                            (coords[0] * widthRatio).toInt(),
                            ((coords[1] + COORDINATE_OFFSET) * heightRatio).toInt(),
                            (coords[2] * widthRatio).toInt(),
                            (coords[3] * heightRatio).toInt()
                    )

            Log.d(
                    TAG,
                    "Mapped rect: original=$coords, display=${displaySize.width}x${displaySize.height}, " +
                            "ratios=${widthRatio}x${heightRatio}, result=$mappedRect"
            )

            return mappedRect
        }

        /** Checks if a touch point (x,y) is within a rectangle defined by the mapped coordinates */
        fun isPointInMappedRect(
                x: Float,
                y: Float,
                originalCoords: List<Float>,
                displaySize: Size
        ): Boolean {
            val rect = mapRect(originalCoords, displaySize)
            val isInRect = x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom

            Log.d(TAG, "Point check: point=($x,$y), rect=$rect, inside=$isInRect")

            return isInRect
        }

        /** Scales a single coordinate value */
        fun scaleX(x: Float, displayWidth: Float): Float {
            return x * (displayWidth / (ORIGINAL_WIDTH + COORDINATE_OFFSET))
        }

        fun scaleY(y: Float, displayHeight: Float): Float {
            return (y + COORDINATE_OFFSET) * (displayHeight / (ORIGINAL_HEIGHT + COORDINATE_OFFSET))
        }

        /** Converts IntSize to Size for easier use with Compose functions */
        fun IntSize.toSize(): Size {
            return Size(width.toFloat(), height.toFloat())
        }
    }

    /** Helper class for parsed Ayah data from HTML */
    data class AyahArea(
            val surahNumber: Int,
            val verseNumber: Int,
            val coords: List<Float>,
            val hasAudio: Boolean = false,
            val startPage: Int? = null
    ) {
        val ayahRef: String
            get() = "$surahNumber:$verseNumber"

        // Convert to AyahPosition model
        fun toAyahPosition(page: Int): AyahPosition {
            return AyahPosition(
                    surahNumber = surahNumber,
                    ayahNumber = verseNumber,
                    startPage = page
            )
        }

        override fun toString(): String {
            return "AyahArea(surah=$surahNumber, verse=$verseNumber, coords=$coords, hasAudio=$hasAudio)"
        }
    }

    /**
     * Parse HTML content to extract Ayah areas
     * @param html HTML content with image map
     * @return List of AyahArea objects containing coordinates and verse references
     */
    fun parseHtmlAreas(html: String): List<AyahArea> {
        val result = mutableListOf<AyahArea>()

        // Parse areas using regex
        val areaPattern =
                Pattern.compile(
                        "<area\\s+[^>]*shape=\"rect\"\\s+[^>]*coords=\"([^\"]*)\"\\s+[^>]*rel=\"([^\"]*)\"",
                        Pattern.DOTALL
                )
        val dataPlayPattern = Pattern.compile("data-start-page-play=\"([^\"]*)\"")

        val matcher = areaPattern.matcher(html)
        var areasFound = 0

        while (matcher.find()) {
            areasFound++
            val coordsStr = matcher.group(1) ?: continue
            val rel = matcher.group(2) ?: continue

            // Check for audio attribute
            val areaHtml = matcher.group(0)
            val dataPlayMatcher = dataPlayPattern.matcher(areaHtml)
            val hasAudio = dataPlayMatcher.find()

            // Extract start page if available
            val startPage =
                    if (hasAudio) {
                        try {
                            dataPlayMatcher.group(1)?.toInt()
                        } catch (e: Exception) {
                            null
                        }
                    } else null

            // Parse rel to get surah and verse (format like "002030" for Surah 2, Verse 30)
            if (rel.length >= 6) {
                try {
                    val surahNumber = rel.substring(0, 3).toInt()
                    val verseNumber = rel.substring(3).toInt()

                    // Parse coordinates - FIXED to parse as float instead of int
                    try {
                        val coords = coordsStr.split(",").map { it.trim().toFloat() }

                        if (coords.size == 4) {
                            result.add(
                                    AyahArea(
                                            surahNumber = surahNumber,
                                            verseNumber = verseNumber,
                                            coords = coords,
                                            hasAudio = hasAudio,
                                            startPage = startPage
                                    )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing coordinates: $coordsStr", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing area: rel=$rel, coords=$coordsStr", e)
                }
            }
        }

        Log.d(
                TAG,
                "HTML parsing found $areasFound area tags, parsed ${result.size} valid ayah areas"
        )

        // Log the first few areas to check parsing
        result.take(3).forEachIndexed { index, area -> Log.d(TAG, "Sample area $index: $area") }

        return result
    }

    /** Cache of parsed HTML areas for better performance */
    private val pageAreasCache = mutableMapOf<Int, List<AyahArea>>()

    /**
     * Load ayah areas for a specific page
     * @param context Android context
     * @param pageNumber Page number to load
     * @return List of AyahArea objects for the page
     */
    suspend fun loadAyahAreasForPage(context: Context, pageNumber: Int): List<AyahArea> =
            withContext(Dispatchers.IO) {
                // Check cache first
                pageAreasCache[pageNumber]?.let {
                    Log.d(TAG, "Using cached areas for page $pageNumber: ${it.size} areas")
                    return@withContext it
                }

                try {
                    val htmlFileName = "${pageNumber.toString().padStart(3, '0')}.html"
                    Log.d(TAG, "Loading HTML file: $htmlFileName")

                    // Use consistent path format with the rest of the app
                    val assetPath = "HTML/$htmlFileName"
                    Log.d(TAG, "Asset path: $assetPath")

                    // Check if file exists
                    try {
                        // Read HTML content
                        val htmlContent =
                                context.assets.open(assetPath).use { inputStream ->
                                    inputStream.bufferedReader().use { it.readText() }
                                }

                        Log.d(TAG, "HTML file loaded, length: ${htmlContent.length}")
                        Log.d(TAG, "Preview of HTML content: ${htmlContent.take(200)}...")

                        // Parse HTML
                        val areas = parseHtmlAreas(htmlContent)

                        // Cache the result
                        pageAreasCache[pageNumber] = areas
                        Log.d(TAG, "Page $pageNumber areas cached: ${areas.size}")

                        areas
                    } catch (e: java.io.FileNotFoundException) {
                        Log.e(TAG, "HTML file not found: $assetPath", e)
                        emptyList()
                    }
                } catch (e: Exception) {
                    // Return empty list on error
                    Log.e(TAG, "Error loading ayah areas for page $pageNumber: ${e.message}", e)
                    emptyList()
                }
            }

    /**
     * Find which ayah was touched at a specific point
     * @param context Android context
     * @param pageNumber Current page number
     * @param x X coordinate of the touch
     * @param y Y coordinate of the touch
     * @param displaySize Current display size
     * @return Ayah reference (e.g. "2:30") or null if no ayah was touched
     */
    suspend fun findAyahAt(
            context: Context,
            pageNumber: Int,
            x: Float,
            y: Float,
            displaySize: Size
    ): String? =
            withContext(Dispatchers.IO) {
                Log.d(
                        TAG,
                        "findAyahAt: page=$pageNumber, point=($x,$y), displaySize=${displaySize.width}x${displaySize.height}"
                )

                val areas = loadAyahAreasForPage(context, pageNumber)
                Log.d(TAG, "Checking ${areas.size} areas for touch point")

                for (area in areas) {
                    if (isPointInMappedRect(x, y, area.coords, displaySize)) {
                        Log.d(TAG, "Found ayah ${area.ayahRef} at touch point")
                        return@withContext area.ayahRef
                    }
                }

                Log.d(TAG, "No ayah found at touch point")
                null
            }

    /**
     * Get all coordinates for a specific ayah on a page
     * @param context Android context
     * @param pageNumber Page number
     * @param ayahRef Ayah reference (e.g. "2:30")
     * @param displaySize Current display size
     * @return List of coordinate rectangles for the ayah
     */
    suspend fun getAyahRects(
            context: Context,
            pageNumber: Int,
            ayahRef: String,
            displaySize: Size
    ): List<Rect> =
            withContext(Dispatchers.IO) {
                val areas = loadAyahAreasForPage(context, pageNumber)

                // Parse the ayah reference
                val parts = ayahRef.split(":")
                if (parts.size != 2) return@withContext emptyList()

                val surahNumber = parts[0].toIntOrNull() ?: return@withContext emptyList()
                val verseNumber = parts[1].toIntOrNull() ?: return@withContext emptyList()

                // Find all areas for this ayah and map them to screen coordinates
                areas
                        .filter { it.surahNumber == surahNumber && it.verseNumber == verseNumber }
                        .map { mapRect(it.coords, displaySize) }
            }

    /** Clear the cache to free up memory */
    fun clearCache() {
        Log.d(TAG, "Clearing area cache (had ${pageAreasCache.size} pages)")
        pageAreasCache.clear()
    }

    /**
     * Get ayah areas for multiple pages Used for scanning and finding ayahs across multiple pages
     */
    suspend fun loadAyahAreasForPages(
            context: Context,
            startPage: Int,
            endPage: Int
    ): Map<Int, List<AyahArea>> =
            withContext(Dispatchers.IO) {
                val result = mutableMapOf<Int, List<AyahArea>>()

                for (page in startPage..endPage) {
                    result[page] = loadAyahAreasForPage(context, page)
                }

                result
            }

    /**
     * Extract all ayah positions from HTML files
     * @param context Android context
     * @param totalPages Total number of pages to scan
     * @param progressCallback Optional callback to report initialization progress
     * @return List of extracted AyahPosition objects
     */
    suspend fun extractAyahPositions(
            context: Context,
            totalPages: Int,
            progressCallback: ((Int, Int) -> Unit)? = null
    ): List<AyahPosition> =
            withContext(Dispatchers.IO) {
                val ayahPositions = mutableListOf<AyahPosition>()
                var totalAyahsFound = 0

                for (pageNumber in 1..totalPages) {
                    try {
                        // Report progress
                        progressCallback?.invoke(pageNumber, totalPages)

                        // Load ayah areas
                        val areas = loadAyahAreasForPage(context, pageNumber)

                        // Extract ayah positions
                        for (area in areas) {
                            val position = area.toAyahPosition(pageNumber)
                            ayahPositions.add(position)
                        }

                        totalAyahsFound += areas.size
                        if (pageNumber % 50 == 0 || pageNumber == totalPages) {
                            Log.d(
                                    TAG,
                                    "Extraction progress: $pageNumber/$totalPages pages, $totalAyahsFound ayahs found"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing page $pageNumber: ${e.message}")
                    }
                }

                Log.d(TAG, "Extraction complete: ${ayahPositions.size} ayah positions extracted")
                ayahPositions
            }
}
