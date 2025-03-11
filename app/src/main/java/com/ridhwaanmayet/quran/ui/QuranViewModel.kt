package com.ridhwaanmayet.quran.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridhwaanmayet.quran.model.Bookmark
import com.ridhwaanmayet.quran.model.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuranViewModel : ViewModel() {

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

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
}