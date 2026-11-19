package com.materialchat.ui.screens.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canvas templates expose the app colour scheme to rendered artifacts as CSS variables.
 * These tests pin the injected block's contents and its position in the cascade: after the
 * fixed-hex fallback (so it wins over `prefers-color-scheme`) and before the artifact body
 * (so an artifact's own `--bg` / `--fg` still wins).
 */
class ArtifactHtmlTemplatesTest {

    private val colors = CanvasThemeColors(
        surfaceArgb = 0xFF141218.toInt(),
        onSurfaceArgb = 0xFFE6E0E9.toInt(),
        primaryArgb = 0xFFD0BCFF.toInt(),
        outlineArgb = 0xFF938F99.toInt()
    )

    private val wrappers: List<Pair<String, (String, CanvasThemeColors?) -> String>> = listOf(
        "html" to ArtifactHtmlTemplates::wrapHtml,
        "mermaid" to ArtifactHtmlTemplates::wrapMermaid,
        "svg" to ArtifactHtmlTemplates::wrapSvg,
        "latex" to ArtifactHtmlTemplates::wrapLatex
    )

    @Test
    fun `theme block carries the four roles as opaque hex and re-points the template aliases`() {
        val block = ArtifactHtmlTemplates.themeStyleBlock(colors)

        assertTrue(block.startsWith("<style id=\"${ArtifactHtmlTemplates.THEME_STYLE_ID}\">:root{"))
        assertTrue(block.endsWith("}</style>"))
        assertTrue(block.contains("--md-surface:#141218;"))
        assertTrue(block.contains("--md-on-surface:#E6E0E9;"))
        assertTrue(block.contains("--md-primary:#D0BCFF;"))
        assertTrue(block.contains("--md-outline:#938F99;"))
        assertTrue(block.contains("--bg:var(--md-surface);"))
        assertTrue(block.contains("--fg:var(--md-on-surface);"))
        assertTrue(block.contains("--border:var(--md-outline);"))
        assertTrue(block.contains("--accent:var(--md-primary);"))
        // Alpha must never leak into CSS: the same colour with alpha 0 yields the same hex.
        assertEquals("#141218", ArtifactHtmlTemplates.cssHex(0x00141218))
        assertEquals("#000000", ArtifactHtmlTemplates.cssHex(0xFF000000.toInt()))
        assertEquals("#FFFFFF", ArtifactHtmlTemplates.cssHex(-1))
    }

    @Test
    fun `without colours every template keeps the fixed hex fallback and injects nothing`() {
        assertEquals("", ArtifactHtmlTemplates.themeStyleBlock(null))
        wrappers.forEach { (name, wrap) ->
            val html = wrap("<p>x</p>", null)
            assertFalse("$name must not inject a theme block", html.contains(ArtifactHtmlTemplates.THEME_STYLE_ID))
            assertTrue("$name keeps the system fallback", html.contains("@media (prefers-color-scheme: dark)"))
            assertTrue("$name keeps the light fallback", html.contains("--bg: #FFFBFE;"))
            // Default argument path is the same as passing null explicitly.
            assertEquals(html, wrap("<p>x</p>", null))
        }
        assertEquals(ArtifactHtmlTemplates.wrapHtml("<p>x</p>"), ArtifactHtmlTemplates.wrapHtml("<p>x</p>", null))
    }

    @Test
    fun `with colours the block sits after the fixed style and before the body in every template`() {
        wrappers.forEach { (name, wrap) ->
            val html = wrap("<p>x</p>", colors)
            val fixedStyleEnd = html.indexOf("</style>")
            val themeBlock = html.indexOf("id=\"${ArtifactHtmlTemplates.THEME_STYLE_ID}\"")
            val headEnd = html.indexOf("</head>")
            val body = html.indexOf("<body>")
            assertTrue("$name has a fixed style", fixedStyleEnd >= 0)
            assertTrue("$name injects the theme block", themeBlock >= 0)
            assertTrue("$name: theme block after the fixed style", themeBlock > fixedStyleEnd)
            assertTrue("$name: theme block inside head", themeBlock < headEnd && headEnd < body)
            assertEquals("$name: exactly one theme block", 1, Regex(ArtifactHtmlTemplates.THEME_STYLE_ID).findAll(html).count())
            assertTrue("$name still has the system fallback", html.contains("@media (prefers-color-scheme: dark)"))
        }
    }

    @Test
    fun `an artifact that defines its own variables is placed after the theme block so it wins`() {
        val own = "<style>:root{--bg:#FF0000;--fg:#00FF00;}</style><p>mine</p>"
        val html = ArtifactHtmlTemplates.wrapHtml(own, colors)
        val themeBlock = html.indexOf(ArtifactHtmlTemplates.THEME_STYLE_ID)
        val ownBlock = html.indexOf("--bg:#FF0000")
        assertTrue(themeBlock >= 0 && ownBlock > themeBlock)
        // The template's table and link rules read the aliases, never a literal hex.
        assertTrue(html.contains("border: 1px solid var(--border);"))
        assertTrue(html.contains("a { color: var(--accent); }"))
    }

    @Test
    fun `latex escaping is unchanged by the theme block`() {
        val html = ArtifactHtmlTemplates.wrapLatex("\\frac{a}{b} \"q\"\nx", colors)
        assertTrue(html.contains("katex.render(\"\\\\frac{a}{b} \\\"q\\\"\\nx\""))
        assertTrue(html.contains(ArtifactHtmlTemplates.THEME_STYLE_ID))
    }
}
