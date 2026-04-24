package com.materialchat.ui.screens.workflows.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Horizontal step progress indicator for workflow execution.
 *
 * Displays a row of circles connected by lines representing workflow steps:
 * - Completed steps: filled primary circle with a checkmark icon
 * - Current step: filled pulsing primary circle with step number
 * - Pending steps: outlined surface container circle with step number
 *
 * Uses M3 color tokens and spring-based animation for the pulsing effect.
 *
 * @param totalSteps Total number of steps in the workflow
 * @param currentStep The 0-indexed current step
 * @param completedSteps Set of 0-indexed step indices that are completed
 * @param modifier Modifier for the indicator row
 */
@Composable
fun StepProgressIndicator(
    totalSteps: Int,
    currentStep: Int,
    completedSteps: Set<Int>,
    modifier: Modifier = Modifier
) {
    if (totalSteps == 0) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val pendingContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    // Pulsing animation for the current step
    val infiniteTransition = rememberInfiniteTransition(label = "stepPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (stepIndex in 0 until totalSteps) {
            val isCompleted = stepIndex in completedSteps
            val isCurrent = stepIndex == currentStep
            val isPending = !isCompleted && !isCurrent

            // Step circle
            StepCircle(
                stepNumber = stepIndex + 1,
                isCompleted = isCompleted,
                isCurrent = isCurrent,
                pulseAlpha = if (isCurrent) pulseAlpha else 1f,
                primaryColor = primaryColor,
                onPrimaryColor = onPrimaryColor,
                pendingContainerColor = pendingContainerColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                outlineColor = outlineColor
            )

            // Connector line (between circles)
            if (stepIndex < totalSteps - 1) {
                val lineColor by animateColorAsState(
                    targetValue = if (isCompleted) primaryColor else outlineColor.copy(alpha = 0.3f),
                    animationSpec = ExpressiveMotion.Effects.color(),
                    label = "lineColor"
                )

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .size(height = 2.dp, width = 0.dp)
                ) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Individual step circle in the progress indicator.
 */
@Composable
private fun StepCircle(
    stepNumber: Int,
    isCompleted: Boolean,
    isCurrent: Boolean,
    pulseAlpha: Float,
    primaryColor: Color,
    onPrimaryColor: Color,
    pendingContainerColor: Color,
    onSurfaceVariantColor: Color,
    outlineColor: Color
) {
    val circleSize = 32.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(circleSize)
    ) {
        val bgColor by animateColorAsState(
            targetValue = when {
                isCompleted -> primaryColor
                isCurrent -> primaryColor
                else -> pendingContainerColor
            },
            animationSpec = ExpressiveMotion.Effects.color(),
            label = "circleBgColor"
        )

        Canvas(modifier = Modifier.size(circleSize)) {
            val radius = size.minDimension / 2

            // Filled circle for all states so every step has a clear container.
            drawCircle(
                color = bgColor,
                radius = radius
            )

            if (!isCompleted && !isCurrent) {
                drawCircle(
                    color = outlineColor.copy(alpha = 0.4f),
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        if (isCompleted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Step $stepNumber completed",
                modifier = Modifier.size(18.dp),
                tint = onPrimaryColor
            )
        } else {
            Text(
                text = "$stepNumber",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isCurrent) onPrimaryColor else onSurfaceVariantColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
