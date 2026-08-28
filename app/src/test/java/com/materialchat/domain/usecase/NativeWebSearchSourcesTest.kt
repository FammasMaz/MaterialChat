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
    fun `single bare link is not treated as source list`() {
        val content = """
            Check this out.
            - [One link](https://example.com)
        """.trimIndent()

        val result = extractNativeWebSearchSources(content, "query")

        assertTrue(result.metadata == null)
    }
}
