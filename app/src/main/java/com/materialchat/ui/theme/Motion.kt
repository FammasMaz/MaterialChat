package com.materialchat.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing

/**
 * Material 3 Expressive Motion specifications for MaterialChat.
 *
 * The motion system defines animation parameters for various UI interactions.
 * Material 3 Expressive emphasizes spring-based physics for natural,
 * playful animations.
 */
object MaterialChatMotion {

    /**
     * Spring specifications for different animation needs.
     */
    object Springs {

        /**
         * Default spring for most animations.
         * Balanced between responsiveness and smoothness.
         */
        val Default = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )

        /**
         * Snappy spring for quick interactions like button presses.
         */
        val Snappy = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )

        /**
         * Gentle spring for subtle animations.
         */
        val Gentle = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )

        /**
         * Bouncy spring for playful animations.
         */
        val Bouncy = spring<Float>(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        )

        /**
         * No bounce spring for smooth transitions.
         */
        val Smooth = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

        /**
         * Spring for message bubble content size animations.
         * Used when streaming messages grow in size.
         */
        val MessageContent = spring<Float>(
            dampingRatio = 0.8f,
            stiffness = 300f
        )

        /**
         * Spring for FAB animations.
         */
        val Fab = spring<Float>(
            dampingRatio = 0.65f,
            stiffness = 400f
        )

        /**
         * Spring for bottom sheet animations.
         */
        val BottomSheet = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

        /**
         * Spring for scale animations on press.
         */
        val ScalePress = spring<Float>(
            dampingRatio = 0.6f,
            stiffness = 500f
        )
    }

    /**
     * Tween animation specifications for specific use cases.
     */
    object Tweens {

        /**
         * Standard duration for most transitions.
         */
        const val StandardDuration = 300

        /**
         * Short duration for quick feedback.
         */
        const val ShortDuration = 150

        /**
         * Long duration for emphasis.
         */
        const val LongDuration = 500

        /**
         * Enter animation for content appearing.
         */
        val Enter = tween<Float>(
            durationMillis = StandardDuration,
            easing = LinearOutSlowInEasing
        )

        /**
         * Exit animation for content disappearing.
         */
        val Exit = tween<Float>(
            durationMillis = ShortDuration,
            easing = FastOutSlowInEasing
        )

        /**
         * Fade animation for opacity changes.
         */
        val Fade = tween<Float>(
            durationMillis = ShortDuration,
            easing = LinearOutSlowInEasing
        )

        /**
         * Screen transition animation.
         */
        val ScreenTransition = tween<Float>(
            durationMillis = StandardDuration,
            easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
        )
    }

    /**
     * Duration values in milliseconds.
     */
    object Durations {
        const val Instant = 0
        const val Quick = 100
        const val Short = 150
        const val Standard = 300
        const val Long = 500
        const val ExtraLong = 700

        /**
         * Duration for streaming indicator animation cycle.
         */
        const val StreamingIndicator = 1200

        /**
         * Delay between streaming indicator dots.
         */
        const val StreamingDotDelay = 100

        /**
         * Duration for swipe-to-delete animation.
         */
        const val SwipeToDelete = 250

        /**
         * Duration for snackbar appearance.
         */
        const val SnackbarAppear = 300

        /**
         * Duration snackbar stays visible.
         */
        const val SnackbarDuration = 5000
    }

    /**
     * Scale values for press animations.
     */
    object Scales {
        const val Normal = 1f
        const val Pressed = 0.95f
        const val Selected = 1.02f
        const val Disabled = 0.98f
    }

    /**
     * Alpha values for opacity animations.
     */
    object Alphas {
        const val Full = 1f
        const val High = 0.87f
        const val Medium = 0.60f
        const val Low = 0.38f
        const val Disabled = 0.38f
        const val Transparent = 0f
    }

    /**
     * Offset values for slide animations in dp.
     */
    object Offsets {
        const val Small = 8
        const val Medium = 16
        const val Large = 32
        const val ExtraLarge = 64
        const val ScreenSlide = 300
    }
}

/**
 * Spring animation specs for Int values.
 */
object IntSprings {
    val Default = spring<Int>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val Snappy = spring<Int>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
}

/**
 * Commonly used easing curves.
 */
object Easings {
    /**
     * Standard easing for most animations.
     */
    val Standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /**
     * Emphasized easing for entrances.
     */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /**
     * Emphasized easing for exits.
     */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /**
     * Linear easing (no acceleration).
     */
    val Linear = CubicBezierEasing(0.0f, 0.0f, 1.0f, 1.0f)
}
