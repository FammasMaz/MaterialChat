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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A normalized [0..1] "shake energy" value that spikes when the phone is
 * physically shaken and decays smoothly back to zero — used to give M3
 * Expressive surfaces a playful kick that answers vigorous motion.
 *
 * Pairs well with [rememberDeviceTilt]: tilt for gentle parallax, shake
 * for bursts.
 */
@Composable
fun rememberShakeEnergy(spikeThreshold: Float = 4.5f): State<Float> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val energy = remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    DisposableEffect(context, lifecycleOwner, spikeThreshold, scope) {
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

        fun register() {
            accelerometer?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager?.unregisterListener(listener)
                    energy.value = 0f
                }
                else -> Unit
            }
        }

        // Register immediately if already resumed — observers do not replay events.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Frame-paced drain loop bleeds energy back to zero smoothly.
        val drainJob: Job = scope.launch {
            while (true) {
                withFrameNanos { }
                if (energy.value > 0.004f) {
                    energy.value *= 0.93f
                } else if (energy.value != 0f) {
                    energy.value = 0f
                }
            }
        }

        onDispose {
            drainJob.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager?.unregisterListener(listener)
        }
    }

    return energy
}
