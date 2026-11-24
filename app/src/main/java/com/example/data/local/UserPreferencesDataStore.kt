package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dolilPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dolil_user_preferences"
)

data class LocalUserPreferences(
    val language: AppLanguage = AppLanguage.BN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val lastSyncMillis: Long = 0L,
    val hasCompletedOnboarding: Boolean = false,
    val isGuestModeActive: Boolean = false,
    val hasCompletedGuestTour: Boolean = false,
    val hasCompletedUserTour: Boolean = false
)

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("preferred_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val IS_GUEST_MODE_ACTIVE = booleanPreferencesKey("is_guest_mode_active")
        val HAS_COMPLETED_GUEST_TOUR = booleanPreferencesKey("has_completed_guest_tour")
        val HAS_COMPLETED_USER_TOUR = booleanPreferencesKey("has_completed_user_tour")
    }

    val preferencesFlow: Flow<LocalUserPreferences> = context.dolilPreferencesDataStore.data
        .map { prefs ->
            LocalUserPreferences(
                language = AppLanguage.fromRaw(prefs[Keys.LANGUAGE]),
                themeMode = ThemeMode.fromRaw(prefs[Keys.THEME_MODE]),
                useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
                lastSyncMillis = prefs[Keys.LAST_SYNC_MILLIS] ?: 0L,
                hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
                isGuestModeActive = prefs[Keys.IS_GUEST_MODE_ACTIVE] ?: false,
                hasCompletedGuestTour = prefs[Keys.HAS_COMPLETED_GUEST_TOUR] ?: false,
                hasCompletedUserTour = prefs[Keys.HAS_COMPLETED_USER_TOUR] ?: false
            )
        }

    suspend fun setLanguage(language: AppLanguage) {
        context.dolilPreferencesDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dolilPreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = themeMode.code
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dolilPreferencesDataStore.edit { prefs ->
            prefs[Keys.USE_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setLastSyncMillis(timestampMillis: Long) {
        context.dolilPreferencesDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_MILLIS] = timestampMillis
        }
    }

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        context.dolilPreferencesDataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun setGuestModeActive(active: Boolean) {
        context.dolilPreferencesDataStore.edit { prefs ->
            prefs[Keys.IS_GUEST_MODE_ACTIVE] = active
        }
    }

    suspend fun setHasCompletedGuestTour(completed: Boolean) {
        context.dolilPreferencesDataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_GUEST_TOUR] = completed
        }
    }

    suspend fun setHasCompletedUserTour(completed: Boolean) {
        context.dolilPreferencesDataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_USER_TOUR] = completed
        }
    }

    suspend fun resetProductTour(isGuest: Boolean) {
        context.dolilPreferencesDataStore.edit { prefs ->
            if (isGuest) {
                prefs[Keys.HAS_COMPLETED_GUEST_TOUR] = false
            } else {
                prefs[Keys.HAS_COMPLETED_USER_TOUR] = false
            }
        }
    }
}
