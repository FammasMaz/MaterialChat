package com.materialchat.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        val AI_GENERATED_TITLES_ENABLED = booleanPreferencesKey("ai_generated_titles_enabled")
        val TITLE_GENERATION_MODEL = stringPreferencesKey("title_generation_model")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
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
        const val DEFAULT_AI_GENERATED_TITLES_ENABLED = true
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

    // ========== AI Generated Titles ==========

    /**
     * Get whether AI-generated titles are enabled as a Flow.
     * When enabled, conversation titles are generated by the AI model after the first response.
     */
    val aiGeneratedTitlesEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AI_GENERATED_TITLES_ENABLED] ?: DEFAULT_AI_GENERATED_TITLES_ENABLED
    }

    /**
     * Set whether AI-generated titles are enabled.
     */
    suspend fun setAiGeneratedTitlesEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AI_GENERATED_TITLES_ENABLED] = enabled
        }
    }

    // ========== Title Generation Model ==========

    /**
     * Get the custom model for title generation as a Flow.
     * Empty string means use the conversation's model.
     */
    val titleGenerationModel: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.TITLE_GENERATION_MODEL] ?: ""
    }

    /**
     * Set the custom model for title generation.
     * Set to empty string to use the conversation's model.
     */
    suspend fun setTitleGenerationModel(model: String) {
        dataStore.edit { preferences ->
            preferences[Keys.TITLE_GENERATION_MODEL] = model
        }
    }

    // ========== App Updates ==========

    /**
     * Get whether automatic update checking is enabled as a Flow.
     * When enabled, the app checks for updates on startup (once per day).
     */
    val autoCheckUpdates: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.AUTO_CHECK_UPDATES] ?: true
    }

    /**
     * Set whether automatic update checking is enabled.
     */
    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_CHECK_UPDATES] = enabled
        }
    }

    /**
     * Get the timestamp of the last update check as a Flow.
     */
    val lastUpdateCheck: Flow<Long> = dataStore.data.map { preferences ->
        preferences[Keys.LAST_UPDATE_CHECK] ?: 0L
    }

    /**
     * Set the timestamp of the last update check.
     */
    suspend fun setLastUpdateCheck(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_UPDATE_CHECK] = timestamp
        }
    }

    /**
     * Get the version that the user has chosen to skip as a Flow.
     * Empty string means no version is skipped.
     */
    val skippedUpdateVersion: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.SKIPPED_UPDATE_VERSION] ?: ""
    }

    /**
     * Set the version to skip (user chose to dismiss this update).
     */
    suspend fun setSkippedUpdateVersion(version: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SKIPPED_UPDATE_VERSION] = version
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
