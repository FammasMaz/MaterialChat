package com.materialchat.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptic feedback patterns for different interactions.
 */
enum class HapticPattern {
    /** Light tap feedback for button clicks */
    CLICK,
    /** Medium feedback for long press actions */
    LONG_PRESS,
    /** Strong feedback when crossing swipe thresholds */
    SWIPE_THRESHOLD,
    /** Subtle tick for confirmations */
    CONFIRM,
    /** Double tap pattern for emphasis */
    EMPHASIS
}

/**
 * Manager class for handling haptic feedback throughout the app.
 *
 * This class provides a unified API for triggering haptic feedback,
 * supporting both standard Compose haptics and custom vibration patterns.
 *
 * @param context Android context for accessing the vibrator service
 * @param hapticFeedback Compose haptic feedback for standard patterns
 */
class HapticFeedbackManager(
    private val context: Context,
    private val hapticFeedback: HapticFeedback
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Perform haptic feedback with the specified pattern.
     *
     * @param pattern The haptic pattern to perform
     * @param enabled Whether haptic feedback is enabled (from user preferences)
     */
    fun perform(pattern: HapticPattern, enabled: Boolean = true) {
        if (!enabled) return

        when (pattern) {
            HapticPattern.CLICK -> performClick()
            HapticPattern.LONG_PRESS -> performLongPress()
            HapticPattern.SWIPE_THRESHOLD -> performSwipeThreshold()
            HapticPattern.CONFIRM -> performConfirm()
            HapticPattern.EMPHASIS -> performEmphasis()
        }
    }

    /**
     * Light tap feedback for button clicks.
     */
    private fun performClick() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /**
     * Medium feedback for long press actions.
     */
    private fun performLongPress() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Strong feedback when crossing swipe thresholds.
     * Uses custom vibration for a more pronounced effect.
     */
    private fun performSwipeThreshold() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    /**
     * Subtle tick for confirmations.
     */
    private fun performConfirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            )
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Double tap pattern for emphasis.
     */
    private fun performEmphasis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create a double-tap pattern manually
            val timings = longArrayOf(0, 30, 50, 30)
            val amplitudes = intArrayOf(0, 128, 0, 128)
            vibrator?.vibrate(
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 30, 50, 30), -1)
        }
    }
}

/**
 * Remember and provide a HapticFeedbackManager for the current composition.
 *
 * Usage:
 * ```
 * val haptics = rememberHapticFeedback()
 * 
 * Button(onClick = {
 *     haptics.perform(HapticPattern.CLICK, enabled = hapticsEnabled)
 *     // ... rest of click handler
 * }) {
 *     Text("Click me")
 * }
 * ```
 *
 * @return A HapticFeedbackManager instance
 */
@Composable
fun rememberHapticFeedback(): HapticFeedbackManager {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    
    return remember(context, hapticFeedback) {
        HapticFeedbackManager(context, hapticFeedback)
    }
}
