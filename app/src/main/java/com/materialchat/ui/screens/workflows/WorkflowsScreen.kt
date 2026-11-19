package com.materialchat.ui.screens.workflows

import com.materialchat.ui.components.ExpressiveEmptyState
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.ExpressiveMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.materialchat.ui.components.ExpressiveFab
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.components.ExpressiveContentSurface
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.components.ExpressiveTopBarTitle
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.screens.workflows.components.WorkflowCard

/**
 * Workflows list screen.
 *
 * Displays all workflows in a LazyColumn with swipe-to-delete support.
 * Includes a FAB for creating new workflows and an empty state when
 * no workflows exist. Follows Material 3 Expressive design patterns.
 *
 * @param onNavigateBack Callback to navigate back
 * @param onNavigateToBuilder Callback to navigate to the workflow builder (null for new, ID for edit)
 * @param onNavigateToExecution Callback to navigate to workflow execution with a workflow ID
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBuilder: (String?) -> Unit,
    onNavigateToExecution: (String) -> Unit,
    viewModel: WorkflowsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHapticFeedback()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    ExpressiveTopBarTitle(
                        title = "Workflows",
                        subtitle = "Reusable prompt chains"
                    )
                },
                navigationIcon = {
                    ExpressiveFilledIconButton(
                        onClick = { haptics.perform(HapticPattern.CLICK); onNavigateBack() },
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            ExpressiveFab(
                onClick = { onNavigateToBuilder(null) },
                icon = Icons.Filled.Add,
                contentDescription = "Create",
                expanded = true,
                text = "New Workflow"
            )
        }
    ) { paddingValues ->
        ExpressiveContentSurface(
            padding = PaddingValues(top = paddingValues.calculateTopPadding())
        ) {
            when (val state = uiState) {
                is WorkflowsUiState.Loading -> {
                    WorkflowsLoadingContent()
                }

                is WorkflowsUiState.Success -> {
                    if (state.workflows.isEmpty()) {
                        WorkflowsEmptyContent(onCreate = { onNavigateToBuilder(null) })
                    } else {
                        WorkflowsListContent(
                            state = state,
                            onNavigateToExecution = onNavigateToExecution,
                            onNavigateToBuilder = onNavigateToBuilder,
                            onDeleteWorkflow = { viewModel.deleteWorkflow(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loading state content with centered progress indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkflowsLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Empty state content when no workflows exist.
 */
@Composable
private fun WorkflowsEmptyContent(onCreate: () -> Unit) {
    // Same empty-state family as the home list, with the FAB's action repeated inline.
    ExpressiveEmptyState(
        icon = Icons.Outlined.AccountTree,
        title = "No workflows yet",
        message = "Create multi-step prompt chains to automate complex tasks",
        actionText = "New workflow",
        onAction = onCreate
    )
}

/**
 * Main list content displaying workflow cards with swipe-to-delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkflowsListContent(
    state: WorkflowsUiState.Success,
    onNavigateToExecution: (String) -> Unit,
    onNavigateToBuilder: (String?) -> Unit,
    onDeleteWorkflow: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 88.dp // Room for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = state.workflows,
            key = { it.id }
        ) { workflow ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                        onDeleteWorkflow(workflow.id)
                        true
                    } else {
                        false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    // Swipe-to-delete background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CustomShapes.ConversationItem
                            )
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete workflow",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                },
                enableDismissFromStartToEnd = false,
                modifier = Modifier.animateItem(
                    fadeInSpec = ExpressiveMotion.ListItem.FadeIn,
                    fadeOutSpec = ExpressiveMotion.ListItem.FadeOut,
                    placementSpec = ExpressiveMotion.ListItem.Placement
                )
            ) {
                WorkflowCard(
                    workflow = workflow,
                    onClick = { onNavigateToExecution(workflow.id) },
                    onLongClick = { onNavigateToBuilder(workflow.id) }
                )
            }
        }
    }
}
