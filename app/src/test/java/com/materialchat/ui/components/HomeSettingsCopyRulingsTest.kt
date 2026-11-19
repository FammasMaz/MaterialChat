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

    /**
     * Copy that reaches the user from below the UI layer: persona starters, conversation previews
     * and generated titles, failure lines built in the data layer (W8 part 3). Raw strings
     * (`"""…"""`) are model prompts and JSON templates, never shown as copy; log lines are
     * diagnostics. Both are skipped by [userFacingLiterals].
     */
    private val copyDirectories = listOf(
        "domain/usecase",
        "data"
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

    private fun copySources(): List<File> {
        val root = mainSourceRoot()
        val files = copyDirectories.flatMap { entry ->
            val dir = File(root, entry)
            if (!dir.isDirectory) emptyList() else dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }.distinctBy { it.canonicalPath }
        assertTrue("expected the domain/data copy set to contain sources, got ${files.size}", files.size >= 20)
        return files
    }

    private val rawQuote = Regex("\"\"\"")
    private val logLine = Regex("\\bLog\\.[a-z]+\\(|\\bTimber\\.")

    /** Plain string literals outside comments, raw strings and log calls. */
    private fun userFacingLiterals(file: File): List<Pair<Int, String>> {
        var inRaw = false
        return file.readLines().flatMapIndexed { index, line ->
            val wasRaw = inRaw
            if (rawQuote.findAll(line).count() % 2 == 1) inRaw = !inRaw
            if (wasRaw || inRaw || isComment(line) || logLine.containsMatchIn(line)) {
                emptyList()
            } else {
                literal.findAll(line.substringBefore(" // ")).map { (index + 1) to it.groupValues[1] }.toList()
            }
        }
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
    fun `no domain or data copy literal carries an ASCII ellipsis`() {
        val hits = copySources().flatMap { file ->
            userFacingLiterals(file).filter { (_, text) -> text.contains("...") }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "ASCII ... in copy shown to users below the UI layer; use \u2026 (CopyRules.ELLIPSIS)")
    }

    @Test
    fun `the raw string and log exemptions do not hide ordinary literals`() {
        val personas = File(mainSourceRoot(), "domain/usecase/GetBuiltinPersonasUseCase.kt")
        assertTrue(personas.isFile)
        val starters = userFacingLiterals(personas).map { it.second }
        assertTrue("the persona starters must be scanned", starters.any { it.startsWith("Challenge my argument that") })
        val memories = File(mainSourceRoot(), "domain/usecase/ExtractMemoriesUseCase.kt")
        assertTrue(memories.isFile)
        assertTrue(
            "the JSON template inside the extraction prompt is a raw string and must be skipped",
            userFacingLiterals(memories).none { it.second == "..." }
        )
    }

    /**
     * W9 part 3 (`CopyRules` dash rule): no em dash and no spaced en dash joins two clauses in
     * any owned literal, not only in snackbars. Exempt by the ruling: the bare "\u2014" drawn as
     * an empty-value glyph (`ModelAssignmentsScreen`) and the dash between a picker label and
     * its interpolated detail value (`"${'$'}{name} \u2014 ${'$'}{id}"`, the voice pickers), which
     * are separators, not punctuation. Source escapes (`\u2014`) are normalised before the check.
     */
    @Test
    fun `no owned string literal joins two clauses with a dash`() {
        val separator = Regex("\\}\\s*\u2014|\u2014\\s*\\$\\{")
        val hits = owned().flatMap { file ->
            literals(file)
                .map { (line, text) -> line to text.replace("\\u2014", "\u2014").replace("\\u2013", "\u2013") }
                .filter { (_, text) ->
                    (text.contains('\u2014') || text.contains(" \u2013 ")) &&
                        text.trim() != "\u2014" &&
                        !separator.containsMatchIn(text)
                }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "a dash joins no clauses: a comma in a phrase, a full stop in a notice (CopyRules dash rule)")
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
