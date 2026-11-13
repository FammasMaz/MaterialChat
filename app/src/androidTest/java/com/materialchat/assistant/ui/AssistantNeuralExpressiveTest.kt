package com.materialchat.assistant.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import com.materialchat.assistant.voice.AudioAmplitudeData
import com.materialchat.assistant.voice.VoiceState
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.LocalNeuralExpressiveRenderControl
import com.materialchat.ui.theme.MaterialChatTheme
import com.materialchat.ui.theme.NeuralExpressiveRenderControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

/** Real decorated assistant content; not a replacement Surface specimen or privileged session. */
@RunWith(AndroidJUnit4::class)
class AssistantNeuralExpressiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun outwardDecorationDoesNotChangeCompactOrExpandedCardBounds() {
        val enabled = mutableStateOf(false)
        var density = 1f
        var hardwareAccelerated = false
        compose.setContent {
            density = LocalDensity.current.density
            hardwareAccelerated = LocalView.current.isHardwareAccelerated
            MaterialChatTheme(
                themeMode = AppPreferences.ThemeMode.DARK,
                dynamicColor = false,
                neuralExpressive = enabled.value
            ) {
                CompositionLocalProvider(
                    LocalNeuralExpressiveRenderControl provides NeuralExpressiveRenderControl(timeSeconds = 4f)
                ) { Specimen() }
            }
        }
        compose.waitForIdle()
        assertTrue("Acceptance must use a hardware-accelerated host", hardwareAccelerated)
        for (expanded in listOf(false, true)) {
            if (expanded) {
                compose.onNodeWithContentDescription("Expand assistant").performClick()
                compose.waitForIdle()
            }
            compose.runOnIdle { enabled.value = false }
            compose.waitForIdle()
            val plainBounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
            val plain = capture()
            compose.runOnIdle { enabled.value = true }
            compose.waitForIdle()
            val decoratedBounds = compose.onNodeWithTag("assistant_sheet").fetchSemanticsNode().boundsInRoot
            val decorated = capture()
            val state = if (expanded) "expanded" else "compact"
            saveEvidence("$state-disabled", plain)
            saveEvidence("$state-enabled", decorated)
            assertEquals("Decoration must not change the card layout", plainBounds, decoratedBounds)
            assertEquals("Actual card bottom stays 8dp above host bottom", 8f * density,
                plain.height - decoratedBounds.bottom, 2f * density)
            // The top-center band lies OUTSIDE the opaque card and excludes corner geometry.
            val left = (decoratedBounds.left + decoratedBounds.width * 0.2f).roundToInt()
            val right = (decoratedBounds.right - decoratedBounds.width * 0.2f).roundToInt()
            val top = (decoratedBounds.top - 26f * density).roundToInt().coerceAtLeast(0)
            val bottom = (decoratedBounds.top - 2f * density).roundToInt()
            var changed = 0
            var samples = 0
            for (y in top until bottom) for (x in left until right) {
                if (rgbDelta(plain.getPixel(x, y), decorated.getPixel(x, y)) >= 12) changed++
                samples++
            }
            assertTrue("Outward halo/particles must visibly change >=12% of band pixels: $changed/$samples",
                samples > 0 && changed.toFloat() / samples >= 0.12f)
        }
    }

    @Test fun actualCardStopsParticleFramesForReducedMotionDisabledAndHiddenLifecycle() {
        val enabled = mutableStateOf(true)
        val motion = mutableStateOf(true)
        val frames = AtomicInteger()
        lateinit var owner: TestOwner
        compose.mainClock.autoAdvance = false
        compose.setContent {
            owner = androidx.compose.runtime.remember { TestOwner() }
            MaterialChatTheme(dynamicColor = false, neuralExpressive = enabled.value) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalNeuralExpressiveRenderControl provides NeuralExpressiveRenderControl(
                        motionEnabled = motion.value, onFrame = { frames.incrementAndGet() }
                    )
                ) { Specimen() }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        // Devices with animations globally disabled or power saver on correctly emit no frames.
        // The pure motion-gate test separately verifies the allowed state and all blocked states.
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

    private fun capture(): Bitmap = compose.onNodeWithTag("assistant_host")
        .captureToImage().asAndroidBitmap()

    private fun saveEvidence(name: String, bitmap: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output != null) File(output, "assistant-neural")
            else File(instrumentation.targetContext.getExternalFilesDir(null), "assistant-neural")
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
private fun Specimen() {
    Box(Modifier.fillMaxSize().background(Color(0xFF17151C)).testTag("assistant_host")) {
        AssistantOverlayContent(
            uiState = AssistantUiState(), voiceState = VoiceState.Idle,
            amplitudeData = AudioAmplitudeData.Empty, messages = emptyList(),
            hasMicPermission = true, hasContent = false,
            onTextChange = {}, onVoiceClick = {}, onInterrupt = {}, onStopVoice = {},
            onSendClick = {}, onOpenInApp = {}, onDismiss = {}, onNewChat = {},
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
