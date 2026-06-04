package com.materialchat.ui.components

import android.text.format.DateUtils
import java.util.Calendar

/**
 * Relative date label for conversation list fast scroll (by [updatedAt]).
 */
fun fastScrollDateLabel(updatedAtMillis: Long): String {
    val now = System.currentTimeMillis()
    val dayMs = DateUtils.DAY_IN_MILLIS
    val diffDays = ((now - updatedAtMillis) / dayMs).toInt().coerceAtLeast(0)
    return when {
        diffDays == 0 -> "Today"
        diffDays == 1 -> "Yesterday"
        diffDays < 7 -> DateUtils.getRelativeTimeSpanString(
            updatedAtMillis,
            now,
            DateUtils.DAY_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = updatedAtMillis }
            val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault())
            val year = cal.get(Calendar.YEAR)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (year == currentYear) {
                "${cal.get(Calendar.DAY_OF_MONTH)} $month"
            } else {
                "${cal.get(Calendar.DAY_OF_MONTH)} $month $year"
            }
        }
    }
}

/**
 * Date label for chat message fast scroll (by [createdAt]).
 */
fun fastScrollMessageDateLabel(createdAtMillis: Long): String = fastScrollDateLabel(createdAtMillis)