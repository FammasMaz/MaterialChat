package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.materialchat.R
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * M3 Expressive scroll-to-bottom affordance: a [SmallExtendedFloatingActionButton] that
 * morphs its corners on press (16dp resting → 10dp pressed, spatial shape-morph spring)
 * and extends with a "New" label when content arrived while the user was scrolled away.
 *
 * - primaryContainer tone, zero drop shadow (tonal only, no shadow-before-icon artifacts)
 * - slide + fade + scale entrance; critically damped exit
 * - subtle bobbing arrow to suggest "scroll down"
 * - haptic feedback on tap
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScrollToBottomFab(
    visible: Boolean,
    hasNewContent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    val haptics = rememberHapticFeedback()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = ExpressiveMotion.Spatial.playful(),
            initialOffsetY = { it }
        ) + scaleIn(
            animationSpec = ExpressiveMotion.Spatial.playful(),
            initialScale = 0.7f
        ) + fadeIn(
            animationSpec = ExpressiveMotion.Effects.alpha()
        ),
        exit = slideOutVertically(
            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f),
            targetOffsetY = { it }
        ) + scaleOut(
            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f),
            targetScale = 0.8f
        ) + fadeOut(
            animationSpec = ExpressiveMotion.Effects.alpha()
        )
    ) {
        // Gentle bobbing on the arrow — suggests "scroll down"
        val bobAmplitude = with(LocalDensity.current) { 2.dp.toPx() }
        val infiniteTransition = rememberInfiniteTransition(label = "scroll_hint")
        val bobOffset by infiniteTransition.animateFloat(
            initialValue = -bobAmplitude,
            targetValue = bobAmplitude,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bobOffset"
        )

        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val corner by animateDpAsState(
            targetValue = if (pressed) 10.dp else 16.dp,
            animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
            label = "scrollFabCorner"
        )

        SmallExtendedFloatingActionButton(
            text = {
                Text(
                    text = stringResource(R.string.chat_new_messages),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.chat_scroll_to_bottom),
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { translationY = bobOffset }
                )
            },
            onClick = {
                haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                onClick()
            },
            expanded = hasNewContent,
            shape = RoundedCornerShape(corner),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp
            ),
            interactionSource = interactionSource
        )
    }
}
