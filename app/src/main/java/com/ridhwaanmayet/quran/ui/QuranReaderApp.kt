package com.ridhwaanmayet.quran.ui

import android.content.res.Configuration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ridhwaanmayet.quran.model.Bookmark
import com.ridhwaanmayet.quran.model.MemorizationSettings
import com.ridhwaanmayet.quran.model.QuranRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QuranReaderApp(viewModel: QuranViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize repositories and handlers
    val quranRepository = remember { QuranRepository(context) }
    val ayahInteractionHandler = remember { AyahInteractionHandler(context, quranRepository) }

    // Load metadata
    val quranMetadata = remember { quranRepository.loadQuranMetadata() }

    // State
    val currentPageFromViewModel by viewModel.currentPage.collectAsState()
    var currentPage by remember { mutableStateOf(currentPageFromViewModel) }
    var showIndexDialog by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showAyahDialog by remember { mutableStateOf(false) }
    var selectedAyahRef by remember { mutableStateOf("") }
    val bookmarks by viewModel.bookmarks.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()

    var showMemorizationScreen by remember { mutableStateOf(false) }
    var showMemorizationPlayer by remember { mutableStateOf(false) }
    var currentMemorizationSettings by remember {
        mutableStateOf<MemorizationSettings?>(quranRepository.getLastMemorizationSettings())
    }

    // Add state for image size
    var imageSize by remember { mutableStateOf(Size.Zero) }

    // Navigation visibility state - now controlled by user preferences
    var isNavigationVisible by remember { mutableStateOf(true) }

    // Update visibility based on user preferences
    LaunchedEffect(userPreferences.keepNavigationBarVisible) {
        isNavigationVisible = userPreferences.keepNavigationBarVisible || true
    }

    // Job for navigation timer
    var navigationTimerJob by remember { mutableStateOf<Job?>(null) }

    // Always keep screen on (as requested - no toggle in settings)
    var keepScreenOn by remember { mutableStateOf(true) }
    var screenTimeoutJob by remember { mutableStateOf<Job?>(null) }

    // Check if current page is bookmarked
    val isCurrentPageBookmarked =
            remember(currentPage, bookmarks) { bookmarks.any { it.page == currentPage } }

    // Get the current orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Function to reset the screen timeout
    fun resetScreenTimeout() {
        // Cancel existing screen timeout job if any
        screenTimeoutJob?.cancel()

        // Keep screen on
        keepScreenOn = true

        // Start new screen timeout timer
        screenTimeoutJob =
                coroutineScope.launch {
                    delay(300000) // 300 seconds (5 minutes)
                    keepScreenOn = false
                }
    }

    // Function to show navigation and hide after delay if auto-hide is enabled
    fun showNavigationTemporarily() {
        // If user prefers to keep nav bar visible, don't hide it
        if (userPreferences.keepNavigationBarVisible) {
            isNavigationVisible = true
            return
        }

        // Cancel existing timer job if any
        navigationTimerJob?.cancel()

        // Show navigation bar
        isNavigationVisible = true

        // Start new timer to hide navigation
        navigationTimerJob =
                coroutineScope.launch {
                    delay(5000) // 5 seconds
                    isNavigationVisible = false
                }

        // Reset screen timeout whenever there's user interaction
        resetScreenTimeout()
    }

    // Function to extend navigation visibility timer
    fun extendNavigationVisibility() {
        if (isNavigationVisible && !userPreferences.keepNavigationBarVisible) {
            // If already visible and auto-hide is enabled, cancel current timer and extend by 5
            // seconds
            navigationTimerJob?.cancel()
            navigationTimerJob =
                    coroutineScope.launch {
                        delay(5000) // Add another 5 seconds
                        isNavigationVisible = false
                    }
        } else {
            // If not visible, just show it with standard timer
            showNavigationTemporarily()
        }

        // Reset screen timeout whenever there's user interaction
        resetScreenTimeout()
    }

    // Function to handle ayah long press
    fun handleAyahLongPress(ayahRef: String) {
        // Create bookmark immediately instead of showing dialog
        val currentJuz =
                quranMetadata.juzs.findLast { it.startPage <= currentPage }
                        ?: quranMetadata.juzs.first()

        val surahNumber = ayahRef.take(3).toIntOrNull() ?: 1
        val surahName =
                quranMetadata.surahs.find { it.number == surahNumber }?.englishName ?: "Unknown"

        val bookmark =
                Bookmark(
                        page = currentPage,
                        juzNumber = currentJuz.number,
                        surahName = surahName,
                        createdAt = System.currentTimeMillis()
                )

        coroutineScope.launch {
            viewModel.addBookmark(quranRepository, bookmark)
            Toast.makeText(context, "Bookmark added for $surahName", Toast.LENGTH_SHORT).show()
        }

        // Reset screen timeout on long press interaction
        resetScreenTimeout()
    }

    // Apply screen wake lock flag based on keepScreenOn state
    LaunchedEffect(keepScreenOn) {
        val activity = context as? android.app.Activity
        activity?.let {
            if (keepScreenOn) {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Load initial bookmarks and set up initial navigation visibility
    LaunchedEffect(Unit) {
        viewModel.loadBookmarks(quranRepository)
        viewModel.loadLastSavedPage(quranRepository)
        ayahInteractionHandler.initializeAyahPositions()
        showNavigationTemporarily()
        resetScreenTimeout() // Initialize screen timeout
    }

    LaunchedEffect(currentPage) { viewModel.saveCurrentPage(quranRepository, currentPage) }

    LaunchedEffect(currentPageFromViewModel) {
        // Only update once on first collection
        currentPage = currentPageFromViewModel
    }

    // Settings Screen
    if (showSettingsScreen) {
        SettingsScreen(
                onNavigateBack = { showSettingsScreen = false },
                userPreferences = userPreferences,
                onUpdatePreferences = { newPreferences ->
                    viewModel.updateUserPreferences(newPreferences)
                }
        )
        return // Return early to show only the settings screen
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // Add state for memorization
        var showMemorizationScreen by remember { mutableStateOf(false) }
        var showMemorizationPlayer by remember { mutableStateOf(false) }
        var currentMemorizationSettings by remember {
            mutableStateOf(quranRepository.getLastMemorizationSettings())
        }

        // Memorization Screen
        if (showMemorizationScreen) {
            MemorizationScreen(
                    quranMetadata = quranMetadata,
                    repository = quranRepository,
                    lastMemorizedSettings = quranRepository.getLastMemorizationSettings(),
                    onNavigateBack = { showMemorizationScreen = false },
                    onStartMemorization = { settings ->
                        currentMemorizationSettings = settings
                        quranRepository.saveMemorizationSettings(settings)
                        showMemorizationScreen = false
                        showMemorizationPlayer = true
                    }
            )
        }
        // Memorization Player
        else if (showMemorizationPlayer && currentMemorizationSettings != null) {
            val surah =
                    remember(currentMemorizationSettings) {
                        quranRepository.getSurahByNumber(currentMemorizationSettings!!.surahNumber)
                                ?: quranMetadata.surahs.first()
                    }

            MemorizationPlayer(
                    settings = currentMemorizationSettings!!,
                    surah = surah,
                    quranRepository = quranRepository,
                    onNavigateBack = { showMemorizationPlayer = false },
                    onSaveProgress = { updatedSettings ->
                        currentMemorizationSettings = updatedSettings
                        quranRepository.saveMemorizationSettings(updatedSettings)
                    }
            )
        }
        // Main QuranReaderApp content
        else {
            // The main content
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(bottom = if (isNavigationVisible) 40.dp else 0.dp)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .then(
                                                ayahInteractionHandler.handleAyahInteractions(
                                                        currentPage = currentPage,
                                                        displaySize = imageSize,
                                                        onTap = { showNavigationTemporarily() },
                                                        onSwipeLeft = {
                                                            if (currentPage > 1) {
                                                                // In landscape mode, navigate by 2
                                                                // pages at a time
                                                                if (isLandscape) {
                                                                    currentPage =
                                                                            maxOf(
                                                                                    1,
                                                                                    currentPage - 2
                                                                            )
                                                                } else {
                                                                    currentPage--
                                                                }
                                                            }
                                                            resetScreenTimeout() // Reset on swipe
                                                        },
                                                        onSwipeRight = {
                                                            if (currentPage <
                                                                            quranMetadata.totalPages
                                                            ) {
                                                                // In landscape mode, navigate by 2
                                                                // pages at a time
                                                                if (isLandscape) {
                                                                    currentPage =
                                                                            minOf(
                                                                                    quranMetadata
                                                                                            .totalPages,
                                                                                    currentPage + 2
                                                                            )
                                                                } else {
                                                                    currentPage++
                                                                }
                                                            }
                                                            resetScreenTimeout() // Reset on swipe
                                                        },
                                                        onAyahLongPress = { ayahRef ->
                                                            handleAyahLongPress(ayahRef)
                                                        }
                                                )
                                        )
                ) {
                    // Update to capture the image size
                    var pageImageSize by remember { mutableStateOf(Size.Zero) }

                    Box(
                            modifier =
                                    Modifier.fillMaxSize().onSizeChanged {
                                        pageImageSize =
                                                Size(it.width.toFloat(), it.height.toFloat())
                                        imageSize = pageImageSize // Update the outer imageSize
                                    }
                    ) {
                        QuranPageContent(
                                currentPage = currentPage,
                                isLandscape = isLandscape,
                                isBookmarked = isCurrentPageBookmarked,
                                showBottomPadding = false,
                                onAddBookmark = {},
                                onRemoveBookmark = {
                                    // Only attempt to remove if the page is bookmarked
                                    if (isCurrentPageBookmarked) {
                                        val bookmarkToRemove =
                                                bookmarks.find { it.page == currentPage }
                                        bookmarkToRemove?.let {
                                            coroutineScope.launch {
                                                viewModel.removeBookmark(quranRepository, it)
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            // Bottom Navigation Bar
            AnimatedVisibility(
                    visible = isNavigationVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter =
                            fadeIn(animationSpec = tween(300)) +
                                    slideInVertically(
                                            initialOffsetY = { it },
                                            animationSpec = tween(300)
                                    ),
                    exit =
                            fadeOut(animationSpec = tween(300)) +
                                    slideOutVertically(
                                            targetOffsetY = { it },
                                            animationSpec = tween(300)
                                    )
            ) {
                NavigationBar(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    // Next button (left arrow for RTL) - Advances to next page
                    NavigationBarItem(
                            icon = {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Next Page"
                                )
                            },
                            selected = false,
                            onClick = {
                                if (currentPage < quranMetadata.totalPages) {
                                    // In landscape mode, navigate by 2 pages at a time
                                    if (isLandscape) {
                                        currentPage =
                                                minOf(quranMetadata.totalPages, currentPage + 2)
                                    } else {
                                        currentPage++
                                    }
                                }
                                extendNavigationVisibility()
                            },
                            enabled = currentPage < quranMetadata.totalPages
                    )

                    // Memorization Button
                    NavigationBarItem(
                            icon = {
                                Icon(Icons.Filled.AudioFile, contentDescription = "Memorization")
                            },
                            selected = false,
                            onClick = {
                                showMemorizationScreen = true
                                extendNavigationVisibility()
                            }
                    )

                    // Index Button
                    NavigationBarItem(
                            icon = {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Index")
                            },
                            selected = false,
                            onClick = {
                                showIndexDialog = true
                                extendNavigationVisibility()
                            }
                    )

                    // Settings Button (Gear icon)
                    NavigationBarItem(
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                            selected = false,
                            onClick = {
                                showSettingsScreen = true
                                extendNavigationVisibility()
                            }
                    )

                    // Previous button (right arrow for RTL) - Goes to previous page
                    NavigationBarItem(
                            icon = {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Previous Page"
                                )
                            },
                            selected = false,
                            onClick = {
                                if (currentPage > 1) {
                                    // In landscape mode, navigate by 2 pages at a time
                                    if (isLandscape) {
                                        currentPage = maxOf(1, currentPage - 2)
                                    } else {
                                        currentPage--
                                    }
                                }
                                extendNavigationVisibility()
                            },
                            enabled = currentPage > 1
                    )
                }
            }

            // Enhanced Index Dialog
            if (showIndexDialog) {
                EnhancedIndexDialog(
                        quranMetadata = quranMetadata,
                        currentPage = currentPage,
                        bookmarks = bookmarks,
                        onDismiss = {
                            showIndexDialog = false
                            extendNavigationVisibility()
                        },
                        onNavigate = { page ->
                            currentPage = page
                            showIndexDialog = false
                            extendNavigationVisibility()
                        },
                        onAddBookmark = { bookmark ->
                            coroutineScope.launch {
                                viewModel.addBookmark(quranRepository, bookmark)
                            }
                        },
                        onRemoveBookmark = { bookmark ->
                            coroutineScope.launch {
                                viewModel.removeBookmark(quranRepository, bookmark)
                            }
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuranPageContent(
        currentPage: Int,
        isLandscape: Boolean,
        isBookmarked: Boolean,
        showBottomPadding: Boolean,
        onAddBookmark: () -> Unit,
        onRemoveBookmark: () -> Unit
) {
    var imageSize by remember { mutableStateOf(Size.Zero) }

    Box(
            modifier =
                    Modifier.fillMaxSize().onSizeChanged {
                        imageSize = Size(it.width.toFloat(), it.height.toFloat())
                    }
    ) {
        // Display Quran page based on orientation
        if (isLandscape) {
            // Landscape mode - show two pages side by side
            val scrollState = rememberScrollState()

            val rightPage = if (currentPage % 2 == 0) currentPage else currentPage - 1
            val leftPage = if (currentPage % 2 == 0) currentPage + 1 else currentPage

            // Scrollable container for the two pages
            Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        if (leftPage <= 604 && leftPage >= 1) {
                            SubcomposeAsyncImage(
                                    model =
                                            ImageRequest.Builder(LocalContext.current)
                                                    .data(
                                                            "file:///android_asset/Images/${leftPage.toString().padStart(3, '0')}.webp"
                                                    )
                                                    .build(),
                                    contentDescription = "Quran page $leftPage",
                                    modifier =
                                            Modifier.fillMaxHeight()
                                                    .wrapContentWidth()
                                                    .align(Alignment.Center),
                                    contentScale = ContentScale.Fit,
                                    loading = {
                                        CircularProgressIndicator(
                                                modifier = Modifier.align(Alignment.Center)
                                        )
                                    },
                                    error = {
                                        Text(
                                                text = "Could not load image",
                                                color = Color.Red,
                                                modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                            )
                        }
                    }

                    // Right page (even numbered)
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        if (rightPage >= 1 && rightPage <= 604) {
                            SubcomposeAsyncImage(
                                    model =
                                            ImageRequest.Builder(LocalContext.current)
                                                    .data(
                                                            "file:///android_asset/Images/${rightPage.toString().padStart(3, '0')}.webp"
                                                    )
                                                    .build(),
                                    contentDescription = "Quran page $rightPage",
                                    modifier =
                                            Modifier.fillMaxHeight()
                                                    .wrapContentWidth()
                                                    .align(Alignment.Center),
                                    contentScale = ContentScale.Fit,
                                    loading = {
                                        CircularProgressIndicator(
                                                modifier = Modifier.align(Alignment.Center)
                                        )
                                    },
                                    error = {
                                        Text(
                                                text = "Could not load image",
                                                color = Color.Red,
                                                modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        } else {
            // Portrait mode - center the image with correct aspect ratio
            Box(modifier = Modifier.fillMaxSize()) {
                SubcomposeAsyncImage(
                        model =
                                ImageRequest.Builder(LocalContext.current)
                                        .data(
                                                "file:///android_asset/Images/${currentPage.toString().padStart(3, '0')}.webp"
                                        )
                                        .build(),
                        contentDescription = "Quran page $currentPage",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                        loading = {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        },
                        error = {
                            Text(
                                    text = "Could not load image",
                                    color = Color.Red,
                                    modifier = Modifier.align(Alignment.Center)
                            )
                        }
                )
            }
        }

        if (isBookmarked) {
            IconButton(
                    onClick = onRemoveBookmark,
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .offset(
                                            x = (-25).dp,
                                            y = 0.dp
                                    ) // 30dp from right, y=0 against top
                                    .size(36.dp)
                                    .zIndex(10f)
            ) {
                Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = "Bookmarked",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
