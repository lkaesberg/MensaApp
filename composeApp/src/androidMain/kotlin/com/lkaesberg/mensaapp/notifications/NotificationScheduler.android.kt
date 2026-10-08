package com.lkaesberg.mensaapp.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lkaesberg.mensaapp.APP_PREFS_NAME
import com.lkaesberg.mensaapp.Canteen
import com.lkaesberg.mensaapp.FavoritesManager
import com.lkaesberg.mensaapp.MealsRepository
import com.lkaesberg.mensaapp.SupabaseProvider
import com.lkaesberg.mensaapp.containsFavorite
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.i18n.Strings
import com.lkaesberg.mensaapp.i18n.stringsFor
import com.lkaesberg.mensaapp.normalizeFavoriteKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.util.concurrent.TimeUnit

/**
 * Android implementation: AlarmManager for the time of day, WorkManager for
 * the check, NotificationManagerCompat to post.
 *
 * Setup: [AndroidNotificationContext.attach] must be called from the
 * Application/MainActivity before the first NotificationScheduler is used.
 * Compose Multiplatform's no-arg expect constructor means we can't pass the
 * Context through the type system, so we cache it process-wide.
 *
 * This replaced a 1-day PeriodicWorkRequest, which is why reminders were
 * "sometimes late, sometimes missing": its next run was "last finish + 24 h",
 * so every Doze / standby / retry delay accumulated, DST shifted it for good,
 * and an hour changed in Settings after the first run never took effect; and
 * a failed network request read as "no favourites today" and was never
 * retried. See [FavoriteAlarms] and [FavoriteCheckWorker].
 */
