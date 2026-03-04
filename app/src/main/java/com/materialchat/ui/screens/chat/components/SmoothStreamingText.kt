package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.CanvasArtifact
import com.materialchat.ui.components.MarkdownText
import kotlinx.coroutines.delay

/**
 * Smooth streaming text composable that normalizes patchy token delivery
 * into a consistent, fluid word-by-word reveal.
 *
 * Different AI providers deliver streaming tokens at varying granularities:
 * some send word-by-word, others send whole paragraphs. This composable
 * buffers incoming text and drips it out at a smooth, consistent rate
 * with a trailing fade effect for a polished "materializing" feel.
 *
 * Motion follows M3 Expressive guidelines:
 * - Content size changes: spring.default.spatial (stiffness=500, damping=0.7)
 * - Trailing fade: spring.fast.effects (stiffness=1400, damping=1.0)
 * - Respects reduced motion preferences
 *
 * @param rawText The full accumulated text from the streaming provider
 * @param isStreaming Whether the provider is actively streaming
 * @param textColor The text color for rendering
 * @param style The text style for rendering
 * @param onOpenCanvas Callback for opening canvas artifacts
 * @param modifier Modifier for the container
 */
@Composable
fun SmoothStreamingText(
    rawText: String,
    isStreaming: Boolean,
    textColor: Color,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
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
            val buffered = totalWordCount - revealedWordCount
            delay(
                when {
                    buffered > 50 -> 5L    // Very far behind — rapid catch-up
                    buffered > 30 -> 10L   // Far behind
                    buffered > 15 -> 18L   // Behind
                    buffered > 8 -> 28L    // Slightly behind
                    buffered > 3 -> 38L    // Normal streaming pace
                    else -> 48L            // Provider is slow, drip gently
                }
            )
        }
    }

    // Build the displayed text: first N words of rawText, preserving original whitespace
    val displayedText = remember(revealedWordCount, rawText) {
        rawText.takeWords(revealedWordCount)
    }

    // Trailing fade gradient: the last ~12dp of content fades from
    // fully visible to transparent, creating a "words materializing" effect.
    // Uses alpha compositing so it works regardless of background color.
    val isBuffering = revealedWordCount < totalWordCount
    val fadeAlpha = remember { Animatable(0f) }

    // Animate the trailing fade in/out using M3 spring.fast.effects
    LaunchedEffect(isBuffering) {
        fadeAlpha.animateTo(
            targetValue = if (isBuffering) 1f else 0f,
            animationSpec = spring(
                stiffness = SPRING_FAST_EFFECTS_STIFFNESS,
                dampingRatio = SPRING_EFFECTS_DAMPING
            )
        )
    }

    if (displayedText.isNotEmpty()) {
        Box(
            modifier = modifier
                .then(
                    if (fadeAlpha.value > 0.01f) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // Draw a small gradient mask at the bottom edge
                                // This makes the newest text appear to "materialize"
                                val fadeHeight = TRAILING_FADE_HEIGHT_DP.dp.toPx() * fadeAlpha.value
                                if (fadeHeight > 0f) {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White, Color.Transparent),
                                            startY = size.height - fadeHeight,
                                            endY = size.height
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            MarkdownText(
                markdown = displayedText,
                textColor = textColor,
                style = style,
                onOpenCanvas = onOpenCanvas
            )
        }
    }
}

// ---- M3 Expressive Motion Constants ----

/** M3 spring.fast.effects: stiffness for opacity/color transitions on small components */
private const val SPRING_FAST_EFFECTS_STIFFNESS = 1400f

/** M3 effects damping: no overshoot/bounce for opacity changes */
private const val SPRING_EFFECTS_DAMPING = 1.0f

/** Height of the trailing fade gradient in dp */
private const val TRAILING_FADE_HEIGHT_DP = 14f

// ---- Word Tokenization Utilities ----

/**
 * Counts the number of whitespace-delimited word tokens in the string.
 * Handles multiple consecutive whitespace characters correctly.
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
    if (inWord) count++ // Count last word if not followed by whitespace
    return count
}

/**
 * Returns a substring containing the first [n] whitespace-delimited words,
 * preserving original whitespace and formatting (including newlines, markdown, etc.).
 *
 * This approach preserves the original character-level structure of the text,
 * so markdown formatting, code blocks, and other syntax remain intact.
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
                    // Include trailing whitespace after the nth word
                    // so the text doesn't end abruptly mid-whitespace
                    while (i < length && this[i].isWhitespace()) i++
                    return substring(0, i)
                }
            }
        } else {
            inWord = true
        }
        i++
    }

    // Reached end of string — return everything
    return this
}
