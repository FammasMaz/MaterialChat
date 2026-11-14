package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streaming haptic ticks with the words, so the boundary test decides whether a reveal step
 * fires a vibrator call at all. It must fire exactly when a word finished, never mid-word.
 */
class StreamingHapticRhythmTest {

    @Test
    fun `crossing a space ticks`() {
        val text = "hello world"
        assertTrue(text.crossedWordBoundary(0, 6))
    }

    @Test
    fun `revealing inside a word does not tick`() {
        val text = "extraordinary"
        assertFalse(text.crossedWordBoundary(0, 5))
    }

    @Test
    fun `punctuation ends a word`() {
        assertTrue("done.".crossedWordBoundary(3, 5))
        assertTrue("really?".crossedWordBoundary(5, 7))
    }

    @Test
    fun `an empty step never ticks`() {
        assertFalse("hello".crossedWordBoundary(3, 3))
        assertFalse("hello".crossedWordBoundary(4, 2))
    }

    @Test
    fun `a step past the end is clamped instead of throwing`() {
        assertFalse("hi".crossedWordBoundary(0, 99))
        assertTrue("hi there".crossedWordBoundary(0, 99))
    }
}
