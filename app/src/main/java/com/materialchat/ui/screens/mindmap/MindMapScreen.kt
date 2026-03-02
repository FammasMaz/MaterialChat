package com.materialchat.ui.screens.mindmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.materialchat.ui.screens.mindmap.components.MindMapCanvas
import com.materialchat.ui.screens.mindmap.components.MindMapTopBar

/**
 * Full-screen Mind Map (Branch Visualizer) screen.
 *
 * Displays the conversation tree as an interactive, zoomable canvas
 * with nodes connected by Bezier curves. Follows Material 3 Expressive
 * design with spring-based animations and dynamic color tokens.
 *
 * @param onNavigateBack Callback for back navigation
 * @param onNavigateToConversation Callback to navigate to a specific conversation by ID
 * @param viewModel The ViewModel managing mind map state
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MindMapScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConversation: (String) -> Unit,
    viewModel: MindMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            MindMapTopBar(onNavigateBack = onNavigateBack)
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            when (val state = uiState) {
                is MindMapUiState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is MindMapUiState.Error -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is MindMapUiState.Success -> {
                    MindMapCanvas(
                        tree = state.tree,
                        selectedNodeId = state.selectedNodeId,
                        onNodeTap = { nodeId ->
                            viewModel.selectNode(nodeId)
                        },
                        onNodeDoubleTap = { nodeId ->
                            onNavigateToConversation(nodeId)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
