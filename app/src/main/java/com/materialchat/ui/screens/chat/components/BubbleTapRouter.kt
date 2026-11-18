package com.materialchat.ui.screens.chat.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Last-resort delivery of a tap to a control inside a message bubble.
 *
 * On long chats on real devices the memory chips (and the thinking header) have been seen to
 * miss a tap that the bubble itself received: the bubble rippled, the chip never pressed.
 * The hosted emulators never reproduce it, so instead of guessing which layer eats the tap
 * the bubble routes the tap itself. Each control registers its root-space bounds and action
 * here; the bubble's own tap detector (which only ever sees a down the inner controls left
 * unconsumed, so a working chip can never fire twice) hands the tap to the control whose
 * bounds contain it.
 */
internal class BubbleTapRouter {
    private class Target(val bounds: Rect, val action: () -> Unit)

    private val targets = LinkedHashMap<Any, Target>()

    val size: Int get() = targets.size

    /** Registers (or re-registers with fresh bounds) the control identified by [key]. */
    fun register(key: Any, bounds: Rect, action: () -> Unit) {
        targets[key] = Target(bounds, action)
    }

    fun unregister(key: Any) {
        targets.remove(key)
    }

    /** Fires the first registered control whose bounds contain [rootPosition]; true when one did. */
    fun route(rootPosition: Offset): Boolean {
        val hit = targets.values.firstOrNull { it.bounds.contains(rootPosition) } ?: return false
        hit.action()
        return true
    }
}

/**
 * Makes this node a routing target of [router]: its root-space bounds are kept current from
 * placement, [action] always resolves to the latest lambda, and the target is dropped when the
 * node leaves the composition.
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
        router.register(key, coordinates.boundsInRoot()) { current() }
    }
}
