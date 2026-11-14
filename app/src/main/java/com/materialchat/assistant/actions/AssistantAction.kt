package com.materialchat.assistant.actions

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Something the assistant can actually do on this phone, whichever way it was requested:
 * recognised locally from the utterance ([AssistantCommandParser]) or asked for by the model
 * through a directive ([ActionDirectives]). Both paths execute through the same handlers.
 */
sealed interface AssistantAction {
    /** Handed to the clock app through the assistant-authorised launch; the clock UI confirms. */
    data class Clock(val command: ClockCommand) : AssistantAction

    /** Performed by [DeviceActionHandler], which reports the real outcome. */
    data class Device(val action: DeviceAction) : AssistantAction
}

/**
 * First pass over every utterance, before any provider call, on every turn of a conversation.
 *
 * Obvious commands are resolved here so they cost no model round trip and work even when no
 * provider is configured. Anything this misses goes to the model, which can still request the
 * same actions through [ActionDirectives]. Unambiguous requests resolve to a command with
 * values; recognised-but-ambiguous clock requests open the relevant UI rather than guessing;
 * everything else returns null so the model can ask or answer.
 */
object AssistantCommandParser {
    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): AssistantAction? {
        ClockCommandParser.parse(input, now.toLocalTime())?.let { return AssistantAction.Clock(it) }
        ReminderParser.parse(input, now)?.let { return it }
        DeviceActionParser.parse(input)?.let { return AssistantAction.Device(it) }
        return null
    }
}

/**
 * Reminders have no universal Android API (no public reminder intent exists on API 26-36), so
 * a reminder becomes whichever supported thing matches the request best:
 *
 * - "remind me in 20 minutes to check the oven" -> a labelled clock timer (precise countdown).
 * - "remind me at 5 pm to call mom" -> a labelled clock alarm (`ACTION_SET_ALARM`).
 * - "remind me tomorrow [at 9 am] to ..." -> a calendar event via `ACTION_INSERT`, all-day when
 *   no time is given; the user confirms it in the calendar app.
 *
 * A reminder with no time at all ("remind me to buy milk") and an ambiguous time ("at 7" with
 * no am/pm) return null on purpose: the model, which knows the conversation, asks when, and
 * the follow-up answer is turned into the same action through a directive.
 */
object ReminderParser {
    private val PREFIXES = listOf(
        Regex("^remind me (?:to |that i (?:need|have|should) to |that i |that |about |of )?(.+)$"),
        Regex("^(?:set|create|add|make|schedule) (?:a |me a |up a )?(?:new |quick )?reminder (?:to |for me to |for |about |that |of )?(.+)$"),
        Regex("^reminder (?:to |for |about |of )?(.+)$")
    )
    private val DAY_FIRST = Regex("^(tomorrow|today|tonight)(?: at (.+?))? (?:to|that|about) (.+)$")
    private val TIME_FIRST = Regex("^(?:(at|in|for) )?(.+?) (?:to|that|about) (.+)$")
    private val DAY_AT = Regex("^(.+) (tomorrow|today|tonight) at (.+)$")
    private val AT_TIME = Regex("^(.+) at (.+)$")
    private val RELATIVE = Regex("^(.+) in (.+)$")
    private val DAY_ONLY = Regex("^(.+) (tomorrow|today|tonight)$")
    private val HAS_PERIOD = Regex("\\b(?:am|pm)\\b|morning|afternoon|evening|night")

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): AssistantAction? {
        val text = CommandText.normalise(input)
        val body = PREFIXES.firstNotNullOfOrNull { it.matchEntire(text)?.groupValues?.get(1)?.trim() }
            ?: return null
        if (body.isEmpty() || CommandText.isQuestion(body)) return null

        DAY_FIRST.matchEntire(body)?.let { match ->
            val (day, whenText, title) = match.destructured
            return if (whenText.isEmpty()) dayOnly(day, title, now) else atTime(whenText, title, day, now)
        }
        TIME_FIRST.matchEntire(body)?.let { match ->
            val (keyword, whenText, title) = match.destructured
            val action = when {
                keyword == "in" -> relative(whenText, title)
                keyword == "at" -> atTime(whenText, title, null, now)
                TimeExpressions.durationSeconds(whenText) != null -> relative(whenText, title)
                TimeExpressions.clockTime(whenText) is TimeExpressions.ClockTime.Exact -> atTime(whenText, title, null, now)
                else -> null
            }
            // With an explicit keyword this was the time; without one it may just be prose.
            if (action != null || keyword.isNotEmpty()) return action
        }
        DAY_AT.matchEntire(body)?.let { match ->
            val (title, day, whenText) = match.destructured
            return atTime(whenText, title, day, now)
        }
        AT_TIME.matchEntire(body)?.let { match ->
            return atTime(match.groupValues[2], match.groupValues[1], null, now)
        }
        RELATIVE.matchEntire(body)?.let { match ->
            relative(match.groupValues[2], match.groupValues[1])?.let { return it }
        }
        DAY_ONLY.matchEntire(body)?.let { match ->
            return dayOnly(match.groupValues[2], match.groupValues[1], now)
        }
        return null
    }

    private fun relative(duration: String, title: String): AssistantAction? {
        val seconds = TimeExpressions.durationSeconds(duration) ?: return null
        return AssistantAction.Clock(ClockCommand.Timer(seconds.toInt(), CommandText.label(title)))
    }

    private fun atTime(whenText: String, title: String, day: String?, now: LocalDateTime): AssistantAction? {
        var timeText = whenText.trim()
        var dayWord = day
        for (word in listOf("tomorrow", "today", "tonight")) {
            if (timeText.endsWith(" $word")) {
                timeText = timeText.removeSuffix(" $word").trim()
                dayWord = dayWord ?: word
            }
        }
        if (dayWord == "tonight" && !HAS_PERIOD.containsMatchIn(timeText)) timeText = "$timeText tonight"
        val time = TimeExpressions.clockTime(timeText) as? TimeExpressions.ClockTime.Exact ?: return null
        val label = CommandText.label(title) ?: return null
        return if (dayWord == "tomorrow") {
            AssistantAction.Device(CalendarEvents.build(label, now.toLocalDate().plusDays(1), time.hour, time.minute))
        } else {
            AssistantAction.Clock(ClockCommand.Alarm(time.hour, time.minute, label))
        }
    }

    private fun dayOnly(day: String, title: String, now: LocalDateTime): AssistantAction? {
        val label = CommandText.label(title) ?: return null
        val date = if (day == "tomorrow") now.toLocalDate().plusDays(1) else now.toLocalDate()
        return AssistantAction.Device(CalendarEvents.build(label, date, null, 0))
    }
}

