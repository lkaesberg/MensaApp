package com.lkaesberg.mensaapp

import com.lkaesberg.mensaapp.data.Course
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.MenuSection
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.i18n.sidesFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuStructureTest {

    private fun md(
        category: String,
        title: String,
        sortOrder: Int? = null,
        servedOn: String = "2026-10-08",
        course: String? = null,
        sides: List<String>? = null,
        alternatives: List<String>? = null,
    ) = MealDate(
        id = "$category|$title|$servedOn",
        mealId = title,
        canteenId = "turm",
        servedOn = servedOn,
        category = category,
        sortOrder = sortOrder,
        meals = Meal(
            id = title,
            title = title,
            fullText = title,
            cleanTitle = title,
            course = course,
            sides = sides,
            alternatives = alternatives,
        ),
    )

    @Test
    fun classify_fallsBackToCategoryForLegacyRows() {
        assertEquals(Course.Side, MenuStructure.courseOf(md("Stärkebeilage", "Senf-Kartoffeln")))
        assertEquals(Course.Salad, MenuStructure.courseOf(md("Salat", "Rote Betekugelsalat")))
        assertEquals(Course.Dessert, MenuStructure.courseOf(md("Dessertbuffet Bistro HAWK", "Götterspeise")))
        assertEquals(Course.Soup, MenuStructure.courseOf(md("Spezial Eintopf", "Gnocchieintopf")))
        assertEquals(Course.Main, MenuStructure.courseOf(md("Green Spezial Süß", "Grießbrei")))
        // The synced value wins over the category heuristic.
        assertEquals(Course.Main, MenuStructure.courseOf(md("Salat Bowl", "Bowl", course = "main")))
    }

    @Test
    fun feedOrder_putsMainsFirstThenSidesThenDessert() {
        // Mensa am Turm, 2026-10-08, as the API orders it (<preis_pos>).
        val day = listOf(
            md("Dessert", "Rote Grütze", 210),
            md("Stärkebeilage", "Senf-Kartoffeln", 200),
            md("Asia Point", "Sojageschnetzeltes", 200),
            md("Salat", "Rote Betekugelsalat", 190),
            md("Gemüsebeilage", "Möhrengemüse", 180),
            md("Nds-Menü MaT Turm-Menü Vegetarisch Kombi", "Balkankäse", 141),
            md("Turm Vegan", "Veganes Schnitzel", 130),
        )
        val sorted = day.sortedWith(MenuStructure.feedOrder(emptySet()))
        assertEquals(
            listOf("Veganes Schnitzel", "Balkankäse", "Sojageschnetzeltes", "Möhrengemüse", "Rote Betekugelsalat", "Senf-Kartoffeln", "Rote Grütze"),
            sorted.map { it.meals!!.title },
        )
        assertEquals(
            listOf(MenuSection.Mains, MenuSection.Sides, MenuSection.Desserts),
            sorted.map { MenuStructure.sectionOf(it) }.distinct(),
        )
    }

    @Test
    fun staples_goAfterTodaysDishes_andSoupsAfterPlatedMains() {
        // Zentralmensa-like week: CampusCurry every day, the Menü changes daily.
        val days = (6..10).map { "2026-10-${it.toString().padStart(2, '0')}" }
        val week = days.flatMap { d ->
            listOf(
                md("CampusCurry", "CampusCurry Fleischwurst vom Schwein", 40, servedOn = d),
                md("Menü", "Menü am $d", 80, servedOn = d),
            )
        } + md("Spezial Eintopf", "Gnocchieintopf", 70, servedOn = days.last())
        val staples = MenuStructure.staples(week)
        assertEquals(setOf("campuscurry fleischwurst vom schwein"), staples)

        val friday = week.filter { it.servedOn == days.last() }.sortedWith(MenuStructure.feedOrder(staples))
        assertEquals(
            listOf("Menü am ${days.last()}", "Gnocchieintopf", "CampusCurry Fleischwurst vom Schwein"),
            friday.map { it.meals!!.title },
        )
        assertEquals(MenuSection.Staples, MenuStructure.sectionOf(friday.last(), staples))
    }

    @Test
    fun staples_needAtLeastThreeDays() {
        val twoDays = listOf("2026-10-08", "2026-10-09").map { md("CampusCurry", "Currywurst", servedOn = it) }
        assertTrue(MenuStructure.staples(twoDays).isEmpty())
    }

    @Test
    fun displayCategory_dropsCanteenNoise() {
        assertEquals("Nds-Menü · Vegetarisch", MenuStructure.displayCategory("Nds-Menü MaT Turm-Menü Vegetarisch Kombi"))
        assertEquals("Nds-Menü · Natürlich Fit", MenuStructure.displayCategory("Nds-Menü Natürlich Fit HK ZM"))
        assertEquals("Nds-Menü · Vegan", MenuStructure.displayCategory("Nds-Menü Vegan Kombi Bistro HAWK"))
        assertEquals("Vegetarisch", MenuStructure.displayCategory("Vegetarisch Kombi Zentralmensa"))
        assertEquals("Dessertbuffet", MenuStructure.displayCategory("Dessertbuffet Bistro HAWK"))
        assertEquals("Beats & Bites", MenuStructure.displayCategory("Beats&Bites MAT"))
        assertEquals("Teppan Yaki Burger", MenuStructure.displayCategory("CGiN Tepp.Yaki Burger kon"))
        assertEquals("Turm Vegan", MenuStructure.displayCategory("Turm Vegan"))
        assertEquals("Basic 1", MenuStructure.displayCategory("Basic 1"))
        assertEquals("Kombi", MenuStructure.displayCategory("Kombi"))
    }

    @Test
    fun counterOptions_structuredRows() {
        val starch = md(
            "Stärkebeilage", "Senf-Kartoffeln",
            course = "side", sides = emptyList(),
            alternatives = listOf("Pommes frites", "Tomatennudeln", "Kräuterpüree"),
        )
        assertEquals(
            listOf("Senf-Kartoffeln", "Pommes frites", "Tomatennudeln", "Kräuterpüree"),
            MenuStructure.counterOptions(starch, Locale.De),
        )
        val dessert = md(
            "Dessert", "Hausgemachte Rote Grütze",
            course = "dessert", sides = listOf("veganer Vanillesauce"),
            alternatives = listOf("Hausgemachter Fruchtquark Ananas"),
        )
        assertEquals(
            listOf("Hausgemachte Rote Grütze mit veganer Vanillesauce", "Hausgemachter Fruchtquark Ananas"),
            MenuStructure.counterOptions(dessert, Locale.De),
        )
    }

    @Test
    fun counterOptions_legacyRowsStillReadable() {
        val starch = md("Stärkebeilage", "Senf-Kartoffeln", sides = listOf("Pommes frites", "Tomatennudeln"))
        assertEquals(
            listOf("Senf-Kartoffeln", "Pommes frites", "Tomatennudeln"),
            MenuStructure.counterOptions(starch, Locale.De),
        )
        val dessert = md(
            "Dessert", "Hausgemachte Rote Grütze",
            sides = listOf("mit veganer Vanillesauce oder Hausgemachter Fruchtquark Ananas oder Veganer Joghurt"),
        )
        assertEquals(
            listOf("Hausgemachte Rote Grütze mit veganer Vanillesauce", "Hausgemachter Fruchtquark Ananas", "Veganer Joghurt"),
            MenuStructure.counterOptions(dessert, Locale.De),
        )
    }

    @Test
    fun legacyConnectorChipsAreDropped() {
        val meal = md("Asia Point", "Tofu", sides = listOf("auf", "Curryreis", "zusätzlich", "Brötchen")).meals!!
        assertEquals(listOf("Curryreis", "Brötchen"), meal.sidesFor(Locale.De))
    }

    @Test
    fun closedPlaceholderIsNotADish() {
        assertTrue(MenuStructure.isNonDish("Heute leider geschlossen"))
        assertFalse(MenuStructure.isNonDish("Veganes Schnitzel"))
    }
}
