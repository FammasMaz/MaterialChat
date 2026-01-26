package com.materialchat.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Material 3 Expressive Motion System for MaterialChat.
 *
 * M3 Expressive distinguishes between two types of springs:
 * 
 * 1. SPATIAL SPRINGS - For position, size, rotation, corners
 *    - CAN overshoot and bounce
 *    - Creates playful, lively feel
 *    - dampingRatio < 1.0
 *
 * 2. EFFECTS SPRINGS - For color, opacity, blur, elevation  
 *    - NO overshoot - seamless, smooth transitions
 *    - dampingRatio = 1.0 (critical!)
 *
 * This distinction is KEY to M3 Expressive motion language.
 */
object ExpressiveMotion {

    /**
     * SPATIAL SPRINGS - For position, size, rotation, corners
     * CAN overshoot and bounce - creates playful feel
     */
    object Spatial {

        /**
         * Default spatial spring for most interactions.
         * M3 Expressive standard: dampingRatio=0.6, stiffness=380
         */
        fun <T> default(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 380f
        )

        /**
         * Shape morphing spring for corner radius changes.
         * Noticeable bounce for playful shape transformations.
         */
        fun <T> shapeMorph(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 500f
        )

        /**
         * Scale spring for button press animations.
         * Bouncy feedback on press/release.
         */
        fun <T> scale(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 600f
        )

        /**
         * Container spring for large movements like screen transitions.
         * M3 Expressive standard spatial.
         */
        fun <T> container(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 380f
        )

        /**
         * Playful spring for hero moments like FAB animations.
         * Very bouncy for delightful interactions (M3 Expressive).
         */
        fun <T> playful(): SpringSpec<T> = spring(
            dampingRatio = 0.5f,
            stiffness = 400f
        )

        /**
         * FAB expand/collapse spring.
         * Bouncy width animation for scroll behavior.
         */
        fun <T> fabExpand(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 380f
        )

        /**
         * Shared element transition spring.
         * M3 Expressive standard for container transforms.
         */
        fun <T> sharedElement(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 380f
        )

        /**
         * Message entrance slide spring.
         */
        fun <T> messageEntrance(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 380f
        )

        /**
         * List item press spring.
         */
        fun <T> listItemPress(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 500f
        )
    }

    /**
     * EFFECTS SPRINGS - For color, opacity, blur, elevation
     * NO overshoot - seamless, smooth transitions (dampingRatio = 1.0)
     */
    object Effects {
        
        /**
         * Color transition spring.
         * Smooth with NO bounce - colors should never overshoot.
         */
        fun <T> color(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 300f
        )
        
        /**
         * Alpha/opacity transition spring.
         * Smooth fade with no bounce.
         */
        fun <T> alpha(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 300f
        )
        
        /**
         * Elevation/shadow transition spring.
         * Smooth shadow changes.
         */
        fun <T> elevation(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 350f
        )
        
        /**
         * Blur transition spring.
         */
        fun <T> blur(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 300f
        )
    }
    
    /**
     * Pre-built spring specs for common types.
     * Use these for type-safe animation specs.
     */
    object SpringSpecs {
        // Float springs
        val ScaleFloat: SpringSpec<Float> = Spatial.scale()
        val AlphaFloat: SpringSpec<Float> = Effects.alpha()
        val ContainerFloat: SpringSpec<Float> = Spatial.container()
        val PlayfulFloat: SpringSpec<Float> = Spatial.playful()
        
        // Dp springs
        val ShapeMorphDp: SpringSpec<Dp> = Spatial.shapeMorph()
        val ElevationDp: SpringSpec<Dp> = Effects.elevation()
        val FabExpandDp: SpringSpec<Dp> = Spatial.fabExpand()
        
        // Color springs
        val ColorTransition: SpringSpec<Color> = Effects.color()
        
        // IntOffset springs (for position)
        val PositionOffset: SpringSpec<IntOffset> = Spatial.default()
    }
}

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
         * Uses spring physics for M3 Expressive spatial animations.
         */
        fun <T> Enter(): SpringSpec<T> = ExpressiveMotion.Spatial.container()

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
         * Uses spring physics for M3 Expressive spatial animations.
         */
        fun <T> ScreenTransition(): SpringSpec<T> = ExpressiveMotion.Spatial.container()
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
