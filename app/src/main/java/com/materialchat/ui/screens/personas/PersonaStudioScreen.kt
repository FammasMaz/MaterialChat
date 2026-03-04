package com.materialchat.ui.screens.personas

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import com.materialchat.ui.components.ExpressiveButton
import com.materialchat.ui.components.ExpressiveButtonStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialchat.ui.screens.personas.components.PersonaCard
import com.materialchat.ui.screens.personas.components.PersonaEditorSheet
import kotlinx.coroutines.launch
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.text.font.FontWeight

/**
 * Persona Studio screen displaying a grid of AI personas with create/edit/delete capabilities.
 *
 * Uses a 2-column LazyVerticalGrid with M3 Expressive card styling.
 *
 * @param onNavigateBack Callback to navigate back
 * @param viewModel The ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaStudioScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonaStudioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberHapticFeedback()

    // Handle one-time events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PersonaStudioEvent.ShowSnackbar -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Persona Studio") },
                navigationIcon = {
                    IconButton(onClick = { haptics.perform(HapticPattern.CLICK); onNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            com.materialchat.ui.components.ExpressiveFab(
                onClick = { haptics.perform(HapticPattern.CLICK); viewModel.showCreateEditor() },
                icon = Icons.Filled.Add,
                contentDescription = "Create",
                expanded = true,
                text = "New Persona"
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { paddingValues ->
        when (val state = uiState) {
            is PersonaStudioUiState.Loading -> {
                PersonaStudioLoadingContent(paddingValues = paddingValues)
            }

            is PersonaStudioUiState.Error -> {
                PersonaStudioErrorContent(
                    message = state.message,
                    paddingValues = paddingValues,
                    onRetry = { /* ViewModel re-observes automatically */ },
                    onNavigateBack = onNavigateBack
                )
            }

            is PersonaStudioUiState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp // Room for FAB
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PersonaOnboardingCard()
                    }
                    items(
                        items = state.personas,
                        key = { it.id }
                    ) { persona ->
                        PersonaCard(
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
                            persona = persona,
                            onClick = { viewModel.showEditEditor(persona) },
                            onDelete = if (!persona.isBuiltin) {
                                { viewModel.deletePersona(persona.id) }
                            } else {
                                null
                            }
                        )
                    }
                }

                // Editor bottom sheet
                if (state.showEditorSheet) {
                    PersonaEditorSheet(
                        persona = state.editingPersona,
                        onDismiss = { viewModel.hideEditor() },
                        onSave = { viewModel.savePersona(it) }
                    )
                }
            }
        }
    }
}

/**
 * Loading state content for Persona Studio.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonaStudioLoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Error state content for Persona Studio.
 */
@Composable
private fun PersonaStudioErrorContent(
    message: String,
    paddingValues: PaddingValues,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val haptics = rememberHapticFeedback()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
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

        ExpressiveButton(onClick = { onNavigateBack() }, text = "Go Back", style = ExpressiveButtonStyle.Text)
    }
}

/**
 * Onboarding tip card explaining how to use personas.
 */
@Composable
private fun PersonaOnboardingCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "How to use Personas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Create a persona below, then select it when starting a new chat. Your persona will shape how the AI responds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
