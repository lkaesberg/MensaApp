package com.example.mensaapp

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate

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

        raw.groupBy { LocalDate.parse(it.servedOn) }
            .mapValues { entry ->
                entry.value.sortedBy { it.category.lowercase() }
            }
    } catch (e: Throwable) {
        println("Error fetching meals for canteen $canteenId: ${e.message}")
        emptyMap()
    }
} 