package com.materialchat.ui.screens.canvas

/**
 * Provides HTML document templates for rendering different artifact types in a WebView.
 *
 * Each template wraps the raw artifact code in a full HTML5 document with:
 * - Responsive viewport meta tag
 * - Dark/light theme support via CSS `prefers-color-scheme` media queries
 * - Appropriate CDN libraries (Mermaid.js, KaTeX) where needed
 */
object ArtifactHtmlTemplates {

    /**
     * Wraps raw HTML code in a basic HTML5 boilerplate.
     *
     * The code is inserted directly into the document body.
     * Includes responsive viewport and adaptive dark/light theme styling.
     *
     * @param code The raw HTML content to wrap
     * @return A full HTML5 document string
     */
    fun wrapHtml(code: String): String = """
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
                }
                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg: #1C1B1F;
                        --fg: #E6E1E5;
                        --surface: #2B2930;
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
                    border: 1px solid var(--surface);
                    padding: 8px 12px;
                    text-align: left;
                }
            </style>
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
     * @return A full HTML5 document string that renders the diagram
     */
    fun wrapMermaid(code: String): String = """
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
     * @return A full HTML5 document string that renders the SVG
     */
    fun wrapSvg(code: String): String = """
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
     * @return A full HTML5 document string that renders the math
     */
    fun wrapLatex(code: String): String {
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
