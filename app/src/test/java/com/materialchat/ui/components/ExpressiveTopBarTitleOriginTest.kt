package com.materialchat.ui.components

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * W6 theme part 4: `ExpressiveTopBarTitle` scales toward its start edge, which mirrors under
 * RTL (the masthead's `titleOriginX` has the same rule, `ConversationsScreen.kt:499`).
 */
class ExpressiveTopBarTitleOriginTest {

    @Test fun ltrPivotsOnTheLeftEdgeAtMidHeight() {
        assertEquals(TransformOrigin(0f, 0.5f), expressiveTitleTransformOrigin(LayoutDirection.Ltr))
    }

    @Test fun rtlPivotsOnTheRightEdgeAtMidHeight() {
        assertEquals(TransformOrigin(1f, 0.5f), expressiveTitleTransformOrigin(LayoutDirection.Rtl))
    }

    @Test fun theTwoDirectionsMirrorEachOtherHorizontallyOnly() {
        val ltr = expressiveTitleTransformOrigin(LayoutDirection.Ltr)
        val rtl = expressiveTitleTransformOrigin(LayoutDirection.Rtl)
        assertNotEquals(ltr, rtl)
        assertEquals(1f, ltr.pivotFractionX + rtl.pivotFractionX, 0f)
        assertEquals(ltr.pivotFractionY, rtl.pivotFractionY, 0f)
    }
}
