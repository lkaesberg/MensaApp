package com.lkaesberg.mensaapp.ui

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Ignore navigation requests from a screen that's already mid-transition.
 * Without this, double-tapping a button (or rapidly tapping back) pushes /
 * pops the same destination twice, occasionally landing on a blank or
 * out-of-order back stack.
 */
fun NavController.navigateSafely(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (canActOnCurrentEntry()) {
        navigate(route) {
            launchSingleTop = true
            builder()
        }
    }
}

fun NavController.popBackStackSafely(): Boolean {
    if (!canActOnCurrentEntry()) return false
    return popBackStack()
}

private fun NavController.canActOnCurrentEntry(): Boolean {
    val current = currentBackStackEntry ?: return true
    return current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
}

/** Stable route definitions used by the NavHost. */
object Route {
    const val Onboarding = "onboarding"
    const val Feed = "feed"
    const val CanteenPicker = "canteens"
    const val CanteenDetail = "canteen/{slug}"
    const val MealDetail = "meal/{dateId}"
    const val Search = "search"
    const val Archive = "archive"
    const val DishStats = "stats"
    const val Favorites = "favorites"
    const val UpcomingFavorites = "favorites/upcoming"
    const val Settings = "settings"
    const val Sides = "sides/{date}"
    const val Desserts = "desserts/{date}"

    fun canteenDetail(slug: String) = "canteen/$slug"
    fun mealDetail(dateId: String) = "meal/$dateId"
    fun sides(date: String) = "sides/$date"
    fun desserts(date: String) = "desserts/$date"
}
