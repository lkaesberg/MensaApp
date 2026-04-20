package com.lkaesberg.mensaapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun shouldHideAfternoonMealsForCanteenOnDate_hidesForZentralmensaOnSaturday() {
        assertTrue(
            shouldHideAfternoonMealsForCanteenOnDate(
                canteen = Canteen(id = "1", name = "Zentralmensa"),
                meals = listOf(sampleMealDate(servedOn = "2026-04-18"))
            )
        )
    }

    @Test
    fun shouldHideAfternoonMealsForCanteenOnDate_doesNotHideForZentralmensaOnWeekday() {
        assertFalse(
            shouldHideAfternoonMealsForCanteenOnDate(
                canteen = Canteen(id = "1", name = "Zentralmensa"),
                meals = listOf(sampleMealDate(servedOn = "2026-04-20"))
            )
        )
    }

    @Test
    fun shouldHideAfternoonMealsForCanteenOnDate_doesNotHideForOtherCanteens() {
        assertFalse(
            shouldHideAfternoonMealsForCanteenOnDate(
                canteen = Canteen(id = "2", name = "Nordmensa"),
                meals = listOf(sampleMealDate(servedOn = "2026-04-18"))
            )
        )
    }

    private fun sampleMealDate(servedOn: String): MealDate = MealDate(
        id = "meal-date-1",
        mealId = "meal-1",
        canteenId = "canteen-1",
        servedOn = servedOn,
        category = "Hauptgericht"
    )
}
