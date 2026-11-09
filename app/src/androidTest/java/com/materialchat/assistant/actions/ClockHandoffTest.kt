package com.materialchat.assistant.actions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Intent extras; does not launch or create actual alarms. */
@RunWith(AndroidJUnit4::class)
class ClockHandoffTest {
    @Test fun valuesAndConfirmationUi() {
        val alarm = ClockHandoff.intent(ClockCommand.Alarm(19, 30))
        assertEquals(AlarmClock.ACTION_SET_ALARM, alarm.action)
        assertEquals(19, alarm.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, alarm.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        val timer = ClockHandoff.intent(ClockCommand.Timer(90))
        assertEquals(AlarmClock.ACTION_SET_TIMER, timer.action)
        assertEquals(90, timer.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        for (intent in listOf(alarm, timer)) {
            assertTrue(intent.hasExtra(AlarmClock.EXTRA_SKIP_UI))
            assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
    }

    @Test fun ambiguousCommandsCarryNoInventedValues() {
        val alarms = ClockHandoff.intent(ClockCommand.ShowAlarms)
        val timers = ClockHandoff.intent(ClockCommand.ShowTimers)
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, alarms.action)
        assertEquals(AlarmClock.ACTION_SHOW_TIMERS, timers.action)
        for (intent in listOf(alarms, timers)) {
            assertFalse(intent.hasExtra(AlarmClock.EXTRA_HOUR))
            assertFalse(intent.hasExtra(AlarmClock.EXTRA_MINUTES))
            assertFalse(intent.hasExtra(AlarmClock.EXTRA_LENGTH))
        }
    }

    @Test fun unavailableAndDeniedLaunchesAreReported() {
        assertNotNull(ClockHandoff.launch(ClockCommand.ShowAlarms) { throw ActivityNotFoundException() })
        assertNotNull(ClockHandoff.launch(ClockCommand.ShowTimers) { throw SecurityException() })
        var launched = false
        assertNull(ClockHandoff.launch(ClockCommand.ShowTimers) { launched = true })
        assertTrue(launched)
    }

    /**
     * VoiceInteractionSession.startAssistantActivity throws IllegalStateException when the
     * session is not the active or shown session, and AndroidRuntimeException for cancelled
     * or unrecognised start results. Those escaped into the session's event collector and
     * killed the overlay, so the clock request appeared to do nothing.
     */
    @Test fun inactiveSessionFailuresAreReportedInsteadOfThrown() {
        val inactive = ClockHandoff.launch(ClockCommand.Alarm(7, 0)) {
            throw IllegalStateException("Session calling startAssistantActivity does not match active session")
        }
        assertNotNull(inactive)

        val cancelled = ClockHandoff.launch(ClockCommand.Timer(60)) {
            throw android.util.AndroidRuntimeException("Activity could not be started")
        }
        assertNotNull(cancelled)
    }
}
