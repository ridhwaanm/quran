package com.ridhwaan.quran.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Data class to represent user preferences - now with landscape display preference
data class UserPreferences(
        val keepNavigationBarVisible: Boolean = false,
        val useDualPageInLandscape: Boolean = true // Default to dual page view (current behavior)
)

// Extension property for Context to access DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

// Repository class to manage user preferences
class UserPreferencesRepository(private val context: Context) {

    // Define keys for preferences
    private object PreferencesKeys {
        val KEEP_NAV_BAR_VISIBLE = booleanPreferencesKey("keep_nav_bar_visible")
        val USE_DUAL_PAGE_IN_LANDSCAPE = booleanPreferencesKey("use_dual_page_in_landscape")
    }

    // Get the preferences as a Flow
    val userPreferencesFlow: Flow<UserPreferences> =
            context.dataStore.data
                    .catch { exception ->
                        // If an error occurs during reading, emit the default values
                        if (exception is IOException) {
                            emit(emptyPreferences())
                        } else {
                            throw exception
                        }
                    }
                    .map { preferences ->
                        // Map preferences to our data class
                        UserPreferences(
                                keepNavigationBarVisible =
                                        preferences[PreferencesKeys.KEEP_NAV_BAR_VISIBLE] ?: false,
                                useDualPageInLandscape =
                                        preferences[PreferencesKeys.USE_DUAL_PAGE_IN_LANDSCAPE]
                                                ?: true
                        )
                    }

    // Update keepNavigationBarVisible preference
    suspend fun updateKeepNavigationBarVisible(keepVisible: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_NAV_BAR_VISIBLE] = keepVisible
        }
    }

    // Update useDualPageInLandscape preference
    suspend fun updateUseDualPageInLandscape(useDualPage: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DUAL_PAGE_IN_LANDSCAPE] = useDualPage
        }
    }

    // Update all preferences at once
    suspend fun updatePreferences(userPreferences: UserPreferences) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_NAV_BAR_VISIBLE] =
                    userPreferences.keepNavigationBarVisible
            preferences[PreferencesKeys.USE_DUAL_PAGE_IN_LANDSCAPE] =
                    userPreferences.useDualPageInLandscape
        }
    }
}
