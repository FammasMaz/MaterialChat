package com.materialchat.assistant.actions

import java.time.LocalTime

/** Only complete, explicit commands supply values. Everything else opens the clock UI. */
sealed interface ClockCommand {
    data class Alarm(val hour: Int, val minute: Int, val label: String? = null) : ClockCommand
    data class Timer(val seconds: Int, val label: String? = null) : ClockCommand
    data object ShowAlarms : ClockCommand
    data object ShowTimers : ClockCommand
}

/**
 * Recognises clock requests locally, before any provider call.
 *
 * Recognising a request here matters more than parsing it perfectly: an unrecognised
 * "set an alarm for 7am" costs a model round trip, and a model that misses the directive
 * protocol answers as though it had set something. Every phrasing that clearly asks for an
 * alarm or timer must therefore resolve to a [ClockCommand] - either with values, or as
 * [ClockCommand.ShowAlarms] / [ClockCommand.ShowTimers] so the clock app opens and the user
 * can finish the job.
 *
 * Values are only supplied when the request is unambiguous. A bare "7" is not: it could be
 * 7am or 7pm, so it opens the clock UI instead of silently creating an alarm at the wrong
 * time. Relative requests ("alarm in 20 minutes") are resolved against [LocalTime.now] unless
 * a time is supplied.
 */
object ClockCommandParser {
    private val ALARM_PREFIXES = listOf(
        Regex("^(?:set|create|make|add|put|schedule|set up) (?:an? |the |my |another )?(?:new )?alarm(?: clock)?\\b(.*)$"),
        Regex("^alarm (?=(?:for|at|in)\\b)(.*)$"),
        Regex("^wake me(?: up)? (?=(?:at|for|in|by)\\b)(.*)$")
    )
    private val ALARM_LISTS = listOf(
        // "turn off my 7 am alarm": whatever qualifies the alarm, the alarm list is the answer.
        Regex("^(?:cancel|dismiss|delete|remove|turn off|switch off|stop|disable|edit|change|snooze) (?:my |the |all |all my |all of my )?(?:[\\w: ]{1,30} )?alarms?\\b.*$"),
        Regex("^(?:show|list|open|check|see|view|display|tell me) (?:me )?(?:my |the |all |all my )?alarms?$"),
        Regex("^what alarms (?:do i have|are set|have i got)(?: set)?$"),
        Regex("^(?:do i have|have i got|are there) (?:any )?alarms?(?: set)?$"),
        Regex("^open (?:the )?(?:clock|alarm clock|clock app)$")
    )
    private val TIMER_PREFIXES = listOf(
        Regex("^(?:set|start|create|make|put|begin|run|set up|start up) (?:a |the |me a |up a |another )?(?:new |quick )?timer\\b(.*)$"),
        Regex("^timer (?:for |of )?(.*)$"),
        Regex("^(?:set|start|create|make|put|begin|set up) (?:a |the |me a |another )?(.+?) timer\\b(.*)$"),
        Regex("^(.+?) timer$")
    )
    private val TIMER_LISTS = listOf(
        Regex("^(?:cancel|stop|dismiss|delete|remove|pause|reset|check|show|open|see|view|list) (?:my |the |all |all my )?(?:current |running )?timers?$"),
        Regex("^(?:how much time|how long) (?:is )?(?:left|remaining) on (?:my |the )?timer$")
    )
    private val TIME_THEN_LABEL = Regex("^(.*?) (?:for|to|called|named|labell?ed|so i can|so that i|that says) (.+)$")
    private val LABEL_THEN_TIME = Regex("^(.+) (at|for|in) (.+)$")

