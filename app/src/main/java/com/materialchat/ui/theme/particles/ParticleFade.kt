/*
 * Presence (fade in/out) math for the neural particle field. Pure Kotlin so the timing and the
 * frame-request gate are unit tested; the composable only feeds it frame times.
 */
package com.materialchat.ui.theme.particles

import androidx.compose.runtime.Immutable

/** Fade timing. Durations are scaled by the system animator duration scale at run time. */
@Immutable
data class ParticleFadeSpec(
    val fadeInMillis: Long = 600L,
    val fadeOutMillis: Long = 800L,
) {
    init {
        require(fadeInMillis >= 0L) { "fadeInMillis must be >= 0" }
        require(fadeOutMillis >= 0L) { "fadeOutMillis must be >= 0" }
    }
}

/** Smoothstep ease shared with the band envelope so the fade matches the field's own falloff. */
internal fun fadeEase(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** The presence the field should settle at: shown only while active and not idle-expired. */
internal fun fadeTarget(active: Boolean, idleExpired: Boolean): Float =
    if (active && !idleExpired) 1f else 0f

/** An idle timeout is only armed when positive; null or a non-positive value disables it. */
internal fun effectiveIdleTimeout(idleTimeoutMillis: Long?): Long? =
    idleTimeoutMillis?.takeIf { it > 0L }

/**
 * Presence [elapsedMillis] after a fade from [from] towards [target] started. A zero duration or
 * a zero [durationScale] (animations disabled) snaps to the target immediately.
 */
internal fun fadePresence(
    from: Float,
    target: Float,
    elapsedMillis: Long,
    spec: ParticleFadeSpec,
    durationScale: Float = 1f,
): Float {
    val base = if (target > from) spec.fadeInMillis else spec.fadeOutMillis
    val scale = if (durationScale.isFinite() && durationScale > 0f) durationScale else 0f
    val duration = base * scale
    if (duration <= 0f || elapsedMillis >= duration) return target
    if (elapsedMillis <= 0L) return from
    return from + (target - from) * fadeEase(elapsedMillis / duration)
}

/**
 * Whether the field must keep requesting animation frames. Once the presence has fully faded
 * nothing is drawn, so no frames are needed regardless of the other gates.
 */
internal fun shouldRequestParticleFrames(
    visible: Boolean,
    presence: Float,
    painterAnimated: Boolean,
    motionEnabled: Boolean,
    animationScale: Float,
): Boolean = visible && presence > 0f && painterAnimated && motionEnabled && animationScale > 0f
