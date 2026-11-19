package com.materialchat.ui.screens.settings

import com.materialchat.ui.theme.CopyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every notice `AssistantVoiceSettingsViewModel` can put in front of the user — `message`,
 * `previewMessage` and the model picker's `error` — passes [CopyRules.isNotice]: one or more
 * complete sentences each ending with a full stop, sentence case, no "please", no exclamation
 * mark, not opened by a banned failure verb. The retry verb inside a sentence is "try again",
 * never "retry" ("Please retry." / "Reopen to retry." were the W6 survivors, theme row H5).
 *
 * Scanned on disk (W8 part 1) so an inline literal regresses here rather than in the dialog.
 * Notices the ViewModel forwards from the voice layer (`VoiceCopy`, `sherpaFallbackMessage`)
 * are the assistant owner's and are pinned by `AssistantCopyTest`.
 */
class AssistantVoiceSettingsNoticeRulesTest {

    private val assignment = Regex(
        "(?:message|previewMessage)\\.value\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"" +
            "|\\berror\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\""
    )

    private val retryVerb = Regex("\\bretry\\b", RegexOption.IGNORE_CASE)

    private fun source(): File {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "app/src/main/java/com/materialchat") }
            .firstOrNull { it.isDirectory }
            ?: generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
                .map { File(it, "src/main/java/com/materialchat") }
                .firstOrNull { it.isDirectory }
        assertNotNull("main source root not found from ${System.getProperty("user.dir")}", root)
        val file = File(root!!, "ui/screens/settings/AssistantVoiceSettingsViewModel.kt")
        assertTrue("${file.path} missing", file.isFile)
        return file
    }

    private fun notices(): List<Pair<Int, String>> = source().readLines().flatMapIndexed { index, line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
            emptyList()
        } else {
            assignment.findAll(line).map { match ->
                (index + 1) to (match.groupValues[1].ifEmpty { match.groupValues[2] })
            }.toList()
        }
    }

    @Test
    fun `the scanner finds the notice assignments`() {
        val found = notices()
        assertTrue("expected at least 8 literal notices in the ViewModel, found ${found.size}", found.size >= 8)
        assertTrue(found.any { it.second.startsWith("Voice settings saved") })
        assertTrue(found.any { it.second.startsWith("Could not play the voice preview") })
    }

    @Test
    fun `every literal notice passes CopyRules isNotice`() {
        val failures = notices().filterNot { (_, text) -> CopyRules.isNotice(text) }
            .map { (line, text) -> "AssistantVoiceSettingsViewModel.kt:$line \"$text\"" }
        assertEquals("notices are complete sentences ending with a full stop:\n${failures.joinToString("\n")}", emptyList<String>(), failures)
    }

    @Test
    fun `no literal notice uses the retry verb`() {
        val failures = notices().filter { (_, text) -> retryVerb.containsMatchIn(text) }
            .map { (line, text) -> "AssistantVoiceSettingsViewModel.kt:$line \"$text\"" }
        assertEquals("the retry verb inside a notice is \"try again\":\n${failures.joinToString("\n")}", emptyList<String>(), failures)
    }
}
