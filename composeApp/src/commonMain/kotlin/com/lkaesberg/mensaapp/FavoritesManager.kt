package com.lkaesberg.mensaapp

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesManager(private val settings: Settings) {
    private val _favorites = MutableStateFlow<Set<String>>(load(FAVORITES_KEY))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    /**
     * Favourite sides and desserts ("Pommes frites", "Hausgemachte Rote
     * Grütze mit veganer Vanillesauce"), kept apart from favourite dishes so
     * the dish list, its "next on the plan" lookup and the notification
     * worker stay about dishes.
     */
    private val _sideFavorites = MutableStateFlow<Set<String>>(load(SIDE_FAVORITES_KEY))
    val sideFavorites: StateFlow<Set<String>> = _sideFavorites.asStateFlow()

    private fun load(key: String): Set<String> {
        val stored = settings.getStringOrNull(key) ?: ""
        return if (stored.isNotEmpty()) stored.split(",").toSet() else emptySet()
    }

    private fun saveFavorites(favorites: Set<String>) {
        settings.putString(FAVORITES_KEY, favorites.joinToString(","))
    }

    /**
     * Toggle a meal title in the favourites set. Matching is case- and
     * whitespace-insensitive so the same dish at a different canteen counts as
     * a single favourite even when the scraper produced slightly different
     * strings (extra spaces, casing, etc.). Removing strips every stored entry
     * that normalises to the same key, so any duplicates accumulated before
     * this normalisation existed get cleaned up on the next un-star.
     */
    fun toggleFavorite(title: String) {
        val n = normalizeFavoriteKey(title)
        val current = _favorites.value.toMutableSet()
        val removed = current.removeAll { normalizeFavoriteKey(it) == n }
        if (!removed) current.add(title.trim())
        _favorites.value = current
        saveFavorites(current)
    }

    fun isFavorite(title: String): Boolean = _favorites.value.containsFavorite(title)

    /** Same normalised matching as [toggleFavorite]. Commas are stripped: they're the storage separator. */
    fun toggleSideFavorite(name: String) {
        val clean = name.replace(",", " ").trim()
        val n = normalizeFavoriteKey(clean)
        val current = _sideFavorites.value.toMutableSet()
        val removed = current.removeAll { normalizeFavoriteKey(it) == n }
        if (!removed) current.add(clean)
        _sideFavorites.value = current
        settings.putString(SIDE_FAVORITES_KEY, current.joinToString(","))
    }

    companion object {
        private const val FAVORITES_KEY = "favorite_meals"
        private const val SIDE_FAVORITES_KEY = "favorite_sides"
    }
}

/** Lowercase + collapse whitespace so titles match across canteens. */
internal fun normalizeFavoriteKey(s: String): String =
    s.trim().lowercase().replace(Regex("\\s+"), " ")

/** Membership check that ignores casing / whitespace differences. */
internal fun Set<String>.containsFavorite(title: String): Boolean {
    if (title.isBlank()) return false
    val n = normalizeFavoriteKey(title)
    return any { normalizeFavoriteKey(it) == n }
}

/** [containsFavorite] for side names, which are stored with commas stripped. */
internal fun Set<String>.containsSide(name: String): Boolean = containsFavorite(name.replace(",", " "))
