package com.materialchat.assistant.actions

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ActionDirectivesTest {
    private val now = LocalDateTime.of(2026, 11, 14, 11, 40)

    @Test fun completeDirectiveIsStrippedAndExtracted() {
        val reply = "Opening the clock to set a 7:00 alarm.\n<<action:{\"type\":\"set_alarm\",\"hour\":7,\"minute\":0,\"label\":\"gym\"}>>\n"
        val extraction = ActionDirectives.extract(reply, now)
        assertEquals(listOf<AssistantAction>(AssistantAction.Clock(ClockCommand.Alarm(7, 0, "Gym"))), extraction.actions)
        assertEquals(0, extraction.rejected)
        assertFalse(extraction.text.contains("<<"))
        assertFalse(extraction.text.contains("action"))
        assertEquals("Opening the clock to set a 7:00 alarm.", extraction.text.trim())
    }

    @Test fun fencedDirectiveIsStrippedWithItsFence() {
        val reply = "Starting a timer.\n```json\n<<action:{\"type\":\"set_timer\",\"minutes\":5}>>\n```\nDone."
        val extraction = ActionDirectives.extract(reply, now)
        assertEquals(listOf<AssistantAction>(AssistantAction.Clock(ClockCommand.Timer(300))), extraction.actions)
        assertFalse(extraction.text, extraction.text.contains("`"))
        assertFalse(extraction.text, extraction.text.contains("<<"))
        assertTrue(extraction.text, extraction.text.contains("Starting a timer."))
        assertTrue(extraction.text, extraction.text.contains("Done."))
    }

    /** Every streamed snapshot must be a prefix of the next, or the speech chunker rejects it. */
    @Test fun streamingStripIsMonotoneAndNeverShowsAPartialDirective() {
        val full = "Setting that.\n<<action:{\"type\":\"toggle_mute\"}>> After."
        var previous = ""
        for (end in 1..full.length) {
            val shown = ActionDirectives.strip(full.substring(0, end))
            assertTrue("'$shown' must extend '$previous'", shown.startsWith(previous))
            assertFalse(shown, shown.contains("<<"))
            assertFalse(shown, shown.contains("action:"))
            previous = shown
        }
        val final = ActionDirectives.strip(full, isFinal = true)
        assertTrue(final.startsWith(previous))
        assertEquals("Setting that.\n After.", final)
    }

    @Test fun unfinishedDirectiveAtEndOfReplyIsHidden() {
        val reply = "Opening the clock.\n<<action:{\"type\":\"set_alarm\",\"hour\":7"
        val extraction = ActionDirectives.extract(reply, now)
        assertTrue(extraction.actions.isEmpty())
        assertEquals("Opening the clock.", extraction.text.trim())
    }

    @Test fun invalidDirectivesAreRejectedSilently() {
        val cases = listOf(
            "<<action:not json>>",
            "<<action:{\"type\":\"set_alarm\",\"hour\":25}>>",
            "<<action:{\"type\":\"set_alarm\",\"hour\":7,\"minute\":60}>>",
            "<<action:{\"type\":\"set_alarm\"}>>",
            "<<action:{\"type\":\"set_timer\",\"minutes\":0}>>",
            "<<action:{\"type\":\"set_timer\",\"minutes\":-5}>>",
            "<<action:{\"type\":\"launch_missiles\"}>>",
            "<<action:{\"type\":\"set_torch\"}>>",
            "<<action:{\"type\":\"set_volume\",\"direction\":\"sideways\"}>>",
            "<<action:{\"type\":\"open_settings\",\"panel\":\"brightness\"}>>",
            "<<action:{\"type\":\"create_event\",\"date\":\"tomorrow\"}>>",
            "<<action:[1,2]>>"
        )
        for (case in cases) {
            val extraction = ActionDirectives.extract("Sure. $case", now)
            assertTrue(case, extraction.actions.isEmpty())
            assertEquals(case, 1, extraction.rejected)
            assertEquals(case, "Sure.", extraction.text.trim())
        }
    }

    @Test fun validDirectivesMapToActions() {
        assertEquals(
            AssistantAction.Clock(ClockCommand.Timer(5400, "Laundry")),
            ActionDirectives.parse("{\"type\":\"set_timer\",\"hours\":1,\"minutes\":30,\"label\":\"laundry\"}", now)
        )
        assertEquals(AssistantAction.Clock(ClockCommand.ShowAlarms), ActionDirectives.parse("{\"type\":\"show_alarms\"}", now))
        assertEquals(AssistantAction.Clock(ClockCommand.ShowTimers), ActionDirectives.parse("{\"type\":\"SHOW_TIMERS\"}", now))
        assertEquals(AssistantAction.Device(DeviceAction.SetTorch(true)), ActionDirectives.parse("{\"type\":\"set_torch\",\"on\":true}", now))
        assertEquals(AssistantAction.Device(DeviceAction.SetTorch(false)), ActionDirectives.parse("{\"type\":\"set_torch\",\"on\":\"off\"}", now))
        assertEquals(
            AssistantAction.Device(DeviceAction.SetVolume(DeviceAction.VolumeDirection.DOWN)),
            ActionDirectives.parse("{\"type\":\"set_volume\",\"direction\":\"down\"}", now)
        )
        assertEquals(AssistantAction.Device(DeviceAction.ToggleMute), ActionDirectives.parse("{\"type\":\"toggle_mute\"}", now))
        assertEquals(
            AssistantAction.Device(DeviceAction.OpenSettingsPanel(DeviceAction.SettingsPanel.WIFI)),
            ActionDirectives.parse("{\"type\":\"open_settings\",\"panel\":\"Wi-Fi\"}", now)
        )
        assertEquals(AssistantAction.Device(DeviceAction.OpenApp("Spotify")), ActionDirectives.parse("{\"type\":\"open_app\",\"name\":\"Spotify\"}", now))
        assertEquals(
            AssistantAction.Device(DeviceAction.CreateCalendarEvent("Dentist")),
            ActionDirectives.parse("{\"type\":\"create_event\",\"title\":\"dentist\"}", now)
        )
        val timed = ActionDirectives.parse(
            "{\"type\":\"create_event\",\"title\":\"Dentist\",\"date\":\"2026-11-15\",\"time\":\"9:30\",\"duration_minutes\":45}", now
        ) as AssistantAction.Device
        val event = timed.action as DeviceAction.CreateCalendarEvent
        assertEquals("Dentist", event.title)
        assertFalse(event.allDay)
        assertEquals(45L * 60_000, event.endMillis!! - event.beginMillis!!)
        val allDay = (ActionDirectives.parse("{\"type\":\"create_event\",\"title\":\"Trip\",\"date\":\"2026-12-01\"}", now) as AssistantAction.Device).action as DeviceAction.CreateCalendarEvent
        assertTrue(allDay.allDay)
    }

    @Test fun directiveCountIsBounded() {
        val reply = (1..5).joinToString("\n") { "<<action:{\"type\":\"toggle_mute\"}>>" }
        assertEquals(ActionDirectives.MAX_PER_REPLY, ActionDirectives.extract(reply, now).actions.size)
    }

    @Test fun noticeDescribesEveryActionAndTheHonestyRule() {
        val notice = ActionDirectives.capabilityNotice(now)
        for (type in listOf("set_alarm", "set_timer", "show_alarms", "show_timers", "create_event", "set_torch", "set_volume", "toggle_mute", "open_settings", "open_app")) {
            assertTrue(type, notice.contains(type))
        }
        assertTrue(notice.contains(ActionDirectives.OPEN))
        assertTrue(notice.contains("Never claim an action"))
        assertTrue(notice.contains("14 November 2026"))
        assertFalse(notice.contains("cannot control the device"))
    }
}
