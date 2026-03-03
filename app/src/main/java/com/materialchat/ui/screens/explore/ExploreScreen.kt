package com.materialchat.ui.screens.explore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Data class representing a feature card in the Explore hub.
 */
private data class ExploreFeature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Explore hub screen - central navigation point for feature discovery.
 *
 * Displays M3 ElevatedCards in a responsive 2-column grid for:
 * - Arena (model comparison battles)
 * - Insights (conversation analytics)
 * - Bookmarks (saved messages)
 * - Workflows (prompt chains)
 * - Personas (custom AI characters)
 *
 * Each card uses spring-physics press animations following M3 Expressive guidelines:
 * - Spatial spring (stiffness=500, dampingRatio=0.7) for scale
 * - Effects spring (stiffness=500, dampingRatio=1.0) for elevation
 *
 * @param onNavigateToArena Navigate to Arena model battles
 * @param onNavigateToInsights Navigate to Insights dashboard
 * @param onNavigateToBookmarks Navigate to Bookmarks knowledge base
 * @param onNavigateToWorkflows Navigate to Workflows prompt chains
 * @param onNavigateToPersonas Navigate to Persona studio
 * @param modifier Optional modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onNavigateToArena: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToWorkflows: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    modifier: Modifier = Modifier
) {
    val features = remember(
        onNavigateToArena, onNavigateToInsights,
        onNavigateToBookmarks, onNavigateToWorkflows, onNavigateToPersonas
    ) {
        listOf(
            ExploreFeature(
                title = "Arena",
                description = "Compare models head-to-head",
                icon = Icons.Filled.EmojiEvents,
                onClick = onNavigateToArena
            ),
            ExploreFeature(
                title = "Insights",
                description = "Conversation analytics",
                icon = Icons.Filled.Insights,
                onClick = onNavigateToInsights
            ),
            ExploreFeature(
                title = "Bookmarks",
                description = "Saved messages & knowledge",
                icon = Icons.Filled.Bookmark,
                onClick = onNavigateToBookmarks
            ),
            ExploreFeature(
                title = "Workflows",
                description = "Prompt chain automation",
                icon = Icons.Filled.AccountTree,
                onClick = onNavigateToWorkflows
            ),
            ExploreFeature(
                title = "Personas",
                description = "Custom AI characters",
                icon = Icons.Filled.Face,
                onClick = onNavigateToPersonas
            )
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Explore",
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(features, key = { it.title }) { feature ->
                ExploreFeatureCard(feature = feature)
            }
        }
    }
}

/**
 * Individual feature card with M3 Expressive spring-animated press feedback.
 * Uses ElevatedCard with 48dp minimum touch target compliance.
 */
@Composable
private fun ExploreFeatureCard(
    feature: ExploreFeature,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive: Spatial spring for scale (can bounce)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            stiffness = 500f,
            dampingRatio = 0.7f  // Spatial - subtle bounce on release
        ),
        label = "cardScale_${feature.title}"
    )

    // M3 Expressive: Effects spring for elevation (no bounce)
    val elevationDp by animateFloatAsState(
        targetValue = if (isPressed) 1f else 4f,
        animationSpec = spring(
            stiffness = 500f,
            dampingRatio = 1.0f  // Effects - smooth
        ),
        label = "cardElevation_${feature.title}"
    )

    ElevatedCard(
        onClick = feature.onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = elevationDp.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.title,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
