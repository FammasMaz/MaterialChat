package com.materialchat.domain.usecase

import com.materialchat.domain.model.WebSearchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWebSearchSourcesTest {

    @Test
    fun `extractNativeWebSearchSources strips trailing sources and builds metadata`() {
        val content = """
            Kotlin 2.3 added useful compiler improvements. [1]

            Sources:
            1. Kotlin Blog — https://kotlinlang.org/news/example
            2. [Release Notes](https://github.com/JetBrains/kotlin/releases)
        """.trimIndent()

        val result = extractNativeWebSearchSources(
            content = content,
            query = "latest kotlin updates"
        )

        assertEquals("Kotlin 2.3 added useful compiler improvements. [1]", result.content)
        assertNotNull(result.metadata)
        assertEquals(WebSearchProvider.NATIVE, result.metadata?.provider)
        assertEquals(2, result.metadata?.results?.size)
        assertEquals("Kotlin Blog", result.metadata?.results?.get(0)?.title)
        assertEquals("kotlinlang.org", result.metadata?.results?.get(0)?.domain)
        assertEquals("Release Notes", result.metadata?.results?.get(1)?.title)
    }

    @Test
    fun `extractNativeWebSearchSources returns original content without source urls`() {
        val content = "No source section here."

        val result = extractNativeWebSearchSources(content, "query")

        assertEquals(content, result.content)
        assertTrue(result.metadata == null)
    }

    @Test
    fun `parses bold sources header`() {
        val content = """
            Here is the answer based on search results.

            **Sources:**
            1. Kotlin Blog — https://kotlinlang.org/news/example
            2. GitHub Releases — https://github.com/JetBrains/kotlin/releases
        """.trimIndent()

        val result = extractNativeWebSearchSources(content, "query")

        assertEquals("Here is the answer based on search results.", result.content)
        assertEquals(2, result.metadata?.results?.size)
    }

    @Test
    fun `parses heading sources header`() {
        val content = """
            Some answer content.

            ## References
            1. [MDN Web Docs](https://developer.mozilla.org/en-US/)
            2. [Kotlin Official](https://kotlinlang.org)
        """.trimIndent()

        val result = extractNativeWebSearchSources(content, "query")

        assertEquals("Some answer content.", result.content)
        assertEquals(2, result.metadata?.results?.size)
        assertEquals("MDN Web Docs", result.metadata?.results?.get(0)?.title)
    }

    @Test
    fun `parses trailing bare markdown links without header`() {
        val content = """
            Here is what I found. [1]

            - [Kotlin Blog](https://kotlinlang.org/news/example)
            - [GitHub Releases](https://github.com/JetBrains/kotlin/releases)
        """.trimIndent()

        val result = extractNativeWebSearchSources(content, "query")

        // Content is NOT stripped (no header matched) but metadata should be present
        assertEquals(content, result.content)
        assertNotNull(result.metadata)
        assertEquals(2, result.metadata?.results?.size)
        assertEquals("Kotlin Blog", result.metadata?.results?.get(0)?.title)
    }

    @Test
    fun `only the final sources section is stripped when earlier headings exist`() {
        val answer = """
            References:
            - [Background](https://example.com/background)

            This explanation must not disappear.

            ## Sources
            - [Final source](https://example.com/final)
        """.trimIndent()

        val result = extractNativeWebSearchSources(answer, "query")

        assertEquals(answer.substringBefore("\n\n## Sources"), result.content)
        assertEquals(listOf("https://example.com/final"), result.metadata?.results?.map { it.url })
    }

    @Test
    fun `a mid-answer references section never removes later prose`() {
        val answer = """
            Here is the answer.

            References:
            - [Background](https://example.com/background)

            Important limitation: the evidence does not establish causation.
        """.trimIndent()

        val result = extractNativeWebSearchSources(answer, "query")

        assertEquals(answer, result.content)
        assertTrue(result.metadata == null)
    }

    @Test
    fun `a later heading is answer content rather than part of a source list`() {
        val answer = """
            An introduction.

            Sources:
            - [Evidence](https://example.com/evidence)

            ## Limitations
            Results may differ in production.
        """.trimIndent()

        assertEquals(answer, extractNativeWebSearchSources(answer, "query").content)
    }

    @Test
    fun `links embedded in prose do not make a whole section disposable`() {
        val answer = """
            Sources:
            The [study](https://example.com/study) supports only the first claim.
            The second claim is unproven.
        """.trimIndent()

        val result = extractNativeWebSearchSources(answer, "query")

        assertEquals(answer, result.content)
        assertTrue(result.metadata == null)
    }

    @Test
    fun `source-looking headings inside fenced examples are never stripped`() {
        for (fence in listOf("```", "~~~")) {
            val answer = """
                Example source document:
                ${fence}markdown
                Sources:
                - [Example](https://example.com)
                $fence
            """.trimIndent()

            val result = extractNativeWebSearchSources(answer, "query")

            assertEquals(answer, result.content)
            assertTrue(result.metadata == null)
        }
    }

    @Test
    fun `plain named source rows and CRLF headings still extract`() {
        val answer = "Answer.\r\n\r\n__References__:\r\nKotlin Blog — https://kotlinlang.org/news\r\n"

        val result = extractNativeWebSearchSources(answer, "query")

        assertEquals("Answer.", result.content)
        assertEquals("Kotlin Blog", result.metadata?.results?.single()?.title)
    }

    @Test
    fun `a source-only answer keeps its visible text`() {
        val answer = "Sources:\n- [Evidence](https://example.com/evidence)"

        val result = extractNativeWebSearchSources(answer, "query")

        assertEquals(answer, result.content)
        assertEquals(1, result.metadata?.results?.size)
    }

    @Test
    fun `single bare link is not treated as source list`() {
        val content = """
            Check this out.
            - [One link](https://example.com)
        """.trimIndent()

        val result = extractNativeWebSearchSources(content, "query")

        assertTrue(result.metadata == null)
    }
}
