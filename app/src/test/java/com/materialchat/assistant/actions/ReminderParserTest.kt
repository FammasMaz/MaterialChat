package com.materialchat.assistant.actions

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ReminderParserTest {
    private val now = LocalDateTime.of(2026, 11, 14, 11, 40)

    @Test fun relativeRemindersBecomeLabelledTimers() {
        assertEquals(
            AssistantAction.Clock(ClockCommand.Timer(1200, "Check the oven")),
            ReminderParser.parse("remind me in 20 minutes to check the oven", now)
        )
        assertEquals(
            AssistantAction.Clock(ClockCommand.Timer(1200, "Check the oven")),
            ReminderParser.parse("remind me to check the oven in 20 minutes", now)
        )
        assertEquals(
            AssistantAction.Clock(ClockCommand.Timer(3600, "Call back")),
            ReminderParser.parse("set a reminder to call back in an hour", now)
        )
    }

    @Test fun todayRemindersBecomeLabelledAlarms() {
        assertEquals(
            AssistantAction.Clock(ClockCommand.Alarm(17, 0, "Call mom")),
            ReminderParser.parse("remind me at 5 pm to call mom", now)
        )
        assertEquals(
            AssistantAction.Clock(ClockCommand.Alarm(17, 0, "Call mom")),
            ReminderParser.parse("remind me to call mom at 5 pm", now)
        )
        assertEquals(
            AssistantAction.Clock(ClockCommand.Alarm(21, 0, "Take the pills")),
            ReminderParser.parse("remind me to take the pills at 9 tonight", now)
        )
    }

    @Test fun tomorrowRemindersBecomeCalendarEvents() {
        val timed = ReminderParser.parse("remind me tomorrow at 9 am to send the report", now) as AssistantAction.Device
        val event = timed.action as DeviceAction.CreateCalendarEvent
        assertEquals("Send the report", event.title)
        assertFalse(event.allDay)
        assertEquals(CalendarEvents.build("Send the report", now.toLocalDate().plusDays(1), 9, 0), event)
        val allDay = ReminderParser.parse("remind me to water the plants tomorrow", now) as AssistantAction.Device
        assertEquals(CalendarEvents.build("Water the plants", now.toLocalDate().plusDays(1), null, 0), allDay.action)
    }

    /** No time, or an ambiguous one: the model asks when instead of the app guessing. */
    @Test fun unresolvedTimesGoToTheModel() {
        listOf(
            "remind me to buy milk",
            "remind me at 7 to call mom",
            "remind me to call mom at 7",
            "what reminders do i have",
            "remind me why i set this alarm"
        ).forEach { assertNull(it, ReminderParser.parse(it, now)) }
    }

    /**
     * The utterance from the user's screenshot (refused by v2.29.34's model prompt) and its
     * spoken variants resolve locally to a labelled timer, through the full first pass
     * ([AssistantCommandParser]), so no model is ever asked and nothing can refuse them.
     */
    @Test fun spokenTimerRemindersResolveLocallyThroughTheFullFirstPass() {
        mapOf(
            "remind me to call someone in 10 minutes" to ClockCommand.Timer(600, "Call someone"),
            "remind me to call mum in ten minutes" to ClockCommand.Timer(600, "Call mum"),
            "can you remind me in 10 min to call someone" to ClockCommand.Timer(600, "Call someone"),
            "set a reminder to call someone in 10 minutes" to ClockCommand.Timer(600, "Call someone"),
            "remind me in an hour to stretch" to ClockCommand.Timer(3600, "Stretch"),
            "Remind me to call someone in 10 minutes." to ClockCommand.Timer(600, "Call someone"),
            "hey can you please remind me to call someone in 10 minutes please" to ClockCommand.Timer(600, "Call someone")
        ).forEach { (utterance, expected) ->
            assertEquals(utterance, AssistantAction.Clock(expected), AssistantCommandParser.parse(utterance, now))
        }
    }

    @Test fun fillerIsIgnoredMidConversation() {
        assertEquals(
            AssistantAction.Clock(ClockCommand.Timer(600, "Check the pasta")),
            ReminderParser.parse("Ok, and also can you remind me in 10 minutes to check the pasta please?", now)
        )
    }
}
