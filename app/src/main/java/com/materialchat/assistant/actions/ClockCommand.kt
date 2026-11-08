package com.materialchat.assistant.actions

/** Only complete, explicit commands supply values. Everything else opens the clock UI. */
sealed interface ClockCommand {
    data class Alarm(val hour: Int, val minute: Int) : ClockCommand
    data class Timer(val seconds: Int) : ClockCommand
    data object ShowAlarms : ClockCommand
    data object ShowTimers : ClockCommand
}

object ClockCommandParser {
    fun parse(input: String): ClockCommand? {
        val text = input.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
            .removePrefix("please ").trimEnd('.', '!', '?')
        val alarm = Regex("(?:set|create) (?:an? )?alarm(?: (?:at|for) (.+))?").matchEntire(text)
        if (alarm != null) {
            val time = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").matchEntire(alarm.groupValues[1])
                ?: return ClockCommand.ShowAlarms
            var hour = time.groupValues[1].toInt()
            val minute = time.groupValues[2].ifEmpty { "0" }.toInt()
            val period = time.groupValues[3]
            if (minute !in 0..59) return ClockCommand.ShowAlarms
            if (period.isNotEmpty()) {
                if (hour !in 1..12) return ClockCommand.ShowAlarms
                hour = hour % 12 + if (period == "pm") 12 else 0
            } else if (hour !in 0..23 || (hour in 1..12 && !time.groupValues[1].startsWith("0"))) {
                return ClockCommand.ShowAlarms
            }
            return ClockCommand.Alarm(hour, minute)
        }
        val timer = Regex("(?:set|start|create) (?:a )?timer(?: for (.+))?").matchEntire(text)
        if (timer != null) {
            val duration = timer.groupValues[1]
            val parts = Regex("(\\d+) (hours?|minutes?|seconds?)").findAll(duration).toList()
            if (parts.isEmpty() || parts.joinToString(" ") { it.value } != duration ||
                parts.map { it.groupValues[2].removeSuffix("s") }.distinct().size != parts.size) {
                return ClockCommand.ShowTimers
            }
            var total = 0L
            for (part in parts) {
                val amount = part.groupValues[1].toLongOrNull() ?: return ClockCommand.ShowTimers
                val multiplier = when (part.groupValues[2].removeSuffix("s")) {
                    "hour" -> 3600
                    "minute" -> 60
                    else -> 1
                }
                if (amount > Int.MAX_VALUE.toLong() / multiplier) return ClockCommand.ShowTimers
                total += amount * multiplier
                if (total > Int.MAX_VALUE) return ClockCommand.ShowTimers
            }
            return if (total > 0) ClockCommand.Timer(total.toInt()) else ClockCommand.ShowTimers
        }
        return when (text) {
            "open clock", "open alarms", "show alarms" -> ClockCommand.ShowAlarms
            "open timers", "show timers" -> ClockCommand.ShowTimers
            else -> null
        }
    }
}
