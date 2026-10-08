package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.containsSide
import com.lkaesberg.mensaapp.data.Course
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.data.SideGroup
import com.lkaesberg.mensaapp.data.SideKind
import com.lkaesberg.mensaapp.i18n.LocalAppLocale
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.MonoNumericStyle

/**
 * Card for a side, salad or dessert counter: the counter name, its price,
 * and everything it offers as a compact dot list. These aren't dishes on
 * their own — at Mensa am Turm the "Stärkebeilage" is Senf-Kartoffeln,
 * Pommes frites, Tomatennudeln and Kräuterpüree, one of which you pick — so
 * every option gets the same row and there's no photo of whichever one
 * upstream happened to list first. Favourite sides carry a star.
 */
@Composable
fun CounterCard(
    mealDate: MealDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    priceText: String? = null,
    favoriteSides: Set<String> = emptySet(),
    /** Same contract as [MealCard]. */
    forceDeactivated: Boolean = false,
    forceActive: Boolean = false,
) {
    val locale = LocalAppLocale.current
    val isDeactivated = !forceActive && (mealDate.deactivatedAt != null || forceDeactivated)
    val options = remember(mealDate.id, mealDate.meals, locale) { MenuStructure.counterOptions(mealDate, locale) }
    // Favourites are keyed by the German name; line the English options up by
    // position when both lists match, otherwise compare what's shown.
    val favorites = remember(mealDate.id, mealDate.meals, locale, favoriteSides) {
        val german = if (locale == Locale.De) options else MenuStructure.counterOptions(mealDate, Locale.De)
        options.mapIndexed { i, shown ->
            favoriteSides.containsSide(if (german.size == options.size) german[i] else shown)
        }
    }
    OptionsCard(
        label = MenuStructure.displayCategory(mealDate.category),
        icon = counterIcon(MenuStructure.courseOf(mealDate), mealDate.category),
        options = options,
        favorites = favorites,
        priceText = priceText,
        deactivated = isDeactivated,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * A group of the day's sides ([MenuStructure.sideBoard]): a side counter with
 * the matching sides of the mains merged in, or the mains' sides of one kind
 * where there's no counter. Names are German — favourites and photos use them.
 */
@Composable
fun SideGroupCard(
    group: SideGroup,
    favoriteSides: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    priceText: String? = null,
    deactivated: Boolean = false,
) {
    OptionsCard(
        label = group.label,
        icon = when (group.kind) {
            SideKind.Starch -> Icons.Filled.RiceBowl
            SideKind.Vegetable -> Icons.Filled.Eco
            SideKind.Salad -> Icons.Filled.Grass
            SideKind.Dessert -> Icons.Filled.Icecream
        },
        options = group.items,
        favorites = group.items.map { favoriteSides.containsSide(it) },
        priceText = priceText,
        deactivated = deactivated,
        onClick = onClick,
        modifier = modifier,
    )
}

// Same shell as MealCard; the icon tile sits where a dish card has its photo.
@Composable
private fun OptionsCard(
    label: String,
    icon: ImageVector,
    options: List<String>,
    favorites: List<Boolean>,
    priceText: String?,
    deactivated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = MensaTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(16.dp))
            .alpha(if (deactivated) 0.5f else 1f)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.moss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.forest, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow(text = label, modifier = Modifier.weight(1f, fill = false))
                if (priceText != null) {
                    Text(
                        text = priceText,
                        color = palette.forestDark,
                        fontSize = 13.sp,
                        style = MonoNumericStyle,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEachIndexed { i, option ->
                    Row(verticalAlignment = Alignment.Top) {
                        // Fixed slot so dots and stars keep the text aligned.
                        Box(modifier = Modifier.padding(top = 4.dp).size(10.dp), contentAlignment = Alignment.Center) {
                            if (favorites.getOrElse(i) { false }) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = palette.amber, modifier = Modifier.size(10.dp))
                            } else {
                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(palette.sub))
                            }
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = option,
                            color = palette.ink,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Starch side, vegetables, salad or dessert — told apart at a glance. */
private fun counterIcon(course: Course, category: String): ImageVector = when {
    course == Course.Dessert -> Icons.Filled.Icecream
    course == Course.Salad -> Icons.Filled.Grass
    "gemüse" in category.lowercase() -> Icons.Filled.Eco
    else -> Icons.Filled.RiceBowl
}

/** Small left-aligned header between the mains and the side/dessert counters. */
@Composable
fun MenuSectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = MensaTheme.palette.sub,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 4.dp, top = 6.dp),
    )
}
