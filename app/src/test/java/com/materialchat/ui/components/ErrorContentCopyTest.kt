package com.materialchat.ui.components

import androidx.compose.ui.unit.dp
import com.materialchat.assistant.ui.AssistantCopy
import com.materialchat.ui.screens.chat.components.ChatErrorCopy
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.SquircleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The error verb is "Try again" app-wide (W5 orchestrator ruling); every owner's copy constant
 * must equal the shared one so the three surfaces cannot drift back to "Retry".
 */
class ErrorContentCopyTest {

    @Test fun tryAgainIsTheOneRetryVerb() {
        assertEquals("Try again", ErrorContentCopy.TRY_AGAIN)
        assertEquals(ErrorContentCopy.TRY_AGAIN, ChatErrorCopy.TRY_AGAIN)
        assertEquals(ErrorContentCopy.TRY_AGAIN, AssistantCopy.AFTER_ERROR)
    }

    @Test fun copyFollowsTheNoticeRules() {
        // One-phrase action labels carry no trailing period; sentence case; no ASCII ellipsis.
        for (text in listOf(ErrorContentCopy.TRY_AGAIN, ErrorContentCopy.GENERIC_HEADLINE)) {
            assertFalse(text, text.endsWith("."))
            assertFalse(text, text.contains("..."))
            assertTrue(text, text.first().isUpperCase())
            assertTrue(text, text.drop(1).none { it.isUpperCase() })
        }
    }

    @Test fun stateCardGeometryMatchesTheChatEmptyState() {
        // ChatEmptyState: 24dp inner padding, 360dp width cap.
        assertEquals(24.dp, StateCardPadding)
        assertEquals(360.dp, StateCardMaxWidth)
    }

    @Test fun emptyAndErrorStatesShareTheCardSquircle() {
        // W7 part 1 (audit F3): ExpressiveEmptyState and ErrorContent both draw ExpressiveStateCard,
        // whose container is the one card token, not a round corner.
        assertSame(ExpressiveShapes.Card, StateCardShape)
        assertEquals(SquircleShape(28.dp), StateCardShape)
    }
}
