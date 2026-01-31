package com.materialchat.ui.oauth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.OAuthState
import com.materialchat.ui.components.M3ExpressiveInlineLoading
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Material 3 Expressive OAuth status indicator.
 *
 * A chip-style indicator that shows the current OAuth authentication state:
 * - Unauthenticated: Gray with "Not connected" label
 * - Authenticating: Loading animation with "Connecting..." label
 * - Authenticated: Green/Primary with email and checkmark
 * - Error: Red with error message
 *
 * Uses spring-physics animations for smooth state transitions.
 *
 * @param state The current OAuth state
 * @param modifier Optional modifier
 * @param showEmail Whether to show the email when authenticated
 * @param compact Whether to use a compact layout (icon only when authenticated)
 */
@Composable
fun OAuthStatusIndicator(
    state: OAuthState,
    modifier: Modifier = Modifier,
    showEmail: Boolean = true,
    compact: Boolean = false
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (state) {
            is OAuthState.Authenticated -> MaterialTheme.colorScheme.primaryContainer
            is OAuthState.Authenticating -> MaterialTheme.colorScheme.secondaryContainer
            is OAuthState.Error -> MaterialTheme.colorScheme.errorContainer
            is OAuthState.Unauthenticated -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "statusBackgroundColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when (state) {
            is OAuthState.Authenticated -> MaterialTheme.colorScheme.onPrimaryContainer
            is OAuthState.Authenticating -> MaterialTheme.colorScheme.onSecondaryContainer
            is OAuthState.Error -> MaterialTheme.colorScheme.onErrorContainer
            is OAuthState.Unauthenticated -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "statusContentColor"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 4.dp else 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn()).togetherWith(
                        scaleOut(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh
                            )
                        ) + fadeOut()
                    )
                },
                label = "statusIcon"
            ) { currentState ->
                when (currentState) {
                    is OAuthState.Authenticated -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Connected",
                            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
                        )
                    }
                    is OAuthState.Authenticating -> {
                        M3ExpressiveInlineLoading(
                            modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                            color = contentColor
                        )
                    }
                    is OAuthState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Error",
                            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
                        )
                    }
                    is OAuthState.Unauthenticated -> {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = "Not connected",
                            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
                        )
                    }
                }
            }

            if (!compact) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn() + expandHorizontally()).togetherWith(
                            fadeOut() + shrinkHorizontally()
                        )
                    },
                    label = "statusText"
                ) { currentState ->
                    when (currentState) {
                        is OAuthState.Authenticated -> {
                            Text(
                                text = if (showEmail) currentState.email else "Connected",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is OAuthState.Authenticating -> {
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        is OAuthState.Error -> {
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is OAuthState.Unauthenticated -> {
                            Text(
                                text = "Not connected",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple dot indicator for OAuth connection status.
 *
 * A minimal indicator showing just a colored dot:
 * - Green: Connected
 * - Yellow/Orange: Connecting
 * - Red: Error
 * - Gray: Not connected
 *
 * @param state The current OAuth state
 * @param modifier Optional modifier
 * @param size The size of the dot indicator
 */
@Composable
fun OAuthStatusDot(
    state: OAuthState,
    modifier: Modifier = Modifier,
    size: Int = 8
) {
    val color by animateColorAsState(
        targetValue = when (state) {
            is OAuthState.Authenticated -> Color(0xFF4CAF50) // Green
            is OAuthState.Authenticating -> Color(0xFFFF9800) // Orange
            is OAuthState.Error -> MaterialTheme.colorScheme.error
            is OAuthState.Unauthenticated -> MaterialTheme.colorScheme.outline
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "dotColor"
    )

    val pulseAlpha by animateFloatAsState(
        targetValue = if (state is OAuthState.Authenticating) 0.6f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(size.dp)
            .graphicsLayer { alpha = pulseAlpha }
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * OAuth status badge for provider cards.
 *
 * Shows the authentication status in a compact badge format suitable
 * for display in provider cards or list items.
 *
 * @param state The current OAuth state
 * @param modifier Optional modifier
 */
@Composable
fun OAuthStatusBadge(
    state: OAuthState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OAuthStatusDot(state = state)

        Text(
            text = when (state) {
                is OAuthState.Authenticated -> state.email
                is OAuthState.Authenticating -> "Connecting..."
                is OAuthState.Error -> "Error"
                is OAuthState.Unauthenticated -> "Not signed in"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (state) {
                is OAuthState.Authenticated -> MaterialTheme.colorScheme.primary
                is OAuthState.Authenticating -> MaterialTheme.colorScheme.secondary
                is OAuthState.Error -> MaterialTheme.colorScheme.error
                is OAuthState.Unauthenticated -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
