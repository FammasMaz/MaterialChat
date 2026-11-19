package com.materialchat.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The in-pill photo reveal: opened by its affordance, closed by a choice or by losing the pill. */
class AssistantAttachRevealTest {
    @Test fun startsClosed() {
        assertFalse(AssistantAttachRevealState().open)
    }

    @Test fun affordanceToggles() {
        val opened = AssistantAttachRevealState().toggled()
        assertTrue(opened.open)
        assertFalse(opened.toggled().open)
    }

    @Test fun choosingAnActionCollapses() {
        assertFalse(AssistantAttachRevealState(open = true).modeChosen().open)
        assertFalse(AssistantAttachRevealState(open = false).modeChosen().open)
    }

    @Test fun hidingTheAffordanceCollapsesButShowingItDoesNotOpen() {
        assertFalse(AssistantAttachRevealState(open = true).affordanceVisibilityChanged(false).open)
        assertTrue(AssistantAttachRevealState(open = true).affordanceVisibilityChanged(true).open)
        assertFalse(AssistantAttachRevealState(open = false).affordanceVisibilityChanged(true).open)
    }

    @Test fun hidingTheKeyboardCollapses() {
        assertFalse(AssistantAttachRevealState(open = true).keyboardHidden().open)
    }

    @Test fun sendingTheTypedQuestionCollapses() {
        // Keyboard mode survives the send, so nothing else would close the reveal over the answer.
        assertFalse(AssistantAttachRevealState(open = true).questionSent().open)
        assertFalse(AssistantAttachRevealState(open = false).questionSent().open)
    }

    @Test fun hidingThePillCollapses() {
        assertFalse(AssistantAttachRevealState(open = true).pillHidden().open)
    }

    @Test fun affordanceAppearsOnlyInKeyboardModeOrWithPhotos() {
        assertFalse("the resting voice pill has no affordance",
            AssistantAttachRevealState.affordanceVisible(keyboardMode = false, attachmentCount = 0))
        assertTrue(AssistantAttachRevealState.affordanceVisible(keyboardMode = true, attachmentCount = 0))
        assertTrue(AssistantAttachRevealState.affordanceVisible(keyboardMode = false, attachmentCount = 1))
    }

    @Test fun pillExpandsForTheRevealOrForPhotosOnly() {
        assertFalse("nothing attached and closed: the 76dp pill",
            AssistantAttachRevealState.expanded(open = false, attachmentCount = 0))
        assertTrue(AssistantAttachRevealState.expanded(open = true, attachmentCount = 0))
        assertTrue("photos keep the thumbnails visible after the reveal closes",
            AssistantAttachRevealState.expanded(open = false, attachmentCount = 2))
    }

    @Test fun revealSurvivesRecompositionAsPlainValue() {
        // The state is a value: identical inputs give identical states (remember/equality safe).
        assertEquals(AssistantAttachRevealState(open = true), AssistantAttachRevealState().toggled())
    }
}
