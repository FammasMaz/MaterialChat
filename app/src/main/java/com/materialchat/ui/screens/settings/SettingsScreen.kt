package com.materialchat.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsSystemDaydream
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import com.materialchat.ui.components.ExpressiveButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChipDefaults
import com.materialchat.ui.components.ExpressiveSwitch
import androidx.compose.material3.Text
import com.materialchat.ui.components.ExpressiveButtonStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.AppUpdate
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.UpdateState

import com.materialchat.ui.screens.settings.components.AddProviderSheet
import com.materialchat.ui.screens.settings.components.ProviderCard
import com.materialchat.ui.screens.settings.components.SystemPromptField
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.MaterialChatThemePalettes

/**
 * Settings screen for managing providers, theme, and app preferences.
 *
 * Features:
 * - Provider list with add/edit/delete
 * - System prompt configuration
 * - Theme selection (System/Light/Dark)
 * - Dynamic color toggle
 *
 * @param onNavigateBack Callback to navigate back
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToInteractionSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateNotificationsEnabled(granted)
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Notification permission denied",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.NavigateBack -> { /* No-op: Settings is a top-level tab */ }
                is SettingsEvent.ShowSnackbar -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = event.actionLabel,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.ProviderSaved -> {
                    val action = if (event.isNew) "added" else "updated"
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "${event.providerName} $action",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.ProviderDeleted -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "${event.providerName} deleted",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.ProviderActivated -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "${event.providerName} is now active",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.SettingsSaved -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Settings saved",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is SettingsEvent.ConnectionTestResult -> {
                    val message = if (event.success) {
                        "Connected to ${event.providerName}"
                    } else {
                        "Connection failed: ${event.errorMessage ?: "Unknown error"}"
                    }
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    shape = CustomShapes.Snackbar,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    ) { paddingValues ->
        SettingsContent(
            uiState = uiState,
            paddingValues = paddingValues,
            onAddProvider = { viewModel.showAddProviderSheet() },
            onEditProvider = { viewModel.editProvider(it) },
            onDeleteProvider = { viewModel.showDeleteConfirmation(it) },
            onSetActiveProvider = { viewModel.setActiveProvider(it) },
            onTestConnection = { viewModel.testConnection(it) },
            onSystemPromptChange = { viewModel.updateSystemPrompt(it) },
            onThemeModeChange = { viewModel.updateThemeMode(it) },
            onThemePaletteChange = { viewModel.updateThemePalette(it) },
            onChatBubbleStyleChange = { viewModel.updateChatBubbleStyle(it) },
            onControlShapeStyleChange = { viewModel.updateControlShapeStyle(it) },
            onDynamicColorChange = { viewModel.updateDynamicColorEnabled(it) },
            onHapticsChange = { viewModel.updateHapticsEnabled(it) },
            onFontSizeScaleChange = { viewModel.updateFontSizeScale(it) },
            onNotificationsChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        viewModel.updateNotificationsEnabled(true)
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    viewModel.updateNotificationsEnabled(enabled)
                }
            },
            onBeautifulModelNamesChange = { viewModel.updateBeautifulModelNamesEnabled(it) },
            onAiGeneratedTitlesChange = { viewModel.updateAiGeneratedTitlesEnabled(it) },
            onTitleGenerationModelChange = { viewModel.updateTitleGenerationModel(it) },
            onDefaultImageGenerationModelChange = { viewModel.updateDefaultImageGenerationModel(it) },
            onDefaultImageOutputFormatChange = { viewModel.updateDefaultImageOutputFormat(it) },
            onRememberLastModelChange = { viewModel.updateRememberLastModelEnabled(it) },
            onAlwaysShowThinkingChange = { viewModel.updateAlwaysShowThinking(it) },
            onShowTokenCounterChange = { viewModel.updateShowTokenCounter(it) },
            onAutoCheckUpdatesChange = { viewModel.updateAutoCheckUpdates(it) },
            onCheckForUpdates = { viewModel.checkForUpdates() },
            onDownloadUpdate = { viewModel.downloadUpdate(it) },
            onInstallUpdate = { viewModel.installUpdate() },
            onCancelDownload = { viewModel.cancelDownload() },
            onSkipVersion = { viewModel.skipVersion() },
            onRetry = { viewModel.retry() },
            onAssistantEnabledChange = { viewModel.updateAssistantEnabled(it) },
            onAssistantVoiceChange = { viewModel.updateAssistantVoiceEnabled(it) },
            onAssistantTtsChange = { viewModel.updateAssistantTtsEnabled(it) },
            onWebSearchEnabledChange = { viewModel.updateWebSearchEnabled(it) },
            onWebSearchProviderChange = { viewModel.updateWebSearchProvider(it) },
            onExaApiKeyChange = { viewModel.updateExaApiKey(it) },
            onSearxngBaseUrlChange = { viewModel.updateSearxngBaseUrl(it) },
            onWebSearchMaxResultsChange = { viewModel.updateWebSearchMaxResults(it) },
            onNavigateToInteractionSettings = onNavigateToInteractionSettings
        )
    }

    // Add Provider Bottom Sheet
    val successState = uiState as? SettingsUiState.Success
    if (successState != null) {
        AddProviderSheet(
            isVisible = successState.showAddProviderSheet,
            isEditing = successState.editingProvider != null,
            editingProvider = successState.editingProvider,
            formState = formState,
            isSaving = successState.isSaving,
            onDismiss = { viewModel.hideProviderSheet() },
            onFieldChange = { name, type, baseUrl, defaultModel, apiKey ->
                viewModel.updateFormField(
                    name = name,
                    type = type,
                    baseUrl = baseUrl,
                    defaultModel = defaultModel,
                    apiKey = apiKey
                )
            },
            onFetchModels = { viewModel.fetchProviderModels() },
            onAuthenticate = { viewModel.authenticateNativeProvider() },
            onSave = { viewModel.saveProvider() }
        )

        // Delete Confirmation Dialog
        successState.showDeleteConfirmation?.let { provider ->
            DeleteProviderDialog(
                providerName = provider.name,
                onConfirm = { viewModel.deleteProvider(provider) },
                onDismiss = { viewModel.hideDeleteConfirmation() }
            )
        }
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    paddingValues: PaddingValues,
    onAddProvider: () -> Unit,
    onEditProvider: (Provider) -> Unit,
    onDeleteProvider: (Provider) -> Unit,
    onSetActiveProvider: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onThemeModeChange: (AppPreferences.ThemeMode) -> Unit,
    onThemePaletteChange: (AppPreferences.ThemePalette) -> Unit,
    onChatBubbleStyleChange: (AppPreferences.ChatBubbleStyle) -> Unit,
    onControlShapeStyleChange: (AppPreferences.ControlShapeStyle) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onFontSizeScaleChange: (Float) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onBeautifulModelNamesChange: (Boolean) -> Unit,
    onAiGeneratedTitlesChange: (Boolean) -> Unit,
    onTitleGenerationModelChange: (String) -> Unit,
    onDefaultImageGenerationModelChange: (String) -> Unit,
    onDefaultImageOutputFormatChange: (String) -> Unit,
    onRememberLastModelChange: (Boolean) -> Unit,
    onAlwaysShowThinkingChange: (Boolean) -> Unit,
    onShowTokenCounterChange: (Boolean) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkipVersion: () -> Unit,
    onRetry: () -> Unit,
    onAssistantEnabledChange: (Boolean) -> Unit,
    onAssistantVoiceChange: (Boolean) -> Unit,
    onAssistantTtsChange: (Boolean) -> Unit,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onWebSearchProviderChange: (String) -> Unit,
    onExaApiKeyChange: (String) -> Unit,
    onSearxngBaseUrlChange: (String) -> Unit,
    onWebSearchMaxResultsChange: (Int) -> Unit,
    onNavigateToInteractionSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding())
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
        when (uiState) {
            is SettingsUiState.Loading -> {
                LoadingContent()
            }
            is SettingsUiState.Success -> {
                SuccessContent(
                    uiState = uiState,
                    onAddProvider = onAddProvider,
                    onEditProvider = onEditProvider,
                    onDeleteProvider = onDeleteProvider,
                    onSetActiveProvider = onSetActiveProvider,
                    onTestConnection = onTestConnection,
                    onSystemPromptChange = onSystemPromptChange,
                    onThemeModeChange = onThemeModeChange,
                    onThemePaletteChange = onThemePaletteChange,
                    onChatBubbleStyleChange = onChatBubbleStyleChange,
                    onControlShapeStyleChange = onControlShapeStyleChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onHapticsChange = onHapticsChange,
                    onFontSizeScaleChange = onFontSizeScaleChange,
                    onNotificationsChange = onNotificationsChange,
                    onBeautifulModelNamesChange = onBeautifulModelNamesChange,
                    onAiGeneratedTitlesChange = onAiGeneratedTitlesChange,
                    onTitleGenerationModelChange = onTitleGenerationModelChange,
                    onDefaultImageGenerationModelChange = onDefaultImageGenerationModelChange,
                    onDefaultImageOutputFormatChange = onDefaultImageOutputFormatChange,
                    onRememberLastModelChange = onRememberLastModelChange,
                    onAlwaysShowThinkingChange = onAlwaysShowThinkingChange,
                    onShowTokenCounterChange = onShowTokenCounterChange,
                    onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallUpdate = onInstallUpdate,
                    onCancelDownload = onCancelDownload,
                    onSkipVersion = onSkipVersion,
                    onAssistantEnabledChange = onAssistantEnabledChange,
                    onAssistantVoiceChange = onAssistantVoiceChange,
                    onAssistantTtsChange = onAssistantTtsChange,
                    onWebSearchEnabledChange = onWebSearchEnabledChange,
                    onWebSearchProviderChange = onWebSearchProviderChange,
                    onExaApiKeyChange = onExaApiKeyChange,
                    onSearxngBaseUrlChange = onSearxngBaseUrlChange,
                    onWebSearchMaxResultsChange = onWebSearchMaxResultsChange,
                    onNavigateToInteractionSettings = onNavigateToInteractionSettings
                )
            }
            is SettingsUiState.Error -> {
                ErrorContent(
                    message = uiState.message,
                    onRetry = onRetry
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingContent() {
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

@Composable
private fun SuccessContent(
    uiState: SettingsUiState.Success,
    onAddProvider: () -> Unit,
    onEditProvider: (Provider) -> Unit,
    onDeleteProvider: (Provider) -> Unit,
    onSetActiveProvider: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onThemeModeChange: (AppPreferences.ThemeMode) -> Unit,
    onThemePaletteChange: (AppPreferences.ThemePalette) -> Unit,
    onChatBubbleStyleChange: (AppPreferences.ChatBubbleStyle) -> Unit,
    onControlShapeStyleChange: (AppPreferences.ControlShapeStyle) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onFontSizeScaleChange: (Float) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onBeautifulModelNamesChange: (Boolean) -> Unit,
    onAiGeneratedTitlesChange: (Boolean) -> Unit,
    onTitleGenerationModelChange: (String) -> Unit,
    onDefaultImageGenerationModelChange: (String) -> Unit,
    onDefaultImageOutputFormatChange: (String) -> Unit,
    onRememberLastModelChange: (Boolean) -> Unit,
    onAlwaysShowThinkingChange: (Boolean) -> Unit,
    onShowTokenCounterChange: (Boolean) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkipVersion: () -> Unit,
    onAssistantEnabledChange: (Boolean) -> Unit,
    onAssistantVoiceChange: (Boolean) -> Unit,
    onAssistantTtsChange: (Boolean) -> Unit,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onWebSearchProviderChange: (String) -> Unit,
    onExaApiKeyChange: (String) -> Unit,
    onSearxngBaseUrlChange: (String) -> Unit,
    onWebSearchMaxResultsChange: (Int) -> Unit,
    onNavigateToInteractionSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Providers Section
        item {
            SectionHeader(title = "AI Providers")
        }

        items(
            items = uiState.providers,
            key = { it.provider.id }
        ) { providerItem ->
            ProviderCard(
                providerItem = providerItem,
                onActivate = { onSetActiveProvider(providerItem.provider.id) },
                onEdit = { onEditProvider(providerItem.provider) },
                onDelete = { onDeleteProvider(providerItem.provider) },
                onTestConnection = { onTestConnection(providerItem.provider.id) },
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

        item {
            AddProviderButton(onClick = onAddProvider)
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Appearance Section
        item {
            SectionHeader(title = "Appearance")
        }

        item {
            ThemeModeSelector(
                selectedMode = uiState.themeMode,
                onModeSelected = onThemeModeChange
            )
        }

        item {
            ColorPaletteSelector(
                selectedPalette = uiState.themePalette,
                dynamicColorEnabled = uiState.dynamicColorEnabled && uiState.isDynamicColorSupported,
                onPaletteSelected = onThemePaletteChange
            )
        }

        item {
            ChatBubbleStyleSelector(
                selectedStyle = uiState.chatBubbleStyle,
                onStyleSelected = onChatBubbleStyleChange
            )
        }

        item {
            ControlShapeStyleSelector(
                selectedStyle = uiState.controlShapeStyle,
                onStyleSelected = onControlShapeStyleChange
            )
        }

        item {
            InteractionSettingsCard(onClick = onNavigateToInteractionSettings)
        }

        // Dynamic Color Toggle (Android 12+ only)
        if (uiState.isDynamicColorSupported) {
            item {
                DynamicColorToggle(
                    enabled = uiState.dynamicColorEnabled,
                    onToggle = onDynamicColorChange
                )
            }
        }

        // Font Size Selector
        item {
            FontSizeSelector(
                selectedScale = uiState.fontSizeScale,
                onScaleSelected = onFontSizeScaleChange
            )
        }

        // Haptics Toggle
        item {
            HapticsToggle(
                enabled = uiState.hapticsEnabled,
                onToggle = onHapticsChange
            )
        }

        item {
            NotificationsToggle(
                enabled = uiState.notificationsEnabled,
                onToggle = onNotificationsChange
            )
        }

        // Beautiful Model Names Toggle
        item {
            val activeProvider = uiState.providers.find { it.provider.isActive }?.provider
            BeautifulModelNamesToggle(
                enabled = uiState.beautifulModelNamesEnabled,
                activeProviderName = activeProvider?.name,
                onToggle = onBeautifulModelNamesChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Chat Section
        item {
            SectionHeader(title = "Chat")
        }

        item {
            RememberLastModelToggle(
                enabled = uiState.rememberLastModelEnabled,
                onToggle = onRememberLastModelChange
            )
        }

        item {
            AiGeneratedTitlesToggle(
                enabled = uiState.aiGeneratedTitlesEnabled,
                onToggle = onAiGeneratedTitlesChange
            )
        }

        // Show title generation model field only when AI titles are enabled
        if (uiState.aiGeneratedTitlesEnabled) {
            item {
                TitleGenerationModelField(
                    currentModel = uiState.titleGenerationModel,
                    onModelChange = onTitleGenerationModelChange
                )
            }
        }

        item {
            DefaultImageGenerationModelField(
                currentModel = uiState.defaultImageGenerationModel,
                onModelChange = onDefaultImageGenerationModelChange
            )
        }

        item {
            DefaultImageOutputFormatField(
                currentFormat = uiState.defaultImageOutputFormat,
                onFormatChange = onDefaultImageOutputFormatChange
            )
        }

        item {
            AlwaysShowThinkingToggle(
                enabled = uiState.alwaysShowThinking,
                onToggle = onAlwaysShowThinkingChange
            )
        }

        item {
            ShowTokenCounterToggle(
                enabled = uiState.showTokenCounter,
                onToggle = onShowTokenCounterChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Web Search Section
        item {
            SectionHeader(title = "Web Search")
        }

        item {
            WebSearchSection(
                webSearchEnabled = uiState.webSearchEnabled,
                webSearchProvider = uiState.webSearchProvider,
                exaApiKeyConfigured = uiState.exaApiKeyConfigured,
                searxngBaseUrl = uiState.searxngBaseUrl,
                webSearchMaxResults = uiState.webSearchMaxResults,
                onWebSearchEnabledChange = onWebSearchEnabledChange,
                onWebSearchProviderChange = onWebSearchProviderChange,
                onExaApiKeyChange = onExaApiKeyChange,
                onSearxngBaseUrlChange = onSearxngBaseUrlChange,
                onWebSearchMaxResultsChange = onWebSearchMaxResultsChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Assistant Section
        item {
            SectionHeader(title = "Assistant")
        }

        item {
            AssistantSection(
                assistantEnabled = uiState.assistantEnabled,
                voiceEnabled = uiState.assistantVoiceEnabled,
                ttsEnabled = uiState.assistantTtsEnabled,
                onAssistantEnabledChange = onAssistantEnabledChange,
                onVoiceEnabledChange = onAssistantVoiceChange,
                onTtsEnabledChange = onAssistantTtsChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // System Prompt Section
        item {
            SectionHeader(title = "System Prompt")
        }

        item {
            SystemPromptField(
                currentPrompt = uiState.systemPrompt,
                onPromptChange = onSystemPromptChange
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // About & Updates Section
        item {
            SectionHeader(title = "About & Updates")
        }

        item {
            AboutSection(
                appVersion = uiState.appVersion,
                autoCheckUpdates = uiState.autoCheckUpdates,
                updateState = uiState.updateState,
                onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate,
                onCancelDownload = onCancelDownload,
                onSkipVersion = onSkipVersion
            )
        }

        // Bottom padding for floating toolbar clearance
        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AddProviderButton(onClick: () -> Unit) {
    ExpressiveButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        text = "Add Provider",
        leadingIcon = Icons.Default.Add,
        style = ExpressiveButtonStyle.Outlined
    )
}

@Composable
private fun ThemeModeSelector(
    selectedMode: AppPreferences.ThemeMode,
    onModeSelected: (AppPreferences.ThemeMode) -> Unit
) {
    val haptics = rememberHapticFeedback()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPreferences.ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            haptics.perform(HapticPattern.CLICK)
                            onModeSelected(mode)
                        },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    AppPreferences.ThemeMode.SYSTEM -> Icons.Outlined.SettingsSystemDaydream
                                    AppPreferences.ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                    AppPreferences.ThemeMode.DARK -> Icons.Outlined.DarkMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
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
        }
    }
}

@Composable
private fun ColorPaletteSelector(
    selectedPalette: AppPreferences.ThemePalette,
    dynamicColorEnabled: Boolean,
    onPaletteSelected: (AppPreferences.ThemePalette) -> Unit
) {
    val haptics = rememberHapticFeedback()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Material palette",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (dynamicColorEnabled) {
                            "Used when Dynamic Color is off or unavailable"
                        } else {
                            "Choose a colorful Material 3 fallback theme"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AppPreferences.ThemePalette.entries) { palette ->
                    val selected = selectedPalette == palette
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.CLICK)
                            onPaletteSelected(palette)
                        },
                        shape = RoundedCornerShape(if (selected) 28.dp else 20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        tonalElevation = if (selected) 4.dp else 1.dp,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialChatThemePalettes.previewColor(palette))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialChatThemePalettes.previewSecondaryColor(palette))
                                )
                            }
                            Text(
                                text = palette.prettyName(),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleStyleSelector(
    selectedStyle: AppPreferences.ChatBubbleStyle,
    onStyleSelected: (AppPreferences.ChatBubbleStyle) -> Unit
) {
    val haptics = rememberHapticFeedback()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Chat bubble shape",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pick a Material 3 bubble geometry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AppPreferences.ChatBubbleStyle.entries) { style ->
                    val selected = selectedStyle == style
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.MORPH_TRANSITION)
                            onStyleSelected(style)
                        },
                        shape = RoundedCornerShape(if (selected) 28.dp else 20.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        tonalElevation = if (selected) 4.dp else 1.dp,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(128.dp)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(width = 52.dp, height = 24.dp),
                                    shape = previewBubbleShape(style, isUser = false),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {}
                                Surface(
                                    modifier = Modifier.size(width = 48.dp, height = 24.dp),
                                    shape = previewBubbleShape(style, isUser = true),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {}
                            }
                            Text(
                                text = style.prettyName(),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlShapeStyleSelector(
    selectedStyle: AppPreferences.ControlShapeStyle,
    onStyleSelected: (AppPreferences.ControlShapeStyle) -> Unit
) {
    val haptics = rememberHapticFeedback()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Control shape intensity",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose how playful buttons and controls feel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(AppPreferences.ControlShapeStyle.entries) { style ->
                    val selected = selectedStyle == style
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.MORPH_TRANSITION)
                            onStyleSelected(style)
                        },
                        shape = RoundedCornerShape(if (selected) 28.dp else 20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        tonalElevation = if (selected) 4.dp else 1.dp,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(124.dp)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ShapePreviewDot(style = style, selected = selected)
                                ShapePreviewDot(style = style, selected = false)
                            }
                            Text(
                                text = style.prettyName(),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapePreviewDot(
    style: AppPreferences.ControlShapeStyle,
    selected: Boolean
) {
    val radius = when (style) {
        AppPreferences.ControlShapeStyle.CLASSIC -> 24.dp
        AppPreferences.ControlShapeStyle.BALANCED -> if (selected) 16.dp else 20.dp
        AppPreferences.ControlShapeStyle.EXPRESSIVE -> if (selected) 8.dp else 14.dp
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(radius))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer
            )
    )
}

private fun AppPreferences.ThemePalette.prettyName(): String {
    return name.lowercase().replaceFirstChar { it.uppercase() }
}

private fun AppPreferences.ChatBubbleStyle.prettyName(): String {
    return name.lowercase().replaceFirstChar { it.uppercase() }
}

private fun AppPreferences.ControlShapeStyle.prettyName(): String {
    return name.lowercase().replaceFirstChar { it.uppercase() }
}

private fun previewBubbleShape(
    style: AppPreferences.ChatBubbleStyle,
    isUser: Boolean
): RoundedCornerShape {
    return when (style) {
        AppPreferences.ChatBubbleStyle.EXPRESSIVE -> if (isUser) {
            RoundedCornerShape(24.dp, 6.dp, 24.dp, 24.dp)
        } else {
            RoundedCornerShape(6.dp, 24.dp, 24.dp, 24.dp)
        }
        AppPreferences.ChatBubbleStyle.ROUNDED -> RoundedCornerShape(28.dp)
        AppPreferences.ChatBubbleStyle.COMPACT -> if (isUser) {
            RoundedCornerShape(16.dp, 6.dp, 16.dp, 16.dp)
        } else {
            RoundedCornerShape(6.dp, 16.dp, 16.dp, 16.dp)
        }
        AppPreferences.ChatBubbleStyle.GEOMETRIC -> if (isUser) {
            RoundedCornerShape(18.dp, 4.dp, 18.dp, 10.dp)
        } else {
            RoundedCornerShape(4.dp, 18.dp, 10.dp, 18.dp)
        }
    }
}

@Composable
private fun DynamicColorToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Dynamic Color",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use colors from your wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun FontSizeSelector(
    selectedScale: Float,
    onScaleSelected: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Chat Font Size",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Adjust text size in chat bubbles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Size labels above the slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "A",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // M3 Slider with discrete steps at the preset sizes
            Slider(
                value = selectedScale,
                onValueChange = { onScaleSelected(it) },
                valueRange = 0.85f..1.4f,
                steps = 10,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            // Step labels below the slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Small",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Default",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedScale in 0.97f..1.03f) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedScale in 0.97f..1.03f) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
                Text(
                    text = "Large",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Preview text
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The quick brown fox jumps over the lazy dog.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * selectedScale,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * selectedScale
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun InteractionSettingsCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Vibration,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interaction & Motion",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Per-component haptics, button shapes, and expressive motion controls",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.graphicsLayer { rotationZ = 180f }
            )
        }
    }
}

@Composable
private fun HapticsToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Haptic Feedback",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vibrate on interactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun NotificationsToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Show agent updates when app is in background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun BeautifulModelNamesToggle(
    enabled: Boolean,
    activeProviderName: String?,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Beautiful Model Names",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (activeProviderName != null) {
                            "Using $activeProviderName • Tap to filter by provider"
                        } else {
                            "Format model names into readable badges"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun RememberLastModelToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Remember Last Model",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "New chats use your last selected model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun AiGeneratedTitlesToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Title,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI-Generated Titles",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use AI to create meaningful conversation titles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun AlwaysShowThinkingToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Always Show Thinking",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Show model reasoning content by default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun ShowTokenCounterToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Numbers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Token Counter",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Show word and estimated token count while typing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExpressiveSwitch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun TitleGenerationModelField(
    currentModel: String,
    onModelChange: (String) -> Unit
) {
    var text by remember(currentModel) { mutableStateOf(currentModel) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Title Generation Model",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Leave empty to use the conversation's model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue
                },
                placeholder = {
                    Text(
                        text = "e.g., llama3.2:1b or gpt-4o-mini",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ExpressiveButton(
                    onClick = { onModelChange(text) },
                    enabled = text != currentModel,
                    text = "Save",
                    style = ExpressiveButtonStyle.FilledTonal
                )
            }
        }
    }
}

@Composable
private fun DefaultImageGenerationModelField(
    currentModel: String,
    onModelChange: (String) -> Unit
) {
    var text by remember(currentModel) { mutableStateOf(currentModel) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Default Image Model",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Any chat can hand image requests to this model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { newValue -> text = newValue },
                placeholder = {
                    Text(
                        text = AppPreferences.DEFAULT_IMAGE_GENERATION_MODEL,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ExpressiveButton(
                    onClick = { onModelChange(text) },
                    enabled = text != currentModel,
                    text = "Save",
                    style = ExpressiveButtonStyle.FilledTonal
                )
            }
        }
    }
}

@Composable
private fun DefaultImageOutputFormatField(
    currentFormat: String,
    onFormatChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Image Output Format",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Generated images are saved in this format",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPreferences.SUPPORTED_IMAGE_OUTPUT_FORMATS.forEach { format ->
                    FilterChip(
                        selected = currentFormat == format,
                        onClick = { onFormatChange(format) },
                        label = { Text(format.uppercase()) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteProviderDialog(
    providerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Provider",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"$providerName\"? This action cannot be undone. Existing conversations will remain but will show \"Provider deleted\".",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            ExpressiveButton(
                onClick = onConfirm,
                text = "Delete",
                style = ExpressiveButtonStyle.Text
            )
        },
        dismissButton = {
            ExpressiveButton(
                onClick = onDismiss,
                text = "Cancel",
                style = ExpressiveButtonStyle.Text
            )
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

        ExpressiveButton(
            onClick = onRetry,
            text = "Retry",
            style = ExpressiveButtonStyle.Text
        )
    }
}

@Composable
private fun AboutSection(
    appVersion: String,
    autoCheckUpdates: Boolean,
    updateState: UpdateState,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkipVersion: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Version Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MaterialChat",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version $appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Auto-check for updates toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Auto-check for updates",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Check for updates on startup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ExpressiveSwitch(
                    checked = autoCheckUpdates,
                    onCheckedChange = onAutoCheckUpdatesChange
                )
            }
        }

        // Check for updates button with status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Check for updates",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = getUpdateStatusText(updateState),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    when (updateState) {
                        is UpdateState.Checking -> {
                            com.materialchat.ui.components.M3ExpressiveCircularProgress(
                                size = 24.dp,
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is UpdateState.Downloading -> {
                            com.materialchat.ui.components.M3ExpressiveCircularProgress(
                                size = 24.dp,
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            com.materialchat.ui.components.ExpressiveButton(
                                onClick = onCheckForUpdates,
                                text = "Check",
                                style = com.materialchat.ui.components.ExpressiveButtonStyle.Outlined,
                                enabled = updateState !is UpdateState.Installing
                            )
                        }
                    }
                }

                // Show actions based on update state
                when (updateState) {
                    is UpdateState.Available -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpressiveButton(
                                onClick = onSkipVersion,
                                text = "Skip version",
                                style = ExpressiveButtonStyle.Text
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            com.materialchat.ui.components.ExpressiveButton(
                                onClick = { onDownloadUpdate(updateState.update) },
                                text = "Download v${updateState.update.versionName}",
                                style = com.materialchat.ui.components.ExpressiveButtonStyle.Outlined
                            )
                        }
                    }
                    is UpdateState.Downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            ExpressiveButton(
                                onClick = onCancelDownload,
                                text = "Cancel",
                                style = ExpressiveButtonStyle.Text
                            )
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            com.materialchat.ui.components.ExpressiveButton(
                                onClick = onInstallUpdate,
                                text = "Install Now",
                                style = com.materialchat.ui.components.ExpressiveButtonStyle.Outlined
                            )
                        }
                    }
                    is UpdateState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> { /* No additional UI */ }
                }
            }
        }
    }
}

private fun getUpdateStatusText(state: UpdateState): String {
    return when (state) {
        is UpdateState.Idle -> "Tap to check for updates"
        is UpdateState.Checking -> "Checking..."
        is UpdateState.Available -> "Update available: v${state.update.versionName}"
        is UpdateState.UpToDate -> "You're up to date"
        is UpdateState.Downloading -> "Downloading... ${(state.progress * 100).toInt()}%"
        is UpdateState.ReadyToInstall -> "Ready to install v${state.update.versionName}"
        is UpdateState.Installing -> "Installing..."
        is UpdateState.Error -> "Error checking for updates"
    }
}

@Composable
private fun AssistantSection(
    assistantEnabled: Boolean,
    voiceEnabled: Boolean,
    ttsEnabled: Boolean,
    onAssistantEnabledChange: (Boolean) -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onTtsEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Assistant Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "System Assistant",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Replace default assistant on your device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ExpressiveSwitch(
                    checked = assistantEnabled,
                    onCheckedChange = onAssistantEnabledChange
                )
            }
        }

        // Set as Default Assistant button
        if (assistantEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set as Default Assistant",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Open system settings to select MaterialChat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpressiveButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                            context.startActivity(intent)
                        },
                        text = "Open",
                        style = ExpressiveButtonStyle.Outlined
                    )
                }
            }

            // Voice Input Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Voice Input",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Use microphone for voice queries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ExpressiveSwitch(
                        checked = voiceEnabled,
                        onCheckedChange = onVoiceEnabledChange
                    )
                }
            }

            // Text-to-Speech Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Read Aloud",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Use text-to-speech for responses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ExpressiveSwitch(
                        checked = ttsEnabled,
                        onCheckedChange = onTtsEnabledChange
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSearchSection(
    webSearchEnabled: Boolean,
    webSearchProvider: String,
    exaApiKeyConfigured: Boolean,
    searxngBaseUrl: String,
    webSearchMaxResults: Int,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onWebSearchProviderChange: (String) -> Unit,
    onExaApiKeyChange: (String) -> Unit,
    onSearxngBaseUrlChange: (String) -> Unit,
    onWebSearchMaxResultsChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            // Enable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Web Search",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Search the web before each message",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ExpressiveSwitch(
                    checked = webSearchEnabled,
                    onCheckedChange = onWebSearchEnabledChange
                )
            }

            // Expanded settings when enabled
            if (webSearchEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // Provider selection
                Text(
                    text = "Search Provider",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = webSearchProvider == "EXA",
                        onClick = { onWebSearchProviderChange("EXA") },
                        label = { Text("Exa") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = webSearchProvider == "SEARXNG",
                        onClick = { onWebSearchProviderChange("SEARXNG") },
                        label = { Text("SearXNG") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = webSearchProvider == "NATIVE",
                        onClick = { onWebSearchProviderChange("NATIVE") },
                        label = { Text("Native") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Provider-specific fields
                when (webSearchProvider) {
                    "EXA" -> {
                        var apiKeyText by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { apiKeyText = it },
                            label = {
                                Text(
                                    if (exaApiKeyConfigured) "Exa API Key (configured)"
                                    else "Exa API Key"
                                )
                            },
                            placeholder = { Text("Enter your Exa API key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (apiKeyText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveButton(
                                onClick = {
                                    onExaApiKeyChange(apiKeyText)
                                    apiKeyText = ""
                                },
                                text = "Save API Key",
                                style = ExpressiveButtonStyle.FilledTonal
                            )
                        }
                    }
                    "SEARXNG" -> {
                        var urlText by remember(searxngBaseUrl) { mutableStateOf(searxngBaseUrl) }
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = {
                                urlText = it
                                onSearxngBaseUrlChange(it)
                            },
                            label = { Text("SearXNG Base URL") },
                            placeholder = { Text("https://searx.be") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    "NATIVE" -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = "Uses the selected chat provider's backend search through the proxy /v2 chat endpoint. Configure search providers in the proxy; MaterialChat will ask for citations and turn a final Sources section into the collapsible sources carousel.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                if (webSearchProvider != "NATIVE") {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Max results slider
                    Text(
                        text = "Max Results: $webSearchMaxResults",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = webSearchMaxResults.toFloat(),
                        onValueChange = { onWebSearchMaxResultsChange(it.toInt()) },
                        valueRange = 3f..10f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }
        }
    }
}
