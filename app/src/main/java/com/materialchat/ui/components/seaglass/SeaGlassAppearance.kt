package com.materialchat.ui.components.seaglass

import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.seaglass.compose.rememberSeaGlassStageState
import dev.seaglass.compose.LocalSeaGlassStageState
import dev.seaglass.compose.seaGlassBackdrop
import dev.seaglass.compose.seaGlass
import dev.seaglass.compose.toSeaGlassMaterial
import dev.seaglass.compose.LocalSeaGlassTiltState
import dev.seaglass.compose.rememberSeaGlassTiltState
import dev.seaglass.core.SeaGlassCornerAdaptationMode

/** Optical appearance is independent of the source of the Material 3 color scheme. */
val LocalSeaGlassEnabled = staticCompositionLocalOf { false }

@Composable
fun SeaGlassAppearance(enabled: Boolean, content: @Composable () -> Unit) {
    val stage = rememberSeaGlassStageState()
    val neutralTilt = remember { dev.seaglass.compose.SeaGlassTiltState() }
    val tilt = if (enabled) rememberSeaGlassTiltState() else null
    // Keep the foreground at the same composition identity when appearance changes.
    // Moving content between an if/else and a StageHost would recreate the NavHost.
    CompositionLocalProvider(
        LocalSeaGlassEnabled provides enabled,
        LocalSeaGlassStageState provides if (enabled) stage else null,
        LocalSeaGlassTiltState provides (tilt ?: neutralTilt)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (enabled) {
                tilt?.ambientDriftEnabled = false
                Box(Modifier.fillMaxSize().seaGlassBackdrop(stage)) { SeaGlassAmbientBackdrop() }
            }
            androidx.compose.runtime.key("appearance-foreground") { content() }
        }
    }
}

@Composable
private fun BoxScope.SeaGlassAmbientBackdrop() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
        drawRect(scheme.background)
        val radius = size.minDimension * 0.9f
        listOf(
            Triple(scheme.primaryContainer, Offset(size.width * 0.05f, size.height * 0.15f), 0.85f),
            Triple(scheme.tertiaryContainer, Offset(size.width * 0.95f, size.height * 0.52f), 0.75f),
            Triple(scheme.secondaryContainer, Offset(size.width * 0.25f, size.height * 0.95f), 0.8f)
        ).forEach { (color, center, alpha) ->
            drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius))
        }
    }
}

/** Uses the upstream optical renderer and the exact Material component contour. */
@Composable
fun Modifier.materialGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainer
): Modifier {
    if (!LocalSeaGlassEnabled.current || shape == RectangleShape) return this
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val scheme = MaterialTheme.colorScheme
    val resolvedTint = if (tint.alpha == 0f) scheme.surfaceContainer else tint
    val material = remember(resolvedTint) {
        resolvedTint.toSeaGlassMaterial().copy(
            // Preserve dark M3 tones instead of forcing every surface into a light pastel.
            tintColorArgb = if (resolvedTint.luminance() < 0.2f) resolvedTint.toArgb()
                else resolvedTint.toSeaGlassMaterial().tintColorArgb,
            transparency = 0.72f,
            thicknessDp = 10f,
            chromaticDispersion = 0.12f,
            coarseness = 0.38f,
            frostRadiusDp = 22f
        )
    }
    return seaGlass(
        material = material,
        cornerAdaptationMode = SeaGlassCornerAdaptationMode.FIXED,
        customContourPathBuilder = { width, height, path ->
            path.reset()
            when (val outline = shape.createOutline(Size(width, height), direction, density)) {
                is Outline.Rectangle -> path.addRect(0f, 0f, width, height, Path.Direction.CW)
                is Outline.Rounded -> {
                    val r = outline.roundRect
                    path.addRoundRect(r.left, r.top, r.right, r.bottom, floatArrayOf(
                        r.topLeftCornerRadius.x, r.topLeftCornerRadius.y,
                        r.topRightCornerRadius.x, r.topRightCornerRadius.y,
                        r.bottomRightCornerRadius.x, r.bottomRightCornerRadius.y,
                        r.bottomLeftCornerRadius.x, r.bottomLeftCornerRadius.y
                    ), Path.Direction.CW)
                }
                is Outline.Generic -> path.set(outline.path.asAndroidPath())
            }
        }
    )
}

/** The page stays transparent to its captured ambient backdrop; only controls are glass. */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    androidx.compose.material3.Scaffold(
        modifier, topBar, bottomBar, snackbarHost, floatingActionButton,
        floatingActionButtonPosition,
        if (LocalSeaGlassEnabled.current) Color.Transparent else containerColor,
        contentColor, contentWindowInsets, content
    )
}
