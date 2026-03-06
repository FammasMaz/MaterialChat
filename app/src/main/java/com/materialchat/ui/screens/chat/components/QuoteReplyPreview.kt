package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Quote reply preview shown above the message input when the user swipes
 * on a message to quote it.
 *
 * Follows M3 Expressive guidelines:
 * - Contained design with rounded corners and tonal surface
 * - Accent bar on the left using primaryContainer (containment + color)
 * - Spring-based expand/collapse animation with overshoot
 * - 48dp close button touch target
 * - Haptic feedback on dismiss
 *
 * @param quotedText The text of the message being quoted (truncated preview)
 * @param quotedRole Label for the quoted source ("You" or "Assistant")
 * @param visible Whether the quote preview is visible
 * @param onDismiss Callback when the user clears the quote
 */
@Composable
fun QuoteReplyPreview(
    quotedText: String,
    quotedRole: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberHapticFeedback()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = ExpressiveMotion.Spatial.default()
        ) + fadeIn(
            animationSpec = ExpressiveMotion.Effects.alpha()
        ),
        exit = shrinkVertically(
            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)
        ) + fadeOut(
            animationSpec = ExpressiveMotion.Effects.alpha()
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                // M3 Expressive: Accent bar for containment and visual hierarchy
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = quotedRole,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = quotedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Close button — 48dp touch target per M3 spec
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                        onDismiss()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear quote",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
