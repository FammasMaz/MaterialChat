package com.materialchat.ui.screens.memories

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.domain.model.Memory
import com.materialchat.domain.model.MemoryKind
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import com.materialchat.ui.components.ExpressiveContentSurface
import com.materialchat.ui.components.ExpressiveEmptyState
import com.materialchat.ui.screens.conversations.EmptyStateAccent
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.components.dialogActionButtonColors
import com.materialchat.ui.components.dialogDestructiveButtonColors
import com.materialchat.ui.components.ExpressiveSnackbar
import com.materialchat.ui.components.ExpressiveTopBarTitle
import com.materialchat.ui.components.HomeSettingsSlot
import com.materialchat.ui.components.LoadErrorContent
import com.materialchat.ui.components.dialogEntrance
import com.materialchat.ui.components.rememberHomeSettingsPalette
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.ExpressiveSizes
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import java.text.DateFormat
import java.util.Date
import com.materialchat.ui.theme.CustomShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemoriesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHapticFeedback()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Delete failures do not change the list state, so they surface here.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MemoriesEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                ExpressiveSnackbar(snackbarData = snackbarData)
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    ExpressiveTopBarTitle(
                        title = "Memories",
                        subtitle = "Context your assistant remembers"
                    )
                },
                navigationIcon = {
                    ExpressiveFilledIconButton(
                        onClick = { haptics.perform(HapticPattern.CLICK); onNavigateBack() },
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                },
                actions = {
                    if (state.memories.isNotEmpty()) {
                        ExpressiveFilledIconButton(
                            onClick = { haptics.perform(HapticPattern.CLICK); showDeleteAllDialog = true },
                            icon = Icons.Outlined.Delete,
                            contentDescription = "Delete all memories",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        ExpressiveContentSurface(
            padding = PaddingValues(top = paddingValues.calculateTopPadding())
        ) {
            when {
                state.isLoading -> MemoriesLoadingContent()
                state.error != null -> LoadErrorContent(
                    title = "Memories could not be loaded",
                    message = "Something went wrong while reading what your assistant remembers",
                    onRetry = viewModel::retry
                )
                state.memories.isEmpty() -> EmptyMemoriesContent()
                else -> MemoriesListContent(
                    state = state,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onDeleteMemory = viewModel::deleteMemory
                )
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            modifier = Modifier.dialogEntrance(),
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete all memories?") },
            text = { Text("This removes every saved memory from this device. Chat history stays intact.") },
            confirmButton = {
                TextButton(
                    colors = dialogDestructiveButtonColors(),
                    onClick = {
                        haptics.perform(HapticPattern.CONFIRM)
                        viewModel.deleteAllMemories()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }, colors = dialogActionButtonColors()) {
                    Text("Cancel")
                }
            },
            shape = ExpressiveShapes.Dialog,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MemoriesLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(ExpressiveSizes.IndicatorHero),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MemoriesListContent(
    state: MemoriesUiState,
    onSearchQueryChange: (String) -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize(
                animationSpec = ExpressiveMotion.Spatial.contentSize()
            )
    ) {
        MemorySearchField(
            query = state.searchQuery,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (state.filteredMemories.isEmpty()) {
            EmptyFilteredMemoriesContent()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = state.filteredMemories,
                    key = { it.id }
                ) { memory ->
                    MemoryCard(
                        memory = memory,
                        onDelete = { onDeleteMemory(memory.id) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = ExpressiveMotion.ListItem.FadeIn,
                            fadeOutSpec = ExpressiveMotion.ListItem.FadeOut,
                            placementSpec = ExpressiveMotion.ListItem.Placement
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MemorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search memories") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = CustomShapes.SearchBar,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

@Composable
private fun MemoryCard(
    memory: Memory,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = memoryColors(memory.kind)
    val date = remember(memory.updatedAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(memory.updatedAt))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.Card,
        color = colors.container,
        contentColor = colors.content,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(ExpressiveSizes.Tile),
                shape = ExpressiveShapes.Tile,
                // 12% of the kind's content colour over its container; the icon on it is gated as
                // HomeSettingsSlot.MemoryTileIcon* (W7 #6).
                color = colors.content.copy(alpha = 0.12f),
                contentColor = memoryTileIconColor(memory.kind)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(ExpressiveSizes.IconLarge)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = memory.kind.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.content
                )
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.content
                )
                Text(
                    text = "Updated $date · recalled ${memory.recallCount}×",
                    style = MaterialTheme.typography.labelSmall,
                    // 66% dropped to 3.7-4.2:1 on every dark palette; resolved against the card.
                    color = colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete memory",
                    tint = colors.content
                )
            }
        }
    }
}

/** Same empty-state family as the home list (shape cluster, headline, one supporting line). */
@Composable
private fun EmptyMemoriesContent() {
    ExpressiveEmptyState(
        icon = Icons.Outlined.AutoAwesome,
        title = "No memories yet",
        message = "Say “remember that…” in a chat and MaterialChat keeps durable preferences and facts here",
        accent = EmptyStateAccent.Primary
    )
}

@Composable
private fun EmptyFilteredMemoriesContent() {
    ExpressiveEmptyState(
        icon = Icons.Outlined.Search,
        title = "No matching memories",
        message = "Try another search term",
        accent = EmptyStateAccent.Secondary
    )
}

@Composable
private fun memoryColors(kind: MemoryKind): MemoryColors {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        MemoryKind.USER_PREFERENCE -> MemoryColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        MemoryKind.INSTRUCTION -> MemoryColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        MemoryKind.PROJECT_FACT -> MemoryColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        else -> MemoryColors(scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
    }
}

/** The icon tint on the memory tile wash, resolved through the home/settings contrast slots. */
@Composable
private fun memoryTileIconColor(kind: MemoryKind): androidx.compose.ui.graphics.Color =
    rememberHomeSettingsPalette()[
        when (kind) {
            MemoryKind.USER_PREFERENCE -> HomeSettingsSlot.MemoryTileIconPreference
            MemoryKind.INSTRUCTION -> HomeSettingsSlot.MemoryTileIconInstruction
            MemoryKind.PROJECT_FACT -> HomeSettingsSlot.MemoryTileIconFact
            else -> HomeSettingsSlot.MemoryTileIconOther
        }
    ]

private data class MemoryColors(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color
)
