package com.materialchat.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback patterns for different interactions.
 *
 * These patterns are designed to leverage the best available haptic APIs
 * on each Android version, with graceful fallbacks for older devices.
 *
 * ALL patterns are ultra-crisp with NO rumble/buzzy vibrations allowed.
 */
enum class HapticPattern {
    /** Light tap feedback for button clicks - crisp and subtle */
    CLICK,

    /** Medium feedback for long press actions */
    LONG_PRESS,

    /** Strong feedback when crossing swipe thresholds */
    SWIPE_THRESHOLD,

    /** Subtle tick for confirmations and successful actions */
    CONFIRM,

    /** Feedback for rejected actions or errors */
    REJECT,

    /** Double tap pattern for emphasis */
    EMPHASIS,

    /** Feedback when starting a gesture (drag, swipe) */
    GESTURE_START,

    /** Feedback when ending a gesture */
    GESTURE_END,

    /** Very light tick for scrolling through segments/items */
    SEGMENT_TICK,

    /** Keyboard press feedback */
    KEYBOARD_TAP,

    /** Toggle switch feedback */
    TOGGLE,

    /** Ultra-light tick for AI thinking/reasoning chunks - barely perceptible */
    THINKING_TICK,

    /** Slightly more pronounced tick for AI content chunks - crisp but not intrusive */
    CONTENT_TICK,

    /** Subtle morph transition feedback for M3 Expressive shape morphing animations */
    MORPH_TRANSITION
}

/**
 * Cutting-edge haptic feedback manager for Android.
 *
 * This class provides a unified API for triggering haptic feedback,
 * leveraging the most modern APIs available on each device:
 *
 * - **API 34+ (Android 14)**: Native HapticFeedbackConstants (CONFIRM, REJECT, DRAG_START, etc.)
 * - **API 30+ (Android 11)**: VibrationEffect.Composition with primitives for premium haptics
 * - **API 29+ (Android 10)**: VibrationEffect.createPredefined for standard effects
 * - **API 26+ (Android 8)**: VibrationEffect for basic patterns
 *
 * All methods include proper capability checks and graceful fallbacks.
 *
 * @param context Android context for accessing the vibrator service
 * @param hapticFeedback Compose haptic feedback for standard patterns
 * @param view Android View for native HapticFeedbackConstants (API 34+)
 */
