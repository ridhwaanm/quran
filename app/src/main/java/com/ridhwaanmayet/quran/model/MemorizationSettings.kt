package com.ridhwaanmayet.quran.model

import kotlinx.serialization.Serializable

/**
 * Data class to store memorization settings Instead of storing the full Surah object, we store the
 * surah number and retrieve it when needed
 */
@Serializable
data class MemorizationSettings(
        val surahNumber: Int = 1,
        val startAyah: Int = 1,
        val endAyah: Int = 7,
        val repeatCount: Int = 3,
        val loopSection: Boolean = false,
        val lastUsedTimestamp: Long = System.currentTimeMillis()
)
