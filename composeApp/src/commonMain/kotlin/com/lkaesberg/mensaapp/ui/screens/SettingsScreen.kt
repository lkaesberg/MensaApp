package com.lkaesberg.mensaapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.UserRole
import com.lkaesberg.mensaapp.i18n.LocalStrings
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.components.MTopBar

@Composable
fun SettingsScreen(
    state: MealsAppState,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onBack: () -> Unit,
    requestNotificationPermission: ((granted: Boolean) -> Unit) -> Unit = { it(false) },
) {
    val palette = MensaTheme.palette
    val settings = state.settings
    val role by state.userRole.collectAsState()
    val canteens by state.canteens.collectAsState()
    val disabledCanteenIds by state.disabledCanteenIds.collectAsState()
    var notifyFavorites by remember { mutableStateOf(settings.getBoolean("notif_favorites", false)) }
    var leadDays by remember { mutableStateOf(settings.getInt("notif_lead_days", 3)) }
    var notifyHour by remember { mutableStateOf(settings.getInt("notif_time_hour", 9)) }
    var onlyHomeCanteen by remember { mutableStateOf(settings.getBoolean("notif_only_home", false)) }

    fun saveAndReschedule() {
        settings.putBoolean("notif_favorites", notifyFavorites)
        settings.putInt("notif_lead_days", leadDays)
        settings.putInt("notif_time_hour", notifyHour)
        settings.putBoolean("notif_only_home", onlyHomeCanteen)
        state.notificationScheduler.setEnabled(notifyFavorites, leadDays, notifyHour)
    }

    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        MTopBar(title = LocalStrings.current.settings, onBack = onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader("PERSON")
            Column(modifier = Modifier.background(palette.surface).padding(16.dp)) {
                Text("Personenkreis", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Welcher Preis wird hervorgehoben?", color = palette.sub, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserRole.entries.forEach { option ->
                        val active = role == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) palette.forest else palette.paper)
                                .border(1.dp, if (active) Color.Transparent else palette.hair, RoundedCornerShape(100.dp))
                                .clickable { state.setUserRole(option) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                option.longLabel,
                                color = if (active) Color.White else palette.ink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            SectionHeader("ERSCHEINUNGSBILD")
            SettingRow(
                "Dunkler Modus",
                if (isDarkMode) "Dunkles Farbschema aktiv" else "Helles Farbschema aktiv",
                checked = isDarkMode,
                last = true,
                onChange = { onToggleDarkMode() },
            )

            // Sprache / Language picker (DE/EN). Wired to state.locale; changes
            // immediately re-render the whole tree via LocalStrings.
            val locale by state.locale.collectAsState()
            val s = LocalStrings.current
            SectionHeader(s.settingsLanguage.uppercase())
            Column(modifier = Modifier.background(palette.surface).padding(16.dp)) {
                Text(s.settingsLanguage, color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Locale.De to s.settingsLanguageDe, Locale.En to s.settingsLanguageEn).forEach { (option, label) ->
                        val active = locale == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) palette.forest else palette.paper)
                                .border(1.dp, if (active) Color.Transparent else palette.hair, RoundedCornerShape(100.dp))
                                .clickable { state.setLocale(option) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (active) Color.White else palette.ink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            if (canteens.isNotEmpty()) {
                SectionHeader("MENSEN")
                val sorted = remember(canteens) { canteens.sortedBy { it.name.lowercase() } }
                sorted.forEachIndexed { i, canteen ->
                    SettingRow(
                        label = canteen.name,
                        sub = "Im Picker und in Listen anzeigen",
                        checked = canteen.id !in disabledCanteenIds,
                        last = i == sorted.lastIndex,
                        onChange = { state.setCanteenEnabled(canteen.id, it) },
                    )
                    if (i != sorted.lastIndex) DividerLine()
                }
            }

            if (state.notificationScheduler.isSupported()) {
            SectionHeader("BENACHRICHTIGUNGEN")
            SettingRow(
                "Favoriten-Erinnerung",
                "Push, wenn ein Favorit auf dem Plan steht",
                checked = notifyFavorites,
                onChange = { wantsOn ->
                    // Trust the user's intent. Toggle never auto-reverts —
                    // the permission-request callback can return false even
                    // when the OS truly allows notifications (e.g. previously
                    // denied → manually re-enabled in system settings), so
                    // letting it flip the slider back was misleading. The
                    // worker silently no-ops if permission is actually missing.
                    notifyFavorites = wantsOn
                    saveAndReschedule()
                    if (wantsOn && !state.notificationScheduler.isPermitted()) {
                        requestNotificationPermission { /* informational */ }
                    }
                },
            )
            // The reminders can't be posted while the system blocks them; say so
            // instead of leaving the switch on and staying silent.
            if (notifyFavorites && !state.notificationScheduler.isPermitted()) {
                Text(
                    text = "Benachrichtigungen sind in den Systemeinstellungen für die App ausgeschaltet – Erinnerungen können nicht angezeigt werden.",
                    color = palette.closed,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.background(palette.surface).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            DividerLine()
            Column(modifier = Modifier.background(palette.surface).padding(16.dp)) {
                Text("Voraus-Reichweite", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Wie weit im Voraus erinnern?", color = palette.sub, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val options = listOf(0 to "Heute", 1 to "Morgen", 3 to "3 Tage", 7 to "Diese Wo")
                    options.forEach { (days, label) ->
                        val active = leadDays == days
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) palette.forest else palette.paper)
                                .border(1.dp, if (active) Color.Transparent else palette.hair, RoundedCornerShape(100.dp))
                                .clickable { leadDays = days; saveAndReschedule() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (active) Color.White else palette.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            DividerLine()
            Column(modifier = Modifier.background(palette.surface).padding(16.dp)) {
                Text("Sendezeit", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Tägliche Push um …", color = palette.sub, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (6..22).forEach { hour ->
                        val active = notifyHour == hour
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) palette.forest else palette.paper)
                                .border(1.dp, if (active) Color.Transparent else palette.hair, RoundedCornerShape(100.dp))
                                .clickable { notifyHour = hour; saveAndReschedule() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${hour.toString().padStart(2, '0')}:00",
                                color = if (active) Color.White else palette.ink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            DividerLine()
            SettingRow(
                "Nur Stamm-Mensa",
                "Andere Standorte ignorieren",
                checked = onlyHomeCanteen,
                last = true,
                onChange = { onlyHomeCanteen = it; saveAndReschedule() },
            )

            Spacer(Modifier.height(8.dp))
            var testFeedback by remember { mutableStateOf<String?>(null) }
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.moss)
                    .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                    .clickable {
                        val favorites = state.favoritesManager.favorites.value
                        when {
                            favorites.isEmpty() -> {
                                testFeedback = "Du hast noch keine Favoriten. Markiere ein Gericht mit ★."
                            }
                            // Already permitted — fire immediately. Skips the
                            // permission round-trip so a granted permission
                            // never gets misreported as "not allowed".
                            state.notificationScheduler.isPermitted() -> {
                                state.notificationScheduler.runCheckNow()
                                testFeedback = "Suche läuft. Eine Push erscheint gleich, falls ein Favorit auf dem Plan steht."
                            }
                            else -> {
                                requestNotificationPermission { granted ->
                                    if (granted) {
                                        state.notificationScheduler.runCheckNow()
                                        testFeedback = "Suche läuft. Eine Push erscheint gleich, falls ein Favorit auf dem Plan steht."
                                    } else {
                                        testFeedback = "Push nicht erlaubt. Bitte Berechtigung in den App-Einstellungen freigeben."
                                    }
                                }
                            }
                        }
                    }
                    .padding(14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column {
                    Text("Push jetzt prüfen", color = palette.forestDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Sendet Erinnerungen für heutige Favoriten-Treffer", color = palette.sub, fontSize = 11.sp)
                    val feedback = testFeedback
                    if (feedback != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(feedback, color = palette.sub, fontSize = 11.sp)
                    }
                }
            }
            } // end notification section
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val palette = MensaTheme.palette
    Text(
        text = title,
        color = palette.sub,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingRow(
    label: String,
    sub: String? = null,
    checked: Boolean,
    last: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    val palette = MensaTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth().background(palette.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = palette.sub, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.forest,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = palette.hair,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = palette.hair,
            ),
        )
    }
}

@Composable
private fun DividerLine() {
    val palette = MensaTheme.palette
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.hair))
}
