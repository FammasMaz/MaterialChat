package com.materialchat.domain.model

/**
 * Represents an artifact that can be rendered in the Smart Canvas.
 *
 * @property type The type of artifact (HTML, MERMAID, SVG, LATEX)
 * @property code The raw source code of the artifact
 * @property language The detected language tag from the code block
 */
data class CanvasArtifact(
    val type: ArtifactType,
    val code: String,
    val language: String?
)

/**
 * Types of artifacts that can be rendered in the Smart Canvas.
 */
enum class ArtifactType {
    /** HTML/CSS/JS — rendered directly in WebView */
    HTML,
    /** Mermaid diagram syntax — rendered via Mermaid.js CDN */
    MERMAID,
    /** SVG markup — rendered directly */
    SVG,
    /** LaTeX/TeX math — rendered via KaTeX CDN */
    LATEX
}
