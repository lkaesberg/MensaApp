package com.lkaesberg.mensaapp.i18n

import com.lkaesberg.mensaapp.Meal
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.MenuStructure

/**
 * Locale-aware accessors for meal text. The new API populates English
 * variants alongside the German fields; for any row missing an English
 * value (legacy rows, partial data) we fall back to German so the UI is
 * never empty.
 */
fun Meal.titleFor(locale: Locale): String =
    if (locale == Locale.En && !titleEn.isNullOrBlank()) titleEn
    else cleanTitle?.takeIf { it.isNotBlank() } ?: title

fun Meal.descriptionFor(locale: Locale): String =
    if (locale == Locale.En && !descriptionEn.isNullOrBlank()) descriptionEn
    else description ?: fullText

fun Meal.sidesFor(locale: Locale): List<String> =
    (if (locale == Locale.En && !sidesEn.isNullOrEmpty()) sidesEn else sides.orEmpty())
        .filterNot { MenuStructure.isConnectorOnly(it) }

/** Other options at the same counter (side counters, desserts, buffets). */
fun Meal.alternativesFor(locale: Locale): List<String> =
    if (locale == Locale.En && !alternativesEn.isNullOrEmpty()) alternativesEn
    else alternatives.orEmpty()

fun MealDate.titleFor(locale: Locale): String =
    meals?.titleFor(locale) ?: ""

fun MealDate.sidesFor(locale: Locale): List<String> =
    meals?.sidesFor(locale).orEmpty()
