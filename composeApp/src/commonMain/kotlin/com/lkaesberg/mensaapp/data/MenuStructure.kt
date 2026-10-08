package com.lkaesberg.mensaapp.data

import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.i18n.alternativesFor
import com.lkaesberg.mensaapp.i18n.sidesFor
import com.lkaesberg.mensaapp.i18n.titleFor

/** What a counter serves. The DB stores the lowercase name (`meals.course`). */
enum class Course {
    Main, Soup, Side, Salad, Dessert;

    companion object {
        fun fromDb(value: String?): Course? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** What a side is, for grouping: Stärkebeilage, Gemüsebeilage, Salat — and the dessert counters. */
enum class SideKind { Starch, Vegetable, Salad, Dessert }

/**
 * One block of the day's sides: a side/salad counter with its options plus
 * the matching sides of the mains (Mensa am Turm), or — without a counter of
 * that kind ([counter] null) — just the mains' sides of that kind.
 */
data class SideGroup(val label: String, val kind: SideKind, val counter: MealDate?, val items: List<String>)

/**
 * Feed sections, in display order. Soups (Eintopf) are a full meal, so they
 * sit with the mains; [Staples] are mains on the plan nearly every day
 * (CampusCurry, the daily Teppan Yaki) — listed after what's new today.
 */
enum class MenuSection { Mains, Staples, Sides, Desserts }

/**
 * Structure of a day's menu: which entries are dishes and which are side or
 * dessert counters, the order they're shown in, and readable counter names.
 *
 * Mensa am Turm in particular lists its "Stärkebeilage" / "Gemüsebeilage" /
 * "Salat" / "Dessert" counters with the same shape as a dish; sorted
 * alphabetically by category they ended up between the mains.
 */
object MenuStructure {

    /**
     * DB value when the row was synced after the 2026-10-08 migration,
     * otherwise derived from the category. Mirror of classifyCourse() in
     * supabase/functions/_shared/menu.ts — keep the two in step.
     */
    fun courseOf(md: MealDate): Course = Course.fromDb(md.meals?.course) ?: classify(md.category)

    fun classify(category: String): Course {
        val c = category.lowercase()
        return when {
            "dessert" in c || "nachtisch" in c || "nachspeise" in c -> Course.Dessert
            "beilage" in c -> Course.Side
            "salat" in c -> Course.Salad
            "eintopf" in c || "suppe" in c -> Course.Soup
            else -> Course.Main
        }
    }

    /** Pass the day's [staples] to split them out; without them every main is in [MenuSection.Mains]. */
    fun sectionOf(md: MealDate, staples: Set<String> = emptySet()): MenuSection = when (courseOf(md)) {
        Course.Main, Course.Soup -> if (dishKey(md) in staples) MenuSection.Staples else MenuSection.Mains
        Course.Side, Course.Salad -> MenuSection.Sides
        Course.Dessert -> MenuSection.Desserts
    }

    /** Side, salad and dessert counters render as compact option lists. */
    fun isCounter(md: MealDate): Boolean = when (courseOf(md)) {
        Course.Side, Course.Salad, Course.Dessert -> true
        Course.Main, Course.Soup -> false
    }

    private val allergenParens = Regex("""\s*\([^)]*\)""")

    /** Identity of a dish across days, independent of drifting allergen codes. */
    fun dishKey(md: MealDate): String {
        val meal = md.meals ?: return ""
        val title = meal.cleanTitle?.takeIf { it.isNotBlank() } ?: meal.title.replace(allergenParens, "")
        return title.trim().lowercase()
    }

    private const val STAPLE_MIN_DAYS = 3

    /**
     * Mains that are on the plan on at least half of the canteen's serving
     * days in [rows] (and on at least three). Measured on live data that's
     * CampusCurry and Vegane Currywurst at Zentralmensa and CGiN, the daily
     * Teppan Yaki at CGiN — and nothing at Mensa am Turm, whose mains change
     * every day. Data-driven so a renamed or new standing counter needs no code.
     */
    fun staples(rows: Collection<MealDate>): Set<String> {
        val servingDays = rows.mapTo(HashSet()) { it.servedOn }.size
        return rows.asSequence()
            .filter { !isCounter(it) }
            .groupBy({ dishKey(it) }, { it.servedOn })
            .filter { (key, days) ->
                val n = days.toSet().size
                key.isNotEmpty() && n >= STAPLE_MIN_DAYS && n * 2 >= servingDays
            }
            .keys
    }

    /**
     * Today's mains → staples → sides & salads → desserts. Within a section
     * an Eintopf comes after the plated mains, then the canteen's own counter
     * order (`<preis_pos>`), then the category for rows synced before that
     * column existed.
     */
    fun feedOrder(staples: Set<String>): Comparator<MealDate> = compareBy(
        { sectionOf(it, staples).ordinal },
        { if (courseOf(it) == Course.Soup) 1 else 0 },
        { it.sortOrder ?: Int.MAX_VALUE },
        { it.category.lowercase() },
    )

    private val nonDishRegex = Regex("""^\s*(heute\s+)?(leider\s+)?(geschlossen|closed)""", RegexOption.IGNORE_CASE)

    /** The closed-day placeholder ("Heute leider geschlossen") older syncs stored as a dish. */
    fun isNonDish(title: String): Boolean = nonDishRegex.containsMatchIn(title)

    // Canteen names and variant markers the counters carry around:
    // "Nds-Menü MaT Turm-Menü Vegetarisch Kombi", "Vegetarisch Kombi Zentralmensa",
    // "Dessertbuffet Bistro HAWK". Matched per whitespace token rather than with
    // \b, which is ASCII-only on Kotlin/Wasm and would miss "Turm-Menü".
    private val noiseTokens = setOf("mat", "zm", "zentralmensa", "hawk", "cgin", "turm-menü", "kombi", "hk", "kon")
    private val whitespace = Regex("""\s+""")

    /** "Nds-Menü MaT Turm-Menü Vegetarisch Kombi" → "Nds-Menü · Vegetarisch". */
    fun displayCategory(raw: String): String {
        val tokens = raw
            .replace("Bistro HAWK", " ", ignoreCase = true)
            .replace("Beats&Bites", "Beats & Bites", ignoreCase = true)
            .replace("Tepp.Yaki", "Teppan Yaki", ignoreCase = true)
            .split(whitespace)
            .filter { it.isNotBlank() && it.lowercase() !in noiseTokens }
        if (tokens.isEmpty()) return raw.trim()
        val head = tokens.first()
        return if (head.equals("Nds-Menü", ignoreCase = true) && tokens.size > 1) {
            "$head · ${tokens.drop(1).joinToString(" ")}"
        } else {
            tokens.joinToString(" ")
        }
    }

    private val legacyOr = Regex("""\s+(oder|or)\s+""", RegexOption.IGNORE_CASE)
    private val leadingWith = Regex("""^(mit|with)\s+""", RegexOption.IGNORE_CASE)

    /**
     * Everything a side/salad/dessert counter offers, in upstream order:
     * "Hausgemachte Rote Grütze mit veganer Vanillesauce", "Fruchtquark …", ….
     * The first entry is only first because upstream puts one option in
     * <essen> — the UI shows all of them alike.
     */
    fun counterOptions(md: MealDate, locale: Locale): List<String> {
        val meal = md.meals ?: return emptyList()
        val title = meal.titleFor(locale)
        val withWord = if (locale == Locale.De) "mit" else "with"
        val sides = meal.sidesFor(locale)
        if (meal.alternatives != null) {
            val head = if (sides.isEmpty()) title else "$title $withWord ${sides.joinToString(", ")}"
            return (listOf(head) + meal.alternativesFor(locale)).distinct()
        }
        // Synced before 2026-10-08: the comma split put the other options into
        // `sides`, and a dessert's whole "mit … oder … oder …" line into one entry.
        val pieces = sides.flatMap { it.split(legacyOr) }.map { it.trim() }.filter { it.isNotEmpty() }
        val first = pieces.firstOrNull()
        return if (first != null && leadingWith.containsMatchIn(first)) {
            listOf("$title $withWord ${first.replace(leadingWith, "")}") + pieces.drop(1)
        } else {
            listOf(title) + pieces
        }
    }

    // A comma split of "auf, Curryreis" or "zusätzlich, Brötchen" leaves the
    // connector as its own entry; rows synced before 2026-10-08 still have them.
    private val connectorWords = setOf("mit", "with", "auf", "on", "und", "and", "oder", "or", "zusätzlich", "additional")

    fun isConnectorOnly(side: String): Boolean = side.trim().lowercase() in connectorWords

    // ─── Real side dishes ───
    // Mirror of isRealSide() in supabase/functions/_shared/menu.ts (see the
    // reasoning there) — keep the two in step. German names only.
    private val condimentEnd = Regex(
        """(sauce|soße|sosse|sugo|ketchup|dip|dressing|vinaigrette|remoulade|mayonnaise|mayo|aioli|pesto|salsa|chutney|relish|jus|schmelze|hollandaise|topping|senf|quark|tzatziki|ragout|glasur|preiselbeeren)$""",
    )
    private val garnish = Regex(
        """^(frisch[a-zäöüß]*\s+)?(petersilie|schnittlauch|koriander|kresse|rucola|minze|zitronenecke|zitronen?|limetten?[a-zäöüß]*|frühlingszwiebel[a-zäöüß]*|röstzwiebeln|tomaten|gurken|in)$""",
    )
    private val notASide = Regex("""sprossen|parmesan|umlegt|dessert|brötchen|brot|baguette|ciabatta|naan""")
    private val sideHeadSplit = Regex("""\s+(mit|in)\s+""")
    private val extraPrefix = Regex("""^zusätzlich(\s|$)""")

    /** A side you'd pick on its own — not a sauce, dip, garnish, bread or optional extra. */
    fun isRealSide(name: String): Boolean {
        val t = name.trim().lowercase()
        if (t.isEmpty() || extraPrefix.containsMatchIn(t)) return false
        val head = t.split(sideHeadSplit, limit = 2).first().trim()
        if (garnish.matches(head) || notASide.containsMatchIn(head)) return false
        return head.split(whitespace).none { condimentEnd.containsMatchIn(it) }
    }

    private val saladWords = Regex("""salat|slaw|crunch""")
    private val starchWords = Regex(
        """kartoffel|pommes|frites|krokette|rösti|gratin|klöße|knödel|reis|nudel|spätzle|spaghetti|penne|farfalle|tortiglioni|totiglioni|couscous|bulgur|grünkern|zartweizen|quinoa|polenta|hirse|gnocchi|püree""",
    )
    // "Kräuterpüree" is potato; "Püree aus jungen Erbsen" is a vegetable.
    private val vegetablePuree = Regex("""erbse|möhre|karotte|gemüse|blumenkohl|brokkoli|broccoli|kürbis|sellerie|pastinake|spinat""")

    /** Salad, starch (potatoes, rice, pasta, grains) or — by default — vegetable. German names. */
    fun sideKind(name: String): SideKind {
        val head = name.trim().lowercase().split(sideHeadSplit, limit = 2).first()
        return when {
            saladWords.containsMatchIn(head) -> SideKind.Salad
            "püree" in head && vegetablePuree.containsMatchIn(head) -> SideKind.Vegetable
            starchWords.containsMatchIn(head) -> SideKind.Starch
            else -> SideKind.Vegetable
        }
    }

    private fun counterKind(md: MealDate): SideKind {
        val category = md.category.lowercase()
        return when {
            courseOf(md) == Course.Salad -> SideKind.Salad
            "gemüse" in category -> SideKind.Vegetable
            "stärke" in category -> SideKind.Starch
            else -> sideKind(md.meals?.cleanTitle ?: md.meals?.title.orEmpty())
        }
    }

    /**
     * The day's sides by kind — Stärkebeilage, Gemüsebeilage, Salat. Sides can
     * be combined freely, so the mains' real sides join in: at Mensa am Turm
     * they're merged into the counter of the same kind, at Zentralmensa or
     * CGiN (no side counters) they form the groups, labelled by [label].
     * German names: photos and favourites are keyed by them.
     */
    fun sideBoard(meals: List<MealDate>, label: (SideKind) -> String): List<SideGroup> {
        val counters = meals
            .filter { sectionOf(it) == MenuSection.Sides }
            .map { SideGroup(displayCategory(it.category), counterKind(it), it, counterOptions(it, Locale.De)) }
        val listed = counters.flatMapTo(HashSet()) { g -> g.items.map { it.lowercase() } }
        val fromMenus = menuSides(meals).filter { it.lowercase() !in listed }.groupBy { sideKind(it) }
        return SideKind.entries.flatMap { kind ->
            val extra = fromMenus[kind].orEmpty()
            val ofKind = counters.filter { it.kind == kind }
            when {
                ofKind.isNotEmpty() -> listOf(ofKind.first().copy(items = ofKind.first().items + extra)) + ofKind.drop(1)
                extra.isNotEmpty() -> listOf(SideGroup(label(kind), kind, null, extra))
                else -> emptyList()
            }
        }
    }

    /** The day's dessert counters with all their options (German names, like [sideBoard]). */
    fun dessertBoard(meals: List<MealDate>): List<SideGroup> = meals
        .filter { sectionOf(it) == MenuSection.Desserts }
        .map { SideGroup(displayCategory(it.category), SideKind.Dessert, it, counterOptions(it, Locale.De)) }

    /**
     * Every side and dessert you could pick on a day, normalised for
     * favourite matching — drives the favourite dot in the date strip and the
     * "next on the plan" line of a favourite side or dessert.
     */
    fun pickableItems(meals: List<MealDate>): Set<String> =
        (sideBoard(meals) { "" } + dessertBoard(meals))
            .flatMapTo(HashSet()) { g -> g.items.map { it.replace(",", " ").trim().lowercase().replace(whitespace, " ") } }

    /** Real sides of the day's mains, first appearance first, without duplicates. */
    fun menuSides(meals: List<MealDate>): List<String> = meals
        .filter { !isCounter(it) }
        .flatMap { it.meals?.sidesFor(Locale.De).orEmpty() }
        .filter { isRealSide(it) }
        .distinctBy { it.trim().lowercase() }

    private val nonSlugChars = Regex("""[^A-Za-z0-9_]+""")

    /** Storage path of a side's photo; the same ASCII-only slug smooth-endpoint writes. */
    fun sideImagePath(name: String): String =
        "sides/" + name.trim().replace(nonSlugChars, "_").lowercase() + ".jpg"
}
