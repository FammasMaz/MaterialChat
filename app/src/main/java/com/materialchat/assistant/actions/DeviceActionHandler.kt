package com.materialchat.assistant.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Starts an activity on the assistant's behalf. Returns null when the start succeeded,
 * otherwise a user-facing reason. The session supplies one that goes through
 * `VoiceInteractionSession.startAssistantActivity` with a plain-launch fallback, the same
 * path the clock handoff uses.
 */
fun interface AssistantActivityLauncher {
    fun launch(intent: Intent): String?

    companion object {
        /** A plain activity start from a non-activity context. */
        fun plain(context: Context) = AssistantActivityLauncher { intent -> guarded { context.startActivity(intent) } }

        /**
         * The clock handoff's path for any intent: `startAssistantActivity` while the session
         * is the active and shown one, a plain start otherwise (the platform reports the
         * inactive and hidden states as [IllegalStateException]).
         */
        fun throughSession(
            startAssistantActivity: (Intent) -> Unit,
            startActivity: (Intent) -> Unit
        ) = AssistantActivityLauncher { intent ->
            guarded {
                try {
                    startAssistantActivity(intent)
                } catch (_: IllegalStateException) {
                    startActivity(intent)
                }
            }
        }

        internal fun guarded(start: () -> Unit): String? = try {
            start()
            null
        } catch (_: ActivityNotFoundException) {
            "No app on this phone can handle that."
        } catch (_: SecurityException) {
            "Android did not allow opening that."
        } catch (_: RuntimeException) {
            "Android could not open that from the assistant."
        }
    }
}

/**
 * What really happened. [launchedActivity] means another app is now in front, so the overlay
 * should get out of the way as it does after a clock handoff.
 */
data class DeviceActionOutcome(
    val message: String,
    val succeeded: Boolean,
    val launchedActivity: Boolean = false
)

/**
 * Performs [DeviceAction]s and reports what actually happened.
 *
 * Every method returns a user-facing message describing the real outcome, because the
 * assistant must never claim a device change it did not make. Failures are reported with the
 * reason rather than swallowed.
 */
