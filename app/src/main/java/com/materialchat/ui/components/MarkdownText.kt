package com.materialchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    style: TextStyle = MaterialTheme.typography.bodyLarge
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
            codeBlockBackground = codeBlockBackground
        )
    }
}

/**
 * Internal representation of parsed markdown content.
 */
private sealed class MarkdownElement {
    data class TextBlock(val text: AnnotatedString) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String?) : MarkdownElement()
}

/**
 * Renders the parsed markdown content with proper layout for code blocks.
 */
@Composable
private fun MarkdownContent(
    content: List<MarkdownElement>,
    modifier: Modifier = Modifier,
    style: TextStyle,
    codeBlockBackground: Color
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        content.forEach { element ->
            when (element) {
                is MarkdownElement.TextBlock -> {
                    if (element.text.isNotEmpty()) {
                        Text(
                            text = element.text,
                            style = style,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is MarkdownElement.CodeBlock -> {
                    CodeBlockView(
                        code = element.code,
                        language = element.language,
                        backgroundColor = codeBlockBackground
                    )
                }
            }
        }
    }
}

/**
 * Renders a code block with syntax highlighting styling.
 */
@Composable
private fun CodeBlockView(
    code: String,
    language: String?,
    backgroundColor: Color
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color(0xFFD4D4D4) else Color(0xFF1E1E1E)
    val keywordColor = if (isDarkTheme) Color(0xFF569CD6) else Color(0xFF0000FF)
    val stringColor = if (isDarkTheme) Color(0xFFCE9178) else Color(0xFFA31515)
    val commentColor = if (isDarkTheme) Color(0xFF6A9955) else Color(0xFF008000)
    val numberColor = if (isDarkTheme) Color(0xFFB5CEA8) else Color(0xFF098658)

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
        )
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

    // Split by code blocks first
    val codeBlockPattern = Regex("```(\\w*)?\\n?([\\s\\S]*?)```", RegexOption.MULTILINE)
    var lastIndex = 0

    codeBlockPattern.findAll(markdown).forEach { match ->
        // Add text before code block
        if (match.range.first > lastIndex) {
            val textBefore = markdown.substring(lastIndex, match.range.first)
            if (textBefore.isNotBlank()) {
                elements.add(
                    MarkdownElement.TextBlock(
                        parseInlineMarkdown(textBefore, baseColor, codeColor, linkColor)
                    )
                )
            }
        }

        // Add code block
        val language = match.groupValues[1].ifEmpty { null }
        val code = match.groupValues[2].trimEnd()
        elements.add(MarkdownElement.CodeBlock(code, language))

        lastIndex = match.range.last + 1
    }

    // Add remaining text after last code block
    if (lastIndex < markdown.length) {
        val remainingText = markdown.substring(lastIndex)
        if (remainingText.isNotBlank()) {
            elements.add(
                MarkdownElement.TextBlock(
                    parseInlineMarkdown(remainingText, baseColor, codeColor, linkColor)
                )
            )
        }
    }

    // If no code blocks found, parse entire text
    if (elements.isEmpty() && markdown.isNotBlank()) {
        elements.add(
            MarkdownElement.TextBlock(
                parseInlineMarkdown(markdown, baseColor, codeColor, linkColor)
            )
        )
    }

    return elements
}

/**
 * Parse inline markdown formatting (bold, italic, code, links, headers, lists).
 */
private fun parseInlineMarkdown(
    text: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.lines()

        lines.forEachIndexed { index, line ->
            parseLine(line, baseColor, codeColor, linkColor)
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

/**
 * Parse a single line of markdown.
 */
private fun AnnotatedString.Builder.parseLine(
    line: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color
) {
    val trimmedLine = line.trimStart()

    // Handle headers
    when {
        trimmedLine.startsWith("#### ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = baseColor)) {
                parseInlineFormatting(trimmedLine.removePrefix("#### "), baseColor, codeColor, linkColor)
            }
            return
        }
        trimmedLine.startsWith("### ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = baseColor)) {
                parseInlineFormatting(trimmedLine.removePrefix("### "), baseColor, codeColor, linkColor)
            }
            return
        }
        trimmedLine.startsWith("## ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = baseColor)) {
                parseInlineFormatting(trimmedLine.removePrefix("## "), baseColor, codeColor, linkColor)
            }
            return
        }
        trimmedLine.startsWith("# ") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = baseColor)) {
                parseInlineFormatting(trimmedLine.removePrefix("# "), baseColor, codeColor, linkColor)
            }
            return
        }
    }

    // Handle bullet lists
    val bulletMatch = Regex("^\\s*[-*]\\s+(.*)").find(line)
    if (bulletMatch != null) {
        append("  • ")
        parseInlineFormatting(bulletMatch.groupValues[1], baseColor, codeColor, linkColor)
        return
    }

    // Handle numbered lists
    val numberedMatch = Regex("^\\s*(\\d+)\\.\\s+(.*)").find(line)
    if (numberedMatch != null) {
        append("  ${numberedMatch.groupValues[1]}. ")
        parseInlineFormatting(numberedMatch.groupValues[2], baseColor, codeColor, linkColor)
        return
    }

    // Regular text
    parseInlineFormatting(line, baseColor, codeColor, linkColor)
}

/**
 * Parse inline formatting (bold, italic, code, links) within text.
 */
private fun AnnotatedString.Builder.parseInlineFormatting(
    text: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color
) {
    var currentIndex = 0

    // Combined pattern for all inline elements
    // Order matters: ** before *, __ before _
    val pattern = Regex(
        """(\*\*|__)(.*?)\1""" + // Bold
        """|(\*|_)(?!\s)(.*?)(?!\s)\3""" + // Italic (non-greedy, no leading/trailing space)
        """|`([^`]+)`""" + // Inline code
        """|\[([^\]]+)\]\(([^)]+)\)""" // Links
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
