package com.materialchat.ui.screens.openclaw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.ui.screens.openclaw.components.ChannelStatusList
import com.materialchat.ui.screens.openclaw.components.GatewayStatusCard
import com.materialchat.ui.components.ExpressiveFab
import com.materialchat.ui.screens.openclaw.components.OpenClawSetupSheet
import com.materialchat.ui.theme.CustomShapes

/**
 * OpenClaw Dashboard screen composable.
 *
 * Displays gateway connection status, channel health, session summary,
 * and provides access to agent chat and session management.
 *
 * @param onNavigateBack Callback to navigate back
 * @param onNavigateToChat Callback to navigate to agent chat
 * @param onNavigateToSessions Callback to navigate to sessions list
 * @param modifier Modifier for the screen
 * @param viewModel The ViewModel managing dashboard state
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenClawDashboardScreen(
    onNavigateToChat: (String?) -> Unit,
    onNavigateToSessions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenClawDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OpenClawDashboardEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is OpenClawDashboardEvent.NavigateToChat -> {
                    onNavigateToChat(event.sessionKey)
                }
                is OpenClawDashboardEvent.NavigateToSessions -> {
                    onNavigateToSessions()
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OpenClaw Gateway")
                    }
                },
                actions = {
                    val currentState = uiState
                    if (currentState is OpenClawDashboardUiState.Success) {
                        IconButton(onClick = viewModel::navigateToSessions) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "Sessions"
                            )
                        }
                        IconButton(onClick = viewModel::showSetup) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Setup"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            val currentState = uiState
            if (currentState is OpenClawDashboardUiState.Success && currentState.isConnected) {
                ExpressiveFab(
                    onClick = viewModel::navigateToChat,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Chat with Agent",
                    expanded = true,
                    text = "Chat with Agent"
                )
            }
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
        },
        modifier = modifier
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            when (val state = uiState) {
                is OpenClawDashboardUiState.Loading -> {
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

                is OpenClawDashboardUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is OpenClawDashboardUiState.Success -> {
                    DashboardContent(
                        state = state,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onRefresh = viewModel::refreshStatus,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Setup bottom sheet
                    if (state.showSetupSheet) {
                        OpenClawSetupSheet(
                            config = state.config,
                            onDismiss = viewModel::hideSetup,
                            onSave = viewModel::saveConfig
                        )
                    }
                }
            }
        }
    }
}

/**
 * Main dashboard content with gateway status, channels, and session summary.
 */
@Composable
private fun DashboardContent(
    state: OpenClawDashboardUiState.Success,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 500f
                )
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Gateway connection status card
        GatewayStatusCard(
            connectionState = state.connectionState,
            status = state.status,
            latencyMs = state.latencyMs,
            isRefreshing = state.isRefreshing,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth()
        )

        // Not configured hint
        if (!state.isConfigured) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Gateway Not Configured",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Set up your gateway URL and authentication token to connect to your OpenClaw agent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Channel health grid (visible when connected)
        AnimatedVisibility(
            visible = state.isConnected && state.status != null,
            enter = fadeIn(
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 300f)
            ) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                initialOffsetY = { it / 4 }
            ),
            exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Session count summary
                state.status?.let { status ->
                    SessionSummaryRow(
                        activeSessions = status.activeSessions,
                        activeChannels = status.activeChannels,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Channel status list
                if (state.channels.isNotEmpty()) {
                    Text(
                        text = "Channels",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    ChannelStatusList(
                        channels = state.channels,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Row displaying session and channel count summary cards.
 */
@Composable
private fun SessionSummaryRow(
    activeSessions: Int,
    activeChannels: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        SummaryCard(
            label = "Sessions",
            value = activeSessions.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "Channels",
            value = activeChannels.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Summary card displaying a label and value.
 */
@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
