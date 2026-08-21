package com.materialchat.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A physical device orientation snapshot, low-pass filtered and clamped,
 * used to drive M3 Expressive "alive in your hand" parallax and 3D tilt.
 *
 * Values are in degrees relative to a comfortable reading posture:
 * - [pitch]: top/back tilt of the phone (positive = top edge tipped away)
 * - [roll]: left/right tilt of the phone (positive = tilted right)
 */
data class TiltPose(
    val pitch: Float = 0f,
    val roll: Float = 0f
) {
    companion object {
        val Neutral = TiltPose()
    }
}

/**
 * Remember a live stream of the device's physical tilt.
 *
 * Prefers the rotation-vector sensor; falls back to the accelerometer
 * inclinometer (works on every device including emulators).
 *
 * Design notes:
 * - Sampling starts immediately if the lifecycle is already RESUMED — adding
 *   an observer does NOT replay past events, so waiting for ON_RESUME would
 *   never fire for a screen entered while the app is foregrounded.
 * - Raw sensor data is exponential-smoothed so surfaces glide instead of jitter.
 * - Output is clamped to +/- [maxDegrees] with a small dead zone so hand tremor
 *   does not move the UI.
 */
@Composable
fun rememberDeviceTilt(maxDegrees: Float = 12f): State<TiltPose> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pose = remember { mutableStateOf(TiltPose.Neutral) }

    DisposableEffect(context, lifecycleOwner, maxDegrees) {
        val sensorManager =
            context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Prefer rotation vector (no gravity-flip weirdness), else accelerometer.
        val primarySensor = rotationVector ?: accelerometer ?: return@DisposableEffect onDispose { }

        val rotationMatrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientationAngles = FloatArray(3)
        var lastEmitMs = 0L

        fun publish(rawPitch: Float, rawRoll: Float) {
            // Throttle to ~60fps max; sensors can fire at 500Hz.
            val now = System.nanoTime() / 1_000_000L
            if (now - lastEmitMs < 16) return
            lastEmitMs = now

            val targetPitch = clampDeadzone(rawPitch, maxDegrees)
            val targetRoll = clampDeadzone(rawRoll, maxDegrees)

            val previous = pose.value
            // Exponential smoothing tuned responsive (~120ms settle).
            val alpha = 0.35f
            val smoothPitch = previous.pitch + (targetPitch - previous.pitch) * alpha
            val smoothRoll = previous.roll + (targetRoll - previous.roll) * alpha

            if (kotlin.math.abs(smoothPitch - previous.pitch) > 0.02f ||
                kotlin.math.abs(smoothRoll - previous.roll) > 0.02f
            ) {
                pose.value = TiltPose(smoothPitch, smoothRoll)
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        // Remap so pitch/roll are relative to natural portrait hold.
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_X,
                            SensorManager.AXIS_Z,
                            remapped
                        )
                        SensorManager.getOrientation(remapped, orientationAngles)
                        val pitchDeg =
                            Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                        val rollDeg =
                            Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                        publish(pitchDeg.coerceIn(-45f, 45f), rollDeg.coerceIn(-45f, 45f))
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        // Inclinometer from gravity: robust and universally available.
                        val gx = event.values[0]
                        val gy = event.values[1]
                        val gz = event.values[2]
                        val pitchDeg = Math.toDegrees(
                            kotlin.math.atan2(gy.toDouble(), kotlin.math.sqrt(gx * gx + gz * gz.toDouble()))
                        ).toFloat()
                        val rollDeg = Math.toDegrees(kotlin.math.atan2(gx.toDouble(), gz.toDouble())).toFloat()
                        publish(pitchDeg.coerceIn(-45f, 45f), rollDeg.coerceIn(-45f, 45f))
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun register() {
            sensorManager?.registerListener(listener, primarySensor, SensorManager.SENSOR_DELAY_UI)
        }

        fun unregister() {
            sensorManager?.unregisterListener(listener)
            pose.value = TiltPose.Neutral
        }

        // CRITICAL: addObserver does not replay past lifecycle events. If we are
        // composed while already resumed (the normal case), start sampling now.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    return pose
}

/**
 * Dead-zone + clamp: ignore tremor-scale motion near neutral, saturate past
 * the expressive maximum so extreme tilts never distort layouts.
 */
private fun clampDeadzone(value: Float, maxDegrees: Float): Float {
    val deadzone = 1.2f
    if (value < deadzone && value > -deadzone) return 0f
    return value.coerceIn(-maxDegrees, maxDegrees)
}
