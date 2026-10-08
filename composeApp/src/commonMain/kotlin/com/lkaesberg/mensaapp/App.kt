package com.lkaesberg.mensaapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import androidx.compose.runtime.CompositionLocalProvider
import com.lkaesberg.mensaapp.i18n.LocalAppLocale
import com.lkaesberg.mensaapp.i18n.LocalStrings
import com.lkaesberg.mensaapp.i18n.stringsFor
import com.lkaesberg.mensaapp.notifications.NotificationScheduler
import com.lkaesberg.mensaapp.ui.MensaAppTheme
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.Route
import com.lkaesberg.mensaapp.ui.SystemBarThemeEffect
import com.lkaesberg.mensaapp.ui.navigateSafely
import com.lkaesberg.mensaapp.ui.popBackStackSafely
import com.lkaesberg.mensaapp.ui.screens.AllMealsArchiveScreen
import com.lkaesberg.mensaapp.ui.screens.CanteenDetailScreen
import com.lkaesberg.mensaapp.ui.screens.CanteenPickerScreen
import com.lkaesberg.mensaapp.ui.screens.DishStatsScreen
import com.lkaesberg.mensaapp.ui.screens.FavoritesScreen
import com.lkaesberg.mensaapp.ui.screens.FeedScreen
import com.lkaesberg.mensaapp.ui.screens.MealDetailScreen
import com.lkaesberg.mensaapp.ui.screens.OnboardingScreen
import com.lkaesberg.mensaapp.ui.screens.SearchScreen
import com.lkaesberg.mensaapp.ui.screens.SettingsScreen
import com.lkaesberg.mensaapp.ui.screens.SidesScreen
import com.lkaesberg.mensaapp.ui.screens.UpcomingFavoritesScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App(
    /**
     * Trigger the platform's notification-permission flow. The callback is
     * invoked with `true` if the user grants (or has already granted), `false`
     * if they deny or the platform doesn't support runtime push permission.
     */
    requestNotificationPermission: ((granted: Boolean) -> Unit) -> Unit = { it(false) },
) {
    val settings = remember { createAppSettings() }
    val favoritesManager = remember(settings) { FavoritesManager(settings) }
    val notificationScheduler = remember { NotificationScheduler() }
    val state = remember(settings, favoritesManager) {
        MealsAppState(
            settings = settings,
            favoritesManager = favoritesManager,
            notificationScheduler = notificationScheduler,
        )
    }
    var isDarkMode by remember { mutableStateOf(settings.getBoolean("dark_mode", false)) }
    val onboarded = remember { settings.getBoolean("onboarded", false) }
    val locale by state.locale.collectAsState()

    MensaAppTheme(useDarkTheme = isDarkMode) {
      // Match the OS status-bar icon colors to the app background — without
      // this the default light icons disappear on our pale paper palette.
      SystemBarThemeEffect(isDarkBackground = isDarkMode)
      CompositionLocalProvider(
          LocalStrings provides stringsFor(locale),
          LocalAppLocale provides locale,
      ) {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
        var menuOpen by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { state.loadCanteens(scope) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MensaTheme.palette.paper)
                .statusBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
        ) {
        NavHost(
            navController = navController,
            startDestination = if (onboarded) Route.Feed else Route.Onboarding,
        ) {
            composable(Route.Onboarding) {
                OnboardingScreen(
                    state = state,
                    requestNotificationPermission = requestNotificationPermission,
                    onComplete = {
                        navController.navigateSafely(Route.Feed) {
                            popUpTo(Route.Onboarding) { inclusive = true }
                        }
                    },
                )
            }
            composable(Route.Feed) {
                FeedScreen(
                    state = state,
                    onOpenCanteenPicker = { navController.navigateSafely(Route.CanteenPicker) },
                    onOpenSearch = { navController.navigateSafely(Route.Search) },
                    onOpenNotifications = { navController.navigateSafely(Route.UpcomingFavorites) },
                    onOpenMenu = { menuOpen = true },
                    onOpenMealDetail = { md -> navController.navigateSafely(Route.mealDetail(md.id)) },
                    onOpenSides = { date -> navController.navigateSafely(Route.sides(date)) },
                    onOpenDesserts = { date -> navController.navigateSafely(Route.desserts(date)) },
                )
            }
            composable(
                route = Route.Desserts,
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.read { getString("date") }.orEmpty()
                SidesScreen(
                    state = state,
                    date = date,
                    onBack = { navController.popBackStackSafely() },
                    desserts = true,
                )
            }
            composable(
                route = Route.Sides,
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.read { getString("date") }.orEmpty()
                SidesScreen(
                    state = state,
                    date = date,
                    onBack = { navController.popBackStackSafely() },
                )
            }
            composable(Route.CanteenPicker) {
                CanteenPickerScreen(
                    state = state,
                    onBack = { navController.popBackStackSafely() },
                    onSelected = { _ -> navController.popBackStackSafely() },
                )
            }
            composable(
                route = Route.CanteenDetail,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { backStackEntry ->
                val slug = backStackEntry.arguments?.read { getString("slug") }.orEmpty()
                CanteenDetailScreen(
                    state = state,
                    slug = slug,
                    onBack = { navController.popBackStackSafely() },
                )
            }
            composable(
                route = Route.MealDetail,
                arguments = listOf(navArgument("dateId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val dateId = backStackEntry.arguments?.read { getString("dateId") }.orEmpty()
                MealDetailScreen(
                    state = state,
                    dateId = dateId,
                    onBack = { navController.popBackStackSafely() },
                )
            }
            composable(Route.Search) {
                SearchScreen(
                    state = state,
                    onBack = { navController.popBackStackSafely() },
                    onOpenMealDetail = { md -> navController.navigateSafely(Route.mealDetail(md.id)) },
                )
            }
            composable(Route.Archive) {
                AllMealsArchiveScreen(state = state, onBack = { navController.popBackStackSafely() })
            }
            composable(Route.DishStats) {
                DishStatsScreen(state = state, onBack = { navController.popBackStackSafely() })
            }
            composable(Route.Favorites) {
                FavoritesScreen(state = state, onBack = { navController.popBackStackSafely() })
            }
            composable(Route.UpcomingFavorites) {
                UpcomingFavoritesScreen(
                    state = state,
                    onBack = { navController.popBackStackSafely() },
                    onOpenMealDetail = { md -> navController.navigateSafely(Route.mealDetail(md.id)) },
                )
            }
            composable(Route.Settings) {
                SettingsScreen(
                    state = state,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = {
                        isDarkMode = !isDarkMode
                        settings.putBoolean("dark_mode", isDarkMode)
                    },
                    onBack = { navController.popBackStackSafely() },
                    requestNotificationPermission = requestNotificationPermission,
                )
            }
        }
        } // end of width-cap Box
        } // end of insets Box

        if (menuOpen) {
            MenuSheet(
                onClose = { menuOpen = false },
                onNavigate = { route ->
                    menuOpen = false
                    navController.navigateSafely(route)
                },
            )
        }
      } // end CompositionLocalProvider(LocalStrings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val palette = MensaTheme.palette
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    fun dismissThen(action: () -> Unit) {
        scope.launch {
            runCatching { sheetState.hide() }
            action()
        }
    }
    val s = LocalStrings.current
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = palette.paper,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = if (s == com.lkaesberg.mensaapp.i18n.StringsDe) "Menü" else "Menu",
                color = palette.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            MenuItem(s.pickCanteen, if (s == com.lkaesberg.mensaapp.i18n.StringsDe) "Standorte" else "Locations", Icons.Filled.Apartment) { dismissThen { onNavigate(Route.CanteenPicker) } }
            MenuItem(s.favorites, if (s == com.lkaesberg.mensaapp.i18n.StringsDe) "Lieblingsgerichte verwalten" else "Manage favorite dishes", Icons.Filled.Star) { dismissThen { onNavigate(Route.Favorites) } }
            MenuItem(s.allMealsArchive, if (s == com.lkaesberg.mensaapp.i18n.StringsDe) "Im gesamten Archiv suchen" else "Search the full archive", Icons.Filled.Inventory2) { dismissThen { onNavigate(Route.Archive) } }
            MenuItem(s.dishStats, if (s == com.lkaesberg.mensaapp.i18n.StringsDe) "Häufigkeit + zuletzt gesehen" else "Frequency & last seen", Icons.Filled.Analytics) { dismissThen { onNavigate(Route.DishStats) } }
            MenuItem(s.settings, if (s == com.lkaesberg.mensaapp.i18n.StringsDe) "Personenkreis, Erscheinungsbild, Push" else "Group, appearance, push", Icons.Filled.Settings) { dismissThen { onNavigate(Route.Settings) } }
        }
    }
}

@Composable
private fun MenuItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val palette = MensaTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.moss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = palette.forestDark, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.sub, fontSize = 11.sp)
        }
    }
}
