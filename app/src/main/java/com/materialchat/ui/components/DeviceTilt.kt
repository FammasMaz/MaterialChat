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
 * - [roll]: left/right tilt of the phone (positive = tilted right)
 * - [pitch]: top/back tilt of the phone (positive = top edge tipped toward user)
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
 * Remember a live stream of the device's physical tilt driven by the
 * rotation-vector sensor (gyroscope-fused, so it does not drift like raw
 * gyro integration would).
 *
 * Design notes:
 * - Sampling runs only while the lifecycle is RESUMED (battery-safe).
 * - Raw sensor data is exponential-smoothed so surfaces glide instead of jitter.
 * - Output is clamped to +/- [maxDegrees] and dead-zoned near neutral so tiny
 *   hand tremors do not move the UI.
 *
 * Apply the result with graphicsLayer rotationX/rotationY or translation
 * offsets at call sites that want physical parallax.
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

        // No rotation vector (rare): fall back to accelerometer+gravity estimate.
        val fallbackAccelerometer = if (rotationVector == null) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else null

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        var lastEmitMs = 0L

        fun publish(pitchDeg: Float, rollDeg: Float) {
            // Throttle emissions to ~30fps; sensors fire far faster than needed.
            val now = System.currentTimeMillis()
            if (now - lastEmitMs < 33) return
            lastEmitMs = now

            val previous = pose.value
            val targetPitch = clampDeadzone(pitchDeg, maxDegrees)
            val targetRoll = clampDeadzone(rollDeg, maxDegrees)

            // Exponential smoothing: heavy enough to feel premium, light
            // enough to stay responsive (alpha ≈ 0.25 per frame @30fps).
            val alpha = 0.25f
            val smoothedPitch = previous.pitch + (targetPitch - previous.pitch) * alpha
            val smoothedRoll = previous.roll + (targetRoll - previous.roll) * alpha

            if (kotlin.math.abs(smoothedPitch - previous.pitch) > 0.05f ||
                kotlin.math.abs(smoothedRoll - previous.roll) > 0.05f
            ) {
                pose.value = TiltPose(smoothedPitch, smoothedRoll)
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        // Remap for natural portrait holding: pitch from the X
                        // rotation, roll from the Y rotation.
                        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                        publish(pitchDeg.coerceIn(-45f, 45f), rollDeg.coerceIn(-45f, 45f))
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        val gx = event.values[0] / SensorManager.GRAVITY_EARTH
                        val gy = event.values[1] / SensorManager.GRAVITY_EARTH
                        val gz = event.values[2] / SensorManager.GRAVITY_EARTH
                        val pitchDeg = Math.toDegrees(Math.atan2(gy.toDouble(), kotlin.math.sqrt((gx * gx + gz * gz).toDouble()))).toFloat()
                        val rollDeg = Math.toDegrees(Math.atan2(gx.toDouble(), gz.toDouble())).toFloat()
                        publish(pitchDeg.coerceIn(-45f, 45f), rollDeg.coerceIn(-45f, 45f))
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun register() {
            if (rotationVector != null) {
                sensorManager?.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
            }
            if (fallbackAccelerometer != null) {
                sensorManager?.registerListener(listener, fallbackAccelerometer, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        fun unregister() {
            sensorManager?.unregisterListener(listener)
            pose.value = TiltPose.Neutral
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
