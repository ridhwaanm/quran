package com.ridhwaanmayet.quran.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ridhwaanmayet.quran.model.MemorizationSettings
import com.ridhwaanmayet.quran.model.QuranMetadata
import com.ridhwaanmayet.quran.model.QuranRepository
import com.ridhwaanmayet.quran.model.Surah
import com.ridhwaanmayet.quran.utils.QuranAudioDownloader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizationScreen(
        quranMetadata: QuranMetadata,
        repository: QuranRepository,
        lastMemorizedSettings: MemorizationSettings?,
        onNavigateBack: () -> Unit,
        onStartMemorization: (MemorizationSettings) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val audioDownloader = remember { QuranAudioDownloader(context) }

    // Download dialog state
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var settingsToStart by remember { mutableStateOf<MemorizationSettings?>(null) }
    var currentDownloadSurah by remember { mutableStateOf<Surah?>(null) }

    // Get the initial surah based on lastMemorizedSettings or default to Surah Al-Fatiha
    val initialSurah = remember {
        if (lastMemorizedSettings != null) {
            quranMetadata.surahs.find { it.number == lastMemorizedSettings.surahNumber }
                    ?: quranMetadata.surahs.first()
        } else {
            quranMetadata.surahs.first()
        }
    }

    // State for the selected surah
    var selectedSurah by remember { mutableStateOf(initialSurah) }

    // State for start and end ayah
    var startAyah by remember {
        mutableStateOf(lastMemorizedSettings?.startAyah?.toString() ?: "1")
    }
    var endAyah by remember {
        mutableStateOf(lastMemorizedSettings?.endAyah?.toString() ?: selectedSurah.ayahs.toString())
    }

    // State for repetition count
    var repeatCount by remember {
        mutableStateOf(lastMemorizedSettings?.repeatCount?.toString() ?: "3")
    }

    // State for loop section toggle
    var loopSection by remember { mutableStateOf(lastMemorizedSettings?.loopSection ?: false) }

    // State for surah search
    var showSurahSearch by remember { mutableStateOf(false) }
    var surahSearchQuery by remember { mutableStateOf("") }

    // Function to check if audio files are available and start memorization or download
    fun checkAndStartMemorization(settings: MemorizationSettings) {
        settingsToStart = settings
        val surah = quranMetadata.surahs.find { it.number == settings.surahNumber }

        if (surah != null) {
            currentDownloadSurah = surah
            coroutineScope.launch {
                val audioAvailable = audioDownloader.isSurahComplete(surah.number)

                if (audioAvailable) {
                    // Audio files are available, proceed to start memorization
                    onStartMemorization(settings)
                } else {
                    // Audio files need to be downloaded
                    downloadProgress = 0f
                    isDownloading = false
                    showDownloadDialog = true
                }
            }
        } else {
            // Fallback if surah not found (shouldn't happen with proper UI validation)
            Toast.makeText(context, "Invalid surah selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Filtered surahs based on search query
    val filteredSurahs =
            remember(surahSearchQuery) {
                if (surahSearchQuery.isEmpty()) {
                    quranMetadata.surahs
                } else {
                    quranMetadata.surahs.filter { surah ->
                        surah.englishName.contains(surahSearchQuery, ignoreCase = true) ||
                                surah.number.toString().contains(surahSearchQuery)
                    }
                }
            }

    // Validation for ayah numbers
    val startAyahError =
            remember(startAyah, selectedSurah) {
                startAyah.toIntOrNull()?.let { it < 1 || it > selectedSurah.ayahs } ?: true
            }

    val endAyahError =
            remember(endAyah, startAyah, selectedSurah) {
                endAyah.toIntOrNull()?.let { end ->
                    startAyah.toIntOrNull()?.let { start ->
                        end < start || end > selectedSurah.ayahs
                    }
                            ?: true
                }
                        ?: true
            }

    val repeatCountError =
            remember(repeatCount) { repeatCount.toIntOrNull()?.let { it < 1 || it > 100 } ?: true }

    // Check if all inputs are valid
    val isValid = !startAyahError && !endAyahError && !repeatCountError

    // Download Dialog
    if (showDownloadDialog && currentDownloadSurah != null) {
        Dialog(
                onDismissRequest = {
                    if (!isDownloading) {
                        showDownloadDialog = false
                    }
                }
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = "Audio Files Required", style = MaterialTheme.typography.titleLarge)
                    Text(
                            text =
                                    "Audio files for ${currentDownloadSurah!!.englishName} need to be downloaded before memorization.",
                            style = MaterialTheme.typography.bodyMedium
                    )

                    if (isDownloading) {
                        LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                                onClick = {
                                    showDownloadDialog = false
                                    settingsToStart = null
                                    currentDownloadSurah = null
                                },
                                enabled = !isDownloading
                        ) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isDownloading = true

                                        // Start the download
                                        val success =
                                                audioDownloader.downloadSurah(
                                                        currentDownloadSurah!!.number
                                                ) { downloaded, total ->
                                                    downloadProgress = downloaded.toFloat() / total
                                                }

                                        isDownloading = false
                                        showDownloadDialog = false

                                        if (success && settingsToStart != null) {
                                            // Download successful, proceed with memorization
                                            onStartMemorization(settingsToStart!!)
                                        } else {
                                            // Download failed
                                            Toast.makeText(
                                                            context,
                                                            "Failed to download audio files. Please try again.",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }

                                        // Reset state
                                        settingsToStart = null
                                        currentDownloadSurah = null
                                    }
                                },
                                enabled = !isDownloading
                        ) { Text("Download") }
                    }
                }
            }
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Memorization") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                )
                            }
                        }
                )
            }
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 16.dp)
                                .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Surah selection
            Text(text = "Select Surah", style = MaterialTheme.typography.titleMedium)

            // Surah selection box
            OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { showSurahSearch = true }) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                                text = selectedSurah.englishName,
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                                text =
                                        "Surah ${selectedSurah.number} • ${selectedSurah.ayahs} Ayahs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select Surah")
                }
            }

            // Ayah range selection
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start Ayah
                OutlinedTextField(
                        value = startAyah,
                        onValueChange = { startAyah = it },
                        label = { Text("Start Ayah") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = startAyahError,
                        supportingText =
                                if (startAyahError) {
                                    { Text("Enter a valid ayah (1-${selectedSurah.ayahs})") }
                                } else null
                )

                // End Ayah
                OutlinedTextField(
                        value = endAyah,
                        onValueChange = { endAyah = it },
                        label = { Text("End Ayah") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = endAyahError,
                        supportingText =
                                if (endAyahError) {
                                    { Text("Must be ≥ start and ≤ ${selectedSurah.ayahs}") }
                                } else null
                )
            }

            // Repetition settings
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat Count
                OutlinedTextField(
                        value = repeatCount,
                        onValueChange = { repeatCount = it },
                        label = { Text("Repeat Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        isError = repeatCountError,
                        supportingText =
                                if (repeatCountError) {
                                    { Text("Enter a number between 1-100") }
                                } else null
                )

                // Loop Toggle
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                ) {
                    Text(
                            text = "Loop Section",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                    )
                    Switch(checked = loopSection, onCheckedChange = { loopSection = it })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Summary card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                            text = "Memorization Summary",
                            style = MaterialTheme.typography.titleMedium
                    )
                    Text("Surah: ${selectedSurah.englishName}")
                    Text(
                            "Verses: ${if (!startAyahError && !endAyahError) "$startAyah to $endAyah" else "Invalid range"}"
                    )
                    Text("Repeat: ${if (!repeatCountError) "$repeatCount times" else "Invalid"}")
                    Text("Loop: ${if (loopSection) "Yes" else "No"}")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Start Button
            Button(
                    onClick = {
                        val settings =
                                MemorizationSettings(
                                        surahNumber = selectedSurah.number,
                                        startAyah = startAyah.toIntOrNull() ?: 1,
                                        endAyah = endAyah.toIntOrNull() ?: selectedSurah.ayahs,
                                        repeatCount = repeatCount.toIntOrNull() ?: 3,
                                        loopSection = loopSection
                                )

                        // Check if audio files exist, otherwise show download dialog
                        checkAndStartMemorization(settings)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isValid
            ) { Text("Start Memorization", modifier = Modifier.padding(vertical = 8.dp)) }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Surah search dialog
        if (showSurahSearch) {
            AlertDialog(
                    onDismissRequest = { showSurahSearch = false },
                    title = { Text("Select Surah") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Search field
                            OutlinedTextField(
                                    value = surahSearchQuery,
                                    onValueChange = { surahSearchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Search Surah") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = "Search")
                                    },
                                    singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Surah list
                            LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                                items(filteredSurahs) { surah ->
                                    val isSelected = surah.number == selectedSurah.number
                                    SurahListItem(
                                            surah = surah,
                                            isSelected = isSelected,
                                            onClick = {
                                                selectedSurah = surah

                                                // Reset ayah values to valid defaults for the new
                                                // surah
                                                startAyah = "1"
                                                endAyah = surah.ayahs.toString()

                                                showSurahSearch = false
                                            }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSurahSearch = false }) { Text("Close") }
                    }
            )
        }
    }
}
