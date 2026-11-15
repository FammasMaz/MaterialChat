package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Title shown on the working indicator. Bold and upright; never italic. */
internal const val CHAT_WORKING_TITLE = "Thinking"

/** Status line under the title, matching the assistant overlay's wording. */
internal const val CHAT_WORKING_STATUS = "Working on your answer"

/**
 * The "working on your answer" state of a chat bubble, shown from send until the first token.
 *
 * A port of the assistant overlay's thinking surface into the message bubble: the M3
 * Expressive [LoadingIndicator] (polygons morphing into one another, the spec's "working"
 * motion) inside a secondary circle, next to a bold upright title over a regular status line.
 * The bubble already provides the surface, shape and colours, so this draws no container of
 * its own and keeps the bubble's text colour for the title.
 *
 * Motion: one spring on appearance, anchored at the bubble's bottom-start corner, and nothing
 * else. The composition owns no timers and no infinite transition; the indicator is the only
 * continuous motion, exactly as in the assistant's version.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatWorkingIndicator(
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val entrance = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f))
    }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
                transformOrigin = TransformOrigin(0f, 1f)
            }
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
            .semantics { contentDescription = "$CHAT_WORKING_TITLE. $CHAT_WORKING_STATUS" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(
                modifier = Modifier.size(30.dp),
                color = scheme.onSecondaryContainer
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = CHAT_WORKING_TITLE,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp
                ),
                color = textColor
            )
            Text(
                text = CHAT_WORKING_STATUS,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                color = scheme.onSurfaceVariant
            )
        }
    }
}
