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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.MonoNumericStyle
import com.lkaesberg.mensaapp.ui.components.DietPip
import com.lkaesberg.mensaapp.ui.components.Plate
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn

@Composable
fun SearchScreen(
    state: MealsAppState,
    onBack: () -> Unit,
    onOpenMealDetail: (MealDate) -> Unit = {},
) {
    val palette = MensaTheme.palette
    val mealsByDate by state.mealsByDate.collectAsState()
    val selectedCanteen by state.selectedCanteen.collectAsState()
    var query by remember { mutableStateOf("") }
    val activeFilters = remember { mutableStateOf<Set<String>>(emptySet()) }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val filtered = remember(mealsByDate, query, activeFilters.value) {
        val all = mealsByDate.flatMap { (d, list) -> list.map { d to it } }
            .filter { (_, md) ->
                val title = md.meals?.cleanTitle ?: md.meals?.title ?: ""
                if (title.contains(query, ignoreCase = true)) return@filter true
                val sides = md.meals?.sides.orEmpty()
                sides.any { it.contains(query, ignoreCase = true) }
            }
            .filter { (_, md) ->
                if (activeFilters.value.isEmpty()) true
                else {
                    val icons = md.meals?.icons.orEmpty().map { it.lowercase() }
                    activeFilters.value.any { f ->
                        when (f) {
                            "vegetarisch" -> "vegetarisch" in icons || "vegan" in icons
                            "fleisch" -> "fleisch" in icons || "strohschwein" in icons
                            else -> f in icons
                        }
                    }
                }
            }
        all.sortedBy { (d, _) -> d }
    }

    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = palette.ink, modifier = Modifier.size(20.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.hair, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = palette.sub, modifier = Modifier.size(16.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(palette.forest),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("Suchen…", color = palette.sub.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Box(modifier = Modifier.clickable { query = "" }) {
                        Icon(Icons.Filled.Close, null, tint = palette.sub, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        // Active filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("vegan", "vegetarisch", "fleisch", "fisch").forEach { kind ->
                val active = kind in activeFilters.value
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) palette.forest else palette.surface)
                        .border(1.dp, if (active) Color.Transparent else palette.hair, RoundedCornerShape(100.dp))
                        .clickable {
                            activeFilters.value = if (active) activeFilters.value - kind else activeFilters.value + kind
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    DietPip(kind = kind, size = 13.dp)
                    Text(
                        text = com.lkaesberg.mensaapp.ui.DietColors.longLabel(kind),
                        color = if (active) Color.White else palette.ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${filtered.size} ERGEBNISSE · DIESE WOCHE",
            color = palette.sub,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            var previousDate: LocalDate? = null
            filtered.forEach { (d, md) ->
                if (d != previousDate) {
                    item(key = "header-$d") { DaySeparator(date = d, today = today) }
                    previousDate = d
                }
                item(key = "meal-${md.id}") {
                    val enriched = MealEnrichment.enrich(md)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.hair, RoundedCornerShape(12.dp))
                            .clickable { onOpenMealDetail(md) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Plate(meal = md.meals, size = 64.dp, radius = 10.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = MenuStructure.displayCategory(md.category).uppercase(),
                                color = palette.forest,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            )
                            Text(
                                text = enriched.cleanTitle.ifBlank { md.meals?.title.orEmpty() },
                                color = palette.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 16.sp,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Filled.Place, null, tint = palette.sub, modifier = Modifier.size(10.dp))
                                Text(selectedCanteen?.name.orEmpty(), color = palette.sub, fontSize = 11.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            md.meals?.icons.orEmpty().forEach { kind -> DietPip(kind = kind, size = 11.dp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySeparator(date: LocalDate, today: LocalDate) {
    val palette = MensaTheme.palette
    val diff = (date.toEpochDays() - today.toEpochDays()).toInt()
    val weekdays = listOf("MONTAG", "DIENSTAG", "MITTWOCH", "DONNERSTAG", "FREITAG", "SAMSTAG", "SONNTAG")
    val months = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
    val primary = when (diff) {
        0 -> "HEUTE"
        1 -> "MORGEN"
        else -> weekdays.getOrElse((date.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)) { "" }
    }
    val sub = "${date.dayOfMonth}. ${months[(date.monthNumber - 1).coerceIn(0, 11)]}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
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
            text = sub,
            color = palette.sub,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
