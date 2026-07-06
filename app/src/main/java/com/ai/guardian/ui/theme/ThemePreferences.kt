package com.ai.guardian.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ─── Theme mode enum ─────────────────────────────────────────────────────────

enum class ThemeMode { LIGHT, DARK, SYSTEM }

// ─── DataStore instance (singleton per process) ───────────────────────────────

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "guardian_theme")

// ─── Preferences key ─────────────────────────────────────────────────────────

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

// ─── ThemePreferences helper ──────────────────────────────────────────────────

class ThemePreferences(private val context: Context) {

    /** Emits the persisted ThemeMode, defaulting to SYSTEM. */
    val themeModeFlow: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            ThemeMode.LIGHT.name  -> ThemeMode.LIGHT
            ThemeMode.DARK.name   -> ThemeMode.DARK
            else                  -> ThemeMode.SYSTEM
        }
    }

    /** Persists the user-selected ThemeMode. */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }
}
