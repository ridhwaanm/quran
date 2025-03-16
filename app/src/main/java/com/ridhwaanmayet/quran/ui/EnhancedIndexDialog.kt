package com.ridhwaanmayet.quran.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ridhwaanmayet.quran.model.Bookmark
import com.ridhwaanmayet.quran.model.Juz
import com.ridhwaanmayet.quran.model.QuranMetadata
import com.ridhwaanmayet.quran.model.Surah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedIndexDialog(
        quranMetadata: QuranMetadata,
        currentPage: Int,
        bookmarks: List<Bookmark>,
        onDismiss: () -> Unit,
        onNavigate: (Int) -> Unit,
        onAddBookmark: (Bookmark) -> Unit,
        onRemoveBookmark: (Bookmark) -> Unit
) {
    // Find current Surah and Juz based on current page
    val currentSurah =
            remember(currentPage) {
                quranMetadata.surahs.findLast { it.startPage <= currentPage }
                        ?: quranMetadata.surahs.first()
            }
    val currentJuz =
            remember(currentPage) {
                quranMetadata.juzs.findLast { it.startPage <= currentPage }
                        ?: quranMetadata.juzs.first()
            }

    // State for the tabs
    val tabOptions = listOf("Surah", "Juz", "Page", "Bookmarks")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // State for search
    var searchQuery by remember { mutableStateOf("") }
    var surahSearchResults by remember { mutableStateOf(quranMetadata.surahs) }

    // State for page input
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    var pageError by remember { mutableStateOf(false) }

    // State for juz selection
    var selectedJuzNumber by remember { mutableIntStateOf(currentJuz.number) }

    // Current bookmark status
    val isCurrentPageBookmarked =
            remember(currentPage, bookmarks) { bookmarks.any { it.page == currentPage } }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Quran Navigator") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    // Tabs
                    TabRow(
                            selectedTabIndex = selectedTabIndex,
                            // Add divider color to make tabs more compact
                            divider = { HorizontalDivider(thickness = 1.dp) }
                    ) {
                        tabOptions.forEachIndexed { index, title ->
                            Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    modifier =
                                            Modifier.padding(
                                                    horizontal = 0.5.dp,
                                                    vertical = 0.5.dp
                                            ),
                                    icon =
                                            if (title == "Bookmarks") {
                                                // If it's the Bookmarks tab, show the bookmark icon
                                                {
                                                    Icon(
                                                            Icons.Filled.Bookmark,
                                                            contentDescription = "Bookmarks"
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                    text =
                                            if (title != "Bookmarks") {
                                                // If it's not the Bookmarks tab, show the text
                                                {
                                                    Text(
                                                            text = title,
                                                            style =
                                                                    MaterialTheme.typography
                                                                            .labelSmall
                                                    )
                                                }
                                            } else {
                                                null
                                            }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content based on selected tab
                    when (selectedTabIndex) {
                        0 -> { // Surah Tab
                            SurahTab(
                                    surahs = surahSearchResults,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = {
                                        searchQuery = it
                                        surahSearchResults =
                                                if (it.isEmpty()) {
                                                    quranMetadata.surahs
                                                } else {
                                                    quranMetadata.surahs.filter { surah ->
                                                        surah.englishName.contains(
                                                                it,
                                                                ignoreCase = true
                                                        ) || surah.number.toString() == it
                                                    }
                                                }
                                    },
                                    onSurahSelected = { surah -> onNavigate(surah.startPage) },
                                    currentSurah = currentSurah
                            )
                        }
                        1 -> { // Juz Tab
                            JuzTab(
                                    juzs = quranMetadata.juzs,
                                    selectedJuzNumber = selectedJuzNumber,
                                    onJuzSelected = { juzNumber ->
                                        selectedJuzNumber = juzNumber
                                        val selectedJuz =
                                                quranMetadata.juzs.find { it.number == juzNumber }
                                        selectedJuz?.let { onNavigate(it.startPage) }
                                    }
                            )
                        }
                        2 -> { // Page Tab
                            PageTab(
                                    pageInput = pageInput,
                                    pageError = pageError,
                                    totalPages = quranMetadata.totalPages,
                                    onPageInputChange = { input ->
                                        pageInput = input
                                        pageError =
                                                input.toIntOrNull()?.let {
                                                    it in 1..quranMetadata.totalPages
                                                } == false
                                    },
                                    onNavigateToPage = {
                                        val page = pageInput.toIntOrNull()
                                        if (page != null && page in 1..quranMetadata.totalPages) {
                                            onNavigate(page)
                                        } else {
                                            pageError = true
                                        }
                                    }
                            )
                        }
                        3 -> { // Bookmarks Tab
                            BookmarksTab(
                                    bookmarks = bookmarks,
                                    quranMetadata = quranMetadata,
                                    onBookmarkSelected = { bookmark -> onNavigate(bookmark.page) },
                                    onRemoveBookmark = onRemoveBookmark
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bookmark Toggle
                    IconButton(
                            onClick = {
                                val bookmark =
                                        Bookmark(
                                                page = currentPage,
                                                juzNumber = currentJuz.number,
                                                surahName = currentSurah.englishName,
                                                createdAt = System.currentTimeMillis()
                                        )

                                if (isCurrentPageBookmarked) {
                                    onRemoveBookmark(bookmark)
                                } else {
                                    onAddBookmark(bookmark)
                                }
                            }
                    ) {
                        Icon(
                                imageVector =
                                        if (isCurrentPageBookmarked) Icons.Filled.Bookmark
                                        else Icons.Filled.BookmarkBorder,
                                contentDescription =
                                        if (isCurrentPageBookmarked) "Remove Bookmark"
                                        else "Add Bookmark"
                        )
                    }

                    // Close Button
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahTab(
        surahs: List<Surah>,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        onSurahSelected: (Surah) -> Unit,
        currentSurah: Surah
) {
    Column {
        // Search bar
        OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search Surah") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Surah list
        LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            items(surahs) { surah ->
                val isSelected = surah.number == currentSurah.number
                SurahListItem(
                        surah = surah,
                        isSelected = isSelected,
                        onClick = { onSurahSelected(surah) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SurahListItem(surah: Surah, isSelected: Boolean, onClick: () -> Unit) {
    ListItem(
            headlineContent = {
                Text(
                        text = "${surah.number}. ${surah.englishName}",
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = {
                Text(
                        text = "${surah.ayahs} Ayahs • Page ${surah.startPage}",
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun JuzTab(juzs: List<Juz>, selectedJuzNumber: Int, onJuzSelected: (Int) -> Unit) {
    Column {
        Text(
                text = "Select Juz",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            items(juzs) { juz ->
                val juzNumber = juz.number
                val isSelected = juzNumber == selectedJuzNumber

                // Calculate page range for this juz
                val nextJuzIndex = juzs.indexOfFirst { it.number == juz.number + 1 }
                val endPage =
                        if (nextJuzIndex != -1) {
                            juzs[nextJuzIndex].startPage - 1
                        } else {
                            // Last juz ends at the last page of the Quran
                            juzs.first { it.number == 30 }.startPage +
                                    29 // Approximate end page for the last juz
                        }

                ListItem(
                        headlineContent = {
                            Text(
                                    text = juz.name,
                                    color =
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(
                                    text = "Pages ${juz.startPage}-$endPage",
                                    color =
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { onJuzSelected(juzNumber) }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTab(
        pageInput: String,
        pageError: Boolean,
        totalPages: Int,
        onPageInputChange: (String) -> Unit,
        onNavigateToPage: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
                text = "Enter Page Number (1-$totalPages)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
                value = pageInput,
                onValueChange = onPageInputChange,
                label = { Text("Page Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = pageError,
                supportingText =
                        if (pageError) {
                            { Text("Please enter a valid page number (1-$totalPages)") }
                        } else null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        Button(onClick = onNavigateToPage, modifier = Modifier.padding(top = 8.dp)) {
            Text("Go to Page")
        }
    }
}

@Composable
fun BookmarksTab(
        bookmarks: List<Bookmark>,
        quranMetadata: QuranMetadata,
        onBookmarkSelected: (Bookmark) -> Unit,
        onRemoveBookmark: (Bookmark) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentAlignment = Alignment.Center
        ) { Text("No bookmarks yet. Long-press on a page or ayah to create a bookmark.") }
    } else {
        // Group bookmarks by type
        val pageBookmarks = bookmarks.filter { it.ayahRef == null }
        val ayahBookmarks = bookmarks.filter { it.ayahRef != null }

        Column(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            // Only show tabs if we have both types of bookmarks
            if (pageBookmarks.isNotEmpty() && ayahBookmarks.isNotEmpty()) {
                var selectedTabIndex by remember { mutableIntStateOf(0) }
                val tabs = listOf("Pages", "Ayahs")

                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show the appropriate bookmarks based on tab selection
                when (selectedTabIndex) {
                    0 ->
                            BookmarksList(
                                    bookmarks = pageBookmarks.sortedByDescending { it.createdAt },
                                    onBookmarkSelected = onBookmarkSelected,
                                    onRemoveBookmark = onRemoveBookmark
                            )
                    1 ->
                            BookmarksList(
                                    bookmarks = ayahBookmarks.sortedByDescending { it.createdAt },
                                    onBookmarkSelected = onBookmarkSelected,
                                    onRemoveBookmark = onRemoveBookmark
                            )
                }
            } else {
                // If we only have one type of bookmarks, show all without tabs
                BookmarksList(
                        bookmarks = bookmarks.sortedByDescending { it.createdAt },
                        onBookmarkSelected = onBookmarkSelected,
                        onRemoveBookmark = onRemoveBookmark
                )
            }
        }
    }
}

@Composable
fun BookmarksList(
        bookmarks: List<Bookmark>,
        onBookmarkSelected: (Bookmark) -> Unit,
        onRemoveBookmark: (Bookmark) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        items(bookmarks) { bookmark ->
            BookmarkItem(
                    bookmark = bookmark,
                    onClick = { onBookmarkSelected(bookmark) },
                    onRemove = { onRemoveBookmark(bookmark) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun BookmarkItem(bookmark: Bookmark, onClick: () -> Unit, onRemove: () -> Unit) {
    val isAyahBookmark = bookmark.ayahRef != null

    ListItem(
            headlineContent = {
                if (isAyahBookmark) {
                    Text("${bookmark.surahName}, Ayah ${bookmark.ayahRef}")
                } else {
                    Text("${bookmark.surahName} (Juz ${bookmark.juzNumber})")
                }
            },
            supportingContent = {
                if (isAyahBookmark) {
                    Text("Page ${bookmark.page}, Juz ${bookmark.juzNumber}")
                } else {
                    Text("Page ${bookmark.page}")
                }
            },
            trailingContent = {
                IconButton(onClick = onRemove) {
                    Icon(
                            imageVector = Icons.Filled.BookmarkBorder,
                            contentDescription = "Remove Bookmark"
                    )
                }
            },
            modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun PageBookmarkItem(bookmark: Bookmark, onClick: () -> Unit, onRemove: () -> Unit) {
    ListItem(
            headlineContent = { Text("${bookmark.surahName} (Juz ${bookmark.juzNumber})") },
            supportingContent = { Text("Page ${bookmark.page}") },
            trailingContent = {
                IconButton(onClick = onRemove) {
                    Icon(
                            imageVector = Icons.Filled.BookmarkBorder,
                            contentDescription = "Remove Bookmark"
                    )
                }
            },
            modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun AyahBookmarkItem(bookmark: Bookmark, onClick: () -> Unit, onRemove: () -> Unit) {
    val ayahRef = bookmark.ayahRef ?: ""

    ListItem(
            headlineContent = { Text("${bookmark.surahName}, Ayah ${ayahRef}") },
            supportingContent = { Text("Page ${bookmark.page}, Juz ${bookmark.juzNumber}") },
            trailingContent = {
                IconButton(onClick = onRemove) {
                    Icon(
                            imageVector = Icons.Filled.BookmarkBorder,
                            contentDescription = "Remove Bookmark"
                    )
                }
            },
            modifier = Modifier.clickable(onClick = onClick)
    )
}
