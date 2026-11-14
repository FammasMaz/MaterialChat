package com.materialchat.assistant.actions

/**
 * Device actions the assistant performs itself, as opposed to clock actions which are handed
 * to the clock app.
 *
 * Scope is deliberately limited to what a normal, non-privileged app is actually allowed to
 * do on Android 8-16. Android gives no general-purpose "system actions" API to third-party
 * assistants: the assistant role grants a voice session and an activity launch, not control
 * of system settings. Concretely:
 *
 * - Flashlight works through [android.hardware.camera2.CameraManager.setTorchMode], which
 *   needs no permission and no camera open.
 * - Volume works through [android.media.AudioManager], subject to [android.media.AudioManager.isVolumeFixed].
 * - Calendar events use the `ACTION_INSERT` intent on `CalendarContract.Events`, which needs
 *   no calendar permission because the calendar app itself saves the event after the user
 *   confirms it. Reminders without a universal API are expressed this way, or as labelled
 *   alarms and timers (see [ReminderParser]).
 * - Opening another app resolves its launcher activity by label through PackageManager
 *   (the manifest declares `queries` for launcher activities so API 30+ can see them).
 * - Wi-Fi and Bluetooth cannot be toggled. `WifiManager.setWifiEnabled` always fails for apps
 *   targeting API 29+, and there is no public Bluetooth equivalent. The most an app may do is
 *   open the relevant settings panel, which is a navigation aid rather than an action.
 * - Do Not Disturb needs a separate user-granted policy access; without it the request must
 *   be reported as unavailable rather than silently ignored.
 *
 * Anything outside this list is reported as unsupported instead of being answered as though
 * it had happened.
 */
sealed interface DeviceAction {
    data class SetTorch(val on: Boolean) : DeviceAction
    data class SetVolume(val direction: VolumeDirection) : DeviceAction
    data object ToggleMute : DeviceAction
    /** Opens a settings surface; the user still has to change the setting themselves. */
    data class OpenSettingsPanel(val panel: SettingsPanel) : DeviceAction
    /**
     * Opens the calendar's new-event screen with these values filled in. Times are epoch
     * millis in the device zone; without them the user picks the time in the calendar.
     */
    data class CreateCalendarEvent(
        val title: String?,
        val beginMillis: Long? = null,
        val endMillis: Long? = null,
        val allDay: Boolean = false
    ) : DeviceAction
    /** Launches an installed app by the name shown on the phone. */
    data class OpenApp(val name: String) : DeviceAction

    enum class VolumeDirection { UP, DOWN }

    enum class SettingsPanel { INTERNET, WIFI, BLUETOOTH, VOLUME, NFC }
}

/**
 * Recognises device actions locally, before any provider call.
 *
 * As with [ClockCommandParser], recognition matters more than precision: a recognised request
 * costs no model round trip. Requests that are recognised but cannot be performed are reported
 * as such, so the user is never told something happened when it did not. Anything this misses
 * still reaches the model, which can request the same actions through [ActionDirectives].
 */
object DeviceActionParser {
    private const val LIGHT = "(?:the |my )?(?:flashlight|torch|flash)(?: light)?"
    private val TORCH_ON = Regex(
        "^(?:(?:turn|switch|put) on $LIGHT|(?:turn|switch|put) $LIGHT on|(?:enable|activate|start) $LIGHT|$LIGHT on)$"
    )
    private val TORCH_OFF = Regex(
        "^(?:(?:turn|switch|shut) off $LIGHT|(?:turn|switch|shut) $LIGHT off|(?:disable|deactivate|kill|stop) $LIGHT|$LIGHT off)$"
    )
    private val VOLUME_UP = Regex(
        "^(?:turn (?:the |my )?volume up|volume up|louder|make it louder|(?:increase|raise|turn up|crank up) (?:the |my )?volume)$"
    )
    private val VOLUME_DOWN = Regex(
        "^(?:turn (?:the |my )?volume down|volume down|quieter|make it quieter|(?:decrease|lower|reduce|turn down) (?:the |my )?volume)$"
    )
    private val MUTE = Regex("^(?:mute|mute (?:the |my )?(?:volume|sound|audio|phone)|silence|silence (?:the |my )?phone)$")
    private val UNMUTE = Regex("^(?:unmute|unmute (?:the |my )?(?:volume|sound|audio|phone))$")
    private val EVENT = Regex(
        "^(?:add|create|schedule|put|make|set up|set|new) (?:a |an |me a |new |a new )?(?:calendar )?(event|appointment|meeting)(?: (called|named|titled|for|about|to|with) (.+))?$"
    )
    private val OPEN_APP = Regex("^(?:open|launch) (?:the |my )?(.+?)(?: app)?$")
    /** Dates and times in an event request are left to the model, which knows today's date. */
    private val TIME_WORDS = Regex("\\b(?:at|on|in|tomorrow|today|tonight|am|pm|noon|midnight|o'clock|next|this)\\b|\\d")

    fun parse(input: String): DeviceAction? {
        val text = CommandText.normalise(input)
        if (text.isEmpty() || CommandText.isQuestion(text)) return null

        return when {
            TORCH_ON.matches(text) -> DeviceAction.SetTorch(true)
            TORCH_OFF.matches(text) -> DeviceAction.SetTorch(false)
            VOLUME_UP.matches(text) -> DeviceAction.SetVolume(DeviceAction.VolumeDirection.UP)
            VOLUME_DOWN.matches(text) -> DeviceAction.SetVolume(DeviceAction.VolumeDirection.DOWN)
            MUTE.matches(text) -> DeviceAction.ToggleMute
            UNMUTE.matches(text) -> DeviceAction.ToggleMute
            EVENT.matches(text) -> event(EVENT.matchEntire(text)!!)
            text.contains("wifi") || text.contains("wi-fi") ->
                DeviceAction.OpenSettingsPanel(DeviceAction.SettingsPanel.WIFI)
            text.contains("bluetooth") ->
                DeviceAction.OpenSettingsPanel(DeviceAction.SettingsPanel.BLUETOOTH)
            else -> OPEN_APP.matchEntire(text)?.let { openApp(it.groupValues[1]) }
        }
    }

    private fun event(match: MatchResult): DeviceAction? {
        val kind = match.groupValues[1]
        val connector = match.groupValues[2]
        val rest = match.groupValues[3].trim()
        if (rest.isEmpty()) return DeviceAction.CreateCalendarEvent(null)
        if (TIME_WORDS.containsMatchIn(rest)) return null
        val title = if (connector == "with") "$kind with $rest" else rest
        return DeviceAction.CreateCalendarEvent(CommandText.label(title))
    }

    private fun openApp(raw: String): DeviceAction? {
        val name = raw.trim().trim('"', '\'')
        if (name.isEmpty() || name.length > 40 || name.split(' ').size > 4) return null
        return DeviceAction.OpenApp(name)
    }
}
