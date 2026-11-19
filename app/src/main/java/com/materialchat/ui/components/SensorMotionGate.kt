package com.materialchat.ui.components

import androidx.lifecycle.Lifecycle

/**
 * The single decision for whether an input-driven decoration sensor (device tilt, shake
 * energy) may hold a registration right now.
 *
 * Two conditions, both required:
 * - the host lifecycle is RESUMED (not merely STARTED: a paused window must not keep the
 *   accelerometer awake), and
 * - the decoration motion gate allows motion (animator scale > 0, no battery saver, host
 *   started). Tilt parallax and shake kicks are decoration, so they follow the same gate
 *   as every continuous animation; while gated the pose rests at neutral and no sensor is
 *   registered.
 *
 * Callers evaluate this once against the *current* state when the observer is attached
 * (observers added while already RESUMED never see ON_RESUME) and again for every lifecycle
 * event via [Lifecycle.Event.getTargetState].
 */
object SensorMotionGate {
    fun shouldSample(lifecycleState: Lifecycle.State, motionAllowed: Boolean): Boolean =
        motionAllowed && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    fun shouldSample(event: Lifecycle.Event, motionAllowed: Boolean): Boolean =
        shouldSample(event.targetState, motionAllowed)
}
