/*
 * The host-side gates every continuously animated decoration shares: the composition's
 * lifecycle (STARTED or not), the platform power-saver switch and the animator duration scale.
 * One observer set per host, reused by every animated particle surface, so
 * there is exactly one definition of "may this surface keep requesting frames".
 */
package com.materialchat.ui.theme.particles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Observable host state that decides whether a decoration may run a continuous frame clock. */
@Stable
class ParticleMotionGate internal constructor(started: Boolean, powerSave: Boolean, animationScale: Float) {
    /** The composition's lifecycle is at least STARTED (the window is shown). */
    var started: Boolean by mutableStateOf(started)
        internal set

    /** The platform's battery saver is on. */
    var powerSave: Boolean by mutableStateOf(powerSave)
        internal set

    /** Settings.Global.ANIMATOR_DURATION_SCALE; 0 means animations are disabled. */
    var animationScale: Float by mutableStateOf(animationScale)
        internal set

    /** Whether continuous motion may run on this host right now. */
    val motionAllowed: Boolean
        get() = started && !powerSave && animationScale.isFinite() && animationScale > 0f
}

/**
 * The gate for the current composition. It follows the composition's lifecycle owner (not the
 * view tree's): that defaults to the window owner but honours an overriding provider, so a host
 * that stops its content stops every frame loop behind this gate. Power-saver and animator-scale
 * changes are picked up from their broadcasts.
 */
@Composable
fun rememberParticleMotionGate(): ParticleMotionGate {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gate = remember(context, view, lifecycle) {
        ParticleMotionGate(
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true,
            animationScale = readAnimationScale(context)
        )
    }
    DisposableEffect(gate, lifecycle) {
        fun update() {
            gate.started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(gate, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                gate.powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                gate.animationScale = readAnimationScale(context)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction("android.intent.action.ANIMATOR_DURATION_SCALE_CHANGED")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return gate
}
