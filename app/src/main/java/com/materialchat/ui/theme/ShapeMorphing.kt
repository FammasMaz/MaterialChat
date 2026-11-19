package com.materialchat.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape Morphing Utilities.
 *
 * M3 Expressive emphasizes shape changes on interaction states. Components should
 * subtly morph their shape when pressed, selected, or focused to provide
 * tactile, responsive feedback.
 *
 * Key principles:
 * - Round shapes become slightly LESS rounded on press for tactile compression
 * - Press morphs run on [ExpressiveMotion.Press.Shape] (M3E fast spatial, 0.6 / 800) so
 *   every pressable in the app settles alike; focus morphs (search bar, text field) keep
 *   [ExpressiveMotion.Spatial.shapeMorph]
 * - Shape changes should be subtle but noticeable
 *
 * Corner class ruling (W9, cross-surface audit W8 #1; the same text sits on
 * [ExpressiveShapes]): every helper here returns a circular [RoundedCornerShape] on purpose.
 * Pressables (buttons, FAB, icon buttons, chips) are round and press-morph on that radius;
 * containers (cards, sheets, dialogs, notices, content blocks, tiles) are static
 * [SquircleShape] tokens from [ExpressiveShapes] and never morph. [cardShape] is kept for its
 * two callers (a position-morphing carousel card and the provider card's radius dip), which
 * both resolve the animated radius into a [SquircleShape] themselves; a new card must not
 * draw the [RoundedCornerShape] this helper returns.
 *
 * [buttonShape] still defaults to [ExpressiveMotion.Spatial.shapeMorph] (0.6 / 500): its one
 * consumer (ExpressiveSnackbar) would settle ~21% sooner on the Press token, which is a feel
 * change for that owner to verify; pass `animationSpec = ExpressiveMotion.Press.Shape` to opt in.
 */
object ShapeMorphing {

    /**
     * Animated button shape that morphs on press.
     * Round buttons become slightly less rounded when pressed.
     *
     * @param isPressed Whether the button is currently pressed
     * @param defaultRadius Default corner radius
     * @param pressedRadius Corner radius when pressed (typically larger)
     * @param animationSpec Corner spring; [ExpressiveMotion.Press.Shape] is the shared press physics
     * @return Animated RoundedCornerShape
     */
    @Composable
    fun buttonShape(
        isPressed: Boolean,
        defaultRadius: Dp = 24.dp,
        pressedRadius: Dp = 12.dp,
        animationSpec: SpringSpec<Dp> = ExpressiveMotion.Spatial.shapeMorph()
    ): RoundedCornerShape {
        val radius by animateDpAsState(
            targetValue = if (isPressed) pressedRadius else defaultRadius,
            animationSpec = animationSpec,
            label = "buttonShapeMorph"
        )
        return RoundedCornerShape(radius)
    }

    /**
     * Animated FAB shape that morphs on press.
     * FABs use larger radii for the pill-like M3 Expressive look.
     *
     * @param isPressed Whether the FAB is currently pressed
     * @param isExpanded Whether the FAB is in expanded state (extended FAB)
     * @return Animated RoundedCornerShape
     */
    @Composable
    fun fabShape(
        isPressed: Boolean,
        isExpanded: Boolean = false
    ): RoundedCornerShape {
        val baseRadius = 28.dp  // Pill-like default
        val pressedRadius = 32.dp  // Even more rounded on press

        val radius by animateDpAsState(
            targetValue = if (isPressed) pressedRadius else baseRadius,
            animationSpec = ExpressiveMotion.Press.Shape,
            label = "fabShapeMorph"
        )
        return RoundedCornerShape(radius)
    }

    /**
     * Animated icon button shape.
     * Morphs between circle and squircle on press.
     *
     * @param isPressed Whether the button is pressed
     * @param size The button size (used to calculate radii)
     * @return Animated RoundedCornerShape
     */
    @Composable
    fun iconButtonShape(
        isPressed: Boolean,
        size: Dp = 48.dp
    ): RoundedCornerShape {
        val defaultRadius = size / 2  // Circle
        val pressedRadius = size / 3  // Squircle

        val radius by animateDpAsState(
            targetValue = if (isPressed) pressedRadius else defaultRadius,
            animationSpec = ExpressiveMotion.Press.Shape,
            label = "iconButtonShapeMorph"
        )
        return RoundedCornerShape(radius)
    }

    /**
     * Animated card/list item shape.
     * Becomes slightly less rounded on press (opposite of buttons).
     *
     * @param isPressed Whether the card is pressed
     * @param defaultRadius Default corner radius
     * @param pressedRadius Corner radius when pressed (typically smaller)
     * @return Animated RoundedCornerShape
     */
    @Composable
    fun cardShape(
        isPressed: Boolean,
        defaultRadius: Dp = 20.dp,
        pressedRadius: Dp = 16.dp
    ): RoundedCornerShape {
        val radius by animateDpAsState(
            targetValue = if (isPressed) pressedRadius else defaultRadius,
            animationSpec = ExpressiveMotion.Press.Shape,
            label = "cardShapeMorph"
        )
        return RoundedCornerShape(radius)
    }

    /**
     * Animated chip shape.
     * Chips become slightly less rounded on press.
     *
     * @param isPressed Whether the chip is pressed
     * @param isSelected Whether the chip is in selected state
     * @return Animated RoundedCornerShape
     */
    @Composable
    fun chipShape(
        isPressed: Boolean,
        isSelected: Boolean = false
    ): RoundedCornerShape {
        val baseRadius = if (isSelected) 12.dp else 10.dp
        val pressedRadius = if (isSelected) 8.dp else 6.dp

        val radius by animateDpAsState(
            targetValue = if (isPressed) pressedRadius else baseRadius,
            animationSpec = ExpressiveMotion.Press.Shape,
            label = "chipShapeMorph"
        )
        return RoundedCornerShape(radius)
    }

    /**
     * Animated search bar shape.
     * Maintains pill shape but subtly adjusts on focus.
     *
     * @param isFocused Whether the search bar is focused
     * @return Animated RoundedCornerShape
     */
    @Composable
    fun searchBarShape(
        isFocused: Boolean
    ): RoundedCornerShape {
        val radius by animateDpAsState(
            targetValue = if (isFocused) 24.dp else 28.dp,
            animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
            label = "searchBarShapeMorph"
        )
        return RoundedCornerShape(radius)
    }

    /**
     * Animated text field shape.
     * Becomes more defined on focus.
     *
     * @param isFocused Whether the field is focused
     * @return Animated RoundedCornerShape
     */
    @Composable
    fun textFieldShape(
        isFocused: Boolean
    ): RoundedCornerShape {
        val radius by animateDpAsState(
            targetValue = if (isFocused) 28.dp else 32.dp,
            animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
            label = "textFieldShapeMorph"
        )
        return RoundedCornerShape(radius)
    }
}

/**
 * Predefined shape configurations for M3 Expressive components.
 */
object ExpressiveShapeDefaults {

    /**
     * Button shape defaults
     */
    object Button {
        val defaultRadius = 24.dp
        val pressedRadius = 12.dp
        val largeDefaultRadius = 28.dp
        val largePressedRadius = 16.dp
    }

    /**
     * FAB shape defaults - pill-like for M3 Expressive
     */
    object Fab {
        val defaultRadius = 28.dp
        val pressedRadius = 32.dp
        val smallDefaultRadius = 16.dp
        val smallPressedRadius = 20.dp
        val largeDefaultRadius = 32.dp
        val largePressedRadius = 36.dp
    }

    /**
     * Card/list item shape defaults
     */
    object Card {
        val defaultRadius = 20.dp
        val pressedRadius = 16.dp
        val elevatedRadius = 24.dp
        val elevatedPressedRadius = 20.dp
    }

    /**
     * Message bubble shape defaults
     */
    object MessageBubble {
        val cornerRadius = 28.dp
        val tailCornerRadius = 8.dp
        val groupedCornerRadius = 8.dp
    }

    /**
     * Input field shape defaults
     */
    object Input {
        val defaultRadius = 32.dp
        val focusedRadius = 28.dp
    }
}
