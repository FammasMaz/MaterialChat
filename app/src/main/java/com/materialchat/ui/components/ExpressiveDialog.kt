package com.materialchat.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.materialchat.ui.theme.ExpressiveMotion
import kotlinx.coroutines.launch

/** Scale a dialog card starts from before it springs to full size. */
internal const val DialogEntranceScale = 0.92f

/**
 * M3 Expressive dialog entrance: the card scales from [DialogEntranceScale] to 1 on
 * the spatial menu-open spring while it fades in on the effects spring. Apply to an
 * `AlertDialog`'s `modifier` so only the card animates; the scrim is owned by the
 * dialog window. Exit stays with the window because `AlertDialog` removes the
 * composition the moment it is dismissed.
 *
 * The scale stays inside the card's bounds, so it renders the same on API 26 where
 * `graphicsLayer` clips drawing outside its layer.
 */
@Composable
fun Modifier.dialogEntrance(): Modifier {
    val scale = remember { Animatable(DialogEntranceScale) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, ExpressiveMotion.Spatial.menuOpen()) }
        launch { alpha.animateTo(1f, ExpressiveMotion.Effects.reveal()) }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
        transformOrigin = TransformOrigin.Center
    }
}
