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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.data.CanteenInfo
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.data.ResolvedPrice
import com.lkaesberg.mensaapp.data.UserRole
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.MonoNumericStyle
import com.lkaesberg.mensaapp.ui.components.Eyebrow
import com.lkaesberg.mensaapp.ui.components.OccupancyChip
import com.lkaesberg.mensaapp.ui.components.Plate
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun CanteenDetailScreen(
    state: MealsAppState,
    slug: String,
    onBack: () -> Unit,
) {
    val palette = MensaTheme.palette
    val info = remember(slug) { CanteenStaticData.all.firstOrNull { it.slug == slug } }
    val canteens by state.canteens.collectAsState()
    val prices by state.canteenPrices.collectAsState()
    val mealsByDate by state.mealsByDate.collectAsState()
    val userRole by state.userRole.collectAsState()
    val occupancyMap by state.occupancy.collectAsState()

    val canteen = remember(info, canteens) {
        canteens.firstOrNull {
            val canteenInfo = CanteenStaticData.matchFor(it.name)
            canteenInfo?.slug == slug
        }
    }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val todaysMeals = mealsByDate[today].orEmpty().take(3)
    val isOpen = canteen?.let { state.openNow(it) } ?: false

    if (info == null) {
        Box(modifier = Modifier.fillMaxSize().background(palette.paper), contentAlignment = Alignment.Center) {
            Text("Mensa nicht gefunden.", color = palette.ink)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.paper),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            // Hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.linearGradient(listOf(palette.forest, palette.forestDeep))
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.95f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = palette.ink, modifier = Modifier.size(18.dp))
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "STANDORT · ZENTRALCAMPUS",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = info.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                    )
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.Place, null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Text(info.address, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            // Status row card overlapping the hero
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-18).dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                StatusCell("STATUS", if (isOpen) "Offen" else "Geschlossen", if (isOpen) palette.open else palette.closed, dot = true)
                CellDivider()
                StatusCell("SCHLIESST", canteen?.let { state.closesAt(it) } ?: "—", palette.ink)
                CellDivider()
                StatusCell("HEUTE", "${todaysMeals.size} Gerichte", palette.forestDark)
            }
        }
        // Live "Auslastung" snapshot from /api/frequenz. Only shown while
        // the canteen is open — values are stale outside opening hours.
        canteen?.id?.let { canteenId ->
            val occupancy = occupancyMap[canteenId]
            if (isOpen) {
                item {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        OccupancyChip(occupancy = occupancy, isOpen = true)
                    }
                }
            }
        }
        item {
            // Hours card
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Schedule, null, tint = palette.forest, modifier = Modifier.size(14.dp))
                    Text("Öffnungszeiten", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                val labels = com.lkaesberg.mensaapp.i18n.LocalStrings.current.weekdaysShort
                val closedLabel = com.lkaesberg.mensaapp.i18n.LocalStrings.current.closedLabel
                val hourLines = canteen?.let { state.hoursLinesFor(it, labels, closedLabel) }
                    ?: com.lkaesberg.mensaapp.data.HoursDisplay.fromStatic(info.hours)
                hourLines.forEachIndexed { i, h ->
                    if (i > 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.hair))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(h.daysLabel, color = palette.sub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(h.timeLabel, color = palette.ink, fontSize = 12.sp, style = MonoNumericStyle, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            // Prices
            val displayPrices = if (prices.isNotEmpty()) {
                prices.map {
                    Triple(
                        it.category,
                        listOf(
                            ResolvedPrice.sanitize(it.priceStudents),
                            ResolvedPrice.sanitize(it.priceEmployees),
                            ResolvedPrice.sanitize(it.priceGuests),
                        ),
                        false,
                    )
                }
            } else {
                info.fallbackPrices.map { Triple(it.category, listOf(it.students, it.employees, it.guests), true) }
            }
            if (displayPrices.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text("Preise", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Text("", modifier = Modifier.weight(1.4f), fontSize = 11.sp)
                        UserRole.entries.forEach { role ->
                            val isActive = role == userRole
                            Text(
                                text = role.shortLabel,
                                color = if (isActive) palette.forestDark else palette.sub,
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                modifier = Modifier.weight(0.8f),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    displayPrices.forEach { (cat, vals, fb) ->
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.hair))
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                text = cat,
                                color = palette.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1.4f),
                            )
                            vals.forEachIndexed { idx, v ->
                                val isActive = idx == userRole.priceColumnIndex && v.isNotBlank()
                                val display = if (v.isBlank()) "—" else "$v €"
                                Text(
                                    text = display,
                                    color = if (isActive) palette.forestDark else palette.sub,
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                    style = MonoNumericStyle,
                                    modifier = Modifier.weight(0.8f),
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Heute auf dem Plan", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${todaysMeals.size} ›", color = palette.forest, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        items(todaysMeals, key = { it.id }) { md ->
            val enr = MealEnrichment.enrich(md)
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.hair, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Plate(meal = md.meals, size = 48.dp, radius = 10.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Eyebrow(text = MenuStructure.displayCategory(md.category))
                    Text(
                        text = enr.cleanTitle.ifBlank { md.meals?.title.orEmpty() },
                        color = palette.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCell(label: String, value: String, color: Color, dot: Boolean = false) {
    val palette = MensaTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = palette.sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (dot) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            }
            Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CellDivider() {
    val palette = MensaTheme.palette
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(palette.hair)
    )
}

