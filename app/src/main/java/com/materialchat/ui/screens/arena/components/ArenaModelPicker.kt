package com.materialchat.ui.screens.arena.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Provider

/**
 * Dual model picker for the arena screen.
 *
 * Shows two side-by-side dropdowns for selecting provider and model
 * for each panel of the arena battle.
 *
 * @param providers Available providers
 * @param leftProviderId Currently selected left provider ID
 * @param rightProviderId Currently selected right provider ID
 * @param leftModelName Currently selected left model name
 * @param rightModelName Currently selected right model name
 * @param leftModels Models available for the left provider
 * @param rightModels Models available for the right provider
 * @param onLeftProviderSelected Callback when left provider changes
 * @param onRightProviderSelected Callback when right provider changes
 * @param onLeftModelSelected Callback when left model changes
 * @param onRightModelSelected Callback when right model changes
 * @param enabled Whether the dropdowns are interactive
 * @param isLoadingModels Whether models are currently loading
 */
@Composable
fun ArenaModelPicker(
    providers: List<Provider>,
    leftProviderId: String?,
    rightProviderId: String?,
    leftModelName: String?,
    rightModelName: String?,
    leftModels: List<AiModel>,
    rightModels: List<AiModel>,
    onLeftProviderSelected: (String) -> Unit,
    onRightProviderSelected: (String) -> Unit,
    onLeftModelSelected: (AiModel) -> Unit,
    onRightModelSelected: (AiModel) -> Unit,
    enabled: Boolean = true,
    isLoadingModels: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left panel picker
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Model A",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            ProviderDropdown(
                providers = providers,
                selectedProviderId = leftProviderId,
                onProviderSelected = onLeftProviderSelected,
                enabled = enabled
            )
            Spacer(modifier = Modifier.height(4.dp))
            ModelDropdown(
                models = leftModels,
                selectedModelName = leftModelName,
                onModelSelected = onLeftModelSelected,
                enabled = enabled && !isLoadingModels,
                placeholder = if (isLoadingModels) "Loading..." else "Select model"
            )
        }

        // Right panel picker
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Model B",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            ProviderDropdown(
                providers = providers,
                selectedProviderId = rightProviderId,
                onProviderSelected = onRightProviderSelected,
                enabled = enabled
            )
            Spacer(modifier = Modifier.height(4.dp))
            ModelDropdown(
                models = rightModels,
                selectedModelName = rightModelName,
                onModelSelected = onRightModelSelected,
                enabled = enabled && !isLoadingModels,
                placeholder = if (isLoadingModels) "Loading..." else "Select model"
            )
        }
    }
}

@Composable
private fun ProviderDropdown(
    providers: List<Provider>,
    selectedProviderId: String?,
    onProviderSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProvider = providers.find { it.id == selectedProviderId }

    FilterChip(
        selected = selectedProvider != null,
        onClick = { if (enabled) expanded = true },
        label = {
            Text(
                text = selectedProvider?.name ?: "Provider",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null
            )
        },
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        providers.forEach { provider ->
            DropdownMenuItem(
                text = { Text(provider.name) },
                onClick = {
                    onProviderSelected(provider.id)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun ModelDropdown(
    models: List<AiModel>,
    selectedModelName: String?,
    onModelSelected: (AiModel) -> Unit,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.find { it.id == selectedModelName }

    FilterChip(
        selected = selectedModel != null,
        onClick = { if (enabled && models.isNotEmpty()) expanded = true },
        label = {
            Text(
                text = selectedModel?.name ?: placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null
            )
        },
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = modifier.fillMaxWidth()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        models.forEach { model ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = model.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                onClick = {
                    onModelSelected(model)
                    expanded = false
                }
            )
        }
    }
}
