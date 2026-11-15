package com.materialchat.assistant.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.ui.theme.particles.LocalNeuralParticleRenderControl
import com.materialchat.ui.theme.particles.NeuralParticleRenderControl
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Proves the dot lattice actually reaches the screen, from pixels, on a controlled background,
 * and that it moves the way the Gemini Live reference does: the dots twinkle in place (the lit
 * set changes between two clock times) while the grid itself never translates (both captures
 * share one x phase modulo the pitch), and the pill's opaque interior carries no dots.
 *
 * The live gate used to measure this on the running assistant window, where the launcher's
 * wallpaper and dock icons sit behind a translucent window: hosted run 43657360948 measured the
 * wallpaper (control band 0.2792 specks) rather than the lattice (0.0051) and failed for the
 * wrong reason. The texture is therefore asserted here, over a solid background, and the live
 * gate keeps the draw-path markers plus the geometry of the real window.
 */
@RunWith(AndroidJUnit4::class)
class AssistantDotLatticeRenderTest {

    @get:Rule val compose = createComposeRule()

    private var time by mutableFloatStateOf(0f)

    @Test
    fun latticeTwinklesInsideItsBandOverThePillEdgeAndNowhereElse() {
        time = 0f
        compose.setContent { Specimen() }
        compose.waitForIdle()
        val first = Band(capture())
        time = 2f
        compose.waitForIdle()
        val second = Band(capture())
        saveEvidence("lattice-t0", first)
        saveEvidence("lattice-t2", second)

        for (band in listOf(first, second)) {
            // 1.2dp dots on a 7dp pitch cover ~2.3% of the band when every dot is lit; the
            // reference (and this lattice) keeps about 40% of them lit at any instant, and the
            // band fades upward, so a quarter of that is the floor a real render must clear.
            assertTrue("The lattice band must carry dot texture (measured ${band.inBand})", band.inBand >= 0.0015f)
            assertTrue(
                "No dots may be drawn above the band (measured ${band.aboveBand})",
                band.aboveBand <= band.inBand / 4f
            )
            // The dots are a grid, not noise: their x positions share one phase modulo the pitch.
            assertTrue(
                "The dots must sit on the lattice grid (concentration ${band.concentration}, density ${band.density})",
                band.concentration >= 0.75f
            )
            // The pill's opaque interior (inset by the inner band from the pill rect) is clear;
            // the ring just outside the pill is where the lattice is liveliest.
            assertTrue(
                "No dots may be drawn inside the pill's interior (measured ${band.insidePill})",
                band.insidePill <= band.inBand / 8f
            )
            assertTrue(
                "The dots must float over the pill's edge (ring ${band.pillRing} vs band ${band.inBand})",
                band.pillRing >= band.inBand
            )
        }
        // Motion: two seconds apart, most dots have switched state, in place. The grid phase
        // above already proves nothing translated.
        assertTrue(
            "The lattice must twinkle between clock times (changed ${first.changedAgainst(second)})",
            first.changedAgainst(second) >= 0.3f
        )
    }

    @Composable
    private fun Specimen() {
        // A solid, dark host: the assistant window's own background, without a wallpaper behind it.
        // A fixed, small host: the geometry is then exact and the pixel passes stay cheap, so
        // this test cannot slow the classes that run after it on a loaded CI emulator.
        val density = LocalDensity.current
        val pill = with(density) {
            Rect(
                left = PillLeftDp.dp.toPx(), top = PillTopDp.dp.toPx(),
                right = PillRightDp.dp.toPx(), bottom = PillBottomDp.dp.toPx()
            )
        }
        CompositionLocalProvider(
            // A pinned clock: the lattice draws the twinkle at exactly this time and runs no
            // frame loop, so each capture is deterministic.
            LocalNeuralParticleRenderControl provides NeuralParticleRenderControl(timeSeconds = time)
        ) {
            Box(
                modifier = Modifier
                    .size(width = HostWidthDp.dp, height = HostHeightDp.dp)
                    .background(Color(0xFF101014))
                    .assistantDotLattice(visible = true, pillRect = pill, pillCornerRadius = 38.dp)
                    .testTag("lattice_host")
            )
        }
    }

    private fun capture(): Bitmap =
        compose.onNodeWithTag("lattice_host").captureToImage().asAndroidBitmap()

