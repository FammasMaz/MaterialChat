package com.materialchat.ui.screens.conversations

import androidx.compose.ui.unit.dp
import com.materialchat.ui.screens.conversations.components.SwipeActionSlot
import com.materialchat.ui.screens.conversations.components.swipeActionCorners
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Per-position geometry of the swipe-reveal action pills: the edge shared with a
 * sibling action is the tight one, the screen-facing edge keeps the full radius.
 */
class SwipeActionShapesTest {

    private val outer = 24.dp
    private val inner = 10.dp

    @Test
    fun onlyActionUsesOuterRadiusOnEveryCorner() {
        val corners = swipeActionCorners(SwipeActionSlot.Only, outer, inner)
        assertEquals(outer, corners.topStart)
        assertEquals(outer, corners.topEnd)
        assertEquals(outer, corners.bottomStart)
        assertEquals(outer, corners.bottomEnd)
    }

    @Test
    fun leadingActionIsTightOnItsEndEdge() {
        val corners = swipeActionCorners(SwipeActionSlot.Leading, outer, inner)
        assertEquals(outer, corners.topStart)
        assertEquals(outer, corners.bottomStart)
        assertEquals(inner, corners.topEnd)
        assertEquals(inner, corners.bottomEnd)
    }

    @Test
    fun trailingActionIsTightOnItsStartEdge() {
        val corners = swipeActionCorners(SwipeActionSlot.Trailing, outer, inner)
        assertEquals(inner, corners.topStart)
        assertEquals(inner, corners.bottomStart)
        assertEquals(outer, corners.topEnd)
        assertEquals(outer, corners.bottomEnd)
    }

    @Test
    fun armedActionCollapsesToOneRadius() {
        // When armed both radii are passed as the same value; every corner must match.
        val armed = 28.dp
        SwipeActionSlot.entries.forEach { slot ->
            val corners = swipeActionCorners(slot, armed, armed)
            assertEquals(armed, corners.topStart)
            assertEquals(armed, corners.topEnd)
            assertEquals(armed, corners.bottomStart)
            assertEquals(armed, corners.bottomEnd)
        }
    }
}
