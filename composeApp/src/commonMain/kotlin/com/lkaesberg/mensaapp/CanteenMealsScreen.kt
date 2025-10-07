package com.lkaesberg.mensaapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun CanteenMealsScreen(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { MealsRepository(SupabaseProvider.client().postgrest) }

    // Persistent settings storage
    val settings = remember { Settings() }

    var canteens by remember { mutableStateOf<List<Canteen>>(emptyList()) }
    var selectedCanteen by remember { mutableStateOf<Canteen?>(null) }
    var mealsByDate by remember { mutableStateOf<Map<LocalDate, List<MealDate>>>(emptyMap()) }
    var expanded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    // Get current local date once for labelling and initial page selection
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = {
        val canteen = selectedCanteen ?: return@rememberPullRefreshState
        coroutineScope.launch {
            refreshing = true
            mealsByDate = repository.getMealsForCanteen(canteen.id)
            refreshing = false
        }
    })

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            canteens = repository.getCanteens()
            if (canteens.isNotEmpty()) {
                val savedId = settings.getStringOrNull("selected_canteen_id")
                selectedCanteen = canteens.find { it.id == savedId } ?: canteens.first()
            }
        }
    }

    LaunchedEffect(selectedCanteen) {
        val canteen = selectedCanteen ?: return@LaunchedEffect
        mealsByDate = repository.getMealsForCanteen(canteen.id)
    }

    Box(modifier = modifier
        .fillMaxSize()
        .pullRefresh(pullRefreshState)) {

        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(title = {
                Text(text = "Mensa Menu", style = MaterialTheme.typography.titleLarge)
            })

            Spacer(Modifier.height(8.dp))

            // Canteen selector
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Canteen:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Box {
                    Button(onClick = { expanded = true }) {
                        Text(selectedCanteen?.name ?: "Select")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        canteens.forEach { canteen ->
                            DropdownMenuItem(text = { Text(canteen.name) }, onClick = {
                                selectedCanteen = canteen
                                settings.putString("selected_canteen_id", canteen.id)
                                expanded = false
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (mealsByDate.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No meals available.")
                }
            } else {
                val dates = mealsByDate.keys.sorted()

                // Figure out which page corresponds to "today" (fallback to first page)
                val todayIndex = remember(dates) {
                    val idx = dates.indexOf(today)
                    if (idx >= 0) idx else 0
                }
                // Pager is experimental
                @OptIn(ExperimentalFoundationApi::class)
                val pagerState = rememberPagerState(
                    initialPage = todayIndex,
                    pageCount = { dates.size }
                )

                val coroutineScopePager = rememberCoroutineScope()

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val date = dates[page]
                    val list = mealsByDate[date].orEmpty()

                    var menuExpanded by remember { mutableStateOf(false) }

                    val headerRow: @Composable () -> Unit = {
                        val formatted = remember(date) {
                            val dow = date.dayOfWeek
                            val day = date.dayOfMonth.toString().padStart(2, '0')
                            val month = date.monthNumber.toString().padStart(2, '0')
                            val base = "${dow.name.lowercase().replaceFirstChar { it.titlecase() }.take(3)} $day.$month"

                            when (today.daysUntil(date)) {
                                0 -> "$base (Today)"
                                1 -> "$base (Tomorrow)"
                                -1 -> "$base (Yesterday)"
                                else -> base
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val arrowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

                            if (page > 0) {
                                Text("‹", color = arrowColor, style = MaterialTheme.typography.titleMedium)
                            } else {
                                Spacer(Modifier.width(16.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatted,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.clickable { menuExpanded = true },
                                    textAlign = TextAlign.Center
                                )

                                // Menu anchored to the date Text (center of the row)
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    dates.forEachIndexed { index, d ->
                                        val display = d.let { local ->
                                            val dow = local.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }.take(3)
                                            val day = local.dayOfMonth.toString().padStart(2, '0')
                                            val month = local.monthNumber.toString().padStart(2, '0')
                                            val base = "$dow $day.$month"

                                            when (today.daysUntil(local)) {
                                                0 -> "$base (Today)"
                                                1 -> "$base (Tomorrow)"
                                                -1 -> "$base (Yesterday)"
                                                else -> base
                                            }
                                        }
                                        DropdownMenuItem(text = { Text(display) }, onClick = {
                                            menuExpanded = false
                                            coroutineScopePager.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        })
                                    }
                                }
                            }

                            if (page < dates.lastIndex) {
                                Text("›", color = arrowColor, style = MaterialTheme.typography.titleMedium)
                            } else {
                                Spacer(Modifier.width(16.dp))
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        headerRow()
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            items(list) { mealDate ->
                                MealRow(mealDate)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(dates.size) { index ->
                        val color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Text("← swipe →", style = MaterialTheme.typography.bodySmall, modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp))
            }
        }

        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun MealRow(mealDate: MealDate) {
    val meal = mealDate.meals
    val title = meal?.title ?: "Meal"

    // URL resolved based on image_path or fallback to placeholder
    val imageUrl = remember(meal?.imagePath) {
        val base = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/"
        val imagePath = meal?.imagePath
        val imagePathGeneric = meal?.imagePathGeneric
        val path = "mensa-food/" + (imagePath ?: imagePathGeneric ?: "mensa.png")
        base + path
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            var imageExpanded by remember { mutableStateOf(false) }
            val imageSize by animateDpAsState(
                targetValue = if (imageExpanded) 160.dp else 80.dp,
                animationSpec = tween(durationMillis = 300)
            )
            // First row: image + title / category / icons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Image on the left
                KamelImage(
                    resource = asyncPainterResource(data = imageUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(imageSize)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { imageExpanded = !imageExpanded }
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title and icons in a single row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))

                        meal?.icons?.takeIf { it.isNotEmpty() }?.forEach { iconKey ->
                            val emoji = iconEmoji[iconKey.lowercase()] ?: "❓"
                            Text(emoji, modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    AssistChip(
                        onClick = {},
                        label = { Text(mealDate.category) }
                    )
                }
            }

            // Second row: full description (and optional note)
            Spacer(Modifier.height(8.dp))

            Text(meal?.fullText ?: "", style = MaterialTheme.typography.bodyMedium)

            mealDate.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Simple icon → emoji mapping for quick visualization
private val iconEmoji = mapOf(
    "vegetarisch" to "🥕",
    "vegan" to "🥗",
    "fleisch" to "🥩",
    "strohschwein" to "🐷",
    "leinetalerrind" to "🐄",
    "fisch" to "🐟",
) 