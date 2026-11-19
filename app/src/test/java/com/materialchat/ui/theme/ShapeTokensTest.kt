package com.materialchat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.RoundedPolygon
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

    @Test fun badgeIsTheM3SmallCornerAndMatchesExtraSmall() {
        // The 8dp badge token the W4 inventory needs (nine raw sites): plain round corner, no
        // squircle, identical to the component-default extraSmall so the two never diverge.
        assertEquals(RoundedCornerShape(8.dp), ExpressiveShapes.Badge)
        assertEquals(MaterialChatShapes.extraSmall, ExpressiveShapes.Badge)
        assertNotEquals(ExpressiveShapes.ContentBlock, ExpressiveShapes.Badge)
    }

    @Test fun squircleEqualityIsByRadiusAndSmoothing() {
        assertEquals(SquircleShape(16.dp), SquircleShape(16.dp))
        assertEquals(SquircleShape(16.dp).hashCode(), SquircleShape(16.dp).hashCode())
        assert(SquircleShape(16.dp) != SquircleShape(20.dp))
        assert(SquircleShape(16.dp, 0.65f) != SquircleShape(16.dp, 0.3f))
        assertEquals(SquircleSmoothing, 0.65f, 0f)
    }

    // --- W5: per-corner squircle ---------------------------------------------------------

    private val density = Density(2f)
    private val box = Size(300f, 180f)

    private fun RoundedPolygon.points(): List<Float> = cubics.flatMap { c: Cubic ->
        listOf(c.anchor0X, c.anchor0Y, c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
    }

    private fun assertSameGeometry(expected: RoundedPolygon, actual: RoundedPolygon) {
        val e = expected.points()
        val a = actual.points()
        assertEquals("cubic count", e.size, a.size)
        for (i in e.indices) assertEquals("point $i", e[i], a[i], 1e-3f)
    }

    private fun assertDifferentGeometry(a: RoundedPolygon, b: RoundedPolygon) {
        val pa = a.points()
        val pb = b.points()
        assertTrue(pa.size != pb.size || pa.indices.any { kotlin.math.abs(pa[it] - pb[it]) > 1e-3f })
    }

    @Test fun perCornerSquircleWithFourEqualRadiiIsTheSymmetricSquircle() {
        val symmetric = SquircleShape(20.dp)
        val perCorner = SquircleShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
        assertEquals(symmetric, perCorner)
        assertEquals(perCorner, symmetric)
        assertEquals(symmetric.hashCode(), perCorner.hashCode())
        for (dir in LayoutDirection.values()) {
            assertSameGeometry(symmetric.roundedPolygon(box, dir, density), perCorner.roundedPolygon(box, dir, density))
        }
    }

    @Test fun asymmetricSquircleIsNotTheSymmetricOneAndDrawsDifferentCorners() {
        val asymmetric = SquircleShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 8.dp)
        assertNotEquals(SquircleShape(20.dp), asymmetric)
        assertNotEquals(asymmetric, SquircleShape(20.dp))
        assertNotEquals(SquircleShape(20.dp).hashCode(), asymmetric.hashCode())
        assertDifferentGeometry(
            SquircleShape(20.dp).roundedPolygon(box, LayoutDirection.Ltr, density),
            asymmetric.roundedPolygon(box, LayoutDirection.Ltr, density)
        )
        // Equality is positional: swapping two corners is a different shape.
        assertNotEquals(asymmetric, SquircleShape(topStart = 8.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
        // Equality and hashCode agree with themselves and include the smoothing.
        assertEquals(asymmetric, SquircleShape(20.dp, 20.dp, 20.dp, 8.dp))
        assertEquals(asymmetric.hashCode(), SquircleShape(20.dp, 20.dp, 20.dp, 8.dp).hashCode())
        assertNotEquals(asymmetric, SquircleShape(20.dp, 20.dp, 20.dp, 8.dp, smoothing = 0.3f))
    }

    @Test fun startCornersFollowTheLayoutDirectionLikeRoundedCornerShape() {
        val shape = SquircleShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 8.dp)
        // Under RTL the tight start corner lands bottom-right: the same geometry as the LTR
        // shape whose tight corner is bottomEnd.
        val mirrored = SquircleShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 8.dp, bottomStart = 20.dp)
        assertSameGeometry(
            mirrored.roundedPolygon(box, LayoutDirection.Ltr, density),
            shape.roundedPolygon(box, LayoutDirection.Rtl, density)
        )
        assertDifferentGeometry(
            shape.roundedPolygon(box, LayoutDirection.Ltr, density),
            shape.roundedPolygon(box, LayoutDirection.Rtl, density)
        )
    }

    @Test fun perCornerSquircleOutlinesDegenerateSizesAsPlainRectangles() {
        val shape = SquircleShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 28.dp, bottomStart = 28.dp)
        for (size in listOf(Size(0f, 100f), Size(100f, 0f), Size(0f, 0f))) {
            val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
            assertTrue(outline is Outline.Rectangle)
            assertEquals(size, (outline as Outline.Rectangle).rect.size)
        }
        // A 0dp corner is left sharp, and oversize radii are clamped to half the shorter side
        // without throwing (the polygon is still built).
        SquircleShape(0.dp, 20.dp, 0.dp, 20.dp).roundedPolygon(box, LayoutDirection.Ltr, density)
        SquircleShape(999.dp, 999.dp, 999.dp, 999.dp).roundedPolygon(Size(10f, 10f), LayoutDirection.Ltr, density)
        SquircleShape(999.dp).roundedPolygon(Size(10f, 10f), LayoutDirection.Ltr, density)
    }

    @Test fun assistantSurfaceTokensCarryTheAssistantRadiiExactly() {
        // W6: the three theme tokens are the record of the assistant's radii. No assistant
        // composable draws AssistantShapes.Answer/Notice/Thinking any more (W5 assistant
        // 8208d55), so this pins the token geometry against the recorded values directly, in
        // the assistant's topStart / topEnd / bottomStart / bottomEnd order, and the assistant
        // owner can delete those vals without touching this test.
        assertEquals(SquircleShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 28.dp, bottomEnd = 28.dp), ExpressiveShapes.AnswerSurface)
        assertEquals(SquircleShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp), ExpressiveShapes.NoticeSurface)
        assertEquals(SquircleShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 8.dp, bottomEnd = 20.dp), ExpressiveShapes.ThinkingSurface)
        // Positional: the thinking surface's tight corner is bottom-START, nothing else.
        assertNotEquals(SquircleShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 8.dp), ExpressiveShapes.ThinkingSurface)
        assertNotEquals(SquircleShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 36.dp, bottomEnd = 36.dp), ExpressiveShapes.AnswerSurface)
        // All three draw the app's G2 smoothing, not a plain round corner.
        assertNotEquals(SquircleShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 28.dp, bottomEnd = 28.dp, smoothing = 0f), ExpressiveShapes.AnswerSurface)
        assertNotEquals(ExpressiveShapes.AnswerSurface, ExpressiveShapes.NoticeSurface)
        assertNotEquals(ExpressiveShapes.NoticeSurface, ExpressiveShapes.ThinkingSurface)
        assertNotEquals(ExpressiveShapes.AnswerSurface, ExpressiveShapes.ThinkingSurface)
    }

    @Test fun noticeIsTheTwentyDpSquircleTheSnackbarDraws() {
        assertEquals(SquircleShape(20.dp), ExpressiveShapes.Notice)
        assertEquals(ExpressiveShapes.DisclosurePanel, ExpressiveShapes.Notice)
        assertNotEquals(ExpressiveShapes.Card, ExpressiveShapes.Notice)
    }

    @Test fun tileBadgeKeepsTheTileProportionAtThirtyTwoDp() {
        assertEquals(SquircleShape(12.dp), ExpressiveShapes.TileBadge)
        // 18 / 48 == 12 / 32.
        assertEquals(18f / 48f, 12f / 32f, 1e-6f)
        assertNotEquals(ExpressiveShapes.Badge, ExpressiveShapes.TileBadge)
        assertNotEquals(ExpressiveShapes.ContentBlock, ExpressiveShapes.TileBadge)
    }

    @Test fun chipIsTheCapsule() {
        assertSame(CustomShapes.Pill, ExpressiveShapes.Chip)
        assertEquals(RoundedCornerShape(50), ExpressiveShapes.Chip)
    }

    @Suppress("DEPRECATION")
    @Test fun fabAliasIsTheMediumRoundCornerUntilItsLastSiteMoves() {
        assertSame(MaterialChatShapes.medium, CustomShapes.Fab)
        assertEquals(RoundedCornerShape(16.dp), CustomShapes.Fab)
    }
}
