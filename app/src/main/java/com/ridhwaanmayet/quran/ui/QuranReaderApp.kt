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
            isNavigationVisible = userPreferences.keepNavigationBarVisible
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
        val parts = ayahRef.split(":")
        if (parts.size != 2) {
            return
        }

        val surahNumber = parts[0].toIntOrNull() ?: return
        val ayahNumber = parts[1].toIntOrNull() ?: return

        // Find the current juz
        val currentJuz =
                quranMetadata.juzs.findLast { it.startPage <= currentPage }
                        ?: quranMetadata.juzs.first()

        // Get the correct surah name based on the surah number
        val surahName =
                quranMetadata.surahs.find { it.number == surahNumber }?.englishName ?: "Unknown"

        val bookmark =
                Bookmark(
                        page = currentPage,
                        juzNumber = currentJuz.number,
                        surahName = surahName,
                        createdAt = System.currentTimeMillis(),
                        ayahRef = ayahRef,
                        surahNumber = surahNumber,
                        ayahNumber = ayahNumber
                )

        coroutineScope.launch {
            viewModel.addBookmark(quranRepository, bookmark)
            viewModel.loadBookmarks(quranRepository)
            Toast.makeText(
                            context,
                            "Bookmark added for $surahName, Ayah $ayahRef",
                            Toast.LENGTH_SHORT
                    )
                    .show()
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
        // Main QuranReaderApp content
        else {
            // The main content
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(
                                            bottom =
                                                    if (isNavigationVisible) {
                                                        // Add additional padding when the player is
                                                        // showing
                                                        if (showMemorizationPlayer) 80.dp else 40.dp
                                                    } else {
                                                        if (showMemorizationPlayer) 40.dp else 0.dp
                                                    }
                                    )
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
                                                                // Navigate based on display mode in
                                                                // landscape
                                                                if (isLandscape &&
                                                                                userPreferences
                                                                                        .useDualPageInLandscape
                                                                ) {
                                                                    // In dual page landscape mode,
                                                                    // navigate by 2 pages
                                                                    currentPage =
                                                                            maxOf(
                                                                                    1,
                                                                                    currentPage - 2
                                                                            )
                                                                } else {
                                                                    // In portrait or single page
                                                                    // landscape mode, navigate by 1
                                                                    // page
                                                                    currentPage--
                                                                }
                                                            }
                                                            resetScreenTimeout() // Reset on swipe
                                                        },
                                                        onSwipeRight = {
                                                            if (currentPage <
                                                                            quranMetadata.totalPages
                                                            ) {
                                                                // Navigate based on display mode in
                                                                // landscape
                                                                if (isLandscape &&
                                                                                userPreferences
                                                                                        .useDualPageInLandscape
                                                                ) {
                                                                    // In dual page landscape mode,
                                                                    // navigate by 2 pages
                                                                    currentPage =
                                                                            minOf(
                                                                                    quranMetadata
                                                                                            .totalPages,
                                                                                    currentPage + 2
                                                                            )
                                                                } else {
                                                                    // In portrait or single page
                                                                    // landscape mode, navigate by 1
                                                                    // page
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
                        // This is a snippet showing just the change needed in the QuranReaderApp
                        // function
                        // where it calls QuranPageContent

                        // Find this section in the original code where QuranPageContent is called
                        QuranPageContent(
                                currentPage = currentPage,
                                isLandscape = isLandscape,
                                isBookmarked = isCurrentPageBookmarked,
                                showBottomPadding = false,
                                onAddBookmark = {},
                                onRemoveBookmark = { bookmark ->
                                    coroutineScope.launch {
                                        if (bookmark != null) {
                                            // Remove the specific bookmark that was clicked
                                            viewModel.removeBookmark(quranRepository, bookmark)
                                            Toast.makeText(
                                                            context,
                                                            if (bookmark.ayahRef != null)
                                                                    "Removed ayah bookmark"
                                                            else "Removed page bookmark",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        } else {
                                            // Backward compatibility - remove first bookmark for
                                            // this page
                                            val bookmarkToRemove =
                                                    bookmarks.find { it.page == currentPage }
                                            bookmarkToRemove?.let {
                                                viewModel.removeBookmark(quranRepository, it)
                                                Toast.makeText(
                                                                context,
                                                                "Removed bookmark",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                            }
                                        }
                                    }
                                },
                                // Pass the bookmarks list
                                bookmarks = bookmarks,
                                // Pass the ayah interaction handler
                                ayahInteractionHandler = ayahInteractionHandler,
                                // Pass the landscape display preference
                                useDualPageInLandscape = userPreferences.useDualPageInLandscape
                        )
                    }
                }
            }

            // Memorization Player (when active)
            if (showMemorizationPlayer && currentMemorizationSettings != null) {
                val surah =
                        remember(currentMemorizationSettings) {
                            quranRepository.getSurahByNumber(
                                    currentMemorizationSettings!!.surahNumber
                            )
                                    ?: quranMetadata.surahs.first()
                        }

                Box(
                        modifier =
                                Modifier.align(Alignment.BottomCenter)
                                        .offset(y = if (isNavigationVisible) (-40).dp else 0.dp)
                ) {
                    MemorizationPlayer(
                            settings = currentMemorizationSettings!!,
                            surah = surah,
                            quranRepository = quranRepository,
                            onNavigateBack = { showMemorizationPlayer = false },
                            onSaveProgress = { updatedSettings ->
                                currentMemorizationSettings = updatedSettings
                                quranRepository.saveMemorizationSettings(updatedSettings)
                            },
                            isNavigationVisible = isNavigationVisible
                    )
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
                                    // Navigate based on display mode in landscape
                                    if (isLandscape && userPreferences.useDualPageInLandscape) {
                                        // In dual page landscape mode, navigate by 2 pages
                                        currentPage =
                                                minOf(quranMetadata.totalPages, currentPage + 2)
                                    } else {
                                        // In portrait or single page landscape mode, navigate by 1
                                        // page
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
                                    // Navigate based on display mode in landscape
                                    if (isLandscape && userPreferences.useDualPageInLandscape) {
                                        // In dual page landscape mode, navigate by 2 pages
                                        currentPage = maxOf(1, currentPage - 2)
                                    } else {
                                        // In portrait or single page landscape mode, navigate by 1
                                        // page
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
        onRemoveBookmark: (Bookmark?) -> Unit,
        bookmarks: List<Bookmark> = emptyList(),
        ayahInteractionHandler: AyahInteractionHandler? = null,
        useDualPageInLandscape: Boolean = true // Default to true for backward compatibility
) {
    var imageSize by remember { mutableStateOf(Size.Zero) }
    val context = LocalContext.current
    val tag = "QuranPageContent"

    // Log current page and bookmark status
    android.util.Log.d(
            tag,
            "Loading page $currentPage, isBookmarked: $isBookmarked, landscape: $isLandscape, useDualPage: $useDualPageInLandscape"
    )
    android.util.Log.d(tag, "Total bookmarks: ${bookmarks.size}")

    val pageBookmarks =
            remember(bookmarks, currentPage) {
                bookmarks.filter { it.page == currentPage && it.ayahRef == null }
            }

    val ayahBookmarks =
            remember(bookmarks, currentPage) {
                bookmarks.filter { it.page == currentPage && it.ayahRef != null }
            }

    // Log filtered bookmarks
    android.util.Log.d(tag, "Page bookmarks for page $currentPage: ${pageBookmarks.size}")
    android.util.Log.d(tag, "Ayah bookmarks for page $currentPage: ${ayahBookmarks.size}")

    if (ayahBookmarks.isNotEmpty()) {
        android.util.Log.d(tag, "Ayah bookmark details:")
        ayahBookmarks.forEach { bookmark ->
            android.util.Log.d(
                    tag,
                    "  Ayah ref: ${bookmark.ayahRef}, Surah: ${bookmark.surahName}, Number: ${bookmark.ayahNumber}"
            )
        }
    }

    // A map to hold ayah rectangle positions
    val ayahRects = remember { mutableStateMapOf<String, android.graphics.Rect>() }
    var rectsLoaded by remember { mutableStateOf(false) }

    // Load all ayah positions when the size changes
    LaunchedEffect(imageSize, currentPage, ayahBookmarks.size) {
        android.util.Log.d(
                tag,
                "LaunchedEffect triggered - Image size: $imageSize, Ayah bookmarks: ${ayahBookmarks.size}"
        )
        rectsLoaded = false

        if (imageSize.width > 0 && imageSize.height > 0 && ayahInteractionHandler != null) {
            // Clear previous positions
            ayahRects.clear()
            android.util.Log.d(tag, "Cleared previous ayah rectangles")

            // Load positions for all ayah bookmarks
            ayahBookmarks.forEach { bookmark ->
                bookmark.ayahRef?.let { ayahRef ->
                    android.util.Log.d(tag, "Trying to get rect for ayah $ayahRef")

                    val rects =
                            ayahInteractionHandler.getAyahRects(
                                    context,
                                    currentPage,
                                    ayahRef,
                                    imageSize
                            )

                    android.util.Log.d(
                            tag,
                            "getAyahRects returned ${rects.size} rects for $ayahRef"
                    )

                    if (rects.isNotEmpty()) {
                        val rect = rects.first()
                        ayahRects[ayahRef] = rect
                        android.util.Log.d(
                                tag,
                                "Stored rect for $ayahRef: left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}"
                        )
                    } else {
                        android.util.Log.e(tag, "Failed to get rectangle for ayah $ayahRef")
                    }
                }
                        ?: run { android.util.Log.e(tag, "Bookmark has null ayahRef: $bookmark") }
            }

            android.util.Log.d(tag, "Final ayahRects map contains ${ayahRects.size} entries")
            rectsLoaded = true
        } else {
            android.util.Log.w(
                    tag,
                    "Conditions not met to load ayah positions: imageSize=${imageSize}, handler=${ayahInteractionHandler != null}"
            )
        }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize().onSizeChanged {
                        val newSize = Size(it.width.toFloat(), it.height.toFloat())
                        android.util.Log.d(tag, "Image size changed: $newSize")
                        imageSize = newSize
                    }
    ) {
        // Display Quran page based on orientation and preferences
        if (isLandscape) {
            if (useDualPageInLandscape) {
                // Dual page landscape mode - show two pages side by side
                val scrollState = rememberScrollState()

                val rightPage = if (currentPage % 2 == 0) currentPage else currentPage - 1
                val leftPage = if (currentPage % 2 == 0) currentPage + 1 else currentPage

                android.util.Log.d(
                        tag,
                        "Landscape dual page mode - left page: $leftPage, right page: $rightPage"
                )

                // Scrollable container for the two pages
                Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center
                    ) {
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            if (leftPage <= 850 && leftPage >= 1) {
                                SubcomposeAsyncImage(
                                        model =
                                                ImageRequest.Builder(LocalContext.current)
                                                        .data(
                                                                "file:///android_asset/Images/${
                                                    leftPage.toString().padStart(3, '0')
                                                }.webp"
                                                        )
                                                        .build(),
                                        contentDescription = "Quran page $leftPage",
                                        modifier =
                                                Modifier.fillMaxHeight()
                                                        .wrapContentWidth()
                                                        .align(Alignment.Center),
                                        contentScale = ContentScale.Fit,
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
                            if (rightPage >= 1 && rightPage <= 850) {
                                SubcomposeAsyncImage(
                                        model =
                                                ImageRequest.Builder(LocalContext.current)
                                                        .data(
                                                                "file:///android_asset/Images/${
                                                    rightPage.toString().padStart(3, '0')
                                                }.webp"
                                                        )
                                                        .build(),
                                        contentDescription = "Quran page $rightPage",
                                        modifier =
                                                Modifier.fillMaxHeight()
                                                        .wrapContentWidth()
                                                        .align(Alignment.Center),
                                        contentScale = ContentScale.Fit,
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
                // Single page landscape mode with max width while preserving aspect ratio
                android.util.Log.d(tag, "Landscape single page mode: $currentPage")
                val scrollState = rememberScrollState()

                Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .aspectRatio(0.7f)
                                            .align(
                                                    Alignment.Center
                                            ) // Approximate aspect ratio for Quran pages
                    ) {
                        SubcomposeAsyncImage(
                                model =
                                        ImageRequest.Builder(LocalContext.current)
                                                .data(
                                                        "file:///android_asset/Images/${
                                        currentPage.toString().padStart(3, '0')
                                    }.webp"
                                                )
                                                .build(),
                                contentDescription = "Quran page $currentPage",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
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
        } else {
            // Portrait mode - center the image with correct aspect ratio (unchanged)
            android.util.Log.d(tag, "Portrait mode - single page: $currentPage")
            Box(modifier = Modifier.fillMaxSize()) {
                SubcomposeAsyncImage(
                        model =
                                ImageRequest.Builder(LocalContext.current)
                                        .data(
                                                "file:///android_asset/Images/${
                                    currentPage.toString().padStart(3, '0')
                                }.webp"
                                        )
                                        .build(),
                        contentDescription = "Quran page $currentPage",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
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

        // Page bookmark overlay
        Box(modifier = Modifier.fillMaxSize()) {
            if (isBookmarked && pageBookmarks.isNotEmpty()) {
                android.util.Log.d(tag, "Showing page bookmark icon")
                IconButton(
                        onClick = { onRemoveBookmark(pageBookmarks.first()) },
                        modifier =
                                Modifier.align(Alignment.TopEnd)
                                        .offset(x = (-5).dp, y = 0.dp)
                                        .size(36.dp)
                                        .zIndex(100f)
                ) {
                    Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "Page Bookmarked",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }

    if (ayahBookmarks.isNotEmpty() && !isLandscape && rectsLoaded) {
        android.util.Log.d(
                tag,
                "Rendering ayah bookmarks overlay with ${ayahBookmarks.size} bookmarks"
        )

        // Wrap all ayah bookmarks in a BoxWithConstraints to get the size
        BoxWithConstraints(
                modifier =
                        Modifier.fillMaxSize()
                                .zIndex(9999f) // Ensure this overlay is on top of everything
        ) {
            val parentMaxWidth = this.maxWidth
            val parentMaxHeight = this.maxHeight

            android.util.Log.d(
                    tag,
                    "Ayah bookmarks parent container size: $parentMaxWidth x $parentMaxHeight"
            )

            // Calculate scaling factors between the image size and container size
            val scaleX = parentMaxWidth.value / imageSize.width
            val scaleY = parentMaxHeight.value / imageSize.height

            android.util.Log.d(
                    tag,
                    "Scaling factors: scaleX=$scaleX, scaleY=$scaleY (image size: ${imageSize.width}x${imageSize.height})"
            )

            // Place each ayah bookmark in a separate Box
            ayahBookmarks.forEach { bookmark ->
                bookmark.ayahRef?.let { ayahRef ->
                    android.util.Log.d(tag, "Processing ayah bookmark: $ayahRef")
                    val rect = ayahRects[ayahRef]

                    if (rect != null) {
                        android.util.Log.d(tag, "Found rect for $ayahRef: $rect")

                        // DEBUGGING: Log more details about the rectangle position
                        android.util.Log.d(
                                tag,
                                "Rect details: left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}, width=${rect.width()}, height=${rect.height()}, centerX=${rect.centerX()}"
                        )

                        // For Arabic text, we want to position at the right side of the text (which
                        // is the
                        // beginning of the line for RTL text) or use the center for better
                        // visibility
                        val rightX = rect.right
                        val centerX = rect.centerX()

                        // Apply scaling to the rect coordinates - use right edge for x positioning
                        val scaledRight = rightX * scaleX
                        val scaledCenter = centerX * scaleX
                        val scaledTop = rect.top * scaleY

                        // Use the right coordinate for proper RTL positioning
                        val finalX = scaledRight - 20 // Offset a bit from the edge

                        android.util.Log.d(
                                tag,
                                "Scaled coordinates: right=${scaledRight}dp, center=${scaledCenter}dp, top=${scaledTop}dp, finalX=${finalX}dp"
                        )

                        // Use absolute offset with the scaled coordinates
                        Box(
                                modifier =
                                        Modifier.absoluteOffset(
                                                        x = (finalX.dp + 5.dp),
                                                        y = scaledTop.dp
                                                )
                                                .size(30.dp)
                                                .zIndex(
                                                        9999f
                                                ) // Make sure it's on top of everything
                        ) {
                            // Bookmark icon with green tint
                            IconButton(
                                    onClick = {
                                        android.util.Log.d(tag, "Ayah bookmark clicked: $ayahRef")
                                        onRemoveBookmark(bookmark)
                                    },
                                    modifier = Modifier.size(30.dp).align(Alignment.Center)
                            ) {
                                Icon(
                                        imageVector = Icons.Filled.Bookmark,
                                        contentDescription = "Ayah Bookmarked",
                                        tint = Color(0x99007A55),
                                        modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        android.util.Log.d(
                                tag,
                                "Rendered ayah bookmark for $ayahRef at scaled position (${finalX}dp, ${scaledTop}dp)"
                        )
                    } else {
                        android.util.Log.e(tag, "No rectangle found for ayah $ayahRef")
                    }
                }
                        ?: run { android.util.Log.e(tag, "Null ayahRef in bookmark: $bookmark") }
            }
        }
    } else if (ayahBookmarks.isNotEmpty()) {
        if (!rectsLoaded) {
            android.util.Log.d(
                    tag,
                    "Not showing ayah bookmarks yet because rectangles haven't loaded"
            )
        } else if (isLandscape) {
            android.util.Log.d(tag, "Not showing ayah bookmarks in landscape mode")
        }
    }
}
