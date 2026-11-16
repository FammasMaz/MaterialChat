package com.materialchat.ui.screens.conversations

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bottom clearance for the home lists so the last row never sits under the floating
 * navigation toolbar and its "+" button.
 *
 * The toolbar is overlaid by MainActivity, not by the Scaffold, so the list's padding
 * values carry no bottom inset: the clearance has to be computed here from the
 * navigation-bar inset plus the toolbar's own geometry. Pure arithmetic so it is
 * unit-testable on the JVM.
 */
object HomeListInsets {

    /** MainActivity's `padding(bottom = 16.dp)` under the toolbar row. */
    val ToolbarBottomMargin: Dp = 16.dp

    /** M3 HorizontalFloatingToolbar container height (holds the 56dp FAB). */
    val ToolbarHeight: Dp = 64.dp

    /** Breathing room between the toolbar's top edge and the last row's text. */
    val RowGap: Dp = 16.dp

    /**
     * Content padding the list needs below its last row.
     *
     * @param navigationBarInset bottom system-bar inset the toolbar is already padded by
     */
    fun bottomClearance(navigationBarInset: Dp): Dp =
        navigationBarInset.coerceAtLeast(0.dp) + ToolbarBottomMargin + ToolbarHeight + RowGap
}

/**
 * [HomeListInsets.bottomClearance] fed with the live navigation-bar inset, for use as the
 * bottom content padding of the home lists and the matching fast-scroll bar inset.
 */
@Composable
internal fun listBottomClearance(): Dp =
    HomeListInsets.bottomClearance(
        navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
