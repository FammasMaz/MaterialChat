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
 *
 * W7 part 1 adds the exception-text rule: no `${x.message}` template and no `x.message ?:`
 * fallback on any non-comment line under chat ownership. Snackbars, error cards and notices
 * take classified copy from [ChatErrorCopy.action] / [ChatErrorCopy.notice]; the one documented
 * pass-through is [ChatErrorCopy.snackbar] for an unclassified reply failure, which reads
 * `error.error.message?.takeIf { … }` and matches neither pattern on purpose.
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

    /**
     * `"…${e.message}…"` and `e.message ?: "…"` (any receiver, `localizedMessage` too) are the
     * two shapes through which an exception's text reached a snackbar or an error card.
     */
    private val exceptionTextInTemplate = Regex("\\$\\{[^}]*\\.(?:localizedM|m)essage\\b[^}]*}")
    // The fallback must be a string literal: `item?.message ?: return` on a domain `Message` is not copy.
    private val exceptionTextAsFallback = Regex("\\.(?:localizedM|m)essage\\s*\\?:\\s*\"")

    /**
     * The rule applies to the UI layer (files under `ui/`), where a string becomes a snackbar, a
     * notice or an error card. The `data/remote` layer builds exception / event messages that the view model
     * classifies before display (an `IOException` is [ChatErrorCopy.NO_CONNECTION], a failed
     * `Result` goes through [ChatErrorCopy.action], an in-stream provider error body is the
     * documented unclassified-snackbar diagnostic), so its `${e.message}` lines are inputs, not copy.
     */
    private fun uiFiles(): List<File> = ownedFiles().filter { it.canonicalPath.contains("/ui/") }

    @Test
    fun `no exception text reaches a user-visible string under chat ownership`() {
        val files = uiFiles()
        assertTrue("expected UI files under chat ownership, got ${files.size}", files.size >= 10)
        val hits = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (isComment(line)) return@mapIndexedNotNull null
                val code = line.substringBefore(" // ")
                if (exceptionTextInTemplate.containsMatchIn(code) || exceptionTextAsFallback.containsMatchIn(code)) {
                    "${file.name}:${index + 1} ${code.trim()}"
                } else null
            }
        }
        assertEquals(
            "exception text in copy; classify through ChatErrorCopy.action/notice instead:\n${hits.joinToString("\n")}",
            emptyList<String>(),
            hits
        )
    }

    @Test
    fun `failed-action snackbar lines are one phrase in the Could not family`() {
        val lines = listOf(
            ChatErrorCopy.COULD_NOT_LOAD_MODELS,
            ChatErrorCopy.COULD_NOT_CHANGE_MODEL,
            ChatErrorCopy.COULD_NOT_BRANCH,
            ChatErrorCopy.COULD_NOT_RESEND_EDIT,
            ChatErrorCopy.COULD_NOT_EXPORT,
            ChatErrorCopy.COULD_NOT_SHARE,
            ChatErrorCopy.COULD_NOT_REDO,
            ChatErrorCopy.COULD_NOT_UPDATE_BOOKMARK,
            ChatErrorCopy.COULD_NOT_RUN_FUSION,
            ChatErrorCopy.couldNotLoadModels("OpenAI")
        )
        for (line in lines) {
            assertTrue(line, line.startsWith("Could not "))
            assertTrue("snackbar lines carry no trailing period: $line", !line.endsWith("."))
            assertTrue("no ASCII ellipsis: $line", !line.contains("..."))
        }
        assertEquals(ChatErrorCopy.COULD_NOT_LOAD_MODELS, ChatErrorCopy.couldNotLoadModels(null))
        assertEquals(ChatErrorCopy.COULD_NOT_LOAD_MODELS, ChatErrorCopy.couldNotLoadModels(" "))
        // The error card's supporting line is a notice: full stops, the last sentence names the action.
        val card = ChatErrorCopy.COULD_NOT_LOAD_CONVERSATION
        assertTrue(card, card.startsWith("Could not "))
        assertTrue(card, card.endsWith("."))
        assertTrue(card, card.contains("Try again"))
    }
}
