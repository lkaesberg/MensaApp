package com.lkaesberg.mensaapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Canteen(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String? = null,
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

    // Embedded join, will be null if not requested
    val meals: Meal? = null,
)