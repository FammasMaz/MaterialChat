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
 */
fun Modifier.tapThroughScroll(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(onClick) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
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
