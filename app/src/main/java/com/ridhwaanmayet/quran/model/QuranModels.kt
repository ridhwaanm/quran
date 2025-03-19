package com.ridhwaanmayet.quran.model

import android.content.Context
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.Serializable

@Immutable
data class QuranMetadata(
        val totalPages: Int,
        val fileBaseIndex: Int,
        val surahs: List<Surah>,
        val juzs: List<Juz>
)

@Immutable
data class Surah(
        val number: Int,
        val name: String,
        val englishName: String,
        val startPage: Int,
        val ayahs: Int
)

@Immutable
data class Juz(val number: Int, val name: String, val startPage: Int) {
    // Function to check if a page belongs to this juz
    fun containsPage(page: Int, nextJuzStartPage: Int?): Boolean {
        val endPage = nextJuzStartPage?.minus(1) ?: Int.MAX_VALUE
        return page in startPage..endPage
    }
}

@Serializable
data class Bookmark(
        val page: Int,
        val juzNumber: Int,
        val surahName: String,
        val createdAt: Long,
        val ayahRef: String? = null,
        val surahNumber: Int? = null,
        val ayahNumber: Int? = null
) {
    val displayName: String
        get() =
                if (ayahRef != null) {
                    "Juz $juzNumber, $surahName, Ayah $ayahRef, Page $page"
                } else {
                    "Juz $juzNumber, $surahName, Page $page"
                }
}

/** Repository for handling Quran metadata and bookmarks */
class QuranRepository(private val context: Context) {
    private val sharedPreferences =
            context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Ayah positions cache
    private var ayahPositionsCache: Map<String, Int>? = null

    // Load Quran metadata from the JSON file
    fun loadQuranMetadata(): QuranMetadata {
        val jsonString =
                context.assets.open("quran-metadata.json").bufferedReader().use { it.readText() }
        return gson.fromJson(jsonString, QuranMetadata::class.java)
    }

    // Get all bookmarks
    fun getBookmarks(): List<Bookmark> {
        val bookmarksJson = sharedPreferences.getString("bookmarks", "[]") ?: "[]"
        val type = object : TypeToken<List<Bookmark>>() {}.type
        return gson.fromJson(bookmarksJson, type)
    }

    // Add a bookmark
    fun addBookmark(bookmark: Bookmark) {
        val currentBookmarks = getBookmarks().toMutableList()

        // If this is an ayah bookmark, remove any existing bookmarks for the same ayah
        if (bookmark.ayahRef != null) {
            currentBookmarks.removeIf { it.ayahRef == bookmark.ayahRef }
        } else {
            // For page bookmarks, remove existing bookmark for the same page
            currentBookmarks.removeIf { it.page == bookmark.page && it.ayahRef == null }
        }

        // Add the new bookmark
        currentBookmarks.add(bookmark)

        // Save updated bookmarks
        saveBookmarks(currentBookmarks)
    }

    fun removeBookmark(bookmark: Bookmark) {
        val currentBookmarks = getBookmarks().toMutableList()

        if (bookmark.ayahRef != null) {
            // Remove by ayah reference for ayah bookmarks
            currentBookmarks.removeIf { it.ayahRef == bookmark.ayahRef }
        } else {
            // Remove by page for page bookmarks
            currentBookmarks.removeIf { it.page == bookmark.page && it.ayahRef == null }
        }

        saveBookmarks(currentBookmarks)
    }

    // Check if a page is bookmarked
    fun isPageBookmarked(page: Int): Boolean {
        return getBookmarks().any { it.page == page }
    }

