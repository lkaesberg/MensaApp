package com.lkaesberg.mensaapp

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

class MealsRepository(private val postgrest: Postgrest) {

    suspend fun getCanteens(): List<Canteen> = try {
        postgrest["canteens"].select().decodeList<Canteen>()
    } catch (e: Throwable) {
        println("Error fetching canteens: ${e.message}")
        emptyList()
    }

    suspend fun getMealsForCanteen(canteenId: String): Map<LocalDate, List<MealDate>> = try {
        // We embed the meal information via a join: select("*,meals(*)")
        val raw = postgrest["meal_dates"].select(columns = Columns.raw("*,meals(*)")) {
            filter { eq("canteen_id", canteenId) }
            order("served_on", Order.ASCENDING)
        }.decodeList<MealDate>()

        // Determine today's date once for filtering
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        raw.filter { LocalDate.parse(it.servedOn) >= yesterday }
            .groupBy { LocalDate.parse(it.servedOn) }
            .mapValues { entry ->
                entry.value.sortedBy { it.category.lowercase() }
            }
    } catch (e: Throwable) {
        println("Error fetching meals for canteen $canteenId: ${e.message}")
        emptyMap()
    }
} 