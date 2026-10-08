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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.containsSide
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.EnrichedMeal
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.i18n.LocalAppLocale
import com.lkaesberg.mensaapp.i18n.LocalStrings
import com.lkaesberg.mensaapp.i18n.alternativesFor
import com.lkaesberg.mensaapp.i18n.sidesFor
import com.lkaesberg.mensaapp.i18n.titleFor
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.MonoNumericStyle

/**
 * Compact horizontal meal card used by the daily feed.
 * 88×88 image left, content right (eyebrow + title + sides + diet pips + price).
 *
 * @param favoriteHint optional inline note like "Favorit · auch Do" rendered
 *                     in an amber chip below the meta row when [isFavorite].
 * @param priceText pre-formatted price string, e.g. "4,20 €" — null hides the price.
 */
@Composable
fun MealCard(
    mealDate: MealDate,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    priceText: String? = null,
    favoriteHint: String? = null,
    enriched: EnrichedMeal = MealEnrichment.enrich(mealDate),
    /** Force the card into the deactivated visual state (e.g. canteen past closing). */
    forceDeactivated: Boolean = false,
    /**
     * Override that wins over both [forceDeactivated] and the row's own
     * `deactivated_at`. Used when today's canteen hasn't opened yet — the
     * scraper may have already deactivated some rows, but the menu can
     * still change upstream, so dimming them now is misleading.
     */
    forceActive: Boolean = false,
    /** Favourite side dishes; a dish served with one gets a star chip naming it. */
    favoriteSides: Set<String> = emptySet(),
) {
    val palette = MensaTheme.palette
    val isDeactivated = !forceActive && (mealDate.deactivatedAt != null || forceDeactivated)
    val s = LocalStrings.current
    val locale = LocalAppLocale.current
    val titleText = mealDate.meals?.titleFor(locale)?.takeIf { it.isNotBlank() }
        ?: enriched.cleanTitle.ifBlank { mealDate.meals?.title.orEmpty() }
    val sidesText = mealDate.meals?.sidesFor(locale).orEmpty()
        .ifEmpty { enriched.sides }
    val withWord = if (locale == com.lkaesberg.mensaapp.data.Locale.De) "mit" else "with"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(16.dp))
            .alpha(if (isDeactivated) 0.5f else 1f)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Plate(meal = mealDate.meals, size = 88.dp, radius = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow(
                    text = MenuStructure.displayCategory(mealDate.category),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onFavoriteToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = s.unfavorite,
                            tint = palette.amber,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = s.favorite,
                            tint = palette.sub,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = titleText,
                color = palette.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sidesText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$withWord " + sidesText.joinToString(", "),
                    color = palette.sub,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Buffet counters (Beats&Bites) list several "<dish> auf <base>" pairs.
            val moreAtCounter = mealDate.meals?.alternativesFor(locale).orEmpty().size
            if (moreAtCounter > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = s.moreAtCounter(moreAtCounter),
                    color = palette.forest,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rating = mealDate.meals?.ratingAvg
                    if (rating != null && rating > 0f) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = palette.amber,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = formatRating(rating),
                                color = palette.sub,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = MonoNumericStyle,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        mealDate.meals?.icons.orEmpty().forEach { kind ->
                            DietPip(kind = kind, size = 18.dp)
                        }
                    }
                }
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
            // Matched on the German names, which side favourites are keyed by.
            val favoriteSidesHere = remember(mealDate.meals, favoriteSides) {
                mealDate.meals?.sidesFor(Locale.De).orEmpty()
                    .filter { MenuStructure.isRealSide(it) && favoriteSides.containsSide(it) }
            }
            if (favoriteSidesHere.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.amberLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = palette.amberDark,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = favoriteSidesHere.joinToString(", "),
                        color = palette.amberDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isFavorite && favoriteHint != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.amberLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = palette.amberDark,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = favoriteHint,
                        color = palette.amberDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// "4,2" / "4,3" — one decimal, comma separator (matches German conventions
// and the price text style). Inputs above 5 (impossible per spec) are
// clamped so a glitched value can't render as "12,3".
internal fun formatRating(value: Float): String {
    val clamped = value.coerceIn(0f, 5f)
    val tenths = (clamped * 10f + 0.5f).toInt()
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole,$frac"
}
