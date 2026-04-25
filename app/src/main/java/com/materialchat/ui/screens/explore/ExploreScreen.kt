package com.materialchat.ui.screens.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NorthEast
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
import kotlinx.coroutines.delay

private data class ExploreFeature(
    val title: String,
    val eyebrow: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val tone: ExploreTone,
    val isWide: Boolean = false
)

private enum class ExploreTone { Primary, Secondary, Tertiary, Neutral }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onNavigateToArena: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToGeneratedImages: () -> Unit,
    onNavigateToWorkflows: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    modifier: Modifier = Modifier
) {
    val features = remember(
        onNavigateToInsights,
        onNavigateToBookmarks,
        onNavigateToGeneratedImages,
        onNavigateToWorkflows,
        onNavigateToPersonas
    ) {
        listOf(
            ExploreFeature(
                title = "Generated Images",
                eyebrow = "Library",
                description = "Browse every image, save it, share it, or return to the source thread.",
                icon = Icons.Filled.Image,
                onClick = onNavigateToGeneratedImages,
                tone = ExploreTone.Primary,
                isWide = true
            ),
            ExploreFeature(
                title = "Insights",
                eyebrow = "Signals",
                description = "Usage patterns, model speed, and conversation intelligence.",
                icon = Icons.Filled.Insights,
                onClick = onNavigateToInsights,
                tone = ExploreTone.Secondary
            ),
            ExploreFeature(
                title = "Bookmarks",
                eyebrow = "Knowledge",
                description = "Saved answers and reusable fragments.",
                icon = Icons.Filled.Bookmark,
                onClick = onNavigateToBookmarks,
                tone = ExploreTone.Tertiary
            ),
            ExploreFeature(
                title = "Workflows",
                eyebrow = "Automation",
                description = "Run prompt chains for repeatable tasks.",
                icon = Icons.Filled.AccountTree,
                onClick = onNavigateToWorkflows,
                tone = ExploreTone.Neutral
            ),
            ExploreFeature(
                title = "Personas",
                eyebrow = "Style",
                description = "Shape how assistants answer across chats.",
                icon = Icons.Filled.Face,
                onClick = onNavigateToPersonas,
                tone = ExploreTone.Secondary
            )
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Explore",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tools, memory, and creative output",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ArenaHeroCard(onClick = onNavigateToArena)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Create and organize",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                itemsIndexed(
                    items = features,
                    key = { _, feature -> feature.title },
                    span = { _, feature -> if (feature.isWide) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
                ) { index, feature ->
                    StaggeredFeatureCard(index = index, feature = feature)
                }
            }
        }
    }
}

@Composable
private fun ArenaHeroCard(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "arenaHeroScale"
    )

    ElevatedCard(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(30.dp)
                    )
                }
                Text(
                    text = "Arena",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Compare models head-to-head and vote on the better answer.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.NorthEast,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StaggeredFeatureCard(index: Int, feature: ExploreFeature) {
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(18f) }

    LaunchedEffect(Unit) {
        delay(index * 45L)
        alpha.animateTo(1f, animationSpec = ExpressiveMotion.Effects.alpha())
    }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        offset.animateTo(0f, animationSpec = spring(stiffness = 420f, dampingRatio = 0.8f))
    }

    AnimatedVisibility(
        visible = alpha.value > 0f,
        enter = fadeIn() + slideInVertically { it / 8 }
    ) {
        ExploreFeatureCard(
            feature = feature,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha.value
                translationY = offset.value
            }
        )
    }
}

@Composable
private fun ExploreFeatureCard(feature: ExploreFeature, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "featureScale_${feature.title}"
    )
    val colors = feature.colors()

    ElevatedCard(
        onClick = feature.onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(if (feature.isWide) 28.dp else 24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.first),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (feature.isWide) 3.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(if (feature.isWide) 144.dp else 156.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (feature.isWide) 18.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.second.copy(alpha = 0.18f),
                    contentColor = colors.second
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(if (feature.isWide) 26.dp else 22.dp)
                    )
                }
                Text(
                    text = feature.eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.second.copy(alpha = 0.78f),
                    maxLines = 1
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = feature.title,
                    style = if (feature.isWide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.second,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.second.copy(alpha = 0.72f),
                    maxLines = if (feature.isWide) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExploreFeature.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        ExploreTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        ExploreTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ExploreTone.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ExploreTone.Neutral -> scheme.surfaceContainerHighest to scheme.onSurface
    }
}
