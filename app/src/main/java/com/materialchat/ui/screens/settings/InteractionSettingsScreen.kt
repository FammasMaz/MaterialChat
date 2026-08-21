package com.materialchat.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.components.ExpressiveSwitch
import com.materialchat.ui.components.ExpressiveTopBarTitle
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: InteractionSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    ExpressiveTopBarTitle(
                        title = "Interaction & Motion",
                        subtitle = "Haptics, shape, and expressive button feel"
                    )
                },
                navigationIcon = {
                    ExpressiveFilledIconButton(
                        onClick = onNavigateBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 16.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsMotionCard(
                    title = "Haptic feedback",
                    description = "Choose which interaction families are allowed to vibrate.",
                    icon = Icons.Filled.GraphicEq
                ) {
                    HapticToggleRow("Master haptics", uiState.globalHaptics, viewModel::updateGlobalHaptics)
                    HapticToggleRow("Chat controls", uiState.chatHaptics, viewModel::updateChatHaptics)
                    HapticToggleRow("Navigation", uiState.navigationHaptics, viewModel::updateNavigationHaptics)
                    HapticToggleRow("Lists and scrolling", uiState.listHaptics, viewModel::updateListHaptics)
                    HapticToggleRow("Gestures and swipes", uiState.gestureHaptics, viewModel::updateGestureHaptics)
                }
            }

            item {
                SettingsMotionCard(
                    title = "Button shapes",
                    description = "Override the expressive shape token used by main and chat page buttons.",
                    icon = Icons.Filled.SmartButton
                ) {
                    ShapeSelector(
                        title = "Main page buttons",
                        selectedShape = uiState.mainButtonShape,
                        onShapeSelected = viewModel::updateMainButtonShape
                    )
                    ShapeSelector(
                        title = "Chat page buttons",
                        selectedShape = uiState.chatButtonShape,
                        onShapeSelected = viewModel::updateChatButtonShape
                    )
                }
            }

            item {
                SettingsMotionCard(
                    title = "Motion mechanics",
                    description = "Feel each haptic pattern before you commit to it.",
                    icon = Icons.Filled.Gesture
                ) {
                    HapticPatternDemoRow()
                    Text(
                        text = "Chat image actions softly push nearby controls instead of moving alone, and expressive controls share one spring language across the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMotionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun HapticToggleRow(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        ExpressiveSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

/**
 * Live demo of the haptic vocabulary: tap a chip to feel the exact pattern
 * that interaction family produces. A settings screen about touch should be
 * tangible, not just descriptive.
 */
@Composable
private fun HapticPatternDemoRow() {
    val haptics = rememberHapticFeedback()
    val demos = listOf(
        "Click" to HapticPattern.CLICK,
        "Confirm" to HapticPattern.CONFIRM,
        "Reject" to HapticPattern.REJECT,
        "Emphasis" to HapticPattern.EMPHASIS,
        "Toggle" to HapticPattern.TOGGLE
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        demos.forEach { (label, pattern) ->
            FilterChip(
                selected = false,
                onClick = { haptics.perform(pattern) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeSelector(
    title: String,
    selectedShape: AppPreferences.ComponentButtonShape,
    onShapeSelected: (AppPreferences.ComponentButtonShape) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val haptics = rememberHapticFeedback()
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppPreferences.ComponentButtonShape.entries.forEach { shape ->
                FilterChip(
                    selected = selectedShape == shape,
                    onClick = {
                        haptics.perform(HapticPattern.SEGMENT_TICK)
                        onShapeSelected(shape)
                    },
                    label = { Text(shape.prettyName()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

private fun AppPreferences.ComponentButtonShape.prettyName(): String {
    return when (this) {
        AppPreferences.ComponentButtonShape.SYSTEM -> "System"
        AppPreferences.ComponentButtonShape.COOKIE -> "Cookie"
        AppPreferences.ComponentButtonShape.COOKIE_SOFT -> "Cookie soft"
        AppPreferences.ComponentButtonShape.CLOVER -> "Clover"
        AppPreferences.ComponentButtonShape.FLOWER -> "Flower"
        AppPreferences.ComponentButtonShape.PUFFY -> "Puffy"
        AppPreferences.ComponentButtonShape.SOFT_BURST -> "Soft burst"
    }
}
