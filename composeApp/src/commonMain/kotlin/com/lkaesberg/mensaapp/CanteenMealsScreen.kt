package com.lkaesberg.mensaapp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.russhwolf.settings.Settings
import io.github.jan.supabase.postgrest.postgrest
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun CanteenMealsScreen(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { MealsRepository(SupabaseProvider.client().postgrest) }
    val settings = remember { Settings() }
    val favoritesManager = remember { FavoritesManager(settings) }
    val favoriteIds by favoritesManager.favorites.collectAsState()

    // Data State
    var canteens by remember { mutableStateOf<List<Canteen>>(emptyList()) }
    var selectedCanteen by remember { mutableStateOf<Canteen?>(null) }
    var mealsByDate by remember { mutableStateOf<Map<LocalDate, List<MealDate>>>(emptyMap()) }
    var refreshing by remember { mutableStateOf(false) }

    // UI State
    var searchQuery by remember { mutableStateOf("") }
    var selectedDietaryFilters by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSearch by remember { mutableStateOf(false) }
    var showCanteenSheet by remember { mutableStateOf(false) }
    
    // Automatically switch to weekly (list) view when searching
    val isWeeklyView by remember(showSearch, searchQuery) {
        derivedStateOf { showSearch || searchQuery.isNotEmpty() }
    }

    // Time Logic
    val systemTz = remember { TimeZone.currentSystemDefault() }
    val now = remember { Clock.System.now().toLocalDateTime(systemTz) }
    val today = remember(now) { now.date }
    val initialDate = remember(now) {
        if (now.hour > 14) today.plus(1, DateTimeUnit.DAY) else today
    }

    // Data Loading
    LaunchedEffect(Unit) {
        canteens = repository.getCanteens()
        if (canteens.isNotEmpty()) {
            val savedId = settings.getStringOrNull("selected_canteen_id")
            selectedCanteen = canteens.find { it.id == savedId } ?: canteens.first()
        }
    }

    LaunchedEffect(selectedCanteen) {
        val canteen = selectedCanteen ?: return@LaunchedEffect
        refreshing = true
        mealsByDate = repository.getMealsForCanteen(canteen.id)
        refreshing = false
    }

    val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = {
        val canteen = selectedCanteen ?: return@rememberPullRefreshState
        coroutineScope.launch {
            refreshing = true
            mealsByDate = repository.getMealsForCanteen(canteen.id)
            refreshing = false
        }
    })

    // Derived Data
    val dates = remember(mealsByDate) { mealsByDate.keys.sorted() }
    val initialPageIndex = remember(dates, initialDate) {
        val idx = dates.indexOf(initialDate)
        if (idx >= 0) idx else dates.indexOfFirst { it > initialDate }.takeIf { it >= 0 } ?: 0
    }

    fun filterAndSortMeals(meals: List<MealDate>): List<MealDate> {
        var filtered = meals
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery.lowercase()
            filtered = filtered.filter {
                (it.meals?.title?.lowercase()?.contains(query) == true) ||
                (it.meals?.fullText?.lowercase()?.contains(query) == true)
            }
        }
        if (selectedDietaryFilters.isNotEmpty()) {
            filtered = filtered.filter { mealDate ->
                val mealIcons = mealDate.meals?.icons?.map { it.lowercase() } ?: emptyList()
                selectedDietaryFilters.any { filter -> mealIcons.contains(filter) }
            }
        }
        return filtered
    }

    fun separateMealsByTime(meals: List<MealDate>): Pair<List<MealDate>, List<MealDate>> {
        val lunchMeals = mutableListOf<MealDate>()
        val afternoonMeals = mutableListOf<MealDate>()

        meals.forEach { meal ->
            val note = meal.note?.lowercase() ?: ""
            val isAfternoonOnly = note.contains("nachmittag")
            val isAllTime = note.isBlank()

            if (isAfternoonOnly) {
                // Only afternoon
                afternoonMeals.add(meal)
            } else if (isAllTime) {
                // Available at all times - add to both
                lunchMeals.add(meal)
                afternoonMeals.add(meal)
            } else {
                // Lunch only
                lunchMeals.add(meal)
            }
        }

        val lunchSorted = lunchMeals.sortedWith(compareBy(
            { !favoriteIds.contains(it.meals?.title) },
            {
                val cat = it.category.lowercase()
                if (cat.contains("dessert") || cat.contains("nachtisch")) "zzz" else cat
            }
        ))

        val afternoonSorted = afternoonMeals.sortedWith(compareBy(
            { !favoriteIds.contains(it.meals?.title) },
            {
                val cat = it.category.lowercase()
                if (cat.contains("dessert") || cat.contains("nachtisch")) "zzz" else cat
            }
        ))

        return Pair(lunchSorted, afternoonSorted)
    }

    Scaffold(
        topBar = {
            MensaTopBar(
                title = selectedCanteen?.name ?: "Mensa",
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                onCanteenClick = { showCanteenSheet = true },
                onSearchClick = { showSearch = !showSearch }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                AnimatedVisibility(visible = showSearch) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = { showSearch = false; searchQuery = "" }
                    )
                }

                // Filters
                if (mealsByDate.isNotEmpty()) {
                    FilterRow(
                        selectedFilters = selectedDietaryFilters,
                        onFilterChange = { filter ->
                            selectedDietaryFilters = if (filter in selectedDietaryFilters) {
                                selectedDietaryFilters - filter
                            } else {
                                selectedDietaryFilters + filter
                            }
                        },
                        onClear = { selectedDietaryFilters = emptySet() }
                    )
                }

                if (dates.isEmpty() && !refreshing) {
                    EmptyState()
                } else if (dates.isNotEmpty()) {
                    if (isWeeklyView) {
                        WeeklyView(
                            dates = dates,
                            mealsByDate = mealsByDate,
                            favoriteIds = favoriteIds,
                            onToggleFavorite = { favoritesManager.toggleFavorite(it) },
                            filterMeals = ::filterAndSortMeals
                        )
                    } else {
                        DailyView(
                            dates = dates,
                            initialPageIndex = initialPageIndex,
                            mealsByDate = mealsByDate,
                            favoriteIds = favoriteIds,
                            onToggleFavorite = { favoritesManager.toggleFavorite(it) },
                            filterMeals = ::filterAndSortMeals,
                            separateMealsByTime = ::separateMealsByTime
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )

            if (showCanteenSheet) {
                CanteenSelectionDialog(
                    canteens = canteens,
                    onSelect = {
                        selectedCanteen = it
                        settings.putString("selected_canteen_id", it.id)
                        showCanteenSheet = false
                    },
                    onDismiss = { showCanteenSheet = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MensaTopBar(
    title: String,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onCanteenClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onCanteenClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = "Select Canteen",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Rounded.Search, "Search")
            }
        },
        navigationIcon = {
            IconButton(onClick = onToggleDarkMode) {
                Icon(
                    if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    "Toggle Theme"
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search meals...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, "Close")
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true
        )
    }
}

@Composable
private fun FilterRow(
    selectedFilters: Set<String>,
    onFilterChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val filters = listOf(
        "vegan" to "🌱 Vegan",
        "vegetarisch" to "🥕 Veggie",
        "fleisch" to "🥩 Meat",
        "fisch" to "🐟 Fish"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedFilters.isNotEmpty()) {
            AssistChip(
                onClick = onClear,
                label = { Text("Clear") },
                leadingIcon = { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
        filters.forEach { (key, label) ->
            val selected = key in selectedFilters
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(key) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailyView(
    dates: List<LocalDate>,
    initialPageIndex: Int,
    mealsByDate: Map<LocalDate, List<MealDate>>,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    filterMeals: (List<MealDate>) -> List<MealDate>,
    separateMealsByTime: (List<MealDate>) -> Pair<List<MealDate>, List<MealDate>>
) {
    val pagerState = rememberPagerState(initialPage = initialPageIndex) { dates.size }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Date Strip
        ScrollableDateStrip(
            dates = dates,
            selectedIndex = pagerState.currentPage,
            onDateSelected = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { page ->
            val date = dates[page]
            val meals = filterMeals(mealsByDate[date].orEmpty())
            val (lunchMeals, afternoonMeals) = separateMealsByTime(meals)

            // Check if we should dim lunch items (past 14:30 on current day and afternoon meals exist)
            val systemTz = TimeZone.currentSystemDefault()
            val now = Clock.System.now().toLocalDateTime(systemTz)
            val today = now.date
            val isToday = date == today
            val isPastLunchTime = now.hour > 14 || (now.hour == 14 && now.minute >= 30)
            val shouldDimLunch = isToday && isPastLunchTime && afternoonMeals.isNotEmpty()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (meals.isEmpty()) {
                    item { EmptyState("No meals for this day") }
                } else {
                    // Lunch section
                    if (lunchMeals.isNotEmpty()) {
                        items(lunchMeals, key = { "lunch_${it.id}" }) { meal ->
                            BeautifulMealCard(
                                mealDate = meal,
                                isFavorite = favoriteIds.contains(meal.meals?.title),
                                onToggleFavorite = { meal.meals?.title?.let(onToggleFavorite) },
                                isDimmed = shouldDimLunch
                            )
                        }
                    }

                    // Separator
                    if (afternoonMeals.isNotEmpty() && lunchMeals.isNotEmpty()) {
                        item(key = "separator_$date") {
                            TimeSeparator("Afternoon")
                        }
                    }

                    // Afternoon section
                    if (afternoonMeals.isNotEmpty()) {
                        items(afternoonMeals, key = { "afternoon_${it.id}" }) { meal ->
                            BeautifulMealCard(
                                mealDate = meal,
                                isFavorite = favoriteIds.contains(meal.meals?.title),
                                onToggleFavorite = { meal.meals?.title?.let(onToggleFavorite) },
                                isDimmed = false
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) } // Bottom padding
            }
        }
    }
}

@Composable
private fun ScrollableDateStrip(
    dates: List<LocalDate>,
    selectedIndex: Int,
    onDateSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(maxOf(0, selectedIndex - 2))
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dates.size) { index ->
            val date = dates[index]
            val isSelected = index == selectedIndex
            DateChip(
                date = date,
                isSelected = isSelected,
                onClick = { onDateSelected(index) }
            )
        }
    }
}

@Composable
private fun TimeSeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun DateChip(
    date: LocalDate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val isToday = date == today
    
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfWeek.name.take(3),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.7f)
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = contentColor
        )
        if (isToday) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WeeklyView(
    dates: List<LocalDate>,
    mealsByDate: Map<LocalDate, List<MealDate>>,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    filterMeals: (List<MealDate>) -> List<MealDate>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        dates.forEach { date ->
            val meals = filterMeals(mealsByDate[date].orEmpty())
            if (meals.isNotEmpty()) {
                item(key = "header_$date") {
                    Text(
                        text = "${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}.${date.monthNumber}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(meals, key = { it.id }) { meal ->
                    BeautifulMealCard(
                        mealDate = meal,
                        isFavorite = favoriteIds.contains(meal.meals?.title),
                        onToggleFavorite = { meal.meals?.title?.let(onToggleFavorite) },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BeautifulMealCard(
    mealDate: MealDate,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    isDimmed: Boolean = false
) {
    val meal = mealDate.meals
    var expanded by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf(false) }

    val title = remember(meal?.title) {
        meal?.title?.replace(Regex("\\([^)]*\\)"), " ")?.replace(Regex("\\s+"), " ")?.trim() ?: "Meal"
    }
    
    val imageUrl = remember(meal?.id, meal?.imagePath, meal?.imagePathGeneric) {
        val base = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/"
        val imagePath = meal?.imagePath?.replace(".png", ".jpg")
        val imagePathGeneric = meal?.imagePathGeneric?.replace(".png", ".jpg")
        val path = "mensa-food/" + (imagePath ?: imagePathGeneric ?: "mensa.png")
        base + path
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
            .then(if (isDimmed) Modifier.alpha(0.5f) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(IntrinsicSize.Min), // Match height of children
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Square Image on the side
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                KamelImage(
                    resource = asyncPainterResource(data = imageUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable { showImageDialog = true }
                )
                
                // Favorite Button (small overlay on image)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clickable { onToggleFavorite() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                     Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Outlined.Star,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFFB400) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(4.dp).size(16.dp)
                    )
                }
            }

            // Content Side
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Category Badge
                    Text(
                        text = mealDate.category,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    
                    // Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Add description preview if title is short
                    if (!expanded && title.length < 50) {
                        val shortDesc = meal?.fullText?.replace(Regex("\\([^)]*\\)"), " ")?.replace(Regex("\\s+"), " ")?.trim() ?: ""
                        if (shortDesc.isNotBlank()) {
                             Spacer(Modifier.height(4.dp))
                             Text(
                                text = shortDesc,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Icons Row (Bottom of text content)
                if (!meal?.icons.isNullOrEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        meal?.icons?.take(4)?.forEach { iconKey ->
                            val (emoji, _) = iconEmojiWithLabel[iconKey.lowercase()] ?: ("❓" to iconKey)
                            Text(text = emoji, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
        
        // Expandable details (Description & Notes)
        if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                
                // Full description
                val fullText = meal?.fullText?.replace(Regex("\\([^)]*\\)"), " ")?.replace(Regex("\\s+"), " ")?.trim() ?: ""
                if (fullText.isNotBlank()) {
                    Text(
                        text = fullText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                
                // Notes
                if (mealDate.note?.isNotBlank() == true) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Outlined.Info, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = mealDate.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showImageDialog) {
        Dialog(onDismissRequest = { showImageDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Box {
                    KamelImage(
                        resource = asyncPainterResource(data = imageUrl),
                        contentDescription = title,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                    )

                    // Close button
                    IconButton(
                        onClick = { showImageDialog = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp)
                        )
                    }

                    // Title Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                    startY = 0f
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (mealDate.category.isNotEmpty()) {
                            Text(
                                text = mealDate.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String = "No meals available") {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🍽️", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CanteenSelectionDialog(
    canteens: List<Canteen>,
    onSelect: (Canteen) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Canteen") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(canteens) { canteen ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(canteen) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍", modifier = Modifier.padding(end = 12.dp))
                        Text(
                            text = canteen.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Constants
private val iconEmojiWithLabel = mapOf(
    "vegetarisch" to ("🥕" to "Vegetarian"),
    "vegan" to ("🌱" to "Vegan"),
    "fleisch" to ("🥩" to "Meat"),
    "strohschwein" to ("🐷" to "Pork"),
    "leinetalerrind" to ("🐄" to "Beef"),
    "fisch" to ("🐟" to "Fish"),
    "bio" to ("🌿" to "Organic"),
    "regional" to ("🏡" to "Regional"),
    "klimaessen" to ("🌍" to "Climate-friendly"),
)
