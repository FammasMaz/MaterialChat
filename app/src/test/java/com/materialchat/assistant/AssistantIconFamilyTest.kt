package com.materialchat.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Icon-family ruling pinned at the source level for the assistant layer (every `.kt` under `assistant/`).
 *
 * The chat and home surfaces draw the Material 3 Expressive icon set from the **Outlined** family
 * (`Icons.Outlined.*`, `Icons.AutoMirrored.Outlined.*`). The assistant pill must read as the same app, so
 * every assistant icon is Outlined unless the chat composer / bubble action row draws the SAME action
 * with a filled glyph — then the pill shares that glyph (consistency with chat wins over blanket
 * Outlined). Those shared filled glyphs are the explicit [allowlist] below, each tied to the chat-side
 * site that justifies it; anything else on `Icons.Default.`, `Icons.Filled.` or
 * `Icons.AutoMirrored.Filled.` fails this scan.
 *
 * Same on-disk scan as [AssistantCopyRulingsTest]: comments and raw strings are exempt; imports are
 * checked too so an unused filled import cannot linger.
 */
class AssistantIconFamilyTest {

    /** (file name, glyph literal as written at the site, reason = the chat-side site it mirrors). */
    private data class Allowed(val file: String, val glyph: String, val reason: String)

    private val allowlist = listOf(
        Allowed("AssistantOverlay.kt", "Icons.AutoMirrored.Filled.Send", "chat composer send: ComposerSendButton.kt"),
        Allowed("AssistantOverlay.kt", "Icons.Default.Stop", "chat composer stop square: ComposerSendButton.kt"),
        Allowed("AssistantOverlay.kt", "Icons.Default.Add", "chat composer attach affordance: MessageInput.kt"),
        Allowed("AssistantOverlay.kt", "Icons.Default.Close", "chat composer dismiss / preview remove: MessageInput.kt"),
        Allowed("AssistantOverlay.kt", "Icons.Default.ContentCopy", "chat bubble copy action: MessageActions.kt"),
        Allowed("AssistantOverlay.kt", "Icons.Default.Refresh", "chat bubble regenerate action: MessageActions.kt"),
        Allowed("AssistantOverlay.kt", "Icons.Filled.AutoAwesome", "chat model glyph: MessageInput.kt, MessageBubble.kt"),
        Allowed("AssistantOverlay.kt", "Icons.Default.PhotoCamera", "chat composer attach sheet: MessageInput.kt"),
        Allowed("AssistantOverlay.kt", "Icons.Default.AddPhotoAlternate", "chat composer attach sheet: MessageInput.kt")
    )

    /** Import-form counterparts of the allowlist (`androidx.compose.material.icons.filled.X`). */
    private val allowedImports: Set<Pair<String, String>> = allowlist.map { entry ->
        val name = entry.glyph.substringAfterLast('.')
        val pkg = if (entry.glyph.startsWith("Icons.AutoMirrored.")) "automirrored.filled" else "filled"
        entry.file to "androidx.compose.material.icons.$pkg.$name"
    }.toSet()

    private val scannedDirectories = listOf("assistant")

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

    private fun scanned(): List<File> {
        val root = mainSourceRoot()
        val files = scannedDirectories.flatMap { entry ->
            val dir = File(root, entry)
            if (dir.isDirectory) dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() else emptyList()
        }.distinctBy { it.canonicalPath }
        assertTrue("expected the assistant layer to contain sources, got ${files.size}", files.size >= 50)
        return files
    }

    private val rawQuote = Regex("\"\"\"")

    private fun isComment(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
    }

    /** Non-comment, non-raw-string code lines with their 1-based number, trailing `// …` stripped. */
    private fun codeLines(file: File): List<Pair<Int, String>> {
        var inRaw = false
        return file.readLines().mapIndexedNotNull { index, line ->
            val wasRaw = inRaw
            if (rawQuote.findAll(line).count() % 2 == 1) inRaw = !inRaw
            if (wasRaw || inRaw || isComment(line)) null
            else (index + 1) to line.substringBefore(" // ")
        }
    }

    private fun report(hits: List<String>, rule: String) =
        assertEquals("$rule:\n${hits.joinToString("\n")}", emptyList<String>(), hits)

    private val filledUse = Regex("\\bIcons\\.(?:Default|Filled|AutoMirrored\\.Filled)\\.[A-Z][A-Za-z0-9]*")
    private val filledImport = Regex("^import\\s+(androidx\\.compose\\.material\\.icons\\.(?:automirrored\\.)?filled\\.[A-Z][A-Za-z0-9]*)\\s*$")

    @Test
    fun `no assistant icon uses the filled family outside the chat-shared allowlist`() {
        val hits = scanned().flatMap { file ->
            codeLines(file).flatMap { (line, text) ->
                filledUse.findAll(text)
                    .map { it.value }
                    .filterNot { glyph -> allowlist.any { it.file == file.name && it.glyph == glyph } }
                    .map { glyph -> "${file.name}:$line $glyph" }
                    .toList()
            }
        }
        report(hits, "assistant icons are Icons.Outlined / Icons.AutoMirrored.Outlined unless the chat composer draws the same action filled (see allowlist)")
    }

    @Test
    fun `no assistant file imports a filled icon outside the chat-shared allowlist`() {
        val hits = scanned().flatMap { file ->
            codeLines(file).mapNotNull { (line, text) ->
                val import = filledImport.find(text.trim())?.groupValues?.get(1) ?: return@mapNotNull null
                if ((file.name to import) in allowedImports) null else "${file.name}:$line $import"
            }
        }
        report(hits, "filled icon imports in the assistant layer must be on the chat-shared allowlist")
    }

    @Test
    fun `every allowlisted glyph is still used so the allowlist cannot silently grow stale`() {
        val sources = scanned().associateBy { it.name }
        val unused = allowlist.filter { entry ->
            val file = sources[entry.file] ?: return@filter true
            codeLines(file).none { (_, text) -> filledUse.findAll(text).any { it.value == entry.glyph } }
        }.map { "${it.file} ${it.glyph} (${it.reason})" }
        report(unused, "allowlist entries must correspond to a live site; drop the entry when the site moves to Outlined")
    }
}
