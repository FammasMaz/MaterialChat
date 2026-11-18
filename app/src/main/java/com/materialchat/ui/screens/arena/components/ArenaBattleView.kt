package com.materialchat.ui.screens.arena.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.StreamingState
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.screens.chat.components.ShimmerSkeletonLines
import com.materialchat.ui.screens.arena.ContenderUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Blind N-model battle pager: one full-screen card per contender, swipeable.
 *
 * While the blind phase is active each card shows only a codename. Cards are
 * shuffled per battle by the ViewModel so position never identifies a model;
 * answers are buffered and released together once every contender finishes.
 */
@Composable
fun ArenaBattleView(
    contenders: List<ContenderUi>,
    revealed: Boolean,
    realNamesBySlot: Map<Int, String>,
    modifier: Modifier = Modifier
) {
    if (contenders.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { contenders.size })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val contender = contenders.getOrNull(page) ?: return@HorizontalPager
            BattleCard(
                contender = contender,
                revealed = revealed,
                realName = realNamesBySlot[contender.slot],
                accentContainer = if (page % 2 == 0) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                accentOnContainer = if (page % 2 == 0) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
        }

        // Page indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(contenders.size) { index ->
                val selected = pagerState.currentPage == index
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (selected) 20.dp else 8.dp, height = 8.dp)
                ) {}
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Swipe for other responses →",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (pagerState.pageCount > 1) 1f else 0f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * One anonymous response card: codename header (scrambling while running),
 * full markdown body once released, shared shimmer skeleton while buffering.
 */
@Composable
private fun BattleCard(
    contender: ContenderUi,
    revealed: Boolean,
    realName: String?,
    accentContainer: androidx.compose.ui.graphics.Color,
    accentOnContainer: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = accentContainer,
        contentColor = accentOnContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: codename during blind phase, real name after reveal.
                AnimatedContent(
                    targetState = revealed,
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideInHorizontally(tween(350)) { it / 3 })
                            .togetherWith(fadeOut(tween(200)))
                    },
                    label = "arenaReveal"
                ) { isRevealed ->
                    Column {
                        Text(
                            text = if (isRevealed) realName ?: contender.codename
                                   else scrambleWhileStreaming(contender, isRevealed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRevealed && realName != null) {
                            Text(
                                text = "was ${contender.codename}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentOnContainer.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                when {
                    // Buffering phase: shared loading animation on EVERY card —
                    // no partial text, no cadence leak, until release.
                    !revealed && contender.streamState is StreamingState.Starting -> {
                        ShimmerSkeletonLines(
                            color = accentOnContainer,
                            lines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    contender.streamState is StreamingState.Error -> Text(
                        text = contender.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> MarkdownText(
                        markdown = contender.content.ifEmpty { "…" },
                        textColor = accentOnContainer,
                        isStreaming = contender.streamState is StreamingState.Streaming,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Codename display that shuffles through the name pool while its model is
 * still running — the "mixup" effect. Freezes once finished or revealed.
 */
@Composable
private fun scrambleWhileStreaming(
    contender: ContenderUi,
    revealed: Boolean
): String {
    var display by remember(contender.slot) {
        mutableStateOf(contender.codename)
    }
    val animating = !revealed &&
            (contender.streamState is StreamingState.Streaming ||
                    contender.streamState is StreamingState.Starting)

    LaunchedEffect(contender.slot, animating) {
        if (!animating) {
            display = contender.codename
            return@LaunchedEffect
        }
        val pool = listOf("Aurora", "Borealis", "Comet", "Drift", "Ember", "Flux", "Gale", "Halcyon", "Ion", "Juno")
        // Random start offset so the scramble phase of one card never reveals
        // its slot index through a deterministic name sequence.
        var i = Random.nextInt(pool.size)
        while (isActive) {
            i = (i + 1) % pool.size
            display = pool[i]
            delay(700L)
        }
    }
    return display
}
