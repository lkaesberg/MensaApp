package com.lkaesberg.mensaapp.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class FavoriteAlarmsTest {
    private val berlin = TimeZone.of("Europe/Berlin")
    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun laterTodayWhenTheHourIsStillAhead() {
        // 08:00 CEST → 09:00 CEST the same day (07:00Z).
        assertEquals(at("2026-10-08T07:00:00Z").toEpochMilliseconds(),
            FavoriteAlarms.nextTriggerAt(9, at("2026-10-08T06:00:00Z"), berlin))
    }

    @Test
    fun tomorrowOnceTheHourHasPassed_evenExactlyOnIt() {
        // An alarm firing at 09:00:00 must arm tomorrow, not itself again.
        assertEquals(at("2026-10-09T07:00:00Z").toEpochMilliseconds(),
            FavoriteAlarms.nextTriggerAt(9, at("2026-10-08T07:00:00Z"), berlin))
        assertEquals(at("2026-10-09T07:00:00Z").toEpochMilliseconds(),
            FavoriteAlarms.nextTriggerAt(9, at("2026-10-08T08:00:00Z"), berlin))
    }

    @Test
    fun keepsWallClockHourAcrossDaylightSaving() {
        // Sat 24 Oct 09:00 CEST (07:00Z) → Sun 25 Oct 09:00 CET (08:00Z), 25 h later.
        assertEquals(at("2026-10-25T08:00:00Z").toEpochMilliseconds(),
            FavoriteAlarms.nextTriggerAt(9, at("2026-10-24T07:00:00Z"), berlin))
    }
}
