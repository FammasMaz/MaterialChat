package com.materialchat.ui.screens.arena.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.ArenaVote
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Voting bar with 4 buttons: Left Wins, Right Wins, Tie, Both Bad.
 *
 * Appears after both models complete their responses. Uses M3 Expressive
 * spring animations for press feedback.
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VoteButton(
            label = "A Wins",
            icon = { Icon(Icons.Filled.ThumbUp, contentDescription = "Model A wins") },
            onClick = { onVote(ArenaVote.LEFT) },
            enabled = enabled && !voted,
            containerColor = VoteColor.LEFT,
            modifier = Modifier.weight(1f)
        )

        VoteButton(
            label = "B Wins",
            icon = { Icon(Icons.Filled.ThumbUp, contentDescription = "Model B wins") },
            onClick = { onVote(ArenaVote.RIGHT) },
            enabled = enabled && !voted,
            containerColor = VoteColor.RIGHT,
            modifier = Modifier.weight(1f)
        )

        VoteButton(
            label = "Tie",
            icon = null,
            onClick = { onVote(ArenaVote.TIE) },
            enabled = enabled && !voted,
            containerColor = VoteColor.TIE,
            modifier = Modifier.weight(1f)
        )

        VoteButton(
            label = "Both Bad",
            icon = { Icon(Icons.Filled.ThumbDown, contentDescription = "Both bad") },
            onClick = { onVote(ArenaVote.BOTH_BAD) },
            enabled = enabled && !voted,
            containerColor = VoteColor.BAD,
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
    containerColor: VoteColor,
    modifier: Modifier = Modifier
) {
    val colors = when (containerColor) {
        VoteColor.LEFT -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        VoteColor.RIGHT -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        VoteColor.TIE -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        VoteColor.BAD -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = colors,
        modifier = modifier.height(48.dp)
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
