package com.materialchat.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.materialchat.data.local.preferences.AppPreferences

/**
 * Named Material Expressive control shapes used across the app.
 */
enum class ExpressiveShapeToken {
    Cookie,
    CookieSoft,
    Clover,
    Flower,
    Puffy,
    SoftBurst
}

fun AppPreferences.ComponentButtonShape.toExpressiveShapeToken(
    fallback: ExpressiveShapeToken
): ExpressiveShapeToken = when (this) {
    AppPreferences.ComponentButtonShape.SYSTEM -> fallback
    AppPreferences.ComponentButtonShape.COOKIE -> ExpressiveShapeToken.Cookie
    AppPreferences.ComponentButtonShape.COOKIE_SOFT -> ExpressiveShapeToken.CookieSoft
    AppPreferences.ComponentButtonShape.CLOVER -> ExpressiveShapeToken.Clover
    AppPreferences.ComponentButtonShape.FLOWER -> ExpressiveShapeToken.Flower
    AppPreferences.ComponentButtonShape.PUFFY -> ExpressiveShapeToken.Puffy
    AppPreferences.ComponentButtonShape.SOFT_BURST -> ExpressiveShapeToken.SoftBurst
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun expressiveControlShape(
    token: ExpressiveShapeToken,
    pressed: Boolean,
    startAngle: Int = 0,
    style: AppPreferences.ControlShapeStyle = LocalControlShapeStyle.current
): Shape {
    return when (style) {
        AppPreferences.ControlShapeStyle.CLASSIC -> {
            val radius by animateDpAsState(
                targetValue = if (pressed) 12.dp else 24.dp,
                animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
                label = "classicControlShape"
            )
            RoundedCornerShape(radius)
        }
        AppPreferences.ControlShapeStyle.BALANCED -> {
            val radius by animateDpAsState(
                targetValue = when {
                    pressed -> 13.dp
                    token == ExpressiveShapeToken.SoftBurst -> 20.dp
                    else -> 18.dp
                },
                animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
                label = "balancedControlShape"
            )
            RoundedCornerShape(radius)
        }
        AppPreferences.ControlShapeStyle.EXPRESSIVE -> {
            val resting = token.roundedPolygon()
            val pressedShape = MaterialShapes.Square
            val progress by animateFloatAsState(
                targetValue = if (pressed) 1f else 0f,
                animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
                label = "expressiveControlMorph"
            )
            rememberMaterialShapeMorph(
                start = resting,
                end = pressedShape,
                progress = progress,
                startAngle = startAngle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ExpressiveShapeToken.roundedPolygon(): RoundedPolygon {
    return when (this) {
        ExpressiveShapeToken.Cookie -> MaterialShapes.Cookie4Sided
        ExpressiveShapeToken.CookieSoft -> MaterialShapes.Cookie6Sided
        ExpressiveShapeToken.Clover -> MaterialShapes.Clover4Leaf
        ExpressiveShapeToken.Flower -> MaterialShapes.Flower
        ExpressiveShapeToken.Puffy -> MaterialShapes.Puffy
        ExpressiveShapeToken.SoftBurst -> MaterialShapes.SoftBurst
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberMaterialShapeMorph(
    start: RoundedPolygon,
    end: RoundedPolygon,
    progress: Float,
    startAngle: Int
): Shape {
    val morph = remember(start, end) { Morph(start, end) }
    return remember(morph, progress, startAngle) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val path = Path().apply {
                    addPath(morph.toPath(progress = progress, startAngle = startAngle))
                    transform(Matrix().apply { scale(size.width, size.height) })
                    translate(size.center - getBounds().center)
                }
                return Outline.Generic(path)
            }
        }
    }
}
