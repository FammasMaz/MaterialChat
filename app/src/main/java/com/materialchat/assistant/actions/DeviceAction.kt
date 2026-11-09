package com.materialchat.assistant.actions

/**
 * Device actions the assistant can perform itself, as opposed to clock actions which are
 * handed to the clock app.
 *
 * Scope is deliberately limited to what a normal, non-privileged app is actually allowed to
 * do. Android gives no general-purpose "system actions" API to third-party assistants: the
 * assistant role grants a voice session and an activity launch, not control of system
 * settings. Concretely:
 *
 * - Flashlight works through [android.hardware.camera2.CameraManager.setTorchMode], which
 *   needs no permission and no camera open.
 * - Volume works through [android.media.AudioManager], subject to [android.media.AudioManager.isVolumeFixed].
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

    enum class VolumeDirection { UP, DOWN }

    enum class SettingsPanel { INTERNET, WIFI, BLUETOOTH, VOLUME, NFC }
}

/**
 * Recognises device actions locally, before any provider call.
 *
 * As with [ClockCommandParser], recognition matters more than precision: an unrecognised
 * "turn on the flashlight" reaches the language model, which cannot control the device and
 * answers as though it had. Requests that are recognised but cannot be performed are
 * reported as such, so the user is never told something happened when it did not.
 */
object DeviceActionParser {
    private val TORCH_ON = Regex(
        "^(?:(?:turn on|switch on|enable|activate) (?:the )?(?:flashlight|torch|flash)(?: light)?|(?:flashlight|torch|flash)(?: light)? on)$"
    )
    private val TORCH_OFF = Regex(
        "^(?:(?:turn off|switch off|disable|deactivate|kill) (?:the )?(?:flashlight|torch|flash)(?: light)?|(?:flashlight|torch|flash)(?: light)? off)$"
    )
    private val VOLUME_UP = Regex(
        "^(?:turn (?:the )?volume up|volume up|louder|increase (?:the )?volume|raise (?:the )?volume)$"
    )
    private val VOLUME_DOWN = Regex(
        "^(?:turn (?:the )?volume down|volume down|quieter|lower (?:the )?volume|decrease (?:the )?volume)$"
    )
    private val MUTE = Regex("^(?:mute|mute (?:the )?(?:volume|sound|audio)|silence)$")
    private val UNMUTE = Regex("^(?:unmute|unmute (?:the )?(?:volume|sound|audio))$")

    fun parse(input: String): DeviceAction? {
        val text = input.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
            .removePrefix("please ").trimEnd('.', '!', '?', ',')

        return when {
            TORCH_ON.matches(text) -> DeviceAction.SetTorch(true)
            TORCH_OFF.matches(text) -> DeviceAction.SetTorch(false)
            VOLUME_UP.matches(text) -> DeviceAction.SetVolume(DeviceAction.VolumeDirection.UP)
            VOLUME_DOWN.matches(text) -> DeviceAction.SetVolume(DeviceAction.VolumeDirection.DOWN)
            MUTE.matches(text) -> DeviceAction.ToggleMute
            UNMUTE.matches(text) -> DeviceAction.ToggleMute
            text.contains("wifi") || text.contains("wi-fi") ->
                DeviceAction.OpenSettingsPanel(DeviceAction.SettingsPanel.WIFI)
            text.contains("bluetooth") ->
                DeviceAction.OpenSettingsPanel(DeviceAction.SettingsPanel.BLUETOOTH)
            else -> null
        }
    }
}
