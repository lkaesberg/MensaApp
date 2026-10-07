package com.lkaesberg.mensaapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Canteen(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String? = null,
    val slug: String? = null,
    @SerialName("external_id") val externalId: Int? = null,
)

@Serializable
data class Meal(
    val id: String,
    val title: String,
    @SerialName("full_text") val fullText: String,
    val icons: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("image_path_generic") val imagePathGeneric: String? = null,

    // Richer fields added by the 2026-05-03 schema migration. Nullable so rows
    // that haven't been re-scraped still decode cleanly.
    @SerialName("clean_title") val cleanTitle: String? = null,
    val description: String? = null,
    val sides: List<String>? = null,
    val allergens: List<String>? = null,
    val additives: List<String>? = null,

    // Added by the 2026-05-05 API migration. external_id is the upstream
    // dish id; title_en/description_en/sides_en come from the new endpoint.
    @SerialName("external_id") val externalId: Int? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("description_en") val descriptionEn: String? = null,
    @SerialName("sides_en") val sidesEn: List<String>? = null,
    @SerialName("rating_avg") val ratingAvg: Float? = null,
    @SerialName("recipe_name") val recipeName: String? = null,

    // 2026-10-08 menu-structure migration. course: main | soup | side | salad
    // | dessert. alternatives: other options at the same counter (a side
    // counter's other sides, a dessert's other desserts) — `sides` stays
    // "served with this dish". All null on rows synced before the migration.
    val course: String? = null,
    val alternatives: List<String>? = null,
    @SerialName("alternatives_en") val alternativesEn: List<String>? = null,
)

@Serializable
data class MealDate(
    val id: String,
    @SerialName("meal_id") val mealId: String,
    @SerialName("canteen_id") val canteenId: String,
    @SerialName("served_on") val servedOn: String, // ISO yyyy-MM-dd
    val category: String,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Set when the upstream stops listing this meal. Null = active. Field is
    // absent on databases without the deactivation migration; that's fine —
    // it stays null and the meal is treated as active.
    @SerialName("deactivated_at") val deactivatedAt: String? = null,
    // "lunch" | "afternoon" | null. Drives the time-separator on the feed.
    @SerialName("meal_period") val mealPeriod: String? = null,
    // Upstream `<mittag>` integer (2026-05-07 migration). 0 = served whenever
    // the canteen is open; >0 = lunch-only. Primary signal for the
    // Mittag/Nachmittag split at Zentralmensa.
    val mittag: Int? = null,
    // Upstream `<preis_pos>` (2026-10-08): the canteen's own counter order.
    @SerialName("sort_order") val sortOrder: Int? = null,

    // Per-dish prices populated by the new API scraper (2026-05-05).
    // Nullable for rows scraped before the migration.
    @SerialName("price_students") val priceStudents: String? = null,
    @SerialName("price_employees") val priceEmployees: String? = null,
    @SerialName("price_guests") val priceGuests: String? = null,
    @SerialName("price_students_cents") val priceStudentsCents: Int? = null,
    @SerialName("price_employees_cents") val priceEmployeesCents: Int? = null,
    @SerialName("price_guests_cents") val priceGuestsCents: Int? = null,

    // Embedded join, will be null if not requested
    val meals: Meal? = null,
)

@Serializable
data class CanteenHours(
    @SerialName("canteen_id") val canteenId: String,
    @SerialName("day_of_week") val dayOfWeek: Int, // ISO 1=Mon..7=Sun
    @SerialName("open_time") val openTime: String? = null, // "HH:MM:SS" or null
    @SerialName("close_time") val closeTime: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CanteenOccupancy(
    @SerialName("canteen_id") val canteenId: String,
    @SerialName("observed_at") val observedAt: String,
    @SerialName("sales_current") val salesCurrent: Double? = null,
    @SerialName("sales_avg_weekday") val salesAvgWeekday: Double? = null,
    @SerialName("sales_avg_yearly") val salesAvgYearly: Double? = null,
    val color: String? = null,
    @SerialName("status_key") val statusKey: String? = null,
)

@Serializable
data class CanteenPrice(
    val id: String,
    @SerialName("canteen_id") val canteenId: String,
    val category: String,
    @SerialName("price_students") val priceStudents: String? = null,
    @SerialName("price_employees") val priceEmployees: String? = null,
    @SerialName("price_guests") val priceGuests: String? = null,
    @SerialName("price_students_cents") val priceStudentsCents: Int? = null,
    @SerialName("price_employees_cents") val priceEmployeesCents: Int? = null,
    @SerialName("price_guests_cents") val priceGuestsCents: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
