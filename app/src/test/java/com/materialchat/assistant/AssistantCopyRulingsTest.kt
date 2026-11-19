package com.materialchat.assistant

import com.materialchat.assistant.ui.AssistantCopy
import com.materialchat.assistant.voice.SpeechRecognitionErrors
import com.materialchat.ui.theme.CopyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copy rulings pinned at the source level for the assistant layer (every file under `assistant/`) and the three
 * data-layer sinks whose text reaches the user outside the UI owners' scanners (W9 part 5):
 * `UpdateManager` (the settings update card), `MonetizationManager` (the settings snackbar through
 * `SettingsViewModel`) and `NativeAuthManager` (the loopback page the browser shows).
 *
 * Same on-disk scan as `ChatCopyRulingsTest` and `HomeSettingsCopyRulingsTest`:
 * - exception text never reaches a typed result or an event: no `x.message ?:` fallback and no
 *   `${x.message}` interpolation, except where one exception wraps another (`throw …(…)`);
 * - provider text never reaches the user: no `x.message.ifBlank { … }` / `x.debugMessage.ifBlank { … }`
 *   with a fixed line as the fallback (the fixed line is the line);
 * - the failure verb family is "Could not …" ([CopyRules.BANNED_FAILURE_VERBS]);
 * - no "please" in a line; the parser's one-word filler token is vocabulary, a thrown exception's
 *   text is diagnostics the ViewModel above maps to a fixed line (`NativeAuthManager`'s device-code
 *   expiry is caught at `SettingsViewModel` and shown as `CopyRules.failureLine("sign in")`), and
 *   `SpeechRecognitionManager`'s `ERROR_CLIENT` diagnostics are allowed only while the pill maps
 *   the code to [AssistantCopy.RECOGNITION_FAILED] instead of showing them;
 * - the retry action is "Try again", never "Retry"; ellipses are the typographic character.
 *
 * Comments, raw strings and log lines are exempt.
 */
class AssistantCopyRulingsTest {

    private val scannedDirectories = listOf("assistant")

