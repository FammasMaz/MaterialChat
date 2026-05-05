package com.materialchat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableParserTest {

    @Test
    fun `parseTableRow keeps escaped pipes inside a cell`() {
        val row = parseTableRow("| Name | Value \\| with pipe | Notes |")

        assertEquals(listOf("Name", "Value | with pipe", "Notes"), row)
    }

    @Test
    fun `parseTableRow keeps pipes inside inline code spans`() {
        val row = parseTableRow("| Expression | `a | b | c` | Result |")

        assertEquals(listOf("Expression", "`a | b | c`", "Result"), row)
    }

    @Test
    fun `isTableRow ignores prose with only escaped pipes`() {
        assertFalse(isTableRow("Use A \\| B when explaining alternatives."))
    }

    @Test
    fun `isTableSeparator accepts common alignment markers`() {
        assertTrue(isTableSeparator("| :--- | :---: | ---: |"))
    }
}
