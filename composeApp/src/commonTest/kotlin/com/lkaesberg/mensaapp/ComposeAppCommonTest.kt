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
    fun pastabuffetMealMatchesVegetarianFilterEvenWithMeatIcon() {
        val mealDate = createMealDate(
            title = "Pastabuffet",
            fullText = "mit Tomatensauce oder Bolognese",
            icons = listOf("fleisch")
        )

        assertTrue(mealMatchesDietaryFilters(mealDate, setOf("vegetarisch")))
    }

    @Test
    fun teppanYakiSubtitleMatchesVegetarianFilterEvenWithMeatIcon() {
        val mealDate = createMealDate(
            title = "Nudelteller",
            fullText = "Teppan Yaki mit zwei Saucen",
            icons = listOf("fleisch")
        )

        assertTrue(mealMatchesDietaryFilters(mealDate, setOf("vegetarisch")))
    }

    @Test
    fun regularMeatMealDoesNotMatchVegetarianFilter() {
        val mealDate = createMealDate(
            title = "Schnitzel",
            fullText = "mit Pommes",
            icons = listOf("fleisch")
        )

        assertFalse(mealMatchesDietaryFilters(mealDate, setOf("vegetarisch")))
    }

    private fun createMealDate(title: String, fullText: String, icons: List<String>): MealDate = MealDate(
        id = "meal-date-id",
        mealId = "meal-id",
        canteenId = "canteen-id",
        servedOn = "2026-04-20",
        category = "Hauptgericht",
        meals = Meal(
            id = "meal-id",
            title = title,
            fullText = fullText,
            icons = icons
        )
    )
}
