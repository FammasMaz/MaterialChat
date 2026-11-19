package com.materialchat.ui.screens.canvas

/**
 * The four Material colour roles a rendered artifact borrows from the app theme, as ARGB ints.
 *
 * Pure data so the templates (and their JVM tests) never touch Compose. Alpha is ignored when
 * the colours are written to CSS; every role here is an opaque surface or content colour.
 */
data class CanvasThemeColors(
    val surfaceArgb: Int,
    val onSurfaceArgb: Int,
    val primaryArgb: Int,
    val outlineArgb: Int
)

/**
 * Provides HTML document templates for rendering different artifact types in a WebView.
 *
 * Each template wraps the raw artifact code in a full HTML5 document with:
 * - Responsive viewport meta tag
 * - A fixed-hex `:root` block with `prefers-color-scheme` fallbacks, used when the caller
 *   supplies no theme colours (and when the document is opened outside the app)
 * - An optional second `:root` block, see [themeStyleBlock], that re-points the same variables
 *   at the app's current colour scheme so the artifact matches the surrounding screen in every
 *   palette (dynamic, static, pitch-black) rather than only the system light/dark switch
 * - Appropriate CDN libraries (Mermaid.js, KaTeX) where needed
 *
 * Artifact code that declares its own `--bg` / `--fg` (or any `--md-*`) wins over both blocks:
 * it sits later in the document, so the cascade keeps the author's values.
 */
object ArtifactHtmlTemplates {

    /** Element id of the injected theme block, so tests and tooling can find it. */
    const val THEME_STYLE_ID = "materialchat-theme"

    /**
     * The `<style>` element carrying the app colour scheme as CSS variables, or an empty string
     * when [colors] is null so the fixed-hex fallback in each template stands alone.
     *
     * Emits the raw roles (`--md-surface`, `--md-on-surface`, `--md-primary`, `--md-outline`)
     * and re-points the template aliases (`--bg`, `--fg`, `--border`, `--accent`) at them.
     * The block is placed after the template's own `<style>` in `<head>`, so it overrides the
     * fixed hex values and the `prefers-color-scheme` media query, and before `<body>`, so
     * any variables the artifact itself declares still override it.
     */
    fun themeStyleBlock(colors: CanvasThemeColors?): String {
        if (colors == null) return ""
        return "<style id=\"$THEME_STYLE_ID\">:root{" +
            "--md-surface:${cssHex(colors.surfaceArgb)};" +
            "--md-on-surface:${cssHex(colors.onSurfaceArgb)};" +
            "--md-primary:${cssHex(colors.primaryArgb)};" +
            "--md-outline:${cssHex(colors.outlineArgb)};" +
            "--bg:var(--md-surface);" +
            "--fg:var(--md-on-surface);" +
            "--border:var(--md-outline);" +
            "--accent:var(--md-primary);" +
            "}</style>"
    }