    // Save bookmarks to SharedPreferences
    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val bookmarksJson = gson.toJson(bookmarks)
        sharedPreferences.edit().putString("bookmarks", bookmarksJson).apply()
    }

    fun saveCurrentPage(page: Int) {
        sharedPreferences.edit().putInt("last_page", page).apply()
    }

    fun getLastSavedPage(): Int {
        return sharedPreferences.getInt("last_page", 4)
    }

    // Memorization settings methods
    fun saveMemorizationSettings(settings: MemorizationSettings) {
        val settingsJson = gson.toJson(settings)
        sharedPreferences.edit().putString("memorization_settings", settingsJson).apply()
    }

    fun getLastMemorizationSettings(): MemorizationSettings? {
        val settingsJson = sharedPreferences.getString("memorization_settings", null) ?: return null

        return try {
            // Try to parse as the unified format
            gson.fromJson(settingsJson, MemorizationSettings::class.java)
        } catch (e: Exception) {
            try {
                // If that fails, try to handle legacy format and convert to new format
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(settingsJson, type)

                // Create a new settings object with available fields
                MemorizationSettings(
                        surahNumber = (map["surahNumber"] as? Number)?.toInt() ?: 1,
                        startAyah = (map["startAyah"] as? Number)?.toInt()
                                        ?: (map["ayahStart"] as? Number)?.toInt() ?: 1,
                        endAyah = (map["endAyah"] as? Number)?.toInt()
                                        ?: (map["ayahEnd"] as? Number)?.toInt() ?: 7,
                        repeatCount = (map["repeatCount"] as? Number)?.toInt() ?: 3,
                        loopSection = map["loopSection"] as? Boolean ?: false,
                        lastUsedTimestamp = (map["lastUsedTimestamp"] as? Number)?.toLong()
                                        ?: System.currentTimeMillis()
                )
            } catch (e2: Exception) {
                // If all parsing fails, return default settings
                MemorizationSettings()
            }
        }
    }

    // Helper function to get the Surah object from a surah number
    fun getSurahByNumber(surahNumber: Int): Surah? {
        val metadata = loadQuranMetadata()
        return metadata.surahs.find { it.number == surahNumber }
    }

    // NEW METHODS FOR AYAH POSITION TRACKING

    /**
     * Get the starting page for a specific ayah
     * @param surahNumber The surah number
     * @param ayahNumber The ayah number within the surah
     * @return The page number where this ayah starts, or null if not found
     */
    fun getAyahStartPage(surahNumber: Int, ayahNumber: Int): Int? {
        val position = AyahPosition(surahNumber, ayahNumber, 0)
        return getAyahPositions()[position.identifier]
    }

    /**
     * Get the starting page for a specific ayah by reference
     * @param ayahRef The ayah reference in format "surah:ayah" (e.g., "2:255")
     * @return The page number where this ayah starts, or null if not found
     */
    fun getAyahStartPageByRef(ayahRef: String): Int? {
        val parts = ayahRef.split(":")
        if (parts.size != 2) return null

        val surahNumber = parts[0].toIntOrNull() ?: return null
        val ayahNumber = parts[1].toIntOrNull() ?: return null

        return getAyahStartPage(surahNumber, ayahNumber)
    }

    /**
     * Add or update an ayah position in the repository
     * @param position The ayah position to save
     */
    fun addAyahPosition(position: AyahPosition) {
        val positions = getAyahPositions().toMutableMap()
        positions[position.identifier] = position.startPage
        saveAyahPositions(positions)

        // Update cache
        ayahPositionsCache = positions
    }

    /**
     * Add multiple ayah positions at once
     * @param positions List of ayah positions to save
     */
    fun addAyahPositions(positions: List<AyahPosition>) {
        val existingPositions = getAyahPositions().toMutableMap()

        // Add all new positions
        for (position in positions) {
            existingPositions[position.identifier] = position.startPage
        }

        saveAyahPositions(existingPositions)

        // Update cache
        ayahPositionsCache = existingPositions
    }

    /**
     * Get all ayah positions from the repository
     * @return Map of ayah identifiers to starting page numbers
     */
    fun getAyahPositions(): Map<String, Int> {
        // Use cache if available
        ayahPositionsCache?.let {
            return it
        }

        val positionsJson =
                sharedPreferences.getString("ayah_positions", "{\"ayahPositions\":{}}")
                        ?: "{\"ayahPositions\":{}}"

        try {
            val data = gson.fromJson(positionsJson, AyahPositionsData::class.java)
            ayahPositionsCache = data.ayahPositions
            return data.ayahPositions
        } catch (e: Exception) {
            // Return empty map if there's an error
            return mapOf()
        }
    }

    private fun saveAyahPositions(positions: Map<String, Int>) {
        val data = AyahPositionsData(positions)
        val positionsJson = gson.toJson(data)
        sharedPreferences.edit().putString("ayah_positions", positionsJson).apply()
    }
}
