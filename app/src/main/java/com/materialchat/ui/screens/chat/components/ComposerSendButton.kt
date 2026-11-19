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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.materialchat.R
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveSizes
import com.materialchat.ui.theme.SquircleShape

/**
 * The composer text field's resting corner radius: 28dp, the same value as [ExpressiveShapes.Card]
 * and the former `CustomShapes.MessageInputContainer`.
 *
 * Ruling (W7 chat, audit #6 / W4): **the composer stays at 28dp**. The field draws it as a
 * [SquircleShape] (the app's card contour), tightening to [ComposerFieldFocusedRadius] on the
 * spatial shape spring while focused, and it is not migrated to a chip capsule or to the bubble
 * family's circular corners. `MessageInput` reads these two values; nothing else may restate them.
 */
internal val ComposerFieldRadius = 28.dp

/** The composer field's corner radius while it has focus (the focus morph target). */
internal val ComposerFieldFocusedRadius = 20.dp

/** Squircle contour of the composer text field at rest, matching the app's card contour. */
internal val ComposerFieldShape = SquircleShape(ComposerFieldRadius)

/** The three states of the composer's primary control. */
internal enum class ComposerSendState { Idle, Ready, Streaming }

internal fun composerSendState(isStreaming: Boolean, canSend: Boolean): ComposerSendState = when {
    isStreaming -> ComposerSendState.Streaming
    canSend -> ComposerSendState.Ready
    else -> ComposerSendState.Idle
}

/**
 * Where each state sits on the button's shape track. The track is two chained morphs over
 * [MaterialShapes]: 0 = four-sided cookie (idle, nothing to send), 1 = nine-sided scalloped
 * cookie (ready to send — a plain circle read as the old button next to the expressive attach
 * control), 2 = rounded square (streaming: the stop block). A press pushes the contour toward
 * the neighbouring shape (cookie squares off, square softens) so the press reads as a shape
 * morph, the M3 Expressive button gesture, rather than only a scale dip.
 */
internal const val COMPOSER_SEND_TRACK_END = 2f

internal fun composerSendShapeProgress(state: ComposerSendState, pressed: Boolean): Float = when (state) {
    ComposerSendState.Idle -> 0f
    ComposerSendState.Ready -> if (pressed) 1.35f else 1f
    ComposerSendState.Streaming -> if (pressed) 1.65f else COMPOSER_SEND_TRACK_END
}

/**
 * A [Shape] that walks a two-segment morph track: [first] for progress 0..1, [second] for
 * 1..2. [MaterialShapes] polygons are normalised to the unit square, so the path is scaled
 * to the outline size. Pure path geometry; works on every supported API level.
 */
internal class ComposerSendTrackShape(
    private val first: Morph,
    private val second: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val clamped = progress.coerceIn(0f, COMPOSER_SEND_TRACK_END)
        val path = if (clamped <= 1f) {
            first.toPath(clamped)
        } else {
            second.toPath(clamped - 1f)
        }
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

/**
 * The composer's send / stop control as an M3 Expressive filled [Button] whose contour
 * morphs between states.
 *
 * State machine: [ComposerSendState.Idle] (nothing to send, disabled, neutral tone, cookie
 * contour), [ComposerSendState.Ready] (filled primary, scalloped cookie, send glyph) and
 * [ComposerSendState.Streaming] (error-container rounded-square stop button with a plain,
 * calm stop glyph). The contour and press feedback ride the theme's spatial shape-morph
 * spring, scale dips on the spatial scale spring, colours are effects springs (no
 * overshoot). While streaming nothing animates continuously: the only motion is the one-shot
 * glyph swap and the press morph.
 *
 * Behaviour: tap sends when ready, cancels when streaming, and is disabled otherwise.
 *
 * Deliberately a raw M3 [Button], not `ExpressiveButton` (W7 chat part 2, audit F9/F11):
 * the shared button animates a single `RoundedCornerShape` corner radius on press, while this
 * control's contour is a [Morph] track over `MaterialShapes` (cookie → scalloped cookie →
 * square) that also encodes the Idle/Ready/Streaming state, so the shared button cannot carry
 * it. The glyphs are [ExpressiveSizes.IconLarge] (24dp, the M3 glyph inside a 48dp filled
 * control; W7 theme icon ladder) — they were a bespoke 22dp before.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ComposerSendButton(
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val state = composerSendState(isStreaming, canSend)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val idleToReady = remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie9Sided) }
    val readyToStreaming = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Square) }
    val trackProgress by animateFloatAsState(
        targetValue = composerSendShapeProgress(state, pressed),
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "composerSendShape"
    )
    val shape = remember(trackProgress) {
        ComposerSendTrackShape(idleToReady, readyToStreaming, trackProgress)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed && state != ComposerSendState.Idle) 0.92f else 1f,
        animationSpec = ExpressiveMotion.Press.Scale,
        label = "composerSendScale"
    )

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

    val description = stringResource(
        when (state) {
            ComposerSendState.Streaming -> R.string.chat_stop_generating
            ComposerSendState.Ready -> R.string.chat_send_message
            ComposerSendState.Idle -> R.string.chat_send_message_disabled
        }
    )

    Button(
        onClick = {
            when (state) {
                ComposerSendState.Streaming -> onCancel()
                ComposerSendState.Ready -> onSend()
                ComposerSendState.Idle -> Unit
            }
        },
        shape = shape,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
                        modifier = Modifier.size(ExpressiveSizes.IconLarge)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(ExpressiveSizes.IconLarge)
                    )
                }
            }
        }
    }
}