class DeviceActionHandler(
    private val context: Context,
    private val launcher: AssistantActivityLauncher = AssistantActivityLauncher.plain(context)
) {

    private val cameraManager: CameraManager? =
        ContextCompat.getSystemService(context, CameraManager::class.java)

    private val audioManager: AudioManager? =
        ContextCompat.getSystemService(context, AudioManager::class.java)

    fun perform(action: DeviceAction): DeviceActionOutcome = when (action) {
        is DeviceAction.SetTorch -> setTorch(action.on)
        is DeviceAction.SetVolume -> changeVolume(action.direction)
        DeviceAction.ToggleMute -> toggleMute()
        is DeviceAction.OpenSettingsPanel -> openPanel(action.panel)
        is DeviceAction.CreateCalendarEvent -> createCalendarEvent(action)
        is DeviceAction.OpenApp -> openApp(action.name)
    }

    private fun setTorch(on: Boolean): DeviceActionOutcome {
        val manager = cameraManager ?: return failed("This device has no flash.")
        val cameraId = try {
            manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        } ?: return failed("This device has no flash.")

        return try {
            manager.setTorchMode(cameraId, on)
            DeviceActionOutcome(if (on) "Flashlight on." else "Flashlight off.", succeeded = true)
        } catch (_: SecurityException) {
            failed("Android did not allow using the flashlight.")
        } catch (_: Exception) {
            failed("The flashlight is in use by another app.")
        }
    }

    private fun changeVolume(direction: DeviceAction.VolumeDirection): DeviceActionOutcome {
        val manager = audioManager ?: return failed("Audio control is unavailable.")
        if (manager.isVolumeFixed) return failed("This device has a fixed volume.")
        val adjustment = when (direction) {
            DeviceAction.VolumeDirection.UP -> AudioManager.ADJUST_RAISE
            DeviceAction.VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
        }
        return try {
            manager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                adjustment,
                AudioManager.FLAG_SHOW_UI
            )
            DeviceActionOutcome("Volume ${percentOf(manager)}%.", succeeded = true)
        } catch (_: SecurityException) {
            // Do Not Disturb can block volume changes without policy access.
            failed("Android did not allow changing the volume.")
        }
    }

    private fun toggleMute(): DeviceActionOutcome {
        val manager = audioManager ?: return failed("Audio control is unavailable.")
        if (manager.isVolumeFixed) return failed("This device has a fixed volume.")
        return try {
            if (manager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                DeviceActionOutcome("Unmuted.", succeeded = true)
            } else {
                manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                DeviceActionOutcome("Muted.", succeeded = true)
            }
        } catch (_: SecurityException) {
            failed("Android did not allow muting the volume.")
        }
    }

    private fun percentOf(manager: AudioManager): Int {
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current * 100 / max).coerceIn(0, 100)
    }

    /**
     * Opens a settings surface. Wi-Fi and Bluetooth cannot be toggled by an ordinary app, so
     * the honest outcome is navigation plus a message saying the user must change it. The
     * quick-settings panels exist from API 29; older devices get the full settings screen.
     */
    private fun openPanel(panel: DeviceAction.SettingsPanel): DeviceActionOutcome {
        val panels = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val intent = when (panel) {
            DeviceAction.SettingsPanel.WIFI ->
                Intent(if (panels) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)
            DeviceAction.SettingsPanel.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            DeviceAction.SettingsPanel.VOLUME ->
                Intent(if (panels) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS)
            DeviceAction.SettingsPanel.NFC ->
                Intent(if (panels) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS)
            DeviceAction.SettingsPanel.INTERNET ->
                Intent(if (panels) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val error = launcher.launch(intent)
        if (error != null) return failed("This device has no settings screen for that.")
        val message = when (panel) {
            DeviceAction.SettingsPanel.WIFI ->
                "Opened Wi-Fi settings. Android does not let apps switch Wi-Fi on or off, so change it there."
            DeviceAction.SettingsPanel.BLUETOOTH ->
                "Opened Bluetooth settings. Android does not let apps switch Bluetooth on or off, so change it there."
            else -> "Opened settings."
        }
        return DeviceActionOutcome(message, succeeded = true, launchedActivity = true)
    }

    /**
     * `ACTION_INSERT` on the events table opens the calendar app's new-event screen with the
     * fields filled in. Nothing is saved until the user confirms there, so the message says
     * "opening", never "added".
     */
    private fun createCalendarEvent(event: DeviceAction.CreateCalendarEvent): DeviceActionOutcome {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        event.title?.let { intent.putExtra(CalendarContract.Events.TITLE, it) }
        event.beginMillis?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        event.endMillis?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)

        val error = launcher.launch(intent)
        if (error != null) return failed("No calendar app is available to add that.")
        val what = event.title?.let { "\"$it\"" } ?: "an event"
        return DeviceActionOutcome(
            "Opening the calendar to add $what${describeWhen(event)}. Save it there.",
            succeeded = true,
            launchedActivity = true
        )
    }

    private fun describeWhen(event: DeviceAction.CreateCalendarEvent): String {
        val begin = event.beginMillis ?: return ""
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(begin).atZone(zone)
        val today = LocalDate.now(zone)
        val day = when (start.toLocalDate()) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> "on " + start.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
        }
        return if (event.allDay) " $day" else " $day at " + start.format(DateTimeFormatter.ofPattern("H:mm", Locale.getDefault()))
    }

    /**
     * Finds an installed app by the label shown in the launcher and starts its launcher
     * activity. Exact label first, then prefix, then substring, then package name.
     */
    private fun openApp(name: String): DeviceActionOutcome {
        val packageManager = context.packageManager
        val wanted = name.trim().lowercase(Locale.ROOT)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = try {
            packageManager.queryIntentActivities(launcherIntent, 0)
                .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
                .map { it to it.loadLabel(packageManager).toString() }
        } catch (_: Exception) {
            emptyList()
        }
        val match = candidates.firstOrNull { it.second.equals(wanted, ignoreCase = true) }
            ?: candidates.firstOrNull { it.second.lowercase(Locale.ROOT).startsWith(wanted) }
            ?: candidates.firstOrNull { it.second.lowercase(Locale.ROOT).contains(wanted) }
            ?: candidates.firstOrNull {
                it.first.activityInfo.packageName.lowercase(Locale.ROOT).contains(wanted.replace(" ", ""))
            }
            ?: return failed("I could not find an app called \"$name\" on this phone.")
        val info = match.first.activityInfo
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(info.packageName, info.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val error = launcher.launch(intent)
        return if (error == null) {
            DeviceActionOutcome("Opening ${match.second}.", succeeded = true, launchedActivity = true)
        } else {
            failed(error)
        }
    }

    private fun failed(message: String) = DeviceActionOutcome(message, succeeded = false)
}
