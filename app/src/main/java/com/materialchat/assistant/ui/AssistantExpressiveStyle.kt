package com.materialchat.assistant.ui

import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Assistant-local Material 3 Expressive tokens.
 *
 * The assistant window runs its own composition on top of the shared theme. These tokens derive
 * from [androidx.compose.material3.MaterialTheme] values at the call site (colour scheme and
 * typography are passed in) and add the expressive layer the assistant surfaces need: weight
 * contrast in type, organic corner radii, bold tonal fills and springy physical motion. Nothing
 * here replaces a theme token; the pill's 38dp capsule and 76dp footprint are contracts shared
 * with the particle field and the hosted geometry gate and stay as they are.
 */
internal object AssistantMotion {
    // Every spec here is either the shared theme token (ui/theme/Motion.kt) or a documented
    // assistant-specific derivation of one; no spring is hand-rolled at a call site.

    /** Glyph and state-line switches (listening -> thinking -> speaking): the shared fast spatial token. */
    fun <T> stateSwitch(): SpringSpec<T> = ExpressiveMotion.Spatial.fast()

    /**
     * The answer surface rising out of the pill or sinking back (slide + scale): the shared default
     * spatial token. Height disclosures (the opened pill's regions growing or folding) do not use
     * this: they draw [ExpressiveMotion.Disclosure.Expand] / [ExpressiveMotion.Disclosure.Shrink]
     * directly, like every other disclosure in the app (W5).
     */
    fun <T> surfaceReveal(): SpringSpec<T> = ExpressiveMotion.Spatial.default()

    /** Sheet entrance: the shared M3 Expressive container spatial token. */
    fun <T> container(): SpringSpec<T> = ExpressiveMotion.Spatial.container()

    /** Sheet entrance scale: the shared bouncy hero token. */
    fun <T> playful(): SpringSpec<T> = ExpressiveMotion.Spatial.playful()

    /**
     * The whole surface leaving: it drops and shrinks on a critically damped spring, so the exit
     * never overshoots back into view (M3: expressive motion overshoots on arrival, not departure).
     * The shared critically damped collapse token (1.0 / 420, omega = 20.5 rad/s) is 90 % of the
     * way at 190 ms and 99 % at 324 ms; the sheet's alpha leaves on [effect] (1.0 / 1600,
     * omega = 40 rad/s), which is at 99 % by 166 ms, so nothing of the last 1 % of travel is
     * visible before [AssistantExitMotion]'s 320 ms window closes. The opened pill's regions fold
     * on [ExpressiveMotion.Disclosure.Shrink], which is this same spec, so they leave on the
     * sheet's own curve (AssistantMotionTest pins all three).
     */
    fun <T> exit(): SpringSpec<T> = ExpressiveMotion.Spatial.collapse()

    // Shape morphs of small components (the glyph container's corner, the `+` -> `x` affordance's
    // corner and rotation) draw ExpressiveMotion.Press.Shape / Press.Progress directly: the one
    // press-morph token every pressable control in the app shares (W5; value-identical to the
    // fast spatial token this object used to alias).

    /** The primary action changing role: the shared threshold pop (overshoots, then settles). */
    fun <T> pop(): SpringSpec<T> = ExpressiveMotion.Spatial.thresholdPop()

    /** Expand/collapse of the answer surface while a reply streams in: the shared contentSize token. */
    fun <T> contentSize(): SpringSpec<T> = ExpressiveMotion.Spatial.contentSize()

    /** Non-spatial effects (alpha, colour): the shared effects token, never bouncy. */
    fun <T> effect(): SpringSpec<T> = ExpressiveMotion.Effects.color()
}

internal object AssistantShapes {
    /** The pill's corner: half its 76dp row. The particle field and the backdrop blur read the same value. */
    val PillCorner: Dp = 38.dp

    /** The pill: a full capsule at 76dp. Shared with the particle field's capsule geometry. */
    val Pill = RoundedCornerShape(PillCorner)

    /** The answer surface: wide, organic top corners that sit against the pill below. */
    val Answer = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

    /** The one-line notice above the pill. */
    val Notice = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    /** Tonal action chips in the answer surface. */
    val Chip = RoundedCornerShape(50)

