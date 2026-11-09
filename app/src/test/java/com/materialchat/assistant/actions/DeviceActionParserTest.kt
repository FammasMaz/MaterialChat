package com.materialchat.assistant.actions

import org.junit.Assert.*
import org.junit.Test

class DeviceActionParserTest {
    @Test fun flashlightPhrasings() {
        listOf("turn on the flashlight", "turn on flashlight", "flashlight on", "enable torch", "turn on the torch").forEach {
            assertEquals(it, DeviceAction.SetTorch(true), DeviceActionParser.parse(it))
        }
        listOf("turn off the flashlight", "turn off flashlight", "disable the torch", "switch off the flash").forEach {
            assertEquals(it, DeviceAction.SetTorch(false), DeviceActionParser.parse(it))
        }
    }

    @Test fun volumePhrasings() {
        listOf("turn the volume up", "volume up", "louder", "increase the volume").forEach {
            assertEquals(it, DeviceAction.SetVolume(DeviceAction.VolumeDirection.UP), DeviceActionParser.parse(it))
        }
        listOf("turn the volume down", "volume down", "quieter", "lower the volume").forEach {
            assertEquals(it, DeviceAction.SetVolume(DeviceAction.VolumeDirection.DOWN), DeviceActionParser.parse(it))
        }
    }

    @Test fun mutePhrasings() {
        listOf("mute", "mute the volume", "silence", "unmute").forEach {
            assertEquals(it, DeviceAction.ToggleMute, DeviceActionParser.parse(it))
        }
    }

    /** Wi-Fi and Bluetooth cannot be toggled by an app, so they open settings instead. */
    @Test fun radioTogglesOpenSettings() {
        assertEquals(
            DeviceAction.OpenSettingsPanel(DeviceAction.SettingsPanel.WIFI),
            DeviceActionParser.parse("turn on wifi")
        )
        assertEquals(
            DeviceAction.OpenSettingsPanel(DeviceAction.SettingsPanel.BLUETOOTH),
            DeviceActionParser.parse("turn off bluetooth")
        )
    }

    @Test fun unrelatedTextIsNotAnAction() {
        listOf("what is a flashlight", "explain volume", "how do i mute").forEach {
            assertNull(it, DeviceActionParser.parse(it))
        }
    }
}
