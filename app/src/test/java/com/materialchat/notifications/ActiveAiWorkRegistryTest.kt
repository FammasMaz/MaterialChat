package com.materialchat.notifications

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The keep-alive notification is process-wide, but chat streams and image
 * generations overlap. One owner finishing must not tear the notification down
 * while another owner is still working, and the service must stop once the last
 * owner is done.
 */
class ActiveAiWorkRegistryTest {

    @Before
    fun setUp() = ActiveAiWorkRegistry.clear()

    @After
    fun tearDown() = ActiveAiWorkRegistry.clear()

    @Test
    fun `finishing one owner keeps the service alive for the other`() {
        ActiveAiWorkRegistry.begin("conv-a")
        ActiveAiWorkRegistry.begin("conv-b")

        assertTrue("another owner is still working", ActiveAiWorkRegistry.finish("conv-a"))
        assertEquals(1, ActiveAiWorkRegistry.activeCount())
    }

    @Test
    fun `finishing the last owner stops the service`() {
        ActiveAiWorkRegistry.begin("conv-a")

        assertFalse("no work remains", ActiveAiWorkRegistry.finish("conv-a"))
        assertEquals(0, ActiveAiWorkRegistry.activeCount())
    }

    @Test
    fun `finishing an unknown owner does not resurrect or leak state`() {
        ActiveAiWorkRegistry.begin("conv-a")

        assertTrue(ActiveAiWorkRegistry.finish("never-started"))
        assertEquals(1, ActiveAiWorkRegistry.activeCount())
    }

    @Test
    fun `repeated begins are idempotent and need a matching finish`() {
        ActiveAiWorkRegistry.begin("conv-a")
        ActiveAiWorkRegistry.begin("conv-a")
        assertEquals(1, ActiveAiWorkRegistry.activeCount())

        assertFalse(ActiveAiWorkRegistry.finish("conv-a"))
        assertEquals(0, ActiveAiWorkRegistry.activeCount())
    }

    @Test
    fun `a chat and its inline image generation are independent owners`() {
        ActiveAiWorkRegistry.begin("conv-a")
        ActiveAiWorkRegistry.begin("conv-a:image")

        assertTrue(ActiveAiWorkRegistry.finish("conv-a:image"))
        assertFalse(ActiveAiWorkRegistry.finish("conv-a"))
    }
}