    /**
     * The thinking surface: an asymmetric container (M3 variable radii, used for one hero moment)
     * whose tight bottom-start corner points at the pill it will answer into.
     */
    val Thinking = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 8.dp, bottomEnd = 20.dp)

    /** The action toolbar inside the answer surface: a full capsule, like an M3 floating toolbar. */
    val Toolbar = RoundedCornerShape(50)

    /** Outer corner of a connected action group (answer toolbar icons, attach actions). */
    val ConnectedOuterCorner: Dp = 20.dp

    /** Join corner between neighbours inside a connected group. */
    val ConnectedInnerCorner: Dp = 8.dp

    /** Corner every grouped item (and the chrome icon buttons) tightens to while pressed. */
    val ConnectedPressedCorner: Dp = 12.dp

    /** Pressed shape shared by the connected groups and the chrome icon buttons. */
    val ConnectedPressed = RoundedCornerShape(ConnectedPressedCorner)

    /** Start/end corners of item [index] of [count] in a connected group. */
    fun connectedCorners(index: Int, count: Int): Pair<Dp, Dp> {
        if (count <= 1) return ConnectedOuterCorner to ConnectedOuterCorner
        return (if (index == 0) ConnectedOuterCorner else ConnectedInnerCorner) to
            (if (index == count - 1) ConnectedOuterCorner else ConnectedInnerCorner)
    }

    /** Per-position shape of one item in a connected group. */
    fun connectedShape(index: Int, count: Int): RoundedCornerShape {
        val (start, end) = connectedCorners(index, count)
        return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
    }

    /** The pill's glyph container at rest, listening and speaking: a circle (40dp). */
    val GlyphRoundCorner: Dp = 20.dp

    /** The pill's glyph container while thinking: a rounded square, the M3 toggled/selected morph. */
    val GlyphSquareCorner: Dp = 12.dp

    /** The 56dp primary button at rest (mic, send): a circle. */
    val PrimaryRoundCorner: Dp = 28.dp

    /** The primary button while it interrupts (stop): the M3 toggled rounded square. */
    val PrimarySquareCorner: Dp = 18.dp

    /** The screen capture's 28dp thumbnail inside the Screen chip. */
    val ThumbnailCorner: Dp = 8.dp
}

/**
 * The one vertical and horizontal scale of the assistant window. Every padding and gap in the
 * pill, the opened pill, the answer surface, the thinking surface, the notice surface and the
 * chips is one of these steps, so the surfaces share a rhythm instead of each carrying its own
 * numbers. The pill's 76dp row and [AssistantKeyboardSpacing.GAP_DP] are geometry contracts and
 * stay literal.
 */
internal object AssistantSpacing {
    /** Title over its status line; the Screen chip's trailing edge before its remove target. */
    val Hairline: Dp = 2.dp

    /** The toolbar's inner inset and the answer text's own inset inside the surface. */
    val Tight: Dp = 4.dp

    /** Icon to label inside a chip or marker. */
    val Gap: Dp = 6.dp

    /** Between siblings: stacked surfaces in the sheet, messages, the parts of the Screen chip. */
    val Between: Dp = 8.dp

    /** Between the pill's controls; between a notice's glyph and its message. */
    val Row: Dp = 10.dp

    /** Inner vertical inset of the small surfaces (notice, thinking, question bubble) and the sheet's side inset. */
    val Inset: Dp = 12.dp

    /** Horizontal inset of a 40dp chip and of the notice surface. */
    val ChipInset: Dp = 14.dp

    /** The question bubble's side inset; the opened pill's top inset above a thumbnail row (its remove badge overhangs 12dp). */
    val Block: Dp = 16.dp

    /** The pill row's leading inset and the opened region's side insets. */
    val PillInset: Dp = 18.dp

    /** The answer surface's inner inset. */
    val SurfaceInset: Dp = 20.dp
}

/**
 * Fixed sizes of the assistant's controls (not spacing): the layout maths that decides whether a
 * labelled chip and the connected icon group fit one toolbar row reads the same values the
 * composables draw, so a size change cannot silently break the fit.
 */
internal object AssistantSizes {
    /** Every labelled chip and the notice's action: a 40dp capsule. */
    val ChipHeight: Dp = 40.dp

    /** The 16dp leading icon of a chip, the Screen chip and the Using-screen marker. */
    val ChipIcon: Dp = 16.dp

    /** A Material icon button's laid-out touch target (40dp control inside its 48dp minimum). */
    val Target: Dp = 48.dp

    /** The pill's leading glyph container and the two 40dp chrome controls (`+`, keyboard, close). */
    val Control: Dp = 40.dp

