package com.materialchat.ui.screens.conversations.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * The trailing date on a conversation row.
 *
 * Measured at its intrinsic width and never wrapped: the row's model-name text is the
 * weighted sibling that gives way, so this label is laid out on exactly one line with
 * no ellipsis. The label itself is kept short by [com.materialchat.ui.screens.conversations.RelativeDateLabel].
 */
@Composable
internal fun RowDateLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier
    )
}
