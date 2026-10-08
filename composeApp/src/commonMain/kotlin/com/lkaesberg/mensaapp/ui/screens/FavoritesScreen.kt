package com.lkaesberg.mensaapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.Canteen
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.i18n.LocalStrings
import com.lkaesberg.mensaapp.normalizeFavoriteKey
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.components.MTopBar
import com.lkaesberg.mensaapp.ui.components.MenuSectionHeader
import com.lkaesberg.mensaapp.ui.components.Plate
import com.lkaesberg.mensaapp.ui.components.SidePhoto
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn

@Composable
fun FavoritesScreen(
    state: MealsAppState,
    onBack: () -> Unit,
) {
    val palette = MensaTheme.palette
    val scope = rememberCoroutineScope()
    val favoriteIds by state.favoritesManager.favorites.collectAsState()
    val sideFavorites by state.favoritesManager.sideFavorites.collectAsState()
    val history by state.history.collectAsState()
    val upcomingMap by state.upcomingAcrossCanteens.collectAsState()

    LaunchedEffect(state.selectedCanteen.value?.id) {
        state.loadHistoryForSelected(scope, sinceDays = 90)
        state.loadUpcomingAcrossCanteens(scope)
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val favorites = remember(favoriteIds, upcomingMap, history) {
        favoriteIds.map { fav ->
            val favNorm = normalizeFavoriteKey(fav)
            val matches: (MealDate) -> Boolean = { md ->
                val key = md.meals?.cleanTitle ?: md.meals?.title ?: ""
                normalizeFavoriteKey(key) == favNorm ||
                    normalizeFavoriteKey(md.meals?.title ?: "") == favNorm
            }
            val mostRecent = history.firstOrNull(matches)
            // Find the closest upcoming match across every canteen.
            val nextOccurrence = upcomingMap.entries
                .flatMap { (canteenAndDate, meals) ->
                    meals.map { Triple(canteenAndDate.first, canteenAndDate.second, it) }
                }
                .filter { (_, date, md) ->
                    if (date < today) return@filter false
                    matches(md)
                }
                .sortedBy { it.second }
                .firstOrNull()
            FavoriteRow(
                title = fav,
                count = history.count(matches),
                mostRecent = mostRecent,
                next = nextOccurrence,
            )
        }
    }
    // Favourite sides and desserts with their next day on any plan.
    val sideRows = remember(sideFavorites, upcomingMap) {
        val byDay = upcomingMap.entries
            .filter { it.key.second >= today }
            .sortedBy { it.key.second }
            .map { (canteenAndDate, meals) -> canteenAndDate to MenuStructure.pickableItems(meals) }
        sideFavorites.sortedBy { it.lowercase() }.map { name ->
            val key = normalizeFavoriteKey(name)
            name to byDay.firstOrNull { (_, sides) -> key in sides }?.first
        }
    }
    val upcoming = favorites.count {
        it.next != null && (it.next.second.toEpochDays() - today.toEpochDays()).toInt() in 0..3
    }

    val s = LocalStrings.current
    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        MTopBar(
            title = s.favorites,
            subtitle = s.favoritesSummary(favorites.size, upcoming),
            onBack = onBack,
            actions = {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Star, null, tint = palette.amber, modifier = Modifier.size(20.dp))
                }
            },
        )
        // Upcoming banner
        if (upcoming > 0) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.forest)
                    .padding(14.dp),
            ) {
                Text(s.nextThreeDays, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(s.favsArrivingSoon(upcoming), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
                val sample = favorites.filter { it.next != null }.take(3).joinToString(" · ") { it.title.take(20) }
                Text(sample, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
            }
        }
        if (favorites.isEmpty() && sideRows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("☆", fontSize = 36.sp, color = palette.sub)
                Spacer(Modifier.height(8.dp))
                Text(s.noFavoritesYet, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(s.noFavoritesHint, color = palette.sub, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(favorites, key = { it.title }) { f ->
                    val nextLabel = f.next?.let { (canteen, date, _) ->
                        val diff = (date.toEpochDays() - today.toEpochDays()).toInt()
                        val day = when (diff) {
                            0 -> s.today
                            1 -> s.tomorrow
                            else -> "${s.weekdaysShort[(date.dayOfWeek.isoDayNumber-1).coerceIn(0,6)]} ${date.dayOfMonth}."
                        }
                        "$day · ${canteen.name}"
                    } ?: s.notScheduled
                    val isSoon = f.next != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Plate(meal = f.mostRecent?.meals ?: f.next?.third?.meals, size = 60.dp, radius = 12.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(f.title, color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Row(
                                modifier = Modifier.padding(top = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (isSoon) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(palette.open))
                                }
                                Text(
                                    text = nextLabel,
                                    color = if (isSoon) palette.open else palette.sub,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = s.timesEaten(f.count) + (f.mostRecent?.servedOn?.let {
                                    runCatching { LocalDate.parse(it) }.getOrNull()?.let { d -> " · " + s.lastEaten(relativeAgo(today, d)) }
                                } ?: ""),
                                color = palette.sub,
                                fontSize = 10.sp,
                            )
                        }
                        Box(modifier = Modifier.size(28.dp).clickable { state.favoritesManager.toggleFavorite(f.title) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Star, null, tint = palette.amber, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (sideRows.isNotEmpty()) {
                    item(key = "sides-header") {
                        MenuSectionHeader(label = s.favoriteSidesAndDesserts)
                    }
                    items(sideRows, key = { "side-${it.first}" }) { (name, next) ->
                        val nextLabel = next?.let { (canteen, date) ->
                            val diff = (date.toEpochDays() - today.toEpochDays()).toInt()
                            val day = when (diff) {
                                0 -> s.today
                                1 -> s.tomorrow
                                else -> "${s.weekdaysShort[(date.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)]} ${date.dayOfMonth}."
                            }
                            "$day · ${canteen.name}"
                        } ?: s.notScheduled
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SidePhoto(name = name, modifier = Modifier.size(60.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Row(
                                    modifier = Modifier.padding(top = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (next != null) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(palette.open))
                                    }
                                    Text(
                                        text = nextLabel,
                                        color = if (next != null) palette.open else palette.sub,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            Box(modifier = Modifier.size(28.dp).clickable { state.favoritesManager.toggleSideFavorite(name) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Star, null, tint = palette.amber, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FavoriteRow(
    val title: String,
    val count: Int,
    val mostRecent: MealDate?,
    val next: Triple<Canteen, LocalDate, MealDate>?,
)
