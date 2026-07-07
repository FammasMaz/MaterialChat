package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.ui.screens.chat.ContextWindowLevel
import com.materialchat.ui.screens.chat.ContextWindowUsage
import com.materialchat.ui.theme.ExpressiveMotion
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ContextWindowIndicator(
    usage: ContextWindowUsage,
    modifier: Modifier = Modifier
) {
    val indicatorColors = contextIndicatorColors(usage.level)
    val label = usage.formatTokenLabel()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = indicatorColors.container,
            contentColor = indicatorColors.content,
            tonalElevation = 1.dp,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Context window usage, tap for details" }
                .clickable { expanded = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                WavyContextRing(
                    progress = usage.fractionUsed ?: 0.12f,
                    progressColor = indicatorColors.progress,
                    trackColor = indicatorColors.content.copy(alpha = 0.16f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 240.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Context window",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WavyContextRing(
                        progress = usage.fractionUsed ?: 0.12f,
                        progressColor = indicatorColors.progress,
                        trackColor = indicatorColors.content.copy(alpha = 0.16f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = usage.detailLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WavyContextRing(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "contextWindowProgress"
    )
    // Reuse a single Path across frames — wavyArcPath otherwise allocates one per draw.
    val path = remember { Path() }

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.16f
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = trackColor,
            startAngle = 128f,
            sweepAngle = 284f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawPath(
            path = wavyArcPath(
                path = path,
                center = center,
                radius = radius,
                startDegrees = -92f,
                sweepDegrees = 284f * animatedProgress,
                waveAmplitude = strokeWidth * 0.24f
            ),
            color = progressColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun wavyArcPath(
    path: Path,
    center: Offset,
    radius: Float,
    startDegrees: Float,
    sweepDegrees: Float,
    waveAmplitude: Float
): Path {
    path.rewind()
    val steps = 36
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val angle = Math.toRadians((startDegrees + sweepDegrees * t).toDouble())
        val wave = sin(t * PI * 5.0).toFloat() * waveAmplitude
        val r = radius + wave
        val point = Offset(
            x = center.x + cos(angle).toFloat() * r,
            y = center.y + sin(angle).toFloat() * r
        )
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    return path
}

@Composable
private fun contextIndicatorColors(level: ContextWindowLevel): ContextIndicatorColors {
    val colors = MaterialTheme.colorScheme
    return when (level) {
        ContextWindowLevel.Critical -> ContextIndicatorColors(
            container = colors.errorContainer,
            content = colors.onErrorContainer,
            progress = colors.error
        )
        ContextWindowLevel.High -> ContextIndicatorColors(
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
            progress = colors.tertiary
        )
        ContextWindowLevel.Filling -> ContextIndicatorColors(
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            progress = colors.secondary
        )
        ContextWindowLevel.Calm,
        ContextWindowLevel.Unknown -> ContextIndicatorColors(
            container = colors.surfaceContainerHigh,
            content = colors.onSurfaceVariant,
            progress = colors.primary
        )
    }
}

private data class ContextIndicatorColors(
    val container: Color,
    val content: Color,
    val progress: Color
)

private fun ContextWindowUsage.formatTokenLabel(): String {
    val used = compactTokenCount(usedTokens)
    val max = maxTokens?.let { compactTokenCount(it) }
    return if (max != null) "~$used / $max" else "~$used used"
}

private fun ContextWindowUsage.detailLabel(): String {
    val model = modelName.takeIf { it.isNotBlank() } ?: "selected model"
    val suffix = if (isApproximate) "Approximate" else "Reported"
    return if (maxTokens == null) {
        "$suffix token estimate for $model. Model limit unknown."
    } else {
        "$suffix chat context used for $model."
    }
}

private fun compactTokenCount(tokens: Int): String {
    return when {
        tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000f)
        tokens >= 10_000 -> "${tokens / 1_000}K"
        tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000f)
        else -> tokens.toString()
    }
}
