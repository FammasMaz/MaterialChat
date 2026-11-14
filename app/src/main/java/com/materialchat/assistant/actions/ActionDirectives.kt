package com.materialchat.assistant.actions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The model's way of performing device actions: a structured action protocol carried inside
 * the reply text.
 *
 * ## Why a directive and not tool calling
 *
 * The app's `ChatApiClient` only ever *disables* tools (`tools: []`, `tool_choice: none`); no
 * provider path parses `tool_calls` deltas, the Ollama, Copilot, Codex and Antigravity paths
 * have their own wire formats, and several configured providers are proxies of unknown tool
 * support. Teaching five streaming parsers to round-trip tool calls would be a large, risky
 * change for a single feature. A directive inside the text works identically with every
 * provider and model that can follow an instruction, streams through the existing pipeline,
 * and degrades safely: a model that ignores the protocol simply answers in words.
 *
 * ## Protocol
 *
 * The system prompt ([capabilityNotice]) lists the available actions and asks the model to
 * emit, on its own line, `<<action:{...json...}>>`. During streaming [strip] removes complete
 * directives and any unfinished one from the text that is shown and spoken; stripping is
 * monotone, so the append-only speech chunker keeps working on the stripped stream. When the
 * reply is complete [extract] parses every directive; each one is validated ([parse]) and
 * mapped onto the same [AssistantAction]s the local parsers produce, so execution goes through
 * the existing clock handoff and [DeviceActionHandler]. Unknown types, malformed JSON and
 * out-of-range values are ignored, counted as rejected, and never shown to the user.
 *
 * The model is told to describe the action as what it is doing rather than as done, because
 * clock and calendar actions only open the other app for confirmation (`EXTRA_SKIP_UI` stays
 * false). After execution the ViewModel appends the real outcome whenever the action failed
 * or the model said nothing, so the reply never claims something that did not happen.
 */
object ActionDirectives {
    const val OPEN = "<<action:"
    const val CLOSE = ">>"

    /** More than this in one reply is a confused model, not a plan. */
    const val MAX_PER_REPLY = 3

    data class Extraction(val text: String, val actions: List<AssistantAction>, val rejected: Int)

    /**
     * Text without directives. With [isFinal] false a trailing partial opener (and any code
     * fence or backtick the model may wrap it in) is held back too, so that every streamed
     * result is a prefix of the next one and of the final one.
     */
    fun strip(content: String, isFinal: Boolean = false): String = scan(content, isFinal, null).trimStart()

    fun extract(content: String, now: LocalDateTime): Extraction {
        val bodies = mutableListOf<String>()
        val text = scan(content, isFinal = true, bodies = bodies).trimStart()
        var rejected = 0
        val actions = bodies.mapNotNull { body -> parse(body, now).also { if (it == null) rejected++ } }
        return Extraction(text, actions.take(MAX_PER_REPLY), rejected)
    }

