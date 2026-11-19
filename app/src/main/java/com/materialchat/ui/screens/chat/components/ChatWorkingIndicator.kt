package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveSizes

/** Title shown on the working indicator. Bold and upright; never italic. */
internal const val CHAT_WORKING_TITLE = "Thinking"

/** Status line under the title, matching the assistant overlay's wording. */
internal const val CHAT_WORKING_STATUS = "Working on your answer"

/**
 * Geometry of the working indicator, pinned in `ChatThinkingGeometryTest`.
 *
 * The indicator sits inside a bubble whose `EXPRESSIVE` corners are asymmetric (an 8dp origin
 * corner at the top-start, 28dp everywhere else; `MessageBubbleShapes.AssistantBubble`). The
 * bubble's own content padding is symmetric (16dp / 12dp), so a one-sided inset here is the
 * one thing that can push the circle off-centre against the sharp corner: the port carried
 * `end = 8dp` with no start inset, which left the 48dp circle 16dp from the bubble's leading
 * edge and the text 24dp from the trailing edge, so the whole block sat crowded into the
 * origin corner. Every side now carries the same inset, the assistant thinking surface's
 * `AssistantSpacing.Tight` (4dp), so the circle is centred in its own slot whatever the
 * bubble's corners do around it.
 */
internal object ChatWorkingIndicatorGeometry {
    /**
     * The contained indicator's container: the M3 `LoadingIndicatorDefaults.ContainerWidth` /
     * `ContainerHeight` (48dp, a full circle, 38dp active indicator), the theme's hero size.
     */
    val Container: Dp = ExpressiveSizes.IndicatorHero

    /** The inset on every side of the indicator row; equal on all four sides by contract. */
    val Inset: Dp = 4.dp

    /** Gap between the indicator and the title column. */
    val Gap: Dp = 12.dp

    /** Gap between the title and the status line. */
    val TextGap: Dp = 2.dp

    /** The row's insets: the same [Inset] on start, end, top and bottom. */
    fun insets(): PaddingValues = PaddingValues(all = Inset)
}

/**
 * The "working on your answer" state of a chat bubble, shown from send until the first token.
 *
 * A port of the assistant overlay's thinking surface into the message bubble: the official M3
 * Expressive [ContainedLoadingIndicator] (polygons morphing into one another, the spec's
 * "working" motion, 38dp active inside its 48dp container) in the app's primary colour, next
 * to a bold upright title over a regular status line. The bubble already provides the
 * surface, shape and colours, so this draws no container of its own and keeps the bubble's
 * text colour for the title.
 *
 * Motion: one spring on appearance, anchored at the bubble's bottom-start corner, and nothing
 * else. The composition owns no timers and no infinite transition; the indicator is the only
 * continuous motion, exactly as in the assistant's version.
 *
 * Geometry: [ChatWorkingIndicatorGeometry]; the insets are symmetric so the indicator is
 * centred in its slot regardless of the bubble style's corner radii.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatWorkingIndicator(
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val entrance = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, ExpressiveMotion.Spatial.container())
    }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
                transformOrigin = TransformOrigin(0f, 1f)
            }
            .padding(ChatWorkingIndicatorGeometry.insets())
            .semantics { contentDescription = "$CHAT_WORKING_TITLE. $CHAT_WORKING_STATUS" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChatWorkingIndicatorGeometry.Gap)
    ) {
        ContainedLoadingIndicator(
            modifier = Modifier.size(ChatWorkingIndicatorGeometry.Container),
            containerColor = scheme.primaryContainer,
            indicatorColor = scheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(ChatWorkingIndicatorGeometry.TextGap)) {
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
