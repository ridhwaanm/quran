package com.ridhwaanmayet.quran.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ridhwaanmayet.quran.model.Bookmark
import com.ridhwaanmayet.quran.model.QuranRepository
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Modifier.swipeToNavigate(
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onOtherGesture: () -> Unit  // New callback for non-swipe gestures
): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            val velocityTracker = VelocityTracker()
            var startX = 0f
            var startY = 0f
            var totalDx = 0f
            var totalDy = 0f
            var isSwipe = false

            // Constants for swipe detection
            val minHorizontalSwipeDistance = 50f // Minimum distance for a swipe
            val minVelocity = 400f // Minimum velocity for a swipe
            val maxVerticalRatio = 0.5f // Max ratio of vertical/horizontal movement
            val movementThreshold = 10f // Minimum movement to be considered a drag and not a tap

            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    velocityTracker.resetTracking()
                    startX = offset.x
                    startY = offset.y
                    totalDx = 0f
                    totalDy = 0f
                    isSwipe = false
                },
                onDragEnd = {
                    val velocity = velocityTracker.calculateVelocity()
                    val velocityX = velocity.x

                    // Check if this qualifies as a proper swipe
                    val isHorizontalEnough = totalDy < totalDx.absoluteValue * maxVerticalRatio
                    val isLongEnough = totalDx.absoluteValue > minHorizontalSwipeDistance
                    val isFastEnough = velocityX.absoluteValue > minVelocity

                    if (isSwipe && isHorizontalEnough && isLongEnough && isFastEnough) {
                        // This is a valid swipe - handle page navigation
                        if (velocityX > 0) {
                            // Swipe right - next page (for RTL reading direction)
                            onNextPage()
                        } else {
                            // Swipe left - previous page (for RTL reading direction)
                            onPreviousPage()
                        }
                    } else if (totalDx.absoluteValue < minHorizontalSwipeDistance || !isHorizontalEnough) {
                        // This is not a valid swipe - treat as another gesture
                        // Only trigger if movement was minimal (could be a tap) or not horizontal
                        onOtherGesture()
                    }
                },
                onHorizontalDrag = { change, dragAmount ->
                    // Track movement in both directions
                    totalDx += dragAmount
                    val currentY = change.position.y
                    val dy = (currentY - startY).absoluteValue - totalDy
                    totalDy += dy.coerceAtLeast(0f) // Ensure we don't get negative values

                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                    // If we've moved beyond the threshold, mark it as a swipe attempt
                    if (totalDx.absoluteValue > movementThreshold || totalDy > movementThreshold) {
                        isSwipe = true
                    }

                    // Only consume the event if it's a significant horizontal movement
                    val isHorizontalEnough = totalDy < totalDx.absoluteValue * maxVerticalRatio
                    val isSignificantMovement = dragAmount.absoluteValue > 5f

                    if (isHorizontalEnough && isSignificantMovement) {
                        change.consume()
                    }
                }
            )
        }
    )

@Composable
fun QuranReaderApp(viewModel: QuranViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize repository
    val quranRepository = remember { QuranRepository(context) }

    // Load metadata
    val quranMetadata = remember { quranRepository.loadQuranMetadata() }

    // State
    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    var showIndexDialog by remember { mutableStateOf(false) }
    val bookmarks by viewModel.bookmarks.collectAsState()

    // Navigation visibility state
    var isNavigationVisible by remember { mutableStateOf(true) }

    // Check if current page is bookmarked
    val isCurrentPageBookmarked =
        remember(currentPage, bookmarks) { bookmarks.any { it.page == currentPage } }

    // Get the current orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Function to show navigation and hide after delay
    fun showNavigationTemporarily() {
        println("Navigation visibility triggered") // Debug print
        isNavigationVisible = true
        coroutineScope.launch {
            delay(5000) // 5 seconds
            println("Navigation hidden after delay") // Debug print
            isNavigationVisible = false
        }
    }

    // Load initial bookmarks and set up initial navigation visibility
    LaunchedEffect(Unit) {
        viewModel.loadBookmarks(quranRepository)
        showNavigationTemporarily()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        // The main content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isNavigationVisible) 80.dp else 0.dp)
        ) {
            // Use the improved swipe gesture handler that also handles non-swipe gestures
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .swipeToNavigate(
                        onNextPage = {
                            if (currentPage < quranMetadata.totalPages) {
                                currentPage++
                            }
                        },
                        onPreviousPage = {
                            if (currentPage > 1) {
                                currentPage--
                            }
                        },
                        onOtherGesture = {
                            // This will be called for taps and other non-swipe gestures
                            showNavigationTemporarily()
                        }
                    )
            ) {
                QuranPageContent(
                    currentPage = currentPage,
                    isLandscape = isLandscape,
                    isBookmarked = isCurrentPageBookmarked,
                    showBottomPadding = false, // Don't add padding in the content
                    // Removed long press bookmark functionality per request
                    onAddBookmark = { },
                    onRemoveBookmark = { }
                )
            }
        }

        // Bottom Navigation Bar
        AnimatedVisibility(
            visible = isNavigationVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            ),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            )
        ) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
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
                        if (currentPage < quranMetadata.totalPages) currentPage++
                        showNavigationTemporarily()
                    },
                    enabled = currentPage < quranMetadata.totalPages
                )

                // Index Button
                NavigationBarItem(
                    icon = {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Index")
                    },
                    selected = false,
                    onClick = {
                        showIndexDialog = true
                        showNavigationTemporarily()
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
                        if (currentPage > 1) currentPage--
                        showNavigationTemporarily()
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
                    showNavigationTemporarily()
                },
                onNavigate = { page ->
                    currentPage = page
                    showIndexDialog = false
                    showNavigationTemporarily()
                },
                onAddBookmark = { bookmark ->
                    coroutineScope.launch { viewModel.addBookmark(quranRepository, bookmark) }
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
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Bookmark icon (kept for visual reference)
        if (isBookmarked) {
            IconButton(
                onClick = onRemoveBookmark,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = "Bookmarked",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Display Quran page based on orientation
        if (isLandscape) {
            // Landscape mode - scrollable container
            val scrollState = rememberScrollState()

            // Scrollable container for the image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                // Removed combinedClickable modifier
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/quran_pages/page${(currentPage + 5).toString().padStart(4, '0')}.webp")
                        .build(),
                    contentDescription = "Quran page $currentPage",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    loading = { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) },
                    error = {
                        Text(
                            text = "Could not load image",
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                )
            }
        } else {
            // Portrait mode - fill the screen
            Box(
                modifier = Modifier.fillMaxSize()
                // Removed combinedClickable modifier
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/quran_pages/page${(currentPage + 5).toString().padStart(4, '0')}.webp")
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
    }
}