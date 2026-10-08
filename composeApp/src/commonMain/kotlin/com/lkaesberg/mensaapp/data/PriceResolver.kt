package com.lkaesberg.mensaapp.data

import com.lkaesberg.mensaapp.CanteenPrice
import com.lkaesberg.mensaapp.MealDate

/**
 * Resolves a Studi-Preis for a given meal category.
 *
 * Order of preference:
 * 1. Exact / fuzzy match against `canteen_prices` rows (DB).
 * 2. Same match against the per-canteen `fallbackPrices` constant.
 * 3. Generic fallback to a "Menü" price (most meals share that base tariff).
 * 4. Any first available student price.
 *
 * Some categories (Snack, Kaffee & Kuchen, ad-hoc desserts) shouldn't display
 * a hot-meal price — caller can opt-out by passing `acceptGenericFallback=false`.
 */
data class ResolvedPrice(val students: String, val employees: String, val guests: String) {
    val studentText: String get() = if (students.isBlank()) "" else "$students €"
    val employeeText: String get() = if (employees.isBlank()) "" else "$employees €"
    val guestText: String get() = if (guests.isBlank()) "" else "$guests €"
    fun textFor(role: UserRole): String = when (role) {
        UserRole.Studierend -> studentText
        UserRole.Bedienstet -> employeeText
        UserRole.Gast -> guestText
    }

    companion object {
        /**
         * Defensive cleanup for rows scraped before the upstream fix shipped:
         * strip "Euro"/"€" suffixes and turn unavailable markers ("---", "—")
         * into empty strings so the UI can render a placeholder instead.
         */
        internal fun sanitize(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val cleaned = raw
                .replace(' ', ' ')
                .replace("€", "")
                .replace(Regex("(?i)\\bEuro\\b"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isEmpty()) return ""
            if (Regex("^[-–—\\s]+$").matches(cleaned)) return ""
            return cleaned
        }
    }
}

object PriceResolver {

    /**
     * Per-dish prices live on `meal_dates` as of the 2026-05-05 API
     * migration — the official endpoint attaches three role-prices to
     * every dish. This is strictly better than the historical fuzzy match
     * against `canteen_prices`. Falls back to canteen-level constants for
     * the rare canteens (HAWK fallback) without per-dish data.
     */
    /**
     * Price of one side of [kind] at the canteen ([CanteenInfo.sidePrices]);
     * vegetables and salads share a price. Null where none is published, and
     * for desserts, which are priced per counter.
     */
    fun forSideKind(kind: SideKind, info: CanteenInfo?): ResolvedPrice? {
        val wanted = when (kind) {
            SideKind.Starch -> "stärke"
            SideKind.Vegetable, SideKind.Salad -> "gemüse"
            SideKind.Dessert -> return null
        }
        return info?.sidePrices
            ?.firstOrNull { wanted in it.category.lowercase() }
            ?.let { ResolvedPrice(it.students, it.employees, it.guests) }
    }

    fun forMealDate(md: MealDate, info: CanteenInfo?): ResolvedPrice? {
        if (!md.priceStudents.isNullOrBlank() ||
            !md.priceEmployees.isNullOrBlank() ||
            !md.priceGuests.isNullOrBlank()) {
            return ResolvedPrice(
                students = ResolvedPrice.sanitize(md.priceStudents),
                employees = ResolvedPrice.sanitize(md.priceEmployees),
                guests = ResolvedPrice.sanitize(md.priceGuests),
            )
        }
        val target = md.category.lowercase().trim()
        return info?.fallbackPrices
            ?.firstOrNull { matches(it.category, target) }
            ?.let { ResolvedPrice(it.students, it.employees, it.guests) }
    }

    fun resolve(
        mealCategory: String,
        dbPrices: List<CanteenPrice>,
        fallbacks: List<FallbackPrice>,
        acceptGenericFallback: Boolean = true,
    ): ResolvedPrice? {
        val target = mealCategory.lowercase().trim()

        dbPrices.firstOrNull { matches(it.category, target) }?.let { row ->
            return ResolvedPrice(
                students = ResolvedPrice.sanitize(row.priceStudents),
                employees = ResolvedPrice.sanitize(row.priceEmployees),
                guests = ResolvedPrice.sanitize(row.priceGuests),
            )
        }
        fallbacks.firstOrNull { matches(it.category, target) }?.let { row ->
            return ResolvedPrice(row.students, row.employees, row.guests)
        }
        if (!acceptGenericFallback) return null

        // Snacks / desserts shouldn't borrow a hot-meal price.
        if (looksLikeDessertOrSnack(target)) return null

        // Use the standard Menü price as the catch-all base tariff (Pasta,
        // Teppan Yaki, NDS-Menü etc. typically cost the same).
        dbPrices.firstOrNull { "menü" in it.category.lowercase() }?.let { row ->
            return ResolvedPrice(
                students = ResolvedPrice.sanitize(row.priceStudents),
                employees = ResolvedPrice.sanitize(row.priceEmployees),
                guests = ResolvedPrice.sanitize(row.priceGuests),
            )
        }
        fallbacks.firstOrNull { "menü" in it.category.lowercase() }?.let { row ->
            return ResolvedPrice(row.students, row.employees, row.guests)
        }
        // Last-ditch: any first non-blank price.
        dbPrices.firstOrNull { !it.priceStudents.isNullOrBlank() }?.let { row ->
            return ResolvedPrice(
                students = ResolvedPrice.sanitize(row.priceStudents),
                employees = ResolvedPrice.sanitize(row.priceEmployees),
                guests = ResolvedPrice.sanitize(row.priceGuests),
            )
        }
        return fallbacks.firstOrNull { it.students.isNotBlank() }?.let {
            ResolvedPrice(it.students, it.employees, it.guests)
        }
    }

    private fun matches(priceCategory: String, mealCategory: String): Boolean {
        val p = priceCategory.lowercase().trim()
        return when {
            p == mealCategory -> true
            // Vegan + Vegetarisch share the "Vegetarisch/vegan" line.
            ("vegetarisch" in p || "vegan" in p) &&
                ("vegan" in mealCategory || "vegetarisch" in mealCategory || mealCategory.startsWith("vegi")) -> true
            // Menü → MENÜ 1, MENÜ 2, NDS-MENÜ …
            "menü" in p && "menü" in mealCategory -> true
            "curry" in p && "curry" in mealCategory -> true
            "pasta" in p && "pasta" in mealCategory -> true
            "teppan" in p && "teppan" in mealCategory -> true
            else -> false
        }
    }

    private fun looksLikeDessertOrSnack(cat: String): Boolean =
        "dessert" in cat || "nachtisch" in cat || "kaffee" in cat ||
            "kuchen" in cat || "snack" in cat || "brötchen" in cat
}
