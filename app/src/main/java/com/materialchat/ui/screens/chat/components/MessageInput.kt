package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PsychologyAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import com.materialchat.ui.navigation.LocalAnimatedVisibilityScope
import com.materialchat.ui.navigation.LocalSharedTransitionScope
import com.materialchat.ui.navigation.SHARED_ELEMENT_FAB_TO_INPUT
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.MaterialChatMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Message input bar component for the chat screen.
 *
 * Features:
 * - Multi-line text input with rounded corners
 * - Animated send/stop button based on streaming state
 * - Keyboard action support (Enter to send)
 * - Disabled state during streaming
 * - Material 3 Expressive styling with spring animations
 * - Haptic feedback on button interactions
 * - Image attachment support with preview
 *
 * @param inputText Current text in the input field
 * @param isStreaming Whether a message is currently streaming
 * @param canSend Whether the send button should be enabled
 * @param pendingAttachments List of pending image attachments
 * @param onInputChange Callback when input text changes
 * @param onSend Callback when send button is clicked
 * @param onCancel Callback when stop button is clicked (during streaming)
 * @param onAttachImage Callback when the attach image button is clicked
 * @param onRemoveAttachment Callback when an attachment is removed
 * @param modifier Modifier for the input bar container
 * @param hapticsEnabled Whether haptic feedback is enabled
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MessageInput(
    inputText: String,
    isStreaming: Boolean,
    canSend: Boolean,
    pendingAttachments: List<Attachment> = emptyList(),
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAttachImage: () -> Unit = {},
    onRemoveAttachment: (Attachment) -> Unit = {},
    reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH,
    onReasoningEffortChange: (ReasoningEffort) -> Unit = {},
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    shouldAutoFocus: Boolean = false
) {
    val haptics = rememberHapticFeedback()
    val textScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus after shared element animation completes for new chats
    // Delay allows the FAB-to-input morph animation to finish smoothly
    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            // Wait for shared element animation to complete
            // Spring animation with dampingRatio=0.65, stiffness=340 settles in ~400ms
            delay(420L)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(inputText) {
        if (textScrollState.maxValue > 0) {
            textScrollState.scrollTo(textScrollState.maxValue)
        }
    }

    // M3 Expressive: Transparent container, edge-to-edge with nav bar padding
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Attachment preview row (shown when there are pending attachments)
        AnimatedVisibility(
            visible = pendingAttachments.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            AttachmentPreviewRow(
                attachments = pendingAttachments,
                onRemoveAttachment = onRemoveAttachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // M3 Expressive: Row with separate circular buttons + pill input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Attach button - M3 Expressive tertiary color for complementary accent
            Surface(
                onClick = {
                    haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                    onAttachImage()
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (!isStreaming) 
                    MaterialTheme.colorScheme.tertiaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                enabled = !isStreaming
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = if (!isStreaming)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Text input pill - shared element for FAB morph
            val sharedTransitionScope = LocalSharedTransitionScope.current
            val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
            
            val sharedContentState = sharedTransitionScope?.let {
                with(it) {
                    rememberSharedContentState(key = SHARED_ELEMENT_FAB_TO_INPUT)
                }
            }
            
            val basePillModifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
            
            val pillModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && sharedContentState != null) {
                with(sharedTransitionScope) {
                    basePillModifier.sharedElement(
                        state = sharedContentState,
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            spring(
                                dampingRatio = 0.65f,
                                stiffness = 340f
                            )
                        }
                    )
                }
            } else {
                basePillModifier
            }
            
            Surface(
                modifier = pillModifier,
                shape = CustomShapes.MessageInputContainer,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    enabled = !isStreaming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 160.dp)
                        .verticalScroll(textScrollState)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    minLines = 1,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                onSend()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Type a message...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            ReasoningEffortSelector(
                reasoningEffort = reasoningEffort,
                enabled = !isStreaming,
                onReasoningEffortChange = onReasoningEffortChange,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            )

            // Send/Stop button - M3 Expressive with primary colors
            // Uses secondaryContainer for send (complementary to tertiary attach button)
            // Uses error container for stop (streaming cancel action)
            Surface(
                onClick = {
                    if (isStreaming) {
                        haptics.perform(HapticPattern.CONFIRM, hapticsEnabled)
                        onCancel()
                    } else if (canSend) {
                        haptics.perform(HapticPattern.CLICK, hapticsEnabled)
                        onSend()
                    }
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = when {
                    isStreaming -> MaterialTheme.colorScheme.errorContainer
                    canSend -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                enabled = canSend || isStreaming
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isStreaming) "Stop" else "Send",
                        tint = when {
                            isStreaming -> MaterialTheme.colorScheme.onErrorContainer
                            canSend -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningEffortSelector(
    reasoningEffort: ReasoningEffort,
    enabled: Boolean,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val options = remember { ReasoningEffort.values().toList() }
    val itemAnimations = remember { options.map { Animatable(0f) } }
    val scope = rememberCoroutineScope()
    var selectedEffort by remember { mutableStateOf(reasoningEffort) }

    LaunchedEffect(reasoningEffort) {
        selectedEffort = reasoningEffort
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            menuVisible = true
            itemAnimations.forEachIndexed { index, animatable ->
                launch {
                    delay((options.size - 1 - index) * 45L)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = ExpressiveMotion.Spatial.playful()
                    )
                }
            }
        } else {
            itemAnimations.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 25L)
                    animatable.animateTo(
                        targetValue = 0f,
                        animationSpec = ExpressiveMotion.Spatial.playful()
                    )
                }
            }
            delay(MaterialChatMotion.Durations.Short.toLong())
            menuVisible = false
        }
    }

    val menuScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.92f,
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "reasoningMenuScale"
    )

    val menuAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = ExpressiveMotion.Effects.alpha(),
        label = "reasoningMenuAlpha"
    )

    val menuOffset by animateFloatAsState(
        targetValue = if (expanded) 0f else 12f,
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "reasoningMenuOffset"
    )

    val menuOffsetPx = with(density) { menuOffset.dp.toPx() }
    val itemOffsetPx = with(density) { 18.dp.toPx() }
    val cascadeStepPx = with(density) { 6.dp.toPx() }
    val menuAnchorOffset = with(density) { (48.dp + 8.dp).roundToPx() }

    val isActive = selectedEffort != ReasoningEffort.NONE
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonSize = 48.dp
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded || isPressed) buttonSize / 2 else 8.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "reasoningButtonCorner"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "reasoningButtonScale"
    )
    // M3 Expressive: Use secondary container for reasoning effort (distinct from tertiary attach and primary send)
    val baseContainerColor = if (isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val containerColor by animateColorAsState(
        targetValue = if (isPressed) {
            baseContainerColor.copy(alpha = 0.85f)
        } else {
            baseContainerColor
        },
        animationSpec = ExpressiveMotion.SpringSpecs.ColorTransition,
        label = "reasoningButtonColor"
    )
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = modifier) {
        Surface(
            onClick = { if (enabled) expanded = !expanded },
            enabled = enabled,
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            contentColor = contentColor,
            interactionSource = interactionSource,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PsychologyAlt,
                    contentDescription = "Reasoning options"
                )
            }
        }

        if (menuVisible) {
            Popup(
                alignment = Alignment.BottomEnd,
                offset = IntOffset(0, -menuAnchorOffset),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .wrapContentWidth(Alignment.End)
                        .graphicsLayer {
                        scaleX = menuScale
                        scaleY = menuScale
                        alpha = menuAlpha
                        translationX = menuOffsetPx
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                    }
                ) {
                    options.forEachIndexed { index, option ->
                        val progress = itemAnimations[index].value
                        val cascadeOffsetPx = itemOffsetPx +
                            (options.size - index - 1) * cascadeStepPx
                        ReasoningOptionPill(
                            option = option,
                            selected = option == selectedEffort,
                            onClick = {
                                selectedEffort = option
                                onReasoningEffortChange(option)
                                scope.launch {
                                    delay(MaterialChatMotion.Durations.Short.toLong() / 2)
                                    expanded = false
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .graphicsLayer {
                                    alpha = progress
                                    scaleX = 0.9f + (0.1f * progress)
                                    scaleY = 0.9f + (0.1f * progress)
                                    translationX = (1f - progress) * cascadeOffsetPx
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningOptionPill(
    option: ReasoningEffort,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // M3 Expressive: Use tertiary container for unselected to create better contrast
    // and primary container for selected state
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        animationSpec = ExpressiveMotion.SpringSpecs.ColorTransition,
        label = "reasoningPillColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        animationSpec = ExpressiveMotion.SpringSpecs.ColorTransition,
        label = "reasoningPillContent"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (selected) 3.dp else 2.dp,
        shadowElevation = if (selected) 4.dp else 2.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .animateContentSize(animationSpec = ExpressiveMotion.Spatial.playful())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = option.displayName,
                style = MaterialTheme.typography.labelLarge
            )
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(
                    animationSpec = ExpressiveMotion.Spatial.scale(),
                    initialScale = 0.6f
                ) + fadeIn(
                    animationSpec = ExpressiveMotion.Effects.alpha()
                ),
                exit = scaleOut(
                    animationSpec = ExpressiveMotion.Spatial.scale(),
                    targetScale = 0.6f
                ) + fadeOut(
                    animationSpec = ExpressiveMotion.Effects.alpha()
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )
            }
        }
    }
}

/**
 * Row of attachment previews with remove buttons.
 */
@Composable
private fun AttachmentPreviewRow(
    attachments: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentPreviewItem(
                attachment = attachment,
                onRemove = { onRemoveAttachment(attachment) }
            )
        }
    }
}

/**
 * Single attachment preview with remove button.
 */
@Composable
private fun AttachmentPreviewItem(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier.size(72.dp)
    ) {
        // Image thumbnail
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(attachment.uri)
                .crossfade(true)
                .build(),
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .align(Alignment.BottomStart)
        )

        // Remove button - 48dp touch target with 24dp visual
        Box(
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-12).dp)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Button to attach an image.
 */
@Composable
private fun AttachButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AddPhotoAlternate,
            contentDescription = "Attach image"
        )
    }
}

/**
 * The text input field for typing messages.
 */
@Composable
private fun MessageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = "Type a message...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        enabled = enabled,
        maxLines = 4,
        shape = CustomShapes.MessageInputContainer,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Send
        ),
        keyboardActions = KeyboardActions(
            onSend = {
                if (canSend) {
                    onSend()
                }
            }
        )
    )
}

/**
 * Animated action button that switches between send and stop icons.
 */
@Composable
private fun ActionButton(
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    // Stop button (shown during streaming)
    AnimatedVisibility(
        visible = isStreaming,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        StopButton(onClick = onCancel)
    }

    // Send button (shown when not streaming)
    AnimatedVisibility(
        visible = !isStreaming,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        SendButton(
            enabled = canSend,
            onClick = onSend
        )
    }
}

/**
 * Send button with animated scale on enabled state change.
 */
@Composable
private fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "send_button_scale"
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send message"
        )
    }
}

/**
 * Stop button to cancel streaming.
 */
@Composable
private fun StopButton(
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = "Stop generating"
        )
    }
}
