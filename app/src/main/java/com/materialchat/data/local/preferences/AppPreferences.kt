package com.materialchat.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore extension for the Context.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * Application preferences manager for non-sensitive data.
 *
 * This class manages app-wide preferences such as:
 * - System prompt
 * - Theme selection
 * - Dynamic color toggle
 *
 * Note: API keys and other sensitive data should be stored using EncryptedPreferences.
 */
class AppPreferences(private val context: Context) {

    private val dataStore = context.dataStore

    /**
     * Preference keys for app settings.
     */
    private object Keys {
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("first_launch_complete")
    }

    /**
     * Theme mode options.
     */
    enum class ThemeMode {
        SYSTEM,
        LIGHT,
        DARK
    }

    /**
     * Default values for preferences.
     */
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant."
        val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        const val DEFAULT_DYNAMIC_COLOR_ENABLED = true
        const val DEFAULT_HAPTICS_ENABLED = true
    }

    // ========== System Prompt ==========

    /**
     * Get the current system prompt as a Flow.
     */
    val systemPrompt: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
    }

    /**
     * Set the system prompt.
     */
    suspend fun setSystemPrompt(prompt: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SYSTEM_PROMPT] = prompt
        }
    }

    // ========== Theme Mode ==========

    /**
     * Get the current theme mode as a Flow.
     */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        val themeName = preferences[Keys.THEME_MODE] ?: DEFAULT_THEME_MODE.name
        try {
            ThemeMode.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            DEFAULT_THEME_MODE
        }
    }

    /**
     * Set the theme mode.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = mode.name
        }
    }

    // ========== Dynamic Color ==========

    /**
     * Get whether dynamic color is enabled as a Flow.
     */
    val dynamicColorEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_COLOR_ENABLED] ?: DEFAULT_DYNAMIC_COLOR_ENABLED
    }

    /**
     * Set whether dynamic color is enabled.
     */
    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    // ========== Haptic Feedback ==========

    /**
     * Get whether haptic feedback is enabled as a Flow.
     */
    val hapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.HAPTICS_ENABLED] ?: DEFAULT_HAPTICS_ENABLED
    }

    /**
     * Set whether haptic feedback is enabled.
     */
    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HAPTICS_ENABLED] = enabled
        }
    }

    // ========== First Launch ==========

    /**
     * Check if first launch setup has been completed.
     */
    val firstLaunchComplete: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.FIRST_LAUNCH_COMPLETE] ?: false
    }

    /**
     * Mark first launch setup as complete.
     */
    suspend fun setFirstLaunchComplete() {
        dataStore.edit { preferences ->
            preferences[Keys.FIRST_LAUNCH_COMPLETE] = true
        }
    }

    // ========== Utility Methods ==========

    /**
     * Clear all preferences (for testing or reset functionality).
     */
    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
