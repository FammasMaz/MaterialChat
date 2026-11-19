package com.materialchat.ui.screens.chat.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.ExpressiveButtonSize

/** The labels of the inline edit row under a user bubble. */
internal object EditRowCopy {
    const val CANCEL = "Cancel"

    /** The submit label where it fits. */
    const val SAVE_FULL = "Save & Submit"

    /** The submit label when the row would otherwise overflow (orchestrator ruling, W8). */
    const val SAVE_SHORT = "Save"
}

/** The space between the Cancel and the submit button. */
internal val EditRowGap: Dp = 8.dp

/** The width of a labelled [com.materialchat.ui.components.ExpressiveButton]: label plus its two insets. */
internal fun editRowButtonWidth(labelWidth: Dp, inset: Dp): Dp = labelWidth + inset * 2

/**
 * The submit label of the edit row (W8 chat part 3). The row holds a text-style Cancel
 * ([ExpressiveButtonSize.textInset] on each side) and a tonal submit button
 * ([ExpressiveButtonSize.inset] on each side) separated by [gap]; when both labelled buttons
 * fit [rowWidth] the full "Save & Submit" stays, otherwise the submit label shortens to
 * "Save" so the row never wraps and Cancel is never clipped. Widths are the measured label
 * widths at the current font scale, so the decision follows the type scale rather than a
 * screen-width guess.
 */
internal fun editRowSaveLabel(
    rowWidth: Dp,
    cancelLabelWidth: Dp,
    fullSaveLabelWidth: Dp,
    size: ExpressiveButtonSize = ExpressiveButtonSize.Default,
    gap: Dp = EditRowGap
): String {
    val cancel = editRowButtonWidth(cancelLabelWidth, size.textInset)
    val full = editRowButtonWidth(fullSaveLabelWidth, size.inset)
    return if (cancel + gap + full <= rowWidth) EditRowCopy.SAVE_FULL else EditRowCopy.SAVE_SHORT
}
