package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.materialchat.R
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.SquircleShape

/** Squircle contour of the composer text field, matching the app's card contour. */
internal val ComposerFieldShape = SquircleShape(28.dp)

/** The three states of the composer's primary control. */
internal enum class ComposerSendState { Idle, Ready, Streaming }

internal fun composerSendState(isStreaming: Boolean, canSend: Boolean): ComposerSendState = when {
    isStreaming -> ComposerSendState.Streaming
    canSend -> ComposerSendState.Ready
    else -> ComposerSendState.Idle
}

/**
 * The composer's send / stop control as a real M3 Expressive filled [Button].
 *
 * State machine: [ComposerSendState.Idle] (nothing to send, disabled, neutral tone),
 * [ComposerSendState.Ready] (filled primary, send icon) and [ComposerSendState.Streaming]
 * (error-container stop button wrapped in an indeterminate [CircularWavyProgressIndicator]
 * whose colour follows the reasoning effort, as the previous indicator did).
 *
 * Press feedback is the component's own shape morph from [ButtonDefaults.shapes] (round →
 * pressed corner), driven by the theme's expressive motion scheme. Colours are effects
 * springs (no overshoot). Nothing here retargets per frame.
 *
 * Behaviour is identical to the control it replaces: tap sends when ready, cancels when
 * streaming, and is disabled otherwise.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ComposerSendButton(
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH
) {
    val scheme = MaterialTheme.colorScheme
    val state = composerSendState(isStreaming, canSend)

    val containerColor by animateColorAsState(
        targetValue = when (state) {
            ComposerSendState.Streaming -> scheme.errorContainer
            ComposerSendState.Ready -> scheme.primary
            ComposerSendState.Idle -> scheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "composerSendContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when (state) {
            ComposerSendState.Streaming -> scheme.onErrorContainer
            ComposerSendState.Ready -> scheme.onPrimary
            ComposerSendState.Idle -> scheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "composerSendContent"
    )
    // Ring colour mirrors the reasoning effort, as the old streaming indicator did.
    val ringColor = when (reasoningEffort) {
        ReasoningEffort.NONE -> scheme.onSurfaceVariant
        ReasoningEffort.LOW -> scheme.tertiary
        ReasoningEffort.MEDIUM -> scheme.secondary
        ReasoningEffort.HIGH -> scheme.primary
        ReasoningEffort.XHIGH, ReasoningEffort.MAX -> scheme.error
    }

    val description = stringResource(
        when (state) {
            ComposerSendState.Streaming -> R.string.chat_stop_generating
            ComposerSendState.Ready -> R.string.chat_send_message
            ComposerSendState.Idle -> R.string.chat_send_message_disabled
        }
    )
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val ringStroke = remember(density) {
        Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round)
    }
    // Ring fades/scales in via graphicsLayer (no layout churn); it is only composed while
    // visible so the indeterminate wave does not tick frames when idle.
    val ringAlpha by animateFloatAsState(
        targetValue = if (state == ComposerSendState.Streaming) 1f else 0f,
        animationSpec = ExpressiveMotion.Effects.alpha(),
        label = "composerRingAlpha"
    )
    val ringScale by animateFloatAsState(
        targetValue = if (state == ComposerSendState.Streaming) 1f else 0.6f,
        animationSpec = if (state == ComposerSendState.Streaming) {
            ExpressiveMotion.Spatial.default()
        } else {
            ExpressiveMotion.Effects.alpha()
        },
        label = "composerRingScale"
    )

    Button(
        onClick = {
            when (state) {
                ComposerSendState.Streaming -> onCancel()
                ComposerSendState.Ready -> onSend()
                ComposerSendState.Idle -> Unit
            }
        },
        shapes = ButtonDefaults.shapes(),
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = description },
        enabled = state != ComposerSendState.Idle,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
        ),
        elevation = null,
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (state == ComposerSendState.Streaming || ringAlpha > 0.01f) {
                CircularWavyProgressIndicator(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            alpha = ringAlpha
                            scaleX = ringScale
                            scaleY = ringScale
                        },
                    color = ringColor,
                    trackColor = Color.Transparent,
                    stroke = ringStroke,
                    trackStroke = ringStroke,
                    gapSize = 0.dp
                )
            }
            AnimatedContent(
                targetState = state == ComposerSendState.Streaming,
                transitionSpec = {
                    (fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()) +
                        scaleIn(animationSpec = ExpressiveMotion.Spatial.scale(), initialScale = 0.6f))
                        .togetherWith(
                            fadeOut(animationSpec = ExpressiveMotion.Effects.alpha()) +
                                scaleOut(animationSpec = ExpressiveMotion.Effects.alpha(), targetScale = 0.6f)
                        )
                },
                label = "composerSendIcon"
            ) { streaming ->
                if (streaming) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
