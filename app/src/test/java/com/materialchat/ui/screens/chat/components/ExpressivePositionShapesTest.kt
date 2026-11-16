package com.materialchat.ui.screens.chat.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Per-position corners of a connected group: outer edges large, joins small. */
class ExpressivePositionShapesTest {

    @Test
    fun singleItemKeepsOuterRadiusOnBothEdges() {
        assertEquals(PositionCorners(20.dp, 20.dp), positionCorners(0, 1))
    }

    @Test
    fun endsAreOuterAndJoinsAreInner() {
        assertEquals(PositionCorners(20.dp, 8.dp), positionCorners(0, 3))
        assertEquals(PositionCorners(8.dp, 8.dp), positionCorners(1, 3))
        assertEquals(PositionCorners(8.dp, 20.dp), positionCorners(2, 3))
    }

    @Test
    fun customRadiiAreHonoured() {
        assertEquals(PositionCorners(16.dp, 6.dp), positionCorners(0, 2, outer = 16.dp, inner = 6.dp))
    }
}
