package com.lkaesberg.mensaapp

import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.Course
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.MenuSection
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.data.PriceResolver
import com.lkaesberg.mensaapp.data.SideKind
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
    fun isRealSide_keepsSidesDropsSaucesAndGarnish() {
        for (side in listOf(
            "Butterkartoffeln", "Pestokartoffeln", "Senf-Kartoffeln", "Pommes frites", "Spätzle",
            "Koriander Wildreismischung", "Rosenkohlröschen mit Speck", "Blattsalatmix mit Frenchdressing",
            "Gurkensalat in Sauerrahm", "Green Fusion Slaw",
        )) assertTrue(MenuStructure.isRealSide(side), side)
        for (notSide in listOf(
            "Remouladensauce", "vegane Remouladensauce", "Mediterrane Gemüsesauce", "Tomatensauce Parmerosa",
            "Kräuterquark Dip", "Mango-Curryketchup", "Portion Senf", "Zitronenecke", "frische Petersilie",
            "Frühlingszwiebelröllchen", "Tagesdessert", "Ciabattabrot", "zusätzlich Geflügelwürstchen Wiener Art",
        )) assertFalse(MenuStructure.isRealSide(notSide), notSide)
    }

    @Test
    fun sideKind_sortsStarchVegetablesAndSalads() {
        for (starch in listOf("Senf-Kartoffeln", "Kräuterpüree", "Basmatireis", "Gemüse-Vollkornreis", "Spätzle", "Spinatnudeln", "Tomaten-Bulgur", "Zartweizen mit buntem Gemüse"))
            assertEquals(SideKind.Starch, MenuStructure.sideKind(starch), starch)
        for (veg in listOf("Fingermöhren", "Püree aus jungen Erbsen", "Blattspinat in Rahm", "Kaiserschoten", "Pariser Karotten", "Leipziger Allerlei"))
            assertEquals(SideKind.Vegetable, MenuStructure.sideKind(veg), veg)
        for (salad in listOf("Gurkensalat in Sauerrahm", "Kartoffelsalat mit Mayonnaise", "Green Fusion Slaw", "Daily Crunch Mix"))
            assertEquals(SideKind.Salad, MenuStructure.sideKind(salad), salad)
    }

    private val label: (SideKind) -> String = { it.name }

    @Test
    fun sideBoard_withoutCounters_groupsMenuSidesByKind() {
        // Zentralmensa: no side counters, sides only inside the menus.
        val day = listOf(
            md("Menü", "Kibbelinge", 80, sides = listOf("Remouladensauce", "Butterkartoffeln", "Fingermöhren", "Tomatensalat")),
            md("Vegan", "Falafel", 10, sides = listOf("Chilisauce hell", "Bulgur mit Zucchini", "Fingermöhren")),
        ).sortedWith(MenuStructure.feedOrder(emptySet()))
        val board = MenuStructure.sideBoard(day, label)
        assertEquals(listOf(SideKind.Starch, SideKind.Vegetable, SideKind.Salad), board.map { it.kind })
        assertEquals(listOf("Bulgur mit Zucchini", "Butterkartoffeln"), board[0].items)
        assertEquals(listOf("Fingermöhren"), board[1].items)
        assertEquals(listOf("Tomatensalat"), board[2].items)
        assertTrue(board.all { it.counter == null })
    }

    @Test
    fun sideBoard_mergesMenuSidesIntoMatchingCounters() {
        // Mensa am Turm: side counters plus a main whose base is a real side.
        val day = listOf(
            md("Asia Point", "Tofu", 200, sides = listOf("Curryreis mit Ananasstücken", "Frühlingszwiebelröllchen", "Kaiserschoten")),
            md("Gemüsebeilage", "Frisches Möhrengemüse", 180, course = "side", sides = emptyList(), alternatives = listOf("Blumenkohlgemüse")),
            md("Stärkebeilage", "Senf-Kartoffeln", 200, course = "side", sides = emptyList(), alternatives = listOf("Pommes frites")),
            md("Salat", "Rote Betekugelsalat", 190, course = "salad", sides = emptyList(), alternatives = emptyList()),
        ).sortedWith(MenuStructure.feedOrder(emptySet()))
        val board = MenuStructure.sideBoard(day, label)
        assertEquals(listOf("Stärkebeilage", "Gemüsebeilage", "Salat"), board.map { it.label })
        assertEquals(listOf("Senf-Kartoffeln", "Pommes frites", "Curryreis mit Ananasstücken"), board[0].items)
        assertEquals(listOf("Frisches Möhrengemüse", "Blumenkohlgemüse", "Kaiserschoten"), board[1].items)
        assertTrue(board.all { it.counter != null })
    }

    @Test
    fun dessertBoard_andPickableItems() {
        val day = listOf(
            md("Dessert", "Hausgemachte Rote Grütze", 210, course = "dessert",
                sides = listOf("veganer Vanillesauce"), alternatives = listOf("Hausgemachter Fruchtquark Ananas")),
            md("Menü", "Kibbelinge", 80, sides = listOf("Remouladensauce", "Butterkartoffeln")),
        )
        assertEquals(
            listOf("Hausgemachte Rote Grütze mit veganer Vanillesauce", "Hausgemachter Fruchtquark Ananas"),
            MenuStructure.dessertBoard(day).single().items,
        )
        // Normalised like favourite keys; sauces never count.
        assertEquals(
            setOf("butterkartoffeln", "hausgemachte rote grütze mit veganer vanillesauce", "hausgemachter fruchtquark ananas"),
            MenuStructure.pickableItems(day),
        )
    }

    @Test
    fun sidePrices_byKindForCanteensThatPublishThem() {
        val zentral = CanteenStaticData.all.first { it.slug == "zentral" }
        assertEquals("0,85", PriceResolver.forSideKind(SideKind.Starch, zentral)?.students)
        assertEquals("1,50", PriceResolver.forSideKind(SideKind.Starch, zentral)?.guests)
        assertEquals("1,35", PriceResolver.forSideKind(SideKind.Salad, zentral)?.guests)
        assertEquals(null, PriceResolver.forSideKind(SideKind.Dessert, zentral))
        assertEquals(null, PriceResolver.forSideKind(SideKind.Starch, CanteenStaticData.all.first { it.slug == "hawk" }))
    }

    @Test
    fun pastaBuffet_isPricedPer100g_andItsItemsAreNotSides() {
        // Zentralmensa, 2026-10-19, as upstream sends it.
        val buffet = md(
            "Pasta- Buffet", "Pastabuffet", 150,
            sides = listOf("Spiralnudeln Totiglioni", "Spaghetti", "Spinat-Käsesauce", "Rucola"),
        )
        val menu = md("Menü", "Schnitzel", 80, sides = listOf("Pommes frites"))
        assertTrue(MenuStructure.isPricedPer100g(buffet))
        assertFalse(MenuStructure.isPricedPer100g(menu))
        assertEquals(listOf("Pommes frites"), MenuStructure.menuSides(listOf(buffet, menu)))
    }

    @Test
    fun sideImagePath_matchesBackendSlug() {
        // smooth-endpoint: name.trim().replace(/\W+/g, '_').toLowerCase() — ASCII-only \W.
        assertEquals("sides/senf_kartoffeln.jpg", MenuStructure.sideImagePath("Senf-Kartoffeln"))
        assertEquals("sides/kr_uterp_ree.jpg", MenuStructure.sideImagePath("Kräuterpüree"))
    }

    @Test
    fun closedPlaceholderIsNotADish() {
        assertTrue(MenuStructure.isNonDish("Heute leider geschlossen"))
        assertFalse(MenuStructure.isNonDish("Veganes Schnitzel"))
    }
}
