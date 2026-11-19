package com.materialchat.ui.components

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision shared by [rememberDeviceTilt] and [rememberShakeEnergy]: a sensor may be
 * registered only while the host is RESUMED and the decoration motion gate allows motion.
 */
class SensorMotionGateTest {

    @Test
    fun `already resumed host with motion allowed samples immediately`() {
        // The observer-attach case: no ON_RESUME will ever replay, so the current state decides.
        assertTrue(SensorMotionGate.shouldSample(Lifecycle.State.RESUMED, motionAllowed = true))
    }

    @Test
    fun `motion gate off blocks sampling even when resumed`() {
        // Animator scale 0, battery saver or reduced motion all collapse to motionAllowed=false.
        assertFalse(SensorMotionGate.shouldSample(Lifecycle.State.RESUMED, motionAllowed = false))
    }

    @Test
    fun `not resumed blocks sampling regardless of the gate`() {
        for (state in listOf(
            Lifecycle.State.INITIALIZED,
            Lifecycle.State.CREATED,
            Lifecycle.State.STARTED,
            Lifecycle.State.DESTROYED
        )) {
            assertFalse("$state", SensorMotionGate.shouldSample(state, motionAllowed = true))
            assertFalse("$state", SensorMotionGate.shouldSample(state, motionAllowed = false))
        }
    }

    @Test
    fun `lifecycle events resolve through their target state`() {
        assertTrue(SensorMotionGate.shouldSample(Lifecycle.Event.ON_RESUME, motionAllowed = true))
        assertFalse(SensorMotionGate.shouldSample(Lifecycle.Event.ON_RESUME, motionAllowed = false))
        assertFalse(SensorMotionGate.shouldSample(Lifecycle.Event.ON_PAUSE, motionAllowed = true))
        assertFalse(SensorMotionGate.shouldSample(Lifecycle.Event.ON_STOP, motionAllowed = true))
        assertFalse(SensorMotionGate.shouldSample(Lifecycle.Event.ON_START, motionAllowed = true))
        assertFalse(SensorMotionGate.shouldSample(Lifecycle.Event.ON_DESTROY, motionAllowed = true))
    }
}