class HapticFeedbackManager(
    private val context: Context,
    private val hapticFeedback: HapticFeedback,
    private val view: View? = null
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

    /** Whether the device has a vibrator */
    private val hasVibrator: Boolean by lazy {
        vibrator?.hasVibrator() == true
    }

    /** Whether the device supports amplitude control for richer haptics */
    private val hasAmplitudeControl: Boolean by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.hasAmplitudeControl() == true
        } else {
            false
        }
    }

    /** Cache for checking primitive support (API 30+) */
    private val supportedPrimitives: Set<Int> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null) {
            checkSupportedPrimitives()
        } else {
            emptySet()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkSupportedPrimitives(): Set<Int> {
        val primitives = listOf(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_SPIN,
            VibrationEffect.Composition.PRIMITIVE_THUD
        )
        return primitives.filter { primitive ->
            vibrator?.arePrimitivesSupported(primitive)?.get(0) == true
        }.toSet()
    }

    /**
     * Check if a specific predefined effect is supported (API 29+).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun isEffectSupported(effectId: Int): Boolean {
        return vibrator?.areEffectsSupported(effectId)?.get(0) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
    }

    /**
     * Perform haptic feedback with the specified pattern.
     *
     * @param pattern The haptic pattern to perform
     * @param enabled Whether haptic feedback is enabled (from user preferences)
     */
    fun perform(pattern: HapticPattern, enabled: Boolean = true) {
        if (!enabled || !hasVibrator) return

        when (pattern) {
            HapticPattern.CLICK -> performClick()
            HapticPattern.LONG_PRESS -> performLongPress()
            HapticPattern.SWIPE_THRESHOLD -> performSwipeThreshold()
            HapticPattern.CONFIRM -> performConfirm()
            HapticPattern.REJECT -> performReject()
            HapticPattern.EMPHASIS -> performEmphasis()
            HapticPattern.GESTURE_START -> performGestureStart()
            HapticPattern.GESTURE_END -> performGestureEnd()
            HapticPattern.SEGMENT_TICK -> performSegmentTick()
            HapticPattern.KEYBOARD_TAP -> performKeyboardTap()
            HapticPattern.TOGGLE -> performToggle()
            HapticPattern.THINKING_TICK -> performThinkingTick()
            HapticPattern.CONTENT_TICK -> performContentTick()
            HapticPattern.MORPH_TRANSITION -> performMorphTransition()
        }
    }

    /**
     * Light tap feedback for button clicks.
     * Uses PRIMITIVE_CLICK composition for the crispest feel on modern devices.
     */
    private fun performClick() {
        when {
            // API 30+: Use composition with PRIMITIVE_CLICK for premium feel
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_CLICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                        .compose()
                )
            }
            // API 29+: Use predefined EFFECT_CLICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            }
            // Fallback to Compose haptics
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Medium feedback for long press actions.
     */
    private fun performLongPress() {
        // API 34+: Use native LONG_PRESS constant via View
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Strong feedback when crossing swipe thresholds.
     * Uses PRIMITIVE_CLICK at maximum intensity for a crisp, strong tap.
     * NO rumble/thud - just a sharp, decisive click.
     */
    private fun performSwipeThreshold() {
        when {
            // API 30+: Use CLICK at full intensity for strong but crisp feedback
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_CLICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                        .compose()
                )
            }
            // API 29+: Use EFFECT_HEAVY_CLICK (still crisp on Pixel)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                )
            }
            // Fallback to Compose long press (crisp, not buzzy)
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    /**
     * Subtle tick for confirmations and successful actions.
     * Uses native CONFIRM constant on Android 14+ for system-consistent feedback.
     */
    private fun performConfirm() {
        when {
            // API 34+: Native CONFIRM haptic
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            // API 30+: Use composition with rising effect for "success" feel
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 50)
                        .compose()
                )
            }
            // API 29+: Use EFFECT_TICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            }
            // Fallback
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Feedback for rejected actions or errors.
     * Uses native REJECT constant on Android 14+ for system-consistent feedback.
     * All patterns are crisp - NO rumble or buzzy vibrations.
     */
    private fun performReject() {
        when {
            // API 34+: Native REJECT haptic
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null -> {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
            // API 30+: Use double CLICK for crisp "error" feel
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_CLICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 60)
                        .compose()
                )
            }
            // API 29+: Double click for emphasis
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                )
            }
            // Fallback to Compose haptics (no waveforms!)
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    /**
     * Double tap pattern for emphasis.
     * Uses composition for precise timing on modern devices.
     * All patterns are crisp - NO rumble or buzzy vibrations.
     */
    private fun performEmphasis() {
        when {
            // API 30+: Use composition for precise double-tap
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_CLICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 80)
                        .compose()
                )
            }
            // API 29+: Use predefined EFFECT_DOUBLE_CLICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                )
            }
            // Fallback to Compose haptics (no waveforms!)
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    /**
     * Feedback when starting a gesture (drag, swipe).
     * Uses native GESTURE_START on Android 14+.
     */
    private fun performGestureStart() {
        when {
            // API 34+: Native GESTURE_START
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null -> {
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
            }
            // API 30+: Use SLOW_RISE for anticipation feel
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.4f)
                        .compose()
                )
            }
            // Fallback: Light tick
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            }
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Feedback when ending a gesture.
     * Uses native GESTURE_END on Android 14+.
     */
    private fun performGestureEnd() {
        when {
            // API 34+: Native GESTURE_END
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null -> {
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
            }
            // API 30+: Use QUICK_FALL for completion feel
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.5f)
                        .compose()
                )
            }
            // Fallback: Click effect
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            }
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Very light tick for scrolling through segments/items.
     * Uses PRIMITIVE_LOW_TICK for the subtlest feedback.
     */
    private fun performSegmentTick() {
        when {
            // API 31+: Use LOW_TICK for very subtle feedback
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f)
                        .compose()
                )
            }
            // API 30+: Use regular TICK at low intensity
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_TICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.2f)
                        .compose()
                )
            }
            // API 29+: Use EFFECT_TICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            }
            // Fallback
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Keyboard press feedback.
     * Uses native keyboard constants for system-consistent typing feel.
     */
    private fun performKeyboardTap() {
        when {
            // API 34+: Use KEYBOARD_PRESS via View
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            }
            // API 27+: Use KEYBOARD_PRESS via View
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && view != null -> {
                @Suppress("DEPRECATION")
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            // Fallback to light tick
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_TICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                        .compose()
                )
            }
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Toggle switch feedback.
     * Provides distinct on/off feel using composition.
     */
    private fun performToggle() {
        when {
            // API 34+: Use TOGGLE_ON via View (simulates toggle state change)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            // API 30+: Use composition for snappy toggle feel
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_CLICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 30)
                        .compose()
                )
            }
            // API 29+: Simple click
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            }
            // Fallback
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Ultra-light tick for AI thinking/reasoning chunks.
     * Barely perceptible - the lightest possible haptic feedback.
     * Uses PRIMITIVE_LOW_TICK at minimum intensity for modern devices.
     */
    private fun performThinkingTick() {
        when {
            // API 31+: Use LOW_TICK at very low intensity - barely perceptible
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.15f)
                        .compose()
                )
            }
            // API 30+: Use TICK at very low intensity
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_TICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.1f)
                        .compose()
                )
            }
            // API 29+: Use EFFECT_TICK (lightest predefined)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            }
            // Fallback: Use Compose haptics (TextHandleMove is very light)
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Slightly more pronounced tick for AI content chunks.
     * Crisp and tight but not intrusive - noticeably stronger than thinking tick.
     * Uses PRIMITIVE_TICK at moderate intensity for modern devices.
     */
    private fun performContentTick() {
        when {
            // API 30+: Use TICK at moderate intensity - crisp and noticeable
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_TICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                        .compose()
                )
            }
            // API 29+: Use EFFECT_TICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            }
            // Fallback
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    /**
     * Subtle morph transition feedback for M3 Expressive shape morphing animations.
     * Uses PRIMITIVE_QUICK_RISE at low intensity for a smooth "transformation" feel.
     * Triggered once when entering loading/morphing state.
     */
    private fun performMorphTransition() {
        when {
            // API 30+: Use QUICK_RISE for a subtle "building up" feel
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.3f)
                        .compose()
                )
            }
            // API 30+ fallback: Use TICK at low intensity
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                supportedPrimitives.contains(VibrationEffect.Composition.PRIMITIVE_TICK) -> {
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.25f)
                        .compose()
                )
            }
            // API 29+: Use EFFECT_TICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            }
            // Fallback
            else -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

/**
 * Remember and provide a HapticFeedbackManager for the current composition.
 *
 * This composable provides access to cutting-edge haptic APIs while
 * maintaining backwards compatibility. On modern Pixel devices (6+),
 * you'll get premium haptic experiences using VibrationEffect.Composition
 * primitives and Android 14+ HapticFeedbackConstants.
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
 * @return A HapticFeedbackManager instance configured for the current composition
 */
@Composable
fun rememberHapticFeedback(): HapticFeedbackManager {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current

    return remember(context, hapticFeedback, view) {
        HapticFeedbackManager(context, hapticFeedback, view)
    }
}
