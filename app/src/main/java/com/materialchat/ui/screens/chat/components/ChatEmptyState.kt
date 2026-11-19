package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.materialchat.ui.components.ExpressiveEmptyState
import com.materialchat.ui.screens.conversations.EmptyStateAccent
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.particles.rememberParticleMotionGate

/** The two lines the empty chat shows before the first message. */
data class ChatEmptyStateCopy(
    val title: String,
    val supporting: String
)

/**
 * Pure copy rule for the empty state. A persona chat leads with the persona; otherwise the
 * current model is named when known. The supporting line points at the starters only when
 * there are starters to point at.
 */
fun chatEmptyStateCopy(
    modelName: String,
    personaName: String?,
    hasStarters: Boolean
): ChatEmptyStateCopy {
    val trimmedModel = modelName.trim()
    val trimmedPersona = personaName?.trim()?.takeIf { it.isNotEmpty() }
    val title = when {
        trimmedPersona != null -> "Chat with $trimmedPersona"
        else -> "Ask anything"
    }
    val supporting = when {
        hasStarters -> "Pick a starter below, or type your own first message."
        trimmedModel.isNotEmpty() -> "$trimmedModel is ready. Replies stream in word by word."
        else -> "Replies stream in word by word."
    }
    return ChatEmptyStateCopy(title = title, supporting = supporting)
}

/**
 * Empty-thread hero: the shared [ExpressiveEmptyState] card (W5 theme: `ExpressiveShapes.Card`
 * over `surfaceContainer`, primary-container glyph tile, `headlineSmall` Bold headline, one
 * `bodyMedium` supporting line, 24dp padding, 360dp cap) so an empty chat reads like every
 * other empty screen.
 *
 * Motion is a single one-shot entrance spring ([ExpressiveMotion.Spatial.container]) that
 * scales the card in from 0.92 and fades it up; nothing animates continuously. When the
 * decoration motion gate denies motion (power saver, animator scale 0, stopped host) the
 * card snaps straight to its resting state.
 */
@Composable
fun ChatEmptyState(
    copy: ChatEmptyStateCopy,
    modifier: Modifier = Modifier
) {
    val gate = rememberParticleMotionGate()
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(copy.title, gate.motionAllowed) {
        if (gate.motionAllowed) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, animationSpec = ExpressiveMotion.Spatial.container())
        } else {
            entrance.snapTo(1f)
        }
    }

    ExpressiveEmptyState(
        icon = Icons.Filled.AutoAwesome,
        title = copy.title,
        message = copy.supporting,
        accent = EmptyStateAccent.Primary,
        modifier = modifier.graphicsLayer {
            val p = entrance.value
            alpha = p
            scaleX = 0.92f + 0.08f * p
            scaleY = 0.92f + 0.08f * p
        }
    )
}
