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
import androidx.compose.material3.MaterialTheme
import com.materialchat.ui.theme.particles.ParticleFadeSpec
import com.materialchat.ui.theme.particles.ParticleFieldStyle
import com.materialchat.ui.theme.particles.neuralParticleField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.test.espresso.IdlingPolicies
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Renders the real assistant card inside the production decoration host (the full-window parent
 * that targets the card rect, paints the pill's soft-edged fill and inner glow, and draws the
 * accent dots in front) and proves the field is visible, bounded, gated, soft at the capsule
 * edge, and retired by the presence fade while the resting pill stays.
 */
@RunWith(AndroidJUnit4::class)
class AssistantNeuralParticleFieldTest {
    @get:Rule val compose = createComposeRule()

    /**
     * The first composition compiles the AGSL runtime shader and generates its tiles, which can
     * take several seconds on a loaded CI emulator; the default 5 s idling budget then fails the
     * test before any assertion runs. The wait budget is raised here; no assertion is relaxed.
     */
    @Before fun allowSlowShaderSetup() {
        IdlingPolicies.setIdlingResourceTimeout(60, TimeUnit.SECONDS)
        IdlingPolicies.setMasterPolicyTimeout(60, TimeUnit.SECONDS)
    }

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
        // this specimen never has. One pass over the idle pill: plain (decoration off: the pill
        // paints its own hard fill and shadow), resting (decoration on but inactive: only the
        // soft-edged fill), decorated (active: fill, inner glow and dots), then faded again.
        run {
            compose.runOnIdle { enabled.value = false; active.value = true }
            compose.waitForIdle()
            // Platform ripples run on the RenderThread, outside the Compose test clock, and would
            // otherwise differ between the two captures (hosted run 43440221939: every in-card
            // delta was darker); wait for a stable frame first.
            awaitStableCapture()
            val plainBounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
            val plain = capture()
            // Inactive while hidden settles the presence silently, so the resting capture has no
            // dots or glow, only the soft fill the pill keeps between turns.
            compose.runOnIdle { active.value = false }
            compose.waitForIdle()
            compose.runOnIdle { enabled.value = true }
            compose.waitForIdle()
            val rest = runCatching { capture() }.getOrElse {
                // A software-canvas capture cannot execute the AGSL runtime shader on every
                // device; assert the pixels through the static painter instead. The AGSL path is
                // proven by the painter assertion above and by the real-window gate.
                compose.runOnIdle { forceFallback.value = true }
                compose.waitForIdle()
                capture()
            }
            compose.runOnIdle { active.value = true }
            compose.waitForIdle() // The auto-advancing clock runs the fade-in to completion.
            val decoratedBounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
            val decorated = capture()
            val state = "pill"
            saveEvidence("$state-disabled", plain)
            saveEvidence("$state-resting", rest)
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
                "Decoration must visibly change the band around the card: $bandChanged",
                bandChanged >= 0.05f
            )
            // The dots and glow are the live part: the active field differs from the resting
            // pill in the band, not only from the plain one.
            val dotsChanged = changedRatio(rest, decorated, left, right, top, bottom)
            assertTrue(
                "Dots must be visible around the resting pill: $dotsChanged",
                dotsChanged >= 0.02f
            )
            // The soft edge: the first 1..5dp outside the card is the pill's own colour bleeding
            // out continuously (RenderEffect blur on API 31+, geometric feather below), present
            // even while the field rests, instead of the plain pill's hard boundary.
            // The plain pill's own shadow already darkens the pixels just outside its edge, so the
            // reliable signal is just inside: a hard pill is solid there, a soft one fades.
            val edgeTop = (decoratedBounds.top + 1f * density).roundToInt()
            val edgeBottom = (decoratedBounds.top + 5f * density).roundToInt()
            val edgeChanged = changedRatio(plain, rest, left, right, edgeTop, edgeBottom, threshold = 6)
            assertTrue(
                "Soft edge must visibly fade the pill boundary: $edgeChanged",
                edgeChanged >= 0.6f
            )
            // Nothing may be painted deep inside the opaque card region.
            val innerTop = (decoratedBounds.top + 40f * density).roundToInt()
            val innerBottom = (decoratedBounds.bottom - 40f * density).roundToInt()
            val inside = if (innerBottom > innerTop) changedRatio(rest, decorated, left, right, innerTop, innerBottom) else 0f
            assertTrue("Particle field must not paint deep inside the card: $inside", inside < 0.01f)

            // Presence: once the field is inactive and the fade has completed, nothing of the
            // field (glow or particles) remains; only the resting soft-edged pill is left.
            compose.runOnIdle { active.value = false }
            compose.waitForIdle() // The auto-advancing clock runs the fade-out to completion.
            val faded = capture()
            saveEvidence("$state-faded", faded)
            val fadedBounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
            assertEquals("Fading must not change the card layout", plainBounds, fadedBounds)
            val remaining = changedRatio(rest, faded, left, right, top, bottom)
            assertTrue("Faded field must leave only the resting pill: $remaining", remaining < 0.01f)
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
        compose.waitUntil(60_000) { tileReady }
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

