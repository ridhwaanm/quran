package com.ridhwaanmayet.quran.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ridhwaanmayet.quran.model.Bookmark
import com.ridhwaanmayet.quran.model.MemorizationSettings
import com.ridhwaanmayet.quran.model.QuranRepository
import com.ridhwaanmayet.quran.model.UserPreferences
import com.ridhwaanmayet.quran.model.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuranViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    // Initialize UserPreferencesRepository
    private val userPreferencesRepository = UserPreferencesRepository(context)

    // Bookmarks state
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    // Current page state
    private val _currentPage = MutableStateFlow(4)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // User preferences state
    private val _userPreferences = MutableStateFlow(UserPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

    // Memorization settings state
    private val _memorizationSettings = MutableStateFlow<MemorizationSettings?>(null)
    val memorizationSettings: StateFlow<MemorizationSettings?> = _memorizationSettings.asStateFlow()

    init {
        // Collect user preferences flow
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { preferences ->
                _userPreferences.value = preferences
            }
        }
    }

    fun loadBookmarks(repository: QuranRepository) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val loadedBookmarks = repository.getBookmarks()
                _bookmarks.value = loadedBookmarks
            }
        }
    }

    fun addBookmark(repository: QuranRepository, bookmark: Bookmark) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.addBookmark(bookmark)
                _bookmarks.value = repository.getBookmarks()
            }
        }
    }

    fun removeBookmark(repository: QuranRepository, bookmark: Bookmark) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.removeBookmark(bookmark)
                _bookmarks.value = repository.getBookmarks()
            }
        }
    }

    fun loadLastSavedPage(repository: QuranRepository) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val lastPage = repository.getLastSavedPage()
                _currentPage.value = lastPage
            }
        }
    }

    // Save the current page
    fun saveCurrentPage(repository: QuranRepository, page: Int) {
        _currentPage.value = page
        viewModelScope.launch { withContext(Dispatchers.IO) { repository.saveCurrentPage(page) } }
    }

    // Update user preferences
    fun updateUserPreferences(newPreferences: UserPreferences) {
        viewModelScope.launch { userPreferencesRepository.updatePreferences(newPreferences) }
    }

    // Memorization Functions

    // Load the last used memorization settings
    fun loadMemorizationSettings(repository: QuranRepository) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val settings = repository.getLastMemorizationSettings()
                _memorizationSettings.value = settings
            }
        }
    }

    // Save memorization settings
    fun saveMemorizationSettings(repository: QuranRepository, settings: MemorizationSettings) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.saveMemorizationSettings(settings)
                _memorizationSettings.value = settings
            }
        }
    }

    // Start a memorization session
    fun startMemorizationSession(repository: QuranRepository, settings: MemorizationSettings) {
        saveMemorizationSettings(repository, settings)
        // Any other setup needed for the memorization session
    }

    // Factory to provide application context to ViewModel
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(QuranViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST") return QuranViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
