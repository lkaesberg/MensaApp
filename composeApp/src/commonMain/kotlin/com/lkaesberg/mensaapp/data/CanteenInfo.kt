package com.lkaesberg.mensaapp.data

import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Hardcoded supplemental data for the 5 Studierendenwerk Göttingen canteens.
 * The Supabase `canteens` table only stores id + name; addresses, hours,
 * coordinates, and walk-time live here so the canteen-picker / detail screens
 * can render without an extra round-trip.
 */
data class CanteenInfo(
    val slug: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val walkMinFromCenter: Int,
    val distance: String,
    val hours: List<HoursEntry>,
    val fallbackPrices: List<FallbackPrice>,
    /**
     * Price of a single side, by kind ("Stärkebeilage", "Gemüse-/Salatbeilage").
     * The menu API only prices sides that have a counter of their own (Mensa
     * am Turm); everywhere else the sides come with the dishes but can be
     * bought on their own too.
     */
    val sidePrices: List<FallbackPrice> = emptyList(),
)

data class HoursEntry(
    val daysLabel: String, // "Mo–Fr"
    val time: String,      // "11:30–14:30" or "Geschlossen"
    val days: Set<DayOfWeek>,
    val openTime: LocalTime?,
    val closeTime: LocalTime?,
)

data class FallbackPrice(
    val category: String,
    val students: String,
    val employees: String,
    val guests: String,
)

object CanteenStaticData {
    private fun parseTime(hhmm: String): LocalTime {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return LocalTime(h, m)
    }
    private fun weekdayHours(open: String, close: String) = HoursEntry(
        daysLabel = "Mo–Fr",
        time = "$open–$close",
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        openTime = parseTime(open),
        closeTime = parseTime(close),
    )
    private fun saturdayHours(open: String, close: String) = HoursEntry(
        daysLabel = "Sa",
        time = "$open–$close",
        days = setOf(DayOfWeek.SATURDAY),
        openTime = parseTime(open),
        closeTime = parseTime(close),
    )
    private val saturdayClosed = HoursEntry("Sa", "Geschlossen", setOf(DayOfWeek.SATURDAY), null, null)
    private val sundayClosed = HoursEntry("So", "Geschlossen", setOf(DayOfWeek.SUNDAY), null, null)

    // From the canteen pages on studierendenwerk-goettingen.de (Zentralmensa,
    // Mensa am Turm, CGiN list the same "Beilagen" prices), checked 2026-10-08.
    private val standardSidePrices = listOf(
        FallbackPrice("Stärkebeilage", "0,85", "1,30", "1,50"),
        FallbackPrice("Gemüse-/Salatbeilage", "0,85", "1,15", "1,35"),
    )

    private val standardMenuPrices = listOf(
        FallbackPrice("Menü", "4,20", "6,30", "7,50"),
        FallbackPrice("Vegetarisch/vegan", "3,80", "5,90", "7,10"),
    )

