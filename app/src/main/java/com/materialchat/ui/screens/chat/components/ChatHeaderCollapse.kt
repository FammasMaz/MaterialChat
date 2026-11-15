package com.materialchat.ui.screens.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Scroll-driven collapse for the chat header. Pure Kotlin so the thresholds are testable
 * without a clock or a composition.
 *
 * The header collapses after the user has travelled [collapseThresholdPx] toward newer
 * messages (content moving up, negative deltas) and expands again after travelling
 * [expandThresholdPx] back toward older messages. Travel is accumulated per direction and
 * reset whenever the direction flips, so a jittery finger never crosses a threshold by
 * accident. Programmatic scrolls (auto-follow while streaming) are filtered out by the
 * caller; this machine only sees what it is fed.
 */
class ChatHeaderCollapseMachine(
    val collapseThresholdPx: Float,
    val expandThresholdPx: Float
) {
    init {
        require(collapseThresholdPx > 0f) { "collapseThresholdPx must be positive" }
        require(expandThresholdPx > 0f) { "expandThresholdPx must be positive" }
    }

    var isCollapsed: Boolean = false
        private set

    private var travel = 0f

    /**
     * Feed one consumed scroll delta in nested-scroll coordinates: negative when the list
     * content moves up (reading newer messages), positive when it moves down.
     *
     * @return true when the collapsed state changed.
     */
    fun onScroll(consumedY: Float): Boolean {
        if (consumedY == 0f || consumedY.isNaN()) return false
        if (travel != 0f && (travel < 0f) != (consumedY < 0f)) travel = 0f
        travel += consumedY
        val before = isCollapsed
        when {
            travel <= -collapseThresholdPx -> {
                isCollapsed = true
                travel = 0f
            }
            travel >= expandThresholdPx -> {
                isCollapsed = false
                travel = 0f
            }
        }
        return before != isCollapsed
    }

    /** Force the expanded state (the list reached its top, or a new conversation opened). */
    fun expand(): Boolean {
        travel = 0f
        val changed = isCollapsed
        isCollapsed = false
        return changed
    }

    /** Force the collapsed state. */
    fun collapse(): Boolean {
        travel = 0f
        val changed = !isCollapsed
        isCollapsed = true
        return changed
    }

    companion object {
        /** Travel toward newer messages before the masthead folds into the control row. */
        const val DEFAULT_COLLAPSE_DP = 96

        /** Travel toward older messages before the masthead unfolds; easier than collapsing. */
        const val DEFAULT_EXPAND_DP = 56
    }
}

/**
 * Compose holder for [ChatHeaderCollapseMachine]: exposes [isCollapsed] as snapshot state and a
 * [NestedScrollConnection] to chain on the scaffold. Only user-driven scroll is counted, so the
 * auto-follow scroll while a reply streams never folds the header on its own.
 */
@Stable
class ChatHeaderScrollState internal constructor(
    private val machine: ChatHeaderCollapseMachine
) {
    var isCollapsed: Boolean by mutableStateOf(machine.isCollapsed)
        private set

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (source == NestedScrollSource.UserInput && machine.onScroll(consumed.y)) {
                isCollapsed = machine.isCollapsed
            }
            return Offset.Zero
        }
    }

    fun expand() {
        if (machine.expand()) isCollapsed = false
    }
}

@Composable
fun rememberChatHeaderScrollState(): ChatHeaderScrollState {
    val density = LocalDensity.current
    return remember(density) {
        ChatHeaderScrollState(
            ChatHeaderCollapseMachine(
                collapseThresholdPx = with(density) { ChatHeaderCollapseMachine.DEFAULT_COLLAPSE_DP.dp.toPx() },
                expandThresholdPx = with(density) { ChatHeaderCollapseMachine.DEFAULT_EXPAND_DP.dp.toPx() }
            )
        )
    }
}
