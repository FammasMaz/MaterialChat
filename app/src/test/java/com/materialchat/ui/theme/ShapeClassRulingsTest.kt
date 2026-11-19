package com.materialchat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The corner class ruling (W9, cross-surface audit W8 #1 / #10), pinned at the source level:
 *
 * - containers (every `Card` / `ElevatedCard` / `OutlinedCard` / `AlertDialog` /
 *   `ModalBottomSheet` call outside the theme) rest on a [SquircleShape] token of
 *   [ExpressiveShapes] or a file-local `SquircleShape(...)`, never on the M3 default
 *   (`MaterialChatShapes.medium`, a round corner), never on a `RoundedCornerShape`, never
 *   on `CircleShape`;
 * - dialogs rest on [ExpressiveShapes.Dialog] and modal sheets on [ExpressiveShapes.BottomSheet];
 * - pressables stay on circular corners: [CustomShapes.Button] / [ExpressiveShapes.Chip] are
 *   [RoundedCornerShape]s and the container tokens are squircles.
 *
 * Same on-disk scan as `ChatCopyRulingsTest` / `HomeSettingsCopyRulingsTest`, so a new card
 * left on the default shape fails here without a device.
 */
class ShapeClassRulingsTest {

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

    /** Every Kotlin file outside `ui/theme` (the theme defines the tokens; it draws no card). */
    private fun scannedFiles(): List<File> {
        val root = mainSourceRoot()
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.canonicalPath.contains("/ui/theme/") }
            .toList()
        assertTrue("expected the app sources, got ${files.size}", files.size >= 100)
        return files
    }

    private fun isComment(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
    }

    private val containerCall = Regex("(?<![A-Za-z0-9_.])(ElevatedCard|OutlinedCard|Card|AlertDialog|ModalBottomSheet)\\(")
    private val shapeArgument = Regex("(?<![A-Za-z0-9_])shape\\s*=\\s*([^,)]+)")

    /** A call site: the composable, its line, and the `shape =` value found in its argument list. */
    private data class Site(val file: File, val line: Int, val composable: String, val shape: String?)

    /**
     * The `shape =` argument of a call is looked for from the call line to the first line that
     * opens the content lambda (`) {`) or, failing that, 40 lines down. Nested calls inside the
     * content are past that boundary, so a card's content cannot lend it a shape.
     */
    private fun sites(file: File): List<Site> {
        val lines = file.readLines()
        return lines.flatMapIndexed { index, line ->
            if (isComment(line)) return@flatMapIndexed emptyList<Site>()
            containerCall.findAll(line.substringBefore(" // ")).map { match ->
                var shape: String? = null
                var depth = 0
                var closed = false
                for (i in index until minOf(lines.size, index + 40)) {
                    val code = lines[i].substringBefore(" // ")
                    if (!isComment(lines[i])) {
                        shapeArgument.find(code)?.let { if (shape == null && depth <= 1) shape = it.groupValues[1].trim() }
                        for (ch in code) {
                            if (ch == '(') depth++
                            if (ch == ')') { depth--; if (depth == 0 && i >= index) closed = true }
                        }
                    }
                    if (closed) break
                }
                Site(file, index + 1, match.groupValues[1], shape)
            }.toList()
        }
    }

    private fun allSites(): List<Site> {
        val found = scannedFiles().flatMap { sites(it) }
        assertTrue("expected the container call sites to be found, got ${found.size}", found.size >= 40)
        return found
    }

    private val squircleToken = Regex("^ExpressiveShapes\\.(Card|Sheet|BottomSheet|Dialog|Notice|ContentBlock|DisclosurePanel|Tile|TileBadge|MenuSelection|AnswerSurface|NoticeSurface|ThinkingSurface)$")

    /** A file-local `val name = SquircleShape(...)` (the provider card's animated radius). */
    private fun isLocalSquircle(file: File, identifier: String): Boolean =
        Regex("\\bval\\s+${Regex.escape(identifier)}\\s*(?::\\s*Shape)?\\s*=\\s*(?:remember[^{]*\\{\\s*)?SquircleShape\\(").containsMatchIn(file.readText())

    private fun isSquircle(site: Site): Boolean {
        val shape = site.shape ?: return false
        return squircleToken.matches(shape) || shape.startsWith("SquircleShape(") || isLocalSquircle(site.file, shape)
    }

    @Test
    fun `every card dialog and sheet outside the theme rests on a squircle token`() {
        val hits = allSites().filterNot { isSquircle(it) }
            .map { "${it.file.name}:${it.line} ${it.composable}( shape = ${it.shape ?: "<default>"}" }
        assertEquals(
            "containers are squircles (ExpressiveShapes.Card / Sheet / BottomSheet / Dialog or a local SquircleShape); never the M3 default, a RoundedCornerShape or CircleShape:\n${hits.joinToString("\n")}",
            emptyList<String>(),
            hits
        )
    }

    @Test
    fun `dialogs rest on the Dialog token and modal sheets on the BottomSheet token`() {
        val sites = allSites()
        val dialogs = sites.filter { it.composable == "AlertDialog" }
        val sheets = sites.filter { it.composable == "ModalBottomSheet" }
        assertTrue("expected the dialogs to be found, got ${dialogs.size}", dialogs.size >= 8)
        assertTrue("expected the sheets to be found, got ${sheets.size}", sheets.size >= 5)
        val wrongDialogs = dialogs.filter { it.shape != "ExpressiveShapes.Dialog" }
            .map { "${it.file.name}:${it.line} shape = ${it.shape ?: "<default>"}" }
        assertEquals("every AlertDialog is ExpressiveShapes.Dialog:\n${wrongDialogs.joinToString("\n")}", emptyList<String>(), wrongDialogs)
        val wrongSheets = sheets.filter { it.shape != "ExpressiveShapes.BottomSheet" }
            .map { "${it.file.name}:${it.line} shape = ${it.shape ?: "<default>"}" }
        assertEquals("every ModalBottomSheet is ExpressiveShapes.BottomSheet:\n${wrongSheets.joinToString("\n")}", emptyList<String>(), wrongSheets)
    }

    @Test
    fun `the persona card is scanned and rests on the card squircle`() {
        val persona = allSites().filter { it.file.name == "PersonaCard.kt" && it.composable == "Card" }
        assertEquals("PersonaCard draws one Card", 1, persona.size)
        assertEquals("ExpressiveShapes.Card", persona.single().shape)
    }

    @Test
    fun `pressables are round and containers are squircles`() {
        // Pressable class: circular corners that a press morph animates as one Dp.
        assertTrue(CustomShapes.Button is RoundedCornerShape)
        assertTrue(CustomShapes.ButtonPressed is RoundedCornerShape)
        assertTrue(CustomShapes.Pill is RoundedCornerShape)
        assertTrue(ExpressiveShapes.Chip is RoundedCornerShape)
        // Container class: G2 squircles, static at rest and on press.
        assertTrue(ExpressiveShapes.Card is SquircleShape)
        assertTrue(ExpressiveShapes.Sheet is SquircleShape)
        assertTrue(ExpressiveShapes.Dialog is SquircleShape)
        assertTrue(ExpressiveShapes.Notice is SquircleShape)
        assertTrue(ExpressiveShapes.ContentBlock is SquircleShape)
        assertTrue(ExpressiveShapes.Tile is SquircleShape)
        assertTrue(ExpressiveShapes.BottomSheet is TopSquircleShape)
    }
}
