package com.materialchat.ui.screens.conversations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveSizes
import com.materialchat.ui.theme.MaterialChatMotion
import com.materialchat.ui.theme.particles.rememberParticleMotionGate
import kotlinx.coroutines.delay

/** Which tonal container tints the hero shape of the empty-state cluster. */
enum class EmptyStateAccent {
    Primary,
    Secondary
}

/**
 * The single call to action shown under the empty-state copy.
 *
 * @property label Button label.
 * @property onClick Invoked when the button is tapped.
 */
data class EmptyStateAction(
    val label: String,
    val onClick: () -> Unit
)

/**
 * M3 Expressive empty state for the home screen: a cluster of overlapping
 * Material shapes in tonal container colours, an emphasized headline, one line
 * of supporting copy and one shape-morphing primary action.
 *
 * @param headline Short emphasized title.
 * @param supportingText One line explaining what to do next.
 * @param icon Glyph drawn on the hero shape.
 * @param accent Container role for the hero shape; the rest of the cluster stays neutral.
 * @param action Optional primary action; omit when there is nothing to do here.
 * @param modifier Modifier for the whole empty state.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeEmptyState(
    headline: String,
    supportingText: String,
    icon: ImageVector,
    accent: EmptyStateAccent,
    action: EmptyStateAction?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStateShapeCluster(icon = icon, accent = accent)

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        val haptics = rememberHapticFeedback()
        if (action != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    haptics.perform(HapticPattern.CLICK)
                    action.onClick()
                },
                shapes = ButtonDefaults.shapes()
            ) {
                Text(text = action.label)
            }
        }
    }
}

/**
 * Three overlapping Material shapes: a neutral soft burst drifting slowly
 * behind, a neutral clover tucked low, and the accent-coloured hero cookie
 * carrying the icon. Each shape springs in with a short stagger.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyStateShapeCluster(
    icon: ImageVector,
    accent: EmptyStateAccent
) {
    val scheme = MaterialTheme.colorScheme
    val heroColor = when (accent) {
        EmptyStateAccent.Primary -> scheme.primaryContainer
        EmptyStateAccent.Secondary -> scheme.secondaryContainer
    }
    val onHeroColor = when (accent) {
        EmptyStateAccent.Primary -> scheme.onPrimaryContainer
        EmptyStateAccent.Secondary -> scheme.onSecondaryContainer
    }
    val backdropColor = scheme.surfaceContainerHighest
    val echoColor = scheme.surfaceContainerHigh

    val burstShape = MaterialShapes.SoftBurst.toShape()
    val cloverShape = MaterialShapes.Clover4Leaf.toShape()
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()

    val heroPop = rememberClusterEntrance(delayMillis = 0L)
    val backdropPop = rememberClusterEntrance(delayMillis = 90L)
    val echoPop = rememberClusterEntrance(delayMillis = 180L)

    // The slow drift is the only continuous animation on this screen, so it obeys the same
    // host gate as every other decoration: no frames while the window is stopped, the device
    // is in power saver, or animations are switched off.
    val gate = rememberParticleMotionGate()
    val backdropSpin = if (gate.motionAllowed) {
        val drift = rememberInfiniteTransition(label = "emptyClusterDrift")
        val spin by drift.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = ExpressiveMotion.Spatial.decorativeSpin(MaterialChatMotion.Durations.BackdropSpin),
            label = "backdropSpin"
        )
        spin
    } else {
        0f
    }

    Box(modifier = Modifier.size(width = 200.dp, height = 164.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-4).dp)
                .size(108.dp)
                .graphicsLayer {
                    val pop = backdropPop.value
                    rotationZ = backdropSpin
                    scaleX = pop
                    scaleY = pop
                    alpha = pop.coerceIn(0f, 1f)
                }
                .background(color = backdropColor, shape = burstShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 2.dp, y = 8.dp)
                .size(68.dp)
                .graphicsLayer {
                    val pop = echoPop.value
                    rotationZ = 18f
                    scaleX = pop
                    scaleY = pop
                    alpha = pop.coerceIn(0f, 1f)
                }
                .background(color = echoColor, shape = cloverShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-8).dp, y = 4.dp)
                .size(124.dp)
                .graphicsLayer {
                    val pop = heroPop.value
                    scaleX = pop
                    scaleY = pop
                    alpha = pop.coerceIn(0f, 1f)
                }
                .background(color = heroColor, shape = cookieShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onHeroColor,
                modifier = Modifier.size(ExpressiveSizes.CompactTarget)
            )
        }
    }
}

@Composable
private fun rememberClusterEntrance(delayMillis: Long): Animatable<Float, AnimationVector1D> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(anim) {
        delay(delayMillis)
        anim.animateTo(1f, ExpressiveMotion.Spatial.playful())
    }
    return anim
}
