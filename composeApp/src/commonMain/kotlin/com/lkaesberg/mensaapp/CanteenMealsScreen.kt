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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun CanteenMealsScreen(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {}
) {
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
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    MaterialTheme.colorScheme.surface
                )
            )
        )
        .pullRefresh(pullRefreshState)) {

        Column(modifier = Modifier.fillMaxSize()) {
            // Modern header with gradient background
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    // Title row with dark mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🍽️ Mensa Menu",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        
                        // Dark mode toggle
                        IconButton(
                            onClick = onToggleDarkMode,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isDarkMode) "☀️" else "🌙",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Compact canteen selector
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📍",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = selectedCanteen?.name ?: "Select Canteen",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text("▼", style = MaterialTheme.typography.bodyLarge)
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            canteens.forEach { canteen ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("📍", modifier = Modifier.padding(end = 8.dp))
                                            Text(
                                                canteen.name,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCanteen = canteen
                                        settings.putString("selected_canteen_id", canteen.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (mealsByDate.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "🍽️",
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            "No meals available",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            "Check back later for updates",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
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

                        // Compact date header
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shadowElevation = 2.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left arrow - clickable
                                    if (page > 0) {
                                        Text(
                                            "‹",
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier
                                                .alpha(0.8f)
                                                .clickable {
                                                    coroutineScopePager.launch {
                                                        pagerState.animateScrollToPage(page - 1)
                                                    }
                                                }
                                                .padding(6.dp)
                                        )
                                    } else {
                                        Spacer(Modifier.width(20.dp))
                                    }

                                    // Center date with dropdown
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            modifier = Modifier.clickable { menuExpanded = true },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "📅",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                )
                                                Text(
                                                    text = formatted,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    text = " ▼",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.alpha(0.6f)
                                                )
                                            }
                                        }

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
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("📅", modifier = Modifier.padding(end = 8.dp))
                                                            Text(
                                                                display,
                                                                style = MaterialTheme.typography.bodyLarge
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        menuExpanded = false
                                                        coroutineScopePager.launch {
                                                            pagerState.animateScrollToPage(index)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Right arrow - clickable
                                    if (page < dates.lastIndex) {
                                        Text(
                                            "›",
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier
                                                .alpha(0.8f)
                                                .clickable {
                                                    coroutineScopePager.launch {
                                                        pagerState.animateScrollToPage(page + 1)
                                                    }
                                                }
                                                .padding(6.dp)
                                        )
                                    } else {
                                        Spacer(Modifier.width(20.dp))
                                    }
                                }
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

                Spacer(Modifier.height(12.dp))

                // Modern page indicators
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(dates.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                        val color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(width)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        "← swipe →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
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
    
    // Filter out text in brackets from title and description, replace with space and trim
    val title = remember(meal?.title) {
        meal?.title?.replace(Regex("\\([^)]*\\)"), " ")?.replace(Regex("\\s+"), " ")?.trim() ?: "Meal"
    }
    
    val fullText = remember(meal?.fullText) {
        meal?.fullText?.replace(Regex("\\([^)]*\\)"), " ")?.replace(Regex("\\s+"), " ")?.trim() ?: ""
    }

    // URL resolved based on image_path or fallback to placeholder
    val imageUrl = remember(meal?.id, meal?.imagePath, meal?.imagePathGeneric) {
        val base = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/"
        val imagePath = meal?.imagePath
        val imagePathGeneric = meal?.imagePathGeneric
        val path = "mensa-food/" + (imagePath ?: imagePathGeneric ?: "mensa.png")
        base + path
    }

    var showImageDialog by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    
    // Animated values for card expansion
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.01f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    // Image fullscreen dialog
    if (showImageDialog) {
        AlertDialog(
            onDismissRequest = { showImageDialog = false },
            confirmButton = {
                TextButton(onClick = { showImageDialog = false }) {
                    Text("Close")
                }
            },
            title = {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KamelImage(
                        resource = asyncPainterResource(data = imageUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .scale(scale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isExpanded) 8.dp else 4.dp
        ),
        onClick = { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Small square image on the left
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showImageDialog = true }
            ) {
                KamelImage(
                    resource = asyncPainterResource(data = imageUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Subtle gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f)
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
                
                // Expand/fullscreen indicator
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "🔍",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // Content section - right side of the row
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 100.dp)
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Category badge and icons in a row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = mealDate.category,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    // Icons next to category badge
                    meal?.icons?.takeIf { it.isNotEmpty() }?.let { icons ->
                        icons.take(4).forEach { iconKey ->
                            val (emoji, label) = iconEmojiWithLabel[iconKey.lowercase()] 
                                ?: ("❓" to iconKey)
                            
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Full description
                Text(
                    text = fullText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Note with icon if present
                mealDate.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(Modifier.height(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "ℹ️",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // Expand/collapse hint
                if (!isExpanded && fullText.length > 100) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tap to see more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// Enhanced icon → emoji mapping with labels
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