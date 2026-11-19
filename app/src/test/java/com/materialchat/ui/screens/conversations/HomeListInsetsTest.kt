package com.materialchat.ui.screens.conversations

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The floating "+" toolbar overlapped the last row because the list's bottom padding
 * ignored the navigation-bar inset the toolbar is padded by. The clearance must cover
 * inset + margin + toolbar + gap, and never shrink below the toolbar itself.
 */
class HomeListInsetsTest {

    @Test
    fun `clearance stacks inset, margin, toolbar height and gap`() {
        val expected = 24.dp + HomeListInsets.ToolbarBottomMargin + HomeListInsets.ToolbarHeight + HomeListInsets.RowGap
        assertEquals(expected, HomeListInsets.bottomClearance(navigationBarInset = 24.dp))
    }

    @Test
    fun `zero inset still clears the toolbar`() {
        val clearance = HomeListInsets.bottomClearance(navigationBarInset = 0.dp)
        assertTrue(clearance >= HomeListInsets.ToolbarBottomMargin + HomeListInsets.ToolbarHeight)
        assertEquals(96.dp, clearance)
    }

    @Test
    fun `negative inset is treated as zero`() {
        assertEquals(
            HomeListInsets.bottomClearance(navigationBarInset = 0.dp),
            HomeListInsets.bottomClearance(navigationBarInset = (-8).dp)
        )
    }

    @Test
    fun `clearance exceeds the old fixed 88dp once the inset is applied`() {
        assertTrue(HomeListInsets.bottomClearance(navigationBarInset = 0.dp) > 88.dp)
    }

    @Test
    fun `snackbar clearance covers inset, margin and toolbar but not the row gap`() {
        assertEquals(
            24.dp + HomeListInsets.ToolbarBottomMargin + HomeListInsets.ToolbarHeight,
            HomeListInsets.snackbarClearance(navigationBarInset = 24.dp)
        )
        assertEquals(
            HomeListInsets.bottomClearance(navigationBarInset = 24.dp) - HomeListInsets.RowGap,
            HomeListInsets.snackbarClearance(navigationBarInset = 24.dp)
        )
    }

    @Test
    fun `snackbar clearance treats a negative inset as zero`() {
        assertEquals(
            HomeListInsets.snackbarClearance(navigationBarInset = 0.dp),
            HomeListInsets.snackbarClearance(navigationBarInset = (-8).dp)
        )
    }
}
