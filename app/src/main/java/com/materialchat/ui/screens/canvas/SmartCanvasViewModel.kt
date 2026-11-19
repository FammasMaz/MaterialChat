package com.materialchat.ui.screens.canvas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.materialchat.domain.model.ArtifactType
import com.materialchat.domain.model.CanvasArtifact
import com.materialchat.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URLDecoder
import javax.inject.Inject

/**
 * ViewModel for the Smart Canvas screen.
 *
 * Parses the artifact data passed via navigation arguments, generates the
 * rendered HTML using [ArtifactHtmlTemplates], and manages view mode toggling
 * and refinement input state.
 *
 * The artifact data is passed as a URL-encoded string in the format:
 * `type:::language:::code`
 *
 * @param savedStateHandle Provides access to navigation arguments
 */
@HiltViewModel
class SmartCanvasViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState: MutableStateFlow<CanvasUiState>

    /** Observable UI state for the Smart Canvas screen. */
    val uiState: StateFlow<CanvasUiState>

    /**
     * The app colour scheme last pushed by the screen, exposed to the rendered document as CSS
     * variables. Null until the screen reports it, so the first render uses the templates'
     * fixed-hex fallback and is re-rendered as soon as the colours arrive.
     */
    private var themeColors: CanvasThemeColors? = null

    init {
        val rawData = savedStateHandle.get<String>(Screen.Canvas.ARG_ARTIFACT_DATA) ?: ""
        val decoded = try {
            URLDecoder.decode(rawData, "UTF-8")
        } catch (_: Exception) {
            rawData
        }

        val artifact = parseArtifactData(decoded)
        val renderedHtml = generateHtml(artifact, themeColors)

        _uiState = MutableStateFlow(
            CanvasUiState(
                artifact = artifact,
                renderedHtml = renderedHtml
            )
        )
        uiState = _uiState.asStateFlow()
    }

    /**
     * Toggles between preview and code view modes.
     */
    fun toggleViewMode() {
        _uiState.update { state ->
            state.copy(
                viewMode = when (state.viewMode) {
                    CanvasViewMode.PREVIEW -> CanvasViewMode.CODE
                    CanvasViewMode.CODE -> CanvasViewMode.PREVIEW
                }
            )
        }
    }

    /**
     * Applies the app colour scheme to the rendered document. Re-renders only when the
     * colours actually change (palette switch, dark/light flip, dynamic wallpaper change).
     *
     * @param colors The current scheme's surface / onSurface / primary / outline roles
     */
    fun updateThemeColors(colors: CanvasThemeColors) {
        if (colors == themeColors) return
        themeColors = colors
        _uiState.update { state ->
            state.copy(renderedHtml = generateHtml(state.artifact, colors))
        }
    }

    /**
     * Updates the refinement input text.
     *
     * @param text The new input text
     */
    fun updateRefinementInput(text: String) {
        _uiState.update { it.copy(refinementInput = text) }
    }

    /**
     * Parses the `type:::language:::code` formatted artifact data string.
     *
     * Falls back to treating the entire string as HTML code if parsing fails.
     */
    private fun parseArtifactData(data: String): CanvasArtifact {
        val parts = data.split(":::", limit = 3)
        return if (parts.size == 3) {
            val type = try {
                ArtifactType.valueOf(parts[0].uppercase())
            } catch (_: IllegalArgumentException) {
                ArtifactType.HTML
            }
            val language = parts[1].ifBlank { null }
            val code = parts[2]
            CanvasArtifact(type = type, code = code, language = language)
        } else {
            // Fallback: treat entire data as HTML code
            CanvasArtifact(type = ArtifactType.HTML, code = data, language = "html")
        }
    }

    /**
     * Generates the full HTML document for the given artifact using [ArtifactHtmlTemplates].
     */
    private fun generateHtml(artifact: CanvasArtifact, colors: CanvasThemeColors?): String {
        return when (artifact.type) {
            ArtifactType.HTML -> ArtifactHtmlTemplates.wrapHtml(artifact.code, colors)
            ArtifactType.MERMAID -> ArtifactHtmlTemplates.wrapMermaid(artifact.code, colors)
            ArtifactType.SVG -> ArtifactHtmlTemplates.wrapSvg(artifact.code, colors)
            ArtifactType.LATEX -> ArtifactHtmlTemplates.wrapLatex(artifact.code, colors)
        }
    }
}
