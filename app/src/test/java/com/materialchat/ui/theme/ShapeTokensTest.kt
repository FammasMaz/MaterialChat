package com.materialchat.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The shape vocabulary has one token per surface class. These pin the aliases so a later
 * edit cannot silently reopen the round-vs-squircle dialog split or detach the content
 * block from the disclosure panel family.
 */
class ShapeTokensTest {

    @Suppress("DEPRECATION")
    @Test fun customDialogIsTheExpressiveDialog() {
        assertSame(ExpressiveShapes.Dialog, CustomShapes.Dialog)
        assertEquals(SquircleShape(28.dp), ExpressiveShapes.Dialog)
    }

    @Suppress("DEPRECATION")
    @Test fun codeBlockIsTheContentBlock() {
        assertSame(ExpressiveShapes.ContentBlock, CustomShapes.CodeBlock)
    }

    @Test fun contentBlockIsOneStepInsideTheDisclosurePanelInTheSameFamily() {
        assertEquals(SquircleShape(16.dp), ExpressiveShapes.ContentBlock)
        assertEquals(SquircleShape(20.dp), ExpressiveShapes.DisclosurePanel)
        assertEquals(SquircleShape(28.dp), ExpressiveShapes.Card)
    }

    @Test fun squircleEqualityIsByRadiusAndSmoothing() {
        assertEquals(SquircleShape(16.dp), SquircleShape(16.dp))
        assertEquals(SquircleShape(16.dp).hashCode(), SquircleShape(16.dp).hashCode())
        assert(SquircleShape(16.dp) != SquircleShape(20.dp))
        assert(SquircleShape(16.dp, 0.65f) != SquircleShape(16.dp, 0.3f))
    }
}
