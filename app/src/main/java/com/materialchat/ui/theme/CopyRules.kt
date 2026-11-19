package com.materialchat.ui.theme

/**
 * The copy rules every fixed user-facing string in the app follows, and the constants the
 * rulings fix (W7 part 6; W5/W6 orchestrator rulings, not re-decided here).
 *
 * Rules:
 * - The retry verb is [TRY_AGAIN], never "Retry": as an action label, as a resting line, and
 *   inside a notice sentence ("... and try again.").
 * - A failure line starts with [FAILURE_VERB] ("Could not save backup"), never "Failed to",
 *   "Unable to" or "Couldn't" ([BANNED_FAILURE_VERBS]); [failureLine] builds one.
 * - Period rule: a snackbar or any other one-phrase line (an action label, a phase line, a
 *   status line, a chip label) is not a sentence and carries no trailing period
 *   ([isPhrase]); a notice is one or more complete sentences and every sentence ends with a
 *   full stop ([isNotice]).
 * - Sentence case, addressed to the user, no "please", no exclamation mark, no exception or
 *   provider text, no engine or API names, and the typographic ellipsis [ELLIPSIS] rather
 *   than three ASCII periods.
 *
 * `ui/components/ErrorContentCopy`, chat's `ChatErrorCopy` and the assistant's
 * `AssistantCopy` alias the constants; `CopyRulesTest` pins the aliases and the predicates.
 * Pure Kotlin so the JVM tests run without a device.
 */
object CopyRules {
    /** The one retry verb: sentence case, no trailing period. */
    const val TRY_AGAIN = "Try again"

    /** Default error headline when a screen cannot name what failed. */
    const val GENERIC_HEADLINE = "Something went wrong"

    /** The generic error notice (a sentence pair: what happened, what to do). */
    const val REQUEST_FAILED = "Something went wrong. Try again."

    /**
     * HTTP 429 / quota exhaustion: waiting is the right move. The one line chat
     * (`ChatErrorCopy.RATE_LIMITED`) and the assistant (`AssistantCopy.RATE_LIMITED`) share
     * (W8 part 1); both alias it so the two notices cannot drift apart.
     */
    const val RATE_LIMITED = "The model is busy right now. Wait a moment and try again."

    /**
     * An `IOException` anywhere in the cause chain: no network. Shared by chat
     * (`ChatErrorCopy.NO_CONNECTION`) and the assistant (`AssistantCopy.NO_CONNECTION`).
     */
    const val NO_CONNECTION = "No connection. Check your network and try again."

    /**
     * The share-failure snackbar line, `failureLine("share")`: chat's share sheet and any
     * other share action show this and never the exception text (W6 audit #2).
     */
    const val COULD_NOT_SHARE = "Could not share"

    /** The verb a failure line opens with. */
    const val FAILURE_VERB = "Could not"

    /** Verb families a failure line must not open with. */
    val BANNED_FAILURE_VERBS: List<String> = listOf("Failed to", "Unable to", "Couldn't", "Cannot", "Can't")

    /** The typographic ellipsis (U+2026) phase lines end with; never three ASCII periods. */
    const val ELLIPSIS = "\u2026"

    /** "Could not " + [action], e.g. `failureLine("save backup")` -> "Could not save backup". */
    fun failureLine(action: String): String = "$FAILURE_VERB ${action.trim()}"

    /**
     * A one-phrase line (snackbar, action label, phase or status line, chip label): non-blank,
     * sentence case, no trailing period, no exclamation mark, no ASCII ellipsis, no "please",
     * and not opened by a banned failure verb.
     */
    fun isPhrase(text: String): Boolean =
        text.isNotBlank() &&
            text.first().isUpperCase() &&
            !text.trimEnd().endsWith(".") &&
            passesSharedRules(text)

    /**
     * A notice: one or more complete sentences, each ending with a full stop, sentence case,
     * no exclamation mark, no ASCII ellipsis, no "please", not opened by a banned failure verb.
     */
    fun isNotice(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !trimmed.first().isUpperCase() || !trimmed.endsWith(".")) return false
        if (!passesSharedRules(trimmed)) return false
        // Every sentence break is ". " followed by an upper-case start.
        val sentences = trimmed.dropLast(1).split(". ")
        return sentences.all { it.isNotBlank() && it.first().isUpperCase() }
    }

    private fun passesSharedRules(text: String): Boolean =
        !text.contains("...") &&
            !text.contains('!') &&
            !text.contains("please", ignoreCase = true) &&
            BANNED_FAILURE_VERBS.none { text.startsWith(it) }
}
