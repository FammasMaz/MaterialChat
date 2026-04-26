package com.materialchat.ui.screens.chat.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.materialchat.domain.model.WebSearchMetadata

/**
 * Collapsible Material 3 carousel of source cards for assistant messages that used web search.
 *
 * The header starts collapsed so citations stay out of the way while still making it clear that
 * the answer is grounded. Expanding reveals an M3 carousel whose items resize/mask as they scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchSourcesCarousel(
    metadata: WebSearchMetadata,
    modifier: Modifier = Modifier,
    messageId: String? = null,
    initiallyExpanded: Boolean = false
) {
    if (metadata.results.isEmpty()) return

    val context = LocalContext.current
    var expanded by rememberSaveable(
        messageId,
        metadata.query,
        metadata.provider,
        metadata.results.size
    ) { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "webSearchSourcesChevron"
    )
    val containerShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(containerShape)
                .clickable { expanded = !expanded },
            shape = containerShape,
            color = if (expanded) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (expanded) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(7.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Sources (${metadata.results.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        metadata.searchDurationMs?.let { ms ->
                            Text(
                                text = "· ${formatSearchDuration(ms)}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.alpha(0.68f),
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = if (expanded) "Swipe source cards" else "Tap to inspect web results",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.alpha(0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide web search sources" else "Show web search sources",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
            ) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
            ) + shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                val carouselState = rememberCarouselState { metadata.results.size }
                HorizontalMultiBrowseCarousel(
                    state = carouselState,
                    preferredItemWidth = 220.dp,
                    itemSpacing = 8.dp,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) { itemIndex ->
                    val result = metadata.results[itemIndex]
                    WebSearchSourceCard(
                        index = result.index,
                        title = result.title,
                        domain = result.domain ?: result.url,
                        imageUrl = result.imageUrl ?: result.faviconUrl,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.maskClip(RoundedCornerShape(20.dp))
                    )
                }
            }
        }
    }
}

/**
 * Individual source card within the carousel.
 */
@Composable
private fun WebSearchSourceCard(
    index: Int,
    title: String,
    domain: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (imageUrl != null) 2 else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatSearchDuration(ms: Long): String {
    val seconds = ms / 1000.0
    return if (seconds < 1.0) {
        "${ms}ms"
    } else {
        String.format("%.1fs", seconds)
    }
}
