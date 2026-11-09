package com.materialchat.assistant.actions

/** Only complete, explicit commands supply values. Everything else opens the clock UI. */
sealed interface ClockCommand {
    data class Alarm(val hour: Int, val minute: Int) : ClockCommand
    data class Timer(val seconds: Int) : ClockCommand
    data object ShowAlarms : ClockCommand
    data object ShowTimers : ClockCommand
}

/**
 * Recognises clock requests locally, before any provider call.
 *
 * Recognising a request here matters more than parsing it perfectly: an unrecognised
 * "set an alarm for 7am" is forwarded to the language model, which cannot create alarms and
 * answers as though it had. Every phrasing that clearly asks for an alarm or timer must
 * therefore resolve to a [ClockCommand] - either with values, or as [ClockCommand.ShowAlarms]
 * / [ClockCommand.ShowTimers] so the clock app opens and the user can finish the job.
 *
 * Values are only supplied when the request is unambiguous. A bare "7" is not: it could be
 * 7am or 7pm, so it opens the clock UI instead of silently creating an alarm at the wrong
 * time.
 */
object ClockCommandParser {
    private val ALARM_PREFIXES = listOf(
        Regex("^(?:set|create|make|add) (?:an? )?alarm\\b(.*)$"),
        Regex("^alarm (?:for|at)\\b(.*)$"),
        Regex("^wake me up (?:at|for)\\b(.*)$")
    )
    private val TIMER_PREFIXES = listOf(
        Regex("^(?:set|start|create) (?:a )?timer\\b(.*)$"),
        Regex("^timer (?:for )?(.*)$"),
        Regex("^(?:set|start|create) (?:a )?(.*?) timer$"),
        Regex("^(.*?) timer$")
    )
    private val TIME = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
    private val DURATION_PART = Regex("(\\d+) (hours?|hrs?|minutes?|mins?|seconds?|secs?)")

    fun parse(input: String): ClockCommand? {
        val text = input.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
            // Recognition often returns "7 a.m."; normalise it before matching.
            .replace(Regex("\\b([ap])\\.\\s?m\\.?"), "$1m")
            .removePrefix("please ").trimEnd('.', '!', '?', ',')

        for (pattern in ALARM_PREFIXES) {
            val match = pattern.matchEntire(text) ?: continue
            return alarm(match.groupValues[1].trim())
        }
        for (pattern in TIMER_PREFIXES) {
            val match = pattern.matchEntire(text) ?: continue
            return timer(match.groupValues[1].trim())
        }
        return when (text) {
            "open clock", "open alarms", "show alarms", "open alarm" -> ClockCommand.ShowAlarms
            "open timers", "show timers", "open timer", "timer" -> ClockCommand.ShowTimers
            else -> null
        }
    }

    private fun alarm(remainder: String): ClockCommand {
        val time = remainder.removePrefix("at ").removePrefix("for ").removePrefix("to ").trim()
        if (time.isEmpty()) return ClockCommand.ShowAlarms
        val match = TIME.matchEntire(time) ?: return ClockCommand.ShowAlarms
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        val period = match.groupValues[3]
        if (minute !in 0..59) return ClockCommand.ShowAlarms
        if (period.isNotEmpty()) {
            if (hour !in 1..12) return ClockCommand.ShowAlarms
            hour = hour % 12 + if (period == "pm") 12 else 0
        } else if (hour !in 0..23 || (hour in 1..12 && !match.groupValues[1].startsWith("0"))) {
            // 12-hour time without am/pm is ambiguous, so let the user confirm in the clock.
            return ClockCommand.ShowAlarms
        }
        return ClockCommand.Alarm(hour, minute)
    }

    private fun timer(remainder: String): ClockCommand {
        val duration = remainder.removePrefix("for ").removePrefix("of ").trim()
        if (duration.isEmpty()) return ClockCommand.ShowTimers
        val parts = DURATION_PART.findAll(duration).toList()
        if (parts.isEmpty() || parts.joinToString(" ") { it.value } != duration) {
            return ClockCommand.ShowTimers
        }
        // Repeating a unit ("1 minute 2 minutes") is a misrecognition, not a total.
        val units = parts.map { unitOf(it.groupValues[2]) }
        if (units.distinct().size != units.size) return ClockCommand.ShowTimers
        var total = 0L
        for ((index, part) in parts.withIndex()) {
            val amount = part.groupValues[1].toLongOrNull() ?: return ClockCommand.ShowTimers
            val multiplier = when (units[index]) {
                "hour" -> 3600L
                "minute" -> 60L
                else -> 1L
            }
            if (amount > Int.MAX_VALUE.toLong() / multiplier) return ClockCommand.ShowTimers
            total += amount * multiplier
            if (total > Int.MAX_VALUE) return ClockCommand.ShowTimers
        }
        return if (total > 0) ClockCommand.Timer(total.toInt()) else ClockCommand.ShowTimers
    }

    private fun unitOf(raw: String): String = when (raw) {
        "hour", "hours", "hr", "hrs" -> "hour"
        "minute", "minutes", "min", "mins" -> "minute"
        else -> "second"
    }
}
