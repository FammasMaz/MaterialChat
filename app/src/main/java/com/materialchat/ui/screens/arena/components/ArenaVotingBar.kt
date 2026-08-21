package com.materialchat.ui.screens.arena.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.ArenaVote
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Voting bar with 4 buttons: Left Wins, Right Wins, Tie, Both Bad.
 *
 * Appears after both models complete their responses. M3 Expressive press
 * feedback: bouncy scale + corner shape morph, and a CONFIRM haptic when a
 * vote is cast — voting is a deliberate hero action, not a silent click.
 *
 * @param onVote Callback when a vote is cast
 * @param enabled Whether voting is allowed
 * @param voted Whether the user has already voted
 */
@Composable
fun ArenaVotingBar(
    onVote: (ArenaVote) -> Unit,
    enabled: Boolean = true,
    voted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VoteButton(
            label = "A Wins",
            icon = { Icon(Icons.Filled.ThumbUp, contentDescription = "Model A wins") },
            onClick = {
                haptics.perform(HapticPattern.CONFIRM)
                onVote(ArenaVote.LEFT)
            },
            enabled = enabled && !voted,
            container = VoteColor.LEFT,
            modifier = Modifier.weight(1f)
        )

        VoteButton(
            label = "B Wins",
            icon = { Icon(Icons.Filled.ThumbUp, contentDescription = "Model B wins") },
            onClick = {
                haptics.perform(HapticPattern.CONFIRM)
                onVote(ArenaVote.RIGHT)
            },
            enabled = enabled && !voted,
            container = VoteColor.RIGHT,
            modifier = Modifier.weight(1f)
        )

        VoteButton(
            label = "Tie",
            icon = null,
            onClick = {
                haptics.perform(HapticPattern.CONFIRM)
                onVote(ArenaVote.TIE)
            },
            enabled = enabled && !voted,
            container = VoteColor.TIE,
            modifier = Modifier.weight(1f)
        )

        VoteButton(
            label = "Both Bad",
            icon = { Icon(Icons.Filled.ThumbDown, contentDescription = "Both bad") },
            onClick = {
                haptics.perform(HapticPattern.REJECT)
                onVote(ArenaVote.BOTH_BAD)
            },
            enabled = enabled && !voted,
            container = VoteColor.BAD,
            modifier = Modifier.weight(1f)
        )
    }
}

private enum class VoteColor { LEFT, RIGHT, TIE, BAD }

@Composable
private fun VoteButton(
    label: String,
    icon: @Composable (() -> Unit)?,
    onClick: () -> Unit,
    enabled: Boolean,
    container: VoteColor,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive SPATIAL spring: bouncy scale feedback on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.93f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "voteScale"
    )

    // Shape morph: rounded pills compress their corners while pressed
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 14.dp else 24.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "voteCorner"
    )

    val containerColor = when (container) {
        VoteColor.LEFT -> MaterialTheme.colorScheme.primaryContainer
        VoteColor.RIGHT -> MaterialTheme.colorScheme.tertiaryContainer
        VoteColor.TIE -> MaterialTheme.colorScheme.secondaryContainer
        VoteColor.BAD -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (container) {
        VoteColor.LEFT -> MaterialTheme.colorScheme.onPrimaryContainer
        VoteColor.RIGHT -> MaterialTheme.colorScheme.onTertiaryContainer
        VoteColor.TIE -> MaterialTheme.colorScheme.onSecondaryContainer
        VoteColor.BAD -> MaterialTheme.colorScheme.onErrorContainer
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = animateColorAsState(
                targetValue = containerColor,
                animationSpec = ExpressiveMotion.Effects.color(),
                label = "voteContainer"
            ).value,
            contentColor = contentColor
        ),
        interactionSource = interactionSource,
        modifier = modifier.height(48.dp).scale(scale)
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
