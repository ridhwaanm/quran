package com.ridhwaanmayet.quran.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridhwaanmayet.quran.model.MemorizationSettings
import com.ridhwaanmayet.quran.model.QuranRepository
import com.ridhwaanmayet.quran.model.Surah
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizationPlayer(
    settings: MemorizationSettings,
    surah: Surah,  // We now pass the Surah object separately
    quranRepository: QuranRepository,
    onNavigateBack: () -> Unit,
    onSaveProgress: (MemorizationSettings) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var currentAyah by remember { mutableStateOf(settings.startAyah) }
    var currentRepeat by remember { mutableStateOf(1) }
    var pauseBetweenRepeats by remember { mutableStateOf(3.0f) }

    // Calculate total ayahs in the range
    val ayahCount = settings.endAyah - settings.startAyah + 1

    // Animation for ayah progress
    val progress by animateFloatAsState(
        targetValue = (currentAyah - settings.startAyah).toFloat() / ayahCount.toFloat(),
        label = "ayah progress"
    )

    // Calculate which ayah is next based on current position and settings
    val getNextAyah = {
        if (currentAyah < settings.endAyah) {
            // Move to next ayah in sequence
            currentAyah + 1
        } else {
            // End of section reached
            if (currentRepeat < settings.repeatCount) {
                // Start the section again for another repeat
                currentRepeat++
                settings.startAyah
            } else if (settings.loopSection) {
                // If looping is enabled, reset repeat count and start again
                currentRepeat = 1
                settings.startAyah
            } else {
                // Otherwise, stay at the last ayah (end of session)
                currentAyah
            }
        }
    }

    // Placeholder for actual playback logic
    LaunchedEffect(isPlaying, currentAyah, currentRepeat) {
        if (isPlaying) {
            // Simulate recitation time (would be replaced with actual audio playback)
            val recitationTime = 3000L // 3 seconds per ayah simulation
            delay(recitationTime)

            // When recitation is done, check if we need to move to next ayah
            if (!(currentAyah == settings.endAyah && currentRepeat == settings.repeatCount && !settings.loopSection)) {
                currentAyah = getNextAyah()

                // Add pause between repeats when reaching the end of a section
                if (currentAyah == settings.startAyah && currentRepeat > 1) {
                    delay((pauseBetweenRepeats * 1000).toLong())
                }
            } else {
                // End of session reached
                isPlaying = false
            }
        }
    }

    // Save progress when leaving
    DisposableEffect(Unit) {
        onDispose {
            // Save current progress when leaving the screen
            onSaveProgress(settings.copy(
                startAyah = currentAyah,
                lastUsedTimestamp = System.currentTimeMillis()
            ))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(surah.englishName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            // Reset to beginning
                            isPlaying = false
                            currentAyah = settings.startAyah
                            currentRepeat = 1
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                    }
                    IconButton(onClick = {
                        // Add to favorites or show settings
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${surah.englishName} (${settings.startAyah}-${settings.endAyah})",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Repeat ${currentRepeat}/${settings.repeatCount}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress indicator
                    Text(
                        text = "Ayah $currentAyah",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }

            // Space for Quran text display
            // This would show the actual ayah text in a real implementation
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Surah ${surah.number}, Ayah $currentAyah text would be displayed here",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Pause Between Repeats Slider (when not playing)
            if (!isPlaying) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Pause Between Repeats: ${pauseBetweenRepeats.toInt()} seconds",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = pauseBetweenRepeats,
                        onValueChange = { pauseBetweenRepeats = it },
                        valueRange = 1f..10f,
                        steps = 9
                    )
                }
            }

            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Ayah
                FilledIconButton(
                    onClick = {
                        if (currentAyah > settings.startAyah) {
                            currentAyah--
                        } else if (currentRepeat > 1) {
                            currentRepeat--
                            currentAyah = settings.endAyah
                        }
                    },
                    enabled = currentAyah > settings.startAyah || currentRepeat > 1
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Ayah"
                    )
                }

                // Play/Pause
                FilledIconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Next Ayah
                FilledIconButton(
                    onClick = {
                        if (!(currentAyah == settings.endAyah && currentRepeat == settings.repeatCount && !settings.loopSection)) {
                            currentAyah = getNextAyah()
                        }
                    },
                    enabled = !(currentAyah == settings.endAyah && currentRepeat == settings.repeatCount && !settings.loopSection)
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next Ayah"
                    )
                }
            }
        }
    }
}