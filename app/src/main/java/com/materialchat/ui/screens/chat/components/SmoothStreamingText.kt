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
 * While streaming, text is revealed from a tiny local buffer instead of showing
 * each provider/Room update immediately. This smooths uneven chunks while still
 * rendering Markdown during the reveal and full Markdown after completion.
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
    val haptics = rememberHapticFeedback()
    val latestRawText by rememberUpdatedState(rawText)
    var visibleText by remember { mutableStateOf(if (isStreaming) "" else rawText) }

    LaunchedEffect(isStreaming) {
        while (isStreaming || visibleText != latestRawText) {
            val target = latestRawText
            when {
                target.isEmpty() -> visibleText = ""
                visibleText.length > target.length || !target.startsWith(visibleText) -> {
                    visibleText = target.commonPrefixWith(visibleText)
                }
                visibleText.length < target.length -> {
                    val nextEnd = target.nextSmoothStreamingRevealEnd(visibleText.length)
                    if (nextEnd > visibleText.length) {
                        visibleText = target.substring(0, nextEnd)
                        haptics.perform(HapticPattern.CONTENT_TICK, hapticsEnabled)
                    }
                }
            }
            delay(if (visibleText == target) STREAMING_IDLE_POLL_MS else STREAMING_TEXT_FRAME_MS)
        }
    }

    val revealInProgress = isStreaming || visibleText != rawText
    if (!revealInProgress) {
        MarkdownText(
            markdown = rawText,
            textColor = textColor,
            style = style,
            isStreaming = false,
            fillWidth = false,
            onOpenCanvas = onOpenCanvas,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier) {
        if (visibleText.isNotBlank()) {
            MarkdownText(
                markdown = visibleText,
                textColor = textColor,
                style = style,
                isStreaming = true,
                fillWidth = false,
                onOpenCanvas = onOpenCanvas
            )
        }
        StreamingTailEffect(
            tokenVersion = visibleText.length,
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
    val haptics = rememberHapticFeedback()
    val latestRawText by rememberUpdatedState(rawText)
    var visibleText by remember { mutableStateOf(if (isStreaming) "" else rawText) }

    LaunchedEffect(isStreaming) {
        while (isStreaming || visibleText != latestRawText) {
            val target = latestRawText
            when {
                target.isEmpty() -> visibleText = ""
                visibleText.length > target.length || !target.startsWith(visibleText) -> {
                    visibleText = target.commonPrefixWith(visibleText)
                }
                visibleText.length < target.length -> {
                    val nextEnd = target.nextSmoothStreamingRevealEnd(visibleText.length)
                    if (nextEnd > visibleText.length) {
                        visibleText = target.substring(0, nextEnd)
                        haptics.perform(HapticPattern.THINKING_TICK, hapticsEnabled)
                    }
                }
            }
            delay(if (visibleText == target) STREAMING_IDLE_POLL_MS else THINKING_TEXT_FRAME_MS)
        }
    }

    Text(
        text = visibleText,
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

private fun String.nextSmoothStreamingRevealEnd(startIndex: Int): Int {
    if (isEmpty()) return 0
    val start = startIndex.coerceIn(0, length)
    if (start >= length) return length

    val remaining = length - start
    val step = when {
        remaining > 280 -> 24
        remaining > 120 -> 16
        remaining > 48 -> 10
        else -> 5
    }
    var end = (start + step).coerceAtMost(length)

    // If we are very close to a natural boundary, include it so words do not
    // appear as visibly chopped fragments.
    while (end < length && end - start < step + 6 && !this[end - 1].isWhitespace()) {
        val char = this[end]
        if (char.isWhitespace() || char in NATURAL_STREAMING_BOUNDARIES) {
            end++
            break
        }
        end++
    }

    return end.coerceAtLeast((start + 1).coerceAtMost(length))
}

private val NATURAL_STREAMING_BOUNDARIES = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}')
private const val STREAMING_TEXT_FRAME_MS = 18L
private const val THINKING_TEXT_FRAME_MS = 28L
private const val STREAMING_IDLE_POLL_MS = 24L
