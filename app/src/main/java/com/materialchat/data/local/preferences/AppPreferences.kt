package com.materialchat.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.materialchat.domain.model.ReasoningEffort
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
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val CHAT_BUBBLE_STYLE = stringPreferencesKey("chat_bubble_style")
        val CONTROL_SHAPE_STYLE = stringPreferencesKey("control_shape_style")
        val MAIN_BUTTON_SHAPE = stringPreferencesKey("main_button_shape")
        val CHAT_BUTTON_SHAPE = stringPreferencesKey("chat_button_shape")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val CHAT_HAPTICS_ENABLED = booleanPreferencesKey("chat_haptics_enabled")
        val NAVIGATION_HAPTICS_ENABLED = booleanPreferencesKey("navigation_haptics_enabled")
        val LIST_HAPTICS_ENABLED = booleanPreferencesKey("list_haptics_enabled")
        val GESTURE_HAPTICS_ENABLED = booleanPreferencesKey("gesture_haptics_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
        val FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("first_launch_complete")
        val AI_GENERATED_TITLES_ENABLED = booleanPreferencesKey("ai_generated_titles_enabled")
        val TITLE_GENERATION_MODEL = stringPreferencesKey("title_generation_model")
        val DEFAULT_IMAGE_GENERATION_MODEL = stringPreferencesKey("default_image_generation_model")
        val DEFAULT_IMAGE_OUTPUT_FORMAT = stringPreferencesKey("default_image_output_format")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
        val REMEMBER_LAST_MODEL = booleanPreferencesKey("remember_last_model")
        val LAST_USED_MODEL = stringPreferencesKey("last_used_model")
        // Assistant settings
        val ASSISTANT_ENABLED = booleanPreferencesKey("assistant_enabled")
        val ASSISTANT_VOICE_ENABLED = booleanPreferencesKey("assistant_voice_enabled")
        val ASSISTANT_TTS_ENABLED = booleanPreferencesKey("assistant_tts_enabled")
        val BEAUTIFUL_MODEL_NAMES = booleanPreferencesKey("beautiful_model_names")
        val ALWAYS_SHOW_THINKING = booleanPreferencesKey("always_show_thinking")
        val SHOW_TOKEN_COUNTER = booleanPreferencesKey("show_token_counter")
        // Font settings
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE_SCALE = stringPreferencesKey("font_size_scale")
        // Web search settings
        val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        val WEB_SEARCH_PROVIDER = stringPreferencesKey("web_search_provider")
        val SEARXNG_BASE_URL = stringPreferencesKey("searxng_base_url")
        val WEB_SEARCH_MAX_RESULTS = stringPreferencesKey("web_search_max_results")
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
     * Static Material 3 palette options used when dynamic color is off or unavailable.
     */
    enum class ThemePalette {
        VIOLET,
        OCEAN,
        JADE,
        SUNSET,
        ROSE,
        AMBER,
        GRAPHITE
    }

    /**
     * Material 3 chat bubble shape families.
     */
    enum class ChatBubbleStyle {
        EXPRESSIVE,
        ROUNDED,
        COMPACT,
        GEOMETRIC
    }

    /**
     * Material Expressive control shape intensity.
     */
    enum class ControlShapeStyle {
        CLASSIC,
        BALANCED,
        EXPRESSIVE
    }

    /**
     * Preferred Material Expressive shape token for page-specific buttons.
     */
    enum class ComponentButtonShape {
        SYSTEM,
        COOKIE,
        COOKIE_SOFT,
        CLOVER,
        FLOWER,
        PUFFY,
        SOFT_BURST
    }

    /**
     * Default values for preferences.
     */
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant."
        val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        val DEFAULT_THEME_PALETTE = ThemePalette.VIOLET
        val DEFAULT_CHAT_BUBBLE_STYLE = ChatBubbleStyle.EXPRESSIVE
        val DEFAULT_CONTROL_SHAPE_STYLE = ControlShapeStyle.BALANCED
        val DEFAULT_COMPONENT_BUTTON_SHAPE = ComponentButtonShape.SYSTEM
        const val DEFAULT_DYNAMIC_COLOR_ENABLED = true
        const val DEFAULT_HAPTICS_ENABLED = true
        const val DEFAULT_NOTIFICATIONS_ENABLED = false
        const val DEFAULT_AI_GENERATED_TITLES_ENABLED = true
        val DEFAULT_REASONING_EFFORT = ReasoningEffort.HIGH
        const val DEFAULT_REMEMBER_LAST_MODEL = true
        const val DEFAULT_IMAGE_GENERATION_MODEL = "codex/gpt-image-2-medium"
        const val DEFAULT_IMAGE_OUTPUT_FORMAT = "png"
        val SUPPORTED_IMAGE_OUTPUT_FORMATS = listOf("png", "jpeg", "webp")
        const val DEFAULT_BEAUTIFUL_MODEL_NAMES = true
        const val DEFAULT_FONT_FAMILY = "Roboto Flex"
        const val DEFAULT_FONT_SIZE_SCALE = "Default"
        const val DEFAULT_FONT_SIZE_SCALE_VALUE = 1.0f
        const val DEFAULT_SEARXNG_BASE_URL = "https://searx.be"
        const val DEFAULT_WEB_SEARCH_MAX_RESULTS = 5
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

    // ========== Theme Palette ==========

    /**
     * Get the selected static Material 3 color palette as a Flow.
     */
    val themePalette: Flow<ThemePalette> = dataStore.data.map { preferences ->
        val paletteName = preferences[Keys.THEME_PALETTE] ?: DEFAULT_THEME_PALETTE.name
        try {
            ThemePalette.valueOf(paletteName)
        } catch (e: IllegalArgumentException) {
            DEFAULT_THEME_PALETTE
        }
    }

    /**
     * Set the static Material 3 color palette.
     */
    suspend fun setThemePalette(palette: ThemePalette) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_PALETTE] = palette.name
        }
    }

    // ========== Chat Bubble Style ==========

    /**
     * Get the selected chat bubble shape family as a Flow.
     */
    val chatBubbleStyle: Flow<ChatBubbleStyle> = dataStore.data.map { preferences ->
        val styleName = preferences[Keys.CHAT_BUBBLE_STYLE] ?: DEFAULT_CHAT_BUBBLE_STYLE.name
        try {
            ChatBubbleStyle.valueOf(styleName)
        } catch (e: IllegalArgumentException) {
            DEFAULT_CHAT_BUBBLE_STYLE
        }
    }

    /**
     * Set the chat bubble shape family.
     */
    suspend fun setChatBubbleStyle(style: ChatBubbleStyle) {
        dataStore.edit { preferences ->
            preferences[Keys.CHAT_BUBBLE_STYLE] = style.name
        }
    }

    // ========== Control Shape Style ==========

    /**
     * Get the selected Material Expressive control shape intensity as a Flow.
     */
    val controlShapeStyle: Flow<ControlShapeStyle> = dataStore.data.map { preferences ->
        val styleName = preferences[Keys.CONTROL_SHAPE_STYLE] ?: DEFAULT_CONTROL_SHAPE_STYLE.name
        try {
            ControlShapeStyle.valueOf(styleName)
        } catch (e: IllegalArgumentException) {
            DEFAULT_CONTROL_SHAPE_STYLE
        }
    }

    /**
     * Set the Material Expressive control shape intensity.
     */
    suspend fun setControlShapeStyle(style: ControlShapeStyle) {
        dataStore.edit { preferences ->
            preferences[Keys.CONTROL_SHAPE_STYLE] = style.name
        }
    }

    val mainButtonShape: Flow<ComponentButtonShape> = dataStore.data.map { preferences ->
        preferences[Keys.MAIN_BUTTON_SHAPE].toComponentButtonShape()
    }

    suspend fun setMainButtonShape(shape: ComponentButtonShape) {
        dataStore.edit { preferences ->
            preferences[Keys.MAIN_BUTTON_SHAPE] = shape.name
        }
    }

    val chatButtonShape: Flow<ComponentButtonShape> = dataStore.data.map { preferences ->
        preferences[Keys.CHAT_BUTTON_SHAPE].toComponentButtonShape()
    }

    suspend fun setChatButtonShape(shape: ComponentButtonShape) {
        dataStore.edit { preferences ->
            preferences[Keys.CHAT_BUTTON_SHAPE] = shape.name
        }
    }

    private fun String?.toComponentButtonShape(): ComponentButtonShape {
        return try {
            ComponentButtonShape.valueOf(this ?: DEFAULT_COMPONENT_BUTTON_SHAPE.name)
        } catch (e: IllegalArgumentException) {
            DEFAULT_COMPONENT_BUTTON_SHAPE
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

    val chatHapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.CHAT_HAPTICS_ENABLED] ?: DEFAULT_HAPTICS_ENABLED
    }

    suspend fun setChatHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.CHAT_HAPTICS_ENABLED] = enabled }
    }

    val navigationHapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.NAVIGATION_HAPTICS_ENABLED] ?: DEFAULT_HAPTICS_ENABLED
    }

    suspend fun setNavigationHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.NAVIGATION_HAPTICS_ENABLED] = enabled }
    }

    val listHapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LIST_HAPTICS_ENABLED] ?: DEFAULT_HAPTICS_ENABLED
    }

    suspend fun setListHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.LIST_HAPTICS_ENABLED] = enabled }
    }

    val gestureHapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.GESTURE_HAPTICS_ENABLED] ?: DEFAULT_HAPTICS_ENABLED
    }

    suspend fun setGestureHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.GESTURE_HAPTICS_ENABLED] = enabled }
    }

    // ========== Notifications ==========

    /**
     * Get whether app notifications are enabled as a Flow.
     */
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.NOTIFICATIONS_ENABLED] ?: DEFAULT_NOTIFICATIONS_ENABLED
    }

    /**
     * Set whether app notifications are enabled.
     */
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    // ========== Reasoning Effort ==========

    /**
     * Get the reasoning effort setting as a Flow.
     */
    val reasoningEffort: Flow<ReasoningEffort> = dataStore.data.map { preferences ->
        ReasoningEffort.fromStoredValue(preferences[Keys.REASONING_EFFORT])
    }

    /**
     * Set the reasoning effort setting.
     */
    suspend fun setReasoningEffort(effort: ReasoningEffort) {
        dataStore.edit { preferences ->
            preferences[Keys.REASONING_EFFORT] = effort.name
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

    // ========== Image Generation Model ==========

    /**
     * Get the default image generation model used when any chat asks to create an image.
     */
    val defaultImageGenerationModel: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.DEFAULT_IMAGE_GENERATION_MODEL] ?: DEFAULT_IMAGE_GENERATION_MODEL
    }

    /**
     * Set the default image generation model.
     */
    suspend fun setDefaultImageGenerationModel(model: String) {
        dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_IMAGE_GENERATION_MODEL] = model.ifBlank { DEFAULT_IMAGE_GENERATION_MODEL }
        }
    }

    /**
     * Get the default output format used for generated images.
     */
    val defaultImageOutputFormat: Flow<String> = dataStore.data.map { preferences ->
        val format = preferences[Keys.DEFAULT_IMAGE_OUTPUT_FORMAT] ?: DEFAULT_IMAGE_OUTPUT_FORMAT
        format.takeIf { it in SUPPORTED_IMAGE_OUTPUT_FORMATS } ?: DEFAULT_IMAGE_OUTPUT_FORMAT
    }

    /**
     * Set the default image output format.
     */
    suspend fun setDefaultImageOutputFormat(format: String) {
        dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_IMAGE_OUTPUT_FORMAT] =
                format.lowercase().takeIf { it in SUPPORTED_IMAGE_OUTPUT_FORMATS } ?: DEFAULT_IMAGE_OUTPUT_FORMAT
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

    // ========== Remember Last Model ==========

    /**
     * Get whether to remember the last used model for new chats as a Flow.
     * When enabled, new conversations will use the last model the user selected.
     */
    val rememberLastModel: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.REMEMBER_LAST_MODEL] ?: DEFAULT_REMEMBER_LAST_MODEL
    }

    /**
     * Set whether to remember the last used model for new chats.
     */
    suspend fun setRememberLastModel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.REMEMBER_LAST_MODEL] = enabled
        }
    }

    /**
     * Get the last used model name as a Flow.
     * Empty string means no model has been stored yet.
     */
    val lastUsedModel: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.LAST_USED_MODEL] ?: ""
    }

    /**
     * Set the last used model name.
     */
    suspend fun setLastUsedModel(model: String) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_USED_MODEL] = model
        }
    }

    // ========== Assistant Settings ==========

    /**
     * Get whether the assistant is enabled as a Flow.
     * When enabled, MaterialChat can be set as the default digital assistant.
     */
    val assistantEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ASSISTANT_ENABLED] ?: true
    }

    /**
     * Set whether the assistant is enabled.
     */
    suspend fun setAssistantEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ASSISTANT_ENABLED] = enabled
        }
    }

    /**
     * Get whether voice input is enabled for the assistant as a Flow.
     */
    val assistantVoiceEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ASSISTANT_VOICE_ENABLED] ?: true
    }

    /**
     * Set whether voice input is enabled for the assistant.
     */
    suspend fun setAssistantVoiceEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ASSISTANT_VOICE_ENABLED] = enabled
        }
    }

    /**
     * Get whether text-to-speech is enabled for the assistant as a Flow.
     */
    val assistantTtsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ASSISTANT_TTS_ENABLED] ?: true
    }

    /**
     * Set whether text-to-speech is enabled for the assistant.
     */
    suspend fun setAssistantTtsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ASSISTANT_TTS_ENABLED] = enabled
        }
    }

    // ========== Beautiful Model Names ==========

    /**
     * Get whether beautiful model names display is enabled as a Flow.
     * When enabled, raw model identifiers are formatted into readable dual pill badges.
     */
    val beautifulModelNamesEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BEAUTIFUL_MODEL_NAMES] ?: DEFAULT_BEAUTIFUL_MODEL_NAMES
    }

    /**
     * Set whether beautiful model names display is enabled.
     */
    suspend fun setBeautifulModelNamesEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.BEAUTIFUL_MODEL_NAMES] = enabled
        }
    }

    // ========== Always Show Thinking ==========

    /**
     * Get whether thinking content is always shown expanded as a Flow.
     * When enabled, model reasoning content is expanded by default after completion.
     */
    val alwaysShowThinking: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ALWAYS_SHOW_THINKING] ?: false
    }

    /**
     * Set whether thinking content is always shown expanded.
     */
    suspend fun setAlwaysShowThinking(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ALWAYS_SHOW_THINKING] = enabled
        }
    }

    // ========== Font Settings ==========

    /**
     * Get the selected font family name as a Flow.
     */
    val fontFamily: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.FONT_FAMILY] ?: DEFAULT_FONT_FAMILY
    }

    /**
     * Set the font family name.
     */
    suspend fun setFontFamily(fontFamily: String) {
        dataStore.edit { preferences ->
            preferences[Keys.FONT_FAMILY] = fontFamily
        }
    }

    /**
     * Get the font size scale as a Flow (continuous value, 0.85 to 1.4).
     */
    val fontSizeScale: Flow<Float> = dataStore.data.map { preferences ->
        preferences[Keys.FONT_SIZE_SCALE]?.toFloatOrNull() ?: DEFAULT_FONT_SIZE_SCALE_VALUE
    }

    /**
     * Set the font size scale (continuous value).
     */
    suspend fun setFontSizeScale(scale: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.FONT_SIZE_SCALE] = scale.toString()
        }
    }

    // ========== Token Counter ==========

    /**
     * Get whether the token counter is shown in the message input as a Flow.
     * When enabled, shows live word count and estimated token count while typing.
     */
    val showTokenCounter: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.SHOW_TOKEN_COUNTER] ?: false
    }

    /**
     * Set whether the token counter is shown in the message input.
     */
    suspend fun setShowTokenCounter(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SHOW_TOKEN_COUNTER] = enabled
        }
    }

    /**
     * Clear all preferences (for testing or reset functionality).
     */
    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // ========== Web Search Settings ==========

    /**
     * Get whether web search is enabled as a Flow.
     */
    val webSearchEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.WEB_SEARCH_ENABLED] ?: false
    }

    /**
     * Set whether web search is enabled.
     */
    suspend fun setWebSearchEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.WEB_SEARCH_ENABLED] = enabled
        }
    }

    /**
     * Get the web search provider as a Flow.
     */
    val webSearchProvider: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.WEB_SEARCH_PROVIDER] ?: "EXA"
    }

    /**
     * Set the web search provider.
     */
    suspend fun setWebSearchProvider(provider: String) {
        dataStore.edit { preferences ->
            preferences[Keys.WEB_SEARCH_PROVIDER] = provider
        }
    }

    /**
     * Get the SearXNG base URL as a Flow.
     */
    val searxngBaseUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.SEARXNG_BASE_URL] ?: DEFAULT_SEARXNG_BASE_URL
    }

    /**
     * Set the SearXNG base URL.
     */
    suspend fun setSearxngBaseUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SEARXNG_BASE_URL] = url
        }
    }

    /**
     * Get the web search max results as a Flow.
     */
    val webSearchMaxResults: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.WEB_SEARCH_MAX_RESULTS]?.toIntOrNull() ?: DEFAULT_WEB_SEARCH_MAX_RESULTS
    }

    /**
     * Set the web search max results.
     */
    suspend fun setWebSearchMaxResults(maxResults: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.WEB_SEARCH_MAX_RESULTS] = maxResults.toString()
        }
    }
}
