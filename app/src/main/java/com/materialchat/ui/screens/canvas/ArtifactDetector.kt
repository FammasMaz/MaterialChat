package com.materialchat.ui.screens.canvas

import com.materialchat.domain.model.ArtifactType
import com.materialchat.domain.model.CanvasArtifact

/**
 * Detects renderable artifacts from code blocks based on language tags and content heuristics.
 *
 * Supports detection of HTML, Mermaid diagrams, SVG markup, and LaTeX/TeX math expressions.
 * Used to determine whether a code block in a chat message should offer a "Smart Canvas" preview.
 */
object ArtifactDetector {

    /**
     * Attempts to detect a renderable artifact from the given code and optional language tag.
     *
     * Detection is performed in two passes:
     * 1. **Language tag matching** - Checks the explicit language tag from the code fence (e.g., ```html)
     * 2. **Content heuristics** - Falls back to inspecting the code content for known patterns
     *
     * @param code The raw source code from the code block
     * @param language The language tag from the code fence, if any (e.g., "html", "mermaid")
     * @return A [CanvasArtifact] if the code is a renderable artifact, or null otherwise
     */
    fun detect(code: String, language: String?): CanvasArtifact? {
        // Pass 1: Match by explicit language tag
        val typeByLanguage = language?.lowercase()?.trim()?.let { lang ->
            when (lang) {
                "html" -> ArtifactType.HTML
                "mermaid" -> ArtifactType.MERMAID
                "svg" -> ArtifactType.SVG
                "latex", "tex" -> ArtifactType.LATEX
                else -> null
            }
        }

        if (typeByLanguage != null) {
            return CanvasArtifact(
                type = typeByLanguage,
                code = code,
                language = language
            )
        }

        // Pass 2: Content heuristics — inspect the code body for known patterns
        val trimmed = code.trimStart()
        val typeByContent = when {
            // HTML detection
            trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true) -> ArtifactType.HTML

            // SVG detection
            trimmed.startsWith("<svg", ignoreCase = true) -> ArtifactType.SVG

            // Mermaid diagram detection
            trimmed.startsWith("graph ") ||
                trimmed.startsWith("flowchart ") ||
                trimmed.startsWith("sequenceDiagram") ||
                trimmed.startsWith("classDiagram") -> ArtifactType.MERMAID

            // LaTeX/TeX detection
            trimmed.contains("\\begin{") ||
                trimmed.contains("\\frac{") -> ArtifactType.LATEX

            else -> null
        }

        return typeByContent?.let { type ->
            CanvasArtifact(
                type = type,
                code = code,
                language = language
            )
        }
    }
}
