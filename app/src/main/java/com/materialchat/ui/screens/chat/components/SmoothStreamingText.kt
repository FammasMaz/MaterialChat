package com.materialchat.ui.screens.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * Each word transitions from "not yet in the string" to "rendered at full opacity",
 * creating a natural "appearing from nothing" effect without any translucency.
 *
 * Haptic feedback fires on every revealed word,
 * synced to the visual dripping rather than raw provider chunks, giving
 * a consistent tactile rhythm that matches what the user sees.
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

    // Render at full opacity — each word goes from "not in the string" to "in the string"
    // which IS "appearing from nothing into existing"
    if (displayedText.isNotEmpty()) {
        MarkdownText(
            markdown = displayedText,
            textColor = textColor,
            style = style,
            onOpenCanvas = onOpenCanvas,
            modifier = modifier
        )
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

    if (displayedText.isNotEmpty()) {
        Text(
            text = displayedText,
            style = style,
            color = textColor,
            fontStyle = FontStyle.Italic,
            modifier = modifier
        )
    }
}

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
