package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.seaglass.Surface
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveShapes
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
 * Empty-thread hero: a tonal squircle card ([ExpressiveShapes.Card]) carrying a primary-container
 * glyph tile ([ExpressiveShapes.Tile]), a headline and one supporting line.
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

    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .graphicsLayer {
                val p = entrance.value
                alpha = p
                scaleX = 0.92f + 0.08f * p
                scaleY = 0.92f + 0.08f * p
            },
        shape = ExpressiveShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = ExpressiveShapes.Tile,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = copy.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = copy.supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
