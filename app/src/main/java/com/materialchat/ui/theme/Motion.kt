package com.materialchat.ui.theme

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

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
         * M3 Expressive standard: dampingRatio=0.8, stiffness=380
         * (ExpressiveMotionTokens.defaultSpatialSpec in material3 1.5.0-alpha15).
         */
        fun <T> default(): SpringSpec<T> = spring(
            dampingRatio = 0.8f,
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

        /**
         * List item placement/entrance spring. Slight overshoot so rows settle
         * into place with a physical feel without visibly bouncing past neighbours.
         */
        fun <T> listEntrance(): SpringSpec<T> = spring(
            dampingRatio = 0.72f,
            stiffness = 380f
        )

        /**
         * Menu/sheet open spring for scale and offset of popups and dropdowns.
         */
        fun <T> menuOpen(): SpringSpec<T> = spring(
            dampingRatio = 0.68f,
            stiffness = 520f
        )

        /**
         * Expand of disclosure content (thinking blocks, branch lists, attachment
         * rows, memory references). M3E default-speed spatial; damping high enough
         * that revealed content settles without shoving neighbours past rest.
         */
        fun <T> expand(): SpringSpec<T> = spring(
            dampingRatio = 0.75f,
            stiffness = 420f
        )

        /**
         * Collapse of disclosure content. Critically damped so a shrinking panel
         * never re-expands past zero on its way out.
         */
        fun <T> collapse(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 420f
        )

        /**
         * Container size changes driven by `animateContentSize` on sheets, settings
         * lists, pill labels and the chat title block. Light overshoot. Streaming
         * bubbles keep their own critically-damped spec because they retarget
         * every frame.
         */
        fun <T> contentSize(): SpringSpec<T> = spring(
            dampingRatio = 0.8f,
            stiffness = 450f
        )

        /**
         * Screen-level slide (sibling conversation switch). M3E slow spatial.
         */
        fun <T> screenSlide(): SpringSpec<T> = spring(
            dampingRatio = 0.75f,
            stiffness = 200f
        )

        /**
         * Threshold pop for icons that cross a gesture threshold (swipe arm, drag
         * commit). Low damping so the icon visibly overshoots and settles.
         */
        fun <T> thresholdPop(): SpringSpec<T> = spring(
            dampingRatio = 0.45f,
            stiffness = 700f
        )

        /**
         * Pulse of a disclosure control (chevron tile) when its group opens or closes.
         */
        fun <T> disclosurePulse(): SpringSpec<T> = spring(
            dampingRatio = 0.55f,
            stiffness = 650f
        )

        /**
         * Dialog entrance: scale from slightly below rest with a touch of overshoot.
         */
        fun <T> dialogEnter(): SpringSpec<T> = spring(
            dampingRatio = 0.7f,
            stiffness = 520f
        )

        /**
         * Press morph shared by every pressable control (buttons, chips, tiles, FAB,
         * icon buttons): corner radius / scale / shape-morph progress on press and release.
         * It IS the M3E fast spatial spring ([fast], 0.6 / 800): settles in well under 200 ms
         * so a tap feels immediate, with a visible but short overshoot on release. One spec,
         * two names (W7 part 5): this name documents intent at a press site; [fast] is the
         * M3E token name for every other quick, small movement. Pinned equal in
         * `MotionTokensTest`.
         */
        fun <T> pressMorph(): SpringSpec<T> = fast()

        /**
         * M3E fast spatial (ExpressiveMotionTokens.fastSpatialSpec): quick, small
         * movements such as a toggle thumb, a checkbox mark, or a selection indicator.
         */
        fun <T> fast(): SpringSpec<T> = spring(
            dampingRatio = 0.6f,
            stiffness = 800f
        )

        /**
         * M3E slow spatial (ExpressiveMotionTokens.slowSpatialSpec): screen- and
         * sheet-scale movement where overshoot must stay gentle.
         */
        fun <T> slow(): SpringSpec<T> = spring(
            dampingRatio = 0.8f,
            stiffness = 200f
        )

        /**
         * Data reveal on the insights screen: the count-up of a stat card's number and the
         * grow-in of its chart bars on data arrival. The ONE insights reveal spec: damping is
         * the M3E default spatial damping (0.8, same as [default]) so a count-up never
         * overshoots its final value by a visible digit; stiffness is a quarter of the M3E
         * slow spatial token ([slow], 200 -> 50) so the figure is seen arriving rather than
         * snapping. The former per-chart variants (heatmap 0.9/40, comparison and
         * response-time charts 0.7/60) were retired for this spec: all five insights
         * consumers (`StatCard`, `ModelUsageChart`, `ActivityHeatmap`, `ModelComparisonCard`,
         * `ResponseTimeChart`) draw it.
         */
        fun <T> dataReveal(): SpringSpec<T> = spring(
            dampingRatio = 0.8f,
            stiffness = 50f
        )

        /**
         * Slow decorative rotation of an ornament: one full turn (0 -> 360) per
         * [periodMillis] on a linear easing, restarting seamlessly. The home banner gear
         * spins at [MaterialChatMotion.Durations.DecorativeSpin] (16 s); the empty-state
         * cluster and the explore backdrop at [MaterialChatMotion.Durations.BackdropSpin]
         * (48 s). Not a spring: it is the one sanctioned infinite tween for ornaments.
         * Continuous motion, so a consumer may only run it while
         * `rememberParticleMotionGate().motionAllowed` and must hold 0f otherwise.
         */
        fun decorativeSpin(
            periodMillis: Int = MaterialChatMotion.Durations.DecorativeSpin
        ): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
            animation = tween(durationMillis = periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )

        /**
         * Gentle up/down bob of a hint glyph (the scroll-to-bottom arrow): one half cycle
         * per [MaterialChatMotion.Durations.Bob], reversing, on the default easing. Gate it
         * exactly like [decorativeSpin].
         */
        fun bob(
            halfPeriodMillis: Int = MaterialChatMotion.Durations.Bob
        ): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
            animation = tween(durationMillis = halfPeriodMillis),
            repeatMode = RepeatMode.Reverse
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
         * Matches ExpressiveMotionTokens.defaultEffectsSpec (1.0 / 1600).
         */
        fun <T> color(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 1600f
        )
        
        /**
         * Alpha/opacity transition spring.
         * Smooth fade with no bounce.
         */
        fun <T> alpha(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 1600f
        )
        
        /**
         * Elevation/shadow transition spring.
         * Smooth shadow changes.
         */
        fun <T> elevation(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 1600f
        )
        
        /**
         * Blur transition spring.
         */
        fun <T> blur(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 1600f
        )

        /**
         * Fade for list item appearance/removal. Slightly faster than [alpha]
         * so entrance is driven by placement, not by the fade.
         */
        fun <T> listFade(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 400f
        )

        /**
         * Fade paired with [Spatial.expand]/[Spatial.collapse] so disclosure
         * content fades slightly faster than it grows.
         */
        fun <T> reveal(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 500f
        )

        /**
         * M3E fast effects (ExpressiveMotionTokens.fastEffectsSpec): press tint,
         * ripple-adjacent colour and alpha changes that must track the finger.
         */
        fun <T> fast(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 3800f
        )

        /**
         * M3E slow effects (ExpressiveMotionTokens.slowEffectsSpec): long cross-fades
         * between screens or large content swaps.
         */
        fun <T> slow(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 800f
        )

        /**
         * Content-colour / container-tint change that accompanies [Spatial.pressMorph]
         * so the fill and the shape of a pressed control settle together.
         */
        fun <T> pressTint(): SpringSpec<T> = spring(
            dampingRatio = 1.0f,
            stiffness = 1600f
        )
    }

    /**
     * Specs for the press state of any pressable control so every button, chip, tile and
     * FAB in the app compresses and tints with the same physics.
     */
    object Press {
        val Shape: SpringSpec<Dp> = Spatial.pressMorph()
        val Progress: SpringSpec<Float> = Spatial.pressMorph()
        val Scale: SpringSpec<Float> = Spatial.pressMorph()
        val Tint: SpringSpec<Color> = Effects.pressTint()
        val Alpha: SpringSpec<Float> = Effects.pressTint()
    }

    /**
     * Specs for `AnimatedVisibility(expandVertically/shrinkVertically + fade)` so
     * every expand/collapse disclosure in the app shares one physics.
     */
    object Disclosure {
        val Expand: SpringSpec<IntSize> = Spatial.expand()
        val Shrink: SpringSpec<IntSize> = Spatial.collapse()
        val FadeIn: SpringSpec<Float> = Effects.reveal()
        val FadeOut: SpringSpec<Float> = Effects.reveal()
    }

    /**
     * Specs for `LazyList.animateItem` so every list in the app enters, moves and
     * leaves with the same physics.
     */
    object ListItem {
        val Placement: SpringSpec<IntOffset> = Spatial.listEntrance()
        val FadeIn: SpringSpec<Float> = Effects.listFade()
        val FadeOut: SpringSpec<Float> = Effects.listFade()
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
     * Spring animation specifications for legacy call sites.
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
        val Exit: SpringSpec<Float> = ExpressiveMotion.Effects.alpha()

        /**
         * Fade animation for opacity changes.
         */
        val Fade: SpringSpec<Float> = ExpressiveMotion.Effects.alpha()

        /**
         * Screen transition animation.
         * Uses spring physics for M3 Expressive spatial animations.
         */
        fun <T> ScreenTransition(): SpringSpec<T> = ExpressiveMotion.Spatial.container()

        /**
         * The one linear-time tween: a continuous, constant-rate indicator phase (a rotation, a
         * shimmer pass, a mind-map pulse) over [durationMillis], normally a
         * [MaterialChatMotion.Durations] token. Wrap it in `infiniteRepeatable` at the site
         * (`MindMapCanvas`, `StreamingIndicator.gatedInfiniteFloat`) so no raw `tween(` call
         * is needed outside the theme. (`M3ExpressiveLoadingIndicator`, the former third
         * consumer, was deleted in W9 with zero callers.)
         */
        fun <T> linear(durationMillis: Int): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = LinearEasing)

        /**
         * The ease-in-out tween for a cycle that must slow at both ends (a shape morph, a
         * reversing sweep): [EaseInOutCubic] over [durationMillis].
         */
        fun <T> easeInOut(durationMillis: Int): TweenSpec<T> =
            tween(durationMillis = durationMillis, easing = EaseInOutCubic)
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

        /**
         * Half period of the scroll-to-bottom arrow bob (the `ExtraLong + Quick` the FAB
         * already draws). Pair with [ExpressiveMotion.Spatial.bob].
         */
        const val Bob = ExtraLong + Quick

        /**
         * Period of the standard masthead's pulse strip (`ChatTopBar`): one linear restart
         * loop per period. Drawn with [ExpressiveMotion.Spatial.decorativeSpin] (HeaderPulse);
         * continuous, so gated like every other ornament loop.
         */
        const val HeaderPulse = 1_400

        /**
         * Half period of a breathing pulse (the fusion card's streaming dot): a 1 s reversing
         * cycle drawn with [ExpressiveMotion.Spatial.bob] (Pulse). Value-equal to [Long] but a
         * token of its own, so a breath is named as a breath rather than reusing a transition
         * length that may later be retuned.
         */
        const val Pulse = 500

        /**
         * Period of the fusion animation's orbit (`FusionAnimation`): one linear 0 -> 360 turn
         * per period on [ExpressiveMotion.Spatial.decorativeSpin]. Value-equal to
         * [Indicator.MorphShape] (3 s), which the consumer reused for want of a name; a token of
         * its own so the orbit can be retuned without moving the loading indicator's shape cycle.
         * Continuous, so gated like every other ornament loop.
         */
        const val FusionOrbit = 3_000

        /** Period of a foreground decorative spin (home banner gear). */
        const val DecorativeSpin = 16_000

        /** Period of a slow backdrop spin (empty-state cluster, explore backdrop). */
        const val BackdropSpin = 48_000

        /**
         * How long a message's stats pill stays auto-expanded after a reply completes before
         * it collapses on its own (`MessageBubble`).
         */
        const val StatsPillHold = 3_500

        /**
         * Cycle lengths of the indeterminate loading indicators (the chat `StreamingIndicator`
         * family): every loop runs only behind the particle motion gate and holds a static frame
         * otherwise. Each value is one period of a linear or ease-in-out tween, drawn through
         * [Tweens.linear] / [Tweens.easeInOut]. The arc and linear-bar entries went with their
         * consumers in W7, and the morphing hero indicator (`M3ExpressiveLoadingIndicator`) was
         * deleted in W9 with zero callers; [MorphRotation] and [MorphShape] stay as the record
         * of its cycle ([MorphShape] is also [FusionOrbit]; both pinned in `MotionTokensTest`).
         */
        object Indicator {
            /** One full rotation of the former hero indicator's morphing shape (record only). */
            const val MorphRotation = 4000

            /** Soft-burst -> pentagon -> hexagon -> soft-burst; value-equal to [FusionOrbit]. */
            const val MorphShape = 3000

            /**
             * One full rotation of a chat streaming indicator's morphing shape
             * (`StreamingIndicator`: single and triple variants). Value-equal to [MorphShape]
             * and [FusionOrbit] but its own token: the streaming spin can be retuned alone.
             */
            const val StreamingSpin = 3000

            /** One soft-burst -> polygon -> soft-burst cycle of a chat streaming indicator. */
            const val StreamingMorph = 2400

            /** One pass of the streaming shimmer across its track. */
            const val StreamingShimmer = 1400
        }
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

