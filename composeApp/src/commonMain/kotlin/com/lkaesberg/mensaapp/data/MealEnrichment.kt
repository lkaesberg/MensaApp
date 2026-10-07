package com.lkaesberg.mensaapp.data

import com.lkaesberg.mensaapp.Meal
import com.lkaesberg.mensaapp.MealDate

/**
 * Derives cleanTitle / sides / allergens / additives for rows that haven't
 * been re-scraped since the 2026-05-03 schema migration. Used everywhere
 * the redesigned UI displays meals so we don't show "(a.1, c, g)" in headlines.
 *
 * If the DB columns are populated, [Meal.cleanTitle] etc. are returned
 * verbatim — this only fills the gap.
 */
data class EnrichedMeal(
    val cleanTitle: String,
    val sides: List<String>,
    val description: String,
    val allergens: List<String>,
    val additives: List<String>,
)

object MealEnrichment {
    private val allergenInBracketsRegex = Regex("""\(([^)]*)\)""")
    private val allergenCodeRegex = Regex("""[a-n](?:\.\d)?""", RegexOption.IGNORE_CASE)
    private val additiveCodeRegex = Regex("""\d+""")
    private val splitSidesRegex = Regex("""\s+(mit|und)\s+""", RegexOption.IGNORE_CASE)
    // Matches a bare allergen (a–n, optionally a.1) or additive (1–2 digits).
    // Used to drop fragments that leaked out of a parenthesised code list,
    // e.g. ["…", "(Fleisch", "a", "a.1", "c", "g", "i", "3)"].
    private val codeFragmentRegex =
        Regex("""^([0-9]{1,2}|[a-n](?:\.[0-9]{1,2})?)$""", RegexOption.IGNORE_CASE)

    fun enrich(meal: Meal?): EnrichedMeal {
        if (meal == null) {
            return EnrichedMeal("", emptyList(), "", emptyList(), emptyList())
        }
        val cleanTitle = meal.cleanTitle?.takeIf { it.isNotBlank() }
            ?: stripAllergens(meal.title).trim()
        val sides = meal.sides?.let { sanitizeSides(it) }?.takeIf { it.isNotEmpty() }
            ?: deriveSides(meal.fullText, cleanTitle)
        val description = meal.description?.takeIf { it.isNotBlank() }
            ?: meal.fullText
        val allergens = meal.allergens?.takeIf { it.isNotEmpty() }
            ?: extractAllergens(meal.fullText)
        val additives = meal.additives?.takeIf { it.isNotEmpty() }
            ?: extractAdditives(meal.fullText)
        return EnrichedMeal(cleanTitle, sides, description, allergens, additives)
    }

    fun enrich(date: MealDate): EnrichedMeal = enrich(date.meals)

    private fun stripAllergens(text: String): String =
        text.replace(allergenInBracketsRegex, "").replace(Regex("""\s+"""), " ").trim()

    private fun sanitizeSides(sides: List<String>): List<String> =
        sides.mapNotNull { raw ->
            val trimmed = raw.trim()
            when {
                trimmed.isEmpty() -> null
                trimmed.startsWith('(') -> null
                trimmed.endsWith(')') -> null
                codeFragmentRegex.matches(trimmed) -> null
                MenuStructure.isConnectorOnly(trimmed) -> null
                else -> trimmed
            }
        }

    private fun deriveSides(fullText: String, cleanTitle: String): List<String> {
        if (fullText.isBlank()) return emptyList()
        val cleaned = stripAllergens(fullText)
        // Strip the title portion if the description starts with it.
        val withoutTitle = if (cleaned.startsWith(cleanTitle, ignoreCase = true))
            cleaned.removePrefix(cleanTitle).trim()
        else cleaned
        // Pull out sides after first "mit" / "und".
        val firstSplit = withoutTitle.split(splitSidesRegex, limit = 2)
        val tail = firstSplit.getOrNull(1) ?: return emptyList()
        return tail.split(",", " und ", " · ")
            .map { it.trim().trim(',', '.', ';') }
            .filter { it.isNotEmpty() && it.length < 60 }
            .distinct()
    }

    private fun extractAllergens(fullText: String): List<String> {
        val brackets = allergenInBracketsRegex.findAll(fullText).map { it.groupValues[1] }.joinToString(",")
        return allergenCodeRegex.findAll(brackets)
            .map { it.value.lowercase() }
            .filter { it.matches(Regex("""[a-n](\.\d)?""")) }
            .toSet()
            .toList()
            .sorted()
    }

    private fun extractAdditives(fullText: String): List<String> {
        val brackets = allergenInBracketsRegex.findAll(fullText).map { it.groupValues[1] }.joinToString(",")
        return additiveCodeRegex.findAll(brackets)
            .map { it.value }
            .toSet()
            .toList()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
    }

    /** Human-readable allergen lookup (German). Mirrors mensa-data.js. */
    val allergenLabels: Map<String, String> = mapOf(
        "a" to "Glutenhaltige Getreide",
        "a.1" to "Weizen", "a.2" to "Roggen", "a.3" to "Gerste",
        "a.4" to "Hafer", "a.5" to "Dinkel", "a.6" to "Kamut",
        "b" to "Krebstiere", "c" to "Eier", "d" to "Fisch", "e" to "Erdnüsse",
        "f" to "Soja", "g" to "Milch", "h" to "Schalenfrüchte",
        "h.1" to "Mandeln", "h.2" to "Haselnüsse",
        "i" to "Sellerie", "j" to "Senf", "k" to "Sesam",
        "l" to "Sulfite", "m" to "Lupine", "n" to "Weichtiere",
    )

    val additiveLabels: Map<String, String> = mapOf(
        "1" to "Farbstoff", "2" to "Konservierungsstoff",
        "3" to "Antioxidans", "4" to "Geschmacksverstärker",
        "5" to "Geschwefelt", "6" to "Geschwärzt",
        "7" to "Gewachst", "8" to "Phosphat",
        "9" to "Süßungsmittel", "10" to "Phenylalanin",
        "11" to "Koffein",
    )
}
