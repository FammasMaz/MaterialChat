package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.CanvasArtifact
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion
import kotlinx.coroutines.delay

/**
 * Streaming text composable optimized for long responses without looking raw.
 *
 * While streaming, Markdown is rendered at a reduced cadence instead of on every
 * provider token. Word-level haptics continue independently, so the stream keeps
 * the tactile rhythm of the original word-drip behavior without hammering Room,
 * Markdown parsing, or layout on every tiny chunk.
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

    if (rawText.isEmpty()) return

    val haptics = rememberHapticFeedback()
    val latestRawText by rememberUpdatedState(rawText)
    val latestWordCount by rememberUpdatedState(rawText.streamingWordCount())
    var renderedMarkdown by remember { mutableStateOf(rawText) }
    var hapticWordCount by remember { mutableIntStateOf(0) }

    // Progressive Markdown: update at a comfortable cadence rather than once per token.
    LaunchedEffect(isStreaming) {
        while (isStreaming) {
            val latest = latestRawText
            if (renderedMarkdown != latest) {
                renderedMarkdown = latest
            }
            delay(STREAMING_MARKDOWN_UPDATE_INTERVAL_MS)
        }
    }

    // Per-word haptics restored: decoupled from visual render cadence.
    LaunchedEffect(isStreaming) {
        while (isStreaming) {
            val target = latestWordCount
            when {
                hapticWordCount > target -> hapticWordCount = target
                hapticWordCount < target -> {
                    hapticWordCount++
                    haptics.perform(HapticPattern.CONTENT_TICK, hapticsEnabled)
                    delay(STREAMING_WORD_HAPTIC_INTERVAL_MS)
                }
                else -> delay(STREAMING_HAPTIC_IDLE_POLL_MS)
            }
        }
    }

    Column(modifier = modifier) {
        MarkdownText(
            markdown = renderedMarkdown,
            textColor = textColor,
            style = style,
            isStreaming = true,
            onOpenCanvas = onOpenCanvas
        )
        StreamingTailEffect(
            tokenVersion = rawText.length,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Smooth streaming text for thinking/reasoning content.
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
    val latestWordCount by rememberUpdatedState(rawText.streamingWordCount())
    var hapticWordCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(isStreaming) {
        while (isStreaming) {
            val target = latestWordCount
            when {
                hapticWordCount > target -> hapticWordCount = target
                hapticWordCount < target -> {
                    hapticWordCount++
                    haptics.perform(HapticPattern.THINKING_TICK, hapticsEnabled)
                    delay(THINKING_WORD_HAPTIC_INTERVAL_MS)
                }
                else -> delay(STREAMING_HAPTIC_IDLE_POLL_MS)
            }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreamingTailEffect(
    tokenVersion: Int,
    color: Color
) {
    val pulse by animateFloatAsState(
        targetValue = if (tokenVersion % 2 == 0) 1f else 0.72f,
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "streamingTailPulse"
    )
    val tailShape = MaterialShapes.SoftBurst.toShape(startAngle = 18)

    Box(
        modifier = Modifier
            .padding(top = 2.dp, start = 2.dp)
            .size(12.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                alpha = 0.36f + (pulse * 0.22f)
            }
            .clip(tailShape)
            .background(color.copy(alpha = 0.32f))
    )
}

private fun String.streamingWordCount(): Int {
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

private const val STREAMING_MARKDOWN_UPDATE_INTERVAL_MS = 160L
private const val STREAMING_WORD_HAPTIC_INTERVAL_MS = 34L
private const val THINKING_WORD_HAPTIC_INTERVAL_MS = 56L
private const val STREAMING_HAPTIC_IDLE_POLL_MS = 20L