    /** The glyph inside its container (waveform, loading indicator, icon). */
    val Glyph: Dp = 32.dp

    /** The pill's primary button (mic / send / stop). */
    val PrimaryButton: Dp = 56.dp

    /** A compact remove target inside a chip (the Screen chip's x). */
    val CompactTarget: Dp = 32.dp

    /** The screen capture's thumbnail inside the Screen chip. */
    val Thumbnail: Dp = 28.dp

    /** The notice surface's leading glyph: one step above the chip icon so it leads the message. */
    val NoticeGlyph: Dp = 18.dp

    /** The narrowest the question bubble gets (one short word). */
    val QueryBubbleMin: Dp = 40.dp

    /** One dot of the streaming indicator under the answer. */
    val TypingDot: Dp = 7.dp
}

internal object AssistantTypography {
    /** The live transcript while capturing: large and heavy so the recognised words lead. */
    fun transcript(typography: Typography): TextStyle =
        typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)

    /** The resting prompt and phase words ("Ask anything", "Thinking..."): light against the transcript. */
    fun prompt(typography: Typography): TextStyle =
        typography.titleMedium.copy(fontWeight = FontWeight.Normal)

    /** Typed text in keyboard mode. */
    fun input(typography: Typography): TextStyle =
        typography.titleMedium.copy(fontWeight = FontWeight.Medium)

    /** The answer: regular weight with generous leading for reading at a glance. */
    fun answer(typography: Typography): TextStyle =
        typography.bodyLarge.copy(lineHeight = 26.sp)

    /** The user's question bubble: a semibold title against the regular-weight answer. */
    fun query(typography: Typography): TextStyle =
        typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    /** Action chip labels. */
    fun action(typography: Typography): TextStyle =
        typography.labelLarge.copy(fontWeight = FontWeight.Bold)

    /**
     * The one secondary body style: notices, errors, the Using-screen marker and the thinking
     * surface's status line. The answer is the only other body style in the window.
     */
    fun notice(typography: Typography): TextStyle =
        typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

    /** The thinking surface's header: a bold, upright title (never italic). */
    fun thinkingTitle(typography: Typography): TextStyle =
        typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp)

    /** The thinking surface's status line: the same secondary style every other status line uses. */
    fun thinkingStatus(typography: Typography): TextStyle = notice(typography)
}

/** The pill's phase. The leading glyph, its container and the state line all follow it. */
internal enum class AssistantPhase { Capturing, Thinking, Speaking, Idle }

internal object AssistantColors {
    /** The answer surface: a high surface container tinted toward the accent. */
    fun answerSurface(scheme: ColorScheme): Color =
        lerp(scheme.surfaceContainerHigh, scheme.primaryContainer, 0.22f)

    /** The user's question bubble on the answer surface: the full primary container. */
    fun querySurface(scheme: ColorScheme): Color = scheme.primaryContainer

    /**
     * The pill's glyph container per phase. Each phase has its own tonal container (M3: colour
     * signals state, never colour alone: the glyph changes too): transparent at rest, the primary
     * container while capturing (matching the Stop button), secondary while thinking, tertiary
     * while speaking.
     */
    fun glyphContainer(scheme: ColorScheme, phase: AssistantPhase): Color = when (phase) {
        AssistantPhase.Idle -> Color.Transparent
        AssistantPhase.Capturing -> scheme.primaryContainer
        AssistantPhase.Thinking -> scheme.secondaryContainer
        AssistantPhase.Speaking -> scheme.tertiaryContainer
    }

    // The glyph's own colour is resolved against its container (or the pill fill while the
    // container is transparent) by AssistantSurfacePalette, never read from a role directly.

    /** The thinking surface: the highest surface container leaning toward the secondary container. */
    fun thinkingSurface(scheme: ColorScheme): Color =
        lerp(scheme.surfaceContainerHighest, scheme.secondaryContainer, 0.35f)

    /** The action toolbar strip inside the answer surface: one container step below the answer. */
    fun toolbarSurface(scheme: ColorScheme): Color = scheme.surfaceContainerLow
}

/**
 * The assistant's fills and the content colours resolved against each, for the current scheme.
 * Every text/icon colour in the assistant window comes from here, so it is chosen against the
 * fill it is drawn on rather than assumed from a theme role.
 */
@Composable
internal fun rememberAssistantSurfacePalette(): AssistantSurfacePalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { AssistantSurfacePalette.resolve(scheme) }
}
