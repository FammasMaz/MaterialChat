package com.materialchat.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.seaglass.Snackbar
import com.materialchat.ui.theme.ShapeMorphing
import com.materialchat.ui.theme.SquircleShape

/** Snackbar container: the app's squircle at the M3 extra-large-ish 20dp radius. */
private val SnackbarShape = SquircleShape(20.dp)

/**
 * M3 Expressive snackbar used by every `SnackbarHost` on the home and settings
 * screens. Keeps the M3 inverse-surface container for contrast over any content,
 * squares it off with the app's squircle, and renders the action as a pill button
 * that morphs on press with a click haptic. The dismiss icon is shown only when the
 * visuals ask for it, exactly like the stock snackbar.
 *
 * @param snackbarData The data from the host.
 * @param modifier Modifier for the snackbar.
 */
@Composable
fun ExpressiveSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    val visuals = snackbarData.visuals
    val actionLabel = visuals.actionLabel

    val action: (@Composable () -> Unit)? = actionLabel?.let { label ->
        {
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            TextButton(
                onClick = {
                    haptics.perform(HapticPattern.CLICK)
                    snackbarData.performAction()
                },
                shape = ShapeMorphing.buttonShape(
                    isPressed = pressed,
                    defaultRadius = 20.dp,
                    pressedRadius = 12.dp
                ),
                interactionSource = interactionSource,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.inversePrimary
                )
            ) {
                Text(label)
            }
        }
    }

    val dismissAction: (@Composable () -> Unit)? = if (visuals.withDismissAction) {
        {
            IconButton(onClick = { snackbarData.dismiss() }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    } else {
        null
    }

    Snackbar(
        modifier = modifier.padding(12.dp),
        action = action,
        dismissAction = dismissAction,
        shape = SnackbarShape,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionContentColor = MaterialTheme.colorScheme.inversePrimary,
        dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        Text(visuals.message)
    }
}
