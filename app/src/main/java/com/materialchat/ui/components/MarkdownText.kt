package com.materialchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.materialchat.domain.model.CanvasArtifact
import com.materialchat.ui.screens.canvas.ArtifactDetector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import com.materialchat.ui.theme.CodeBlockBackgroundDark
import com.materialchat.ui.theme.CodeBlockBackgroundLight

/**
 * A composable that renders Markdown text with proper formatting.
 *
 * Supports:
 * - Bold (**text** or __text__)
 * - Italic (*text* or _text_)
 * - Code inline (`code`)
 * - Code blocks (```language\ncode\n```)
 * - Links ([text](url))
 * - Headers (#, ##, ###, ####)
 * - Bullet lists (- or *)
 * - Numbered lists (1. 2. 3.)
 *
 * @param markdown The markdown text to render
 * @param modifier Modifier for the container
 * @param textColor The base text color
 * @param style The base text style
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    isStreaming: Boolean = false,
    onOpenCanvas: ((CanvasArtifact) -> Unit)? = null
) {
    val isDarkTheme = isSystemInDarkTheme()
    val codeBlockBackground = if (isDarkTheme) CodeBlockBackgroundDark else CodeBlockBackgroundLight
    val codeTextColor = if (isDarkTheme) Color(0xFFD4D4D4) else Color(0xFF1E1E1E)
    val linkColor = MaterialTheme.colorScheme.primary

    val parsedContent = remember(markdown, textColor, codeTextColor, linkColor) {
        parseMarkdown(
            markdown = markdown,
            baseColor = textColor,
            codeColor = codeTextColor,
            linkColor = linkColor
        )
    }

    SelectionContainer {
        MarkdownContent(
            content = parsedContent,
            modifier = modifier,
            style = style,
            codeBlockBackground = codeBlockBackground,
            isStreaming = isStreaming,
            onOpenCanvas = onOpenCanvas
        )
    }
}

/**
 * Internal representation of parsed markdown content.
 */
private sealed class MarkdownElement {
    data class TextBlock(val content: ParsedInlineContent) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String?) : MarkdownElement()
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlignment>
    ) : MarkdownElement()
    data object HorizontalRule : MarkdownElement()
    data class MathBlock(val expression: String, val isDisplay: Boolean) : MarkdownElement()
    data class Blockquote(val content: ParsedInlineContent) : MarkdownElement()
}

private enum class TableAlignment { LEFT, CENTER, RIGHT }

private data class ParsedInlineContent(
    val text: AnnotatedString,
    val inlineMath: List<InlineMathContent> = emptyList()
)

private data class InlineMathContent(
    val id: String,
    val expression: String
)

/**
 * Renders the parsed markdown content with proper layout for code blocks.
 */
