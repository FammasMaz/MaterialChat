package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.R
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.particles.rememberParticleMotionGate

/** Resting corner of the scroll FAB (M3E small FAB: 16dp). */
internal val ScrollFabRestingCorner: Dp = 16.dp

/** Corner the FAB arrives with and leaves to: a full circle, so appearance is a shape morph. */
internal val ScrollFabArrivalCorner: Dp = 24.dp

/** Corner while pressed. */
internal val ScrollFabPressedCorner: Dp = 10.dp

/** Badge text for an unread count; capped so the badge never widens past the FAB. */
internal fun scrollFabBadgeLabel(unreadCount: Int): String? = when {
    unreadCount <= 0 -> null
    unreadCount > 99 -> "99+"
    else -> unreadCount.toString()
}

/**
 * M3 Expressive scroll-to-bottom affordance: a [SmallExtendedFloatingActionButton] that
 * morphs its shape on appear/disappear (circle -> 16dp small-FAB corners on the spatial
 * shape-morph spring, back to a circle on exit), tightens to 10dp on press, and extends
 * with a "New" label when content arrived while the user was scrolled away. Messages that
 * arrived while the user was away are counted in an unread [Badge].
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
    hapticsEnabled: Boolean = true,
    unreadCount: Int = 0
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
            animationSpec = ExpressiveMotion.Spatial.collapse(),
            targetOffsetY = { it }
        ) + scaleOut(
            animationSpec = ExpressiveMotion.Spatial.collapse(),
            targetScale = 0.8f
        ) + fadeOut(
            animationSpec = ExpressiveMotion.Effects.alpha()
        )
    ) {
        // Gentle bobbing on the arrow — suggests "scroll down". Continuous motion, so it
        // honours the decoration motion gate (power saver, animator scale 0, stopped host):
        // the arrow then rests at its centre instead of running an infinite transition.
        val bobAmplitude = with(LocalDensity.current) { 2.dp.toPx() }
        val motionGate = rememberParticleMotionGate()
        val bobOffset = if (motionGate.motionAllowed) {
            val infiniteTransition = rememberInfiniteTransition(label = "scroll_hint")
            val animated by infiniteTransition.animateFloat(
                initialValue = -bobAmplitude,
                targetValue = bobAmplitude,
                // Shared bob token (Durations.Bob half period, reversing); identical spec.
                animationSpec = ExpressiveMotion.Spatial.bob(),
                label = "bobOffset"
            )
            animated
        } else {
            0f
        }

        // Shape morph on appearance: the FAB lands as a circle and settles into its small-FAB
        // corners on the spatial spring (the exit transition scales it back down).
        val arrival = remember { Animatable(ScrollFabArrivalCorner, Dp.VectorConverter) }
        LaunchedEffect(Unit) {
            arrival.animateTo(ScrollFabRestingCorner, ExpressiveMotion.Spatial.shapeMorph())
        }

        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val pressCorner by animateDpAsState(
            targetValue = if (pressed) ScrollFabPressedCorner else ScrollFabRestingCorner,
            animationSpec = ExpressiveMotion.Press.Shape,
            label = "scrollFabCorner"
        )
        val corner = if (pressed) pressCorner else maxOf(pressCorner, arrival.value)
        val badgeLabel = scrollFabBadgeLabel(unreadCount)

        BadgedBox(
            badge = {
                if (badgeLabel != null) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.semantics {
                            contentDescription = "$badgeLabel unread messages"
                        }
                    ) {
                        Text(text = badgeLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        ) {
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
}
