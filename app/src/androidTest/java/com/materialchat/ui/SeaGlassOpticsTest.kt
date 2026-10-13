package com.materialchat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.materialchat.ui.components.seaglass.LocalSeaGlassEnabled
import com.materialchat.ui.components.seaglass.Surface
import dev.seaglass.compose.SeaGlassStageHost
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Backdrop transmission test through the real production Surface adapter + upstream renderer. */
@RunWith(AndroidJUnit4::class)
class SeaGlassOpticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun surfaceTransmitsBackdropAndHasNonFlatFrostedRim() {
        val background = mutableStateOf(Color(0xFFEFCACA))
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                CompositionLocalProvider(LocalSeaGlassEnabled provides true) {
                    SeaGlassStageHost(
                        background = { Canvas(Modifier.size(320.dp)) { drawRect(background.value) } },
                        foreground = {
                            Surface(
                                modifier = Modifier.size(240.dp, 160.dp).testTag("glass-specimen"),
                                shape = RoundedCornerShape(28.dp),
                                color = Color(0xFFA8E2CF)
                            ) { Box(Modifier.size(240.dp, 160.dp)) { Text("Readable glass") } }
                        }
                    )
                }
            }
        }
        compose.waitForIdle()
        val red = compose.onNodeWithTag("glass-specimen").captureToImage().asAndroidBitmap()
        compose.runOnIdle { background.value = Color(0xFFCAD8EF) }
        compose.waitForIdle()
        val blue = compose.onNodeWithTag("glass-specimen").captureToImage().asAndroidBitmap()
        var transmission = 0.0
        var count = 0
        // Interior excludes text/top and bevel. Same tint, geometry, theme; only backdrop changes.
        for (y in blue.height / 3 until blue.height * 2 / 3 step 4) {
            for (x in blue.width / 3 until blue.width * 2 / 3 step 4) {
                val a = red.getPixel(x, y)
                val b = blue.getPixel(x, y)
                transmission += kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
                count++
            }
        }
        assertTrue("Backdrop change must transmit >=8 RGB levels averaged through interior, actual=${transmission / count}",
            transmission / count >= 8.0)
        val folder = java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null), "seaglass-evidence").apply { mkdirs() }
        java.io.File(folder, "optics-blue.png").outputStream().use { blue.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        java.io.File(folder, "optics-red.png").outputStream().use { red.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val x = blue.width / 2
        val center = blue.getPixel(x, blue.height / 2)
        // Probe the whole 9dp meniscus band, not an arbitrary single pixel in its outer lip.
        val band = (9 * androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density).toInt()
        val rimDelta = (2..band).maxOf { inset ->
            val rim = blue.getPixel(x, blue.height - inset)
            kotlin.math.abs(android.graphics.Color.red(rim) - android.graphics.Color.red(center)) +
                kotlin.math.abs(android.graphics.Color.green(rim) - android.graphics.Color.green(center)) +
                kotlin.math.abs(android.graphics.Color.blue(rim) - android.graphics.Color.blue(center))
        }
        assertTrue("Frosted rim band must differ >=15 summed RGB levels from flat interior; actual=$rimDelta", rimDelta >= 15)
    }
}
