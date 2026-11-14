package com.materialchat.assistant.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.assistant.voice.VoiceState
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.LocalNeuralExpressiveEnabled
import com.materialchat.ui.theme.MaterialChatTheme
import com.materialchat.ui.theme.particles.LocalNeuralParticleRenderControl
import com.materialchat.ui.theme.particles.NeuralParticlePainterKind
import com.materialchat.ui.theme.particles.NeuralParticleRenderControl
import com.materialchat.ui.theme.particles.ParticleEdgeTreatment
import com.materialchat.ui.theme.particles.ParticleFadeSpec
import com.materialchat.ui.theme.particles.neuralParticleField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Renders the real assistant card inside the production decoration host (the full-window parent
 * that targets the card rect) and proves the ported particle field is visible, bounded, gated,
 * softened at the capsule edge, and retired by the presence fade.
 */
@RunWith(AndroidJUnit4::class)
class AssistantNeuralParticleFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun particleFieldRendersAroundTheCardWithoutChangingItsBounds() {
        val enabled = mutableStateOf(false)
        val active = mutableStateOf(true)
        val forceFallback = mutableStateOf(false)
        var density = 1f
        var hardwareAccelerated = false
        var painterKind: NeuralParticlePainterKind? = null
        var edgeTreatment: ParticleEdgeTreatment? = null
        var tileReady = false
        compose.setContent {
            density = LocalDensity.current.density
            hardwareAccelerated = LocalView.current.isHardwareAccelerated
            MaterialChatTheme(
                themeMode = AppPreferences.ThemeMode.DARK,
                dynamicColor = false,
                neuralExpressive = enabled.value
            ) {
                CompositionLocalProvider(
                    LocalNeuralParticleRenderControl provides NeuralParticleRenderControl(
                        timeSeconds = 4f,
                        onPainterResolved = { painterKind = it },
                        onTileReady = { tileReady = true },
                        onEdgeTreatmentResolved = { edgeTreatment = it }
                    )
                ) { Specimen(forceFallback = forceFallback.value, active = active.value) }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) { tileReady }
        assertTrue("Acceptance must use a hardware-accelerated host", hardwareAccelerated)
        val expectedPainter =
            if (Build.VERSION.SDK_INT >= 33) NeuralParticlePainterKind.Runtime else NeuralParticlePainterKind.Static
        assertEquals("The AGSL painter must be used on API 33+", expectedPainter, painterKind)
        val expectedEdge =
            if (Build.VERSION.SDK_INT >= 31) ParticleEdgeTreatment.Blur else ParticleEdgeTreatment.Gradient
        assertEquals("The RenderEffect blur must soften the field on API 31+", expectedEdge, edgeTreatment)

        // The pill has no expanded state; the response surface only appears with messages, which
        // this specimen never has. One pass over the idle pill.
        run {
            compose.runOnIdle { enabled.value = false; active.value = true }
            compose.waitForIdle()
            // Platform ripples run on the RenderThread, outside the Compose test clock, and would
            // otherwise differ between the two captures (hosted run 43440221939: every in-card
            // delta was darker); wait for a stable frame first.
            awaitStableCapture()
            val plainBounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
            val plain = capture()
            compose.runOnIdle { enabled.value = true }
            compose.waitForIdle()
            val decoratedBounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
            val decorated = runCatching { capture() }.getOrElse {
                // A software-canvas capture cannot execute the AGSL runtime shader on every
                // device; assert the pixels through the static painter instead. The AGSL path is
                // proven by the painter assertion above and by the real-window gate.
                compose.runOnIdle { forceFallback.value = true }
                compose.waitForIdle()
                capture()
            }
            val state = "pill"
            saveEvidence("$state-disabled", plain)
            saveEvidence("$state-enabled", decorated)
            assertEquals("Decoration must not change the card layout", plainBounds, decoratedBounds)
            assertEquals(
                "Actual card bottom stays 8dp above host bottom",
                8f * density,
                plain.height - decoratedBounds.bottom,
                2f * density
            )
            val left = (decoratedBounds.left + decoratedBounds.width * 0.1f).roundToInt()
            val right = (decoratedBounds.right - decoratedBounds.width * 0.1f).roundToInt()
            // The band just outside the card's top edge: inside the outer band, outside the card.
            val top = (decoratedBounds.top - 18f * density).roundToInt().coerceAtLeast(0)
            val bottom = (decoratedBounds.top - 1f * density).roundToInt()
            val bandChanged = changedRatio(plain, decorated, left, right, top, bottom)
            assertTrue(
                "Particle field must visibly change the band around the card: $bandChanged",
                bandChanged >= 0.05f
            )
            // The halo hugs the edge: the first 1..6dp outside the card is a continuous glow, not
            // sparse particles, on both the gradient (API 26) and blurred (API 31+) treatments.
            val haloTop = (decoratedBounds.top - 6f * density).roundToInt().coerceAtLeast(0)
            val haloChanged = changedRatio(plain, decorated, left, right, haloTop, bottom)
            assertTrue(
                "Blurred halo must be visible just outside the card edge: $haloChanged",
                haloChanged >= 0.6f
            )
            // Nothing may be painted deep inside the opaque card region.
            val innerTop = (decoratedBounds.top + 40f * density).roundToInt()
            val innerBottom = (decoratedBounds.bottom - 40f * density).roundToInt()
            val inside = if (innerBottom > innerTop) changedRatio(plain, decorated, left, right, innerTop, innerBottom) else 0f
            assertTrue("Particle field must not paint inside the card: $inside", inside < 0.01f)

            // Presence: once the field is inactive and the fade has completed, nothing of the
            // field (halo or particles) remains anywhere outside the card.
            compose.runOnIdle { active.value = false }
            compose.waitForIdle() // The auto-advancing clock runs the fade-out to completion.
            val faded = capture()
            saveEvidence("$state-faded", faded)
            val fadedBounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
            assertEquals("Fading must not change the card layout", plainBounds, fadedBounds)
            val remaining = changedRatio(plain, faded, left, right, top, bottom)
            assertTrue("Faded field must leave the band around the card untouched: $remaining", remaining < 0.01f)
        }
    }

