package com.materialchat.assistant.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Guards the assistant card's vertical anchoring against the IME.
 *
 * The regression this covers: on a 1068dp screen with Gboard open the card was pinned to the top
 * with a 301dp gap above the keyboard, because the scrim reserved 727dp for a 435dp keyboard.
 */
class AssistantKeyboardSpacingTest {

    @Test
    fun `open keyboard reserves the ime height only`() {
        // Gboard on a 1068dp screen: 435dp keyboard with the 24dp navigation bar underneath it.
        assertEquals(
            1218,
            AssistantKeyboardSpacing.bottomPaddingPx(imeBottomPx = 1218, navigationBarBottomPx = 67)
        )
    }

    @Test
    fun `closed keyboard reserves the navigation bar`() {
        assertEquals(
            67,
            AssistantKeyboardSpacing.bottomPaddingPx(imeBottomPx = 0, navigationBarBottomPx = 67)
        )
    }

    @Test
    fun `keyboard and navigation bar are not added together`() {
        // The IME covers the navigation bar, so reserving both would lift the card by 24dp.
        assertEquals(
            1218,
            AssistantKeyboardSpacing.bottomPaddingPx(imeBottomPx = 1218, navigationBarBottomPx = 67)
        )
    }

    @Test
    fun `missing insets reserve nothing`() {
        assertEquals(0, AssistantKeyboardSpacing.bottomPaddingPx(imeBottomPx = 0, navigationBarBottomPx = 0))
    }

    @Test
    fun `negative insets reserve nothing`() {
        assertEquals(
            0,
            AssistantKeyboardSpacing.bottomPaddingPx(imeBottomPx = -12, navigationBarBottomPx = -4)
        )
    }

    @Test
    fun `card bottom lands one gap above the keyboard`() {
        // 1344x2992 at density 2.8: the reported device geometry.
        val hostHeightPx = 2992
        val imeBottomPx = 1218
        val gapPx = (AssistantKeyboardSpacing.GAP_DP * 2.8f).toInt()
        val paddingPx = AssistantKeyboardSpacing.bottomPaddingPx(imeBottomPx, 67)

        val cardBottomPx = hostHeightPx - paddingPx - gapPx
        val imeTopPx = hostHeightPx - imeBottomPx

        assertEquals(imeTopPx - gapPx, cardBottomPx)
        // The reported defect put the card at 932px, in the top third of the screen.
        assertTrue(
            "card bottom $cardBottomPx must sit in the lower half of a $hostHeightPx px screen",
            cardBottomPx > hostHeightPx / 2
        )
    }

    @Test
    fun `hosted geometry check still measures the card edge at 8dp`() {
        val script = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .map { File(it, "scripts/check-assistant-window.sh") }
            .firstOrNull { it.isFile }
        assumeTrue("hosted geometry script is not present in this checkout", script != null)
        val source = script!!.readText()

        assertTrue(
            "the hosted check must measure the real card edge",
            source.contains("assistant_sheet")
        )
        assertTrue(
            "the hosted check must keep the 8dp +-2dp card-to-keyboard gap",
            Regex("""abs\(gap\s*-\s*8\)\s*<=\s*2""").containsMatchIn(source)
        )
        assertTrue(
            "the hosted check must keep rejecting keyboard overlap",
            Regex("""b\[3\]\s*<=\s*top""").containsMatchIn(source)
        )
    }

    @Test
    fun `ime insets drive spacing only from api 30 where the session window delivers them`() {
        // Regression: API 26 hosted gate showed the keyboard covering the card when the
        // IME-inset path ran on a window that receives no IME inset below API 30.
        org.junit.Assert.assertFalse(AssistantKeyboardSpacing.usesImeInsets(26))
        org.junit.Assert.assertFalse(AssistantKeyboardSpacing.usesImeInsets(29))
        org.junit.Assert.assertTrue(AssistantKeyboardSpacing.usesImeInsets(30))
        org.junit.Assert.assertTrue(AssistantKeyboardSpacing.usesImeInsets(35))
    }
}
