package com.materialchat.ui.screens.chat.components

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.materialchat.assistant.ui.AssistantSpacing
import com.materialchat.ui.theme.ExpressiveSizes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Geometry pins for the three thinking glyphs in a chat bubble: the working indicator shown
 * before the first token, the streaming tail glyph, and the thinking header chevron. Each is
 * symmetric in its own slot, so the bubble style's corner radii (the `EXPRESSIVE` family's 8dp
 * origin corner against 28dp elsewhere) cannot leave it off-centre.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ChatThinkingGeometryTest {

    @Test
    fun `working indicator inset is the same on every side`() {
        val insets = ChatWorkingIndicatorGeometry.insets()
        val start = insets.calculateStartPadding(LayoutDirection.Ltr)
        val end = insets.calculateEndPadding(LayoutDirection.Ltr)
        assertEquals("start vs end", start, end)
        assertEquals("top vs bottom", insets.calculateTopPadding(), insets.calculateBottomPadding())
        assertEquals("horizontal vs vertical", start, insets.calculateTopPadding())
        assertEquals(ChatWorkingIndicatorGeometry.Inset, start)
    }

    @Test
    fun `working indicator inset matches the assistant thinking surface`() {
        assertEquals(AssistantSpacing.Tight, ChatWorkingIndicatorGeometry.Inset)
    }

    @Test
    fun `working indicator container is the M3 contained indicator container`() {
        assertEquals(ExpressiveSizes.IndicatorHero, ChatWorkingIndicatorGeometry.Container)
        assertEquals(LoadingIndicatorDefaults.ContainerWidth, ChatWorkingIndicatorGeometry.Container)
        assertEquals(LoadingIndicatorDefaults.ContainerHeight, ChatWorkingIndicatorGeometry.Container)
        assertEquals(38.dp, LoadingIndicatorDefaults.IndicatorSize)
    }

    @Test
    fun `streaming tail glyph is unrotated with the same inset on every side`() {
        assertEquals(0, StreamingTailGeometry.StartAngle)
        assertEquals(2.dp, StreamingTailGeometry.Inset)
        assertEquals(12.dp, StreamingTailGeometry.Size)
    }

    @Test
    fun `thinking header insets are even and the chevron fits the strip`() {
        assertEquals(8.dp, ThinkingHeaderGeometry.HorizontalInset)
        assertEquals(6.dp, ThinkingHeaderGeometry.VerticalInset)
        assertEquals(18.dp, ThinkingHeaderGeometry.Chevron)
        assertEquals(6.dp, ThinkingHeaderGeometry.ChevronGap)
        // The chevron sits centred in the 40dp strip with room on both sides of it.
        assertEquals(40.dp, ThinkingHeaderGeometry.MinHeight)
        assertEquals(
            true,
            ThinkingHeaderGeometry.Chevron + ThinkingHeaderGeometry.VerticalInset * 2 <= ThinkingHeaderGeometry.MinHeight
        )
    }
}