actual class NotificationScheduler actual constructor() {

    actual fun setEnabled(enabled: Boolean, leadDays: Int, hourOfDay: Int) {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return
        ensureChannel(ctx)
        // leadDays is read back from settings by the worker at run time.
        FavoriteAlarms.cancelLegacyPeriodicWork(ctx)
        if (enabled) FavoriteAlarms.arm(ctx, hourOfDay.coerceIn(0, 23))
        else FavoriteAlarms.disarm(ctx)
    }

    actual fun runCheckNow() {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return
        ensureChannel(ctx)
        FavoriteAlarms.enqueueCheck(ctx)
    }

    actual fun cancelAll() {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return
        FavoriteAlarms.disarm(ctx)
    }

    actual fun sendTestNotification(): Boolean {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return false
        ensureChannel(ctx)
        if (!canPost(ctx)) return false
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("★ Test-Benachrichtigung")
            .setContentText("So sieht eine Favoriten-Erinnerung aus")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(ctx, TEST_NOTIFICATION_ID))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(TEST_NOTIFICATION_ID, notif)
        return true
    }

    actual fun isPermitted(): Boolean {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }
    }

    actual fun isSupported(): Boolean = true

    companion object {
        const val CHANNEL_ID = "favorites_channel"
        const val SYNC_CHANNEL_ID = "favorites_sync_channel"
        const val TEST_NOTIFICATION_ID = 1_999
        const val SYNC_NOTIFICATION_ID = 1_998

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Favoriten",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "Erinnerungen, wenn ein Lieblingsgericht auf dem Plan steht"
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }

        /** Silent channel for the brief "checking" notice expedited work needs before Android 12. */
        fun ensureSyncChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(SYNC_CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(SYNC_CHANNEL_ID, "Speiseplan-Abgleich", NotificationManager.IMPORTANCE_MIN),
                    )
                }
            }
        }

        fun canPost(ctx: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return false
            return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }

        /** Tapping a reminder opens the app. */
        fun openAppIntent(ctx: Context, requestCode: Int): PendingIntent? {
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                ctx,
                requestCode,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

/** Process-wide context holder. Initialized from the Activity. */
object AndroidNotificationContext {
    @Volatile private var ctx: Context? = null
    fun attach(context: Context) { ctx = context.applicationContext }
    fun contextOrNull(): Context? = ctx
    fun requireContext(): Context =
        ctx ?: error("AndroidNotificationContext.attach() not called — wire it from MainActivity.onCreate")
}

/**
 * Daily trigger. An alarm at the chosen local hour starts the check and arms
 * the next day's alarm, each aimed at the wall-clock hour afresh — no drift,
 * DST-safe, and a changed hour applies from the next alarm. Exact when the
 * user allows exact alarms (default up to Android 13); otherwise
 * setAndAllowWhileIdle, which still fires in Doze, at most a few minutes late.
 */
internal object FavoriteAlarms {
    private const val CHECK_WORK = "mensa-favorite-check-run"
    /** The old PeriodicWorkRequest; cancelled wherever we (re-)arm. */
    private const val LEGACY_PERIODIC_WORK = "mensa-favorite-check"
    const val ACTION_ALARM = "com.lkaesberg.mensaapp.action.FAVORITE_CHECK"

    fun prefs(ctx: Context): Settings =
        SharedPreferencesSettings(ctx.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE))

    /** Re-arm from saved settings: app start, boot, app update, clock or time-zone change. */
    fun rearmFromSettings(ctx: Context) {
        cancelLegacyPeriodicWork(ctx)
        val settings = prefs(ctx)
        if (settings.getBoolean("notif_favorites", false)) {
            arm(ctx, settings.getInt("notif_time_hour", 9).coerceIn(0, 23))
        }
    }

    fun arm(ctx: Context, hourOfDay: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = nextTriggerAt(hourOfDay, Clock.System.now(), TimeZone.currentSystemDefault())
        val pi = alarmIntent(ctx)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun disarm(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx))
        WorkManager.getInstance(ctx).cancelUniqueWork(CHECK_WORK)
        cancelLegacyPeriodicWork(ctx)
    }

    fun cancelLegacyPeriodicWork(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(LEGACY_PERIODIC_WORK)
    }

    /**
     * Run the check now. Expedited so it starts inside the alarm's Doze
     * exemption; needs a network, and a failed fetch retries with backoff.
     */
    fun enqueueCheck(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<FavoriteCheckWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(CHECK_WORK, ExistingWorkPolicy.REPLACE, req)
    }

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        0,
        Intent(ctx, FavoriteAlarmReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Epoch millis of the next [hourOfDay]:00 local time strictly after [now].
     * Built as a local date-time — "midnight + N minutes" is an hour off on
     * the 23 h / 25 h days when daylight saving starts or ends.
     */
    internal fun nextTriggerAt(hourOfDay: Int, now: Instant, tz: TimeZone): Long {
        val today = now.toLocalDateTime(tz).date
        val todayAt = LocalDateTime(today, LocalTime(hourOfDay, 0)).toInstant(tz)
        val target = if (todayAt > now) todayAt
        else LocalDateTime(today.plus(1, DateTimeUnit.DAY), LocalTime(hourOfDay, 0)).toInstant(tz)
        return target.toEpochMilliseconds()
    }
}

/** Fires the daily check and re-arms after boot, app updates and clock changes. */
class FavoriteAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FavoriteAlarms.ACTION_ALARM) {
            FavoriteAlarms.enqueueCheck(context)
        }
        // Alarms are one-shot and cleared on reboot: always arm the next one.
        FavoriteAlarms.rearmFromSettings(context)
    }
}

class FavoriteCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    // Expedited work runs as a foreground service before Android 12 and must show a notice.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationScheduler.ensureSyncChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, NotificationScheduler.SYNC_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Speiseplan wird geprüft")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        return ForegroundInfo(NotificationScheduler.SYNC_NOTIFICATION_ID, notification)
    }

    private data class Reminder(val dish: String, val date: LocalDate, val canteens: MutableSet<String>)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        NotificationScheduler.ensureChannel(ctx)
        val settings = FavoriteAlarms.prefs(ctx)
        val favorites = FavoritesManager(settings).favorites.value
        if (favorites.isEmpty()) return@withContext Result.success()

        val strings = stringsFor(Locale.fromTag(settings.getStringOrNull("locale")))
        val leadDays = settings.getInt("notif_lead_days", 3).coerceAtLeast(0)
        val tz = TimeZone.currentSystemDefault()
        val today: LocalDate = Clock.System.todayIn(tz)

        val reminders = try {
            findReminders(settings, favorites, today, leadDays)
        } catch (t: Throwable) {
            // Offline / server unreachable: retry rather than conclude "no favourites today".
            println("FavoriteCheckWorker: fetch failed (attempt $runAttemptCount): ${t.message}")
            return@withContext if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
        post(ctx, settings, strings, today, reminders)
        Result.success()
    }

    private suspend fun findReminders(
        settings: Settings,
        favorites: Set<String>,
        today: LocalDate,
        leadDays: Int,
    ): List<Reminder> {
        // "Nur Stamm-Mensa" restricts the search to the selected canteen.
        val onlyHomeCanteen = settings.getBoolean("notif_only_home", false)
        val activeSlug = settings.getStringOrNull("selected_canteen_slug")
        val disabledCanteenIds = settings.getStringOrNull("disabled_canteens")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

        val repo = MealsRepository(SupabaseProvider.client().postgrest)
        // Same canteen set the app shows (no cafés), minus the ones the user hid.
        val canteens = repo.fetchCanteens()
            .filter { it.id !in disabledCanteenIds }
            .filter { c -> CanteenStaticData.matchFor(c.name)?.let { !onlyHomeCanteen || it.slug == activeSlug } ?: false }

        val window = (0..leadDays).map { today.plus(it, DateTimeUnit.DAY) }.toSet()
        val byDishAndDay = linkedMapOf<String, Reminder>()
        for (canteen: Canteen in canteens) {
            val mealsByDate = repo.fetchMealsForCanteen(canteen.id)
            for ((date, meals) in mealsByDate) {
                if (date !in window) continue
                for (md in meals) {
                    // Rows kept only as a closed-day memento aren't being served.
                    if (md.deactivatedAt != null) continue
                    // Same key the star in the app stores (allergen codes stripped).
                    val dish = MealEnrichment.enrich(md).cleanTitle.ifBlank { md.meals?.title.orEmpty() }
                    if (dish.isBlank()) continue
                    if (!favorites.containsFavorite(dish) && !favorites.containsFavorite(md.meals?.title.orEmpty())) continue
                    byDishAndDay.getOrPut("$date|${normalizeFavoriteKey(dish)}") { Reminder(dish, date, linkedSetOf()) }
                        .canteens += canteen.name
                }
            }
        }
        return byDishAndDay.values.sortedBy { it.date }
    }

    /**
     * One notification per dish and day, posted once when it first shows up
     * in the look-ahead and once more on the day itself (same id, so the
     * day-of reminder replaces the earlier one). Already-sent reminders are
     * remembered, so a retry or "Push jetzt prüfen" doesn't repeat them.
     */
    private fun post(ctx: Context, settings: Settings, strings: Strings, today: LocalDate, reminders: List<Reminder>) {
        if (!NotificationScheduler.canPost(ctx)) return // not remembered: posts once permission is back
        val sent = settings.getStringOrNull(SENT_KEY).orEmpty().split("\n")
            .filter { entry -> runCatching { LocalDate.parse(entry.substringBefore('|')) >= today }.getOrDefault(false) }
            .toMutableSet()
        for (r in reminders) {
            val stage = if (r.date == today) "today" else "ahead"
            val key = "${r.date}|${normalizeFavoriteKey(r.dish)}|$stage"
            if (key in sent) continue
            val id = "${r.date}|${normalizeFavoriteKey(r.dish)}".hashCode()
            val notification = NotificationCompat.Builder(ctx, NotificationScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle(strings.notificationFavoriteTitle(r.dish, dayLabel(strings, today, r.date)))
                .setContentText(r.canteens.joinToString(", "))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(NotificationScheduler.openAppIntent(ctx, id))
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(id, notification)
                sent += key
            } catch (e: SecurityException) {
                return // permission revoked mid-run
            }
        }
        settings.putString(SENT_KEY, sent.joinToString("\n"))
    }

    private fun dayLabel(s: Strings, today: LocalDate, target: LocalDate): String =
        when ((target.toEpochDays() - today.toEpochDays()).toInt()) {
            0 -> s.today.lowercase()
            1 -> s.tomorrow.lowercase()
            else -> s.weekdaysShort[(target.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)]
        }

    private companion object {
        /** About 1 + 2 + … + 64 min of backoff — gives up within ~2 h, the next alarm tries again. */
        const val MAX_ATTEMPTS = 7
        const val SENT_KEY = "notif_sent_reminders"
    }
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) getString(key, "") else null
