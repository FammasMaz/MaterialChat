package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.AiModel
import com.materialchat.ui.components.ExpressiveFilledIconButton
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import com.materialchat.ui.screens.chat.ContextWindowUsage
import com.materialchat.ui.theme.CustomShapes
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.MaterialChatExpressiveTitleFontFamily
import com.materialchat.ui.theme.particles.rememberParticleMotionGate
import com.materialchat.ui.util.ModelNameParser
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The chat masthead.
 *
 * The conversation title is the subject of the screen, so when the header is expanded it sits on
 * its own line at headline scale under the control row, with the model and the conversation's
 * status (temporary, archived, context pressure, branches) as a row of chips beneath it. When the
 * user scrolls toward newer messages the masthead folds: the title springs up into the control
 * row at title scale and the chips fold away, leaving a compact bar. Scrolling back toward older
 * messages, or tapping the compact title, unfolds it again. Collapse thresholds live in
 * [ChatHeaderCollapseMachine]; the chip set in [resolveChatHeaderBadges].
 *
 * While a reply streams, a small pulse strip (the assistant's waveform idea at header scale) and
 * the working indicator's wording appear under the chips, or beside the compact title when folded.
 *
 * All motion uses the app's M3 Expressive springs: disclosure springs for the fold, the title
 * reveal spring for a freshly named chat, effects springs for colour.
 *
 * @param headerScrollState Scroll-driven fold state from [rememberChatHeaderScrollState]; null
 *   keeps the header permanently expanded.
 * @param scrollBehavior Kept for call-site compatibility only. The container tone is fixed
 *   (see [resolveChatHeaderTone]): it never lifts when content scrolls under the bar, because
 *   the bar is cut with 24dp bottom corners over a scaffold of the same colour and any tonal
 *   difference reveals those corners as an artifact while the list scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    title: String,
    icon: String? = null,
    isEphemeral: Boolean = false,
    isArchived: Boolean = false,
    modelName: String,
    providerName: String,
    isStreaming: Boolean,
    availableModels: List<AiModel>,
    isLoadingModels: Boolean,
    beautifulModelNamesEnabled: Boolean = false,
    hasBranches: Boolean = false,
    contextWindowUsage: ContextWindowUsage = ContextWindowUsage(),
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    onModelSelected: (AiModel) -> Unit,
    onLoadModels: () -> Unit,
    onMindMapClick: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    headerScrollState: ChatHeaderScrollState? = null
) {
    val haptics = rememberHapticFeedback()
    val scheme = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }
    val isCollapsed = headerScrollState?.isCollapsed ?: false

    // Title reveal when the model names a fresh conversation.
    var previousTitle by remember { mutableStateOf(title) }
    val revealProgress = remember { Animatable(1f) }
    LaunchedEffect(title) {
        if (shouldRevealTitle(previous = previousTitle, next = title)) {
            revealProgress.snapTo(0f)
            revealProgress.animateTo(
                targetValue = 1f,
                // Slow spatial token: the title is a screen-level element being revealed.
                animationSpec = ExpressiveMotion.Spatial.screenSlide()
            )
        } else {
            revealProgress.snapTo(1f)
        }
        previousTitle = title
    }
    val shownTitle = revealedTitle(title, revealProgress.value)
    val titleAlpha = (revealProgress.value * 5f).coerceIn(0f, 1f)
    // Typography emphasis across the fold: the expressive display face eases from Bold on the
    // masthead to SemiBold in the compact row (and back) on the spatial spring. Upright only.
    val titleWeight by animateIntAsState(
        targetValue = if (isCollapsed) 600 else 700,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "chatTitleWeight"
    )
    val titleFontWeight = FontWeight(titleWeight)

    // One tone in every scroll and fold state: the same colour as the scaffold behind the bar,
    // so neither a colour shift nor the bar's bottom corners show while the list scrolls.
    val containerColor by animateColorAsState(
        targetValue = when (
            resolveChatHeaderTone(
                isEphemeral = isEphemeral,
                contentScrolledUnder = (scrollBehavior?.state?.contentOffset ?: 0f) < -1f,
                isCollapsed = isCollapsed
            )
        ) {
            ChatHeaderTone.Ephemeral -> scheme.tertiaryContainer
            ChatHeaderTone.Standard -> scheme.surfaceContainer
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "chatHeaderContainer"
    )
    val contentColor = if (isEphemeral) scheme.onTertiaryContainer else scheme.onSurface
    val mutedColor = if (isEphemeral) scheme.onTertiaryContainer.copy(alpha = 0.78f) else scheme.onSurfaceVariant
    val badges = resolveChatHeaderBadges(
        isEphemeral = isEphemeral,
        isArchived = isArchived,
        hasBranches = hasBranches,
        contextLevel = contextWindowUsage.level
    )
    val streamingLabel = chatHeaderStreamingLabel(isStreaming)

    Surface(
        shape = CustomShapes.TopAppBar,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
        ) {
            // Control row: back, compact title (folded only), context ring, overflow.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveFilledIconButton(
                    onClick = { haptics.perform(HapticPattern.CLICK); onNavigateBack() },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    containerColor = scheme.surfaceContainerLow,
                    contentColor = contentColor
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCollapsed,
                        enter = fadeIn(ExpressiveMotion.Effects.reveal()) +
                            slideInVertically(ExpressiveMotion.Spatial.expand<IntOffset>()) { it / 2 },
                        exit = fadeOut(ExpressiveMotion.Effects.reveal()) +
                            slideOutVertically(ExpressiveMotion.Spatial.collapse<IntOffset>()) { it / 2 }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = {
                                    haptics.perform(HapticPattern.CLICK)
                                    headerScrollState?.expand()
                                })
                                .semantics { contentDescription = "$title. Tap to expand header" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = isStreaming) {
                                ChatHeaderPulse(
                                    color = scheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            if (icon != null) {
                                Text(text = icon, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = shownTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = MaterialChatExpressiveTitleFontFamily,
                                    fontWeight = titleFontWeight
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(titleAlpha)
                            )
                        }
                    }
                }
                ContextWindowIndicator(
                    usage = contextWindowUsage,
                    modifier = Modifier.padding(end = 2.dp)
                )
                Box {
                    ExpressiveFilledIconButton(
                        onClick = { haptics.perform(HapticPattern.CLICK); showMenu = true },
                        icon = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        containerColor = scheme.surfaceContainerLow,
                        contentColor = if (isEphemeral) scheme.onTertiaryContainer else scheme.onSurfaceVariant
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = CustomShapes.Dropdown
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export") },
                            onClick = {
                                haptics.perform(HapticPattern.CLICK)
                                showMenu = false
                                onExportClick()
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null)
                            }
                        )
                        if (hasBranches) {
                            DropdownMenuItem(
                                text = { Text("Mind Map") },
                                onClick = {
                                    haptics.perform(HapticPattern.CLICK)
                                    showMenu = false
                                    onMindMapClick()
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.AccountTree, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }

            // Masthead: headline title, chip row, streaming presence. Folds on scroll.
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = expandVertically(
                    animationSpec = ExpressiveMotion.Disclosure.Expand,
                    expandFrom = Alignment.Top
                ) + fadeIn(ExpressiveMotion.Disclosure.FadeIn),
                exit = shrinkVertically(
                    animationSpec = ExpressiveMotion.Disclosure.Shrink,
                    shrinkTowards = Alignment.Top
                ) + fadeOut(ExpressiveMotion.Disclosure.FadeOut)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        if (icon != null) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .alpha(titleAlpha)
                            )
                        }
                        Text(
                            text = shownTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = MaterialChatExpressiveTitleFontFamily,
                                fontWeight = titleFontWeight
                            ),
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .alpha(titleAlpha)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (beautifulModelNamesEnabled) {
                            val parsedModel = remember(modelName) { ModelNameParser.parse(modelName) }
                            BeautifulModelBadges(
                                parsedModel = parsedModel,
                                isStreaming = isStreaming,
                                availableModels = availableModels,
                                isLoadingModels = isLoadingModels,
                                onModelSelected = onModelSelected,
                                onLoadModels = onLoadModels
                            )
                        } else {
                            val brand = remember(modelName) { resolveModelBrand(modelName) }
                            if (brand != null) {
                                ModelBrandBadge(brand = brand)
                            }
                            ModelPickerDropdown(
                                currentModel = modelName,
                                availableModels = availableModels,
                                isLoadingModels = isLoadingModels,
                                isStreaming = isStreaming,
                                onModelSelected = onModelSelected,
                                onLoadModels = onLoadModels
                            )
                        }
                        badges.forEach { badge ->
                            HeaderStatusChip(
                                badge = badge,
                                contextWindowUsage = contextWindowUsage,
                                onClick = if (badge == ChatHeaderBadge.Branched) {
                                    { haptics.perform(HapticPattern.CLICK); onMindMapClick() }
                                } else null
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = streamingLabel != null,
                        enter = expandVertically(
                            animationSpec = ExpressiveMotion.Disclosure.Expand,
                            expandFrom = Alignment.Top
                        ) + fadeIn(ExpressiveMotion.Disclosure.FadeIn),
                        exit = shrinkVertically(
                            animationSpec = ExpressiveMotion.Disclosure.Shrink,
                            shrinkTowards = Alignment.Top
                        ) + fadeOut(ExpressiveMotion.Disclosure.FadeOut)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .semantics { contentDescription = streamingLabel ?: "" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatHeaderPulse(color = scheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = streamingLabel ?: CHAT_WORKING_STATUS,
                                style = MaterialTheme.typography.labelLarge,
                                color = mutedColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Five bars breathing on a shared phase: the assistant's waveform vocabulary reduced to a header
 * glyph. Only composed while a reply streams, so the infinite transition never runs idle.
 */
@Composable
internal fun ChatHeaderPulse(
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    height: Dp = 14.dp
) {
    // Continuous motion honours the decoration motion gate: with motion denied the bars hold
    // one mid-amplitude pose so the streaming presence still shows, without a frame loop.
    val motionGate = rememberParticleMotionGate()
    val phase = if (motionGate.motionAllowed) {
        val transition = rememberInfiniteTransition(label = "chatHeaderPulse")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "chatHeaderPulsePhase"
        )
        animated
    } else {
        0f
    }
    val totalWidth = barWidth * barCount + barSpacing * (barCount - 1)
    Canvas(modifier = modifier.width(totalWidth).height(height)) {
        val w = barWidth.toPx()
        val s = barSpacing.toPx()
        val maxH = size.height
        val minH = maxH * 0.3f
        for (i in 0 until barCount) {
            val amplitude = 0.5f + 0.5f * sin(phase + i * 0.9f)
            val h = minH + (maxH - minH) * amplitude
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (w + s), (maxH - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f)
            )
        }
    }
}

@Composable
private fun HeaderStatusChip(
    badge: ChatHeaderBadge,
    contextWindowUsage: ContextWindowUsage,
    onClick: (() -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    val percent = contextWindowUsage.fractionUsed?.let { (it * 100f).roundToInt() }
    val (iconVector, label, container, content) = when (badge) {
        ChatHeaderBadge.Temporary -> ChipLook(
            Icons.Outlined.Timer, "Temporary", scheme.surfaceContainerHigh, scheme.onSurfaceVariant
        )
        ChatHeaderBadge.Archived -> ChipLook(
            Icons.Outlined.Archive, "Archived", scheme.secondaryContainer, scheme.onSecondaryContainer
        )
        ChatHeaderBadge.ContextHigh -> ChipLook(
            Icons.Outlined.DataUsage,
            if (percent != null) "Context $percent%" else "Context high",
            scheme.tertiaryContainer, scheme.onTertiaryContainer
        )
        ChatHeaderBadge.ContextCritical -> ChipLook(
            Icons.Outlined.DataUsage,
            if (percent != null) "Context $percent%" else "Context full",
            scheme.errorContainer, scheme.onErrorContainer
        )
        ChatHeaderBadge.Branched -> ChipLook(
            Icons.Outlined.AccountTree, "Branched", scheme.primaryContainer, scheme.onPrimaryContainer
        )
    }
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = iconVector, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
        }
    }
    // Expressive chip shapes: a state that needs attention (context full) squares off to a
    // 10dp "selected" shape so it is not read as one more neutral pill; the tappable Branched
    // chip morphs to the same shape while pressed, on the spatial spring.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val restingCorner = if (badge == ChatHeaderBadge.ContextCritical) 10.dp else 14.dp
    val corner by animateDpAsState(
        targetValue = if (isPressed) 8.dp else restingCorner,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "headerChipCorner"
    )
    val chipShape = RoundedCornerShape(corner)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = chipShape,
            color = container,
            contentColor = content,
            interactionSource = interactionSource,
            modifier = Modifier.semantics { contentDescription = "$label. Open mind map" }
        ) { body() }
    } else {
        Surface(shape = chipShape, color = container, contentColor = content) { body() }
    }
}

private data class ChipLook(
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color
)

private data class ModelBrand(
    val name: String,
    val symbol: String
)

private fun resolveModelBrand(modelName: String): ModelBrand? {
    if (modelName.isBlank()) return null
    val normalized = modelName.lowercase()
    return when {
        "claude" in normalized -> ModelBrand("Anthropic", "A")
        "gpt" in normalized || "openai" in normalized -> ModelBrand("OpenAI", "O")
        "gemini" in normalized -> ModelBrand("Google", "G")
        "mistral" in normalized || "mixtral" in normalized -> ModelBrand("Mistral", "M")
        "llama" in normalized || "meta" in normalized -> ModelBrand("Meta", "M")
        "deepseek" in normalized -> ModelBrand("DeepSeek", "D")
        "qwen" in normalized -> ModelBrand("Alibaba", "A")
        "cohere" in normalized || "command" in normalized -> ModelBrand("Cohere", "C")
        "perplexity" in normalized -> ModelBrand("Perplexity", "P")
        "grok" in normalized || "xai" in normalized -> ModelBrand("xAI", "x")
        else -> null
    }
}

@Composable
private fun ModelBrandBadge(
    brand: ModelBrand,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = brand.symbol,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
