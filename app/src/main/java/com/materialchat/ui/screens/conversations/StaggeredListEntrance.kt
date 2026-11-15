package com.materialchat.ui.screens.conversations

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
import kotlinx.coroutines.delay

/**
 * Tracks whether the conversation list is still inside its first-show window.
 *
 * Rows that compose while the state is armed (and sit within the first
 * [MaxAnimatedRows]) play a staggered offset+fade entrance; rows that compose
 * later — scrolled into view, filter switches, list refreshes — appear in place.
 * The armed flag is saved so returning from a chat never replays the entrance.
 * It is deliberately not snapshot state: each row captures its decision once,
 * so disarming must not recompose the visible rows.
 */
@Stable
class StaggeredEntranceState internal constructor(initiallyArmed: Boolean) {
    /** True until the first-show window has elapsed. */
    var armed: Boolean = initiallyArmed
        internal set

    /** Whether the row at [index] should play the entrance when it first composes. */
    fun claim(index: Int): Boolean = armed && index < MaxAnimatedRows

    companion object {
        /** Only the rows that can be on screen at first show animate. */
        const val MaxAnimatedRows: Int = 12

        internal val Saver: Saver<StaggeredEntranceState, Boolean> = Saver(
            save = { it.armed },
            restore = { StaggeredEntranceState(it) }
        )
    }
}

/** Milliseconds between consecutive row starts. */
private const val StaggerStepMillis = 32L

/** How long after the list first composes rows may still claim an entrance. */
private const val ArmedWindowMillis = 900L

/**
 * Remembers a [StaggeredEntranceState] that disarms itself once the first-show
 * window elapses. Create it inside the list composable so the window starts
 * when the rows actually appear, not while the loading indicator is up.
 */
@Composable
fun rememberStaggeredEntranceState(): StaggeredEntranceState {
    val state = rememberSaveable(saver = StaggeredEntranceState.Saver) {
        StaggeredEntranceState(initiallyArmed = true)
    }
    LaunchedEffect(state) {
        delay(ArmedWindowMillis)
        state.armed = false
    }
    return state
}

/**
 * Plays a one-shot offset+fade entrance for a list row, staggered by [index].
 *
 * The decision to animate is captured on first composition, so a row that
 * started animating keeps going after [state] disarms, and a row that composes
 * after the window never animates. The graphics layer reads the animation
 * directly, so nothing recomposes per frame.
 *
 * @param index Position of the row in the list; drives the stagger delay.
 * @param state First-show state owned by the enclosing list.
 * @param offset Vertical distance the row travels while fading in.
 */
@Composable
fun Modifier.staggeredEntrance(
    index: Int,
    state: StaggeredEntranceState,
    offset: Dp = 28.dp
): Modifier {
    val animate = remember { state.claim(index) }
    if (!animate) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        delay(index * StaggerStepMillis)
        progress.animateTo(1f, ExpressiveMotion.Spatial.listEntrance())
    }
    val offsetPx = with(LocalDensity.current) { offset.toPx() }
    return graphicsLayer {
        val value = progress.value
        translationY = (1f - value) * offsetPx
        alpha = value.coerceIn(0f, 1f)
    }
}
