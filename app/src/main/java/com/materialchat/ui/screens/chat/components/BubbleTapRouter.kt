package com.materialchat.ui.screens.chat.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Last-resort delivery of a tap to a control inside a message bubble.
 *
 * On long chats on real devices the memory chips (and the thinking header) miss taps that the
 * bubble itself receives: the bubble ripples, the chip never presses. The ripple is the hard
 * evidence about where the gesture goes. The bubble's `combinedClickable` only starts a press
 * when the pointer-down reaches it unconsumed in the Main pass
 * (`CombinedClickableNode.onPointerEvent`, `isChangedToDown(requireUnconsumed = true)`), and the
 * Main pass runs child-first, so a chip `clickable` that had been on the hit path would have
 * consumed that same down first and the bubble would never ripple. A rippling bubble therefore
 * means the chip's node was not on the hit path at all. Nothing placed below the bubble's own
 * node (the chip's clickable, `tapThroughScroll`, a router on the inner Column) can observe such
 * a gesture; only a handler on the bubble node itself can.
 *
 * So the bubble node routes the tap. Each control registers its root-space bounds and action;
 * [bubbleTapRouting], sitting on the bubble's own modifier chain, tracks every down from the
 * Initial pass and on an up within touch slop fires the control whose bounds contain it. It
 * does not care whether anything consumed the up: the bubble's `combinedClickable` consumes it
 * and the chip's own path never reaches here when the chip handled the tap. Exactly-once
 * delivery is explicit instead: a control's own click path marks the gesture delivered through
 * [delivering], and the router skips a gesture that was marked.
 */
internal class BubbleTapRouter {
    private class Target(val bounds: Rect, val action: () -> Unit)

    private val targets = LinkedHashMap<Any, Target>()
    private var deliveredInGesture = false

    val size: Int get() = targets.size

    /** Registers (or re-registers with fresh bounds) the control identified by [key]. */
    fun register(key: Any, bounds: Rect, action: () -> Unit) {
        targets[key] = Target(bounds, action)
    }

    fun unregister(key: Any) {
        targets.remove(key)
    }

    /** A new pointer went down inside the bubble: nothing has been delivered for it yet. */
    fun beginGesture() {
        deliveredInGesture = false
    }

    /** A control's own click handling delivered the tap of the current gesture. */
    fun markDelivered() {
        deliveredInGesture = true
    }

    /** Wraps [action] so that firing it through a control's own path counts as delivered. */
    fun delivering(action: () -> Unit): () -> Unit = {
        markDelivered()
        action()
    }

    /**
     * Fires the first registered control whose bounds contain [rootPosition]; true when one did.
     * A gesture the control already delivered itself is skipped so a toggle never fires twice.
     */
    fun route(rootPosition: Offset): Boolean {
        if (deliveredInGesture) {
            deliveredInGesture = false
            return false
        }
        val hit = targets.values.firstOrNull { it.bounds.contains(rootPosition) } ?: return false
        hit.action()
        return true
    }
}

/**
 * Makes this node a routing target of [router]: its root-space bounds are kept current from
 * placement, [action] always resolves to the latest lambda, and the target is dropped when the
 * node leaves the composition. Bounds are registered unclipped: a tap is compared against where
 * the control is laid out, not against what an ancestor (`animateContentSize` clips to its
 * animated size, the list clips to its viewport) currently lets through.
 */
internal fun Modifier.bubbleTapTarget(
    router: BubbleTapRouter,
    key: Any,
    action: () -> Unit
): Modifier = composed {
    val current by rememberUpdatedState(action)
    DisposableEffect(router, key) {
        onDispose { router.unregister(key) }
    }
    onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) return@onGloballyPositioned
        val bounds = coordinates.findRootCoordinates()
            .localBoundingBoxOf(coordinates, clipBounds = false)
        router.register(key, bounds) { current() }
    }
}

/**
 * Routes taps for [router] from this node. Place it on the bubble's own modifier chain, right
 * before the bubble's `combinedClickable`, so it runs on the one node that is proven to receive
 * the gesture whenever the bubble ripples. Observes the down in the Initial pass (nothing below
 * can have consumed it yet) and the rest in Main; the up is taken whether or not something
 * consumed it, and is consumed here when a control was routed.
 */
internal fun Modifier.bubbleTapRouting(router: BubbleTapRouter): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    onGloballyPositioned { coordinates = it }
        .pointerInput(router) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                router.beginGesture()
                val slop = viewConfiguration.touchSlop
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.size > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val travelled = (change.position - down.position).getDistance()
                    if (change.changedToUpIgnoreConsumed()) {
                        if (travelled <= slop) {
                            val root = coordinates
                                ?.takeIf { it.isAttached }
                                ?.localToRoot(change.position)
                            if (root != null && router.route(root)) change.consume()
                        }
                        break
                    }
                    if (!change.pressed || travelled > slop) break
                }
            }
        }
}