/** Builds calendar-event actions in the device's time zone. */
internal object CalendarEvents {
    fun build(
        title: String?,
        day: LocalDate,
        hour: Int?,
        minute: Int,
        durationMinutes: Int = 30
    ): DeviceAction.CreateCalendarEvent {
        val zone = ZoneId.systemDefault()
        return if (hour == null) {
            DeviceAction.CreateCalendarEvent(
                title = title,
                beginMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                endMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                allDay = true
            )
        } else {
            val begin = day.atTime(hour, minute).atZone(zone)
            DeviceAction.CreateCalendarEvent(
                title = title,
                beginMillis = begin.toInstant().toEpochMilli(),
                endMillis = begin.plusMinutes(durationMinutes.toLong()).toInstant().toEpochMilli(),
                allDay = false
            )
        }
    }
}

/**
 * Normalisation shared by the local parsers. Recognised speech arrives with filler the
 * patterns should not have to know about ("ok now can you set an alarm for 7 am please"),
 * especially mid-conversation.
 */
internal object CommandText {
    private val LEADING = listOf(
        "ok google", "okay google", "hey google", "hey assistant", "hey siri",
        "please", "ok", "okay", "hey", "hi", "so", "now", "also", "and", "then", "just", "well",
        "can you", "could you", "would you", "will you", "can you please", "could you please",
        "i want you to", "i need you to", "i want to", "i need to", "i'd like to", "i would like to",
        "i'd like you to", "i would like you to", "go ahead and", "let's", "actually"
    )
    private val TRAILING = listOf("please", "for me", "thanks", "thank you", "now", "right now", "ok", "okay")
    private val QUESTION = Regex(
        "^(?:what|what's|whats|how|why|when|where|who|which|is|are|was|were|do|does|did|can|could|should|would|will|if|whether|explain)\\b"
    )

    fun normalise(input: String): String {
        var text = input.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            // Recognition often returns "7 a.m."; normalise it before matching.
            .replace(Regex("\\b([ap])\\.\\s?m\\.?"), "$1m")
            .trimEnd('.', '!', '?', ',', ';').trim()
        var changed = true
        while (changed) {
            changed = false
            for (filler in LEADING) {
                // "Ok, and also can you ...": filler is often followed by a comma.
                if (text.startsWith("$filler ") || text.startsWith("$filler,")) {
                    text = text.removePrefix(filler).trimStart(',', ';', ' ')
                    changed = true
                }
            }
            for (filler in TRAILING) {
                if (text.endsWith(" $filler")) {
                    text = text.removeSuffix(" $filler").trim()
                    changed = true
                }
            }
            val stripped = text.trimEnd('.', '!', '?', ',', ';').trim()
            if (stripped != text) {
                text = stripped
                changed = true
            }
        }
        return text
    }

    /** Questions about actions are answered by the model, never performed. */
    fun isQuestion(text: String): Boolean = QUESTION.containsMatchIn(text)

