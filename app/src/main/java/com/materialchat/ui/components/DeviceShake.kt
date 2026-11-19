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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.materialchat.ui.theme.particles.rememberParticleMotionGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A normalized [0..1] "shake energy" value that spikes when the phone is
 * physically shaken and decays smoothly back to zero — used to give M3
 * Expressive surfaces a playful kick that answers vigorous motion.
 *
 * Pairs well with [rememberDeviceTilt]: tilt for gentle parallax, shake
 * for bursts.
 *
 * The accelerometer is held only while [SensorMotionGate.shouldSample] allows it
 * (host RESUMED and the decoration motion gate open); otherwise the listener is
 * unregistered and the energy rests at zero.
 */
@Composable
fun rememberShakeEnergy(spikeThreshold: Float = 4.5f): State<Float> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val energy = remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val motionAllowed = rememberParticleMotionGate().motionAllowed

    DisposableEffect(context, lifecycleOwner, spikeThreshold, scope, motionAllowed) {
        val sensorManager =
            context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var gravityEstimate = 9.81f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                // Low-pass gravity estimate; remainder is user-induced motion.
                gravityEstimate += (kotlin.math.sqrt(x * x + y * y + z * z) - gravityEstimate) * 0.08f
                val dynamic = kotlin.math.abs(
                    kotlin.math.sqrt(x * x + y * y + z * z) - gravityEstimate
                )
                if (dynamic > spikeThreshold) {
                    val kick = ((dynamic - spikeThreshold) / spikeThreshold).coerceIn(0f, 1f)
                    energy.value = (energy.value + kick * 0.55f).coerceIn(0f, 1f)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var registered = false

        fun register() {
            if (registered) return
            accelerometer?.let {
                registered = true
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        fun unregister() {
            if (!registered) return
            registered = false
            sensorManager?.unregisterListener(listener)
            energy.value = 0f
        }

        fun apply(sample: Boolean) = if (sample) register() else unregister()

        val observer = LifecycleEventObserver { _, event ->
            apply(SensorMotionGate.shouldSample(event, motionAllowed))
        }

        // Decide from the current state now — observers do not replay events. The gate
        // value is an effect key, so a gate flip re-runs this block.
        apply(SensorMotionGate.shouldSample(lifecycleOwner.lifecycle.currentState, motionAllowed))
        lifecycleOwner.lifecycle.addObserver(observer)

        // Frame-paced drain loop bleeds energy back to zero smoothly. While there is nothing to
        // bleed it waits instead of requesting a frame: an unconditional withFrameNanos here kept
        // the home screen rendering every vsync forever, even with the phone lying still.
        val drainJob: Job = scope.launch {
            while (true) {
                if (energy.value > 0.004f) {
                    withFrameNanos { }
                    energy.value *= 0.93f
                } else if (energy.value != 0f) {
                    energy.value = 0f
                } else {
                    delay(50)
                }
            }
        }

        onDispose {
            drainJob.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    return energy
}
