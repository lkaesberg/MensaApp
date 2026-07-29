package com.lkaesberg.mensaapp

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

class MealsRepository(private val postgrest: Postgrest) {

    suspend fun getCanteens(): List<Canteen> = try {
        postgrest["canteens"].select().decodeList<Canteen>()
    } catch (e: Throwable) {
        println("Error fetching canteens: ${e.message}")
        emptyList()
    }

    suspend fun getMealsForCanteen(canteenId: String): Map<LocalDate, List<MealDate>> = try {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        // Embed the meal information via a join. New schema columns
        // (clean_title, sides, allergens, additives, meal_period) decode
        // automatically thanks to nullable defaults in the data classes.
        val raw = postgrest["meal_dates"].select(columns = Columns.raw("*,meals(*)")) {
            filter {
                eq("canteen_id", canteenId)
                gte("served_on", yesterday.toString())
            }
            order("served_on", Order.ASCENDING)
        }.decodeList<MealDate>()

        // Drop empty-title rows entirely — they're "Last Minute" placeholders
        // the API returns for unfilled category slots. The new scraper skips
        // them at parse time, but legacy rows linger in DB and surface as
        // weird greyed-out blank cards otherwise.
        val titled = raw.filter { md ->
            val title = md.meals?.cleanTitle?.ifBlank { null } ?: md.meals?.title
            !title.isNullOrBlank()
        }

        // Today's rows can be deactivated for two very different reasons, and
        // `deactivated_at` alone doesn't distinguish them:
        //
        //   • the canteen closed and upstream dropped the whole day — the plan
        //     should stay visible, greyed out via MealCard's deactivatedAt
        //     handling, instead of the feed going blank;
        //   • a single counter was renamed or retired while the rest of the day
        //     is still served (e.g. "Turm Vegan" → "Turm Vegan Kombi") — the
        //     stale row has to go, the API is the ground truth.
        //
        // A still-active row for today means upstream is publishing the day, so
        // any deactivated sibling is a real removal. Only when the entire day is
        // deactivated do we keep the rows as a closed-canteen memento. Past and
        // future deactivations stay filtered unconditionally.
        val todayStillServed = titled.any { md ->
            md.deactivatedAt == null && LocalDate.parse(md.servedOn) == today
        }

        titled.filter { md ->
            md.deactivatedAt == null ||
                (!todayStillServed && LocalDate.parse(md.servedOn) == today)
        }
            .groupBy { LocalDate.parse(it.servedOn) }
            .mapValues { entry ->
                entry.value.sortedBy { it.category.lowercase() }
            }
    } catch (e: Throwable) {
        println("Error fetching meals for canteen $canteenId: ${e.message}")
        emptyMap()
    }

    /**
     * Full history fetch — used by the dish-stats and all-meals-archive screens.
     * Bounded by [sinceDays] so we don't pull the entire DB; the design defaults
     * to a 90-day window for stats.
     */
    suspend fun getMealsHistory(canteenId: String, sinceDays: Int = 90): List<MealDate> = try {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val since = today.minus(sinceDays, DateTimeUnit.DAY)

        // History == *strictly past* offerings. We exclude today and the
        // future for two reasons: future-scheduled rows (the long-mode
        // scraper pulls 30+ days ahead) would surface as the most-recent
        // occurrence; and today's own offerings shouldn't read as "zuletzt:
        // heute" everywhere — that's not history, it's now. The user is
        // looking at the archive for historical context, and "zuletzt vor
        // 2 Wochen" is the useful signal even if the dish happens to also
        // be on today's plan.
        val raw = postgrest["meal_dates"].select(columns = Columns.raw("*,meals(*)")) {
            filter {
                eq("canteen_id", canteenId)
                gte("served_on", since.toString())
                lt("served_on", today.toString())
            }
            order("served_on", Order.DESCENDING)
        }.decodeList<MealDate>()

        raw.filter { it.deactivatedAt == null }
    } catch (e: Throwable) {
        println("Error fetching meal history for canteen $canteenId: ${e.message}")
        emptyList()
    }

    /**
     * Per-canteen prices from the `canteen_prices` table introduced by the
     * 2026-05-03 migration. Returns an empty list if the table is not present
     * (older DB) or no prices have been scraped.
     */
    suspend fun getCanteenPrices(canteenId: String): List<CanteenPrice> = try {
        postgrest["canteen_prices"].select {
            filter { eq("canteen_id", canteenId) }
        }.decodeList<CanteenPrice>()
    } catch (e: Throwable) {
        println("Error fetching prices for canteen $canteenId: ${e.message}")
        emptyList()
    }

    /**
     * Weekly opening-hours pattern, populated by the 2026-05-05 API
     * migration. One row per (canteen, ISO weekday). Open/close times are
     * null on closed days. Empty list when the migration hasn't run.
     */
    suspend fun getCanteenHours(): List<CanteenHours> = try {
        postgrest["canteen_hours"].select().decodeList<CanteenHours>()
    } catch (e: Throwable) {
        println("Error fetching canteen hours: ${e.message}")
        emptyList()
    }

    /**
     * Latest occupancy snapshot per canteen from the `canteen_occupancy_latest`
     * view. Empty list outside opening hours or before the first sync.
     */
    suspend fun getOccupancyLatest(): List<CanteenOccupancy> = try {
        postgrest["canteen_occupancy_latest"].select().decodeList<CanteenOccupancy>()
    } catch (e: Throwable) {
        println("Error fetching occupancy: ${e.message}")
        emptyList()
    }

    /**
     * Time-series of occupancy snapshots for a single canteen on a single day.
     * Pulls every row the 30-minute cron has logged for that day, ordered
     * chronologically. Empty list before the first sync or on closed days.
     */
    suspend fun getOccupancyForDay(canteenId: String, day: LocalDate): List<CanteenOccupancy> = try {
        val tz = TimeZone.currentSystemDefault()
        val start = day.atStartOfDayIn(tz).toString()
        val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toString()
        postgrest["canteen_occupancy"].select {
            filter {
                eq("canteen_id", canteenId)
                gte("observed_at", start)
                lt("observed_at", end)
            }
            order("observed_at", Order.ASCENDING)
        }.decodeList<CanteenOccupancy>()
    } catch (e: Throwable) {
        println("Error fetching occupancy history for canteen $canteenId: ${e.message}")
        emptyList()
    }
}
