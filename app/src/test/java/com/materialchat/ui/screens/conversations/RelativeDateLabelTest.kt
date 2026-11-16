package com.materialchat.ui.screens.conversations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * The conversation-row date must be short and single-line for every locale: on an
 * Italian phone "4 novembre" was squeezed next to a long model id and rendered one
 * glyph per line. These tests pin the compact format and the length ceiling.
 */
class RelativeDateLabelTest {

    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val italian = Locale.ITALY
    private val us = Locale.US

    private fun at(year: Int, month: Int, day: Int, hour: Int = 10, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val now = at(2025, 11, 16, 9, 33)

    @Test
    fun `same year uses abbreviated month in locale order`() {
        assertEquals("4 nov", RelativeDateLabel.format(at(2025, 11, 4), now, zone, italian))
        assertEquals("Nov 4", RelativeDateLabel.format(at(2025, 11, 4), now, zone, us))
    }

    @Test
    fun `today shows the time`() {
        assertEquals("08:15", RelativeDateLabel.format(at(2025, 11, 16, 8, 15), now, zone, italian))
        val usLabel = RelativeDateLabel.format(at(2025, 11, 16, 8, 15), now, zone, us)
        assertTrue(usLabel, usLabel.startsWith("8:15"))
    }

    @Test
    fun `previous six days show a short weekday`() {
        // 2025-11-14 is a Friday
        assertEquals("ven", RelativeDateLabel.format(at(2025, 11, 14), now, zone, italian))
        assertEquals("Fri", RelativeDateLabel.format(at(2025, 11, 14), now, zone, us))
        // Seven days back is no longer a weekday label
        assertEquals("9 nov", RelativeDateLabel.format(at(2025, 11, 9), now, zone, italian))
    }

    @Test
    fun `earlier years show month and year`() {
        assertEquals("nov 2024", RelativeDateLabel.format(at(2024, 11, 4), now, zone, italian))
        assertEquals("Nov 2024", RelativeDateLabel.format(at(2024, 11, 4), now, zone, us))
    }

    @Test
    fun `label is deterministic and does not depend on available space`() {
        val a = RelativeDateLabel.format(at(2025, 11, 4), now, zone, italian)
        val b = RelativeDateLabel.format(at(2025, 11, 4), now, zone, italian)
        assertEquals(a, b)
        assertFalse(a.contains("novembre"))
    }

    @Test
    fun `every label stays within the width ceiling and on one line`() {
        val locales = listOf(
            italian, us, Locale.FRANCE, Locale.GERMANY, Locale.JAPAN, Locale.KOREA,
            Locale.CHINA, Locale("es", "ES"), Locale("pt", "BR"), Locale("ru", "RU"),
            Locale("ar", "EG"), Locale("hi", "IN"), Locale("el", "GR")
        )
        val samples = listOf(
            at(2025, 11, 16, 23, 59), // today
            at(2025, 11, 12),         // weekday
            at(2025, 9, 30),          // same year
            at(2023, 12, 25)          // other year
        )
        for (locale in locales) {
            for (sample in samples) {
                val label = RelativeDateLabel.format(sample, now, zone, locale)
                assertTrue("$locale -> '$label'", label.length <= RelativeDateLabel.MAX_LENGTH)
                assertFalse("$locale -> '$label'", label.contains('\n'))
                assertFalse("$locale -> '$label'", label.contains("  "))
                assertTrue("$locale -> '$label'", label.isNotBlank())
            }
        }
    }

    @Test
    fun `day-before-month follows the locale's medium date pattern`() {
        assertTrue(RelativeDateLabel.dayBeforeMonth(italian))
        assertTrue(RelativeDateLabel.dayBeforeMonth(Locale.GERMANY))
        assertFalse(RelativeDateLabel.dayBeforeMonth(us))
    }
}
