package com.materialchat.ui.screens.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Data class representing a feature card in the Explore hub.
 */
private data class ExploreFeature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isHero: Boolean = false
)

/**
 * Explore hub screen with M3 Expressive design.
 *
 * Features visual hierarchy with a full-width hero card (Arena),
 * color variety per card, staggered entry animations, and shape variety.
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
                onClick = onNavigateToArena,
                isHero = true
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
                description = "Create & apply AI characters to chats",
                icon = Icons.Filled.Face,
                onClick = onNavigateToPersonas
            )
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Explore",
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        // M3 Expressive: Rounded content surface matching other screens
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 88.dp // Clearance for floating toolbar
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = features,
                    key = { _, feature -> feature.title },
                    span = { _, feature ->
                        if (feature.isHero) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                    }
                ) { index, feature ->
                    StaggeredEntryCard(
                        index = index,
                        feature = feature
                    )
                }
            }
        }
    }
}

/**
 * Wrapper that applies staggered entry animation (fade + scale) to each card.
 */
@Composable
private fun StaggeredEntryCard(
    index: Int,
    feature: ExploreFeature
) {
    val entryAlpha = remember { Animatable(0f) }
    val entryScale = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        delay(index * 60L) // Stagger by 60ms per card
        entryAlpha.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = 500f, dampingRatio = 1.0f)
        )
    }

    LaunchedEffect(Unit) {
        delay(index * 60L)
        entryScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f)
        )
    }

    ExploreFeatureCard(
        feature = feature,
        modifier = Modifier.graphicsLayer {
            alpha = entryAlpha.value
            scaleX = entryScale.value
            scaleY = entryScale.value
        }
    )
}

/**
 * Returns the container color for a given card title.
 * Provides M3 Expressive color variety across the grid.
 */
@Composable
private fun containerColorFor(title: String): Color {
    return when (title) {
        "Arena" -> MaterialTheme.colorScheme.primaryContainer
        "Insights" -> MaterialTheme.colorScheme.secondaryContainer
        "Bookmarks" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
}

/**
 * Returns the content color for a given card title.
 */
@Composable
private fun contentColorFor(title: String): Color {
    return when (title) {
        "Arena" -> MaterialTheme.colorScheme.onPrimaryContainer
        "Insights" -> MaterialTheme.colorScheme.onSecondaryContainer
        "Bookmarks" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/**
 * Individual feature card with M3 Expressive styling.
 *
 * Hero cards (Arena) span full width with larger height, larger typography,
 * and 28dp corners. Standard cards use 20dp corners and centered layout.
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
            dampingRatio = 0.7f
        ),
        label = "cardScale_${feature.title}"
    )

    // M3 Expressive: Effects spring for elevation (no bounce)
    val elevationDp by animateFloatAsState(
        targetValue = if (isPressed) 1f else 4f,
        animationSpec = spring(
            stiffness = 500f,
            dampingRatio = 1.0f
        ),
        label = "cardElevation_${feature.title}"
    )

    val containerColor = containerColorFor(feature.title)
    val contentColor = contentColorFor(feature.title)
    val cardHeight: Dp = if (feature.isHero) 160.dp else 140.dp
    val cornerRadius: Dp = if (feature.isHero) 28.dp else 20.dp

    ElevatedCard(
        onClick = feature.onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = elevationDp.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        interactionSource = interactionSource
    ) {
        if (feature.isHero) {
            // Hero card: left-aligned content with larger typography
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    modifier = Modifier.size(40.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        } else {
            // Standard card: centered content
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
                    tint = contentColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
