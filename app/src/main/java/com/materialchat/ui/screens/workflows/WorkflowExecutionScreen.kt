package com.materialchat.ui.screens.workflows

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.domain.model.WorkflowStatus
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.screens.workflows.components.StepProgressIndicator
import com.materialchat.ui.screens.workflows.components.StepResultCard
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Workflow Execution screen.
 *
 * Shows an initial input dialog, then displays step-by-step execution
 * progress including a step progress indicator, completed step results,
 * and streaming content for the current step.
 *
 * @param onNavigateBack Callback to navigate back
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowExecutionScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkflowExecutionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHapticFeedback()

    // Input dialog
    if (uiState.showInputDialog && uiState.workflow != null) {
        WorkflowInputDialog(
            workflowName = uiState.workflow!!.name,
            workflowDescription = uiState.workflow!!.description,
            onExecute = { viewModel.execute(it) },
            onDismiss = onNavigateBack
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.workflow?.name ?: "Workflow",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { haptics.perform(HapticPattern.CLICK); onNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    val execution = uiState.execution
                    if (execution != null && execution.status == WorkflowStatus.RUNNING) {
                        IconButton(onClick = { viewModel.cancel() }) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Cancel execution",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
        when {
            uiState.isLoading -> {
                ExecutionLoadingContent()
            }

            uiState.error != null && uiState.execution == null -> {
                ExecutionErrorContent(
                    message = uiState.error!!,
                    onNavigateBack = onNavigateBack
                )
            }

            uiState.execution != null -> {
                ExecutionContent(
                    uiState = uiState
                )
            }

            !uiState.showInputDialog && uiState.execution == null && !uiState.isLoading -> {
                ExecutionIdleContent()
            }
        }
        }
    }
}

/**
 * Dialog for entering the initial input to start workflow execution.
 */
@Composable
private fun WorkflowInputDialog(
    workflowName: String,
    workflowDescription: String,
    onExecute: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHapticFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = workflowName,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                if (workflowDescription.isNotBlank()) {
                    Text(
                        text = workflowDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Your input") },
                    placeholder = { Text("Enter your prompt to start the workflow...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        },
        confirmButton = {
            ExpressiveButton(
                onClick = { onExecute(inputText) },
                text = "Run",
                leadingIcon = Icons.Default.PlayArrow,
                style = ExpressiveButtonStyle.FilledTonal,
                enabled = inputText.isNotBlank()
            )
        },
        dismissButton = {
            ExpressiveButton(onClick = { onDismiss() }, text = "Cancel", style = ExpressiveButtonStyle.Text)
        }
    )
}

/**
 * Loading content while the workflow metadata is being fetched.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExecutionLoadingContent() {
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
 * Error content when workflow loading fails.
 */
@Composable
private fun ExecutionErrorContent(
    message: String,
    onNavigateBack: () -> Unit
) {
    val haptics = rememberHapticFeedback()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        ExpressiveButton(onClick = { onNavigateBack() }, text = "Go Back", style = ExpressiveButtonStyle.Text)
    }
}

/**
 * Idle content shown before execution starts.
 */
@Composable
private fun ExecutionIdleContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Ready to run workflow",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Main execution content showing step progress, completed results,
 * and streaming content for the current step.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExecutionContent(
    uiState: WorkflowExecutionUiState
) {
    val execution = uiState.execution ?: return
    val completedSteps = execution.stepResults.keys

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Step progress indicator
        item(key = "progress") {
            StepProgressIndicator(
                totalSteps = execution.totalSteps,
                currentStep = execution.currentStepIndex,
                completedSteps = completedSteps,
                modifier = Modifier.animateItem(
                    fadeInSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    fadeOutSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            )
        }

        // Status banner for terminal states
        when (execution.status) {
            WorkflowStatus.COMPLETED -> {
                item(key = "status_completed") {
                    Box(modifier = Modifier.animateItem(
                        fadeInSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        fadeOutSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )) {
                        StatusBanner(
                            text = "Workflow completed",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            WorkflowStatus.FAILED -> {
                item(key = "status_failed") {
                    Box(modifier = Modifier.animateItem(
                        fadeInSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        fadeOutSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )) {
                        StatusBanner(
                            text = "Failed: ${execution.error ?: "Unknown error"}",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            WorkflowStatus.CANCELLED -> {
                item(key = "status_cancelled") {
                    Box(modifier = Modifier.animateItem(
                        fadeInSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        fadeOutSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )) {
                        StatusBanner(
                            text = "Workflow cancelled",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> { /* IDLE, RUNNING — no banner */ }
        }

        // Completed step results
        val sortedStepResults = execution.stepResults.toSortedMap()
        sortedStepResults.forEach { (stepIndex, content) ->
            item(key = "result_$stepIndex") {
                StepResultCard(
                    stepNumber = stepIndex + 1,
                    content = content,
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        fadeOutSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    initiallyExpanded = stepIndex == sortedStepResults.keys.last()
                )
            }
        }

        // Current step streaming content
        if (execution.status == WorkflowStatus.RUNNING &&
            execution.currentStepContent.isNotBlank()
        ) {
            item(key = "streaming") {
                Box(modifier = Modifier.animateItem(
                    fadeInSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    fadeOutSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )) {
                    CurrentStepStreamingCard(
                        stepNumber = execution.currentStepIndex + 1,
                        content = execution.currentStepContent
                    )
                }
            }
        }

        // Loading indicator for running step with no content yet
        if (execution.status == WorkflowStatus.RUNNING &&
            execution.currentStepContent.isBlank()
        ) {
            item(key = "loading_step") {
                Box(modifier = Modifier.animateItem(
                    fadeInSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    fadeOutSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )) {
                    CurrentStepLoadingCard(
                        stepNumber = execution.currentStepIndex + 1
                    )
                }
            }
        }
    }
}

/**
 * Status banner for terminal execution states.
 */
@Composable
private fun StatusBanner(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * Card showing streaming content for the currently executing step.
 */
@Composable
private fun CurrentStepStreamingCard(
    stepNumber: Int,
    content: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = ExpressiveMotion.Spatial.container()),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(26.dp)
                ) {
                    Text(
                        text = "$stepNumber",
                        color = MaterialTheme.colorScheme.onTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "Step $stepNumber — Running...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            MarkdownText(
                markdown = content,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Card showing a loading indicator for a step that hasn't started streaming yet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CurrentStepLoadingCard(stepNumber: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(26.dp)
            ) {
                Text(
                    text = "$stepNumber",
                    color = MaterialTheme.colorScheme.onTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(6.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Step $stepNumber — Waiting for response...",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            LoadingIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
