package com.materialchat.ui.screens.arena.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import com.materialchat.ui.screens.arena.ContenderUi
import com.materialchat.domain.model.Provider

/**
 * Blind-battle roster picker.
 *
 * One provider dropdown browses models; tapping a model chip toggles it into
 * the battle roster (2–4 entrants). Models the user chats with most appear
 * first, ranked from personal usage history. Selected chips show their slot
 * codename so entrants feel like mystery fighters, not config entries.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArenaModelPicker(
    providers: List<Provider>,
    pickerProviderId: String?,
    pickerModels: List<AiModel>,
    selectedContenders: List<ContenderUi>,
    usageRanking: List<String>,
    onProviderSelected: (String) -> Unit,
    onToggleContender: (AiModel) -> Unit,
    enabled: Boolean = true,
    isLoadingModels: Boolean = false,
    modifier: Modifier = Modifier
) {
    var providerMenuOpen by remember { mutableStateOf(false) }
    val providerName = providers.firstOrNull { it.id == pickerProviderId }?.name
        ?: "Choose provider"

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Fighters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Provider selector + selected-roster summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { if (enabled) providerMenuOpen = true },
                label = {
                    Text(providerName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )

            if (selectedContenders.isNotEmpty()) {
                Text(
                    text = "${selectedContenders.size} in the ring" +
                            if (selectedContenders.size < 2) " — pick at least 2" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }

        DropdownMenu(
            expanded = providerMenuOpen,
            onDismissRequest = { providerMenuOpen = false }
        ) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.name) },
                    onClick = {
                        providerMenuOpen = false
                        onProviderSelected(provider.id)
                    }
                )
            }
        }

        if (isLoadingModels && pickerModels.isEmpty()) {
            Text(
                text = "Loading models…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        // Model chips — usage-ranked, toggle to enter/leave the ring.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val sorted = pickerModels.sortedByDescending { model ->
                val rank = usageRanking.indexOf(model.id)
                if (rank == -1) Int.MAX_VALUE else rank
            }
            sorted.forEach { model ->
                val contender = selectedContenders.firstOrNull { it.modelName == model.id }
                FilterChip(
                    selected = contender != null,
                    onClick = { if (enabled || contender != null) onToggleContender(model) },
                    label = {
                        Text(
                            text = if (contender != null) "${contender.codename} · ${model.id}"
                                   else model.id,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = if (contender != null) {
                        { Icon(Icons.Outlined.Check, contentDescription = null) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}
