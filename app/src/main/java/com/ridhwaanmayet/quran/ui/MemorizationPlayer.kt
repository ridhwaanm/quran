package com.ridhwaanmayet.quran.ui

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ridhwaanmayet.quran.model.MemorizationSettings
import com.ridhwaanmayet.quran.model.QuranRepository
import com.ridhwaanmayet.quran.model.Surah
import java.io.File

@Composable
fun MemorizationPlayer(
    settings: MemorizationSettings,
    surah: Surah,
    quranRepository: QuranRepository,
    onNavigateBack: () -> Unit,
    onSaveProgress: (MemorizationSettings) -> Unit,
    onNavigateToPage: (Int) -> Unit, // New callback to navigate to the page of the current ayah
    isNavigationVisible: Boolean = true
) {
    // Player state
    var isPlaying by remember { mutableStateOf(true) }
    var currentAyah by remember { mutableStateOf(settings.startAyah) }
    var currentRepeat by remember { mutableStateOf(1) }

    // Additional state to track ayah-level repetition
    var currentAyahRepeatCount by remember { mutableStateOf(1) }

    // Media player
    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }

    // Function to get the next state (ayah, repetition)
    val getNextState = {
        if (currentAyahRepeatCount < settings.repeatCount) {
            // Still need to repeat this ayah more times
            currentAyahRepeatCount++
            currentAyah // Same ayah
        } else {
            // We've finished repeating this ayah
            // Reset repetition counter for the next ayah
            currentAyahRepeatCount = 1

            if (currentAyah < settings.endAyah) {
                // Move to next ayah in sequence
                currentAyah + 1
            } else {
                // End of section reached
                if (settings.loopSection) {
                    // If looping is enabled, go back to the first ayah
                    settings.startAyah
                } else {
                    // Otherwise, stay at the last ayah (end of session)
                    currentAyah
                }
            }
        }
    }

    // Navigate to the page of the current ayah
    LaunchedEffect(currentAyah) {
        // Look up the page for the current ayah
        val pageNumber = quranRepository.getAyahStartPage(surah.number, currentAyah)

        // If we found a page number, navigate to it
        if (pageNumber != null) {
            Log.d("MemorizationPlayer", "Navigating to page $pageNumber for Surah ${surah.number}, Ayah $currentAyah")
            onNavigateToPage(pageNumber)
        } else {
            Log.e("MemorizationPlayer", "Could not find page for Surah ${surah.number}, Ayah $currentAyah")
        }
    }

    // Function to get audio file for current ayah
    fun getAudioFile(context: Context, surahNum: Int, ayahNum: Int): File? {
        val quranAudioDir = File(context.getExternalFilesDir(null)?.absolutePath + "/quran_audio")
        val surahFormatted = surahNum.toString().padStart(3, '0')
        val ayahFormatted = ayahNum.toString().padStart(3, '0')

        val surahDir = File(quranAudioDir, "surah_$surahFormatted")
        val audioFile = File(surahDir, "${surahFormatted}_${ayahFormatted}.mp3")

        return if (audioFile.exists() && audioFile.length() > 0) {
            audioFile
        } else {
            Log.e("MemorizationPlayer", "Audio file not found: ${audioFile.absolutePath}")
            null
        }
    }

    // Set up completion listener just once
    DisposableEffect(mediaPlayer) {
        mediaPlayer.setOnCompletionListener {
            // Check if we've reached the very end (last ayah's last repetition)
            if (currentAyah == settings.endAyah &&
                currentAyahRepeatCount == settings.repeatCount &&
                !settings.loopSection) {
                // End of session reached
                isPlaying = false
            } else {
                // Get next state (either repeat same ayah or move to next)
                currentAyah = getNextState()
            }
        }

        onDispose { mediaPlayer.setOnCompletionListener(null) }
    }

    // Playback logic
    LaunchedEffect(isPlaying, currentAyah, currentAyahRepeatCount) {
        if (isPlaying) {
            try {
                // Get the audio file for the current ayah
                val audioFile = getAudioFile(context, surah.number, currentAyah)

                if (audioFile != null) {
                    // Reset and prepare the media player
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(audioFile.absolutePath)
                    mediaPlayer.prepare()

                    // Start playback
                    mediaPlayer.start()
                } else {
                    // Log error and stop playing if file doesn't exist
                    Log.e("MemorizationPlayer", "Audio file not found, stopping playback")
                    isPlaying = false
                }
            } catch (e: Exception) {
                Log.e("MemorizationPlayer", "Error playing audio: ${e.message}")
                isPlaying = false
            }
        } else {
            // Stop playback when isPlaying is false
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
        }
    }

    // Clean up resources when done
    DisposableEffect(Unit) {
        onDispose {
            // Release media player resources
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()

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

            // Surah info with ayah repetition indicator
            Text(
                text = "${surah.englishName} (${currentAyah}/${settings.endAyah}, ${currentAyahRepeatCount}/${settings.repeatCount})",
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