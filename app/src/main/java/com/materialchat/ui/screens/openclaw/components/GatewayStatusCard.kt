package com.materialchat.ui.screens.openclaw.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.model.openclaw.GatewayStatus
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Card showing the gateway connection status with an animated pulse indicator,
 * version info, uptime, and connect/disconnect controls.
 *
 * Uses spring-animated pulse for the connection indicator dot and
 * M3 Expressive color tokens throughout.
 *
 * @param connectionState Current gateway connection state
 * @param status Gateway status information (null if not fetched)
 * @param latencyMs Current latency in milliseconds
 * @param isRefreshing Whether a status refresh is in progress
 * @param onConnect Callback to connect to the gateway
 * @param onDisconnect Callback to disconnect from the gateway
 * @param onRefresh Callback to refresh gateway status
 * @param modifier Modifier for the card
 */
@Composable
fun GatewayStatusCard(
    connectionState: GatewayConnectionState,
    status: GatewayStatus?,
    latencyMs: Long?,
    isRefreshing: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState is GatewayConnectionState.Connected
    val isConnecting = connectionState is GatewayConnectionState.Connecting
    val isError = connectionState is GatewayConnectionState.Error

    // M3 Expressive: EFFECTS spring for color (no bounce)
    val cardColor by animateColorAsState(
        targetValue = when {
            isConnected -> MaterialTheme.colorScheme.primaryContainer
            isConnecting -> MaterialTheme.colorScheme.secondaryContainer
            isError -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "cardColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
            isConnecting -> MaterialTheme.colorScheme.onSecondaryContainer
            isError -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "contentColor"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with status indicator and refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Animated pulse indicator
                    PulseIndicator(
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        isError = isError
                    )

                    Column {
                        Text(
                            text = when {
                                isConnected -> "Connected"
                                isConnecting -> "Connecting..."
                                isError -> "Connection Error"
                                else -> "Disconnected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                        if (isConnected && status != null) {
                            Text(
                                text = "v${status.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (isConnected) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh status",
                            tint = contentColor
                        )
                    }
                }
            }

            // Status details (visible when connected)
            if (isConnected && status != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatusDetail(
                        label = "Uptime",
                        value = status.uptime.ifEmpty { "N/A" },
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    StatusDetail(
                        label = "Agent",
                        value = status.agentId,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (latencyMs != null) {
                        StatusDetail(
                            label = "Latency",
                            value = "${latencyMs}ms",
                            color = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Error message
            if (isError) {
                val errorState = connectionState as GatewayConnectionState.Error
                Text(
                    text = errorState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            // Connect/Disconnect button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !isConnecting,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isConnecting) "Connecting..." else "Connect")
                    }
                }
            }
        }
    }
}

/**
 * Animated pulse indicator for connection status.
 * Uses spring physics for the pulse scale animation.
 */
@Composable
private fun PulseIndicator(
    isConnected: Boolean,
    isConnecting: Boolean,
    isError: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by if (isConnected) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else if (isConnecting) {
        infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
            label = "pulseScale"
        )
    }

    val dotColor by animateColorAsState(
        targetValue = when {
            isConnected -> MaterialTheme.colorScheme.primary
            isConnecting -> MaterialTheme.colorScheme.secondary
            isError -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "dotColor"
    )

    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(pulseScale)
            .background(
                color = dotColor.copy(alpha = 0.3f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = dotColor,
                    shape = CircleShape
                )
        )
    }
}

/**
 * Status detail showing a label and value.
 */
@Composable
private fun StatusDetail(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
