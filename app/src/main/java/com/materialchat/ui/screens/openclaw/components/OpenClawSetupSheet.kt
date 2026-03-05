package com.materialchat.ui.screens.openclaw.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SmartToy
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.materialchat.ui.components.ExpressiveSwitch
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.openclaw.OpenClawConfig
import com.materialchat.ui.theme.CustomShapes

/**
 * Modal bottom sheet for configuring the OpenClaw Gateway connection.
 *
 * Provides fields for gateway URL, authentication token, agent ID,
 * and a toggle for allowing self-signed certificates.
 *
 * @param config Current OpenClaw configuration
 * @param onDismiss Callback to dismiss the sheet
 * @param onSave Callback to save configuration (url, token, agentId, selfSigned)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenClawSetupSheet(
    config: OpenClawConfig,
    recentAgents: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (url: String, token: String, agentId: String, selfSigned: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var gatewayUrl by remember { mutableStateOf(config.gatewayUrl) }
    var token by remember { mutableStateOf("") }
    var agentId by remember { mutableStateOf(config.agentId) }
    var allowSelfSigned by remember { mutableStateOf(config.allowSelfSignedCerts) }
    var agentDropdownExpanded by remember { mutableStateOf(false) }

    val recentAgentCandidates = remember(recentAgents) {
        recentAgents
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val filteredAgents = remember(agentId, recentAgents) {
        recentAgentCandidates
            .filter { candidate ->
                agentId.isBlank() || candidate.contains(agentId, ignoreCase = true)
            }
            .take(8)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = CustomShapes.BottomSheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Gateway Setup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Configure your OpenClaw Gateway connection to start chatting with your AI agent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Gateway URL field
            OutlinedTextField(
                value = gatewayUrl,
                onValueChange = { gatewayUrl = it },
                label = { Text("Gateway URL") },
                placeholder = { Text("http://localhost:18789") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Token field
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Authentication Token") },
                placeholder = { Text("Bearer token or API key") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Default agent field with history suggestions
            ExposedDropdownMenuBox(
                expanded = agentDropdownExpanded && filteredAgents.isNotEmpty(),
                onExpandedChange = { expanded ->
                    agentDropdownExpanded = expanded && filteredAgents.isNotEmpty()
                }
            ) {
                OutlinedTextField(
                    value = agentId,
                    onValueChange = {
                        agentId = it
                        agentDropdownExpanded = true
                    },
                    label = { Text("Default Agent ID") },
                    placeholder = { Text("main") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = agentDropdownExpanded && filteredAgents.isNotEmpty()
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryEditable,
                            enabled = true
                        )
                        .fillMaxWidth()
                )

                DropdownMenu(
                    expanded = agentDropdownExpanded && filteredAgents.isNotEmpty(),
                    onDismissRequest = { agentDropdownExpanded = false }
                ) {
                    filteredAgents.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate) },
                            onClick = {
                                agentId = candidate
                                agentDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (recentAgentCandidates.isNotEmpty()) {
                Text(
                    text = "Recent agents",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = recentAgentCandidates,
                        key = { it }
                    ) { candidate ->
                        FilterChip(
                            selected = agentId == candidate,
                            onClick = {
                                agentId = candidate
                                agentDropdownExpanded = false
                            },
                            label = { Text(candidate) }
                        )
                    }
                }
            }

            // Self-signed certificate toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow Self-Signed Certs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enable for VPN or Tailscale connections",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                ExpressiveSwitch(
                    checked = allowSelfSigned,
                    onCheckedChange = { allowSelfSigned = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveButton(
                    onClick = { onDismiss() },
                    text = "Cancel",
                    style = ExpressiveButtonStyle.Text
                )
                Spacer(modifier = Modifier.width(12.dp))
                ExpressiveButton(
                    onClick = { onSave(gatewayUrl, token, agentId, allowSelfSigned) },
                    text = "Save & Connect",
                    style = ExpressiveButtonStyle.Filled,
                    enabled = gatewayUrl.isNotBlank()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