@Composable
private fun MarkdownContent(
    content: List<MarkdownElement>,
    modifier: Modifier = Modifier,
    style: TextStyle,
    codeBlockBackground: Color,
    isStreaming: Boolean = false,
    onOpenCanvas: ((CanvasArtifact) -> Unit)? = null
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        content.forEach { element ->
            when (element) {
                is MarkdownElement.TextBlock -> {
                    if (element.content.text.isNotEmpty()) {
                        // Don't justify list items — preserve bullet/number indentation
                        val hasListItems = element.content.text.text.lines().any { line ->
                            val t = line.trimStart()
                            t.startsWith("• ") || t.matches(Regex("\\d+\\.\\s.*"))
                        }
                        val effectiveStyle = if (hasListItems && style.textAlign == TextAlign.Justify) {
                            style.copy(textAlign = TextAlign.Start)
                        } else {
                            style
                        }
                        MarkdownInlineText(
                            content = element.content,
                            style = effectiveStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is MarkdownElement.CodeBlock -> {
                    CodeBlockView(
                        code = element.code,
                        language = element.language,
                        backgroundColor = codeBlockBackground,
                        onOpenCanvas = onOpenCanvas
                    )
                }
                is MarkdownElement.Table -> {
                    TableView(
                        headers = element.headers,
                        rows = element.rows,
                        alignments = element.alignments,
                        baseColor = style.color.takeIf { it != Color.Unspecified }
                            ?: MaterialTheme.colorScheme.onSurface,
                        codeColor = if (isSystemInDarkTheme()) Color(0xFFD4D4D4) else Color(0xFF1E1E1E),
                        linkColor = MaterialTheme.colorScheme.primary
                    )
                }
                is MarkdownElement.HorizontalRule -> {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                is MarkdownElement.MathBlock -> {
                    MathBlockView(
                        expression = element.expression,
                        backgroundColor = codeBlockBackground,
                        isStreaming = isStreaming
                    )
                }
                is MarkdownElement.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MarkdownInlineText(
                            content = element.content,
                            style = style.copy(fontStyle = FontStyle.Italic),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    content: ParsedInlineContent,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    if (content.inlineMath.isEmpty()) {
        Text(
            text = content.text,
            style = style,
            modifier = modifier
        )
        return
    }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val resolvedColor = style.color.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.onSurface

    val inlineContent = remember(content.inlineMath, style, resolvedColor, density) {
        content.inlineMath.associate { math ->
            math.id to InlineTextContent(
                placeholder = estimateInlineMathPlaceholder(
                    expression = math.expression,
                    style = style,
                    textMeasurer = textMeasurer,
                    density = density
                )
            ) {
                InlineMathView(
                    expression = math.expression,
                    textColor = resolvedColor,
                    style = style
                )
            }
        }
    }

    Text(
        text = content.text,
        style = style,
        inlineContent = inlineContent,
        modifier = modifier
    )
}

private fun estimateInlineMathPlaceholder(
    expression: String,
    style: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density
): Placeholder {
    val sample = inlineMathPreview(expression)
    val measuredWidthPx = textMeasurer.measure(
        text = sample,
        style = style.copy(fontStyle = FontStyle.Italic, color = Color.Unspecified)
    ).size.width
    val fontSize = style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp
    val widthPx = measuredWidthPx.toFloat() * inlineMathWidthFactor(expression)

    val placeholderWidth = with(density) { (widthPx.toDp() + 2.dp).toSp() }
    val lineHeight = style.lineHeight.takeIf { it != TextUnit.Unspecified } ?: (fontSize * 1.35f)
    val heightMultiplier = inlineMathHeightMultiplier(expression)

    return Placeholder(
        width = if (placeholderWidth.value > 0f) placeholderWidth else 1.sp,
        height = lineHeight * heightMultiplier,
        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
    )
}

private fun inlineMathPreview(expression: String): String {
    return expression
        .replace(Regex("""\\(text|mathrm|operatorname)\{([^}]*)\}"""), "$2")
        .replace(Regex("""\\[a-zA-Z]+"""), "x")
        .replace("{", "")
        .replace("}", "")
        .replace("^", "")
        .replace("_", "")
        .ifBlank { "x" }
}

private fun inlineMathHeightMultiplier(expression: String): Float {
    return when {
        expression.contains("\\frac") || expression.contains("\\dfrac") -> 1.35f
        expression.contains("\\sum") || expression.contains("\\int") || expression.contains("\\prod") -> 1.25f
        expression.contains("\\sqrt") || expression.contains("\\binom") -> 1.15f
        else -> 1.0f
    }
}

private fun inlineMathWidthFactor(expression: String): Float {
    return when {
        expression.contains("\\frac") || expression.contains("\\dfrac") -> 1.1f
        expression.contains("\\text") || expression.contains("\\mathrm") || expression.contains("\\operatorname") -> 1.0f
        expression.contains("\\sum") || expression.contains("\\int") || expression.contains("\\prod") -> 1.15f
        else -> 1.05f
    }
}

/**
 * Renders a code block with syntax highlighting styling.
 */
@Composable
private fun CodeBlockView(
    code: String,
    language: String?,
    backgroundColor: Color,
    onOpenCanvas: ((CanvasArtifact) -> Unit)? = null
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color(0xFFD4D4D4) else Color(0xFF1E1E1E)
    val keywordColor = if (isDarkTheme) Color(0xFF569CD6) else Color(0xFF0000FF)
    val stringColor = if (isDarkTheme) Color(0xFFCE9178) else Color(0xFFA31515)
    val commentColor = if (isDarkTheme) Color(0xFF6A9955) else Color(0xFF008000)
    val numberColor = if (isDarkTheme) Color(0xFFB5CEA8) else Color(0xFF098658)

    // Detect if this code block is a renderable artifact
    val artifact = remember(code, language) {
        ArtifactDetector.detect(code, language)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        val highlightedCode = remember(code, language) {
            highlightCode(
                code = code,
                language = language,
                baseColor = textColor,
                keywordColor = keywordColor,
                stringColor = stringColor,
                commentColor = commentColor,
                numberColor = numberColor
            )
        }

        Text(
            text = highlightedCode,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            ),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
                .then(
                    if (artifact != null && onOpenCanvas != null) {
                        Modifier.padding(end = 40.dp)
                    } else Modifier
                )
        )

        // "Open in Canvas" button for eligible code blocks
        if (artifact != null && onOpenCanvas != null) {
            IconButton(
                onClick = { onOpenCanvas(artifact) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "Open in Canvas",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Renders a display math expression using a compact KaTeX WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InlineMathView(
    expression: String,
    textColor: Color,
    style: TextStyle
) {
    val html = remember(expression, textColor, style.fontSize, style.lineHeight) {
        buildInlineMathHtml(
            expression = expression,
            fgColor = textColor.toCssColor(),
            fontScale = 1.0f
        )
    }

    AndroidView(
        factory = { context -> createMathWebView(context) },
        update = { webView -> webView.loadMathHtmlIfNeeded(html) },
        modifier = Modifier.fillMaxSize()
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MathBlockView(
    expression: String,
    backgroundColor: Color,
    isStreaming: Boolean
) {
    val isDarkTheme = isSystemInDarkTheme()
    val fgHex = if (isDarkTheme) "#E6E1E5" else "#1C1B1F"

    val html = remember(expression, isDarkTheme) {
        buildMathHtml(
            expression = expression,
            fgColor = fgHex,
            isDisplay = true,
            fontScale = 1.2f,
            horizontalPaddingPx = 12,
            verticalPaddingPx = 4
        )
    }

    val density = LocalDensity.current
    val fallbackHeightPx = remember(expression, density, isStreaming) {
        estimateBlockMathFallbackHeightPx(expression, density, isStreaming)
    }
    val settledMinHeightPx = remember(expression, density) {
        estimateBlockMathSettledMinHeightPx(expression, density)
    }
    var webViewHeight by remember(expression, isStreaming) { mutableIntStateOf(fallbackHeightPx) }
    var hasMeasuredHeight by remember(expression, isStreaming) { androidx.compose.runtime.mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor.copy(alpha = 0.5f))
    ) {
        AndroidView(
            factory = { context ->
                createMathWebView(context) { view ->
                    fun updateHeight() {
                        measureMathHeight(view) { measuredHeight ->
                            if (measuredHeight > 0) {
                                hasMeasuredHeight = true
                                webViewHeight = if (isStreaming) {
                                    maxOf(measuredHeight, fallbackHeightPx)
                                } else {
                                    maxOf(measuredHeight, settledMinHeightPx)
                                }
                            }
                        }
                    }
                    updateHeight()
                    view.postDelayed({ updateHeight() }, 48)
                    view.postDelayed({ updateHeight() }, 160)
                }
            },
            update = { webView ->
                if (!hasMeasuredHeight && webViewHeight < fallbackHeightPx) {
                    webViewHeight = fallbackHeightPx
                }
                webView.loadMathHtmlIfNeeded(html)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { webViewHeight.toDp() })
        )
    }
}

private fun estimateBlockMathFallbackHeightPx(
    expression: String,
    density: androidx.compose.ui.unit.Density,
    isStreaming: Boolean
): Int {
    val baseHeight = when {
        expression.contains("\\begin{align") || expression.contains("\\begin{gather") -> 60.dp
        expression.contains("\\frac") || expression.contains("\\dfrac") -> 48.dp
        expression.contains("\\sum") || expression.contains("\\int") || expression.contains("\\prod") -> 44.dp
        expression.contains("\\sqrt") || expression.contains("\\binom") -> 40.dp
        expression.contains('\n') -> 52.dp
        else -> 32.dp
    }

    val adjustedHeight = if (isStreaming) baseHeight + 8.dp else baseHeight

    return with(density) { adjustedHeight.roundToPx() }
}

private fun estimateBlockMathSettledMinHeightPx(
    expression: String,
    density: androidx.compose.ui.unit.Density
): Int {
    val minHeight = when {
        expression.contains("\\begin{align") || expression.contains("\\begin{gather") -> 44.dp
        expression.contains("\\frac") || expression.contains("\\dfrac") -> 36.dp
        expression.contains("\\sum") || expression.contains("\\int") || expression.contains("\\prod") -> 32.dp
        expression.contains('\n') -> 40.dp
        else -> 24.dp
    }

    return with(density) { minHeight.roundToPx() }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createMathWebView(
    context: android.content.Context,
    onPageFinished: ((WebView) -> Unit)? = null
): WebView {
    return WebView(context).apply {
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (view != null) {
                    onPageFinished?.invoke(view)
                }
            }
        }
        webChromeClient = WebChromeClient()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }
}

private fun WebView.loadMathHtmlIfNeeded(html: String) {
    if (tag != html) {
        tag = html
        loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
    }
}

private fun Color.toCssColor(): String {
    return String.format("#%06X", 0xFFFFFF and toArgb())
}

private fun measureMathHeight(webView: WebView, onMeasured: (Int) -> Unit) {
    webView.evaluateJavascript(
        """(function() {
            var node = document.getElementById('math');
            if (!node) return 0;
            var rect = node.getBoundingClientRect();
            var bodyStyle = window.getComputedStyle(document.body);
            var padTop = parseFloat(bodyStyle.paddingTop || '0') || 0;
            var padBottom = parseFloat(bodyStyle.paddingBottom || '0') || 0;
            return Math.ceil(rect.height + padTop + padBottom);
        })()"""
    ) { heightStr ->
        val h = heightStr
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.toFloatOrNull()
        if (h != null && h > 0f) {
            onMeasured((h * webView.context.resources.displayMetrics.density).toInt())
        }
    }
}

private fun buildMathHtml(
    expression: String,
    fgColor: String,
    isDisplay: Boolean,
    fontScale: Float,
    horizontalPaddingPx: Int,
    verticalPaddingPx: Int
): String {
    val escaped = expression
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

    return """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body { width: 100%; ${if (!isDisplay) "height: 100%;" else ""} }
            body {
                background: transparent;
                color: $fgColor;
                padding: ${verticalPaddingPx}px ${horizontalPaddingPx}px;
                overflow: hidden;
                ${if (isDisplay) "text-align: center;" else "display: flex; align-items: flex-end;"}
            }
            .katex { color: $fgColor; font-size: ${fontScale}em; }
            .katex-display { margin: 0; overflow-x: auto; overflow-y: hidden; }
            #math {
                display: ${if (isDisplay) "block" else "inline-block"};
                width: ${if (isDisplay) "100%" else "auto"};
                ${if (isDisplay) "text-align: center;" else "align-self: flex-end;"}
            }
        </style>
        </head><body>
        <div id="math"></div>
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
        <script>
            try {
                katex.render("$escaped", document.getElementById('math'), {
                    displayMode: $isDisplay,
                    throwOnError: false,
                    trust: true,
                    strict: false
                });
            } catch(e) {
                document.getElementById('math').textContent = e.message;
            }
        </script>
        </body></html>
    """.trimIndent()
}

private fun buildInlineMathHtml(
    expression: String,
    fgColor: String,
    fontScale: Float
): String {
    val escaped = expression
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
        <style>
            html, body {
                margin: 0;
                padding: 0;
                background: transparent;
                overflow: hidden;
            }
            body {
                color: $fgColor;
                display: inline-block;
            }
            .katex {
                color: $fgColor;
                font-size: ${fontScale}em;
                line-height: 1;
            }
            #math {
                display: inline-block;
                white-space: nowrap;
            }
        </style>
        </head>
        <body>
        <span id="math"></span>
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
        <script>
            try {
                katex.render("$escaped", document.getElementById('math'), {
                    displayMode: false,
                    throwOnError: false,
                    trust: true,
                    strict: false
                });
            } catch (e) {
                document.getElementById('math').textContent = e.message;
            }
        </script>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Renders a markdown table with M3 Expressive styling.
 */
@Composable
private fun TableView(
    headers: List<String>,
    rows: List<List<String>>,
    alignments: List<TableAlignment>,
    baseColor: Color = Color.Unspecified,
    codeColor: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val alternateRowColor = MaterialTheme.colorScheme.surfaceContainerLow

    val numCols = headers.size
    val textMeasurer = rememberTextMeasurer()
    val headerStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val cellPaddingH = 12.dp

    // Compute consistent column widths from measured text
    val columnWidths = remember(headers, rows) {
        val widths = IntArray(numCols)
        headers.forEachIndexed { index, header ->
            widths[index] = maxOf(widths[index], textMeasurer.measure(header, headerStyle).size.width)
        }
        rows.forEach { row ->
            row.forEachIndexed { index, cell ->
                if (index < numCols) {
                    widths[index] = maxOf(widths[index], textMeasurer.measure(cell, bodyStyle).size.width)
                }
            }
        }
        widths.map { with(density) { it.toDp() + cellPaddingH * 2 } }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        val scrollState = rememberScrollState()
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.horizontalScroll(scrollState)
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .background(headerBackground)
                    .padding(horizontal = 4.dp)
            ) {
                headers.forEachIndexed { index, header ->
                    MarkdownInlineText(
                        content = parseInlineMarkdown(header, baseColor, codeColor, linkColor),
                        style = headerStyle.copy(
                            textAlign = tableTextAlign(alignments.getOrElse(index) { TableAlignment.LEFT })
                        ),
                        modifier = Modifier
                            .width(columnWidths.getOrElse(index) { 80.dp })
                            .padding(horizontal = cellPaddingH, vertical = 8.dp)
                    )
                }
            }

            // Header divider
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(outlineColor)
            )

            // Data rows
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .then(
                            if (rowIndex % 2 == 0) Modifier.background(alternateRowColor)
                            else Modifier
                        )
                        .padding(horizontal = 4.dp)
                ) {
                    row.forEachIndexed { colIndex, cell ->
                        MarkdownInlineText(
                            content = parseInlineMarkdown(cell, baseColor, codeColor, linkColor),
                            style = bodyStyle.copy(
                                textAlign = tableTextAlign(alignments.getOrElse(colIndex) { TableAlignment.LEFT })
                            ),
                            modifier = Modifier
                                .width(columnWidths.getOrElse(colIndex) { 80.dp })
                                .padding(horizontal = cellPaddingH, vertical = 8.dp)
                        )
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(outlineColor.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

private fun tableTextAlign(alignment: TableAlignment): androidx.compose.ui.text.style.TextAlign {
    return when (alignment) {
        TableAlignment.LEFT -> androidx.compose.ui.text.style.TextAlign.Start
        TableAlignment.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
        TableAlignment.RIGHT -> androidx.compose.ui.text.style.TextAlign.End
    }
}

/**
 * Parse markdown string into a list of MarkdownElements.
 */
private fun parseMarkdown(
    markdown: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color
): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()

    // Combined pattern for code blocks AND display math blocks
    val blockPattern = Regex(
        """```(\w*)?\n?([\s\S]*?)```""" +                                                          // Code blocks (groups 1, 2)
        "|\\\$\\\$\\n?([\\s\\S]*?)\\\$\\\$" +                                                      // Display math $$...$$ (group 3)
        """|\\\[\n?([\s\S]*?)\\\]""" +                                                              // Display math \[...\] (group 4)
        """|\\begin\{(equation|align|gather|aligned|gathered)\}\n?([\s\S]*?)\\end\{\5\}""",         // LaTeX environments (groups 5, 6)
        RegexOption.MULTILINE
    )
    var lastIndex = 0

    blockPattern.findAll(markdown).forEach { match ->
        // Add text before this block (with table detection)
        if (match.range.first > lastIndex) {
            val textBefore = markdown.substring(lastIndex, match.range.first)
            if (textBefore.isNotBlank()) {
                elements.addAll(parseTextWithTables(textBefore, baseColor, codeColor, linkColor))
            }
        }

        when {
            // Code block
            match.value.startsWith("```") -> {
                val language = match.groupValues[1].ifEmpty { null }
                val code = match.groupValues[2].trimEnd()
                elements.add(MarkdownElement.CodeBlock(code, language))
            }
            // Display math $$...$$
            match.value.startsWith("\$\$") -> {
                elements.add(MarkdownElement.MathBlock(match.groupValues[3].trim(), isDisplay = true))
            }
            // Display math \[...\]
            match.value.startsWith("\\[") -> {
                elements.add(MarkdownElement.MathBlock(match.groupValues[4].trim(), isDisplay = true))
            }
            // LaTeX environment \begin{...}...\end{...}
            match.value.startsWith("\\begin") -> {
                val env = match.groupValues[5]
                val content = match.groupValues[6].trim()
                elements.add(MarkdownElement.MathBlock(
                    "\\begin{$env}\n$content\n\\end{$env}",
                    isDisplay = true
                ))
            }
        }

        lastIndex = match.range.last + 1
    }

    // Add remaining text after last block (with table detection)
    if (lastIndex < markdown.length) {
        val remainingText = markdown.substring(lastIndex)
        if (remainingText.isNotBlank()) {
            // Check for unclosed code block (streaming — opening ``` without closing)
            val unclosedCodeMatch = Regex("(^|\\n)```(\\w*)?\\n?").find(remainingText)
            // Check for unclosed display math (streaming — opening $$ without closing)
            val unclosedMathMatch = Regex("(^|\\n)\\\$\\\$\\n?").find(remainingText)

            when {
                unclosedCodeMatch != null -> {
                    val textBefore = remainingText.substring(0, unclosedCodeMatch.range.first)
                    if (textBefore.isNotBlank()) {
                        elements.addAll(parseTextWithTables(textBefore, baseColor, codeColor, linkColor))
                    }
                    val afterFence = remainingText.substring(unclosedCodeMatch.range.last + 1)
                    val language = unclosedCodeMatch.groupValues[2].ifEmpty { null }
                    elements.add(MarkdownElement.CodeBlock(afterFence.trimEnd(), language))
                }
                unclosedMathMatch != null -> {
                    val textBefore = remainingText.substring(0, unclosedMathMatch.range.first)
                    if (textBefore.isNotBlank()) {
                        elements.addAll(parseTextWithTables(textBefore, baseColor, codeColor, linkColor))
                    }
                    val afterDelimiter = remainingText.substring(unclosedMathMatch.range.last + 1)
                    elements.add(MarkdownElement.MathBlock(afterDelimiter.trimEnd(), isDisplay = true))
                }
                else -> {
                    elements.addAll(parseTextWithTables(remainingText, baseColor, codeColor, linkColor))
                }
            }
        }
    }

    // If no blocks found, parse entire text (with table detection)
    if (elements.isEmpty() && markdown.isNotBlank()) {
        elements.addAll(parseTextWithTables(markdown, baseColor, codeColor, linkColor))
    }

    return elements
}

/**
 * Parses a text segment for tables, splitting into text blocks and table elements.
 */
private fun parseTextWithTables(
    text: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color
): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = text.lines()
    var i = 0
    val textBuffer = mutableListOf<String>()

    fun flushTextBuffer() {
        if (textBuffer.isNotEmpty()) {
            val joined = textBuffer.joinToString("\n")
            if (joined.isNotBlank()) {
                elements.add(
                    MarkdownElement.TextBlock(
                        parseInlineMarkdown(joined, baseColor, codeColor, linkColor)
                    )
                )
            }
            textBuffer.clear()
        }
    }

    while (i < lines.size) {
        val trimmedLine = lines[i].trim()

        // Detect horizontal rule (---, ***, ___)
        if (trimmedLine.matches(Regex("^[-*_]{3,}$"))) {
            flushTextBuffer()
            elements.add(MarkdownElement.HorizontalRule)
            i++
            continue
        }

        // Detect blockquote (lines starting with >)
        if (trimmedLine.startsWith(">")) {
            flushTextBuffer()
            val quoteLines = mutableListOf<String>()
            while (i < lines.size) {
                val qLine = lines[i].trim()
                if (qLine.startsWith(">")) {
                    quoteLines.add(qLine.removePrefix(">").removePrefix(" "))
                    i++
                } else {
                    break
                }
            }
            val quoteText = quoteLines.joinToString("\n")
            if (quoteText.isNotBlank()) {
                elements.add(
                    MarkdownElement.Blockquote(
                        parseInlineMarkdown(quoteText, baseColor, codeColor, linkColor)
                    )
                )
            }
            continue
        }

        // Check if this line starts a table (has | and next line is separator)
        if (i + 1 < lines.size && isTableRow(lines[i]) && isTableSeparator(lines[i + 1])) {
            flushTextBuffer()

            val headers = parseTableRow(lines[i])
            val alignments = parseTableAlignments(lines[i + 1])
            val rows = mutableListOf<List<String>>()

            i += 2 // Skip header and separator
            while (i < lines.size && isTableRow(lines[i])) {
                rows.add(parseTableRow(lines[i]))
                i++
            }

            elements.add(MarkdownElement.Table(headers, rows, alignments))
        } else {
            val currentTrimmed = lines[i].trimStart()
            // Split merged numbered list items (e.g., "1. a,2. b" → separate lines)
            if (currentTrimmed.matches(Regex("\\d+\\.\\s+.*"))) {
                lines[i].replace(Regex(",\\s*(?=\\*{0,2}\\d+\\.\\s)"), ",\n")
                    .lines().forEach { textBuffer.add(it) }
            } else if (textBuffer.isNotEmpty() && currentTrimmed.isNotEmpty()
                && isListContinuation(currentTrimmed, textBuffer.last())) {
                // Join continuation line with the preceding list item
                val prev = textBuffer.removeAt(textBuffer.lastIndex)
                textBuffer.add("$prev $currentTrimmed")
            } else {
                textBuffer.add(lines[i])
            }
            i++
        }
    }

    flushTextBuffer()
    return elements
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return false
    return trimmed.split("|").drop(1).dropLast(1).all { cell ->
        cell.trim().matches(Regex(":?-{1,}:?"))
    }
}

private fun parseTableRow(line: String): List<String> {
    return line.trim().removeSurrounding("|").split("|").map { it.trim() }
}

private fun parseTableAlignments(line: String): List<TableAlignment> {
    return line.trim().removeSurrounding("|").split("|").map { cell ->
        val trimmed = cell.trim()
        when {
            trimmed.startsWith(":") && trimmed.endsWith(":") -> TableAlignment.CENTER
            trimmed.endsWith(":") -> TableAlignment.RIGHT
            else -> TableAlignment.LEFT
        }
    }
}

/**
 * Checks whether a non-list, non-header line should be joined to a preceding list item
 * as a continuation (e.g., a long list item that wraps across source lines).
 */
private fun isListContinuation(currentTrimmed: String, previousLine: String): Boolean {
    val prevTrimmed = previousLine.trimStart()
    val prevIsList = prevTrimmed.startsWith("- ") || prevTrimmed.startsWith("* ")
        || prevTrimmed.matches(Regex("\\d+\\.\\s+.*"))
    val currentIsList = currentTrimmed.startsWith("- ") || currentTrimmed.startsWith("* ")
        || currentTrimmed.matches(Regex("\\d+\\.\\s+.*"))
    val currentIsSpecial = currentTrimmed.startsWith("#") || currentTrimmed.matches(Regex("[-*_]{3,}"))
    return prevIsList && !currentIsList && !currentIsSpecial
}

/**
 * Parse inline markdown formatting (bold, italic, code, links, headers, lists).
 */
private fun parseInlineMarkdown(
    text: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color
): ParsedInlineContent {
    val inlineMath = mutableListOf<InlineMathContent>()

    val annotatedText = buildAnnotatedString {
        val lines = text.lines()

        lines.forEachIndexed { index, line ->
            parseLine(line, baseColor, codeColor, linkColor, inlineMath)
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }

    return ParsedInlineContent(
        text = annotatedText,
        inlineMath = inlineMath
    )
}

/**
 * Parse a single line of markdown.
 */
private fun AnnotatedString.Builder.parseLine(
    line: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color,
    inlineMath: MutableList<InlineMathContent>
) {
    val trimmedLine = line.trimStart()

    // Handle headers
    when {
        trimmedLine.startsWith("#### ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = baseColor)) {
                parseInlineFormatting(trimmedLine.removePrefix("#### "), baseColor, codeColor, linkColor, inlineMath)
            }
            return
        }
        trimmedLine.startsWith("### ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = baseColor)) {
                parseInlineFormatting(trimmedLine.removePrefix("### "), baseColor, codeColor, linkColor, inlineMath)
            }
            return
        }
        trimmedLine.startsWith("## ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = baseColor)) {
                parseInlineFormatting(trimmedLine.removePrefix("## "), baseColor, codeColor, linkColor, inlineMath)
            }
            return
        }
        trimmedLine.startsWith("# ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = baseColor)) {
                parseInlineFormatting(trimmedLine.removePrefix("# "), baseColor, codeColor, linkColor, inlineMath)
            }
            return
        }
    }

    // Handle bullet lists
    val bulletMatch = Regex("^\\s*[-*]\\s+(.*)").find(line)
    if (bulletMatch != null) {
        append("  • ")
        parseInlineFormatting(bulletMatch.groupValues[1], baseColor, codeColor, linkColor, inlineMath)
        return
    }

    // Handle numbered lists
    val numberedMatch = Regex("^\\s*(\\d+)\\.\\s+(.*)").find(line)
    if (numberedMatch != null) {
        append("  ${numberedMatch.groupValues[1]}. ")
        parseInlineFormatting(numberedMatch.groupValues[2], baseColor, codeColor, linkColor, inlineMath)
        return
    }

    // Regular text
    parseInlineFormatting(line, baseColor, codeColor, linkColor, inlineMath)
}

/**
 * Parse inline formatting (bold, italic, code, links) within text.
 */
private fun AnnotatedString.Builder.parseInlineFormatting(
    text: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color,
    inlineMath: MutableList<InlineMathContent>
) {
    var currentIndex = 0
    var inlineMathIndex = inlineMath.size

    // Combined pattern for all inline elements
    // Order matters: ** before *, __ before _, math before other $
    val pattern = Regex(
        """(\*\*|__)(.*?)\1""" + // Bold (groups 1, 2)
        """|(\*|_)(?!\s)(.*?)(?!\s)\3""" + // Italic (groups 3, 4)
        """|`([^`]+)`""" + // Inline code (group 5)
        """|\[([^\]]+)\]\(([^)]+)\)""" + // Links (groups 6, 7)
        """|\\\((.+?)\\\)""" + // Inline math \(...\) (group 8)
        "|(?<!\\\$)\\\$(?!\\\$|\\s)([^\\\$\\n]+?)(?<!\\s)\\\$(?!\\\$|\\d)" // Inline math $...$ (group 9)
    )

    val matches = pattern.findAll(text).toList()

    if (matches.isEmpty()) {
        withStyle(SpanStyle(color = baseColor)) {
            append(text)
        }
        return
    }

    matches.forEach { match ->
        // Add text before match
        if (match.range.first > currentIndex) {
            withStyle(SpanStyle(color = baseColor)) {
                append(text.substring(currentIndex, match.range.first))
            }
        }

        val fullMatch = match.value

        when {
            // Bold (**text** or __text__)
            fullMatch.startsWith("**") || fullMatch.startsWith("__") -> {
                val content = match.groupValues[2]
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                    append(content)
                }
            }
            // Italic (*text* or _text_) - group 4
            match.groupValues.getOrNull(4)?.isNotEmpty() == true -> {
                val content = match.groupValues[4]
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                    append(content)
                }
            }
            // Inline code (`code`) - group 5
            match.groupValues.getOrNull(5)?.isNotEmpty() == true -> {
                val content = match.groupValues[5]
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeColor.copy(alpha = 0.15f),
                    color = codeColor
                )) {
                    append(" $content ")
                }
            }
            // Links [text](url) - groups 6 and 7
            match.groupValues.getOrNull(6)?.isNotEmpty() == true -> {
                val linkText = match.groupValues[6]
                val url = match.groupValues[7]
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                )) {
                    append(linkText)
                }
                pop()
            }
            // Inline math \(...\) - group 8
            match.groupValues.getOrNull(8)?.isNotEmpty() == true -> {
                val content = match.groupValues[8]
                val id = "inline-math-$inlineMathIndex"
                inlineMathIndex++
                inlineMath.add(InlineMathContent(id = id, expression = content))
                appendInlineContent(id, fullMatch)
            }
            // Inline math $...$ - group 9
            match.groupValues.getOrNull(9)?.isNotEmpty() == true -> {
                val content = match.groupValues[9]
                if (looksLikeInlineMath(content)) {
                    val id = "inline-math-$inlineMathIndex"
                    inlineMathIndex++
                    inlineMath.add(InlineMathContent(id = id, expression = content))
                    appendInlineContent(id, fullMatch)
                } else {
                    // Not math, render as literal $content$
                    withStyle(SpanStyle(color = baseColor)) {
                        append("\$${content}\$")
                    }
                }
            }
            else -> {
                // Fallback - just add the text
                withStyle(SpanStyle(color = baseColor)) {
                    append(fullMatch)
                }
            }
        }

        currentIndex = match.range.last + 1
    }

    // Add remaining text
    if (currentIndex < text.length) {
        withStyle(SpanStyle(color = baseColor)) {
            append(text.substring(currentIndex))
        }
    }
}

private fun looksLikeInlineMath(content: String): Boolean {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.all { it.isDigit() || it == '.' || it == ',' }) return false

    return trimmed.any { it.isLetter() } ||
        trimmed.any { it in "\\^_{}=+-*/<>[]()" }
}

/**
 * Apply basic syntax highlighting to code.
 */
private fun highlightCode(
    code: String,
    language: String?,
    baseColor: Color,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    numberColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val keywords = when (language?.lowercase()) {
            "kotlin", "kt" -> listOf(
                "fun", "val", "var", "class", "object", "interface", "enum", "data",
                "sealed", "abstract", "open", "private", "public", "protected", "internal",
                "override", "suspend", "inline", "infix", "operator", "tailrec",
                "if", "else", "when", "for", "while", "do", "return", "break", "continue",
                "try", "catch", "finally", "throw", "import", "package", "is", "as", "in",
                "null", "true", "false", "this", "super", "companion", "init", "by", "lazy"
            )
            "java" -> listOf(
                "public", "private", "protected", "static", "final", "abstract", "synchronized",
                "class", "interface", "enum", "extends", "implements", "new", "this", "super",
                "if", "else", "switch", "case", "default", "for", "while", "do", "break", "continue",
                "return", "try", "catch", "finally", "throw", "throws", "import", "package",
                "void", "int", "long", "double", "float", "boolean", "char", "byte", "short",
                "null", "true", "false", "instanceof"
            )
            "python", "py" -> listOf(
                "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while",
                "try", "except", "finally", "with", "return", "yield", "break", "continue",
                "pass", "raise", "lambda", "and", "or", "not", "in", "is", "None", "True", "False",
                "self", "async", "await", "global", "nonlocal"
            )
            "javascript", "js", "typescript", "ts" -> listOf(
                "function", "const", "let", "var", "class", "extends", "new", "this", "super",
                "if", "else", "switch", "case", "default", "for", "while", "do", "break", "continue",
                "return", "try", "catch", "finally", "throw", "import", "export", "from", "as",
                "async", "await", "null", "undefined", "true", "false", "typeof", "instanceof",
                "interface", "type", "enum", "implements", "private", "public", "protected"
            )
            "rust", "rs" -> listOf(
                "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "type",
                "pub", "mod", "use", "crate", "self", "super", "as", "where",
                "if", "else", "match", "loop", "while", "for", "in", "break", "continue", "return",
                "async", "await", "move", "dyn", "ref", "true", "false", "Some", "None", "Ok", "Err"
            )
            "go", "golang" -> listOf(
                "func", "var", "const", "type", "struct", "interface", "map", "chan",
                "package", "import", "if", "else", "switch", "case", "default", "select",
                "for", "range", "break", "continue", "return", "go", "defer", "fallthrough",
                "true", "false", "nil", "iota", "make", "new", "append", "len", "cap"
            )
            "swift" -> listOf(
                "func", "let", "var", "class", "struct", "enum", "protocol", "extension",
                "import", "if", "else", "switch", "case", "default", "guard", "for", "while",
                "repeat", "break", "continue", "return", "throw", "try", "catch", "defer",
                "true", "false", "nil", "self", "super", "init", "deinit", "override",
                "private", "public", "internal", "fileprivate", "open", "static", "final"
            )
            "bash", "sh", "shell" -> listOf(
                "if", "then", "else", "elif", "fi", "for", "do", "done", "while", "until",
                "case", "esac", "function", "return", "exit", "break", "continue",
                "export", "local", "readonly", "declare", "typeset", "set", "unset",
                "true", "false", "in", "source"
            )
            else -> emptyList()
        }

        val lines = code.lines()
        lines.forEachIndexed { lineIndex, line ->
            highlightLine(line, keywords, baseColor, keywordColor, stringColor, commentColor, numberColor, language)
            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }
}

/**
 * Highlight a single line of code.
 */
private fun AnnotatedString.Builder.highlightLine(
    line: String,
    keywords: List<String>,
    baseColor: Color,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    numberColor: Color,
    language: String?
) {
    val commentStart = when (language?.lowercase()) {
        "python", "py", "bash", "sh", "shell" -> "#"
        else -> "//"
    }

    // Check for comment
    val commentIndex = line.indexOf(commentStart)
    val beforeComment = if (commentIndex >= 0) line.substring(0, commentIndex) else line
    val comment = if (commentIndex >= 0) line.substring(commentIndex) else ""

    // Highlight before comment
    highlightCodeSegment(beforeComment, keywords, baseColor, keywordColor, stringColor, numberColor)

    // Highlight comment
    if (comment.isNotEmpty()) {
        withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
            append(comment)
        }
    }
}