    /** One capture's band statistics, computed from a single luma pass. */
    private inner class Band(val bitmap: Bitmap) {
        val density: Float = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        val bandTop: Int = (bitmap.height * (1f - AssistantLatticeStyle.BAND_FRACTION)).toInt()
        private val width = bitmap.width
        private val height = bitmap.height
        private val luma = FloatArray(width * height)
        /** Pixels clearly brighter than their own row's median: the dots over the scrim. */
        private val speck = BooleanArray(width * height)

        init {
            val row = IntArray(width)
            val sorted = FloatArray(width)
            for (y in 0 until height) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1)
                for (x in 0 until width) {
                    val v = luma(row[x])
                    luma[y * width + x] = v
                    sorted[x] = v
                }
                sorted.sort()
                val median = sorted[width / 2]
                for (x in 0 until width) speck[y * width + x] = luma[y * width + x] >= median + 10f
            }
        }

        private fun ratio(x0: Int, y0: Int, x1: Int, y1: Int): Float {
            var bright = 0
            var total = 0
            for (y in y0.coerceAtLeast(0) until y1.coerceAtMost(height)) {
                for (x in x0.coerceAtLeast(0) until x1.coerceAtMost(width)) {
                    if (speck[y * width + x]) bright++
                    total++
                }
            }
            return if (total == 0) 0f else bright.toFloat() / total
        }

        private fun px(dp: Float): Int = (dp * density).toInt()

        val inBand: Float get() = ratio(0, bandTop, width, height)
        val aboveBand: Float get() = ratio(0, 0, width, bandTop)

        /** The pill rect inset by the inner band: the lattice treats it as the pill's opaque interior. */
        val insidePill: Float
            get() {
                val inset = AssistantLatticeStyle.PillInnerBand.value + 1f
                return ratio(px(PillLeftDp + inset), px(PillTopDp + inset), px(PillRightDp - inset), px(PillBottomDp - inset))
            }

        /** The strip just below the pill's bottom edge, inside the near band, where the twinkle peaks. */
        val pillRing: Float
            get() = ratio(px(PillLeftDp), px(PillBottomDp), px(PillRightDp), px(PillBottomDp + AssistantLatticeStyle.PillNearBand.value))

        /**
         * How tightly the bright specks share one x phase modulo the 7dp pitch. A grid scores
         * near 1; noise and text score near 0.
         */
        val concentration: Float
            get() {
                val pitch = AssistantLatticeStyle.Pitch.value * density
                if (pitch <= 0f) return 0f
                var sx = 0.0
                var sy = 0.0
                var weight = 0.0
                val sorted = FloatArray(width)
                for (y in bandTop until height) {
                    System.arraycopy(luma, y * width, sorted, 0, width)
                    sorted.sort()
                    val median = sorted[width / 2]
                    for (x in 1 until width - 1) {
                        val v = luma[y * width + x]
                        if (v < luma[y * width + x - 1] || v < luma[y * width + x + 1]) continue
                        val amp = v - median
                        if (amp < 10f) continue
                        val angle = 2.0 * PI * x / pitch
                        sx += amp * cos(angle)
                        sy += amp * sin(angle)
                        weight += amp
                    }
                }
                if (weight == 0.0) return 0f
                return hypot(sx, sy).toFloat() / weight.toFloat()
            }

        /** Fraction of the union of both captures' band specks that is lit in only one of them. */
        fun changedAgainst(other: Band): Float {
            var union = 0
            var changed = 0
            for (y in bandTop until height) {
                for (x in 0 until width) {
                    val a = speck[y * width + x]
                    val b = other.speck[y * width + x]
                    if (a || b) union++
                    if (a != b) changed++
                }
            }
            return if (union == 0) 0f else changed.toFloat() / union
        }

        private fun luma(pixel: Int): Float {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000f
        }
    }

    private fun saveEvidence(name: String, band: Band) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "test-evidence/assistant")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { band.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "$name.txt").writeText(
            "size=${band.bitmap.width}x${band.bitmap.height} density=${band.density} bandTop=${band.bandTop} " +
                "band=${band.inBand} above=${band.aboveBand} concentration=${band.concentration} " +
                "insidePill=${band.insidePill} pillRing=${band.pillRing}\n"
        )
    }

    private companion object {
        const val HostWidthDp = 360f
        const val HostHeightDp = 640f
        // A pill of the assistant's proportions, bottom-centred inside the band like the real one.
        const val PillLeftDp = 30f
        const val PillRightDp = 330f
        const val PillBottomDp = HostHeightDp - 40f
        const val PillTopDp = PillBottomDp - 110f
    }
}
