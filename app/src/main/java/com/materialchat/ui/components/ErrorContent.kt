package com.materialchat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.ExpressiveSizes
import com.materialchat.ui.theme.CopyRules
import com.materialchat.ui.theme.ExpressiveSpacing

/**
 * Copy every error surface shares: aliases of the theme's [CopyRules] constants (W7 part 6).
 * The retry verb is "Try again" app-wide (W5 orchestrator ruling); `ChatErrorCopy.TRY_AGAIN`
 * and `AssistantCopy.AFTER_ERROR` are pinned equal to it in `CopyRulesTest`.
 */
object ErrorContentCopy {
    /** The one retry verb, sentence case, no trailing period. */
    const val TRY_AGAIN = CopyRules.TRY_AGAIN

    /** Default headline when a screen cannot name what failed. */
    const val GENERIC_HEADLINE = CopyRules.GENERIC_HEADLINE
}

/**
 * The one full-screen / full-card error state (W4 audit F7): an error tile, a bold
 * `headlineSmall` headline, a `bodyMedium` supporting line and a single primary
 * [ErrorContentCopy.TRY_AGAIN] action, with an optional secondary text action after it ("Go
 * back"). The primary action always comes first; the five previous layouts of the same two
 * buttons disagreed on the order.
 *
 * Drawn on [ExpressiveShapes.Card] over `surfaceContainer`, with 24dp inner padding, the
 * same card, type pair and padding as [ExpressiveEmptyState] and the chat empty state, so an
 * errored screen reads like an empty one with a different glyph. Colours are the role pairs
 * the theme gates in `componentContrastPairs` ("error content glyph/headline/supporting").
 *
 * @param title Sentence-case headline (what failed).
 * @param message One or two sentences with full stops (why, and what the action does).
 * @param onRetry Must put the screen back into its loading state.
 * @param secondaryLabel Optional second action label ("Go back"); shown only with [onSecondary].
 */
@Composable
fun ErrorContent(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.ErrorOutline
) {
    ExpressiveStateCard(
        icon = icon,
        tileColor = MaterialTheme.colorScheme.errorContainer,
        tileContentColor = MaterialTheme.colorScheme.onErrorContainer,
        title = title,
        message = message,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveButton(
                onClick = onRetry,
                text = ErrorContentCopy.TRY_AGAIN,
                style = ExpressiveButtonStyle.FilledTonal
            )
            if (secondaryLabel != null && onSecondary != null) {
                ExpressiveButton(
                    onClick = onSecondary,
                    text = secondaryLabel,
                    style = ExpressiveButtonStyle.Text
                )
            }
        }
    }
}

/**
 * The card every empty and error state draws: a leading tile ([ExpressiveSizes.Tile] /
 * [ExpressiveSizes.TileIcon], [ExpressiveShapes.Tile]), `headlineSmall` Bold, `bodyMedium`
 * on `onSurfaceVariant`, 24dp padding, on [ExpressiveShapes.Card] over `surfaceContainer`,
 * capped at 360dp like the chat empty state. [actions] is laid out under the copy.
 */
@Composable
internal fun ExpressiveStateCard(
    icon: ImageVector,
    tileColor: Color,
    tileContentColor: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxWidth().padding(ExpressiveSpacing.Gutter), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = StateCardMaxWidth),
            shape = StateCardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(StateCardPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = ExpressiveShapes.Tile,
                    color = tileColor,
                    contentColor = tileContentColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(ExpressiveSizes.Tile)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(ExpressiveSizes.TileIcon)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (actions != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    actions()
                }
            }
        }
    }
}

/**
 * The one container of [ExpressiveEmptyState] and [ErrorContent]: [ExpressiveShapes.Card]
 * (W6 audit F3; pinned in `ErrorContentCopyTest` so neither state can drift back to a round
 * corner).
 */
internal val StateCardShape = ExpressiveShapes.Card

/** Inner padding of the empty/error state card (chat `ChatEmptyState` uses the same 24dp). */
internal val StateCardPadding = 24.dp

/** Width cap of the empty/error state card (chat `ChatEmptyState` uses the same 360dp). */
internal val StateCardMaxWidth = 360.dp
