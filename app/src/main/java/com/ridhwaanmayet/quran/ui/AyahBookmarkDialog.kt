package com.ridhwaanmayet.quran.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridhwaanmayet.quran.model.Bookmark
import com.ridhwaanmayet.quran.model.QuranMetadata

/** Dialog that appears when a user long-presses on an ayah */
@Composable
fun AyahBookmarkDialog(
        ayahRef: String,
        currentPage: Int,
        quranMetadata: QuranMetadata,
        onDismiss: () -> Unit,
        onAddBookmark: (Bookmark) -> Unit,
        onReciteAyah: (String) -> Unit,
        onCopyAyah: (String) -> Unit
) {
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

    // Parse surah and ayah numbers from ayahRef (format: "001001" for Surah 1, Ayah 1)
    val surahNumber = remember(ayahRef) { ayahRef.take(3).toIntOrNull() ?: 1 }

    val ayahNumber = remember(ayahRef) { ayahRef.takeLast(3).toIntOrNull() ?: 1 }

    val surahName =
            remember(surahNumber) {
                quranMetadata.surahs.find { it.number == surahNumber }?.englishName
                        ?: currentSurah.englishName
            }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ayah Options") },
            text = {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Surah: $surahName")
                    Text("Ayah: $ayahNumber")
                    Text("Page: $currentPage")
                }
            },
            confirmButton = {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Bookmark button
                    Button(
                            onClick = {
                                val bookmark =
                                        Bookmark(
                                                page = currentPage,
                                                juzNumber = currentJuz.number,
                                                surahName = surahName ?: "Unknown",
                                                createdAt = System.currentTimeMillis()
                                        )
                                onAddBookmark(bookmark)
                                onDismiss()
                            }
                    ) { Text("Bookmark") }

                    // Recite button
                    Button(
                            onClick = {
                                onReciteAyah(ayahRef)
                                onDismiss()
                            }
                    ) { Text("Recite") }

                    // Copy button
                    Button(
                            onClick = {
                                onCopyAyah(ayahRef)
                                onDismiss()
                            }
                    ) { Text("Copy") }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
