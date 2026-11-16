package com.materialchat.ui.screens.conversations

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Compact, locale-aware date label for conversation rows.
 *
 * The label is deterministic for a given (timestamp, now, zone, locale) and is built to
 * sit on a single line: it never contains a line break and never exceeds [MAX_LENGTH]
 * characters, so the row can lay it out with `softWrap = false` and no ellipsis.
 *
 * Rules (all relative to the local calendar day of `now`):
 * - today → short localized time ("14:30", "2:30 PM")
 * - within the previous six days → short weekday ("lun", "Mon")
 * - same year → day and abbreviated month in the locale's order ("4 nov", "Nov 4")
 * - otherwise → abbreviated month and year ("nov 2024")
 *
 * Pure Kotlin/java.time so it is unit-testable on the JVM.
 */
object RelativeDateLabel {

    /** Hard ceiling on the label length; the row reserves width on this basis. */
    const val MAX_LENGTH = 12

    fun format(
        timestampMs: Long,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val then = Instant.ofEpochMilli(timestampMs).atZone(zone)
        val today = now.toLocalDate()
        val day = then.toLocalDate()

        val label = when {
            day == today -> timeOfDay(then, locale)
            day.isBefore(today) && !day.isBefore(today.minusDays(6)) ->
                then.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            day.year == today.year -> dayMonth(then, locale)
            else -> monthYear(then, locale)
        }
        return singleLine(label, then, locale)
    }

    private fun timeOfDay(time: ZonedDateTime, locale: Locale): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

    private fun dayMonth(time: ZonedDateTime, locale: Locale): String {
        val pattern = if (dayBeforeMonth(locale)) "d MMM" else "MMM d"
        return DateTimeFormatter.ofPattern(pattern, locale).format(time)
    }

    private fun monthYear(time: ZonedDateTime, locale: Locale): String =
        DateTimeFormatter.ofPattern("MMM yyyy", locale).format(time)

    /**
     * Whether the locale writes the day before the month, read off the localized
     * medium date pattern ("d MMM y" for it/fr/de, "MMM d, y" for en_US).
     */
    internal fun dayBeforeMonth(locale: Locale): Boolean {
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale
        )
        val dayIndex = pattern.indexOf('d')
        val monthIndex = pattern.indexOf('M')
        if (dayIndex < 0 || monthIndex < 0) return true
        return dayIndex < monthIndex
    }

    /**
     * Collapses whitespace and guarantees the length ceiling; a locale whose abbreviated
     * names are still too long falls back to a numeric day/month.
     */
    private fun singleLine(label: String, time: ZonedDateTime, locale: Locale): String {
        val collapsed = label.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= MAX_LENGTH) return collapsed
        val numeric = if (dayBeforeMonth(locale)) "d/M" else "M/d"
        return DateTimeFormatter.ofPattern(numeric, locale).format(time)
    }
}
