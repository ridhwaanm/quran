package com.ridhwaan.quran

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ridhwaan.quran.model.QuranRepository
import com.ridhwaan.quran.ui.QuranReaderApp
import com.ridhwaan.quran.ui.QuranViewModel

class MainActivity : ComponentActivity() {
    // Keep a reference to the ViewModel and Repository
    private lateinit var quranViewModel: QuranViewModel
    private lateinit var quranRepository: QuranRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository
        quranRepository = QuranRepository(this)

        // Keep screen on initially
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            // Initialize ViewModel and keep a reference
            quranViewModel = viewModel()

            // Load the last saved page immediately
            quranViewModel.loadLastSavedPage(quranRepository)

            MaterialTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) { QuranReaderApp(viewModel = quranViewModel) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d("MainActivity", "onPause: Ensuring current page is saved")
        if (::quranViewModel.isInitialized && ::quranRepository.isInitialized) {
            // Force an immediate save of the current page
            val currentPage = quranViewModel.currentPage.value
            quranRepository.saveCurrentPage(currentPage)
            android.util.Log.d("MainActivity", "Saved page $currentPage on app pause")
        }

        // Clear the screen on flag when app goes to background
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()

        // Re-enable keep screen on flag when app comes to foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
