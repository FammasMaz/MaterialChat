package com.materialchat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
    fillWidth: Boolean = true,
    onOpenCanvas: ((CanvasArtifact) -> Unit)? = null
) {
    val isDarkTheme = isSystemInDarkTheme()
    val codeBlockBackground = if (isDarkTheme) CodeBlockBackgroundDark else CodeBlockBackgroundLight
    val codeTextColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary

    val parsedContent = remember(markdown, textColor, codeTextColor, linkColor) {
        cachedParseMarkdown(
            markdown = markdown,
            baseColor = textColor,
            codeColor = codeTextColor,
            linkColor = linkColor
        )
    }

    MarkdownContent(
        content = parsedContent,
        modifier = modifier,
        style = style,
        codeBlockBackground = codeBlockBackground,
        isStreaming = isStreaming,
        fillWidth = fillWidth,
        onOpenCanvas = onOpenCanvas
    )
}

/**
 * Internal representation of parsed markdown content.
 */
private sealed class MarkdownElement {
    data class TextBlock(val source: String, val content: ParsedInlineContent) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String?) : MarkdownElement()
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlignment>
    ) : MarkdownElement()
    data object HorizontalRule : MarkdownElement()
    data class MathBlock(val expression: String, val isDisplay: Boolean) : MarkdownElement()
    data class Blockquote(val source: String, val content: ParsedInlineContent) : MarkdownElement()
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

private data class MarkdownCacheKey(
    val markdown: String,
    val baseColor: Int,
    val codeColor: Int,
    val linkColor: Int
)

private const val MARKDOWN_PARSE_CACHE_SIZE = 160
private const val BATCHED_MATH_BLOCK_THRESHOLD = 2

private val markdownParseCache = object : android.util.LruCache<MarkdownCacheKey, List<MarkdownElement>>(
    MARKDOWN_PARSE_CACHE_SIZE
) {}

