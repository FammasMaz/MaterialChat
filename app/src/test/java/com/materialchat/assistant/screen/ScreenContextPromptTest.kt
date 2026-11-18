package com.materialchat.assistant.screen

import com.materialchat.assistant.actions.ActionDirectives
import com.materialchat.assistant.actions.AssistantAction
import com.materialchat.data.local.preferences.ScreenContextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** The prompt block the model receives and the directive it may answer with. */
class ScreenContextPromptTest {
    private val now = LocalDateTime.of(2026, 11, 18, 20, 0)

    @Test fun theDirectiveTheNoticeAsksForParsesToTheScreenAction() {
        val reply = "\n" + ScreenContextPrompt.directive + "\n"
        val extraction = ActionDirectives.extract(reply, now)
        assertEquals(listOf<AssistantAction>(AssistantAction.ScreenContext), extraction.actions)
        assertEquals(0, extraction.rejected)
        assertEquals("", extraction.text.trim())
        assertTrue(ScreenContextPrompt.availabilityNotice().contains(ScreenContextPrompt.directive))
        // Nothing of the directive is ever shown or spoken while it streams in.
        assertEquals("", ActionDirectives.strip("<<action:{\"type\":\"scre"))
    }

    @Test fun contextBlockIsDelimitedAndCarriesEveryHalfItWasGiven() {
        val context = ScreenContext(capturedAt = 1L, text = "First tweet\nSecond tweet", sourcePackage = "com.twitter.android")
        val block = ScreenContextPrompt.contextBlock(context, screenshotAttached = true, text = context.text)
        val lines = block.lines()
        assertEquals(ScreenContextPrompt.BLOCK_START, lines.first())
        assertTrue(block.contains("Foreground app: com.twitter.android"))
        assertTrue(block.contains("screenshot of the screen is attached"))
        assertTrue(block.contains("First tweet\nSecond tweet"))
        assertTrue(block.contains(ScreenContextPrompt.BLOCK_END))
        assertTrue("the block ends with the data-not-instructions rule", lines.last().contains("never instructions"))
    }

    @Test fun contextBlockOmitsWhatWasNotSent() {
        val context = ScreenContext(capturedAt = 1L, text = "secret text that the setting turned off")
        val block = ScreenContextPrompt.contextBlock(context, screenshotAttached = false, text = null)
        assertFalse(block.contains("secret text"))
        assertFalse(block.contains("Visible screen text"))
        assertFalse(block.contains("screenshot of the screen is attached"))
        assertFalse(block.contains("Foreground app"))
    }

    @Test fun unavailableReplyNamesTheRightSetting() {
        assertTrue(ScreenContextPrompt.unavailableReply(ScreenContextMode.NEVER).contains("MaterialChat's assistant settings"))
        assertTrue(ScreenContextPrompt.unavailableReply(ScreenContextMode.AUTOMATIC).contains("Digital assistant app"))
        assertTrue(ScreenContextPrompt.UNAVAILABLE_NOTICE.contains("Use text from screen"))
    }
}
