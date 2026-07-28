package com.lkaesberg.mensaapp

import com.lkaesberg.mensaapp.data.CanteenInfo
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.UserRole
import com.lkaesberg.mensaapp.notifications.NotificationScheduler
import com.russhwolf.settings.Settings
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * Shared, conversation-scoped state for the redesign — meals/canteens/prices
 * caches that screens read via [StateFlow]s. Lives for the lifetime of the
 * Compose tree (not a true ViewModel; KMP-friendly plain class).
 */
class MealsAppState(
    val settings: Settings,
    val favoritesManager: FavoritesManager,
    val notificationScheduler: NotificationScheduler,
) {
    val repository = MealsRepository(SupabaseProvider.client().postgrest)

    private val _canteens = MutableStateFlow<List<Canteen>>(emptyList())
    val canteens: StateFlow<List<Canteen>> = _canteens.asStateFlow()

    private val _selectedCanteen = MutableStateFlow<Canteen?>(null)
    val selectedCanteen: StateFlow<Canteen?> = _selectedCanteen.asStateFlow()

    private val _mealsByDate = MutableStateFlow<Map<LocalDate, List<MealDate>>>(emptyMap())
    val mealsByDate: StateFlow<Map<LocalDate, List<MealDate>>> = _mealsByDate.asStateFlow()

    private val _canteenPrices = MutableStateFlow<List<CanteenPrice>>(emptyList())
    val canteenPrices: StateFlow<List<CanteenPrice>> = _canteenPrices.asStateFlow()

    private val _history = MutableStateFlow<List<MealDate>>(emptyList())
    val history: StateFlow<List<MealDate>> = _history.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _userRole = MutableStateFlow(UserRole.fromKey(settings.getStringOrNull(UserRole.SETTINGS_KEY)))
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    fun setUserRole(role: UserRole) {
        _userRole.value = role
        settings.putString(UserRole.SETTINGS_KEY, role.key)
    }

    private val _locale = MutableStateFlow(Locale.fromTag(settings.getStringOrNull(LOCALE_KEY)))
    val locale: StateFlow<Locale> = _locale.asStateFlow()

    fun setLocale(locale: Locale) {
        _locale.value = locale
        settings.putString(LOCALE_KEY, locale.tag)
    }

    /**
     * Weekly hours pattern keyed by canteen id (Map<canteenId, List<CanteenHours>>).
     * Loaded once at launch via [refreshHoursAndOccupancy].
     */
    private val _canteenHours = MutableStateFlow<Map<String, List<CanteenHours>>>(emptyMap())
    val canteenHours: StateFlow<Map<String, List<CanteenHours>>> = _canteenHours.asStateFlow()

    /** Latest occupancy snapshot per canteen id. */
    private val _occupancy = MutableStateFlow<Map<String, CanteenOccupancy>>(emptyMap())
    val occupancy: StateFlow<Map<String, CanteenOccupancy>> = _occupancy.asStateFlow()

    /**
     * Today's occupancy time-series for the currently-selected canteen.
     * One row per 30-minute sync. Loaded lazily by [loadOccupancyTodayForSelected]
     * — the mobile app sticks with the single latest snapshot, only the web
     * crowd panel renders the full curve.
     */
    private val _occupancyToday = MutableStateFlow<List<CanteenOccupancy>>(emptyList())
    val occupancyToday: StateFlow<List<CanteenOccupancy>> = _occupancyToday.asStateFlow()

    fun refreshHoursAndOccupancy(scope: CoroutineScope) {
        scope.launch {
            _canteenHours.value = repository.getCanteenHours().groupBy { it.canteenId }
        }
        scope.launch {
            _occupancy.value = repository.getOccupancyLatest().associateBy { it.canteenId }
        }
    }

    fun loadOccupancyTodayForSelected(scope: CoroutineScope) {
        val canteen = _selectedCanteen.value ?: return
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        scope.launch {
            _occupancyToday.value = repository.getOccupancyForDay(canteen.id, today)
        }
    }

    /** Canteen IDs the user has hidden from picker / upcoming-favourite lists. */
    private val _disabledCanteenIds = MutableStateFlow(loadDisabledCanteens())
    val disabledCanteenIds: StateFlow<Set<String>> = _disabledCanteenIds.asStateFlow()

    private fun loadDisabledCanteens(): Set<String> {
        val raw = settings.getStringOrNull(DISABLED_CANTEENS_KEY)?.takeIf { it.isNotBlank() }
            ?: return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setCanteenEnabled(canteenId: String, enabled: Boolean) {
        val updated = if (enabled) _disabledCanteenIds.value - canteenId
            else _disabledCanteenIds.value + canteenId
        _disabledCanteenIds.value = updated
        settings.putString(DISABLED_CANTEENS_KEY, updated.joinToString(","))
    }

    private companion object {
        const val DISABLED_CANTEENS_KEY = "disabled_canteens"
        const val LOCALE_KEY = "locale"
    }

    /**
     * (canteen, date) → list of meals served — fetched lazily across all
     * canteens so the FavoritesScreen can surface "next occurrence at any
     * mensa". Loaded by [loadUpcomingAcrossCanteens].
     */
    private val _upcomingAcrossCanteens =
        MutableStateFlow<Map<Pair<Canteen, LocalDate>, List<MealDate>>>(emptyMap())
    val upcomingAcrossCanteens: StateFlow<Map<Pair<Canteen, LocalDate>, List<MealDate>>> =
        _upcomingAcrossCanteens.asStateFlow()

    fun selectedInfo(): CanteenInfo? = selectedCanteen.value?.let { CanteenStaticData.matchFor(it.name) }

    fun loadCanteens(scope: CoroutineScope) {
        scope.launch {
            // Pull all canteens, then drop ones the static metadata doesn't
            // recognise (e.g. cafés synced via mensa-hours-sync that we don't
            // surface in the picker). This keeps Nordmensa-style ghosts off
            // the screen if the row lingers in DB.
            val list = repository.getCanteens().filter { CanteenStaticData.matchFor(it.name) != null }
            _canteens.value = list
            if (_selectedCanteen.value == null) {
                val rememberedSlug = settings.getStringOrNull("selected_canteen_slug")
                val match = list.firstOrNull {
                    rememberedSlug != null && CanteenStaticData.matchFor(it.name)?.slug == rememberedSlug
                } ?: list.firstOrNull { CanteenStaticData.matchFor(it.name)?.slug == "zentral" }
                  ?: list.firstOrNull()
                if (match != null) selectCanteen(scope, match)
            }
        }
        refreshHoursAndOccupancy(scope)
    }

    fun selectCanteen(scope: CoroutineScope, canteen: Canteen) {
        _selectedCanteen.value = canteen
        CanteenStaticData.matchFor(canteen.name)?.let {
            settings.putString("selected_canteen_slug", it.slug)
        }
        loadMealsForSelected(scope)
        loadPricesForSelected(scope)
    }

    fun loadMealsForSelected(scope: CoroutineScope) {
        val canteen = _selectedCanteen.value ?: return
        scope.launch {
            _refreshing.value = true
            _mealsByDate.value = repository.getMealsForCanteen(canteen.id)
            _refreshing.value = false
        }
    }

    fun loadPricesForSelected(scope: CoroutineScope) {
        val canteen = _selectedCanteen.value ?: return
        scope.launch {
            _canteenPrices.value = repository.getCanteenPrices(canteen.id)
        }
    }

    fun loadHistoryForSelected(scope: CoroutineScope, sinceDays: Int = 90) {
        val canteen = _selectedCanteen.value ?: return
        scope.launch {
            _history.value = repository.getMealsHistory(canteen.id, sinceDays)
        }
    }

    fun refreshAll(scope: CoroutineScope) {
        loadCanteens(scope)
        loadMealsForSelected(scope)
        loadPricesForSelected(scope)
    }

    // ─── canteen status (DB hours first, fall back to CanteenStaticData) ───

    private fun nowDt(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    private fun parseDbTime(s: String?): LocalTime? {
        if (s.isNullOrBlank()) return null
        val parts = s.split(":")
        return runCatching {
            LocalTime(parts[0].toInt(), parts[1].toInt())
        }.getOrNull()
    }

    private sealed class TodayHours {
        data class Open(val open: LocalTime, val close: LocalTime) : TodayHours()
        object ClosedToday : TodayHours()
        object Unknown : TodayHours()
    }

    private fun dbHoursForToday(canteen: Canteen, dow: DayOfWeek): TodayHours {
        val rows = _canteenHours.value[canteen.id] ?: return TodayHours.Unknown
        val row = rows.firstOrNull { it.dayOfWeek == dow.isoDayNumber }
            ?: return TodayHours.Unknown
        val open = parseDbTime(row.openTime)
        val close = parseDbTime(row.closeTime)
        // NULL open/close on an existing row = "closed today" per the schema.
        // Distinct from Unknown (no row at all), where we fall back to static.
        return if (open != null && close != null) TodayHours.Open(open, close)
        else TodayHours.ClosedToday
    }

    fun openNow(canteen: Canteen): Boolean {
        val now = nowDt()
        return when (val r = dbHoursForToday(canteen, now.date.dayOfWeek)) {
            is TodayHours.Open -> now.time >= r.open && now.time < r.close
            TodayHours.ClosedToday -> false
            TodayHours.Unknown -> CanteenStaticData.matchFor(canteen.name)
                ?.let { CanteenStaticData.openNow(it, now) } ?: false
        }
    }

    fun pastClosingTime(canteen: Canteen): Boolean {
        val now = nowDt()
        return when (val r = dbHoursForToday(canteen, now.date.dayOfWeek)) {
            is TodayHours.Open -> now.time >= r.close
            TodayHours.ClosedToday -> true   // closed all day → effectively past closing
            TodayHours.Unknown -> CanteenStaticData.matchFor(canteen.name)
                ?.let { CanteenStaticData.pastClosingTime(it, now) } ?: true
        }
    }

    fun closesAt(canteen: Canteen): String? {
        val now = nowDt()
        return when (val r = dbHoursForToday(canteen, now.date.dayOfWeek)) {
            is TodayHours.Open -> "${r.close.hour.toString().padStart(2, '0')}:${r.close.minute.toString().padStart(2, '0')}"
            TodayHours.ClosedToday -> null
            TodayHours.Unknown -> CanteenStaticData.matchFor(canteen.name)
                ?.let { CanteenStaticData.closesAt(it, now) }
        }
    }

    /**
     * Open/close times for a canteen on a specific date, DB-first with the
     * legacy static fallback. Returns null when the canteen is closed that
     * day (or hours aren't known). Used by the feed to derive Mittag /
     * Nachmittag time ranges instead of hardcoding them.
     */
    fun openCloseFor(canteen: Canteen, date: LocalDate): Pair<LocalTime, LocalTime>? {
        val isoDow = date.dayOfWeek.isoDayNumber
        val rows = _canteenHours.value[canteen.id]
        if (rows != null) {
            val row = rows.firstOrNull { it.dayOfWeek == isoDow }
            val open = parseDbTime(row?.openTime)
            val close = parseDbTime(row?.closeTime)
            return if (open != null && close != null) open to close else null
        }
        // Static fallback when canteen_hours hasn't synced yet.
        val info = CanteenStaticData.matchFor(canteen.name) ?: return null
        val entry = info.hours.firstOrNull { date.dayOfWeek in it.days } ?: return null
        val open = entry.openTime ?: return null
        val close = entry.closeTime ?: return null
        return open to close
    }

    /**
     * `true` if it's currently before the canteen's opening time today.
     * Used to suppress the deactivated-meal "greyed out" styling in the
     * feed: meals that the scraper has marked deactivated for today should
     * still render at full opacity until the canteen has actually opened —
     * up to that point the menu is mutable upstream.
     */
    fun beforeOpeningToday(canteen: Canteen): Boolean {
        val now = nowDt()
        return when (val r = dbHoursForToday(canteen, now.date.dayOfWeek)) {
            is TodayHours.Open -> now.time < r.open
            TodayHours.ClosedToday -> false
            TodayHours.Unknown -> CanteenStaticData.matchFor(canteen.name)
                ?.let { ci ->
                    val entry = ci.hours.firstOrNull { now.date.dayOfWeek in it.days }
                    val open = entry?.openTime
                    open != null && now.time < open
                } ?: false
        }
    }

    /**
     * Full weekly schedule for a canteen, DB-first with static fallback when
     * `mensa-hours-sync` hasn't populated `canteen_hours` yet. Uses [labels]
     * (locale-aware short weekday names) for the "Mo–Do" labels.
     */
    fun hoursLinesFor(
        canteen: Canteen,
        labels: List<String>,
        closedLabel: String,
    ): List<com.lkaesberg.mensaapp.data.HoursLine> {
        val rows = _canteenHours.value[canteen.id]
        if (!rows.isNullOrEmpty()) {
            return com.lkaesberg.mensaapp.data.HoursDisplay.groupAll(rows, labels, closedLabel)
        }
        val info = CanteenStaticData.matchFor(canteen.name) ?: return emptyList()
        return com.lkaesberg.mensaapp.data.HoursDisplay.fromStatic(info.hours)
    }

    /** One-line view for today; used in the picker for non-active canteens. */
    fun todayHoursLineFor(
        canteen: Canteen,
        labels: List<String>,
        closedLabel: String,
    ): com.lkaesberg.mensaapp.data.HoursLine? {
        val today = nowDt().date.dayOfWeek
        val rows = _canteenHours.value[canteen.id]
        if (!rows.isNullOrEmpty()) {
            return com.lkaesberg.mensaapp.data.HoursDisplay.lineForToday(
                rows = rows,
                today = today,
                weekdayShortLabels = labels,
                closedLabel = closedLabel,
            )
        }
        // Static fallback: pick the entry whose `days` set covers today.
        val info = CanteenStaticData.matchFor(canteen.name) ?: return null
        val match = info.hours.firstOrNull { today in it.days } ?: return null
        return com.lkaesberg.mensaapp.data.HoursLine(
            daysLabel = labels[today.ordinal],
            timeLabel = match.time,
            days = setOf(today),
        )
    }

    /**
     * Fetch the upcoming few days for *every* canteen so the FavoritesScreen
     * can show "next: heute · Zentralmensa" even when the user is browsing a
     * different mensa, and so the notification preview matches the worker's
     * cross-canteen scan.
     */
    fun loadUpcomingAcrossCanteens(scope: CoroutineScope) {
        scope.launch {
            val list = canteens.value.ifEmpty { repository.getCanteens().also { _canteens.value = it } }
            val combined = mutableMapOf<Pair<Canteen, LocalDate>, List<MealDate>>()
            for (canteen in list) {
                val perDate = repository.getMealsForCanteen(canteen.id)
                for ((date, meals) in perDate) {
                    combined[canteen to date] = meals
                }
            }
            _upcomingAcrossCanteens.value = combined
        }
    }
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) getString(key, "") else null
