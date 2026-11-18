package com.materialchat.assistant.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Whether the platform lets this assistant read the screen, and where to send the user to
 * change that.
 *
 * Before Android 16 there is no query: the screenshot and the structure simply arrive or not,
 * depending on "Use screenshot" / "Use text from screen" under Settings > Apps > Default apps >
 * Digital assistant app, which `Settings.ACTION_VOICE_INPUT_SETTINGS` opens. Android 16 (API 37)
 * adds `android.app.voiceinteraction.VoiceInteractionManager` with a read-screen-context
 * request state and a request intent. compileSdk is 36, so that class is reached by reflection
 * and only on API 37+; every failure degrades to the pre-16 behaviour.
 */
object ScreenContextAccess {
    private const val TAG = "MaterialChatAssistant"
    private const val MANAGER_CLASS = "android.app.voiceinteraction.VoiceInteractionManager"

    /** First API level with `VoiceInteractionManager` and its READ_SCREEN_CONTEXT_REQUEST_STATE_* constants. */
    const val API_READ_SCREEN_CONTEXT = 37

    /** Mirrors `VoiceInteractionManager.READ_SCREEN_CONTEXT_REQUEST_STATE_*` (0, 1, 2). */
    enum class RequestState { GRANTED, REQUESTABLE, UNREQUESTABLE }

    /** The platform's state, or null below API 37 or when it cannot be read. */
    fun requestState(context: Context): RequestState? {
        if (Build.VERSION.SDK_INT < API_READ_SCREEN_CONTEXT) return null
        return runCatching {
            val manager = manager(context) ?: return null
            val state = manager.javaClass.getMethod("getReadScreenContextRequestState").invoke(manager) as Int
            fromPlatform(state)
        }.onFailure { Log.w(TAG, "read-screen-context state unavailable", it) }.getOrNull()
    }

    /** READ_SCREEN_CONTEXT_REQUEST_STATE_GRANTED = 0, _REQUESTABLE = 1, _UNREQUESTABLE = 2. */
    internal fun fromPlatform(state: Int): RequestState? = when (state) {
        0 -> RequestState.GRANTED
        1 -> RequestState.REQUESTABLE
        2 -> RequestState.UNREQUESTABLE
        else -> null
    }

    /**
     * The status line for a question that wanted the screen when nothing arrived, worded for
     * what the platform reports: a grant that still delivered nothing means the screen itself
     * was withheld (secure window, lock screen); a block cannot be changed from the app.
     */
    fun notice(state: RequestState?): String = when (state) {
        RequestState.GRANTED ->
            "Nothing arrived from this screen \u2014 it may be private (a secure app or the lock screen). Try again from the app you were looking at."
        RequestState.UNREQUESTABLE ->
            "Screen context is blocked for MaterialChat by the system \u2014 check Settings \u203a Apps \u203a Default apps \u203a Digital assistant app."
        null, RequestState.REQUESTABLE -> ScreenContextPrompt.UNAVAILABLE_NOTICE
    }

    /**
     * On API 37+, the system's own permission dialog while it may still be shown
     * (`VoiceInteractionManager.createRequestReadScreenContextIntent`). The platform requires
     * it to be started with `startActivityForResult`, so only an activity (the settings
     * panel) can use it; null below API 37, when the state is not REQUESTABLE, or on failure.
     */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < API_READ_SCREEN_CONTEXT) return null
        if (requestState(context) != RequestState.REQUESTABLE) return null
        return runCatching {
            val manager = manager(context) ?: return null
            manager.javaClass.getMethod("createRequestReadScreenContextIntent").invoke(manager) as? Intent
        }.onFailure { Log.w(TAG, "read-screen-context request intent unavailable", it) }.getOrNull()
    }

    /**
     * Where the switches live, best first: the digital-assistant settings page
     * (`Settings.ACTION_VOICE_INPUT_SETTINGS`), then plain Settings. Intents an activity
     * resolves come first; when package visibility hides the Settings app from the query the
     * unfiltered list is returned, since a launch failure is caught by the caller anyway.
     */
    fun settingsIntents(context: Context): List<Intent> {
        val candidates = listOf(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        val packageManager = context.packageManager
        val resolvable = candidates.filter { runCatching { it.resolveActivity(packageManager) != null }.getOrDefault(false) }
        return resolvable.ifEmpty { candidates }
    }

    /** Opens the best available settings entry from a non-activity context. Returns false when none opened. */
    fun openSettings(context: Context): Boolean {
        for (intent in settingsIntents(context)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    private fun manager(context: Context): Any? {
        val type = runCatching { Class.forName(MANAGER_CLASS) }.getOrNull() ?: return null
        return context.getSystemService(type)
    }
}
