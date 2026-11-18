package com.materialchat.assistant.screen

import android.net.Uri
import com.materialchat.data.local.preferences.AssistantScreenContextSettings
import com.materialchat.data.local.preferences.ScreenContextMode
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Never / Automatic / Always decision table, every cell. */
class ScreenContextPolicyTest {
    private fun decide(mode: ScreenContextMode, asks: Boolean, available: Boolean, declined: Boolean = false) =
        ScreenContextPolicy.decide(mode, asks, available, declined)

    @Test fun neverSkipsEverything() {
        for (asks in listOf(true, false)) for (available in listOf(true, false)) for (declined in listOf(true, false)) {
            assertEquals(ScreenContextDecision.SKIP, decide(ScreenContextMode.NEVER, asks, available, declined))
        }
    }

    @Test fun automaticAttachesOnlyWhenAskedAndAvailable() {
        assertEquals(ScreenContextDecision.ATTACH, decide(ScreenContextMode.AUTOMATIC, asks = true, available = true))
        assertEquals(ScreenContextDecision.UNAVAILABLE, decide(ScreenContextMode.AUTOMATIC, asks = true, available = false))
        assertEquals(ScreenContextDecision.SKIP, decide(ScreenContextMode.AUTOMATIC, asks = false, available = true))
        assertEquals(ScreenContextDecision.SKIP, decide(ScreenContextMode.AUTOMATIC, asks = false, available = false))
    }

    @Test fun alwaysAttachesWheneverAvailableAndStaysQuietOtherwise() {
        assertEquals(ScreenContextDecision.ATTACH, decide(ScreenContextMode.ALWAYS, asks = false, available = true))
        assertEquals(ScreenContextDecision.ATTACH, decide(ScreenContextMode.ALWAYS, asks = true, available = true))
        // A question that is not about the screen never nags about the system setting.
        assertEquals(ScreenContextDecision.SKIP, decide(ScreenContextMode.ALWAYS, asks = false, available = false))
        assertEquals(ScreenContextDecision.UNAVAILABLE, decide(ScreenContextMode.ALWAYS, asks = true, available = false))
    }

    @Test fun removingTheChipWinsOverEveryMode() {
        assertEquals(ScreenContextDecision.SKIP, decide(ScreenContextMode.AUTOMATIC, true, true, declined = true))
        assertEquals(ScreenContextDecision.SKIP, decide(ScreenContextMode.ALWAYS, true, true, declined = true))
        assertEquals(ScreenContextDecision.SKIP, decide(ScreenContextMode.AUTOMATIC, true, false, declined = true))
    }

    @Test fun usabilityFollowsTheIncludeToggles() {
        val both = AssistantScreenContextSettings()
        val textOnly = AssistantScreenContextSettings(includeScreenshot = false)
        val shotOnly = AssistantScreenContextSettings(includeScreenText = false)
        val none = AssistantScreenContextSettings(includeScreenshot = false, includeScreenText = false)
        val empty = ScreenContext(capturedAt = 1L)
        val withText = ScreenContext(capturedAt = 1L, text = "Hello")
        val withShot = ScreenContext(capturedAt = 1L, screenshot = mockk(relaxed = true))
        val withUri = ScreenContext(capturedAt = 1L, webUri = mockk<Uri>())

        assertFalse(ScreenContextPolicy.usable(empty, both))
        assertTrue(ScreenContextPolicy.usable(withText, both))
        assertTrue(ScreenContextPolicy.usable(withText, textOnly))
        assertFalse(ScreenContextPolicy.usable(withText, shotOnly))
        assertTrue(ScreenContextPolicy.usable(withShot, shotOnly))
        assertFalse(ScreenContextPolicy.usable(withShot, textOnly))
        assertTrue(ScreenContextPolicy.usable(withUri, textOnly))
        assertFalse(ScreenContextPolicy.usable(withText, none))
        assertFalse(ScreenContextPolicy.usable(withShot, none))
    }

    @Test fun storedModeFallsBackToAutomatic() {
        assertEquals(ScreenContextMode.AUTOMATIC, ScreenContextMode.fromStored(null))
        assertEquals(ScreenContextMode.AUTOMATIC, ScreenContextMode.fromStored("bogus"))
        assertEquals(ScreenContextMode.NEVER, ScreenContextMode.fromStored("NEVER"))
        assertEquals(ScreenContextMode.ALWAYS, ScreenContextMode.fromStored("ALWAYS"))
    }
}
