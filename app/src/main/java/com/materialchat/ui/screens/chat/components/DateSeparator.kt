package com.materialchat.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.materialchat.ui.theme.CustomShapes

/**
 * Date separator pill shown between message groups from different days.
 *
 * Follows M3 Expressive guidelines:
 * - Pill shape (fully rounded) for a friendly, modern feel
 * - secondaryContainer tonal pill (content colour from the same pair) flanked by short
 *   outlineVariant hairlines, so the day break reads as a divider and not as a message
 * - labelMedium SemiBold typography; never italic
 * - Centered alignment with generous padding
 *
 * Displays smart date labels:
 * - "Today" for today's messages
 * - "Yesterday" for yesterday's messages
 * - Day of week for last 7 days (e.g., "Monday")
 * - Full date for older messages (e.g., "March 5, 2026")
 */
@Composable
fun DateSeparator(
    timestampMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DateSeparatorRule()
        Surface(
            shape = CustomShapes.Pill,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = formatDateLabel(timestampMs),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        DateSeparatorRule()
    }
}

/** Short hairline either side of the pill. */
@Composable
private fun DateSeparatorRule() {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(1.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    )
}

/**
 * Formats a timestamp into a smart human-readable date label.
 */
private fun formatDateLabel(timestampMs: Long): String {
    val messageDate = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameDay(messageDate, today) -> "Today"
        isSameDay(messageDate, yesterday) -> "Yesterday"
        isWithinLastWeek(messageDate, today) -> {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestampMs))
        }
        isSameYear(messageDate, today) -> {
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(timestampMs))
        }
        else -> {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
        }
    }
}

/**
 * Checks if two calendar instances represent the same calendar day.
 */
private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

/**
 * Checks if a date is within the last 7 days.
 */
private fun isWithinLastWeek(date: Calendar, today: Calendar): Boolean {
    val weekAgo = Calendar.getInstance().apply {
        timeInMillis = today.timeInMillis
        add(Calendar.DAY_OF_YEAR, -7)
    }
    return date.after(weekAgo)
}

/**
 * Checks if two calendar instances are in the same year.
 */
private fun isSameYear(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
}

/**
 * Checks if two timestamps fall on different calendar days.
 * Used to determine when to insert a DateSeparator in the message list.
 */
fun shouldShowDateSeparator(currentTimestampMs: Long, previousTimestampMs: Long?): Boolean {
    if (previousTimestampMs == null) return true // Always show for first message
    val current = Calendar.getInstance().apply { timeInMillis = currentTimestampMs }
    val previous = Calendar.getInstance().apply { timeInMillis = previousTimestampMs }
    return !isSameDay(current, previous)
}
