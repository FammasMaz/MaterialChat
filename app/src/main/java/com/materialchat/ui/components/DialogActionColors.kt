package com.materialchat.ui.components

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.materialchat.ui.theme.dialogActionColor
import com.materialchat.ui.theme.dialogDestructiveColor

/**
 * `TextButton` colours for an `AlertDialog` action on the M3 dialog container
 * (`surfaceContainerHigh`): the theme-resolved [dialogActionColor] (raw `primary` measures
 * 4.07:1 on VIOLET light and 4.13:1 on CITRUS light, under 4.5:1 for a labelLarge button).
 * Every dialog host on home, settings and the drawer screens draws this one value.
 */
@Composable
fun dialogActionButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.dialogActionColor())

/** `TextButton` colours for a destructive dialog action: the theme-resolved [dialogDestructiveColor]. */
@Composable
fun dialogDestructiveButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.dialogDestructiveColor())
