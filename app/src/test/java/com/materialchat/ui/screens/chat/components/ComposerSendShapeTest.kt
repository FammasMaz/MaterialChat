package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The send button's contour follows a fixed track: cookie (idle) → circle (ready) → rounded
 * square (streaming). A press moves the contour toward the neighbouring shape, never off the
 * track, so the clip path always resolves to a real morph frame.
 */
class ComposerSendShapeTest {
    @Test
    fun restingStatesSitOnTheTrackEndpoints() {
        assertEquals(0f, composerSendShapeProgress(ComposerSendState.Idle, pressed = false))
        assertEquals(1f, composerSendShapeProgress(ComposerSendState.Ready, pressed = false))
        assertEquals(COMPOSER_SEND_TRACK_END, composerSendShapeProgress(ComposerSendState.Streaming, pressed = false))
    }

    @Test
    fun pressMorphsTowardTheNeighbouringShapeAndStaysOnTrack() {
        val readyPressed = composerSendShapeProgress(ComposerSendState.Ready, pressed = true)
        assertTrue("ready press squares the circle off", readyPressed > 1f && readyPressed < COMPOSER_SEND_TRACK_END)

        val streamingPressed = composerSendShapeProgress(ComposerSendState.Streaming, pressed = true)
        assertTrue("streaming press softens the square", streamingPressed < COMPOSER_SEND_TRACK_END && streamingPressed > 1f)

        // Idle is disabled: a press must not move it.
        assertEquals(0f, composerSendShapeProgress(ComposerSendState.Idle, pressed = true))
    }

    @Test
    fun stateMachineIsUnchanged() {
        assertEquals(ComposerSendState.Streaming, composerSendState(isStreaming = true, canSend = true))
        assertEquals(ComposerSendState.Ready, composerSendState(isStreaming = false, canSend = true))
        assertEquals(ComposerSendState.Idle, composerSendState(isStreaming = false, canSend = false))
    }
}
