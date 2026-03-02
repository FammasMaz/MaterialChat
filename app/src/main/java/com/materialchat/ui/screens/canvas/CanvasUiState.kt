package com.materialchat.ui.screens.canvas

import com.materialchat.domain.model.CanvasArtifact

/**
 * UI state for the Smart Canvas screen.
 *
 * @property artifact The canvas artifact being displayed
 * @property viewMode Whether the user is viewing the rendered preview or the raw source code
 * @property renderedHtml The full HTML document to load in the WebView
 * @property refinementInput The current text in the refinement input field
 */
data class CanvasUiState(
    val artifact: CanvasArtifact,
    val viewMode: CanvasViewMode = CanvasViewMode.PREVIEW,
    val renderedHtml: String = "",
    val refinementInput: String = ""
)

/**
 * View modes for the Smart Canvas screen.
 */
enum class CanvasViewMode {
    /** Rendered preview in a WebView */
    PREVIEW,
    /** Raw source code text view */
    CODE
}
