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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.containsFavorite
import com.lkaesberg.mensaapp.containsSide
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.data.PriceResolver
import com.lkaesberg.mensaapp.data.UserRole
import com.lkaesberg.mensaapp.i18n.LocalAppLocale
import com.lkaesberg.mensaapp.i18n.LocalStrings
import com.lkaesberg.mensaapp.i18n.alternativesFor
import com.lkaesberg.mensaapp.i18n.sidesFor
import com.lkaesberg.mensaapp.i18n.titleFor
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.MonoNumericStyle
import com.lkaesberg.mensaapp.ui.components.AllergenChips
import com.lkaesberg.mensaapp.ui.components.DietPip
import com.lkaesberg.mensaapp.ui.components.PlateFill
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun MealDetailScreen(
    state: MealsAppState,
    dateId: String,
    onBack: () -> Unit,
) {
    val palette = MensaTheme.palette
    val mealsByDate by state.mealsByDate.collectAsState()
    val favoriteIds by state.favoritesManager.favorites.collectAsState()
    val sideFavorites by state.favoritesManager.sideFavorites.collectAsState()
    val history by state.history.collectAsState()
    val canteenInfo = state.selectedInfo()
    val userRole by state.userRole.collectAsState()
    val scope = rememberCoroutineScope()
    val selectedCanteen by state.selectedCanteen.collectAsState()
    // Force a fresh history load on every entry — the shared `state.history`
    // can carry stale data from another screen (e.g. AllMealsArchive uses a
    // 365-day window, so re-entering MealDetail without re-fetching could
    // surface a "zuletzt" that's older than necessary or, worse, picked up
    // before the repo's `lt today` filter was applied to the cached set).
    LaunchedEffect(selectedCanteen?.id, Unit) {
        if (selectedCanteen != null) state.loadHistoryForSelected(scope)
    }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val target = remember(mealsByDate, dateId) {
        mealsByDate.values.flatten().firstOrNull { it.id == dateId }
    }
    if (target == null) {
        Box(modifier = Modifier.fillMaxSize().background(palette.paper), contentAlignment = Alignment.Center) {
            Text("Gericht nicht gefunden.", color = palette.ink)
        }
        return
    }
    val enriched = remember(target.id) { MealEnrichment.enrich(target) }
    val key = enriched.cleanTitle.ifBlank { target.meals?.title ?: target.mealId }
    val isFav = favoriteIds.containsFavorite(key) || favoriteIds.containsFavorite(target.meals?.title ?: "")
    val priceTriple = remember(target.id, target.priceStudents, target.priceEmployees, target.priceGuests, canteenInfo?.slug) {
        PriceResolver.forMealDate(target, canteenInfo)
            ?.let { Triple(it.students, it.employees, it.guests) }
    }

    val baseHeroHeight = 320.dp
    val maxStretchDp = 280.dp
    val maxStretchPx = with(LocalDensity.current) { maxStretchDp.toPx() }
    val stretch = remember { Animatable(0f) }
    val stretchScope = rememberCoroutineScope()
    val nestedScrollConnection = remember(maxStretchPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // While stretched, an upward swipe (negative y) collapses the
                // hero before the list begins to scroll.
                if (source == NestedScrollSource.Drag && stretch.value > 0f && available.y < 0f) {
                    val consume = minOf(-available.y, stretch.value)
                    stretchScope.launch { stretch.snapTo(stretch.value - consume) }
                    return Offset(0f, -consume)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // At the top edge, a pull-down (positive y leftover) grows the hero.
                if (source == NestedScrollSource.Drag && available.y > 0f) {
                    val canGrow = (maxStretchPx - stretch.value).coerceAtLeast(0f)
                    val grow = minOf(available.y, canGrow)
                    if (grow > 0f) {
                        stretchScope.launch { stretch.snapTo(stretch.value + grow) }
                        return Offset(0f, grow)
                    }
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (stretch.value > 0f) {
                    stretch.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val stretchFraction = (stretch.value / maxStretchPx).coerceIn(0f, 1f)
    val gradientAlpha = 1f - stretchFraction

    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.paper),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            val stretchDp = with(LocalDensity.current) { stretch.value.toDp() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(baseHeroHeight + stretchDp),
            ) {
                PlateFill(meal = target.meals, modifier = Modifier.fillMaxSize())
                // Gradient overlay fades out as the user pulls down so the
                // image can be seen unobstructed.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(gradientAlpha)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                    palette.paper.copy(alpha = 0.95f)
                                ),
                            )
                        )
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.95f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = palette.ink, modifier = Modifier.size(20.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.95f))
                            .clickable { state.favoritesManager.toggleFavorite(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            null,
                            tint = if (isFav) palette.amber else palette.ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(palette.amber)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${MenuStructure.displayCategory(target.category).uppercase()} · ${if ((target.mealPeriod ?: "lunch").lowercase() == "afternoon") "NACHMITTAG" else "MITTAG"}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                    )
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                val locale = LocalAppLocale.current
                // A side/dessert counter is titled by the counter, not by
                // whichever of its options upstream happened to list first.
                val isCounter = MenuStructure.isCounter(target)
                Text(
                    text = if (isCounter) MenuStructure.displayCategory(target.category)
                    else target.meals?.titleFor(locale)?.takeIf { it.isNotBlank() }
                        ?: enriched.cleanTitle.ifBlank { target.meals?.title.orEmpty() },
                    color = palette.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    lineHeight = 28.sp,
                )
                val lastServed = remember(history, target.id, enriched.cleanTitle, today) {
                    val titleKey = enriched.cleanTitle.ifBlank { target.meals?.title ?: "" }
                    val legacy = target.meals?.title ?: ""
                    val todayEpoch = today.toEpochDays()
                    history.asSequence()
                        .filter { md ->
                            md.id != target.id && run {
                                val k = md.meals?.cleanTitle ?: md.meals?.title ?: ""
                                k == titleKey || md.meals?.title == legacy
                            }
                        }
                        .mapNotNull { runCatching { LocalDate.parse(it.servedOn) }.getOrNull() }
                        .filter { it.toEpochDays() < todayEpoch }
                        .maxByOrNull { it.toEpochDays() }
                }
                if (lastServed != null && !isCounter) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = palette.sub,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = (if (LocalAppLocale.current == com.lkaesberg.mensaapp.data.Locale.De)
                                "Zuletzt serviert "
                            else "Last served ") + relativeAgo(today, lastServed),
                            color = palette.sub,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                val ratingAvg = target.meals?.ratingAvg
                if (ratingAvg != null && ratingAvg > 0f) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = palette.amber,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = com.lkaesberg.mensaapp.ui.components.formatRating(ratingAvg) + " / 5,0",
                            color = palette.sub,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = MonoNumericStyle,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    target.meals?.icons.orEmpty().forEach { kind ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(palette.moss)
                                .border(1.dp, palette.hair, RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            DietPip(kind = kind, size = 14.dp)
                            Text(
                                text = com.lkaesberg.mensaapp.ui.DietColors.longLabel(kind),
                                color = palette.forestDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        // Sides — locale-aware (English variants from <essen2_eng>, with
        // German fallback) and falling back to the enrichment-derived list
        // for legacy rows where structured sides aren't populated. A side or
        // dessert counter lists what it offers instead: those aren't sides of
        // the headline item but alternatives to it.
        item {
            val locale = LocalAppLocale.current
            val strings = LocalStrings.current
            if (MenuStructure.isCounter(target)) {
                BulletList(strings.counterOptions, MenuStructure.counterOptions(target, locale))
            } else {
                // Real sides get a star; favourites are keyed by the German name,
                // lined up by position when the English list has the same length.
                // A buffet lists what's on it — not sides to pick, so no stars there.
                val isBuffet = MenuStructure.isPricedPer100g(target)
                val shown = target.meals?.sidesFor(locale).orEmpty().ifEmpty { enriched.sides }
                val german = target.meals?.sidesFor(Locale.De).orEmpty().ifEmpty { enriched.sides }
                BulletList(
                    label = strings.sides,
                    entries = shown,
                    favoriteKeys = shown.mapIndexed { i, side ->
                        (if (german.size == shown.size) german[i] else side).takeIf { !isBuffet && MenuStructure.isRealSide(it) }
                    },
                    favoriteSides = sideFavorites,
                    onToggleFavorite = { state.favoritesManager.toggleSideFavorite(it) },
                )
                BulletList(strings.alsoAtCounter, target.meals?.alternativesFor(locale).orEmpty())
            }
        }
        if (priceTriple != null) {
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (MenuStructure.isPricedPer100g(target)) LocalStrings.current.pricePer100g
                        else LocalStrings.current.price,
                        color = palette.sub,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        PriceCell(UserRole.Studierend.shortLabel, priceTriple.first, userRole == UserRole.Studierend, palette)
                        Box(modifier = Modifier.width(1.dp).height(48.dp).background(palette.hair))
                        PriceCell(UserRole.Bedienstet.shortLabel, priceTriple.second, userRole == UserRole.Bedienstet, palette)
                        Box(modifier = Modifier.width(1.dp).height(48.dp).background(palette.hair))
                        PriceCell(UserRole.Gast.shortLabel, priceTriple.third, userRole == UserRole.Gast, palette)
                    }
                }
            }
        }
        if (enriched.allergens.isNotEmpty() || enriched.additives.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = LocalStrings.current.allergens,
                        color = palette.sub,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    AllergenChips(allergens = enriched.allergens, additives = enriched.additives)
                }
            }
        }
        if (isFav) {
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.amberLight)
                        .border(1.dp, Color(0xFFEAD7A8), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(palette.amber),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Notifications, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Column {
                        Text(
                            "Wir erinnern dich",
                            color = palette.amberDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Du bekommst eine Push-Benachrichtigung, sobald dein Lieblings­gericht wieder auf dem Plan steht.",
                            color = palette.amberDark.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }

    } // wrapper Box
}

