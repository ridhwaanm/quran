package com.ridhwaan.quran.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Data class to represent user preferences - simplified to only include navigation bar visibility
data class UserPreferences(val keepNavigationBarVisible: Boolean = false)

// Extension property for Context to access DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

// Repository class to manage user preferences
class UserPreferencesRepository(private val context: Context) {

    // Define key for navigation bar visibility preference
    private object PreferencesKeys {
        val KEEP_NAV_BAR_VISIBLE = booleanPreferencesKey("keep_nav_bar_visible")
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
                                        preferences[PreferencesKeys.KEEP_NAV_BAR_VISIBLE] ?: false
                        )
                    }

    // Update keepNavigationBarVisible preference
    suspend fun updateKeepNavigationBarVisible(keepVisible: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_NAV_BAR_VISIBLE] = keepVisible
        }
    }

    // Update all preferences at once (only navigation bar in this case)
    suspend fun updatePreferences(userPreferences: UserPreferences) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_NAV_BAR_VISIBLE] =
                    userPreferences.keepNavigationBarVisible
        }
    }
}
