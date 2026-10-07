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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.containsFavorite
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.MonoNumericStyle
import com.lkaesberg.mensaapp.ui.components.DietPip
import com.lkaesberg.mensaapp.ui.components.Plate
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun DishStatsScreen(
    state: MealsAppState,
    onBack: () -> Unit,
) {
    val palette = MensaTheme.palette
    val scope = rememberCoroutineScope()
    val history by state.history.collectAsState()
    val canteen by state.selectedCanteen.collectAsState()
    val favoriteIds by state.favoritesManager.favorites.collectAsState()

    // Re-fire on every screen entry so the 90-day window is always fresh,
    // even if state.history was last populated by another screen with a
    // different sinceDays.
    LaunchedEffect(canteen?.id, Unit) {
        if (canteen != null) state.loadHistoryForSelected(scope, sinceDays = 90)
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val sortOptions = listOf("Häufigkeit", "Zuletzt", "A–Z", "Preis")
    var sort by remember { mutableStateOf(sortOptions.first()) }

    // Defensive past-only filter at the screen level. Repo already filters
    // `served_on < today`; this is belt-and-suspenders against any stale
    // shared-flow data leaking today/future occurrences into the count
    // and the "zuletzt" date.
    val pastHistory = remember(history, today) {
        history.mapNotNull { md ->
            val date = runCatching { LocalDate.parse(md.servedOn) }.getOrNull() ?: return@mapNotNull null
            if (date.toEpochDays() < today.toEpochDays()) md to date else null
        }
    }
    val dishes = remember(pastHistory) {
        pastHistory.groupBy { (md, _) -> md.meals?.cleanTitle ?: md.meals?.title.orEmpty() }
            .filter { it.key.isNotBlank() }
            .map { (title, pairs) ->
                val mostRecent = pairs.maxByOrNull { (_, d) -> d.toEpochDays() } ?: pairs.first()
                val (md, mostRecentDate) = mostRecent
                DishStat(
                    title = title,
                    icons = md.meals?.icons.orEmpty(),
                    category = MenuStructure.displayCategory(md.category),
                    count = pairs.size,
                    lastDate = mostRecentDate,
                    md = md,
                )
            }
    }

    val sorted = remember(dishes, sort) {
        when (sort) {
            "Häufigkeit" -> dishes.sortedByDescending { it.count }
            "Zuletzt" -> dishes.sortedBy { (today.toEpochDays() - it.lastDate.toEpochDays()).toInt() }
            "A–Z" -> dishes.sortedBy { it.title.lowercase() }
            else -> dishes.sortedBy { it.title.lowercase() }
        }
    }
    val maxCount = (dishes.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val totalServings = dishes.sumOf { it.count }
    val topTitle = dishes.maxByOrNull { it.count }?.title?.split(" ")?.firstOrNull().orEmpty()

    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(36.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = palette.ink, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Gerichte-Statistik", color = palette.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
                Text("Letzte 90 Tage · ${dishes.size} Gerichte", color = palette.sub, fontSize = 11.sp)
            }
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface)
                .border(1.dp, palette.hair, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(palette.forest))
            Text(canteen?.name.orEmpty(), color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("· vor 90 Tagen bis heute", color = palette.sub, fontSize = 11.sp)
        }
        // Summary tiles
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SummaryTile("EINZIGARTIG", dishes.size.toString(), palette, modifier = Modifier.weight(1f))
            SummaryTile("SERVIERUNGEN", totalServings.toString(), palette, modifier = Modifier.weight(1f))
            SummaryTile("TOP", topTitle, palette, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sortOptions.forEach { s ->
                val active = s == sort
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) palette.forest else Color.Transparent)
                        .border(1.dp, if (active) Color.Transparent else palette.hair, RoundedCornerShape(100.dp))
                        .clickable { sort = s }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s, color = if (active) Color.White else palette.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items@ for ((i, d) in sorted.withIndex()) {
                val barFraction = d.count.toFloat() / maxCount.toFloat()
                item(key = d.title) {
                    DishStatRow(
                        rank = i + 1,
                        dish = d,
                        barFraction = barFraction,
                        isFavorite = favoriteIds.containsFavorite(d.title) || favoriteIds.containsFavorite(d.md.meals?.title ?: ""),
                        today = today,
                        palette = palette,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, palette: com.lkaesberg.mensaapp.ui.MensaPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(label, color = palette.sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = palette.forestDark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun DishStatRow(
    rank: Int,
    dish: DishStat,
    barFraction: Float,
    isFavorite: Boolean,
    today: LocalDate,
    palette: com.lkaesberg.mensaapp.ui.MensaPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            Plate(meal = dish.md.meals, size = 48.dp, radius = 8.dp)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .padding(0.dp)
                    .clip(CircleShape)
                    .background(if (rank <= 3) palette.forest else Color.White)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rank.toString(),
                    color = if (rank <= 3) Color.White else palette.sub,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = dish.title,
                        color = palette.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (isFavorite) {
                        Text("★", color = palette.amber, fontSize = 10.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    dish.icons.take(2).forEach { kind -> DietPip(kind = kind, size = 12.dp) }
                }
            }
            Text(dish.category.uppercase(), color = palette.forest, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(palette.moss)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.forest)
                    )
                }
                Text("${dish.count}×", color = palette.forestDark, fontSize = 10.sp, style = MonoNumericStyle, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val ago = relativeAgo(today, dish.lastDate)
                val agoColor = if (ago == "heute") palette.open else palette.ink
                Text("Zuletzt: $ago", color = palette.sub, fontSize = 10.sp)
            }
        }
    }
}

private data class DishStat(
    val title: String,
    val icons: List<String>,
    val category: String,
    val count: Int,
    val lastDate: LocalDate,
    val md: com.lkaesberg.mensaapp.MealDate,
)
