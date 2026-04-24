package com.materialchat.ui.screens.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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

/**
 * Streaming text composable optimized for long responses.
 *
 * While a response is streaming, this intentionally renders lightweight plain text
 * and throttled haptics. Full Markdown formatting is applied immediately when the
 * message completes. This avoids reparsing and relaying out large Markdown trees
 * on every token or word while the model is still producing content.
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
            isStreaming = false,
            onOpenCanvas = onOpenCanvas,
            modifier = modifier
        )
        return
    }

    // Empty content during streaming — nothing to show yet
    if (rawText.isEmpty()) return

    val haptics = rememberHapticFeedback()
    var lastHapticLength by remember { mutableIntStateOf(0) }
    var lastHapticTimeMs by remember { mutableLongStateOf(0L) }

    // Streaming used to re-parse and re-layout markdown once per revealed word.
    // That looked nice on short replies, but it becomes very expensive for large
    // responses and long chats. While streaming, render lightweight plain text and
    // let final markdown formatting appear when the message completes.
    LaunchedEffect(rawText.length) {
        val now = System.currentTimeMillis()
        if (rawText.length - lastHapticLength >= STREAMING_HAPTIC_CHAR_STEP &&
            now - lastHapticTimeMs >= STREAMING_HAPTIC_MIN_INTERVAL_MS
        ) {
            haptics.perform(HapticPattern.CONTENT_TICK, hapticsEnabled)
            lastHapticLength = rawText.length
            lastHapticTimeMs = now
        }
    }

    Text(
        text = rawText,
        style = style,
        color = textColor,
        modifier = modifier
    )
}

/**
 * Smooth streaming text for thinking/reasoning content.
 *
 * Same lightweight streaming behavior as [SmoothStreamingText] but renders as
 * italic text, matching ThinkingSection's visual style. Fires ultra-subtle
 * [HapticPattern.THINKING_TICK] at a throttled cadence.
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
    var lastHapticLength by remember { mutableIntStateOf(0) }
    var lastHapticTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(rawText.length) {
        val now = System.currentTimeMillis()
        if (rawText.length - lastHapticLength >= THINKING_HAPTIC_CHAR_STEP &&
            now - lastHapticTimeMs >= STREAMING_HAPTIC_MIN_INTERVAL_MS
        ) {
            haptics.perform(HapticPattern.THINKING_TICK, hapticsEnabled)
            lastHapticLength = rawText.length
            lastHapticTimeMs = now
        }
    }

    Text(
        text = rawText,
        style = style,
        color = textColor,
        fontStyle = FontStyle.Italic,
        modifier = modifier
    )
}

private const val STREAMING_HAPTIC_CHAR_STEP = 36
private const val THINKING_HAPTIC_CHAR_STEP = 56
private const val STREAMING_HAPTIC_MIN_INTERVAL_MS = 120L
