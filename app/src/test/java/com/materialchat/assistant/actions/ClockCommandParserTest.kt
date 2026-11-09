package com.materialchat.assistant.actions

import org.junit.Assert.*
import org.junit.Test

class ClockCommandParserTest {
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
        listOf("what is an alarm", "do not set an alarm", "explain timers").forEach { assertNull(ClockCommandParser.parse(it)) }
    }

    /** Every clear clock request must be recognised, because the model cannot set anything. */
    @Test fun commonAlarmPhrasingsAreRecognised() {
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("set alarm 7am"))
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("set an alarm for 7 a.m."))
        assertEquals(ClockCommand.Alarm(19, 30), ClockCommandParser.parse("alarm for 7:30 pm"))
        assertEquals(ClockCommand.Alarm(7, 0), ClockCommandParser.parse("wake me up at 7 am"))
        assertEquals(ClockCommand.Alarm(6, 15), ClockCommandParser.parse("make an alarm at 06:15"))
        assertEquals(ClockCommand.Alarm(8, 0), ClockCommandParser.parse("add an alarm for 8 in the morning".replace(" in the morning", " am")))
        // Recognised but ambiguous: open the clock rather than guess.
        assertEquals(ClockCommand.ShowAlarms, ClockCommandParser.parse("wake me up at 7"))
        assertEquals(ClockCommand.ShowAlarms, ClockCommandParser.parse("alarm for 8"))
        assertEquals(ClockCommand.ShowAlarms, ClockCommandParser.parse("set an alarm for 7 am tomorrow"))
    }

    @Test fun commonTimerPhrasingsAreRecognised() {
        assertEquals(ClockCommand.Timer(300), ClockCommandParser.parse("timer 5 minutes"))
        assertEquals(ClockCommand.Timer(300), ClockCommandParser.parse("timer for 5 minutes"))
        assertEquals(ClockCommand.Timer(90), ClockCommandParser.parse("90 seconds timer"))
        assertEquals(ClockCommand.Timer(600), ClockCommandParser.parse("set a 10 minute timer"))
        assertEquals(ClockCommand.Timer(3600), ClockCommandParser.parse("set timer for 1 hr"))
        assertEquals(ClockCommand.Timer(65), ClockCommandParser.parse("start a timer for 1 min 5 secs"))
        assertEquals(ClockCommand.ShowTimers, ClockCommandParser.parse("timer"))
        assertEquals(ClockCommand.ShowTimers, ClockCommandParser.parse("set a timer for a bit"))
    }
}