    /** `#RRGGBB` from an ARGB int; alpha is dropped. */
    internal fun cssHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /**
     * Wraps raw HTML code in a basic HTML5 boilerplate.
     *
     * The code is inserted directly into the document body.
     * Includes responsive viewport and adaptive dark/light theme styling.
     *
     * @param code The raw HTML content to wrap
     * @param colors The app colour scheme to expose as CSS variables, or null for the fixed fallback
     * @return A full HTML5 document string
     */
    fun wrapHtml(code: String, colors: CanvasThemeColors? = null): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <style>
                :root {
                    --bg: #FFFBFE;
                    --fg: #1C1B1F;
                    --surface: #F4EFF4;
                    --border: #F4EFF4;
                    --accent: #6750A4;
                }
                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg: #1C1B1F;
                        --fg: #E6E1E5;
                        --surface: #2B2930;
                        --border: #2B2930;
                        --accent: #D0BCFF;
                    }
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, 'Roboto', 'Segoe UI', sans-serif;
                    background-color: var(--bg);
                    color: var(--fg);
                    padding: 16px;
                    line-height: 1.6;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                }
                a { color: var(--accent); }
                img, video, canvas, svg {
                    max-width: 100%;
                    height: auto;
                }
                table {
                    border-collapse: collapse;
                    width: 100%;
                    overflow-x: auto;
                    display: block;
                }
                th, td {
                    border: 1px solid var(--border);
                    padding: 8px 12px;
                    text-align: left;
                }
            </style>
            ${themeStyleBlock(colors)}
        </head>
        <body>
            $code
        </body>
        </html>
    """.trimIndent()

    /**
     * Wraps Mermaid diagram syntax in an HTML document with Mermaid.js CDN.
     *
     * The Mermaid library is loaded from jsDelivr and initialized with theme
     * detection based on the system color scheme preference.
     *
     * @param code The raw Mermaid diagram syntax
     * @param colors The app colour scheme to expose as CSS variables, or null for the fixed fallback
     * @return A full HTML5 document string that renders the diagram
     */
    fun wrapMermaid(code: String, colors: CanvasThemeColors? = null): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <style>
                :root {
                    --bg: #FFFBFE;
                    --fg: #1C1B1F;
                }
                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg: #1C1B1F;
                        --fg: #E6E1E5;
                    }
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, 'Roboto', 'Segoe UI', sans-serif;
                    background-color: var(--bg);
                    color: var(--fg);
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
                    padding: 16px;
                }
                .mermaid {
                    width: 100%;
                    max-width: 100%;
                    overflow-x: auto;
                }
                .mermaid svg {
                    max-width: 100%;
                    height: auto;
                }
            </style>
            ${themeStyleBlock(colors)}
        </head>
        <body>
            <div class="mermaid">
                $code
            </div>
            <script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script>
            <script>
                const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                mermaid.initialize({
                    startOnLoad: true,
                    theme: isDark ? 'dark' : 'default',
                    securityLevel: 'loose',
                    fontFamily: '-apple-system, Roboto, Segoe UI, sans-serif'
                });
            </script>
        </body>
        </html>
    """.trimIndent()

    /**
     * Wraps SVG markup in an HTML document with responsive scaling.
     *
     * The SVG is centered in the viewport and constrained to fit within
     * the available width while maintaining its aspect ratio.
     *
     * @param code The raw SVG markup
     * @param colors The app colour scheme to expose as CSS variables, or null for the fixed fallback
     * @return A full HTML5 document string that renders the SVG
     */
    fun wrapSvg(code: String, colors: CanvasThemeColors? = null): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <style>
                :root {
                    --bg: #FFFBFE;
                    --fg: #1C1B1F;
                }
                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg: #1C1B1F;
                        --fg: #E6E1E5;
                    }
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, 'Roboto', 'Segoe UI', sans-serif;
                    background-color: var(--bg);
                    color: var(--fg);
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
                    padding: 16px;
                }
                .svg-container {
                    width: 100%;
                    max-width: 100%;
                    overflow-x: auto;
                }
                .svg-container svg {
                    max-width: 100%;
                    height: auto;
                    display: block;
                    margin: 0 auto;
                }
            </style>
            ${themeStyleBlock(colors)}
        </head>
        <body>
            <div class="svg-container">
                $code
            </div>
        </body>
        </html>
    """.trimIndent()

    /**
     * Wraps LaTeX/TeX math expressions in an HTML document with KaTeX CDN.
     *
     * KaTeX CSS and JS are loaded from jsDelivr. The code is rendered using
     * `katex.render()` with display mode enabled for block-level math.
     *
     * @param code The raw LaTeX/TeX math expression
     * @param colors The app colour scheme to expose as CSS variables, or null for the fixed fallback
     * @return A full HTML5 document string that renders the math
     */
    fun wrapLatex(code: String, colors: CanvasThemeColors? = null): String {
        // Escape backslashes and quotes for safe embedding in a JS string literal
        val escapedCode = code
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
                <style>
                    :root {
                        --bg: #FFFBFE;
                        --fg: #1C1B1F;
                    }
                    @media (prefers-color-scheme: dark) {
                        :root {
                            --bg: #1C1B1F;
                            --fg: #E6E1E5;
                        }
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, 'Roboto', 'Segoe UI', sans-serif;
                        background-color: var(--bg);
                        color: var(--fg);
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        padding: 16px;
                    }
                    #katex-output {
                        width: 100%;
                        max-width: 100%;
                        overflow-x: auto;
                        text-align: center;
                        font-size: 1.4em;
                    }
                    .katex { color: var(--fg); }
                    .katex-display { overflow-x: auto; overflow-y: hidden; padding: 4px 0; }
                </style>
                ${themeStyleBlock(colors)}
            </head>
            <body>
                <div id="katex-output"></div>
                <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
                <script>
                    try {
                        katex.render("$escapedCode", document.getElementById('katex-output'), {
                            displayMode: true,
                            throwOnError: false,
                            trust: true,
                            strict: false
                        });
                    } catch (e) {
                        document.getElementById('katex-output').textContent = e.message;
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
