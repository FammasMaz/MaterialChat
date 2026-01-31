package com.materialchat.ui.oauth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.M3ExpressiveInlineLoading
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Material 3 Expressive OAuth login button.
 *
 * A button designed for OAuth sign-in flows with:
 * - Spring-physics scale and shape morph animations
 * - Loading state with inline progress indicator
 * - Support for both sign-in and sign-out states
 * - Consistent styling with M3 Expressive design system
 *
 * @param onClick Callback when the button is clicked
 * @param modifier Optional modifier
 * @param isLoading Whether an OAuth operation is in progress
 * @param isAuthenticated Whether the user is currently authenticated
 * @param email The authenticated user's email (shown when authenticated)
 * @param providerName The name of the OAuth provider (e.g., "Google", "Antigravity")
 * @param enabled Whether the button is enabled
 */
@Composable
fun OAuthLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isAuthenticated: Boolean = false,
    email: String? = null,
    providerName: String = "OAuth",
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive SPATIAL spring for scale
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "oauthButtonScale"
    )

    // M3 Expressive SPATIAL spring for shape morph (corners increase on press)
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 24.dp else 20.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "oauthButtonCornerRadius"
    )

    val shape = RoundedCornerShape(cornerRadius)

    if (isAuthenticated) {
        // Authenticated state: Show sign out button
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .scale(scale),
            enabled = enabled && !isLoading,
            shape = shape,
            interactionSource = interactionSource
        ) {
            OAuthButtonContent(
                icon = Icons.AutoMirrored.Outlined.Logout,
                text = "Sign out",
                subtext = email,
                isLoading = isLoading
            )
        }
    } else {
        // Unauthenticated state: Show sign in button
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .scale(scale),
            enabled = enabled && !isLoading,
            shape = shape,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            OAuthButtonContent(
                icon = Icons.AutoMirrored.Outlined.Login,
                text = "Sign in with $providerName",
                isLoading = isLoading
            )
        }
    }
}

/**
 * Internal content composable for the OAuth button.
 */
@Composable
private fun OAuthButtonContent(
    icon: ImageVector,
    text: String,
    subtext: String? = null,
    isLoading: Boolean = false
) {
    if (isLoading) {
        M3ExpressiveInlineLoading(
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Authenticating...",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (subtext != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($subtext)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Compact OAuth login button for use in cards or rows.
 *
 * A smaller variant of OAuthLoginButton designed for inline use.
 *
 * @param onClick Callback when the button is clicked
 * @param modifier Optional modifier
 * @param isLoading Whether an OAuth operation is in progress
 * @param isAuthenticated Whether the user is currently authenticated
 * @param enabled Whether the button is enabled
 */
@Composable
fun OAuthLoginButtonCompact(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isAuthenticated: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "compactButtonScale"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 16.dp else 12.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "compactButtonCornerRadius"
    )

    if (isAuthenticated) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.scale(scale),
            enabled = enabled && !isLoading,
            shape = RoundedCornerShape(cornerRadius),
            interactionSource = interactionSource
        ) {
            if (isLoading) {
                M3ExpressiveInlineLoading(
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = "Sign out",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign out")
            }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.scale(scale),
            enabled = enabled && !isLoading,
            shape = RoundedCornerShape(cornerRadius),
            interactionSource = interactionSource
        ) {
            if (isLoading) {
                M3ExpressiveInlineLoading(
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Login,
                    contentDescription = "Sign in",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign in")
            }
        }
    }
}
