package com.materialchat.ui.screens.arena.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.materialchat.domain.usecase.ArenaVerdict
import com.materialchat.ui.screens.arena.ContenderUi

/**
 * Blind voting bar for N contenders.
 *
 * Shows one "crown" button per codename card plus shared Tie / Both Bad
 * actions. Codenames only — the winner's real identity appears after voting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArenaVotingBar(
    contenders: List<ContenderUi>,
    voted: Boolean,
    onVote: (ArenaVerdict) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (voted) "Vote recorded" else "Which response wins?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            contenders.forEach { contender ->
                Button(
                    onClick = { onVote(ArenaVerdict.Win(contender.slot)) },
                    enabled = !voted && contender.isFinished,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text("👑 ${contender.codename}")
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onVote(ArenaVerdict.Tie) },
                enabled = !voted
            ) { Text("It's a tie") }

            OutlinedButton(
                onClick = { onVote(ArenaVerdict.BothBad) },
                enabled = !voted
            ) { Text("All bad") }
        }
    }
}
