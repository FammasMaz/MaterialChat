package com.materialchat.ui.screens.chat.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.materialchat.domain.model.WebSearchMetadata

/**
 * Horizontal carousel of source cards displayed at the bottom of an assistant message
 * that used web search. Each card shows the source's image/favicon, citation number,
 * domain, and title.
 */
@Composable
fun WebSearchSourcesCarousel(
    metadata: WebSearchMetadata,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header: globe icon + "Sources (N)" + search duration
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .alpha(0.7f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Sources (${metadata.results.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            metadata.searchDurationMs?.let { ms ->
                Text(
                    text = "· ${formatSearchDuration(ms)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scrollable row of source cards
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(metadata.results) { result ->
                WebSearchSourceCard(
                    index = result.index,
                    title = result.title,
                    domain = result.domain ?: result.url,
                    imageUrl = result.imageUrl ?: result.faviconUrl,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
                        context.startActivity(intent)
                    }
                )
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
            .width(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column {
            // Top image area
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            }

            // Bottom text area
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Citation badge + domain
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    Text(
                        text = domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
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