    /**
     * The assistant's dots follow listening: their first appearance fades in rather than
     * snapping, the fade requests a bounded run of frames even with the static painter, and the
     * pill keeps its inner glow (resting floor) once the dots have faded out.
     */
    @Test fun firstAppearanceFadesInAndTheRestingGlowOutlivesTheDots() {
        val fadeIn = mutableStateOf(false)
        val active = mutableStateOf(true)
        val restingGlow = mutableStateOf(0f)
        val generation = mutableStateOf(0)
        val frames = AtomicInteger()
        val tiles = AtomicInteger()
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            MaterialChatTheme(themeMode = AppPreferences.ThemeMode.DARK, dynamicColor = false, neuralExpressive = true) {
                CompositionLocalProvider(
                    LocalNeuralParticleRenderControl provides NeuralParticleRenderControl(
                        onFrame = { frames.incrementAndGet() },
                        onTileReady = { tiles.incrementAndGet() }
                    )
                ) {
                    // A new generation is a fresh first appearance of the field.
                    androidx.compose.runtime.key(generation.value) {
                        Specimen(
                            // The static painter never requests motion frames on any API level,
                            // so every frame counted here belongs to a presence fade.
                            forceFallback = true,
                            active = active.value,
                            fadeSpec = ParticleFadeSpec(fadeInMillis = 200, fadeOutMillis = 200),
                            fadeInOnFirstAppearance = fadeIn.value,
                            restingGlow = restingGlow.value
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) { tiles.get() >= 1 }
        compose.waitForIdle()
        // Default: the first composition snaps to the active target, so no fade frame at all.
        compose.mainClock.autoAdvance = false
        assertFramesStop(frames)
        assertEquals("A snapped first appearance must request no frames", 0, frames.get())

        // Requested: the first appearance fades in (frames) and then settles (silence).
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { fadeIn.value = true; generation.value++ }
        compose.waitUntil(10_000) { tiles.get() >= 2 }
        compose.waitForIdle()
        assertTrue("The first appearance must fade in, not snap", frames.get() > 0)
        compose.mainClock.autoAdvance = false
        assertFadeSettles(frames)

        // Listening stops: the dots fade out and the pill keeps a resting glow inside its edge.
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { active.value = false }
        compose.waitForIdle()
        awaitStableCapture()
        val bounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
        val withoutGlow = capture()
        compose.runOnIdle { restingGlow.value = 0.6f }
        compose.waitForIdle()
        awaitStableCapture()
        val withGlow = capture()
        saveEvidence("pill-faded-no-glow", withoutGlow)
        saveEvidence("pill-faded-resting-glow", withGlow)
        val left = (bounds.left + bounds.width * 0.1f).roundToInt()
        val right = (bounds.right - bounds.width * 0.1f).roundToInt()
        val glowTop = (bounds.top + 2f * density).roundToInt()
        val glowBottom = (bounds.top + 14f * density).roundToInt()
        val glowChanged = changedRatio(withoutGlow, withGlow, left, right, glowTop, glowBottom)
        assertTrue("The resting glow must stay visible inside the pill edge after the dots fade: $glowChanged", glowChanged >= 0.1f)
        assertEquals("The resting glow must not change the card layout", bounds,
            compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = false
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

    private fun changedRatio(
        a: Bitmap, b: Bitmap, left: Int, right: Int, top: Int, bottom: Int, threshold: Int = 12,
    ): Float {
        var changed = 0
        var samples = 0
        for (y in top until bottom) for (x in left until right) {
            if (rgbDelta(a.getPixel(x, y), b.getPixel(x, y)) >= threshold) changed++
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
    fadeInOnFirstAppearance: Boolean = false,
    restingGlow: Float = 0f,
) {
    // Mirrors the production AssistantOverlay host: the full-size box hosts the particle field
    // around the pill rect the pill reports (soft-edged fill in its own colour, accent glow and
    // accent dots), so the band escapes the pill's own bounds; the pill paints no fill of its own.
    var cardBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var hostOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val decorationRect = cardBoundsInRoot?.translate(-hostOriginInRoot.x, -hostOriginInRoot.y)
    val enabled = LocalNeuralExpressiveEnabled.current
    Box(
        Modifier.fillMaxSize().background(Color(0xFF17151C)).testTag("assistant_host")
            .onGloballyPositioned { hostOriginInRoot = it.boundsInRoot().topLeft }
            .neuralParticleField(
                capsuleRect = decorationRect,
                cornerRadius = 38.dp,
                featherColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                style = ParticleFieldStyle(innerBand = 20.dp),
                color = MaterialTheme.colorScheme.primary,
                enabled = enabled,
                capsuleInset = null,
                forceFallback = forceFallback,
                active = active,
                idleTimeoutMillis = idleTimeoutMillis,
                fadeSpec = fadeSpec,
                fadeInOnFirstAppearance = fadeInOnFirstAppearance,
                restingGlow = restingGlow
            )
    ) {
        AssistantOverlayContent(
            uiState = AssistantUiState(), voiceState = VoiceState.Idle,
            amplitudeData = AudioAmplitudeData.Empty, messages = emptyList(),
            hasMicPermission = true, hasContent = false,
            onTextChange = {}, onVoiceClick = {}, onInterrupt = {}, onStopVoice = {},
            onSendClick = {}, onOpenInApp = {}, onDismiss = {}, onNewChat = {},
            onCardBoundsChange = { cardBoundsInRoot = it },
            softEdges = enabled,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
