package com.materialchat.ui.screens.chat.components

import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.ExpressiveButtonSize
import com.materialchat.ui.theme.CopyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inline edit row under a user bubble never wraps (W8 chat part 3, orchestrator ruling):
 * the submit label is "Save & Submit" where both labelled buttons fit the bubble's content
 * width and "Save" otherwise. The widths below are the W7 arithmetic for a 360dp screen
 * (82% bubble = 295dp, minus the 16dp bubble padding on each side = 263dp) with labelLarge
 * Bold labels at font scale 1.0 (~37dp "Cancel", ~92dp "Save & Submit") and 1.3 (~48dp,
 * ~120dp); the composable measures the real widths with a TextMeasurer.
 */
class EditRowLayoutTest {

    private val rowAt360 = (360 * 0.82f).dp - 32.dp

    @Test
    fun `full label stays at font scale one on a 360dp screen`() {
        assertEquals(
            EditRowCopy.SAVE_FULL,
            editRowSaveLabel(rowWidth = rowAt360, cancelLabelWidth = 37.dp, fullSaveLabelWidth = 92.dp)
        )
    }

    @Test
    fun `label shortens to Save at font scale one point three on a 360dp screen`() {
        // 48 + 48 (text insets) + 8 + 120 + 56 (filled insets) = 280dp > 263dp: the full
        // label would wrap or clip Cancel, so the row takes the short label instead.
        assertEquals(
            EditRowCopy.SAVE_SHORT,
            editRowSaveLabel(rowWidth = rowAt360, cancelLabelWidth = 48.dp, fullSaveLabelWidth = 120.dp)
        )
    }

    @Test
    fun `the boundary is inclusive and follows the button insets`() {
        val size = ExpressiveButtonSize.Default
        val cancel = 48.dp + size.textInset * 2
        val full = 120.dp + size.inset * 2
        val exact = cancel + EditRowGap + full
        assertEquals(EditRowCopy.SAVE_FULL, editRowSaveLabel(exact, 48.dp, 120.dp))
        assertEquals(EditRowCopy.SAVE_SHORT, editRowSaveLabel(exact - 1.dp, 48.dp, 120.dp))
    }

    @Test
    fun `the short row fits where the full one does not`() {
        // "Save" at 1.3 is ~40dp wide: 96 + 8 + 96 = 200dp, well inside 263dp, so shortening
        // always resolves the overflow it is chosen for.
        val size = ExpressiveButtonSize.Default
        val shortRow = editRowButtonWidth(48.dp, size.textInset) + EditRowGap + editRowButtonWidth(40.dp, size.inset)
        assertTrue("short row $shortRow must fit $rowAt360", shortRow <= rowAt360)
    }

    @Test
    fun `labels are phrases and the short one opens the full one`() {
        assertTrue(CopyRules.isPhrase(EditRowCopy.CANCEL))
        assertTrue(CopyRules.isPhrase(EditRowCopy.SAVE_FULL))
        assertTrue(CopyRules.isPhrase(EditRowCopy.SAVE_SHORT))
        assertTrue(EditRowCopy.SAVE_FULL.startsWith(EditRowCopy.SAVE_SHORT))
        assertEquals(8.dp, EditRowGap)
    }
}
