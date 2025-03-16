package com.ridhwaanmayet.quran.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ridhwaanmayet.quran.model.MemorizationSettings
import com.ridhwaanmayet.quran.model.QuranRepository
import com.ridhwaanmayet.quran.model.Surah
import kotlinx.coroutines.delay

@Composable
fun MemorizationPlayer(
        settings: MemorizationSettings,
        surah: Surah,
        quranRepository: QuranRepository,
        onNavigateBack: () -> Unit,
        onSaveProgress: (MemorizationSettings) -> Unit,
        isNavigationVisible: Boolean = true
) {
    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var currentAyah by remember { mutableStateOf(settings.startAyah) }
    var currentRepeat by remember { mutableStateOf(1) }

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
            if (!(currentAyah == settings.endAyah &&
                            currentRepeat == settings.repeatCount &&
                            !settings.loopSection)
            ) {
                currentAyah = getNextAyah()
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
            onSaveProgress(
                    settings.copy(
                            startAyah = currentAyah,
                            lastUsedTimestamp = System.currentTimeMillis()
                    )
            )
        }
    }

    // Minimal player bar that sits above the navigation bar
    Surface(
            modifier = Modifier.fillMaxWidth().height(40.dp).zIndex(8f),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp
    ) {
        Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            IconButton(onClick = onNavigateBack, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close Player")
            }

            // Surah info
            Text(
                    text = "${surah.englishName} (${currentAyah}/${settings.endAyah})",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
            )

            // Play/Pause button
            IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
        }
    }
}
