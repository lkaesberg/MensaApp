package com.lkaesberg.mensaapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.containsSide
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.data.PriceResolver
import com.lkaesberg.mensaapp.i18n.LocalStrings
import com.lkaesberg.mensaapp.i18n.sideKindLabel
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.MonoNumericStyle
import com.lkaesberg.mensaapp.ui.components.EmptyState
import com.lkaesberg.mensaapp.ui.components.Eyebrow
import com.lkaesberg.mensaapp.ui.components.MTopBar
import com.lkaesberg.mensaapp.ui.components.SidePhoto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * Every side on a day's plan, each with its own photo and a star, grouped
 * into Stärkebeilage / Gemüsebeilage / Salat: the side counters (Mensa am
 * Turm) with the real sides of all mains sorted in — sides can be combined
 * freely. With [desserts], the same for the dessert counters' options.
 * Opened from the side and dessert cards in the feed.
 */
@Composable
fun SidesScreen(
    state: MealsAppState,
    date: String,
    onBack: () -> Unit,
    desserts: Boolean = false,
) {
    val palette = MensaTheme.palette
    val s = LocalStrings.current
    val mealsByDate by state.mealsByDate.collectAsState()
    val sideFavorites by state.favoritesManager.sideFavorites.collectAsState()
    val canteen by state.selectedCanteen.collectAsState()
    val userRole by state.userRole.collectAsState()
    val day = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }
    val groups = remember(mealsByDate, day, s, desserts) {
        val meals = day?.let { mealsByDate[it] }.orEmpty()
        if (desserts) MenuStructure.dessertBoard(meals) else MenuStructure.sideBoard(meals) { s.sideKindLabel(it) }
    }
    val dayLabel = day?.let {
        "${s.weekdaysLong[(it.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)]}, ${it.dayOfMonth}. ${s.monthsShort[(it.monthNumber - 1).coerceIn(0, 11)]}"
    }

    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        MTopBar(
            title = if (desserts) s.dessertsTitle else s.sidesTitle,
            subtitle = listOfNotNull(canteen?.name, dayLabel).joinToString(" · "),
            onBack = onBack,
        )
        if (groups.isEmpty()) {
            EmptyState(title = if (desserts) s.noDessertsListed else s.noSidesListed)
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEachIndexed { gi, group ->
                item(key = "header-$gi") {
                    val info = state.selectedInfo()
                    val price = (group.counter?.let { PriceResolver.forMealDate(it, info) }
                        ?: PriceResolver.forSideKind(group.kind, info))
                        ?.textFor(userRole)
                        ?.takeIf { it.isNotBlank() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, top = if (gi == 0) 4.dp else 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Eyebrow(text = group.label)
                        if (price != null) {
                            Text(
                                text = price,
                                color = palette.forestDark,
                                fontSize = 13.sp,
                                style = MonoNumericStyle,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                // One card per group, a row per option.
                item(key = "group-$gi") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.hair, RoundedCornerShape(16.dp)),
                    ) {
                        group.items.forEachIndexed { i, name ->
                            if (i > 0) {
                                // Inset to the text, past the photo.
                                Box(
                                    modifier = Modifier
                                        .padding(start = 104.dp)
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(palette.hair),
                                )
                            }
                            SideRow(
                                name = name,
                                isFavorite = sideFavorites.containsSide(name),
                                onToggleFavorite = { state.favoritesManager.toggleSideFavorite(name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideRow(
    name: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val palette = MensaTheme.palette
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SidePhoto(name = name, modifier = Modifier.size(80.dp), radius = 12.dp)
        Text(
            text = name,
            color = palette.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            maxLines = 3, // dessert names are whole descriptions
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (isFavorite) s.unfavorite else s.favorite,
                tint = if (isFavorite) palette.amber else palette.sub,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
