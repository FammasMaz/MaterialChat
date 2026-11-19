package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copy rulings pinned at the source level for every main-source file under chat ownership:
 * the retry action is "Try again" app-wide (never "Retry"), and ellipses are the typographic
 * character, never three ASCII periods, inside any string literal.
 *
 * The scan reads the files on disk (same path resolution as `AssistantKeyboardSpacingTest`),
 * so a regression in any chat-owned file fails here even when the label is built inline
 * instead of through [ChatErrorCopy].
 */
class ChatCopyRulingsTest {

    private val ownership = listOf(
        "ui/screens/chat",
        "ui/components/MarkdownText.kt",
        "ui/components/ImageViewerDialog.kt",
        "data/remote/sse",
        "data/remote/api",
        "domain/model/StreamingState.kt",
        "util/AttachmentLimits.kt"
    )

    private fun mainSourceRoot(): File {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/java/com/materialchat") }
            .firstOrNull { it.isDirectory }
            ?: generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
                .map { File(it, "src/main/java/com/materialchat") }
                .firstOrNull { it.isDirectory }
        assertNotNull("main source root not found from ${System.getProperty("user.dir")}", root)
        return root!!
    }

    private fun ownedFiles(): List<File> {
        val root = mainSourceRoot()
        val files = ownership.flatMap { entry ->
            val f = File(root, entry)
            when {
                f.isDirectory -> f.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                f.isFile -> listOf(f)
                else -> emptyList()
            }
        } + root.resolve("ui/components").listFiles { c -> c.isFile && c.name.startsWith("Markdown") && c.extension == "kt" }.orEmpty()
        val distinct = files.distinctBy { it.canonicalPath }
        assertTrue("expected the chat ownership set to contain sources, got ${distinct.size}", distinct.size >= 10)
        return distinct
    }

    /** String literals on a line, without the quotes. Raw (triple-quoted) strings are not used for copy. */
    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    // Comment lines (line comments, block-comment openers, KDoc star lines) carry no copy;
    // a quoted sample inside one is not a label.
    @Suppress("NOTHING_TO_INLINE")
    private fun isComment(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
    }

    private fun literals(file: File): List<Pair<Int, String>> = file.readLines().flatMapIndexed { index, line ->
        if (isComment(line)) emptyList()
        else literal.findAll(line.substringBefore(" // ")).map { (index + 1) to it.groupValues[1] }.toList()
    }

    @Test
    fun `no string literal under chat ownership is the Retry label`() {
        val retry = Regex("\\bRetry\\b")
        val hits = ownedFiles().flatMap { file ->
            literals(file).filter { (_, text) -> retry.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        assertEquals("\"Retry\" is banned app-wide; the action is \"Try again\":\n${hits.joinToString("\n")}", emptyList<String>(), hits)
    }

    @Test
    fun `no string literal under chat ownership carries an ASCII ellipsis`() {
        val hits = ownedFiles().flatMap { file ->
            literals(file).filter { (_, text) -> text.contains("...") }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        assertEquals("ASCII ... in copy; use \u2026:\n${hits.joinToString("\n")}", emptyList<String>(), hits)
    }

    @Test
    fun `the retry action label is Try again`() {
        assertEquals("Try again", ChatErrorCopy.TRY_AGAIN)
    }
}
