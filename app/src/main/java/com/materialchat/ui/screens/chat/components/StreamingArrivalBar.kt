package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.materialchat.R
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * What the reply stream is doing right now, as seen by [StreamingArrivalBar].
 */
internal enum class StreamingArrivalPhase {
    /** No stream in flight: the bar is hidden. */
    Idle,

    /** A stream is open but no token has arrived in the last moment: flat-ish, low wave. */
    Waiting,

    /** Tokens are actively arriving: full wave. */
    Arriving
}

/**
 * A [LinearWavyProgressIndicator] whose amplitude is tied to "something is actively arriving".
 *
 * The determinate overload is used at full extent so the wave spans the width; the
 * amplitude lambda reads an effects-spring state so the wave rises when tokens arrive,
 * settles to a low ripple while the model is between chunks, and flattens to a line as
 * the stream ends. The amplitude is read inside the lambda (not in composition), so the
 * composable itself never recomposes per frame; the indicator node handles redraw.
 *
 * The bar is an overlay with a fixed height and fades in/out, so it never changes the
 * layout of the message list or the composer and never fights the streaming text.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StreamingArrivalBar(
    phase: StreamingArrivalPhase,
    modifier: Modifier = Modifier
) {
    val amplitude = animateFloatAsState(
        targetValue = when (phase) {
            StreamingArrivalPhase.Idle -> 0f
            StreamingArrivalPhase.Waiting -> 0.3f
            StreamingArrivalPhase.Arriving -> 1f
        },
        animationSpec = ExpressiveMotion.Effects.listFade(),
        label = "streamingArrivalAmplitude"
    )
    val density = LocalDensity.current
    val stroke = remember(density) {
        Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round)
    }
    val description = stringResource(R.string.chat_response_arriving)

    AnimatedVisibility(
        visible = phase != StreamingArrivalPhase.Idle,
        modifier = modifier,
        enter = fadeIn(animationSpec = ExpressiveMotion.Effects.listFade()),
        exit = fadeOut(animationSpec = ExpressiveMotion.Effects.listFade())
    ) {
        LinearWavyProgressIndicator(
            progress = { 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .semantics { contentDescription = description },
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            stroke = stroke,
            trackStroke = stroke,
            gapSize = 0.dp,
            stopSize = 0.dp,
            amplitude = { amplitude.value },
            wavelength = 28.dp
        )
    }
}
