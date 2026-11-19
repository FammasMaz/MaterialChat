package com.materialchat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveSpacing

/**
 * Horizontal gutter every modal bottom sheet uses (chat sheets and the settings
 * "Add provider" sheet), so sheet content lines up across screens. Alias of the theme token
 * [ExpressiveSpacing.SheetGutter]; new call sites should read the token directly.
 */
val SheetGutter: Dp = ExpressiveSpacing.SheetGutter

/**
 * Header row of a modal bottom sheet: headline title with an optional close affordance on
 * the trailing edge. One composable for every sheet in the app so sheets read as a single
 * family regardless of which screen opened them.
 *
 * Pass `onClose = null` for a sheet that only dismisses through its drag handle or scrim.
 */
@Composable
fun SheetHeader(
    title: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (onClose != null) {
            IconButton(onClick = { haptics.perform(HapticPattern.CLICK); onClose() }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