    fun parse(input: String, now: LocalTime = LocalTime.now()): ClockCommand? {
        val text = CommandText.normalise(input)

        for (pattern in ALARM_LISTS) if (pattern.matches(text)) return ClockCommand.ShowAlarms
        for (pattern in TIMER_LISTS) if (pattern.matches(text)) return ClockCommand.ShowTimers
        // "what time is my alarm in Paris" is a question for the model, not a command.
        if (CommandText.isQuestion(text)) return null
        for (pattern in ALARM_PREFIXES) {
            val match = pattern.matchEntire(text) ?: continue
            return alarm(match.groupValues[1].trim(), now)
        }
        for (pattern in TIMER_PREFIXES) {
            val match = pattern.matchEntire(text) ?: continue
            return timer(match.groupValues[1].trim(), match.groupValues.getOrElse(2) { "" })
        }
        return when (text) {
            "open clock", "open alarms", "show alarms", "open alarm", "alarms" -> ClockCommand.ShowAlarms
            "open timers", "show timers", "open timer", "timer", "timers" -> ClockCommand.ShowTimers
            else -> null
        }
    }

    private fun alarm(remainder: String, now: LocalTime): ClockCommand {
        var text = remainder
        for (prefix in listOf("at ", "for ", "to ", "on ", "by ")) text = text.removePrefix(prefix).trim()
        if (text.isEmpty()) return ClockCommand.ShowAlarms
        resolveAlarm(text, null, now)?.let { return it }
        // "7 am for the gym" / "the gym at 7 am": a label on either side of the time.
        TIME_THEN_LABEL.matchEntire(text)?.let { match ->
            resolveAlarm(match.groupValues[1].trim(), match.groupValues[2], now)?.let { return it }
        }
        LABEL_THEN_TIME.matchEntire(text)?.let { match ->
            val time = if (match.groupValues[2] == "in") "in " + match.groupValues[3] else match.groupValues[3]
            resolveAlarm(time, match.groupValues[1], now)?.let { return it }
        }
        return ClockCommand.ShowAlarms
    }

    /** Null when [timeText] is not a time at all, so another split of the text can be tried. */
    private fun resolveAlarm(timeText: String, label: String?, now: LocalTime): ClockCommand? {
        // "7 am tomorrow" is the next 7 am, which is exactly what ACTION_SET_ALARM creates.
        val text = timeText.removeSuffix(" tomorrow").removeSuffix(" today").trim()
        if (text.startsWith("in ")) {
            val seconds = TimeExpressions.durationSeconds(text.removePrefix("in ")) ?: return null
            var target = now.plusSeconds(seconds)
            if (target.second > 0 || target.nano > 0) target = target.plusMinutes(1)
            return ClockCommand.Alarm(target.hour, target.minute, CommandText.label(label))
        }
        return when (val time = TimeExpressions.clockTime(text)) {
            is TimeExpressions.ClockTime.Exact -> ClockCommand.Alarm(time.hour, time.minute, CommandText.label(label))
            TimeExpressions.ClockTime.Ambiguous -> ClockCommand.ShowAlarms
            TimeExpressions.ClockTime.Invalid -> null
        }
    }

    private fun timer(remainder: String, extra: String): ClockCommand {
        var text = remainder.removePrefix("for ").removePrefix("of ").removePrefix("to ").trim()
        var label: String? = null
        val trailing = extra.trim()
        if (text.isEmpty() && trailing.isEmpty()) return ClockCommand.ShowTimers
        TIME_THEN_LABEL.matchEntire(text)?.let {
            if (TimeExpressions.durationSeconds(it.groupValues[1].trim()) != null) {
                text = it.groupValues[1].trim()
                label = it.groupValues[2]
            }
        }
        val trailingDuration = trailing.removePrefix("for ").removePrefix("of ").trim()
        if (TimeExpressions.durationSeconds(text) == null && TimeExpressions.durationSeconds(trailingDuration) != null) {
            // "set a pasta timer for 10 minutes": the name comes before the duration.
            label = text
            text = trailingDuration
        } else if (label == null) {
            for (prefix in listOf("for ", "called ", "named ", "to ")) {
                if (trailing.startsWith(prefix)) {
                    label = trailing.removePrefix(prefix)
                    break
                }
            }
        }
        if (text.isEmpty()) return ClockCommand.ShowTimers
        val seconds = TimeExpressions.durationSeconds(text) ?: return ClockCommand.ShowTimers
        return ClockCommand.Timer(seconds.toInt(), CommandText.label(label))
    }
}
