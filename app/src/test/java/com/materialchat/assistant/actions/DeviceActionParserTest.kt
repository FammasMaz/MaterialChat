package com.materialchat.assistant.actions

import org.junit.Assert.*
import org.junit.Test

class DeviceActionParserTest {
    @Test fun flashlightPhrasings() {
        listOf("turn on the flashlight", "turn on flashlight", "flashlight on", "enable torch", "turn on the torch", "turn the flashlight on", "can you turn on my flashlight please").forEach {
            assertEquals(it, DeviceAction.SetTorch(true), DeviceActionParser.parse(it))
        }
        listOf("turn off the flashlight", "turn off flashlight", "disable the torch", "switch off the flash", "turn the torch off").forEach {
            assertEquals(it, DeviceAction.SetTorch(false), DeviceActionParser.parse(it))
        }
    }

    @Test fun volumePhrasings() {
        listOf("turn the volume up", "volume up", "louder", "increase the volume", "turn up the volume").forEach {
            assertEquals(it, DeviceAction.SetVolume(DeviceAction.VolumeDirection.UP), DeviceActionParser.parse(it))
        }
        listOf("turn the volume down", "volume down", "quieter", "lower the volume", "turn down the volume").forEach {
            assertEquals(it, DeviceAction.SetVolume(DeviceAction.VolumeDirection.DOWN), DeviceActionParser.parse(it))
        }
    }

    @Test fun mutePhrasings() {
        listOf("mute", "mute the volume", "silence", "unmute", "mute my phone").forEach {
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

    @Test fun calendarEventsWithoutTimesAreRecognisedLocally() {
        assertEquals(DeviceAction.CreateCalendarEvent("Dentist"), DeviceActionParser.parse("add a calendar event called dentist"))
        assertEquals(DeviceAction.CreateCalendarEvent("Meeting with sam"), DeviceActionParser.parse("schedule a meeting with sam"))
        assertEquals(DeviceAction.CreateCalendarEvent(null), DeviceActionParser.parse("create an event"))
        // Dates and times need today's date: the model resolves them and sends a directive.
        assertNull(DeviceActionParser.parse("add an event called dentist tomorrow at 9 am"))
    }

    @Test fun openAppByName() {
        assertEquals(DeviceAction.OpenApp("spotify"), DeviceActionParser.parse("open spotify"))
        assertEquals(DeviceAction.OpenApp("google maps"), DeviceActionParser.parse("launch the google maps app"))
        assertNull(DeviceActionParser.parse("open the door and tell me a story about a very long thing"))
    }

    @Test fun unrelatedTextIsNotAnAction() {
        listOf("what is a flashlight", "explain volume", "how do i mute", "is bluetooth on", "why is my wifi slow").forEach {
            assertNull(it, DeviceActionParser.parse(it))
        }
    }
}
