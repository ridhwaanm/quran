package com.ridhwaan.quran.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ridhwaan.quran.model.MemorizationSettings
import com.ridhwaan.quran.model.QuranMetadata
import com.ridhwaan.quran.model.QuranRepository
import com.ridhwaan.quran.model.Surah

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizationScreen(
    quranMetadata: QuranMetadata,
    repository: QuranRepository,
    lastMemorizedSettings: MemorizationSettings?,
    onNavigateBack: () -> Unit,
    onStartMemorization: (MemorizationSettings) -> Unit
) {
    val scrollState = rememberScrollState()

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
        mutableStateOf(lastMemorizedSettings?.endAyah?.toString()
            ?: selectedSurah.ayahs.toString())
    }

    // State for repetition count
    var repeatCount by remember {
        mutableStateOf(lastMemorizedSettings?.repeatCount?.toString() ?: "3")
    }

    // State for loop section toggle
    var loopSection by remember {
        mutableStateOf(lastMemorizedSettings?.loopSection ?: false)
    }

    // State for surah search
    var showSurahSearch by remember { mutableStateOf(false) }
    var surahSearchQuery by remember { mutableStateOf("") }

    // Filtered surahs based on search query
    val filteredSurahs = remember(surahSearchQuery) {
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
    val startAyahError = remember(startAyah, selectedSurah) {
        startAyah.toIntOrNull()?.let {
            it < 1 || it > selectedSurah.ayahs
        } ?: true
    }

    val endAyahError = remember(endAyah, startAyah, selectedSurah) {
        endAyah.toIntOrNull()?.let { end ->
            startAyah.toIntOrNull()?.let { start ->
                end < start || end > selectedSurah.ayahs
            } ?: true
        } ?: true
    }

    val repeatCountError = remember(repeatCount) {
        repeatCount.toIntOrNull()?.let { it < 1 || it > 100 } ?: true
    }

    // Check if all inputs are valid
    val isValid = !startAyahError && !endAyahError && !repeatCountError

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memorization") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Surah selection
            Text(
                text = "Select Surah",
                style = MaterialTheme.typography.titleMedium
            )

            // Surah selection box
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSurahSearch = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectedSurah.englishName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Surah ${selectedSurah.number} • ${selectedSurah.ayahs} Ayahs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Surah"
                    )
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
                    supportingText = if (startAyahError) {
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
                    supportingText = if (endAyahError) {
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
                    supportingText = if (repeatCountError) {
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
                    Switch(
                        checked = loopSection,
                        onCheckedChange = { loopSection = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Summary card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Memorization Summary",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Surah: ${selectedSurah.englishName}")
                    Text("Verses: ${if (!startAyahError && !endAyahError) "$startAyah to $endAyah" else "Invalid range"}")
                    Text("Repeat: ${if (!repeatCountError) "$repeatCount times" else "Invalid"}")
                    Text("Loop: ${if (loopSection) "Yes" else "No"}")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Start Button
            Button(
                onClick = {
                    onStartMemorization(
                        MemorizationSettings(
                            surahNumber = selectedSurah.number,
                            startAyah = startAyah.toIntOrNull() ?: 1,
                            endAyah = endAyah.toIntOrNull() ?: selectedSurah.ayahs,
                            repeatCount = repeatCount.toIntOrNull() ?: 3,
                            loopSection = loopSection
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            ) {
                Text("Start Memorization", modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Surah search dialog
        if (showSurahSearch) {
            AlertDialog(
                onDismissRequest = { showSurahSearch = false },
                title = { Text("Select Surah") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Search field
                        OutlinedTextField(
                            value = surahSearchQuery,
                            onValueChange = { surahSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search Surah") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Surah list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            items(filteredSurahs) { surah ->
                                val isSelected = surah.number == selectedSurah.number
                                SurahListItem(
                                    surah = surah,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedSurah = surah

                                        // Reset ayah values to valid defaults for the new surah
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
                    TextButton(
                        onClick = { showSurahSearch = false }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}