/**
 * Highlight a segment of code (excluding comments).
 */
private fun AnnotatedString.Builder.highlightCodeSegment(
    segment: String,
    keywords: List<String>,
    baseColor: Color,
    keywordColor: Color,
    stringColor: Color,
    numberColor: Color
) {
    if (segment.isEmpty()) return

    // Pattern for strings, numbers, and words
    val tokenPattern = Regex(
        """("[^"]*"|'[^']*')""" + // Strings
        """|(\b\d+\.?\d*\b)""" + // Numbers
        """|(\b\w+\b)""" + // Words (potential keywords)
        """|([^\s\w"']+)""" + // Operators and punctuation
        """|(\s+)""" // Whitespace
    )

    tokenPattern.findAll(segment).forEach { match ->
        val token = match.value

        when {
            // String literal
            token.startsWith("\"") || token.startsWith("'") -> {
                withStyle(SpanStyle(color = stringColor)) {
                    append(token)
                }
            }
            // Number
            token.matches(Regex("""\d+\.?\d*""")) -> {
                withStyle(SpanStyle(color = numberColor)) {
                    append(token)
                }
            }
            // Keyword
            token in keywords -> {
                withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)) {
                    append(token)
                }
            }
            // Other (base color)
            else -> {
                withStyle(SpanStyle(color = baseColor)) {
                    append(token)
                }
            }
        }
    }
}
