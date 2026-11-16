package com.materialchat.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Lets a tap reach this control even when an enclosing scrollable consumed the pointer-down.
 *
 * A `LazyColumn` whose scroll is still in progress (the sub-pixel tail of a fling, the
 * overscroll spring-back, or a programmatic `animateScrollBy`) consumes every pointer-down
 * in the Initial pass so that the touch stops the scroll
 * (`DragGestureNode.processInitialDownState` with `startDragImmediately()` ==
 * `scrollableState.isScrollInProgress`). Every `clickable` below the list then ignores that
 * down (`isChangedToDown(requireUnconsumed = true)`), so the control is dead for as long as
 * the list counts as scrolling. Short chats never scroll, so they never hit this; long chats
 * hit it after nearly every fling.
 *
 * This modifier watches for a down that arrives already consumed, tracks it inside touch
 * slop, and fires [onClick] on the up — stopping the list and pressing the control happen
 * together, which is what the user meant by the tap. An unconsumed down is left to the
 * normal click handling, so this never double-fires.
 *
 * The down is observed in the Initial pass on purpose: that pass runs parent-first, so the
 * only thing that can have consumed it by then is an ancestor such as the scrolling list.
 * Observing in the Main pass would also see a consumption by the control's own `clickable`
 * when that clickable sits later in the modifier chain (as `Surface(onClick)` places it) —
 * then both would fire and a toggle would cancel itself out.
 */
fun Modifier.tapThroughScroll(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(onClick) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!down.isConsumed) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            var tapped = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.size > 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val travelled = (change.position - down.position).getDistance()
                if (change.changedToUpIgnoreConsumed()) {
                    tapped = travelled <= slop
                    break
                }
                if (!change.pressed || travelled > slop) break
            }
            if (tapped) onClick()
        }
    }
