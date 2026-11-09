package com.materialchat.assistant.actions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock

/** Requests clock UI only; launching does not prove that an alarm or timer was saved. */
object ClockHandoff {
    fun intent(command: ClockCommand): Intent = Intent(when (command) {
        is ClockCommand.Alarm -> AlarmClock.ACTION_SET_ALARM
        is ClockCommand.Timer -> AlarmClock.ACTION_SET_TIMER
        ClockCommand.ShowAlarms -> AlarmClock.ACTION_SHOW_ALARMS
        ClockCommand.ShowTimers -> AlarmClock.ACTION_SHOW_TIMERS
    }).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        when (command) {
            is ClockCommand.Alarm -> {
                putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
            }
            is ClockCommand.Timer -> putExtra(AlarmClock.EXTRA_LENGTH, command.seconds)
            else -> Unit
        }
    }

    /**
     * [start] is normally [android.service.voice.VoiceInteractionSession.startAssistantActivity],
     * which throws more than the two obvious exceptions: the platform throws
     * [IllegalStateException] when the session is not the active or shown session, and
     * [android.util.AndroidRuntimeException] for cancelled or unrecognised start results.
     * Those used to escape into the session's event collector and kill the overlay, so a
     * clock request looked like it did nothing at all.
     */
    fun launch(command: ClockCommand, start: (Intent) -> Unit): String? = try {
        start(intent(command))
        null
    } catch (_: ActivityNotFoundException) {
        "No clock app is available to handle this request."
    } catch (_: SecurityException) {
        "Android did not allow opening the clock app."
    } catch (_: IllegalStateException) {
        "The assistant session is no longer active, so the clock app could not be opened."
    } catch (_: RuntimeException) {
        "Android could not open the clock app from the assistant."
    }
}
