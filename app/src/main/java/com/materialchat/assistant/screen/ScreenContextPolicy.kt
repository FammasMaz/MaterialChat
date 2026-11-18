package com.materialchat.assistant.screen

import com.materialchat.data.local.preferences.AssistantScreenContextSettings
import com.materialchat.data.local.preferences.ScreenContextMode

/** What to do with the screen capture for one question. */
enum class ScreenContextDecision {
    /** Send the question without any screen data, and do not tell the user anything. */
    SKIP,
    /** Attach the capture (screenshot and/or text) to this question. */
    ATTACH,
    /** The question wants the screen but nothing arrived from the platform: say how to enable it. */
    UNAVAILABLE
}

/**
 * The decision table behind "Use screen context". Pure so it is tested as a table.
 *
 * @param mode The user's setting.
 * @param asksAboutScreen The utterance refers to the screen ([ScreenContextTrigger]) or the
 * model asked for it through a directive.
 * @param contextAvailable A capture with at least one usable half arrived ([usable]).
 * @param declined The user removed the screen chip for this question.
 */
object ScreenContextPolicy {
    fun decide(
        mode: ScreenContextMode,
        asksAboutScreen: Boolean,
        contextAvailable: Boolean,
        declined: Boolean
    ): ScreenContextDecision = when {
        mode == ScreenContextMode.NEVER -> ScreenContextDecision.SKIP
        declined -> ScreenContextDecision.SKIP
        contextAvailable && (asksAboutScreen || mode == ScreenContextMode.ALWAYS) -> ScreenContextDecision.ATTACH
        asksAboutScreen -> ScreenContextDecision.UNAVAILABLE
        else -> ScreenContextDecision.SKIP
    }

    /** Whether [context] has a half that the settings allow sending. */
    fun usable(context: ScreenContext, settings: AssistantScreenContextSettings): Boolean =
        (settings.includeScreenshot && context.screenshot != null) ||
            (settings.includeScreenText && (!context.text.isNullOrBlank() || context.webUri != null))
}
