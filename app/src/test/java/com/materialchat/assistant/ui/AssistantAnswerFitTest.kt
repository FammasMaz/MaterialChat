package com.materialchat.assistant.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State walk round 3 (W5), pinned: a 640dp window with the keyboard up (~280dp IME) leaves the
 * sheet ~344dp; the pill (76) and the 8dp gap leave the answer surface 260dp, and its 20/12 insets
 * leave 228dp of content. bodyMedium is 20sp, so one notice line is 20dp at font scale 1.0 and
 * 26dp at 1.3.
 */
class AssistantAnswerFitTest {

    private val squeezedContent = 260.dp - AssistantAnswerFit.SurfaceInsets

    @Test fun theSqueezedSurfaceIsTheDocumented228dpOfContent() {
        assertEquals(228.dp, squeezedContent)
        assertEquals(56.dp, AssistantAnswerFit.ToolbarHeight)
        assertEquals(74.dp, AssistantAnswerFit.noticeChrome(hasAction = true))
        assertEquals(24.dp, AssistantAnswerFit.noticeChrome(hasAction = false))
    }

    @Test fun theW4WeightsLeftTheNoticeNoMessageAtAll() {
        val share = AssistantAnswerFit.weightedShare(squeezedContent, markerHeight = 0.dp)
        assertEquals(74.dp, share)
        assertEquals(0.dp, AssistantAnswerFit.noticeMessageHeight(share, hasAction = true))
    }

    @Test fun theCapKeepsOneLineOfMessageAtEveryFontScale() {
        val cap = AssistantAnswerFit.noticeCap(squeezedContent, markerHeight = 0.dp)
        assertEquals(100.dp, cap)
        val message = AssistantAnswerFit.noticeMessageHeight(cap, hasAction = true)
        assertEquals(26.dp, message)
        assertTrue(message >= 26.dp) // one bodyMedium line at 1.3
        assertTrue(message >= 20.dp) // one line at 1.0 (two would need 40dp: the rest scrolls)
        // The exchange keeps its floor and the toolbar its full height: nothing leaves the clip.
        assertEquals(squeezedContent, cap + AssistantAnswerFit.ToolbarHeight + AssistantSpacing.Inset * 2 + AssistantAnswerFit.AnswerFloor)
    }

    @Test fun theUsingScreenMarkerComesOutOfTheCap() {
        val marker = 26.dp // one notice line at font scale 1.3
        val cap = AssistantAnswerFit.noticeCap(squeezedContent, markerHeight = marker)
        assertEquals(100.dp - marker - AssistantSpacing.Inset, cap)
        // 62dp: the chip and its gap still fit; the message has none and scrolls into view
        // only when the surface grows. Recorded as a residual, not a clip: the cap still
        // subtracts the toolbar, so the toolbar and the chip stay inside.
        assertTrue(cap >= AssistantAnswerFit.noticeChrome(hasAction = true) - AssistantSpacing.Inset)
    }

    @Test fun withoutTheKeyboardEveryNoticeFitsWhole() {
        // 0.55 of a 640dp window: 352dp surface, 320dp content.
        val cap = AssistantAnswerFit.noticeCap(352.dp - AssistantAnswerFit.SurfaceInsets, markerHeight = 0.dp)
        assertEquals(192.dp, cap)
        // The longest answer-surface notice (PROVIDER_REJECTED, 65 chars) is three 26dp lines at
        // 1.3 on the 240dp message width: 78 + 74 = 152dp < 192.
        assertTrue(AssistantAnswerFit.noticeMessageHeight(cap, hasAction = true) >= 26.dp * 3)
    }

    @Test fun aTinySurfaceNeverGoesNegative() {
        assertEquals(0.dp, AssistantAnswerFit.noticeCap(100.dp, markerHeight = 0.dp))
        assertEquals(0.dp, AssistantAnswerFit.weightedShare(60.dp, markerHeight = 0.dp))
        assertEquals(0.dp, AssistantAnswerFit.noticeMessageHeight(10.dp, hasAction = true))
    }
}
