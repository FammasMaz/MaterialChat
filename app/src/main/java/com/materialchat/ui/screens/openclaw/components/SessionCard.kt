package com.materialchat.ui.screens.openclaw.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.openclaw.ChannelType
import com.materialchat.domain.model.openclaw.OpenClawSession

/**
 * Card for a session list item showing session key, channel icon,
 * timestamps, and message count.
 *
 * Uses M3 Expressive shapes (16dp card radius) and color tokens.
 * Provides a 48dp minimum touch target via the Surface click handler.
 *
 * @param session The OpenClaw session to display
 * @param onClick Callback when the card is tapped
 * @param modifier Modifier for the card
 */
@Composable
fun SessionCard(
    session: OpenClawSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val relativeTime = remember(session.lastActivity) {
        if (session.lastActivity > 0) {
            DateUtils.getRelativeTimeSpanString(
                session.lastActivity,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } else {
            "Unknown"
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = session.channelType.channelIcon,
                        contentDescription = session.channelType?.displayName ?: "Direct",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Session info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = session.title ?: session.label ?: session.key,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (session.channelType != null) {
                        Text(
                            text = session.channelType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "\u00B7",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Message count badge
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = "${session.messageCount}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Extension property mapping nullable ChannelType to a Material icon.
 */
private val ChannelType?.channelIcon: ImageVector
    get() = when (this) {
        ChannelType.WHATSAPP -> Icons.Filled.PhoneAndroid
        ChannelType.TELEGRAM -> Icons.Filled.Chat
        ChannelType.DISCORD -> Icons.Filled.Forum
        ChannelType.SLACK -> Icons.Filled.Tag
        ChannelType.MATRIX -> Icons.Filled.Language
        ChannelType.IRC -> Icons.Filled.Tag
        ChannelType.WEB -> Icons.Filled.Language
        ChannelType.SMS -> Icons.Filled.Sms
        ChannelType.EMAIL -> Icons.Filled.Email
        ChannelType.UNKNOWN -> Icons.AutoMirrored.Filled.Chat
        null -> Icons.AutoMirrored.Filled.Chat
    }
