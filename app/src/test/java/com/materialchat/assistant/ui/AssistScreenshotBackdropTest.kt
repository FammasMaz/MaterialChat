package com.materialchat.assistant.ui

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

/** Geometry of the shrunk-screen backdrop: centred scaling and corner rounding. */
class AssistScreenshotBackdropTest {
    @Test fun shrunkFrameIsCentredAtTheRestScale() {
        val frame = screenshotBackdropFrame(Size(1000f, 2000f), 0.92f)
        assertEquals(920f, frame.width, 1e-3f)
        assertEquals(1840f, frame.height, 1e-3f)
        assertEquals(40f, frame.left, 1e-3f)
        assertEquals(80f, frame.top, 1e-3f)
        assertEquals(500f, frame.center.x, 1e-3f)
        assertEquals(1000f, frame.center.y, 1e-3f)
    }

    @Test fun fullSizeFrameCoversTheWindowAndInvalidScalesFallBackToIt() {
        val size = Size(1000f, 2000f)
        assertEquals(size, screenshotBackdropFrame(size, 1f).size)
        assertEquals(size, screenshotBackdropFrame(size, Float.NaN).size)
        assertEquals(size, screenshotBackdropFrame(size, 7f).size)
    }

    @Test fun cornersRoundAsTheScreenShrinksAndSquareOffAtFullSize() {
        assertEquals(0f, screenshotBackdropCornerPx(84f, 1f, 0.92f), 0f)
        assertEquals(42f, screenshotBackdropCornerPx(84f, 0.96f, 0.92f), 1e-3f)
        assertEquals(84f, screenshotBackdropCornerPx(84f, 0.92f, 0.92f), 1e-3f)
        assertEquals(84f, screenshotBackdropCornerPx(84f, 0.5f, 0.92f), 1e-3f)
        assertEquals(84f, screenshotBackdropCornerPx(84f, 1f, 1f), 0f)
    }
}
