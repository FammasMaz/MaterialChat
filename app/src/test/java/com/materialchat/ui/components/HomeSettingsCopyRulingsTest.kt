package com.materialchat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copy rulings pinned at the source level for every main-source file under home+settings
 * ownership (W7 brief, cross-surface audit W6 #2, #3, #4, #14):
 *
 * - exception text never reaches the user: no `x.message ?: "..."` fallback chain and no
 *   `${x.message}` interpolation anywhere in the owned sources;
 * - the failure verb family is "Could not …": no user-visible literal starts with "Failed";
 * - the retry action is "Try again" app-wide, never "Retry";
 * - ellipses are the typographic character, never three ASCII periods.
 *
 * Same on-disk scan as chat's `ChatCopyRulingsTest`, so a regression in any owned file fails
 * here even when the label is built inline.
 */
class HomeSettingsCopyRulingsTest {

    private val ownedDirectories = listOf(
        "ui/screens/conversations",
        "ui/screens/settings",
        "ui/screens/bookmarks",
        "ui/screens/memories",
        "ui/screens/workflows",
        "ui/screens/personas",
        "ui/screens/insights",
        "ui/screens/arena",
        "ui/screens/canvas",
        "ui/screens/mindmap",
        "ui/screens/explore",
        "ui/screens/search",
        "ui/screens/onboarding",
        "ui/screens/generatedimages",
        "ui/navigation",
        "ui/components"
    )

    private val ownedFiles = listOf(
        "ui/MainViewModel.kt",
        "MainActivity.kt",
        "data/auth/NativeAuthManager.kt"
    )

    /** `ui/components` files that belong to the theme or chat owner. */
    private val notOwnedComponents = setOf(
        "MathWebViewTouch.kt",
        "TapThroughScroll.kt",
        "ExpressiveSurfaces.kt",
        "ExpressiveSnackbar.kt",
        "ErrorContent.kt",
        "SheetHeader.kt"
    )

    private fun isOwnedComponent(file: File): Boolean =
        !file.name.startsWith("Markdown") && file.name !in notOwnedComponents

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

    private fun owned(): List<File> {
        val root = mainSourceRoot()
        val fromDirectories = ownedDirectories.flatMap { entry ->
            val dir = File(root, entry)
            if (!dir.isDirectory) return@flatMap emptyList<File>()
            val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            if (entry == "ui/components") files.filter { it.parentFile == dir && isOwnedComponent(it) } else files
        }
        val single = ownedFiles.map { File(root, it) }.filter { it.isFile }
        val distinct = (fromDirectories + single).distinctBy { it.canonicalPath }
        assertTrue("expected the home+settings ownership set to contain sources, got ${distinct.size}", distinct.size >= 40)
        return distinct
    }

    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    private fun isComment(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
    }

    private fun codeLines(file: File): List<Pair<Int, String>> =
        file.readLines().mapIndexedNotNull { index, line ->
            if (isComment(line)) null else (index + 1) to line.substringBefore(" // ")
        }

    private fun literals(file: File): List<Pair<Int, String>> = codeLines(file).flatMap { (line, text) ->
        literal.findAll(text).map { line to it.groupValues[1] }.toList()
    }

    private fun report(hits: List<String>, rule: String) =
        assertEquals("$rule:\n${hits.joinToString("\n")}", emptyList<String>(), hits)

    @Test
    fun `no owned source falls back from an exception message into user copy`() {
        val fallback = Regex("\\.message\\s*\\?:")
        val hits = owned().flatMap { file ->
            codeLines(file).filter { (_, text) -> fallback.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line ${text.trim()}" }
        }
        report(hits, "exception text must not reach the user; use a fixed \"Could not …\" line")
    }

    @Test
    fun `no owned string literal interpolates an exception message`() {
        val interpolation = Regex("\\$\\{[^}]*\\.message\\}")
        // Wrapping one exception's text into another (`throw IOException("... ${e.message}")`)
        // is diagnostics, not copy; the ViewModels above it map the thrown exception to a line.
        val wrapsException = Regex("\\bthrow\\b|Exception\\(")
        val hits = owned().flatMap { file ->
            codeLines(file).filterNot { (_, text) -> wrapsException.containsMatchIn(text) }
                .flatMap { (line, text) -> literal.findAll(text).map { line to it.groupValues[1] }.toList() }
                .filter { (_, text) -> interpolation.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "exception text must not be interpolated into user copy")
    }

    @Test
    fun `no owned string literal uses the Failed verb family`() {
        val failed = Regex("^Failed\\b")
        val hits = owned().flatMap { file ->
            literals(file).filter { (_, text) -> failed.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "the failure verb family is \"Could not …\"")
    }

    @Test
    fun `no owned string literal is the Retry label`() {
        val retry = Regex("\\bRetry\\b")
        val hits = owned().flatMap { file ->
            literals(file).filter { (_, text) -> retry.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "\"Retry\" is banned app-wide; the action is \"Try again\"")
    }

    @Test
    fun `no owned string literal carries an ASCII ellipsis`() {
        val hits = owned().flatMap { file ->
            literals(file).filter { (_, text) -> text.contains("...") }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "ASCII ... in copy; use \u2026")
    }

    @Test
    fun `no owned snackbar literal ends with an exclamation mark`() {
        val snackbar = Regex("ShowSnackbar\\(|showSnackbar\\(")
        val hits = owned().flatMap { file ->
            codeLines(file).filter { (_, text) -> snackbar.containsMatchIn(text) }
                .flatMap { (line, text) ->
                    literal.findAll(text).map { line to it.groupValues[1] }.toList()
                }
                .filter { (_, text) -> text.endsWith("!") || text.contains("\u2014") }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "snackbars are one plain phrase: no exclamation mark, no em dash")
    }
}
