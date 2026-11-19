package com.materialchat.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Suppress("DEPRECATION")
    @Test fun customBottomSheetIsTheExpressiveBottomSheet() {
        assertSame(ExpressiveShapes.BottomSheet, CustomShapes.BottomSheet)
        // Top-only 32dp squircle, same radius as the floating Sheet and the same G2 family.
        assertEquals(TopSquircleShape(32.dp), ExpressiveShapes.BottomSheet)
        assertEquals(SquircleShape(32.dp), ExpressiveShapes.Sheet)
    }

    @Suppress("DEPRECATION")
    @Test fun providerCardIsTheExpressiveCard() {
        assertSame(ExpressiveShapes.Card, CustomShapes.ProviderCard)
        assertEquals(SquircleShape(28.dp), CustomShapes.ProviderCard)
    }

    @Test fun topSquircleIsNotTheAllCornerSquircleAtTheSameRadius() {
        assertNotEquals(SquircleShape(32.dp), TopSquircleShape(32.dp))
        assertNotEquals(TopSquircleShape(32.dp), SquircleShape(32.dp))
        assertNotEquals(SquircleShape(32.dp).hashCode(), TopSquircleShape(32.dp).hashCode())
        assertEquals(TopSquircleShape(32.dp), TopSquircleShape(32.dp))
        assertEquals(TopSquircleShape(32.dp).hashCode(), TopSquircleShape(32.dp).hashCode())
        assertNotEquals(TopSquircleShape(32.dp), TopSquircleShape(28.dp))
        assertNotEquals(TopSquircleShape(32.dp, 0.65f), TopSquircleShape(32.dp, 0.3f))
    }

    @Test fun degenerateSizesOutlineAsPlainRectangles() {
        // The only branch that does not touch android.graphics: a zero-area sheet is a rectangle.
        val density = Density(1f)
        for (shape in listOf(TopSquircleShape(32.dp), SquircleShape(32.dp))) {
            val outline = shape.createOutline(Size(0f, 100f), LayoutDirection.Ltr, density)
            assertTrue(outline is Outline.Rectangle)
            assertEquals(Size(0f, 100f), (outline as Outline.Rectangle).rect.size)
        }
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
