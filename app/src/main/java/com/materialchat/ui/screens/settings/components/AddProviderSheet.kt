package com.materialchat.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.ui.screens.settings.ProviderFormState
import com.materialchat.ui.theme.CustomShapes

/**
 * Bottom sheet for adding or editing a provider.
 *
 * Features:
 * - Form fields for name, type, base URL, default model, and API key
 * - Real-time validation with error messages
 * - Provider type selector using filter chips
 * - API key field only visible for OpenAI-compatible providers
 * - Loading state during save operation
 * - Spring-physics animations throughout
 *
 * @param isVisible Whether the sheet is visible
 * @param isEditing Whether we're editing an existing provider (vs adding new)
 * @param editingProvider The provider being edited, if any
 * @param formState Current form state with field values and errors
 * @param isSaving Whether a save operation is in progress
 * @param onDismiss Callback when the sheet is dismissed
 * @param onFieldChange Callback when a form field changes
 * @param onSave Callback to save the provider
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderSheet(
    isVisible: Boolean,
    isEditing: Boolean,
    editingProvider: Provider?,
    formState: ProviderFormState,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onFieldChange: (
        name: String?,
        type: ProviderType?,
        baseUrl: String?,
        defaultModel: String?,
        apiKey: String?
    ) -> Unit,
    onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = CustomShapes.BottomSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = null
        ) {
            AddProviderSheetContent(
                isEditing = isEditing,
                editingProvider = editingProvider,
                formState = formState,
                isSaving = isSaving,
                onDismiss = onDismiss,
                onFieldChange = onFieldChange,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun AddProviderSheetContent(
    isEditing: Boolean,
    editingProvider: Provider?,
    formState: ProviderFormState,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onFieldChange: (
        name: String?,
        type: ProviderType?,
        baseUrl: String?,
        defaultModel: String?,
        apiKey: String?
    ) -> Unit,
    onSave: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val imePadding = with(density) { (imeBottom - navBottom).coerceAtLeast(0).toDp() }

    // Auto-focus name field when sheet opens
    LaunchedEffect(Unit) {
        if (!isEditing) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = imePadding)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        // Header
        SheetHeader(
            title = if (isEditing) "Edit Provider" else "Add Provider",
            onClose = onDismiss
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Provider Name
        FormTextField(
            value = formState.name,
            onValueChange = { onFieldChange(it, null, null, null, null) },
            label = "Provider Name",
            placeholder = "e.g., OpenAI, Ollama Local",
            leadingIcon = Icons.Outlined.TextFields,
            error = formState.nameError,
            enabled = !isSaving,
            modifier = Modifier.focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Provider Type
        ProviderTypeSelector(
            selectedType = formState.type,
            onTypeSelected = { onFieldChange(null, it, null, null, null) },
            enabled = !isSaving && !isEditing // Can't change type when editing
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Base URL
        FormTextField(
            value = formState.baseUrl,
            onValueChange = { onFieldChange(null, null, it, null, null) },
            label = "Base URL",
            placeholder = when (formState.type) {
                ProviderType.OPENAI_COMPATIBLE -> "https://api.openai.com"
                ProviderType.OLLAMA_NATIVE -> "http://localhost:11434"
            },
            leadingIcon = Icons.Outlined.Link,
            error = formState.baseUrlError,
            enabled = !isSaving,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Default Model
        FormTextField(
            value = formState.defaultModel,
            onValueChange = { onFieldChange(null, null, null, it, null) },
            label = "Default Model",
            placeholder = when (formState.type) {
                ProviderType.OPENAI_COMPATIBLE -> "gpt-4o"
                ProviderType.OLLAMA_NATIVE -> "llama3.2"
            },
            leadingIcon = Icons.Outlined.Memory,
            error = formState.defaultModelError,
            enabled = !isSaving,
            keyboardOptions = KeyboardOptions(
                imeAction = if (formState.type == ProviderType.OPENAI_COMPATIBLE) {
                    ImeAction.Next
                } else {
                    ImeAction.Done
                }
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (formState.canSubmit) onSave() }
            )
        )

        // API Key (only for OpenAI-compatible providers)
        AnimatedVisibility(
            visible = formState.type == ProviderType.OPENAI_COMPATIBLE,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut() + shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                FormTextField(
                    value = formState.apiKey,
                    onValueChange = { onFieldChange(null, null, null, null, it) },
                    label = if (isEditing) "API Key (leave blank to keep existing)" else "API Key",
                    placeholder = "sk-...",
                    leadingIcon = Icons.Outlined.Key,
                    error = formState.apiKeyError,
                    enabled = !isSaving,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (formState.canSubmit) onSave() }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save button
        SaveButton(
            isEditing = isEditing,
            isSaving = isSaving,
            canSubmit = formState.canSubmit,
            onClick = onSave
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Sheet header with title and close button.
 */
@Composable
private fun SheetHeader(
    title: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Provider type selector using filter chips.
 */
@Composable
private fun ProviderTypeSelector(
    selectedType: ProviderType,
    onTypeSelected: (ProviderType) -> Unit,
    enabled: Boolean
) {
    Column {
        Text(
            text = "Provider Type",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProviderType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { if (enabled) onTypeSelected(type) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = when (type) {
                                ProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible"
                                ProviderType.OLLAMA_NATIVE -> "Ollama"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (type) {
                                ProviderType.OPENAI_COMPATIBLE -> Icons.Outlined.Cloud
                                ProviderType.OLLAMA_NATIVE -> Icons.Outlined.Computer
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

/**
 * Form text field with Material 3 styling.
 */
@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            isError = error != null,
            enabled = enabled,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = MaterialTheme.colorScheme.error
            )
        )

        // Error message
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = error ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Save button with loading state.
 */
@Composable
private fun SaveButton(
    isEditing: Boolean,
    isSaving: Boolean,
    canSubmit: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = canSubmit && !isSaving,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = when {
                isSaving -> "Saving..."
                isEditing -> "Update Provider"
                else -> "Add Provider"
            }
        )
    }
}
