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
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.Canteen
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.containsFavorite
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.i18n.LocalAppLocale
import com.lkaesberg.mensaapp.i18n.LocalStrings
import com.lkaesberg.mensaapp.i18n.titleFor
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.components.MTopBar
import com.lkaesberg.mensaapp.ui.components.Plate
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn

@Composable
fun UpcomingFavoritesScreen(
    state: MealsAppState,
    onBack: () -> Unit,
    onOpenMealDetail: (MealDate) -> Unit,
) {
    val palette = MensaTheme.palette
    val scope = rememberCoroutineScope()
    val favoriteIds by state.favoritesManager.favorites.collectAsState()
    val upcomingMap by state.upcomingAcrossCanteens.collectAsState()
    val disabledCanteenIds by state.disabledCanteenIds.collectAsState()

    LaunchedEffect(Unit) {
        state.loadUpcomingAcrossCanteens(scope)
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val groupedByDate: List<Pair<LocalDate, List<Pair<Canteen, MealDate>>>> =
        remember(favoriteIds, upcomingMap, today, disabledCanteenIds) {
            upcomingMap.entries
                .asSequence()
                .flatMap { (canteenAndDate, meals) ->
                    val (canteen, date) = canteenAndDate
                    if (date < today || canteen.id in disabledCanteenIds) emptySequence()
                    else meals.asSequence()
                        .filter { md -> md.matchesAnyFavorite(favoriteIds) }
                        .map { Triple(canteen, date, it) }
                }
                .groupBy({ it.second }, { it.first to it.third })
                .entries
                .sortedBy { it.key }
                .map { entry ->
                    entry.key to entry.value.sortedWith(
                        compareBy({ it.first.name }, { it.second.category }),
                    )
                }
        }

    val totalHits = groupedByDate.sumOf { it.second.size }

    val s = LocalStrings.current
    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        MTopBar(
            title = s.upcomingFavorites,
            subtitle = when {
                favoriteIds.isEmpty() -> s.noFavoritesYet
                totalHits == 0 -> s.nothingPlanned
                else -> "$totalHits · ${groupedByDate.size} ${if (groupedByDate.size == 1) s.day else s.days}"
            },
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
        if (groupedByDate.isEmpty()) {
            EmptyContent(hasFavorites = favoriteIds.isNotEmpty())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groupedByDate.forEach { (date, hits) ->
                    item(key = "header-$date") {
                        DayHeader(date = date, today = today, count = hits.size)
                    }
                    items(
                        items = hits,
                        key = { (canteen, md) -> "$date-${canteen.id}-${md.id}" },
                    ) { (canteen, md) ->
                        UpcomingFavoriteRow(
                            md = md,
                            canteen = canteen,
                            onClick = { onOpenMealDetail(md) },
                        )
                    }
                    item(key = "spacer-$date") { Spacer(Modifier.height(6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(hasFavorites: Boolean) {
    val palette = MensaTheme.palette
    val s = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("☆", fontSize = 36.sp, color = palette.sub)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasFavorites) s.noFavoritesPlanned else s.noFavoritesYet,
            color = palette.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hasFavorites) s.checkBackHint else s.noFavoritesHint,
            color = palette.sub,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, count: Int) {
    val palette = MensaTheme.palette
    val diff = (date.toEpochDays() - today.toEpochDays()).toInt()
    val s = LocalStrings.current
    val weekday = s.weekdaysLong.getOrElse((date.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)) { "" }
    val day = "${date.dayOfMonth}. ${s.monthsShort[(date.monthNumber - 1).coerceIn(0, 11)]}"
    val primary = when (diff) {
        0 -> s.today.uppercase()
        1 -> s.tomorrow.uppercase()
        else -> weekday.uppercase()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = primary,
            color = palette.forest,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(palette.sub))
        Text(
            text = day,
            color = palette.sub,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count",
            color = palette.sub,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun UpcomingFavoriteRow(
    md: MealDate,
    canteen: Canteen,
    onClick: () -> Unit,
) {
    val palette = MensaTheme.palette
    val enriched = remember(md.id) { MealEnrichment.enrich(md) }
    val locale = LocalAppLocale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Plate(meal = md.meals, size = 56.dp, radius = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = md.meals?.titleFor(locale)?.takeIf { it.isNotBlank() }
                    ?: enriched.cleanTitle.ifBlank { md.meals?.title ?: "" },
                color = palette.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = palette.sub,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = canteen.name,
                    color = palette.sub,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (md.category.isNotBlank()) {
                    Text("·", color = palette.sub, fontSize = 11.sp)
                    Text(
                        text = MenuStructure.displayCategory(md.category),
                        color = palette.sub,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = palette.amber,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun MealDate.matchesAnyFavorite(favorites: Set<String>): Boolean {
    val cleanTitle = meals?.cleanTitle ?: meals?.title ?: ""
    val legacyTitle = meals?.title ?: ""
    return favorites.containsFavorite(cleanTitle) || favorites.containsFavorite(legacyTitle)
}