    private val scannedSinks = listOf(
        "data/repository/UpdateManager.kt",
        "data/monetization/MonetizationManager.kt",
        "data/auth/NativeAuthManager.kt"
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

    private fun scanned(): List<File> {
        val root = mainSourceRoot()
        val fromDirectories = scannedDirectories.flatMap { entry ->
            val dir = File(root, entry)
            if (dir.isDirectory) dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() else emptyList()
        }
        val sinks = scannedSinks.map { File(root, it) }
        for (sink in sinks) assertTrue("sink must exist: ${sink.path}", sink.isFile)
        val distinct = (fromDirectories + sinks).distinctBy { it.canonicalPath }
        assertTrue("expected the assistant layer to contain sources, got ${distinct.size}", distinct.size >= 50)
        return distinct
    }

    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val rawQuote = Regex("\"\"\"")
    private val logLine = Regex("\\bLog\\.[a-z]+\\(|\\bTimber\\.")

    private fun isComment(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
    }

    /** Non-comment, non-raw-string, non-log code lines with their 1-based number. */
    private fun codeLines(file: File): List<Pair<Int, String>> {
        var inRaw = false
        return file.readLines().mapIndexedNotNull { index, line ->
            val wasRaw = inRaw
            if (rawQuote.findAll(line).count() % 2 == 1) inRaw = !inRaw
            if (wasRaw || inRaw || isComment(line) || logLine.containsMatchIn(line)) null
            else (index + 1) to line.substringBefore(" // ")
        }
    }

    private fun literals(file: File): List<Pair<Int, String>> = codeLines(file).flatMap { (line, text) ->
        literal.findAll(text).map { line to it.groupValues[1] }.toList()
    }

    private fun report(hits: List<String>, rule: String) =
        assertEquals("$rule:\n${hits.joinToString("\n")}", emptyList<String>(), hits)

    // One exception wrapping another's text (`throw IOException("… ${e.message}")`,
    // `SherpaLoadException(error.message ?: "load failed")`) is diagnostics the layer above
    // classifies; the typed results and events the user sees are what the two rules below guard.
    private val wrapsException = Regex("\\bthrow\\b|Exception\\(")
    private val exceptionTextAsFallback = Regex("\\.(?:localizedM|m)essage\\s*\\?:")
    private val exceptionTextInTemplate = Regex("\\$\\{[^}]*\\.(?:localizedM|m)essage\\b[^}]*}")
    private val providerTextAsFallback = Regex("\\.(?:debugM|m)essage\\s*\\.ifBlank\\s*\\{")

    @Test
    fun `no exception message falls back into a typed result or event`() {
        val hits = scanned().flatMap { file ->
            codeLines(file).filterNot { (_, text) -> wrapsException.containsMatchIn(text) }
                .filter { (_, text) -> exceptionTextAsFallback.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line ${text.trim()}" }
        }
        report(hits, "exception text must not reach the user; use a fixed VoiceCopy / CopyRules line")
    }

    @Test
    fun `no string literal interpolates an exception message`() {
        val hits = scanned().flatMap { file ->
            codeLines(file).filterNot { (_, text) -> wrapsException.containsMatchIn(text) }
                .filter { (_, text) -> exceptionTextInTemplate.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line ${text.trim()}" }
        }
        report(hits, "exception text must not be interpolated into user copy")
    }

    @Test
    fun `no provider message is shown with a fixed line as its mere fallback`() {
        val hits = scanned().flatMap { file ->
            codeLines(file).filter { (_, text) -> providerTextAsFallback.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line ${text.trim()}" }
        }
        report(hits, "billing / ad SDK text must not reach the snackbar; emit the fixed line and log the detail")
    }

    @Test
    fun `no string literal opens with a banned failure verb`() {
        val hits = scanned().flatMap { file ->
            literals(file).filter { (_, text) -> CopyRules.BANNED_FAILURE_VERBS.any { text.startsWith(it) } }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "the failure verb family is \"Could not …\" (CopyRules.FAILURE_VERB)")
    }

    @Test
    fun `no string literal says please`() {
        val please = Regex("\\bplease\\b", RegexOption.IGNORE_CASE)
        val hits = scanned().flatMap { file ->
            // A thrown exception's text is diagnostics the layer above maps (the sign-in flow's
            // `throw IOException("… expired. Please try again.")` is shown as a fixed line by
            // SettingsViewModel); a literal that is itself the result or the page is copy.
            codeLines(file).filterNot { (_, text) -> wrapsException.containsMatchIn(text) }
                .flatMap { (line, text) -> literal.findAll(text).map { line to it.groupValues[1] }.toList() }
                // Copy is sentence case (CopyRules): a literal opening in lower case is a token
                // (the command parser's filler list: "please", "can you please"), not a line.
                .filter { (_, text) -> text.trim().firstOrNull()?.isLowerCase() != true && please.containsMatchIn(text) }
                .filter { (_, text) ->
                    if (file.name != "SpeechRecognitionManager.kt") return@filter true
                    // The manager's ERROR_CLIENT diagnostics are pinned by SpeechRecognitionManagerTest
                    // and never shown: the pill maps the code to RECOGNITION_FAILED (AssistantCopyTest).
                    // A diagnostic the pill would show verbatim is a hit.
                    AssistantCopy.recognitionError(SpeechRecognitionErrors.ERROR_CLIENT, text) != AssistantCopy.RECOGNITION_FAILED
                }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "copy is addressed to the user without \"please\"")
    }

    @Test
    fun `no string literal is the Retry label`() {
        val retry = Regex("\\bRetry\\b")
        val hits = scanned().flatMap { file ->
            literals(file).filter { (_, text) -> retry.containsMatchIn(text) }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "\"Retry\" is banned app-wide; the action is \"Try again\"")
    }

    @Test
    fun `no string literal carries an ASCII ellipsis`() {
        val hits = scanned().flatMap { file ->
            literals(file).filter { (_, text) -> text.contains("...") }
                .map { (line, text) -> "${file.name}:$line \"$text\"" }
        }
        report(hits, "ASCII ... in copy; use \u2026 (CopyRules.ELLIPSIS)")
    }

    /**
     * The shapes this scanner exists for, as they stood at v2.41.8 before W9 part 5 rewrote them:
     * each one must be a hit, so a later edit to a pattern cannot silently let them back in.
     */
    @Test
    fun `the scanner recognises the shapes it was written against`() {
        val fallbackSites = listOf(
            "            TtsInitResult.Error(error.message ?: \"Failed to initialize text-to-speech\")",
            "                TtsInitResult.Error(error.message ?: \"Voice configuration failed\")",
            "            apply(model, SherpaInstallEvent.Failed(failure.message ?: VoiceCopy.INSTALL_FAILED))",
            "                        message = error.message ?: \"Failed to check for updates\","
        )
        for (site in fallbackSites) {
            assertTrue(site, exceptionTextAsFallback.containsMatchIn(site))
            assertTrue(site, !wrapsException.containsMatchIn(site))
        }
        // Wrapping is exempt: the layer above classifies the thrown exception.
        assertTrue(wrapsException.containsMatchIn("            throw SherpaLoadException(error.message ?: \"load failed\")"))
        assertTrue(wrapsException.containsMatchIn("                    throw Exception(\"Download failed: HTTP ${'$'}{response.code}\")"))
        val providerSites = listOf(
            "                _events.tryEmit(adError.message.ifBlank { \"Could not show rewarded ad\" })",
            "                _events.tryEmit(result.debugMessage.ifBlank { \"Could not start purchase\" })",
            "                _events.tryEmit(billingResult.debugMessage.ifBlank { \"Purchase failed\" })"
        )
        for (site in providerSites) assertTrue(site, providerTextAsFallback.containsMatchIn(site))
        assertTrue(exceptionTextInTemplate.containsMatchIn("\"Could not share: ${'$'}{e.message}\""))
        val please = Regex("\\bplease\\b", RegexOption.IGNORE_CASE)
        for (line in listOf(
            "APK file not found. Please download again.",
            "<h1>State mismatch</h1><p>Please retry sign-in.</p>",
            "Rewarded ad is loading. Please try again shortly."
        )) assertTrue(line, please.containsMatchIn(line) && line.trim().first().isLowerCase().not())
        // The parser's filler tokens open in lower case and are vocabulary, not copy.
        for (token in listOf("please", "can you please", "could you please")) assertTrue(token, token.first().isLowerCase())
        assertTrue(CopyRules.BANNED_FAILURE_VERBS.any { "Failed to initialize text-to-speech".startsWith(it) })
    }

    @Test
    fun `the three data sinks are in the scanned set`() {
        val names = scanned().map { it.name }.toSet()
        for (sink in listOf("UpdateManager.kt", "MonetizationManager.kt", "NativeAuthManager.kt", "TextToSpeechManager.kt", "SherpaModelStore.kt")) {
            assertTrue(sink, sink in names)
        }
    }
}
