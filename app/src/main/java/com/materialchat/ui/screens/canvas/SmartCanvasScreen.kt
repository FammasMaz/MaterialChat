package com.materialchat.ui.screens.canvas

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.materialchat.ui.screens.canvas.components.CanvasTopBar
import com.materialchat.ui.screens.canvas.components.CanvasWebView
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Smart Canvas screen for rendering live artifacts (HTML, Mermaid, SVG, LaTeX).
 *
 * Displays a full-screen preview of the artifact with toggle between rendered preview
 * and raw source code. Includes a refinement input at the bottom for iterative editing.
 *
 * Uses M3 Expressive spring-based transitions between preview and code view modes
 * via [AnimatedContent] with spatial slide animations.
 *
 * @param onNavigateBack Callback to navigate back to the previous screen
 * @param viewModel The [SmartCanvasViewModel] managing this screen's state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCanvasScreen(
    onNavigateBack: () -> Unit,
    viewModel: SmartCanvasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val onSave = {
        try {
            val fileName = "MaterialChat_${uiState.artifact.type.name.lowercase()}_${System.currentTimeMillis()}.html"
            val htmlContent = uiState.renderedHtml

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/html")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MaterialChat")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(htmlContent.toByteArray())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MaterialChat")
                dir.mkdirs()
                java.io.File(dir, fileName).writeText(htmlContent)
            }
            Toast.makeText(context, "Saved to Downloads/MaterialChat/$fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            CanvasTopBar(
                artifactType = uiState.artifact.type,
                viewMode = uiState.viewMode,
                onNavigateBack = onNavigateBack,
                onToggleViewMode = viewModel::toggleViewMode,
                onShare = {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, uiState.artifact.code)
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(uiState.artifact.code))
                },
                onSave = onSave
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Main content area with animated transitions between preview and code
            AnimatedContent(
                targetState = uiState.viewMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                transitionSpec = {
                    val direction = if (targetState == CanvasViewMode.CODE) 1 else -1
                    (slideInHorizontally(
                        animationSpec = ExpressiveMotion.Spatial.container(),
                        initialOffsetX = { fullWidth -> direction * fullWidth / 4 }
                    ) + fadeIn(
                        animationSpec = ExpressiveMotion.Effects.alpha()
                    )).togetherWith(
                        slideOutHorizontally(
                            animationSpec = ExpressiveMotion.Spatial.container(),
                            targetOffsetX = { fullWidth -> -direction * fullWidth / 4 }
                        ) + fadeOut(
                            animationSpec = ExpressiveMotion.Effects.alpha()
                        )
                    ).using(
                        SizeTransform(clip = false)
                    )
                },
                label = "canvasViewModeTransition"
            ) { viewMode ->
                when (viewMode) {
                    CanvasViewMode.PREVIEW -> {
                        CanvasWebView(
                            html = uiState.renderedHtml,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    CanvasViewMode.CODE -> {
                        Text(
                            text = uiState.artifact.code,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

        }
    }
}
