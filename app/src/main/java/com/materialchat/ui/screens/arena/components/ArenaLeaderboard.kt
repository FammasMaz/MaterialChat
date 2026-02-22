package com.materialchat.ui.screens.arena.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.materialchat.domain.model.ModelRating

/**
 * Leaderboard screen showing all model ELO ratings ranked.
 *
 * Displays a list of models with their ELO ratings, win/loss/tie stats,
 * and ranking position. Uses M3 Expressive design with spring animations.
 *
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaLeaderboard(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArenaLeaderboardViewModel = hiltViewModel()
) {
    val ratings by viewModel.ratings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Arena Leaderboard")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (ratings.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .height(64.dp)
                        .width(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No battles yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Run some arena battles to see rankings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = ratings,
                    key = { _, rating -> rating.modelName }
                ) { index, rating ->
                    LeaderboardItem(
                        rank = index + 1,
                        rating = rating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.7f,
                                    stiffness = 300f
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardItem(
    rank: Int,
    rating: ModelRating,
    modifier: Modifier = Modifier
) {
    val containerColor = when (rank) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (rank) {
        1 -> MaterialTheme.colorScheme.onPrimaryContainer
        2 -> MaterialTheme.colorScheme.onSecondaryContainer
        3 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = contentColor.copy(alpha = 0.15f),
                modifier = Modifier
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Model name and stats
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rating.modelName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatLabel(label = "W", value = "${rating.wins}")
                    StatLabel(label = "L", value = "${rating.losses}")
                    StatLabel(label = "T", value = "${rating.ties}")
                    StatLabel(label = "Total", value = "${rating.totalBattles}")
                }

                if (rating.totalBattles > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (rating.winRate / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = contentColor.copy(alpha = 0.6f),
                        trackColor = contentColor.copy(alpha = 0.12f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ELO rating
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${rating.eloRating.toInt()}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ELO",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StatLabel(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