    @Test fun actualCardStopsParticleFramesForMotionDisabledPreferenceAndLifecycle() {
        val enabled = mutableStateOf(true)
        val motion = mutableStateOf(true)
        val active = mutableStateOf(true)
        val idleTimeout = mutableStateOf<Long?>(null)
        val frames = AtomicInteger()
        var tileReady = false
        var painterKind: NeuralParticlePainterKind? = null
        lateinit var owner: TestOwner
        compose.setContent {
            owner = remember { TestOwner() }
            MaterialChatTheme(dynamicColor = false, neuralExpressive = enabled.value) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalNeuralParticleRenderControl provides NeuralParticleRenderControl(
                        motionEnabled = motion.value,
                        onFrame = { frames.incrementAndGet() },
                        onPainterResolved = { painterKind = it },
                        onTileReady = { tileReady = true }
                    )
                ) {
                    Specimen(
                        active = active.value,
                        idleTimeoutMillis = idleTimeout.value,
                        // Short fades keep the test fast even under a 10x animator scale.
                        fadeSpec = ParticleFadeSpec(fadeInMillis = 200, fadeOutMillis = 200)
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) { tileReady }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        if (painterKind != NeuralParticlePainterKind.Runtime) {
            // The static painter (below API 33 or AGSL failure) draws once and never animates.
            assertEquals("The static painter must never request particle frames", 0, frames.get())
            assertFramesStop(frames)
            // A presence fade requests a bounded run of frames and then stops; the field must not
            // keep ticking once fully faded out or fully faded back in.
            compose.runOnUiThread { active.value = false }
            assertFadeSettles(frames)
            compose.runOnUiThread { active.value = true }
            assertFadeSettles(frames)
            compose.runOnUiThread { idleTimeout.value = 500 }
            assertFadeSettles(frames)
            return
        }
        assertTrue("The live field must request frames before the gates are exercised", frames.get() > 0)
        // Presence: inactive fades out, then no frames at all (battery); active fades back in
        // and the motion loop resumes.
        compose.runOnUiThread { active.value = false }
        assertFadeSettles(frames)
        compose.runOnUiThread { active.value = true }
        assertFramesResume(frames)
        // Idle timeout: still active, but the field retires itself after the timeout and stops.
        compose.runOnUiThread { idleTimeout.value = 500 }
        compose.mainClock.advanceTimeBy(500)
        assertFadeSettles(frames)
        compose.runOnUiThread { idleTimeout.value = null }
        assertFramesResume(frames)
        // Devices with animations globally disabled or power saver on correctly emit no frames;
        // the JVM gate tests cover those branches exhaustively.
        compose.runOnUiThread { motion.value = false }
        assertFramesStop(frames)
        compose.runOnUiThread { motion.value = true; enabled.value = false }
        assertFramesStop(frames)
        compose.runOnUiThread {
            enabled.value = true
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        assertFramesStop(frames)
    }

    private fun assertFramesStop(frames: AtomicInteger) {
        compose.mainClock.advanceTimeBy(100) // Apply recomposition and cancel the previous frame loop.
        compose.waitForIdle()
        val stopped = frames.get()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals("Blocked decoration must not request continuing particle frames", stopped, frames.get())
    }

    /** Lets a 200ms fade finish under any animator scale up to 10x, then requires silence. */
    private fun assertFadeSettles(frames: AtomicInteger) {
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        assertFramesStop(frames)
    }

    private fun assertFramesResume(frames: AtomicInteger) {
        val before = frames.get()
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        assertTrue("Active decoration must request frames again", frames.get() > before)
    }

    private fun changedRatio(a: Bitmap, b: Bitmap, left: Int, right: Int, top: Int, bottom: Int): Float {
        var changed = 0
        var samples = 0
        for (y in top until bottom) for (x in left until right) {
            if (rgbDelta(a.getPixel(x, y), b.getPixel(x, y)) >= 12) changed++
            samples++
        }
        assertTrue("Sample region must not be empty", samples > 0)
        return changed.toFloat() / samples
    }

    private fun capture(): Bitmap = compose.onNodeWithTag("assistant_host")
        .captureToImage().asAndroidBitmap()

    /** Waits (real time) until two consecutive captures are identical. */
    private fun awaitStableCapture() {
        var previous = capture()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(150)
            compose.waitForIdle()
            val next = capture()
            if (next.sameAs(previous)) return
            previous = next
        }
        throw AssertionError("Assistant host never reached a stable frame")
    }

    private fun saveEvidence(name: String, bitmap: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output != null) File(output, "assistant-neural-particles")
        else File(instrumentation.targetContext.getExternalFilesDir(null), "assistant-neural-particles")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun rgbDelta(a: Int, b: Int): Int =
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
            abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
            abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}

@androidx.compose.runtime.Composable
private fun Specimen(
    forceFallback: Boolean = false,
    active: Boolean = true,
    idleTimeoutMillis: Long? = null,
    fadeSpec: ParticleFadeSpec = ParticleFadeSpec(),
) {
    // Mirrors the production AssistantOverlay host: the full-size box hosts the particle field
    // around the card rect the card reports, so the band escapes the card's own bounds.
    var cardBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var hostOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val decorationRect = cardBoundsInRoot?.translate(-hostOriginInRoot.x, -hostOriginInRoot.y)
    Box(
        Modifier.fillMaxSize().background(Color(0xFF17151C)).testTag("assistant_host")
            .onGloballyPositioned { hostOriginInRoot = it.boundsInRoot().topLeft }
            .neuralParticleField(
                capsuleRect = decorationRect,
                cornerRadius = 32.dp,
                color = Color.White,
                enabled = LocalNeuralExpressiveEnabled.current,
                capsuleInset = null,
                forceFallback = forceFallback,
                active = active,
                idleTimeoutMillis = idleTimeoutMillis,
                fadeSpec = fadeSpec
            )
    ) {
        AssistantOverlayContent(
            uiState = AssistantUiState(), voiceState = VoiceState.Idle,
            amplitudeData = AudioAmplitudeData.Empty, messages = emptyList(),
            hasMicPermission = true, hasContent = false,
            onTextChange = {}, onVoiceClick = {}, onInterrupt = {}, onStopVoice = {},
            onSendClick = {}, onOpenInApp = {}, onDismiss = {}, onNewChat = {},
            onCardBoundsChange = { cardBoundsInRoot = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
