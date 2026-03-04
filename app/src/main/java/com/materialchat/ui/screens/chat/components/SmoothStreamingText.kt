package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import com.materialchat.domain.model.CanvasArtifact
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.components.rememberHapticFeedback
import kotlinx.coroutines.delay

/**
 * Smooth streaming text composable that normalizes patchy token delivery
 * into a consistent, fluid word-by-word reveal with per-word haptic feedback.
 *
 * Different AI providers deliver streaming tokens at varying granularities:
 * some send word-by-word, others send whole paragraphs. This composable
 * buffers incoming text and drips it out at a smooth, consistent rate
 * so the experience feels uniform regardless of provider behavior.
 *
 * Haptic feedback fires every [HAPTIC_WORD_INTERVAL] revealed words,
 * synced to the visual dripping rather than raw provider chunks, giving
 * a consistent tactile rhythm that matches what the user sees.
 *
 * Motion follows M3 Expressive guidelines:
 * - Spring physics for all opacity transitions (no easing/duration)
 * - spring.fast.effects (stiffness=1400, damping=1.0) for trailing opacity
 *
 * @param rawText The full accumulated text from the streaming provider
 * @param isStreaming Whether the provider is actively streaming
 * @param textColor The text color for rendering
 * @param style The text style for rendering
 * @param hapticsEnabled Whether haptic feedback is enabled (user preference)
 * @param onOpenCanvas Callback for opening canvas artifacts
 * @param modifier Modifier for the container
 */
