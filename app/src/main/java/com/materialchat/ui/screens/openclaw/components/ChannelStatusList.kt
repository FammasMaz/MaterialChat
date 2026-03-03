package com.materialchat.ui.screens.openclaw.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.openclaw.ChannelType
import com.materialchat.domain.model.openclaw.OpenClawChannel
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Grid/list of channel status cards with platform icons.
 *
 * Displays each connected channel with its platform icon, display name,
 * and connection status using M3 Expressive color tokens.
 *
 * @param channels List of OpenClaw channels to display
 * @param modifier Modifier for the list container
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelStatusList(
    channels: List<OpenClawChannel>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        channels.forEach { channel ->
            ChannelStatusChip(
                channel = channel
            )
        }
    }
}

/**
 * Individual channel status chip with icon and connection state.
 */
@Composable
private fun ChannelStatusChip(
    channel: OpenClawChannel,
    modifier: Modifier = Modifier
) {
    // M3 Expressive: EFFECTS spring for color (no bounce)
    val containerColor by animateColorAsState(
        targetValue = if (channel.isConnected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "channelChipColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (channel.isConnected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "channelChipContent"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = channel.type.icon,
                contentDescription = channel.type.displayName,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
                if (channel.accountId != null) {
                    Text(
                        text = channel.accountId,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Extension property mapping ChannelType to a Material icon.
 */
private val ChannelType.icon: ImageVector
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
        ChannelType.UNKNOWN -> Icons.Filled.Chat
    }
