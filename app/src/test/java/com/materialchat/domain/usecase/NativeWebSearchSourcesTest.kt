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
}