@Composable
fun SmoothStreamingText(
    rawText: String,
    isStreaming: Boolean,
    textColor: Color,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    hapticsEnabled: Boolean = true,
    onOpenCanvas: ((CanvasArtifact) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // When not streaming, render full text with markdown immediately
    if (!isStreaming) {
        MarkdownText(
            markdown = rawText,
            textColor = textColor,
            style = style,
            onOpenCanvas = onOpenCanvas,
            modifier = modifier
        )
        return
    }

    // Empty content during streaming — nothing to show yet
    if (rawText.isEmpty()) return

    // ---- Smooth streaming mode ----

    val haptics = rememberHapticFeedback()

    // Count "word tokens" in the raw text (whitespace-delimited)
    val totalWordCount = remember(rawText) { rawText.wordCount() }

    // Track how many words we've revealed so far
    var revealedWordCount by remember { mutableIntStateOf(0) }

    // Drip words from the buffer at a smooth, adaptive rate.
    // When the buffer is large (provider sent a big chunk), speed up to catch up.
    // When the buffer is small (provider is slow), drip slowly for a natural feel.
    LaunchedEffect(totalWordCount) {
        while (revealedWordCount < totalWordCount) {
            revealedWordCount++

            // Fire haptic on every revealed word for tactile rhythm synced to visual dripping
            haptics.perform(HapticPattern.CONTENT_TICK, hapticsEnabled)

            val buffered = totalWordCount - revealedWordCount
            delay(
                when {
                    buffered > 50 -> 5L    // Very far behind — rapid catch-up
                    buffered > 30 -> 10L   // Far behind
                    buffered > 15 -> 18L   // Behind
                    buffered > 8  -> 28L   // Slightly behind
                    buffered > 3  -> 38L   // Normal streaming pace
                    else -> 48L            // Provider is slow, drip gently
                }
            )
        }
    }

    // Build the displayed text: first N words of rawText, preserving original whitespace
    val displayedText = remember(revealedWordCount, rawText) {
        rawText.takeWords(revealedWordCount)
    }

    // Subtle trailing opacity: when words are buffered, the content renders at
    // reduced opacity to create a gentle "materializing" feel.
    // This does NOT clip or mask content — just an alpha reduction.
    val isBuffering = revealedWordCount < totalWordCount
    val trailingAlpha = remember { Animatable(1f) }

    // Per-reveal pulse: snaps to a lower alpha on each word reveal
    // and springs back, creating visible word-by-word materialization.
    val revealPulseAlpha = remember { Animatable(1f) }

    LaunchedEffect(isBuffering) {
        trailingAlpha.animateTo(
            targetValue = if (isBuffering) BUFFERING_ALPHA else 1f,
            animationSpec = spring(
                stiffness = SPRING_FAST_EFFECTS_STIFFNESS,
                dampingRatio = SPRING_EFFECTS_DAMPING
            )
        )
    }

    LaunchedEffect(revealedWordCount) {
        if (isBuffering && revealedWordCount > 0) {
            revealPulseAlpha.snapTo(0.65f)
            revealPulseAlpha.animateTo(
                targetValue = trailingAlpha.value,
                animationSpec = spring(
                    stiffness = SPRING_FAST_EFFECTS_STIFFNESS,
                    dampingRatio = SPRING_EFFECTS_DAMPING
                )
            )
        }
    }

    if (displayedText.isNotEmpty()) {
        Box(modifier = modifier.alpha(revealPulseAlpha.value)) {
            MarkdownText(
                markdown = displayedText,
                textColor = textColor,
                style = style,
                onOpenCanvas = onOpenCanvas
            )
        }
    }
}

/**
 * Smooth streaming text for thinking/reasoning content.
 *
 * Same word-dripping behavior as [SmoothStreamingText] but renders as plain
 * italic text instead of markdown, matching ThinkingSection's visual style.
 * Fires ultra-subtle [HapticPattern.THINKING_TICK] at a slower cadence.
 *
 * @param rawText The full accumulated thinking text from the provider
 * @param isStreaming Whether the provider is actively streaming
 * @param textColor The text color (typically muted/alpha'd)
 * @param style The text style
 * @param hapticsEnabled Whether haptic feedback is enabled
 * @param modifier Modifier for the container
 */
@Composable
fun SmoothStreamingThinkingText(
    rawText: String,
    isStreaming: Boolean,
    textColor: Color,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // When not streaming, show full text immediately
    if (!isStreaming) {
        Text(
            text = rawText,
            style = style,
            color = textColor,
            fontStyle = FontStyle.Italic,
            modifier = modifier
        )
        return
    }

    if (rawText.isEmpty()) return

    val haptics = rememberHapticFeedback()

    val totalWordCount = remember(rawText) { rawText.wordCount() }
    var revealedWordCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(totalWordCount) {
        while (revealedWordCount < totalWordCount) {
            revealedWordCount++

            // Fire haptic on every revealed word — ultra-subtle thinking tick
            haptics.perform(HapticPattern.THINKING_TICK, hapticsEnabled)

            val buffered = totalWordCount - revealedWordCount
            delay(
                when {
                    buffered > 50 -> 5L
                    buffered > 30 -> 10L
                    buffered > 15 -> 18L
                    buffered > 8  -> 28L
                    buffered > 3  -> 38L
                    else -> 48L
                }
            )
        }
    }

    val displayedText = remember(revealedWordCount, rawText) {
        rawText.takeWords(revealedWordCount)
    }

    val isBuffering = revealedWordCount < totalWordCount
    val trailingAlpha = remember { Animatable(1f) }

    // Per-reveal pulse for thinking text materialization
    val revealPulseAlpha = remember { Animatable(1f) }

    LaunchedEffect(isBuffering) {
        trailingAlpha.animateTo(
            targetValue = if (isBuffering) BUFFERING_ALPHA else 1f,
            animationSpec = spring(
                stiffness = SPRING_FAST_EFFECTS_STIFFNESS,
                dampingRatio = SPRING_EFFECTS_DAMPING
            )
        )
    }

    LaunchedEffect(revealedWordCount) {
        if (isBuffering && revealedWordCount > 0) {
            revealPulseAlpha.snapTo(0.65f)
            revealPulseAlpha.animateTo(
                targetValue = trailingAlpha.value,
                animationSpec = spring(
                    stiffness = SPRING_FAST_EFFECTS_STIFFNESS,
                    dampingRatio = SPRING_EFFECTS_DAMPING
                )
            )
        }
    }

    if (displayedText.isNotEmpty()) {
        Text(
            text = displayedText,
            style = style,
            color = textColor,
            fontStyle = FontStyle.Italic,
            modifier = modifier.alpha(revealPulseAlpha.value)
        )
    }
}

// ---- M3 Expressive Motion Constants ----

/** M3 spring.fast.effects: stiffness for opacity/color transitions */
private const val SPRING_FAST_EFFECTS_STIFFNESS = 1400f

/** M3 effects damping: no overshoot/bounce for opacity changes */
private const val SPRING_EFFECTS_DAMPING = 1.0f

/** Alpha reduction while words are still buffered (visible fade-in materialization) */
private const val BUFFERING_ALPHA = 0.55f

// ---- Word Tokenization Utilities ----

/**
 * Counts the number of whitespace-delimited word tokens in the string.
 */
private fun String.wordCount(): Int {
    if (isEmpty()) return 0
    var count = 0
    var inWord = false
    for (char in this) {
        if (char.isWhitespace()) {
            if (inWord) {
                count++
                inWord = false
            }
        } else {
            inWord = true
        }
    }
    if (inWord) count++
    return count
}

/**
 * Returns a substring containing the first [n] whitespace-delimited words,
 * preserving original whitespace and formatting (newlines, markdown, etc.).
 */
private fun String.takeWords(n: Int): String {
    if (n <= 0) return ""
    if (isEmpty()) return ""

    var wordCount = 0
    var i = 0
    var inWord = false

    while (i < length) {
        if (this[i].isWhitespace()) {
            if (inWord) {
                wordCount++
                inWord = false
                if (wordCount >= n) {
                    while (i < length && this[i].isWhitespace()) i++
                    return substring(0, i)
                }
            }
        } else {
            inWord = true
        }
        i++
    }

    return this
}
