package com.materialchat.ui.screens.chat.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure layout rules of the connected action group: which corners are outer vs inner per
 * position, how a pressed segment morphs, and how the icon segments compress (never wrap)
 * while the stats pill is expanded.
 */
class MessageActionsLayoutTest {

    @Test
    fun positionsFollowIndexWithinGroup() {
        assertEquals(ConnectedSegmentPosition.Only, connectedSegmentPosition(0, 1))
        assertEquals(ConnectedSegmentPosition.First, connectedSegmentPosition(0, 3))
        assertEquals(ConnectedSegmentPosition.Middle, connectedSegmentPosition(1, 3))
        assertEquals(ConnectedSegmentPosition.Last, connectedSegmentPosition(2, 3))
        assertEquals(ConnectedSegmentPosition.First, connectedSegmentPosition(0, 2))
        assertEquals(ConnectedSegmentPosition.Last, connectedSegmentPosition(1, 2))
    }

    @Test
    fun outerEdgesAreExpressiveAndJoinsAreTight() {
        assertEquals(ConnectedSegmentCorners(20.dp, 6.dp), connectedSegmentCorners(ConnectedSegmentPosition.First, pressed = false))
        assertEquals(ConnectedSegmentCorners(6.dp, 6.dp), connectedSegmentCorners(ConnectedSegmentPosition.Middle, pressed = false))
        assertEquals(ConnectedSegmentCorners(6.dp, 20.dp), connectedSegmentCorners(ConnectedSegmentPosition.Last, pressed = false))
        assertEquals(ConnectedSegmentCorners(20.dp, 20.dp), connectedSegmentCorners(ConnectedSegmentPosition.Only, pressed = false))
    }

    @Test
    fun outerCornersStayWithinExpressiveRangeAndInnerStayTight() {
        assertTrue(MessageActionOuterCorner >= 20.dp && MessageActionOuterCorner <= 24.dp)
        assertTrue(MessageActionInnerCorner >= 6.dp && MessageActionInnerCorner <= 8.dp)
    }

    @Test
    fun pressedSegmentMorphsBothEdgesToThePressedRadius() {
        ConnectedSegmentPosition.values().forEach { position ->
            assertEquals(
                ConnectedSegmentCorners(MessageActionPressedCorner, MessageActionPressedCorner),
                connectedSegmentCorners(position, pressed = true)
            )
        }
    }

    @Test
    fun iconSegmentsCompressWhileStatsExpanded() {
        assertEquals(MessageActionRowItemSize, messageActionSegmentWidth(statsExpanded = false))
        assertEquals(MessageActionCompactWidth, messageActionSegmentWidth(statsExpanded = true))
        assertTrue(MessageActionCompactWidth < MessageActionRowItemSize)
    }

    @Test
    fun fullGroupFitsOneLineAt360dpWithStatsExpanded() {
        // 360dp screen minus the list's 16dp side insets. Five icon segments (copy, branch,
        // redo, regenerate, bookmark) plus the stats pill, joined by 2dp gaps.
        val rowWidth = 360.dp - 32.dp
        val iconSegments = 5
        val icons = messageActionSegmentWidth(statsExpanded = true) * iconSegments
        val gaps = MessageActionGroupSpacing * iconSegments
        val leftForStats = rowWidth - icons - gaps
        // The pill needs at least its collapsed width to show the bolt; everything beyond that
        // is text that ellipsises, so the row never needs a second line.
        assertTrue("stats pill must keep ${MessageActionRowItemSize}, had $leftForStats",
            leftForStats >= MessageActionRowItemSize)
        // And the elapsed label yields while the pill is open, so it never competes for width.
        assertFalse(showElapsedLabel("12.3s", statsExpanded = true))
        assertTrue(showElapsedLabel("12.3s", statsExpanded = false))
        assertFalse(showElapsedLabel(null, statsExpanded = false))
    }

    @Test
    fun touchTargetMeetsMinimumWhileVisualStaysCompact() {
        assertEquals(48.dp, MessageActionRowTouchHeight)
        assertEquals(40.dp, MessageActionRowItemSize)
    }
}
