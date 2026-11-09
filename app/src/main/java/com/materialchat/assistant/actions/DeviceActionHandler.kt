package com.materialchat.assistant.actions

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Performs [DeviceAction]s and reports what actually happened.
 *
 * Every method returns a user-facing message describing the real outcome, because the
 * assistant must never claim a device change it did not make. Failures are reported with the
 * reason rather than swallowed.
 */
class DeviceActionHandler(private val context: Context) {

    private val cameraManager: CameraManager? =
        ContextCompat.getSystemService(context, CameraManager::class.java)

    private val audioManager: AudioManager? =
        ContextCompat.getSystemService(context, AudioManager::class.java)

    /** Returns a message to show the user. */
    fun perform(action: DeviceAction): String = when (action) {
        is DeviceAction.SetTorch -> setTorch(action.on)
        is DeviceAction.SetVolume -> changeVolume(action.direction)
        DeviceAction.ToggleMute -> toggleMute()
        is DeviceAction.OpenSettingsPanel -> openPanel(action.panel)
    }

    private fun setTorch(on: Boolean): String {
        val manager = cameraManager ?: return "This device has no flash."
        val cameraId = try {
            manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        } ?: return "This device has no flash."

        return try {
            manager.setTorchMode(cameraId, on)
            if (on) "Flashlight on." else "Flashlight off."
        } catch (_: SecurityException) {
            "Android did not allow using the flashlight."
        } catch (_: Exception) {
            "The flashlight is in use by another app."
        }
    }

    private fun changeVolume(direction: DeviceAction.VolumeDirection): String {
        val manager = audioManager ?: return "Audio control is unavailable."
        if (manager.isVolumeFixed) return "This device has a fixed volume."
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
            val percent = percentOf(manager)
            "Volume $percent%."
        } catch (_: SecurityException) {
            // Do Not Disturb can block volume changes without policy access.
            "Android did not allow changing the volume."
        }
    }

    private fun toggleMute(): String {
        val manager = audioManager ?: return "Audio control is unavailable."
        if (manager.isVolumeFixed) return "This device has a fixed volume."
        return try {
            if (manager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                "Unmuted."
            } else {
                manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                "Muted."
            }
        } catch (_: SecurityException) {
            "Android did not allow muting the volume."
        }
    }

    private fun percentOf(manager: AudioManager): Int {
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current * 100 / max).coerceIn(0, 100)
    }

    /**
     * Opens a settings surface. Wi-Fi and Bluetooth cannot be toggled by an ordinary app, so
     * the honest outcome is navigation plus a message saying the user must change it.
     */
    private fun openPanel(panel: DeviceAction.SettingsPanel): String {
        val intent = when (panel) {
            DeviceAction.SettingsPanel.WIFI -> Intent(Settings.Panel.ACTION_WIFI)
            DeviceAction.SettingsPanel.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            DeviceAction.SettingsPanel.VOLUME -> Intent(Settings.Panel.ACTION_VOLUME)
            DeviceAction.SettingsPanel.NFC -> Intent(Settings.Panel.ACTION_NFC)
            DeviceAction.SettingsPanel.INTERNET -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            when (panel) {
                DeviceAction.SettingsPanel.WIFI ->
                    "Opened Wi-Fi settings. Android does not let apps switch Wi-Fi on or off."
                DeviceAction.SettingsPanel.BLUETOOTH ->
                    "Opened Bluetooth settings. Android does not let apps switch Bluetooth on or off."
                else -> "Opened settings."
            }
        } catch (_: Exception) {
            "This device has no settings screen for that."
        }
    }
}
