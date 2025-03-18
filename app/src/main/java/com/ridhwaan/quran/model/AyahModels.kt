package com.ridhwaan.quran.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Represents information about where an ayah starts in the Quran */
@Immutable
data class AyahPosition(val surahNumber: Int, val ayahNumber: Int, val startPage: Int) {
    val ayahRef: String
        get() = "$surahNumber:$ayahNumber"

    // Consistent format for storage in repository
    val identifier: String
        get() =
                "${surahNumber.toString().padStart(3, '0')}${ayahNumber.toString().padStart(3, '0')}"
}

/** Repository of ayah positions for quick lookup */
@Serializable data class AyahPositionsData(val ayahPositions: Map<String, Int> = mapOf())