@Composable
private fun BulletList(
    label: String,
    entries: List<String>,
    /** Per entry: the side's favourite key, or null for no star (sauces, garnish). */
    favoriteKeys: List<String?> = emptyList(),
    favoriteSides: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
) {
    if (entries.isEmpty()) return
    val palette = MensaTheme.palette
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp)) {
        Text(
            text = label,
            color = palette.sub,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        entries.forEachIndexed { i, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(palette.forest))
                Text(entry, color = palette.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                val key = favoriteKeys.getOrNull(i)
                if (key != null) {
                    val isFavorite = favoriteSides.containsSide(key)
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onToggleFavorite(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (isFavorite) LocalStrings.current.unfavorite else LocalStrings.current.favorite,
                            tint = if (isFavorite) palette.amber else palette.sub,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (i < entries.size - 1) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.hair))
            }
        }
    }
}

@Composable
private fun PriceCell(label: String, value: String, highlight: Boolean, palette: com.lkaesberg.mensaapp.ui.MensaPalette) {
    val display = if (value.isBlank()) "—" else "$value €"
    val available = value.isNotBlank()
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = palette.sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = display,
            color = if (highlight && available) palette.forestDark else palette.sub,
            fontSize = if (highlight && available) 22.sp else 18.sp,
            fontWeight = FontWeight.ExtraBold,
            style = MonoNumericStyle,
        )
    }
}

