package com.materialchat.assistant.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
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
    /** Glyph and state-line switches (listening -> thinking -> speaking): quick, with visible overshoot. */
    fun <T> stateSwitch(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 520f)

    /** The answer surface appearing or leaving: a soft, weighty settle. */
    fun <T> surfaceReveal(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 300f)

    /** Sheet entrance (M3 Expressive standard spatial). */
    fun <T> container(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 380f)

    /** Sheet entrance scale: the bouncy hero spring. */
    fun <T> playful(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 400f)

    /**
     * The whole surface leaving: it drops and shrinks on a critically damped spring, so the exit
     * never overshoots back into view (M3: expressive motion overshoots on arrival, not departure).
     * Settles well inside [AssistantExitMotion]'s 320 ms window.
     */
    fun <T> exit(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)

    /** Shape morphs of small components (the glyph container): M3 `spring.fast.spatial`, expressive. */
    fun <T> morph(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 1400f)

    /** The primary action changing role: a short pop. */
    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = 0.45f, stiffness = 650f)

    /** Expand/collapse of the answer surface while a reply streams in. */
    fun <T> contentSize(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 340f)

    /** Non-spatial effects (alpha, colour): fast and never bouncy. */
    fun <T> effect(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1200f)
}

internal object AssistantShapes {
    /** The pill: a full capsule at 76dp. Shared with the particle field's capsule geometry. */
    val Pill = RoundedCornerShape(38.dp)

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

    /** The pill's glyph container at rest, listening and speaking: a circle (40dp). */
    val GlyphRoundCorner: Dp = 20.dp

    /** The pill's glyph container while thinking: a rounded square, the M3 toggled/selected morph. */
    val GlyphSquareCorner: Dp = 12.dp
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

    /** Notices and errors. */
    fun notice(typography: Typography): TextStyle =
        typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

    /** The thinking surface's header: a bold, upright title (never italic). */
    fun thinkingTitle(typography: Typography): TextStyle =
        typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp)

    /** The thinking surface's status line: regular weight under the bold header. */
    fun thinkingStatus(typography: Typography): TextStyle =
        typography.bodyMedium.copy(fontWeight = FontWeight.Normal)
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
