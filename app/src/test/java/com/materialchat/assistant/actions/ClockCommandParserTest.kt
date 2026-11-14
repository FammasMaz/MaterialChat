package com.materialchat.assistant.actions

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class ClockCommandParserTest {
    private val now = LocalTime.of(11, 40)

    @Test fun whitespaceAndQuestionPunctuation() {
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("please set  an alarm at 7 am?"))
    }
    @Test fun explicitTimes() {
        assertEquals(ClockCommand.Alarm(7, 30), ClockCommandParser.parse("Please set an alarm at 7:30 am?"))
        assertEquals(ClockCommand.Alarm(7, 30), ClockCommandParser.parse("set an alarm at 7:30 am"))
        assertEquals(ClockCommand.Alarm(0, 0), ClockCommandParser.parse("set alarm for 12 am"))
        assertEquals(ClockCommand.Alarm(19, 0), ClockCommandParser.parse("set alarm at 7 pm"))
        assertEquals(ClockCommand.Alarm(23, 15), ClockCommandParser.parse("set alarm at 23:15"))
    }
    @Test fun ambiguousOrInvalidTimesOpenUi() {
        listOf("set alarm", "set alarm at 7", "set alarm at 7 tomorrow", "set alarm at 25:00", "set alarm at 7:90 pm").forEach {
            assertEquals(it, ClockCommand.ShowAlarms, ClockCommandParser.parse(it))
        }
    }
    @Test fun durationsAndOverflow() {
        assertEquals(ClockCommand.Timer(3723), ClockCommandParser.parse("start a timer for 1 hour 2 minutes 3 seconds"))
        listOf("set timer", "set timer for a while", "set timer for 0 seconds", "set timer for 999999999999999999999 hours", "set timer for 1 minute 2 minutes").forEach {
            assertEquals(it, ClockCommand.ShowTimers, ClockCommandParser.parse(it))
        }
    }
    @Test fun recognitionWhitespaceAndPunctuation() {
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("Please set an alarm at 7 am?"))
        assertEquals(ClockCommand.Timer(300), ClockCommandParser.parse("start   a timer for 5 minutes!"))
    }
    @Test fun unrelatedTextIsNotAnAction() {
        listOf(
            "what is an alarm", "do not set an alarm", "explain timers",
            "what time is my alarm in Paris", "when does the alarm go off", "how do timers work",
            "is there an alarm at 7 am", "the fire alarm went off"
        ).forEach { assertNull(it, ClockCommandParser.parse(it)) }
    }

    /** Every clear clock request must be recognised, so it costs no model round trip. */
    @Test fun commonAlarmPhrasingsAreRecognised() {
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("set alarm 7am"))
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("set an alarm for 7 a.m."))
        assertEquals(ClockCommand.Alarm(19, 30), ClockCommandParser.parse("alarm for 7:30 pm"))
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("wake me up at 7 am"))
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("wake me at 7 in the morning"))
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("wake me up at seven am"))
        assertEquals(ClockCommand.Alarm(6, 15), ClockCommandParser.parse("make an alarm at 06:15"))
        assertEquals(ClockCommand.Alarm(8, 0), ClockCommandParser.parse("add an alarm for 8 am"))
        assertEquals(ClockCommand.Alarm(12, 0), ClockCommandParser.parse("set an alarm for noon"))
        assertEquals(ClockCommand.Alarm(6, 30, "Gym"), ClockCommandParser.parse("set an alarm for 6:30 am called gym"))
        assertEquals(ClockCommand.Alarm(6, 30, "Gym"), ClockCommandParser.parse("set an alarm at 6:30 am for gym"))
        // Recognised but ambiguous: open the clock rather than guess.
        assertEquals(ClockCommand.ShowAlarms, ClockCommandParser.parse("wake me up at 7"))
        assertEquals(ClockCommand.ShowAlarms, ClockCommandParser.parse("alarm for 8"))
        // A clock alarm has no date: "7 am tomorrow" is the next 7 am, which is what
        // ACTION_SET_ALARM creates, and the clock UI still shows it for confirmation.
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("set an alarm for 7 am tomorrow"))
    }

    @Test fun relativeAlarmsResolveAgainstNow() {
        assertEquals(ClockCommand.Alarm(12, 0), ClockCommandParser.parse("set an alarm in 20 minutes", now))
        assertEquals(ClockCommand.Alarm(13, 10), ClockCommandParser.parse("alarm in 1 hour 30 minutes", now))
        assertEquals(ClockCommand.Alarm(0, 10), ClockCommandParser.parse("wake me up in half an hour", LocalTime.of(23, 40)))
        assertEquals(ClockCommand.Alarm(11, 41), ClockCommandParser.parse("wake me in 30 seconds", LocalTime.of(11, 40, 30)))
        assertEquals(ClockCommand.ShowAlarms, ClockCommandParser.parse("set an alarm in a while", now))
    }

    @Test fun alarmManagementOpensTheAlarmList() {
        listOf(
            "cancel my alarm", "dismiss the alarm", "delete all my alarms", "turn off my 7 am alarm",
            "show my alarms", "show me my alarms", "list alarms", "what alarms do i have",
            "do i have any alarms set", "open the clock app", "snooze the alarm", "change my alarm"
        ).forEach { assertEquals(it, ClockCommand.ShowAlarms, ClockCommandParser.parse(it)) }
    }

    @Test fun commonTimerPhrasingsAreRecognised() {
        assertEquals(ClockCommand.Timer(300), ClockCommandParser.parse("timer 5 minutes"))
        assertEquals(ClockCommand.Timer(300), ClockCommandParser.parse("timer for 5 minutes"))
        assertEquals(ClockCommand.Timer(90), ClockCommandParser.parse("90 seconds timer"))
        assertEquals(ClockCommand.Timer(600), ClockCommandParser.parse("set a 10 minute timer"))
        assertEquals(ClockCommand.Timer(3600), ClockCommandParser.parse("set timer for 1 hr"))
        assertEquals(ClockCommand.Timer(5400), ClockCommandParser.parse("set a timer for 1h 30m"))
        assertEquals(ClockCommand.Timer(5400), ClockCommandParser.parse("set a timer for an hour and a half"))
        assertEquals(ClockCommand.Timer(300), ClockCommandParser.parse("start a five minute timer"))
        assertEquals(ClockCommand.Timer(1500), ClockCommandParser.parse("set a timer for twenty five minutes"))
        assertEquals(ClockCommand.Timer(65), ClockCommandParser.parse("start a timer for 1 min 5 secs"))
        assertEquals(ClockCommand.Timer(600, "Pasta"), ClockCommandParser.parse("set a 10 minute timer for pasta"))
        assertEquals(ClockCommand.Timer(600, "Pasta"), ClockCommandParser.parse("set a timer for 10 minutes called pasta"))
        assertEquals(ClockCommand.ShowTimers, ClockCommandParser.parse("timer"))
        assertEquals(ClockCommand.ShowTimers, ClockCommandParser.parse("set a timer for a bit"))
        listOf("cancel the timer", "stop my timer", "show timers", "how much time is left on my timer").forEach {
            assertEquals(it, ClockCommand.ShowTimers, ClockCommandParser.parse(it))
        }
    }

    /** Mid-conversation, recognition arrives with filler the patterns must not depend on. */
    @Test fun conversationalFillerIsIgnored() {
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("Ok now can you set an alarm for 7 am please"))
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("Hey, also, I need you to wake me up at 7 am, thanks"))
        assertEquals(ClockCommand.Timer(600), ClockCommandParser.parse("and then start a timer for 10 minutes for me"))
    }
}
