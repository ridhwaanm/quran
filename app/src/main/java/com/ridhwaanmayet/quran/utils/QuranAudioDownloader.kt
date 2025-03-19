package com.ridhwaanmayet.quran.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import org.json.JSONObject

class QuranAudioDownloader(private val context: Context) {

    companion object {
        private const val TAG = "QuranAudioDownloader"
        private const val BASE_URL = "https://everyayah.com/data/"
        private const val RECITER = "Husary_128kbps"
        private const val MAX_CONCURRENT_DOWNLOADS = 5
    }

    // Get the app's file directory in external storage
    private val quranAudioDir by lazy {
        File(context.getExternalFilesDir(null)?.absolutePath + "/quran_audio")
    }

    // Metadata holder
    private data class SurahMetadata(
            val number: Int,
            val name: String,
            val englishName: String,
            val ayahCount: Int
    )

    // Load metadata from JSON asset
    private suspend fun loadMetadata(): List<SurahMetadata> =
            withContext(Dispatchers.IO) {
                try {
                    val json =
                            context.assets.open("quran-metadata.json").bufferedReader().use {
                                it.readText()
                            }
                    val metadata = JSONObject(json)
                    val surahsArray = metadata.getJSONArray("surahs")

                    val surahs = mutableListOf<SurahMetadata>()
                    for (i in 0 until surahsArray.length()) {
                        val surah = surahsArray.getJSONObject(i)
                        surahs.add(
                                SurahMetadata(
                                        surah.getInt("number"),
                                        surah.getString("name"),
                                        surah.getString("englishName"),
                                        surah.getInt("ayahs")
                                )
                        )
                    }
                    surahs
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading metadata: ${e.message}")
                    emptyList()
                }
            }

    // Format number with leading zeros
    private fun formatNumber(num: Int, digits: Int = 3): String {
        return num.toString().padStart(digits, '0')
    }

    // Download a single ayah
    private suspend fun downloadAyah(surahNum: Int, ayahNum: Int): Boolean =
            withContext(Dispatchers.IO) {
                val surahFormatted = formatNumber(surahNum)
                val ayahFormatted = formatNumber(ayahNum)

                // Create folder for this surah
                val surahDir = File(quranAudioDir, "surah_$surahFormatted")
                if (!surahDir.exists()) {
                    surahDir.mkdirs()
                }

                // Construct file path
                val outputFile = File(surahDir, "${surahFormatted}_${ayahFormatted}.mp3")

                // Skip if file already exists and has content
                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.d(TAG, "File already exists: ${outputFile.name}")
                    return@withContext true
                }

                // Construct URL
                val url = URL("$BASE_URL$RECITER/$surahFormatted$ayahFormatted.mp3")

