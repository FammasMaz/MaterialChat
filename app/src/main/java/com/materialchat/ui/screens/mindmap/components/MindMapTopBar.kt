package com.materialchat.ui.screens.mindmap.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.components.ExpressiveTopBarTitle
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback

/**
 * Top app bar for the Mind Map screen.
 *
 * Center-aligned title with a back navigation button.
 * Uses M3 Expressive surface container color for visual hierarchy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindMapTopBar(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticFeedback()
    CenterAlignedTopAppBar(
        title = {
            ExpressiveTopBarTitle(
                title = "Mind Map",
                subtitle = "Conversation branches"
            )
        },
        navigationIcon = {
            ExpressiveFilledIconButton(
                onClick = { haptics.perform(HapticPattern.CLICK); onNavigateBack() },
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate back"
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
    )
}
