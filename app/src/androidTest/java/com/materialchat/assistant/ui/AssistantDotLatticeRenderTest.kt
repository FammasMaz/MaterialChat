package com.materialchat.assistant.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * Proves the dot lattice actually reaches the screen, from pixels, on a controlled background.
 *
 * The live gate used to measure this on the running assistant window, where the launcher's
 * wallpaper and dock icons sit behind a translucent window: hosted run 43657360948 measured the
 * wallpaper (control band 0.2792 specks) rather than the lattice (0.0051) and failed for the
 * wrong reason. The texture is therefore asserted here, over a solid background, and the live
 * gate keeps the draw-path marker plus the geometry of the real window.
 */
@RunWith(AndroidJUnit4::class)
class AssistantDotLatticeRenderTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun latticeDrawsVisibleDotsInsideItsBandAndNoneAboveIt() {
        compose.setContent { Specimen() }
        compose.waitForIdle()
        val bitmap = capture()

        val bandTop = bandTop(bitmap)
        val inBand = speckRatio(bitmap, 0, bandTop, bitmap.width, bitmap.height)
        val aboveBand = speckRatio(bitmap, 0, 0, bitmap.width, bandTop)
        val concentration = phaseConcentration(bitmap, 0, bandTop, bitmap.width, bitmap.height)

        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        saveEvidence("lattice", bitmap)

        // 1.2dp dots on a 7dp pitch cover ~2.3% of the band at full strength, and the band fades
        // upward, so a quarter of that is the floor a real render must clear.
        assertTrue("The lattice band must carry dot texture (measured $inBand)", inBand >= 0.004)
        assertTrue(
            "No dots may be drawn above the band (measured $aboveBand)",
            aboveBand <= inBand / 4f
        )
        // The dots are a grid, not noise: their x positions must share one phase modulo the pitch.
        assertTrue(
            "The dots must sit on the lattice grid (concentration $concentration, density $density)",
            concentration >= 0.75f
        )
    }

    @Composable
    private fun Specimen() {
        // A solid, dark host: the assistant window's own background, without a wallpaper behind it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101014))
                .assistantDotLattice(visible = true)
                .testTag("lattice_host")
        )
    }

    private fun capture(): Bitmap =
        compose.onNodeWithTag("lattice_host").captureToImage().asAndroidBitmap()

    private fun luma(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000f
    }

    /** Fraction of pixels clearly brighter than their own row's median: the dots over the scrim. */
    private fun speckRatio(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int, delta: Float = 10f): Float {
        var bright = 0
        var total = 0
        val row = FloatArray(x1 - x0)
        for (y in y0 until y1) {
            for (x in x0 until x1) row[x - x0] = luma(bitmap.getPixel(x, y))
            val median = row.sortedArray()[row.size / 2]
            for (v in row) {
                if (v >= median + delta) bright++
                total++
            }
        }
        return if (total == 0) 0f else bright.toFloat() / total
    }

    private fun bandTop(bitmap: Bitmap): Int =
        (bitmap.height * (1f - AssistantLatticeStyle.BAND_FRACTION)).toInt()

    /**
     * How tightly the bright specks share one x phase modulo the 7dp pitch. A grid scores near 1;
     * noise and text score near 0.
     */
    private fun phaseConcentration(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        val pitch = AssistantLatticeStyle.Pitch.value * density
        if (pitch <= 0f) return 0f
        var sx = 0.0
        var sy = 0.0
        var weight = 0.0
        val row = FloatArray(x1 - x0)
        for (y in y0 until y1) {
            for (x in x0 until x1) row[x - x0] = luma(bitmap.getPixel(x, y))
            val median = row.sortedArray()[row.size / 2]
            for (i in 1 until row.size - 1) {
                val v = row[i]
                if (v < row[i - 1] || v < row[i + 1]) continue
                val amp = v - median
                if (amp < 10f) continue
                val angle = 2.0 * PI * i / pitch
                sx += amp * cos(angle)
                sy += amp * sin(angle)
                weight += amp
            }
        }
        if (weight == 0.0) return 0f
        return hypot(sx, sy).toFloat() / weight.toFloat()
    }

    private fun saveEvidence(name: String, bitmap: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "test-evidence/assistant")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val top = bandTop(bitmap)
        val band = speckRatio(bitmap, 0, top, bitmap.width, bitmap.height)
        val above = speckRatio(bitmap, 0, 0, bitmap.width, top)
        val concentration = phaseConcentration(bitmap, 0, top, bitmap.width, bitmap.height)
        File(dir, "$name.txt").writeText(
            "size=${bitmap.width}x${bitmap.height} density=$density bandTop=$top " +
                "band=$band above=$above concentration=$concentration\n"
        )
    }
}
