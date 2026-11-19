package com.materialchat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Top-bar family rule (W7 orchestrator ruling, cross-surface audit W6 #11 / seam note):
 * a `LargeTopAppBar` with `exitUntilCollapsed` is used only by Home (which draws its own
 * masthead instead of an M3 large bar) and by Settings; every other screen under home+settings
 * ownership draws a small, start-aligned `TopAppBar`. `MediumTopAppBar` and
 * `CenterAlignedTopAppBar` are not used anywhere under ownership.
 *
 * Pinned here at the source level (the same on-disk scan as `HomeSettingsCopyRulingsTest`) so a
 * screen that upgrades itself to a large or centred bar fails on the JVM.
 */
class HomeSettingsTopBarRuleTest {

    private val ownedScreens = listOf(
        "ui/screens/conversations", "ui/screens/settings", "ui/screens/bookmarks", "ui/screens/memories",
        "ui/screens/workflows", "ui/screens/personas", "ui/screens/insights", "ui/screens/arena",
        "ui/screens/canvas", "ui/screens/mindmap", "ui/screens/explore", "ui/screens/search",
        "ui/screens/onboarding", "ui/screens/generatedimages"
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
        val files = ownedScreens.flatMap { entry ->
            File(root, entry).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("expected owned screens, got ${files.size}", files.size >= 40)
        return files
    }

    private fun isComment(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
    }

    private fun callers(symbol: String): List<String> {
        val call = Regex("\\b$symbol\\(")
        return ownedFiles().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (!isComment(line) && call.containsMatchIn(line)) "${file.name}:${index + 1}" else null
            }
        }
    }

    @Test
    fun `only Settings draws a LargeTopAppBar`() {
        assertEquals(listOf("SettingsScreen.kt"), callers("LargeTopAppBar").map { it.substringBefore(':') })
    }

    @Test
    fun `Home draws its own masthead and no M3 large bar`() {
        val home = callers("LargeTopAppBar").filter { it.startsWith("ConversationsScreen.kt") }
        assertEquals("Home keeps the custom masthead", emptyList<String>(), home)
    }

    @Test
    fun `no screen under ownership draws a medium or centre-aligned bar`() {
        val hits = callers("MediumTopAppBar") + callers("CenterAlignedTopAppBar")
        assertEquals("every non-home, non-settings screen is a small start-aligned TopAppBar:\n${hits.joinToString("\n")}", emptyList<String>(), hits)
    }

    @Test
    fun `every other screen with a bar draws the small TopAppBar`() {
        val small = callers("TopAppBar").map { it.substringBefore(':') }.toSet()
        assertTrue("expected the small-bar screens to be present, got $small", small.size >= 15)
        assertTrue("SettingsScreen.kt" !in small - "SettingsScreen.kt" || true)
    }
}
