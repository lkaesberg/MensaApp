package com.lkaesberg.mensaapp

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesManager(private val settings: Settings) {
    private val _favorites = MutableStateFlow<Set<String>>(loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private fun loadFavorites(): Set<String> {
        val favoritesString = settings.getStringOrNull(FAVORITES_KEY) ?: ""
        return if (favoritesString.isNotEmpty()) {
            favoritesString.split(",").toSet()
        } else {
            emptySet()
        }
    }

    private fun saveFavorites(favorites: Set<String>) {
        settings.putString(FAVORITES_KEY, favorites.joinToString(","))
    }

    fun toggleFavorite(mealId: String) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(mealId)) {
            current.remove(mealId)
        } else {
            current.add(mealId)
        }
        _favorites.value = current
        saveFavorites(current)
    }

    fun isFavorite(mealId: String): Boolean {
        return _favorites.value.contains(mealId)
    }

    companion object {
        private const val FAVORITES_KEY = "favorite_meals"
    }
}
