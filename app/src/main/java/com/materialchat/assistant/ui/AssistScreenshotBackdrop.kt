/*
 * The "shrunk screen" behind the assistant.
 *
 * Gemini scales the whole screen down when it is invoked. A third-party assistant cannot
 * transform other apps' windows, so this is the closest honest approximation: when the
 * platform hands the session the assist screenshot (VoiceInteractionSession.onHandleScreenshot,
 * delivered only when the session is shown with SHOW_WITH_SCREENSHOT and the user keeps
 * "Use screenshot" enabled under Settings > Apps > Default apps > Digital assistant app), that
 * screenshot is painted as the window's backdrop and springs from full size to [restScale] with
 * rounded corners, on black, under the dim scrim. On dismiss it springs back before the window
 * hides. Without a screenshot nothing is drawn and the plain scrim remains; no fakes.
 */
package com.materialchat.assistant.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Where the shrunk screen sits inside a window of [size]: centred, scaled by [scale]. */
internal fun screenshotBackdropFrame(size: Size, scale: Float): Rect {
    val s = if (scale.isFinite()) scale.coerceIn(0f, 1f) else 1f
    val w = size.width * s
    val h = size.height * s
    return Rect(Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h))
}

/**
 * Corner radius of the shrunk screen: square at full size (the real screen has no visible
 * frame), [fullRadiusPx] once it has reached [restScale], proportional in between so the corners
 * round as the screen shrinks and square off again as it grows back.
 */
internal fun screenshotBackdropCornerPx(fullRadiusPx: Float, scale: Float, restScale: Float): Float {
    if (restScale >= 1f || !restScale.isFinite()) return fullRadiusPx
    val t = ((1f - scale) / (1f - restScale)).coerceIn(0f, 1f)
    return fullRadiusPx * t
}

/**
 * Paints [screenshot] as this node's backdrop (behind its content and its own background),
 * shrunk to [restScale] with [cornerRadius] corners while [shrunk] is true and at full size
 * otherwise; the size change is an expressive spring. With a null [screenshot] this is a no-op.
 */
fun Modifier.assistScreenshotBackdrop(
    screenshot: Bitmap?,
    shrunk: Boolean,
    restScale: Float = 0.92f,
    cornerRadius: Dp = 28.dp,
): Modifier = composed {
    if (screenshot == null) return@composed Modifier
    val image = remember(screenshot) { screenshot.asImageBitmap() }
    val scale = remember(screenshot) { Animatable(1f) }
    LaunchedEffect(screenshot, shrunk) {
        // Effects spring with a touch of settle: the screen eases down, never bounces.
        scale.animateTo(if (shrunk) restScale else 1f, spring(dampingRatio = 0.8f, stiffness = 380f))
    }
    val clip = remember { Path() }
    Modifier.drawWithCache {
        val fullRadius = cornerRadius.toPx()
        onDrawBehind {
            // The session releases the bitmap on hide; a stale frame must never touch it.
            if (screenshot.isRecycled) return@onDrawBehind
            val frame = screenshotBackdropFrame(size, scale.value)
            val radius = screenshotBackdropCornerPx(fullRadius, scale.value, restScale)
            drawRect(Color.Black)
            clip.rewind()
            clip.addRoundRect(RoundRect(frame, CornerRadius(radius, radius)))
            clipPath(clip) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                    dstSize = IntSize(frame.width.roundToInt().coerceAtLeast(1), frame.height.roundToInt().coerceAtLeast(1))
                )
            }
        }
    }
}