                try {
                    var connection: HttpURLConnection? = null
                    try {
                        connection =
                                (url.openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 15000
                                    readTimeout = 30000
                                }

                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            connection.inputStream.use { input ->
                                FileOutputStream(outputFile).use { output -> input.copyTo(output) }
                            }
                            Log.d(TAG, "Downloaded: Surah $surahNum, Ayah $ayahNum")
                            true
                        } else {
                            Log.e(
                                    TAG,
                                    "Failed to download: Surah $surahNum, Ayah $ayahNum " +
                                            "(HTTP ${connection.responseCode})"
                            )
                            false
                        }
                    } finally {
                        connection?.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading Surah $surahNum, Ayah $ayahNum: ${e.message}")
                    // Clean up incomplete file
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                    false
                }
            }

    // Check if all files for a specific surah exist
    suspend fun isSurahComplete(surahNum: Int): Boolean =
            withContext(Dispatchers.IO) {
                val metadata = loadMetadata()
                val surah = metadata.find { it.number == surahNum } ?: return@withContext false

                val surahFormatted = formatNumber(surahNum)
                val surahDir = File(quranAudioDir, "surah_$surahFormatted")

                if (!surahDir.exists()) return@withContext false

                // Check if all ayah files exist
                for (ayahNum in 1..surah.ayahCount) {
                    val ayahFormatted = formatNumber(ayahNum)
                    val file = File(surahDir, "${surahFormatted}_${ayahFormatted}.mp3")
                    if (!file.exists() || file.length() == 0L) {
                        return@withContext false
                    }
                }

                true
            }

    // Get list of missing ayahs for a specific surah
    private suspend fun getMissingAyahs(surahNum: Int): List<Int> =
            withContext(Dispatchers.IO) {
                val metadata = loadMetadata()
                val surah =
                        metadata.find { it.number == surahNum } ?: return@withContext emptyList()

                val surahFormatted = formatNumber(surahNum)
                val surahDir = File(quranAudioDir, "surah_$surahFormatted")
                val missingAyahs = mutableListOf<Int>()

                for (ayahNum in 1..surah.ayahCount) {
                    val ayahFormatted = formatNumber(ayahNum)
                    val file = File(surahDir, "${surahFormatted}_${ayahFormatted}.mp3")
                    if (!file.exists() || file.length() == 0L) {
                        missingAyahs.add(ayahNum)
                    }
                }

                missingAyahs
            }

    // Download a specific surah
    suspend fun downloadSurah(surahNum: Int, progressCallback: (Int, Int) -> Unit): Boolean {
        if (isSurahComplete(surahNum)) {
            Log.d(TAG, "Surah $surahNum is already complete")
            return true
        }

        val metadata = loadMetadata()
        val surah = metadata.find { it.number == surahNum } ?: return false

        Log.d(
                TAG,
                "Downloading Surah $surahNum (${surah.englishName}) with ${surah.ayahCount} ayahs"
        )

        // Get only missing ayahs
        val missingAyahs = getMissingAyahs(surahNum)
        val totalMissing = missingAyahs.size

        if (missingAyahs.isEmpty()) {
            return true
        }

        var downloadedCount = 0

        return withContext(Dispatchers.IO) {
            // Use coroutines for concurrent downloads
            val jobs =
                    missingAyahs.map { ayahNum ->
                        async {
                            val result = downloadAyah(surahNum, ayahNum)
                            if (result) {
                                downloadedCount++
                                withContext(Dispatchers.Main) {
                                    progressCallback(downloadedCount, totalMissing)
                                }
                            }
                            result
                        }
                    }

            // Wait for all downloads to complete and check if all were successful
            jobs.awaitAll().all { it }
        }
    }

    // Download all surahs
    suspend fun downloadAllSurahs(progressCallback: (Int, Int, Int, Int) -> Unit): Boolean {
        val metadata = loadMetadata()
        if (metadata.isEmpty()) return false

        var totalCompleted = 0
        val totalSurahs = metadata.size

        return withContext(Dispatchers.Default) {
            try {
                // Create the base directory if it doesn't exist
                if (!quranAudioDir.exists()) {
                    quranAudioDir.mkdirs()
                }

                // Use a limited number of coroutines for downloading surahs in parallel
                val dispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_DOWNLOADS)

                metadata.forEachIndexed { index, surah ->
                    // Report progress before starting each surah
                    withContext(Dispatchers.Main) {
                        progressCallback(index + 1, totalSurahs, 0, surah.ayahCount)
                    }

                    val missingAyahs = getMissingAyahs(surah.number)

                    if (missingAyahs.isEmpty()) {
                        // Surah is already complete
                        totalCompleted++
                        withContext(Dispatchers.Main) {
                            progressCallback(
                                    index + 1,
                                    totalSurahs,
                                    surah.ayahCount,
                                    surah.ayahCount
                            )
                        }
                        return@forEachIndexed
                    }

                    var downloadedCount = surah.ayahCount - missingAyahs.size

                    // Download missing ayahs concurrently with a limited dispatcher
                    withContext(dispatcher) {
                        missingAyahs.forEach { ayahNum ->
                            // Launch each download in its own coroutine
                            launch {
                                val result = downloadAyah(surah.number, ayahNum)
                                if (result) {
                                    downloadedCount++
                                    // Update progress on main thread
                                    withContext(Dispatchers.Main) {
                                        progressCallback(
                                                index + 1,
                                                totalSurahs,
                                                downloadedCount,
                                                surah.ayahCount
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Check if this surah is now complete
                    if (downloadedCount == surah.ayahCount) {
                        totalCompleted++
                    }
                }

                Log.d(
                        TAG,
                        "Download completed. $totalCompleted/$totalSurahs surahs fully downloaded."
                )
                totalCompleted == totalSurahs
            } catch (e: Exception) {
                Log.e(TAG, "Error in download process: ${e.message}")
                false
            }
        }
    }
}

// Example usage in an Activity or ViewModel:
/*
class QuranActivity : AppCompatActivity() {
    private val downloader by lazy { QuranAudioDownloader(applicationContext) }

    private fun downloadSurah(surahNum: Int) {
        lifecycleScope.launch {
            // Show progress UI
            binding.progressBar.isVisible = true

            // Download with progress updates
            val success = downloader.downloadSurah(surahNum) { downloaded, total ->
                val progress = (downloaded * 100) / total
                binding.progressBar.progress = progress
                binding.progressText.text = "$downloaded/$total ayahs downloaded"
            }

            // Update UI when complete
            binding.progressBar.isVisible = false
            if (success) {
                Toast.makeText(this@QuranActivity, "Surah $surahNum downloaded successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@QuranActivity, "Error downloading Surah $surahNum", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
*/