    val all: List<CanteenInfo> = listOf(
        CanteenInfo(
            slug = "zentral",
            name = "Zentralmensa",
            address = "Platz der Göttinger Sieben 4, 37073 Göttingen",
            lat = 51.5413, lng = 9.9355,
            walkMinFromCenter = 4, distance = "320 m",
            hours = listOf(
                HoursEntry(
                    daysLabel = "Mo–Do",
                    time = "11:30–18:00",
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
                    openTime = parseTime("11:30"),
                    closeTime = parseTime("18:00"),
                ),
                HoursEntry(
                    daysLabel = "Fr",
                    time = "11:30–14:30",
                    days = setOf(DayOfWeek.FRIDAY),
                    openTime = parseTime("11:30"),
                    closeTime = parseTime("14:30"),
                ),
                saturdayHours("11:45", "14:30"),
                sundayClosed,
            ),
            fallbackPrices = standardMenuPrices + FallbackPrice("CampusCurry", "4,50", "6,70", "7,90"),
            sidePrices = standardSidePrices,
        ),
        CanteenInfo(
            slug = "turm",
            name = "Mensa am Turm",
            address = "Goßlerstraße 12d, 37073 Göttingen",
            lat = 51.5333, lng = 9.9387,
            walkMinFromCenter = 9, distance = "720 m",
            hours = listOf(
                HoursEntry(
                    daysLabel = "Mo–Do",
                    time = "11:30–14:30",
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
                    openTime = parseTime("11:30"),
                    closeTime = parseTime("14:30"),
                ),
                HoursEntry(
                    daysLabel = "Fr",
                    time = "11:30–14:15",
                    days = setOf(DayOfWeek.FRIDAY),
                    openTime = parseTime("11:30"),
                    closeTime = parseTime("14:15"),
                ),
                saturdayClosed, sundayClosed,
            ),
            fallbackPrices = standardMenuPrices,
            sidePrices = standardSidePrices,
        ),
        CanteenInfo(
            slug = "cgin",
            name = "CGiN",
            address = "Friedrich-Hund-Platz 1, 37077 Göttingen",
            lat = 51.5570, lng = 9.9580,
            walkMinFromCenter = 18, distance = "1,4 km",
            hours = listOf(
                weekdayHours("11:15", "14:15"),
                saturdayClosed, sundayClosed,
            ),
            fallbackPrices = listOf(FallbackPrice("Menü", "4,20", "6,30", "7,50")),
            sidePrices = standardSidePrices,
        ),
        CanteenInfo(
            slug = "hawk",
            name = "Bistro HAWK",
            address = "Von-Ossietzky-Str. 99, 37085 Göttingen",
            lat = 51.5290, lng = 9.9170,
            walkMinFromCenter = 22, distance = "1,8 km",
            // Source: /api/oeffnungszeiten — Bistro HAWK only runs early-week.
            hours = listOf(
                HoursEntry(
                    daysLabel = "Mo–Mi",
                    time = "07:45–14:45",
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
                    openTime = parseTime("07:45"),
                    closeTime = parseTime("14:45"),
                ),
                HoursEntry("Do", "Geschlossen", setOf(DayOfWeek.THURSDAY), null, null),
                HoursEntry("Fr", "Geschlossen", setOf(DayOfWeek.FRIDAY), null, null),
                saturdayClosed, sundayClosed,
            ),
            fallbackPrices = emptyList(),
        ),
        // Nordmensa was removed (2026-05-05): the official mobile-app API
        // no longer lists it (no menu, no hours, no occupancy), so showing
        // the entry led to a permanently empty surface.
    )

    val byName: Map<String, CanteenInfo> = all.associateBy { it.name }

    /** Looks up info by either the DB UUID resolved through a name match, or the slug, or the visible name. */
    fun matchFor(canteenName: String): CanteenInfo? =
        byName[canteenName]
            ?: all.firstOrNull { it.name.equals(canteenName, ignoreCase = true) }
            ?: all.firstOrNull { canteenName.contains(it.name, ignoreCase = true) }
            ?: all.firstOrNull { it.slug.equals(canteenName, ignoreCase = true) }

    fun openNow(info: CanteenInfo, now: LocalDateTime = currentDateTime()): Boolean {
        val today = info.hours.firstOrNull { now.date.dayOfWeek in it.days } ?: return false
        val open = today.openTime ?: return false
        val close = today.closeTime ?: return false
        return now.time >= open && now.time < close
    }

    /**
     * `true` once the canteen is past its closing time for the given day —
     * also `true` on days the canteen has no service slot at all (e.g. Sunday).
     * Used by the feed to show today's meals as deactivated and default the
     * date strip to tomorrow.
     */
    fun pastClosingTime(info: CanteenInfo, now: LocalDateTime = currentDateTime()): Boolean {
        val today = info.hours.firstOrNull { now.date.dayOfWeek in it.days } ?: return true
        val close = today.closeTime ?: return true
        return now.time >= close
    }

    fun closesAt(info: CanteenInfo, now: LocalDateTime = currentDateTime()): String? {
        val today = info.hours.firstOrNull { now.date.dayOfWeek in it.days } ?: return null
        val close = today.closeTime ?: return null
        return "${close.hour.toString().padStart(2, '0')}:${close.minute.toString().padStart(2, '0')}"
    }

    fun todayHoursLine(info: CanteenInfo, now: LocalDateTime = currentDateTime()): String {
        val today = info.hours.firstOrNull { now.date.dayOfWeek in it.days }
        return today?.time ?: "Geschlossen"
    }

    private fun currentDateTime() =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
