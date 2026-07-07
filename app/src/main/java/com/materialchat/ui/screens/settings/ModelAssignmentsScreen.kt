package com.materialchat.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.TaskModelAssignment
import com.materialchat.domain.util.TaskModelAssignmentCodec
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.components.ExpressiveScreenBackdrop
import com.materialchat.ui.components.ExpressiveSwitch
import com.materialchat.ui.components.ExpressiveTopBarTitle
import com.materialchat.ui.theme.CustomShapes

/**
 * Pre-computed display tiers for the constant image-generation model list so the
 * per-chip label isn't re-derived on every recomposition.
 */
private val IMAGE_MODEL_TIERS: List<Pair<String, String>> =
    AppPreferences.SUPPORTED_IMAGE_GENERATION_MODELS.map { model ->
        model to model.substringAfterLast('-').replaceFirstChar { it.uppercase() }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelAssignmentsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelAssignmentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExpressiveScreenBackdrop {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    title = {
                        ExpressiveTopBarTitle(
                            title = "Model assignments",
                            subtitle = "Which model runs each background task"
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
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AssignmentHeroCard(recommendation = uiState.smallestRecommendation)
                }

                item {
                    AssignmentSectionCard(
                        title = "On-device first",
                        description = "Use the smallest downloaded local model for titles and memory when no cloud model is assigned.",
                        icon = Icons.Outlined.Memory
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prefer on-device for background tasks",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            ExpressiveSwitch(
                                checked = uiState.preferOnDeviceBackgroundTasks,
                                onCheckedChange = viewModel::setPreferOnDevice
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Lightweight models (smallest first)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.onDeviceModels.forEach { row ->
                            OnDeviceModelStatusRow(row = row)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                item {
                    TitleAssignmentCard(
                        currentRaw = uiState.titleModelRaw,
                        providers = uiState.providers,
                        pickerState = uiState.pickerState,
                        aiTitlesEnabled = uiState.aiGeneratedTitlesEnabled,
                        onAiTitlesChange = viewModel::setAiGeneratedTitles,
                        onLoadModels = { viewModel.loadCloudModels(force = true) },
                        onAssign = { providerId, modelId ->
                            viewModel.setTitleModel(providerId, modelId)
                        }
                    )
                }

                item {
                    TaskAssignmentCard(
                        task = TaskModelAssignment.MEMORY_EXTRACTION,
                        currentRaw = uiState.memoryModelRaw,
                        providers = uiState.providers,
                        pickerState = uiState.pickerState,
                        enabled = true,
                        disabledHint = null,
                        automaticLabel = "Automatic (on-device if available, else chat model)",
                        onLoadModels = { viewModel.loadCloudModels(force = true) },
                        onAssign = { providerId, modelId ->
                            viewModel.setMemoryModel(providerId, modelId)
                        },
                        icon = Icons.Outlined.Psychology
                    )
                }

                item {
                    ImageGenerationAssignmentCard(
                        currentModel = uiState.imageModelRaw,
                        currentFormat = uiState.imageOutputFormat,
                        onModelChange = viewModel::setImageModel,
                        onFormatChange = viewModel::setImageOutputFormat
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentHeroCard(recommendation: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CustomShapes.ProviderCard,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Smallest model for titles & memory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = recommendation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun AssignmentSectionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun OnDeviceModelStatusRow(row: OnDeviceModelRow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = listOfNotNull(row.sizeLabel, row.availability)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (row.isUsable) "Ready" else "—",
                style = MaterialTheme.typography.labelLarge,
                color = if (row.isUsable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun TitleAssignmentCard(
    currentRaw: String,
    providers: List<com.materialchat.domain.model.Provider>,
    pickerState: TitleModelPickerState,
    aiTitlesEnabled: Boolean,
    onAiTitlesChange: (Boolean) -> Unit,
    onLoadModels: () -> Unit,
    onAssign: (providerId: String?, modelId: String) -> Unit
) {
    TaskAssignmentCard(
        task = TaskModelAssignment.CONVERSATION_TITLE,
        currentRaw = currentRaw,
        providers = providers,
        pickerState = pickerState,
        enabled = aiTitlesEnabled,
        disabledHint = "Turn on AI-generated titles to enable this model assignment.",
        automaticLabel = "Automatic (on-device if available, else chat model)",
        onLoadModels = onLoadModels,
        onAssign = onAssign,
        icon = Icons.Outlined.Title,
        headerContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI-generated titles",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Create an emoji + concise title after the first response",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveSwitch(
                    checked = aiTitlesEnabled,
                    onCheckedChange = onAiTitlesChange
                )
            }
        }
    )
}

@Composable
private fun TaskAssignmentCard(
    task: TaskModelAssignment,
    currentRaw: String,
    providers: List<com.materialchat.domain.model.Provider>,
    pickerState: TitleModelPickerState,
    enabled: Boolean,
    disabledHint: String?,
    automaticLabel: String,
    onLoadModels: () -> Unit,
    onAssign: (providerId: String?, modelId: String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val (currentProviderId, currentModelId) = remember(currentRaw) {
        TaskModelAssignmentCodec.decode(currentRaw)
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var manualMode by remember { mutableStateOf(false) }
    var manualModel by remember(currentRaw) { mutableStateOf(currentModelId) }
    var manualProviderId by remember(currentRaw) { mutableStateOf(currentProviderId) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    val providerName = providers.firstOrNull { it.id == currentProviderId }?.name
    val selectedLabel = TaskModelAssignmentCodec.displayLabel(currentRaw, providerName)
        .let { if (currentRaw.isBlank()) automaticLabel else it }

    val allModels = remember(pickerState.modelsByProvider, providers) {
        providers.flatMap { p ->
            (pickerState.modelsByProvider[p.id] ?: emptyList()).map { m -> p to m }
        }
    }

    val manualProviderLabel = manualProviderId?.let { id ->
        providers.firstOrNull { it.id == id }?.name ?: id
    } ?: "Conversation's provider"
    val manualDirty = manualModel.trim() != currentModelId.trim() ||
        manualProviderId != currentProviderId

    AssignmentSectionCard(
        title = task.title,
        description = task.subtitle,
        icon = icon
    ) {
        headerContent?.let {
            it()
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (!enabled && disabledHint != null) {
            Text(
                text = disabledHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        task.footnote?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pickerState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Refresh models",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = !pickerState.isLoading) { onLoadModels() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (manualMode) "Pick from list" else "Enter manually",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = enabled) { manualMode = !manualMode }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (manualMode) {
            Text(
                text = "Provider",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(enabled = enabled) { providerMenuExpanded = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = manualProviderLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        }
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Expand"
                    )
                }
            }
            DropdownMenu(
                expanded = providerMenuExpanded && enabled,
                onDismissRequest = { providerMenuExpanded = false },
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                DropdownMenuItem(
                    text = { Text("Conversation's provider") },
                    onClick = {
                        providerMenuExpanded = false
                        manualProviderId = null
                    }
                )
                HorizontalDivider()
                providers.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name) },
                        onClick = {
                            providerMenuExpanded = false
                            manualProviderId = p.id
                        }
                    )
                }
                if (providers.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No providers configured — add one in Settings") },
                        onClick = { providerMenuExpanded = false },
                        enabled = false
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = manualModel,
                onValueChange = { manualModel = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                label = { Text("Model id") },
                placeholder = { Text("e.g., gpt-4o-mini or llama3.2:1b") },
                singleLine = true,
                enabled = enabled
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Use this when the model list won't load. The id must match what your provider expects.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ExpressiveButton(
                    onClick = {
                        onAssign(manualProviderId, manualModel.trim())
                        manualMode = false
                    },
                    enabled = enabled && manualModel.trim().isNotBlank() && manualDirty,
                    text = "Save",
                    style = ExpressiveButtonStyle.FilledTonal
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(enabled = enabled) { menuExpanded = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        }
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded && enabled,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                DropdownMenuItem(
                    text = { Text(automaticLabel) },
                    onClick = {
                        menuExpanded = false
                        onAssign(null, "")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Use chat model") },
                    onClick = {
                        menuExpanded = false
                        onAssign(null, TaskModelAssignmentCodec.chatModel())
                    }
                )
                HorizontalDivider()
                allModels.forEach { (provider, model) ->
                    DropdownMenuItem(
                        text = {
                            Text("${provider.name} · ${model.name}")
                        },
                        onClick = {
                            menuExpanded = false
                            onAssign(provider.id, model.id)
                        }
                    )
                }
                if (allModels.isEmpty() && !pickerState.isLoading) {
                    DropdownMenuItem(
                        text = { Text("No cloud models loaded — tap \"Enter manually\" or add a provider in Settings") },
                        onClick = { menuExpanded = false },
                        enabled = false
                    )
                }
            }
        }
        pickerState.error?.let { err ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ImageGenerationAssignmentCard(
    currentModel: String,
    currentFormat: String,
    onModelChange: (String) -> Unit,
    onFormatChange: (String) -> Unit
) {
    var text by remember(currentModel) { mutableStateOf(currentModel) }
    AssignmentSectionCard(
        title = TaskModelAssignment.IMAGE_GENERATION.title,
        description = TaskModelAssignment.IMAGE_GENERATION.subtitle,
        icon = Icons.Outlined.Image
    ) {
        TaskModelAssignment.IMAGE_GENERATION.footnote?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            label = { Text("Model id") },
            placeholder = { Text(AppPreferences.DEFAULT_IMAGE_GENERATION_MODEL) },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IMAGE_MODEL_TIERS.forEach { (model, tier) ->
                FilterChip(
                    selected = currentModel == model,
                    onClick = {
                        text = model
                        onModelChange(model)
                    },
                    label = { Text(tier) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Output format",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppPreferences.SUPPORTED_IMAGE_OUTPUT_FORMATS.forEach { format ->
                FilterChip(
                    selected = currentFormat == format,
                    onClick = { onFormatChange(format) },
                    label = { Text(format.uppercase()) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            ExpressiveButton(
                onClick = { onModelChange(text.trim()) },
                enabled = text.trim() != currentModel.trim() && text.isNotBlank(),
                text = "Save",
                style = ExpressiveButtonStyle.FilledTonal
            )
        }
    }
}