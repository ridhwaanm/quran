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
data class Bookmark(val page: Int, val juzNumber: Int, val surahName: String, val createdAt: Long) {
    val displayName: String
        get() = "Juz $juzNumber, $surahName, Page $page"
}

/** Repository for handling Quran metadata and bookmarks */
class QuranRepository(private val context: Context) {
    private val sharedPreferences =
            context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

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
        // Remove existing bookmark for the same page if it exists
        currentBookmarks.removeIf { it.page == bookmark.page }
        // Add the new bookmark
        currentBookmarks.add(bookmark)
        // Save updated bookmarks
        saveBookmarks(currentBookmarks)
    }

    // Remove a bookmark
    fun removeBookmark(bookmark: Bookmark) {
        val currentBookmarks = getBookmarks().toMutableList()
        currentBookmarks.removeIf { it.page == bookmark.page }
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

    // Add these new methods for memorization settings
    fun saveMemorizationSettings(settings: MemorizationSettings) {
        val settingsJson = gson.toJson(settings)
        sharedPreferences.edit().putString("memorization_settings", settingsJson).apply()
    }

    fun getLastMemorizationSettings(): MemorizationSettings? {
        val settingsJson = sharedPreferences.getString("memorization_settings", null) ?: return null
        return try {
            gson.fromJson(settingsJson, MemorizationSettings::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Helper function to get the Surah object from a surah number
    fun getSurahByNumber(surahNumber: Int): Surah? {
        val metadata = loadQuranMetadata()
        return metadata.surahs.find { it.number == surahNumber }
    }
}