    /** One directive body (the JSON between the markers) to an action, or null if invalid. */
    fun parse(json: String, now: LocalDateTime): AssistantAction? {
        val element = try {
            Json.parseToJsonElement(json.trim())
        } catch (_: Exception) {
            return null
        }
        val obj = element as? JsonObject ?: return null
        val type = obj.string("type")?.lowercase(Locale.ROOT)?.trim() ?: return null
        return when (type) {
            "set_alarm" -> {
                val hour = obj.int("hour")?.takeIf { it in 0..23 } ?: return null
                val minute = (obj.int("minute") ?: 0).takeIf { it in 0..59 } ?: return null
                AssistantAction.Clock(ClockCommand.Alarm(hour, minute, CommandText.label(obj.string("label"))))
            }
            "set_timer" -> {
                val hours = obj.int("hours") ?: 0
                val minutes = obj.int("minutes") ?: 0
                val seconds = obj.int("seconds") ?: 0
                if (hours < 0 || minutes < 0 || seconds < 0) return null
                val total = hours * 3600L + minutes * 60L + seconds
                if (total !in 1..Int.MAX_VALUE.toLong()) return null
                AssistantAction.Clock(ClockCommand.Timer(total.toInt(), CommandText.label(obj.string("label"))))
            }
            "show_alarms" -> AssistantAction.Clock(ClockCommand.ShowAlarms)
            "show_timers" -> AssistantAction.Clock(ClockCommand.ShowTimers)
            "create_event" -> createEvent(obj, now)
            "set_torch" -> AssistantAction.Device(DeviceAction.SetTorch(obj.boolean("on") ?: return null))
            "set_volume" -> when (obj.string("direction")?.lowercase(Locale.ROOT)) {
                "up" -> AssistantAction.Device(DeviceAction.SetVolume(DeviceAction.VolumeDirection.UP))
                "down" -> AssistantAction.Device(DeviceAction.SetVolume(DeviceAction.VolumeDirection.DOWN))
                else -> null
            }
            "toggle_mute" -> AssistantAction.Device(DeviceAction.ToggleMute)
            "open_settings" -> {
                val panel = when (obj.string("panel")?.lowercase(Locale.ROOT)?.replace("-", "")) {
                    "wifi" -> DeviceAction.SettingsPanel.WIFI
                    "bluetooth" -> DeviceAction.SettingsPanel.BLUETOOTH
                    "volume", "sound" -> DeviceAction.SettingsPanel.VOLUME
                    "nfc" -> DeviceAction.SettingsPanel.NFC
                    "internet", "data", "network" -> DeviceAction.SettingsPanel.INTERNET
                    else -> return null
                }
                AssistantAction.Device(DeviceAction.OpenSettingsPanel(panel))
            }
            "open_app" -> {
                val name = obj.string("name")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 } ?: return null
                AssistantAction.Device(DeviceAction.OpenApp(name))
            }
            else -> null
        }
    }

    private fun createEvent(obj: JsonObject, now: LocalDateTime): AssistantAction? {
        val title = CommandText.label(obj.string("title"))
        val dateText = obj.string("date")?.trim()
        val timeText = obj.string("time")?.trim()
        val date = dateText?.takeIf { it.isNotEmpty() }?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { return null }
        }
        val time = timeText?.takeIf { it.isNotEmpty() }?.let {
            try { LocalTime.parse(it, DateTimeFormatter.ofPattern("H:mm")) } catch (_: Exception) { return null }
        }
        val duration = (obj.int("duration_minutes") ?: 30).takeIf { it in 1..(24 * 60) } ?: return null
        if (date == null && time == null) return AssistantAction.Device(DeviceAction.CreateCalendarEvent(title))
        val day = date ?: now.toLocalDate()
        return AssistantAction.Device(CalendarEvents.build(title, day, time?.hour, time?.minute ?: 0, duration))
    }

    private fun scan(content: String, isFinal: Boolean, bodies: MutableList<String>?): String {
        val out = StringBuilder()
        var index = 0
        while (true) {
            val start = content.indexOf(OPEN, index)
            if (start < 0) {
                out.append(content, index, content.length)
                break
            }
            out.append(content, index, start)
            dropOpeningWrapper(out)
            val bodyStart = start + OPEN.length
            val end = content.indexOf(CLOSE, bodyStart)
            // Unfinished directive: never show any of it, whether the stream is still going
            // or the model ran out of tokens in the middle of it.
            if (end < 0) return out.toString()
            bodies?.add(content.substring(bodyStart, end))
            index = skipClosingWrapper(content, end + CLOSE.length)
        }
        return if (isFinal) out.toString() else holdBackPartialOpener(out.toString())
    }

    /** Removes a backtick or ```lang fence immediately before a directive. */
    private fun dropOpeningWrapper(out: StringBuilder) {
        var end = out.length
        if (end > 0 && out[end - 1] == '\n') end--
        var tag = end
        while (tag > 0 && out[tag - 1].isLetter()) tag--
        var ticks = tag
        while (ticks > 0 && out[ticks - 1] == '`') ticks--
        if (tag - ticks > 0) out.setLength(ticks)
    }

    private fun skipClosingWrapper(content: String, from: Int): Int {
        var index = from
        while (index < content.length && content[index] == '`') index++
        if (content.startsWith("\n```", index)) index += 4
        return index
    }

    /** Mid-stream, a suffix that could become `<<action:` (optionally fenced) is withheld. */
    private fun holdBackPartialOpener(text: String): String {
        var cut = text.length
        for (length in OPEN.length - 1 downTo 1) {
            if (text.endsWith(OPEN.substring(0, length))) {
                cut -= length
                break
            }
        }
        val head = text.substring(0, cut)
        var end = head.length
        if (end > 0 && head[end - 1] == '\n') end--
        var tag = end
        while (tag > 0 && head[tag - 1].isLetter()) tag--
        var ticks = tag
        while (ticks > 0 && head[ticks - 1] == '`') ticks--
        return if (tag - ticks > 0) head.substring(0, ticks) else head
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content

    private fun JsonObject.int(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        val value = primitive.content.trim().toDoubleOrNull() ?: return null
        if (value != Math.floor(value) || value > Int.MAX_VALUE || value < Int.MIN_VALUE) return null
        return value.toInt()
    }

    private fun JsonObject.boolean(key: String): Boolean? = when ((this[key] as? JsonPrimitive)?.content?.lowercase(Locale.ROOT)) {
        "true", "on", "yes", "1" -> true
        "false", "off", "no", "0" -> false
        else -> null
    }

    /**
     * Appended to the system prompt on every assistant turn. Tells the model exactly which
     * actions it can perform, how to request them, and the honesty rules; the current date
     * lets it resolve "tomorrow" for events.
     */
    fun capabilityNotice(now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String {
        val stamp = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH))
        return """
You are the voice assistant of the MaterialChat Android app. Your answers are spoken aloud, so keep them to one or two short sentences with no markdown.

You can perform phone actions. To perform one, put an action directive in your reply on its own line, exactly in this form, with no code fence:
$OPEN{"type":"set_alarm","hour":7,"minute":0,"label":"Gym"}$CLOSE
The app removes the directive from what the user sees, runs it after your reply, and opens the clock or calendar app for the user to confirm. Available actions and their JSON fields:
- set_alarm: hour (0-23), minute (0-59), label (optional). Opens the clock with the alarm filled in.
- set_timer: hours, minutes, seconds (whichever apply), label (optional). Starts a countdown in the clock.
- show_alarms / show_timers: open the alarm or timer list, also for cancelling, changing or checking alarms and timers.
- create_event: title, date (YYYY-MM-DD, optional), time (HH:MM, 24-hour, optional), duration_minutes (optional). Opens the calendar's new-event screen; all-day when no time is given.
- set_torch: on (true or false). The flashlight.
- set_volume: direction ("up" or "down").
- toggle_mute
- open_settings: panel ("wifi", "bluetooth", "volume", "nfc" or "internet"). Android does not let apps switch Wi-Fi or Bluetooth; opening the panel is the most you can do, so say so.
- open_app: name (the app's name as it appears on the phone).
Reminders: use set_timer for "in N minutes", set_alarm for a time today, and create_event for another day or anything the user wants on the calendar. If the user gives no time, ask when.

Rules: only act when the user clearly asks for it, and use the values they gave. When a value is ambiguous (for example "7" with no am or pm) ask one short question instead of guessing. Describe the action as what you are doing ("Opening the clock to set a 7:00 alarm"), never as already confirmed, because the user confirms it in the clock or calendar app. Never claim an action you did not request with a directive. Anything not listed, such as sending messages, making calls, changing brightness or Do Not Disturb, you cannot do: say so in one sentence and name the quickest way to do it.

Current date and time: $stamp (${zone.id}).
""".trimIndent().trim()
    }
}
