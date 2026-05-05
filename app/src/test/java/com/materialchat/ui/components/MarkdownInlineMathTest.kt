package com.materialchat.ui.components

import androidx.compose.ui.text.style.BaselineShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkdownInlineMathTest {

    @Test
    fun `renders braced letter subscripts with subscript styling`() {
        val rendered = renderInlineMathForTesting("f_{ty}")

        assertEquals("fty", rendered.text)
        val subscript = rendered.spanStyles.firstOrNull {
            it.item.baselineShift == BaselineShift.Subscript
        }

        assertNotNull(subscript)
        assertEquals(1, subscript?.start)
        assertEquals(3, subscript?.end)
    }

    @Test
    fun `renders superscripts and latex symbols without dropping script styling`() {
        val rendered = renderInlineMathForTesting("x^2 + \\alpha_i")

        assertEquals("x2 + αi", rendered.text)
        val superscript = rendered.spanStyles.firstOrNull {
            it.item.baselineShift == BaselineShift.Superscript
        }
        val subscript = rendered.spanStyles.firstOrNull {
            it.item.baselineShift == BaselineShift.Subscript
        }

        assertNotNull(superscript)
        assertEquals(1, superscript?.start)
        assertEquals(2, superscript?.end)
        assertNotNull(subscript)
        assertEquals(6, subscript?.start)
        assertEquals(7, subscript?.end)
    }
}