private fun cachedParseMarkdown(
    markdown: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color
): List<MarkdownElement> {
    val key = MarkdownCacheKey(
        markdown = markdown,
        baseColor = baseColor.toArgb(),
        codeColor = codeColor.toArgb(),
        linkColor = linkColor.toArgb()
    )

    synchronized(markdownParseCache) {
        markdownParseCache.get(key)?.let { return it }
    }

    val parsed = parseMarkdown(
        markdown = markdown,
        baseColor = baseColor,
        codeColor = codeColor,
        linkColor = linkColor
    )

    synchronized(markdownParseCache) {
        markdownParseCache.put(key, parsed)
    }
    return parsed
}

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
    fillWidth: Boolean = true,
    onOpenCanvas: ((CanvasArtifact) -> Unit)? = null
) {
    if (shouldUseBatchedMathDocument(content, isStreaming)) {
        MathDocumentWebView(
            content = content,
            modifier = modifier,
            style = style,
            codeBlockBackground = codeBlockBackground
        )
        return
    }

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
                        SelectionContainer(
                            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
                        ) {
                            MarkdownInlineText(
                                content = element.content,
                                style = effectiveStyle,
                                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
                            )
                        }
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
                        codeColor = MaterialTheme.colorScheme.onSurface,
                        linkColor = MaterialTheme.colorScheme.primary,
                        bodyTextStyle = style
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
                        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
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
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            MarkdownInlineText(
                                content = element.content,
                                style = style.copy(fontStyle = FontStyle.Italic),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shouldUseBatchedMathDocument(
    content: List<MarkdownElement>,
    isStreaming: Boolean
): Boolean {
    if (isStreaming) return false
    val complexMathBlocks = content.count { element ->
        element is MarkdownElement.MathBlock && needsKatexRendering(element.expression)
    }
    if (complexMathBlocks < BATCHED_MATH_BLOCK_THRESHOLD) return false

    // Never let the perf path replace M3 Compose components with HTML approximations.
    if (content.any { it is MarkdownElement.Table || it is MarkdownElement.CodeBlock }) return false

    return true
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MathDocumentWebView(
    content: List<MarkdownElement>,
    modifier: Modifier,
    style: TextStyle,
    codeBlockBackground: Color
) {
    val density = LocalDensity.current
    val textColor = style.color.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.onSurface
    val backgroundColor = "transparent"
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val headerBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val alternateRowColor = MaterialTheme.colorScheme.surfaceContainerLow
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val fontSize = style.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp
    val lineHeight = style.lineHeight.takeIf { it != TextUnit.Unspecified } ?: (fontSize * 1.45f)
    val fontSizePx = fontSize.value
    val lineHeightPx = lineHeight.value

    val html = remember(
        content,
        textColor,
        backgroundColor,
        surfaceContainer,
        headerBackground,
        alternateRowColor,
        outlineColor,
        linkColor,
        codeBlockBackground,
        fontSizePx,
        lineHeightPx
    ) {
        buildMarkdownMathDocumentHtml(
            content = content,
            textColor = textColor.toCssColor(),
            backgroundColor = backgroundColor,
            mathBackgroundColor = codeBlockBackground.copy(alpha = 0.5f).toCssRgbaColor(),
            codeBackgroundColor = codeBlockBackground.toCssColor(),
            tableBackgroundColor = surfaceContainer.toCssColor(),
            tableHeaderColor = headerBackground.toCssColor(),
            tableAlternateRowColor = alternateRowColor.toCssColor(),
            outlineColor = outlineColor.toCssColor(),
            linkColor = linkColor.toCssColor(),
            fontSizePx = fontSizePx,
            lineHeightPx = lineHeightPx
        )
    }

    val fallbackHeightPx = remember(content, density) {
        estimateMarkdownDocumentHeightPx(content, density)
    }
    var webViewHeight by remember(html) { mutableIntStateOf(fallbackHeightPx) }
    var hasMeasuredHeight by remember(html) { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            createMathDocumentWebView(context) { view ->
                fun updateHeight() {
                    measureDocumentHeight(view) { measuredHeight ->
                        if (measuredHeight > 0) {
                            hasMeasuredHeight = true
                            val heightBufferPx = (12 * view.context.resources.displayMetrics.density).toInt()
                            webViewHeight = measuredHeight + heightBufferPx
                        }
                    }
                }
                updateHeight()
                view.postDelayed({ updateHeight() }, 80)
                view.postDelayed({ updateHeight() }, 220)
                view.postDelayed({ updateHeight() }, 500)
            }
        },
        update = { webView ->
            if (!hasMeasuredHeight && webViewHeight < fallbackHeightPx) {
                webViewHeight = fallbackHeightPx
            }
            webView.loadMathHtmlIfNeeded(html)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { webViewHeight.toDp() })
            .clipToBounds()
    )
}

@Composable
private fun MarkdownInlineText(
    content: ParsedInlineContent,
    style: TextStyle,
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    if (content.inlineMath.isEmpty()) {
        Text(
            text = content.text,
            style = style,
            modifier = modifier,
            softWrap = softWrap,
            maxLines = maxLines
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
        modifier = modifier,
        softWrap = softWrap,
        maxLines = maxLines
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
    val textColor = MaterialTheme.colorScheme.onSurface
    val keywordColor = MaterialTheme.colorScheme.primary
    val stringColor = MaterialTheme.colorScheme.tertiary
    val commentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val numberColor = MaterialTheme.colorScheme.secondary

    // Detect if this code block is a renderable artifact
    val artifact = remember(code, language) {
        ArtifactDetector.detect(code, language)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
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
                    .size(48.dp),
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
    if (isStreaming || !needsKatexRendering(expression)) {
        NativeMathBlockView(
            expression = expression,
            backgroundColor = backgroundColor
        )
        return
    }

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
            .clipToBounds()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor.copy(alpha = 0.5f))
    ) {
        AndroidView(
            factory = { context ->
                createMathWebView(context) { view ->
                    val heightBufferPx = (8 * context.resources.displayMetrics.density).toInt()
                    fun updateHeight() {
                        measureMathHeight(view) { measuredHeight ->
                            if (measuredHeight > 0) {
                                hasMeasuredHeight = true
                                webViewHeight = if (isStreaming) {
                                    maxOf(measuredHeight + heightBufferPx, fallbackHeightPx)
                                } else {
                                    maxOf(measuredHeight + heightBufferPx, settledMinHeightPx)
                                }
                            }
                        }
                    }
                    updateHeight()
                    view.postDelayed({ updateHeight() }, 48)
                    view.postDelayed({ updateHeight() }, 160)
                    view.postDelayed({ updateHeight() }, 350)
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
                .clipToBounds()
        )
    }
}

@Composable
private fun NativeMathBlockView(
    expression: String,
    backgroundColor: Color
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val rendered = remember(expression) {
        if (needsKatexRendering(expression)) {
            buildAnnotatedString { append(expression) }
        } else {
            renderInlineMathForTesting(expression)
        }
    }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor.copy(alpha = 0.5f))
    ) {
        SelectionContainer(modifier = Modifier.align(Alignment.Center)) {
            Text(
                text = rendered,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                softWrap = false
            )
        }
    }
}

private fun estimateBlockMathFallbackHeightPx(
    expression: String,
    density: androidx.compose.ui.unit.Density,
    isStreaming: Boolean
): Int {
    val baseHeight = when {
        expression.contains("\\begin{align") || expression.contains("\\begin{gather") -> 76.dp
        expression.contains("\\frac") || expression.contains("\\dfrac") -> 60.dp
        expression.contains("\\sum") || expression.contains("\\int") || expression.contains("\\prod") -> 54.dp
        expression.contains("\\sqrt") || expression.contains("\\binom") -> 48.dp
        expression.contains('\n') -> 64.dp
        else -> 40.dp
    }

    val adjustedHeight = if (isStreaming) baseHeight + 12.dp else baseHeight

    return with(density) { adjustedHeight.roundToPx() }
}

private fun estimateBlockMathSettledMinHeightPx(
    expression: String,
    density: androidx.compose.ui.unit.Density
): Int {
    val minHeight = when {
        expression.contains("\\begin{align") || expression.contains("\\begin{gather") -> 56.dp
        expression.contains("\\frac") || expression.contains("\\dfrac") -> 46.dp
        expression.contains("\\sum") || expression.contains("\\int") || expression.contains("\\prod") -> 40.dp
        expression.contains('\n') -> 50.dp
        else -> 32.dp
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
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = true
        overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

        var downX = 0f
        var downY = 0f
        @Suppress("ClickableViewAccessibility")
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(event.x - downX)
                    val dy = kotlin.math.abs(event.y - downY)
                    if (dx > dy) {
                        // Let the WebView claim horizontal drags for wide equations instead
                        // of the LazyColumn or bubble swipe gesture taking the gesture stream.
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        isFocusable = false
        isFocusableInTouchMode = false
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

private fun Color.toCssRgbaColor(): String {
    val argb = toArgb()
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    val alpha = ((argb ushr 24) and 0xFF) / 255f
    return "rgba($red, $green, $blue, $alpha)"
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

@SuppressLint("SetJavaScriptEnabled")
private fun createMathDocumentWebView(
    context: android.content.Context,
    onPageFinished: ((WebView) -> Unit)? = null
): WebView {
    return createMathWebView(context, onPageFinished).apply {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = android.view.View.OVER_SCROLL_NEVER
        settings.apply {
            loadWithOverviewMode = false
            useWideViewPort = false
        }
    }
}

private fun measureDocumentHeight(webView: WebView, onMeasured: (Int) -> Unit) {
    webView.evaluateJavascript(
        """(function() {
            var documentNode = document.querySelector('.markdown-document');
            if (!documentNode) return 0;
            var rect = documentNode.getBoundingClientRect();
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

private fun buildMarkdownMathDocumentHtml(
    content: List<MarkdownElement>,
    textColor: String,
    backgroundColor: String,
    mathBackgroundColor: String,
    codeBackgroundColor: String,
    tableBackgroundColor: String,
    tableHeaderColor: String,
    tableAlternateRowColor: String,
    outlineColor: String,
    linkColor: String,
    fontSizePx: Float,
    lineHeightPx: Float
): String {
    val bodyHtml = content.joinToString("\n") { element ->
        when (element) {
            is MarkdownElement.TextBlock -> markdownTextBlockToHtml(element.source)
            is MarkdownElement.CodeBlock -> buildString {
                append("<pre><code")
                element.language?.takeIf { it.isNotBlank() }?.let { language ->
                    append(" class=\"language-")
                    append(escapeHtmlAttribute(language))
                    append("\"")
                }
                append(">")
                append(escapeHtml(element.code))
                append("</code></pre>")
            }
            is MarkdownElement.Table -> markdownTableToHtml(element)
            is MarkdownElement.HorizontalRule -> "<hr>"
            is MarkdownElement.MathBlock -> {
                "<div class=\"math-display\">${escapeHtml(element.expression)}</div>"
            }
            is MarkdownElement.Blockquote -> {
                "<blockquote>${markdownTextBlockToHtml(element.source)}</blockquote>"
            }
        }
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
        <style>
            * { box-sizing: border-box; }
            html, body {
                margin: 0;
                padding: 0;
                width: 100%;
                background: $backgroundColor;
                color: $textColor;
                overflow-x: hidden;
                overflow-y: hidden;
                font-family: sans-serif;
                font-size: ${fontSizePx}px;
                line-height: ${lineHeightPx / fontSizePx};
            }
            body { padding: 0; }
            .markdown-document { width: 100%; }
            p { margin: 0 0 10px; }
            p:last-child { margin-bottom: 0; }
            h1, h2, h3, h4 {
                margin: 14px 0 8px;
                line-height: 1.18;
                font-weight: 700;
            }
            h1 { font-size: 1.55em; }
            h2 { font-size: 1.35em; }
            h3 { font-size: 1.16em; }
            h4 { font-size: 1.02em; }
            ul, ol { margin: 0 0 10px 1.4em; padding: 0; }
            li { margin: 2px 0; }
            a { color: $linkColor; text-decoration: underline; }
            code {
                font-family: monospace;
                background: $codeBackgroundColor;
                border-radius: 5px;
                padding: 0.08em 0.35em;
            }
            pre {
                margin: 8px 0;
                padding: 12px;
                overflow-x: auto;
                background: $codeBackgroundColor;
                border-radius: 16px;
                white-space: pre;
            }
            pre code { background: transparent; padding: 0; }
            blockquote {
                margin: 6px 0;
                padding: 2px 0 2px 10px;
                border-left: 3px solid $linkColor;
                font-style: italic;
            }
            hr {
                border: 0;
                border-top: 1px solid $outlineColor;
                margin: 12px 0;
            }
            .math-display {
                width: 100%;
                margin: 8px 0;
                padding: 8px 12px;
                border-radius: 8px;
                background: $mathBackgroundColor;
                overflow-x: auto;
                overflow-y: hidden;
                text-align: center;
                -webkit-overflow-scrolling: touch;
            }
            .math-display .katex-display {
                margin: 0;
                width: max-content;
                min-width: 100%;
            }
            .math-inline { white-space: nowrap; }
            .table-wrap {
                width: max-content;
                max-width: 100%;
                overflow-x: auto;
                margin: 8px 0;
                border-radius: 16px;
                background: $tableBackgroundColor;
                -webkit-overflow-scrolling: touch;
            }
            table {
                border-collapse: collapse;
                width: max-content;
                min-width: 100%;
                color: $textColor;
            }
            th, td {
                padding: 8px 12px;
                text-align: left;
                vertical-align: top;
                border-bottom: 1px solid $outlineColor;
                max-width: 360px;
                overflow-wrap: anywhere;
            }
            th { background: $tableHeaderColor; font-weight: 700; }
            tr:nth-child(even) td { background: $tableAlternateRowColor; }
            tr:last-child td { border-bottom: 0; }
        </style>
        </head>
        <body>
        <main class="markdown-document">
        $bodyHtml
        </main>
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
        <script>
            function renderMathNode(node, displayMode) {
                var tex = node.textContent;
                try {
                    katex.render(tex, node, {
                        displayMode: displayMode,
                        throwOnError: false,
                        trust: true,
                        strict: false
                    });
                } catch (e) {
                    node.textContent = tex;
                }
            }
            document.querySelectorAll('.math-display').forEach(function(node) {
                renderMathNode(node, true);
            });
            document.querySelectorAll('.math-inline').forEach(function(node) {
                renderMathNode(node, false);
            });
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun markdownTableToHtml(table: MarkdownElement.Table): String {
    val numCols = maxOf(
        table.headers.size,
        table.alignments.size,
        table.rows.maxOfOrNull { it.size } ?: 0
    )
    if (numCols == 0) return ""

    return buildString {
        append("<div class=\"table-wrap\"><table><thead><tr>")
        repeat(numCols) { index ->
            append("<th style=\"text-align:")
            append(tableCssTextAlign(table.alignments.getOrElse(index) { TableAlignment.LEFT }))
            append("\">")
            append(markdownInlineToHtml(table.headers.getOrElse(index) { "" }))
            append("</th>")
        }
        append("</tr></thead><tbody>")
        table.rows.forEach { row ->
            append("<tr>")
            repeat(numCols) { index ->
                append("<td style=\"text-align:")
                append(tableCssTextAlign(table.alignments.getOrElse(index) { TableAlignment.LEFT }))
                append("\">")
                append(markdownInlineToHtml(row.getOrElse(index) { "" }.toTableCellText()))
                append("</td>")
            }
            append("</tr>")
        }
        append("</tbody></table></div>")
    }
}

private fun markdownTextBlockToHtml(source: String): String {
    val html = StringBuilder()
    val paragraph = mutableListOf<String>()
    var listTag: String? = null

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            html.append("<p>")
            html.append(paragraph.joinToString("<br>") { markdownInlineToHtml(it.trim()) })
            html.append("</p>")
            paragraph.clear()
        }
    }

    fun closeList() {
        listTag?.let { html.append("</").append(it).append(">") }
        listTag = null
    }

    fun ensureList(tag: String) {
        flushParagraph()
        if (listTag != tag) {
            closeList()
            html.append("<").append(tag).append(">")
            listTag = tag
        }
    }

    source.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> {
                flushParagraph()
                closeList()
            }
            trimmed.startsWith("#### ") -> {
                flushParagraph()
                closeList()
                html.append("<h4>").append(markdownInlineToHtml(trimmed.removePrefix("#### "))).append("</h4>")
            }
            trimmed.startsWith("### ") -> {
                flushParagraph()
                closeList()
                html.append("<h3>").append(markdownInlineToHtml(trimmed.removePrefix("### "))).append("</h3>")
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                closeList()
                html.append("<h2>").append(markdownInlineToHtml(trimmed.removePrefix("## "))).append("</h2>")
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                closeList()
                html.append("<h1>").append(markdownInlineToHtml(trimmed.removePrefix("# "))).append("</h1>")
            }
            Regex("^[-*]\\s+(.+)").matches(trimmed) -> {
                ensureList("ul")
                val item = Regex("^[-*]\\s+(.+)").find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                html.append("<li>").append(markdownInlineToHtml(item)).append("</li>")
            }
            Regex("^\\d+\\.\\s+(.+)").matches(trimmed) -> {
                ensureList("ol")
                val item = Regex("^\\d+\\.\\s+(.+)").find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                html.append("<li>").append(markdownInlineToHtml(item)).append("</li>")
            }
            else -> {
                closeList()
                paragraph.add(line)
            }
        }
    }

    flushParagraph()
    closeList()
    return html.toString()
}

private fun markdownInlineToHtml(text: String): String {
    val output = StringBuilder()
    var currentIndex = 0
    htmlInlineMarkdownPattern.findAll(text).forEach { match ->
        if (match.range.first > currentIndex) {
            output.append(escapeHtml(text.substring(currentIndex, match.range.first)))
        }

        val fullMatch = match.value
        when {
            fullMatch.startsWith("**") || fullMatch.startsWith("__") -> {
                output.append("<strong>")
                output.append(markdownInlineToHtml(match.groupValues[2]))
                output.append("</strong>")
            }
            match.groupValues.getOrNull(4)?.isNotEmpty() == true -> {
                output.append("<em>")
                output.append(markdownInlineToHtml(match.groupValues[4]))
                output.append("</em>")
            }
            match.groupValues.getOrNull(5)?.isNotEmpty() == true -> {
                output.append("<code>")
                output.append(escapeHtml(match.groupValues[5]))
                output.append("</code>")
            }
            match.groupValues.getOrNull(6)?.isNotEmpty() == true -> {
                val href = sanitizeHtmlHref(match.groupValues[7])
                output.append("<a href=\"")
                output.append(escapeHtmlAttribute(href))
                output.append("\">")
                output.append(escapeHtml(match.groupValues[6]))
                output.append("</a>")
            }
            match.groupValues.getOrNull(8)?.isNotEmpty() == true -> {
                output.append("<span class=\"math-inline\">")
                output.append(escapeHtml(match.groupValues[8]))
                output.append("</span>")
            }
            match.groupValues.getOrNull(9)?.isNotEmpty() == true && looksLikeInlineMath(match.groupValues[9]) -> {
                output.append("<span class=\"math-inline\">")
                output.append(escapeHtml(match.groupValues[9]))
                output.append("</span>")
            }
            match.groupValues.getOrNull(10)?.isNotEmpty() == true -> {
                output.append("<span class=\"citation\">")
                output.append(escapeHtml(match.groupValues[10]))
                output.append("</span>")
            }
            else -> output.append(escapeHtml(fullMatch))
        }
        currentIndex = match.range.last + 1
    }

    if (currentIndex < text.length) {
        output.append(escapeHtml(text.substring(currentIndex)))
    }
    return output.toString()
}

private val htmlInlineMarkdownPattern = Regex(
    """(\*\*|__)(.*?)\1""" +
        """|(\*|_)(?!\s)(.*?)(?!\s)\3""" +
        """|`([^`]+)`""" +
        """|\[([^\]]+)]\(([^)]+)\)""" +
        """|\\\((.+?)\\\)""" +
        "|(?<!\\$)\\$(?!\\$|\\s)([^\\$\\n]+?)(?<!\\s)\\$(?!\\$|\\d)" +
        """|(?<!\[)\[(\d{1,2})](?!\()"""
)

private fun tableCssTextAlign(alignment: TableAlignment): String {
    return when (alignment) {
        TableAlignment.LEFT -> "left"
        TableAlignment.CENTER -> "center"
        TableAlignment.RIGHT -> "right"
    }
}

private fun sanitizeHtmlHref(url: String): String {
    val trimmed = url.trim()
    return if (
        trimmed.startsWith("https://") ||
        trimmed.startsWith("http://") ||
        trimmed.startsWith("mailto:")
    ) {
        trimmed
    } else {
        "#"
    }
}

private fun escapeHtml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

private fun escapeHtmlAttribute(value: String): String {
    return escapeHtml(value).replace("\"", "&quot;")
}

private fun estimateMarkdownDocumentHeightPx(
    content: List<MarkdownElement>,
    density: androidx.compose.ui.unit.Density
): Int {
    val heightDp = content.fold(0.dp) { total, element ->
        total + when (element) {
            is MarkdownElement.TextBlock -> maxOf(28.dp, (element.source.lines().size * 24).dp)
            is MarkdownElement.CodeBlock -> maxOf(56.dp, (element.code.lines().size * 20 + 24).dp)
            is MarkdownElement.Table -> ((element.rows.size + 1) * 42).dp + 16.dp
            is MarkdownElement.HorizontalRule -> 25.dp
            is MarkdownElement.MathBlock -> estimateMathBlockHeightDp(element.expression)
            is MarkdownElement.Blockquote -> maxOf(32.dp, (element.source.lines().size * 24).dp)
        }
    }
    return with(density) { (heightDp + 24.dp).roundToPx() }
}

private fun estimateMathBlockHeightDp(expression: String): androidx.compose.ui.unit.Dp {
    return when {
        expression.contains("\\begin{align") || expression.contains("\\begin{gather") -> 84.dp
        expression.contains("\\begin{") -> 96.dp
        expression.contains("\\frac") || expression.contains("\\dfrac") -> 68.dp
        expression.contains("\\sum") || expression.contains("\\int") || expression.contains("\\prod") -> 58.dp
        expression.contains("\\sqrt") || expression.contains("\\binom") -> 52.dp
        expression.contains('\n') -> 72.dp
        else -> 44.dp
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
                overflow-x: ${if (isDisplay) "auto" else "hidden"};
                overflow-y: hidden;
                -webkit-overflow-scrolling: touch;
                ${if (isDisplay) "text-align: center;" else "display: flex; align-items: flex-end;"}
            }
            .katex { color: $fgColor; font-size: ${fontScale}em; }
            .katex-display {
                margin: 0;
                overflow: visible;
                white-space: nowrap;
                ${if (isDisplay) "width: max-content; min-width: 100%;" else ""}
            }
            #math {
                display: ${if (isDisplay) "block" else "inline-block"};
                width: ${if (isDisplay) "max-content" else "auto"};
                min-width: ${if (isDisplay) "100%" else "0"};
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
    linkColor: Color = Color.Unspecified,
    bodyTextStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val tableContainer = MaterialTheme.colorScheme.surfaceContainerLow
    val headerBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val alternateRowColor = MaterialTheme.colorScheme.surfaceContainer
    val tableShape = MaterialTheme.shapes.large

    val numCols = maxOf(
        headers.size,
        alignments.size,
        rows.maxOfOrNull { it.size } ?: 0
    )
    if (numCols == 0) return

    val textMeasurer = rememberTextMeasurer()
    val bodyStyle = bodyTextStyle.copy(textAlign = TextAlign.Start)
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)
    val density = LocalDensity.current
    val horizontalCellPadding = 12.dp
    val verticalCellPadding = 8.dp

    val columnWidths = remember(headers, rows, density, headerStyle, bodyStyle) {
        val widths = IntArray(numCols)
        headers.forEachIndexed { index, header ->
            if (index < numCols) {
                widths[index] = maxOf(widths[index], textMeasurer.measure(header, headerStyle).size.width)
            }
        }
        rows.forEach { row ->
            row.forEachIndexed { index, cell ->
                if (index < numCols) {
                    widths[index] = maxOf(widths[index], textMeasurer.measure(cell, bodyStyle).size.width)
                }
            }
        }
        widths.map { width ->
            with(density) {
                (width.toDp() + horizontalCellPadding * 2).coerceIn(104.dp, 360.dp)
            }
        }
    }
    val tableWidth = columnWidths.fold(0.dp) { total, width -> total + width }
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val viewportWidth = minOf(maxWidth, tableWidth)

        Surface(
            modifier = Modifier.width(viewportWidth),
            shape = tableShape,
            color = tableContainer,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.62f))
        ) {
            Box(modifier = Modifier.horizontalScroll(scrollState)) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.width(tableWidth)
                ) {
                    Row(
                        modifier = Modifier
                            .width(tableWidth)
                            .background(headerBackground)
                    ) {
                        repeat(numCols) { index ->
                            TableCell(
                                text = headers.getOrElse(index) { "" },
                                style = headerStyle.copy(
                                    textAlign = tableTextAlign(alignments.getOrElse(index) { TableAlignment.LEFT })
                                ),
                                width = columnWidths.getOrElse(index) { 104.dp },
                                horizontalPadding = horizontalCellPadding,
                                verticalPadding = verticalCellPadding,
                                baseColor = baseColor,
                                codeColor = codeColor,
                                linkColor = linkColor
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .width(tableWidth)
                            .height(1.dp)
                            .background(outlineColor.copy(alpha = 0.72f))
                    )

                    rows.forEachIndexed { rowIndex, row ->
                        val rowColor = if (rowIndex % 2 == 0) tableContainer else alternateRowColor
                        Row(
                            modifier = Modifier
                                .width(tableWidth)
                                .background(rowColor)
                        ) {
                            repeat(numCols) { colIndex ->
                                TableCell(
                                    text = row.getOrElse(colIndex) { "" },
                                    style = bodyStyle.copy(
                                        textAlign = tableTextAlign(
                                            alignments.getOrElse(colIndex) { TableAlignment.LEFT }
                                        )
                                    ),
                                    width = columnWidths.getOrElse(colIndex) { 104.dp },
                                    horizontalPadding = horizontalCellPadding,
                                    verticalPadding = verticalCellPadding,
                                    baseColor = baseColor,
                                    codeColor = codeColor,
                                    linkColor = linkColor
                                )
                            }
                        }
                        if (rowIndex < rows.lastIndex) {
                            Spacer(
                                modifier = Modifier
                                    .width(tableWidth)
                                    .height(1.dp)
                                    .background(outlineColor.copy(alpha = 0.44f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    style: TextStyle,
    width: androidx.compose.ui.unit.Dp,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color
) {
    MarkdownInlineText(
        content = parseInlineMarkdown(text.toTableCellText(), baseColor, codeColor, linkColor),
        style = style,
        modifier = Modifier
            .width(width)
            .heightIn(min = 40.dp)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        softWrap = true
    )
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
                        source = joined,
                        content = parseInlineMarkdown(joined, baseColor, codeColor, linkColor)
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
                        source = quoteText,
                        content = parseInlineMarkdown(quoteText, baseColor, codeColor, linkColor)
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

internal fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return !trimmed.startsWith(">") && tableCells(trimmed).size >= 2
}

internal fun isTableSeparator(line: String): Boolean {
    val cells = tableCells(line)
    return cells.size >= 2 && cells.all { cell ->
        cell.trim().matches(Regex(":?-{1,}:?"))
    }
}

internal fun parseTableRow(line: String): List<String> {
    return tableCells(line).map { it.trim() }
}

private fun parseTableAlignments(line: String): List<TableAlignment> {
    return tableCells(line).map { cell ->
        val trimmed = cell.trim()
        when {
            trimmed.startsWith(":") && trimmed.endsWith(":") -> TableAlignment.CENTER
            trimmed.endsWith(":") -> TableAlignment.RIGHT
            else -> TableAlignment.LEFT
        }
    }
}

internal fun tableCells(line: String): List<String> {
    val trimmed = line.trim()
    if ('|' !in trimmed) return emptyList()

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var inCodeSpan = false
    var index = 0

    while (index < trimmed.length) {
        val char = trimmed[index]
        val next = trimmed.getOrNull(index + 1)
        when {
            char == '\\' && next == '|' -> {
                current.append('|')
                index += 2
            }
            char == '`' -> {
                inCodeSpan = !inCodeSpan
                current.append(char)
                index++
            }
            char == '|' && !inCodeSpan -> {
                cells.add(current.toString())
                current.clear()
                index++
            }
            else -> {
                current.append(char)
                index++
            }
        }
    }
    cells.add(current.toString())

    if (trimmed.startsWith("|") && cells.firstOrNull()?.isEmpty() == true) {
        cells.removeAt(0)
    }
    if (trimmed.endsWith("|") && cells.lastOrNull()?.isEmpty() == true) {
        cells.removeAt(cells.lastIndex)
    }

    return cells
}

private fun String.toTableCellText(): String {
    return replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
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

    // Combined pattern for all inline elements
    // Order matters: ** before *, __ before _, math before other $
    val pattern = Regex(
        """(\*\*|__)(.*?)\1""" + // Bold (groups 1, 2)
        """|(\*|_)(?!\s)(.*?)(?!\s)\3""" + // Italic (groups 3, 4)
        """|`([^`]+)`""" + // Inline code (group 5)
        """|\[([^\]]+)\]\(([^)]+)\)""" + // Links (groups 6, 7)
        """|\\\((.+?)\\\)""" + // Inline math \(...\) (group 8)
        "|(?<!\\\$)\\\$(?!\\\$|\\s)([^\\\$\\n]+?)(?<!\\s)\\\$(?!\\\$|\\d)" + // Inline math $...$ (group 9)
        """|(?<!\[)\[(\d{1,2})\](?!\()""" // Citation [N] not part of link (group 10)
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
                // Keep inline math in the Text layout instead of replacing it with
                // InlineTextContent + WebView. Embedded AndroidViews can reserve
                // space but fail to paint while streaming/recomposing, which looks
                // like words vanished in the middle of a sentence.
                appendInlineMathFallback(content, baseColor, linkColor)
            }
            // Inline math $...$ - group 9
            match.groupValues.getOrNull(9)?.isNotEmpty() == true -> {
                val content = match.groupValues[9]
                if (looksLikeInlineMath(content)) {
                    appendInlineMathFallback(content, baseColor, linkColor)
                } else {
                    // Not math, render as literal $content$
                    withStyle(SpanStyle(color = baseColor)) {
                        append("\$${content}\$")
                    }
                }
            }
            // Citation pill [N] - group 10
            match.groupValues.getOrNull(10)?.isNotEmpty() == true -> {
                val citationNum = match.groupValues[10]
                withStyle(SpanStyle(
                    background = linkColor.copy(alpha = 0.15f),
                    color = linkColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )) {
                    append(" $citationNum ")
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

private fun AnnotatedString.Builder.appendInlineMathFallback(
    content: String,
    baseColor: Color,
    linkColor: Color
) {
    withStyle(SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontStyle = FontStyle.Italic,
        background = linkColor.copy(alpha = 0.10f),
        color = baseColor
    )) {
        appendInlineMathExpression(content.trim())
    }
}

internal fun renderInlineMathForTesting(expression: String): AnnotatedString {
    return buildAnnotatedString {
        appendInlineMathExpression(expression.trim())
    }
}

private fun AnnotatedString.Builder.appendInlineMathExpression(expression: String) {
    var index = 0
    while (index < expression.length) {
        when (val char = expression[index]) {
            '\\' -> {
                val command = readLatexCommand(expression, index)
                index = appendLatexCommand(command.value, expression, command.nextIndex)
            }
            '^', '_' -> {
                val script = readMathScriptContent(expression, index + 1)
                if (script.value.isNotEmpty()) {
                    appendMathScript(script.value, superscript = char == '^')
                }
                index = script.nextIndex
            }
            '{', '}' -> index++
            '~' -> {
                append(' ')
                index++
            }
            else -> {
                append(char)
                index++
            }
        }
    }
}

private fun AnnotatedString.Builder.appendLatexCommand(
    command: String,
    expression: String,
    afterCommandIndex: Int
): Int {
    if (command in latexTextWrapperCommands && expression.getOrNull(afterCommandIndex) == '{') {
        val group = readBraceGroup(expression, afterCommandIndex)
        appendInlineMathExpression(group.value)
        return group.nextIndex
    }

    val replacement = latexSymbolReplacement(command)
    if (replacement != null) {
        append(replacement)
        return afterCommandIndex
    }

    append(command.removePrefix("\\"))
    return afterCommandIndex
}

private fun AnnotatedString.Builder.appendMathScript(content: String, superscript: Boolean) {
    withStyle(SpanStyle(
        baselineShift = if (superscript) BaselineShift.Superscript else BaselineShift.Subscript,
        fontSize = 0.82.em
    )) {
        appendInlineMathExpression(content)
    }
}

private data class LatexCommand(
    val value: String,
    val nextIndex: Int,
    val consumedUntil: Int
)

private data class MathToken(
    val value: String,
    val nextIndex: Int
)

private fun readLatexCommand(expression: String, startIndex: Int): LatexCommand {
    var index = startIndex + 1
    while (index < expression.length && expression[index].isLetter()) index++
    if (index == startIndex + 1 && index < expression.length) index++
    val command = expression.substring(startIndex, index)
    return LatexCommand(command, index, index)
}

private fun readMathScriptContent(expression: String, startIndex: Int): MathToken {
    if (startIndex >= expression.length) return MathToken("", startIndex)
    return when (expression[startIndex]) {
        '{' -> readBraceGroup(expression, startIndex)
        '\\' -> {
            val command = readLatexCommand(expression, startIndex)
            MathToken(command.value, command.consumedUntil)
        }
        else -> MathToken(expression[startIndex].toString(), startIndex + 1)
    }
}

private fun readBraceGroup(expression: String, openBraceIndex: Int): MathToken {
    var depth = 0
    var index = openBraceIndex
    while (index < expression.length) {
        when (expression[index]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return MathToken(
                        value = expression.substring(openBraceIndex + 1, index),
                        nextIndex = index + 1
                    )
                }
            }
        }
        index++
    }
    return MathToken(expression.substring(openBraceIndex + 1), expression.length)
}

private val latexTextWrapperCommands = setOf(
    "\\mathbb",
    "\\mathcal",
    "\\mathrm",
    "\\mathbf",
    "\\text",
    "\\textrm",
    "\\operatorname"
)

private fun latexSymbolReplacement(command: String): String? {
    return latexSymbolReplacements[command]
}

private val latexSymbolReplacements = mapOf(
    "\\rightarrow" to "→",
    "\\leftarrow" to "←",
    "\\Rightarrow" to "⇒",
    "\\Leftarrow" to "⇐",
    "\\Leftrightarrow" to "⇔",
    "\\subseteq" to "⊆",
    "\\supseteq" to "⊇",
    "\\operatorname" to "",
    "\\alpha" to "α",
    "\\beta" to "β",
    "\\gamma" to "γ",
    "\\delta" to "δ",
    "\\epsilon" to "ε",
    "\\zeta" to "ζ",
    "\\eta" to "η",
    "\\theta" to "θ",
    "\\iota" to "ι",
    "\\kappa" to "κ",
    "\\lambda" to "λ",
    "\\mu" to "μ",
    "\\nu" to "ν",
    "\\xi" to "ξ",
    "\\pi" to "π",
    "\\rho" to "ρ",
    "\\sigma" to "σ",
    "\\tau" to "τ",
    "\\upsilon" to "υ",
    "\\phi" to "φ",
    "\\chi" to "χ",
    "\\psi" to "ψ",
    "\\omega" to "ω",
    "\\Gamma" to "Γ",
    "\\Delta" to "Δ",
    "\\Theta" to "Θ",
    "\\Lambda" to "Λ",
    "\\Pi" to "Π",
    "\\Sigma" to "Σ",
    "\\Phi" to "Φ",
    "\\Psi" to "Ψ",
    "\\Omega" to "Ω",
    "\\times" to "×",
    "\\cdot" to "·",
    "\\div" to "÷",
    "\\pm" to "±",
    "\\mp" to "∓",
    "\\leq" to "≤",
    "\\geq" to "≥",
    "\\neq" to "≠",
    "\\le" to "≤",
    "\\ge" to "≥",
    "\\ne" to "≠",
    "\\approx" to "≈",
    "\\equiv" to "≡",
    "\\sim" to "∼",
    "\\propto" to "∝",
    "\\notin" to "∉",
    "\\in" to "∈",
    "\\subset" to "⊂",
    "\\supset" to "⊃",
    "\\cup" to "∪",
    "\\cap" to "∩",
    "\\forall" to "∀",
    "\\exists" to "∃",
    "\\infty" to "∞",
    "\\nabla" to "∇",
    "\\partial" to "∂",
    "\\to" to "→",
    "\\iff" to "⇔",
    "\\sqrt" to "√",
    "\\sum" to "∑",
    "\\prod" to "∏",
    "\\int" to "∫",
    "\\ldots" to "…",
    "\\cdots" to "⋯",
    "\\dots" to "…",
    "\\langle" to "⟨",
    "\\rangle" to "⟩"
)

private fun looksLikeInlineMath(content: String): Boolean {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.all { it.isDigit() || it == '.' || it == ',' }) return false

    val hasMathSyntax = trimmed.any { it in "\\^_{}=+-*/<>[]()" }
    val hasLatexCommand = Regex("""\\[a-zA-Z]+""").containsMatchIn(trimmed)
    val hasMathSymbol = trimmed.any { it in "≤≥≠≈∞∑∏√∫π÷×±" }
    val compactVariable = !trimmed.any { it.isWhitespace() } &&
        trimmed.length <= 3 &&
        trimmed.any { it.isLetter() }

    return hasMathSyntax || hasLatexCommand || hasMathSymbol || compactVariable
}

/**
 * Detect expressions that require 2D layout — these need KaTeX WebView.
 * Simple expressions (Greek letters, superscripts, basic operators) can
 * render as Unicode text for dramatically better performance.
 */
private fun needsKatexRendering(expression: String): Boolean {
    val complex = listOf(
        "\\frac", "\\dfrac", "\\tfrac", "\\cfrac",     // fractions
        "\\sqrt",                                        // square roots
        "\\binom", "\\choose",                           // binomials
        "\\begin{", "\\end{",                            // environments (matrix, cases, etc.)
        "\\matrix", "\\pmatrix", "\\bmatrix", "\\vmatrix",
        "\\overline", "\\underline",                     // over/under decorations
        "\\overbrace", "\\underbrace",
        "\\overset", "\\underset", "\\stackrel",         // stacked elements
        "\\hat{", "\\vec{", "\\bar{", "\\dot{",         // accents over expressions
        "\\ddot{", "\\tilde{", "\\widehat{", "\\widetilde{",
        "\\left", "\\right",                             // delimiters that scale
        "\\substack",                                    // stacked subscripts
        "\\xleftarrow", "\\xrightarrow",                 // extensible arrows
        "\\cancelto", "\\cancel", "\\bcancel",           // cancel marks
        "\\boxed",                                       // boxed expressions
    )
    return complex.any { expression.contains(it) }
}

/**
 * Render inline math expression as readable text using Unicode substitutions.
 * Avoids WebView overhead for simple inline expressions.
 */
private fun renderInlineMathText(expression: String): String {
    var result = expression.trim()

    // Strip LaTeX command wrappers
    result = result
        .replace("\\mathbb{", "").replace("\\mathcal{", "")
        .replace("\\mathrm{", "").replace("\\mathbf{", "")
        .replace("\\text{", "").replace("\\textrm{", "")
        .replace("\\operatorname{", "")

    // Common LaTeX symbols
    result = result
        .replace("\\alpha", "\u03B1").replace("\\beta", "\u03B2").replace("\\gamma", "\u03B3")
        .replace("\\delta", "\u03B4").replace("\\epsilon", "\u03B5").replace("\\zeta", "\u03B6")
        .replace("\\eta", "\u03B7").replace("\\theta", "\u03B8").replace("\\iota", "\u03B9")
        .replace("\\kappa", "\u03BA").replace("\\lambda", "\u03BB").replace("\\mu", "\u03BC")
        .replace("\\nu", "\u03BD").replace("\\xi", "\u03BE").replace("\\pi", "\u03C0")
        .replace("\\rho", "\u03C1").replace("\\sigma", "\u03C3").replace("\\tau", "\u03C4")
        .replace("\\upsilon", "\u03C5").replace("\\phi", "\u03C6").replace("\\chi", "\u03C7")
        .replace("\\psi", "\u03C8").replace("\\omega", "\u03C9")
        .replace("\\Gamma", "\u0393").replace("\\Delta", "\u0394").replace("\\Theta", "\u0398")
        .replace("\\Lambda", "\u039B").replace("\\Pi", "\u03A0").replace("\\Sigma", "\u03A3")
        .replace("\\Phi", "\u03A6").replace("\\Psi", "\u03A8").replace("\\Omega", "\u03A9")

    // Operators and relations
    result = result
        .replace("\\times", "\u00D7").replace("\\cdot", "\u00B7").replace("\\div", "\u00F7")
        .replace("\\pm", "\u00B1").replace("\\mp", "\u2213")
        .replace("\\leq", "\u2264").replace("\\geq", "\u2265").replace("\\neq", "\u2260")
        .replace("\\le", "\u2264").replace("\\ge", "\u2265").replace("\\ne", "\u2260")
        .replace("\\approx", "\u2248").replace("\\equiv", "\u2261").replace("\\sim", "\u223C")
        .replace("\\propto", "\u221D")
        .replace("\\in", "\u2208").replace("\\notin", "\u2209")
        .replace("\\subset", "\u2282").replace("\\supset", "\u2283")
        .replace("\\subseteq", "\u2286").replace("\\supseteq", "\u2287")
        .replace("\\cup", "\u222A").replace("\\cap", "\u2229")
        .replace("\\forall", "\u2200").replace("\\exists", "\u2203")
        .replace("\\infty", "\u221E").replace("\\nabla", "\u2207").replace("\\partial", "\u2202")
        .replace("\\to", "\u2192").replace("\\rightarrow", "\u2192").replace("\\leftarrow", "\u2190")
        .replace("\\Rightarrow", "\u21D2").replace("\\Leftarrow", "\u21D0")
        .replace("\\iff", "\u21D4").replace("\\Leftrightarrow", "\u21D4")
        .replace("\\sqrt", "\u221A").replace("\\sum", "\u2211").replace("\\prod", "\u220F")
        .replace("\\int", "\u222B")
        .replace("\\ldots", "\u2026").replace("\\cdots", "\u22EF").replace("\\dots", "\u2026")
        .replace("\\langle", "\u27E8").replace("\\rangle", "\u27E9")

    // Superscripts and subscripts
    val superscriptMap = mapOf(
        '0' to '\u2070', '1' to '\u00B9', '2' to '\u00B2', '3' to '\u00B3',
        '4' to '\u2074', '5' to '\u2075', '6' to '\u2076', '7' to '\u2077',
        '8' to '\u2078', '9' to '\u2079', '+' to '\u207A', '-' to '\u207B',
        'n' to '\u207F', 'i' to '\u2071'
    )
    val subscriptMap = mapOf(
        '0' to '\u2080', '1' to '\u2081', '2' to '\u2082', '3' to '\u2083',
        '4' to '\u2084', '5' to '\u2085', '6' to '\u2086', '7' to '\u2087',
        '8' to '\u2088', '9' to '\u2089', '+' to '\u208A', '-' to '\u208B'
    )

    // Handle ^{...} and ^x
    result = Regex("""\^(?:\{([^}]*)\}|([\w]))""").replace(result) { m ->
        val content = m.groupValues[1].ifEmpty { m.groupValues[2] }
        content.map { superscriptMap[it] ?: it }.joinToString("")
    }

    // Handle _{...} and _x
    result = Regex("""_(?:\{([^}]*)\}|([\w]))""").replace(result) { m ->
        val content = m.groupValues[1].ifEmpty { m.groupValues[2] }
        content.map { subscriptMap[it] ?: it }.joinToString("")
    }

    // Clean up remaining braces and backslashes from unhandled commands
    result = Regex("""\\[a-zA-Z]+""").replace(result) { m ->
        m.value.removePrefix("\\")
    }
    result = result.replace("{", "").replace("}", "")

    return result
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