    /** A short label for an alarm, timer or event; null when there is nothing usable. */
    fun label(raw: String?): String? {
        val text = raw?.trim()?.trim('"', '\'', '.', ',', '!')?.replace(Regex("\\s+"), " ") ?: return null
        if (text.isEmpty()) return null
        return text.take(100).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}

/** Time and duration expressions as speech recognition produces them. */
internal object TimeExpressions {
    sealed interface ClockTime {
        data class Exact(val hour: Int, val minute: Int) : ClockTime
        /** A 12-hour time without am/pm: never guess, let the user confirm. */
        data object Ambiguous : ClockTime
        data object Invalid : ClockTime
    }

    private val ONES = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90
    )
    private val HOUR_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12
    )
    // The word alternative is lazy so "five minutes" is read as five + minutes, not as the
    // number "five minute" followed by the unit "s".
    private val DURATION_PART = Regex(
        "(\\d+|[a-z]+?(?:[ -][a-z]+?)??)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)(?![a-z])"
    )
    private val TIME = Regex(
        "^(\\d{1,2}|[a-z]+)(?::(\\d{2}))?(?:\\s*(am|pm|in the morning|in the afternoon|in the evening|at night|tonight|this morning|this afternoon|this evening))?$"
    )

    /**
     * "1 hour 30 minutes", "1h30m", "five minutes", "half an hour" -> seconds. Null unless the
     * whole text is a duration, so stray words ("5 minutes or so") are never silently dropped.
     */
    fun durationSeconds(raw: String): Long? {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace("an hour and a half", "90 minutes")
            .replace("one and a half hours", "90 minutes")
            .replace("a minute and a half", "90 seconds")
            .replace("half an hour", "30 minutes")
            .replace("half hour", "30 minutes")
            .replace("quarter of an hour", "15 minutes")
            .replace("a quarter hour", "15 minutes")
            .replace(Regex("\\band\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null
        val parts = DURATION_PART.findAll(text).toList()
        if (parts.isEmpty()) return null
        if (parts.joinToString("") { it.value.replace(" ", "") } != text.replace(" ", "")) return null
        // Repeating a unit ("1 minute 2 minutes") is a misrecognition, not a total.
        val units = parts.map { unitOf(it.groupValues[2]) }
        if (units.distinct().size != units.size) return null
        var total = 0L
        for ((index, part) in parts.withIndex()) {
            val amountText = part.groupValues[1]
            val amount = amountText.toLongOrNull() ?: wordNumber(amountText) ?: return null
            val multiplier = when (units[index]) {
                "hour" -> 3600L
                "minute" -> 60L
                else -> 1L
            }
            if (amount > Int.MAX_VALUE.toLong() / multiplier) return null
            total += amount * multiplier
            if (total > Int.MAX_VALUE) return null
        }
        return total.takeIf { it > 0 }
    }

    /** "7 am", "7:30pm", "19:00", "seven in the morning", "noon" -> a time of day. */
    fun clockTime(raw: String): ClockTime {
        val text = raw.trim().lowercase(Locale.ROOT)
            .replace(Regex("\\b([ap])\\.\\s?m\\.?"), "$1m")
            .replace(Regex("\\bo'?clock\\b"), "")
            .replace(Regex("\\b(am|pm) (?:tonight|today|this morning|this afternoon|this evening)$"), "$1")
            .replace(Regex("\\s+"), " ").trim()
        when (text) {
            "noon", "midday", "12 noon" -> return ClockTime.Exact(12, 0)
            "midnight", "12 midnight" -> return ClockTime.Exact(0, 0)
        }
        val match = TIME.matchEntire(text) ?: return ClockTime.Invalid
        val hourText = match.groupValues[1]
        val hour = hourText.toIntOrNull() ?: HOUR_WORDS[hourText] ?: return ClockTime.Invalid
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        if (minute !in 0..59) return ClockTime.Invalid
        val period = when (match.groupValues[3]) {
            "" -> null
            "am", "in the morning", "this morning" -> "am"
            else -> "pm"
        }
        if (period != null) {
            if (hour !in 1..12) return ClockTime.Invalid
            return ClockTime.Exact(hour % 12 + if (period == "pm") 12 else 0, minute)
        }
        if (hour !in 0..23) return ClockTime.Invalid
        // 12-hour time without am/pm is ambiguous, so let the user confirm in the clock.
        if (hour in 1..12 && !hourText.startsWith("0")) return ClockTime.Ambiguous
        return ClockTime.Exact(hour, minute)
    }

    private fun wordNumber(text: String): Long? {
        val tokens = text.split(' ', '-').filter { it.isNotEmpty() }
        return when (tokens.size) {
            1 -> (ONES[tokens[0]] ?: TENS[tokens[0]])?.toLong()
            2 -> {
                val tens = TENS[tokens[0]] ?: return null
                val ones = ONES[tokens[1]] ?: return null
                if (ones in 1..9) (tens + ones).toLong() else null
            }
            else -> null
        }
    }

    private fun unitOf(raw: String): String = when (raw) {
        "hour", "hours", "hr", "hrs", "h" -> "hour"
        "minute", "minutes", "min", "mins", "m" -> "minute"
        else -> "second"
    }
}
