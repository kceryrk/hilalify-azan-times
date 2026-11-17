package com.kceryrk.prayertimes

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private data class Choice(val value: String, val labelRes: Int)

/** Settings page. Changes are saved immediately and applied to the notification, widget and alarms. */
@Composable
fun SettingsScreen(onGpsToggle: (Boolean) -> Unit, onCity: () -> Unit, onRecreate: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 120.dp)
    ) {
        Text(
            stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Section(R.string.sec_general) {
            var gps by remember { mutableStateOf(PrayerRepo.isGpsMode(ctx)) }
            SwitchRow(stringResource(R.string.use_gps), stringResource(R.string.use_gps_desc), gps) {
                gps = it
                onGpsToggle(it)
            }
            RowDivider()
            if (gps) {
                Text(
                    PrayerRepo.locationLabel(ctx)?.let { stringResource(R.string.gps_city, it) }
                        ?: stringResource(R.string.gps_city_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCity)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.set_city), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Chevron()
                }
            }
            RowDivider()
            PrefSwitch(stringResource(R.string.travel_mode), "travel_mode", def = false) {}
            Text(
                stringResource(R.string.travel_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            RowDivider()
            PrefChoice(
                stringResource(R.string.language),
                AppSettings.str(ctx, "language", "system"),
                listOf(
                    Choice("system", R.string.lang_system),
                    Choice("en", R.string.lang_en),
                    Choice("tr", R.string.lang_tr),
                    Choice("ar", R.string.lang_ar),
                    Choice("ur", R.string.lang_ur),
                    Choice("fa", R.string.lang_fa),
                    Choice("ps", R.string.lang_ps),
                    Choice("bn", R.string.lang_bn),
                    Choice("hi", R.string.lang_hi),
                    Choice("ta", R.string.lang_ta),
                    Choice("in", R.string.lang_in),
                    Choice("ms", R.string.lang_ms),
                    Choice("fil", R.string.lang_fil),
                    Choice("fr", R.string.lang_fr),
                    Choice("de", R.string.lang_de),
                    Choice("es", R.string.lang_es),
                    Choice("pt", R.string.lang_pt),
                    Choice("it", R.string.lang_it),
                    Choice("nl", R.string.lang_nl),
                    Choice("pl", R.string.lang_pl),
                    Choice("ru", R.string.lang_ru),
                    Choice("uk", R.string.lang_uk),
                    Choice("ro", R.string.lang_ro),
                    Choice("sq", R.string.lang_sq),
                    Choice("bs", R.string.lang_bs),
                    Choice("az", R.string.lang_az),
                    Choice("kk", R.string.lang_kk),
                    Choice("uz", R.string.lang_uz),
                    Choice("ky", R.string.lang_ky),
                    Choice("tk", R.string.lang_tk),
                    Choice("tg", R.string.lang_tg),
                    Choice("ha", R.string.lang_ha),
                    Choice("sw", R.string.lang_sw),
                    Choice("so", R.string.lang_so),
                    Choice("zh-CN", R.string.lang_zh),
                    Choice("ja", R.string.lang_ja),
                    Choice("ko", R.string.lang_ko)
                )
            ) {
                AppSettings.put(ctx, "language", it)
                onRecreate()
            }
        }

        Section(R.string.sec_appearance) {
            PrefChoice(
                stringResource(R.string.theme),
                AppSettings.str(ctx, "theme", "system"),
                listOf(Choice("system", R.string.theme_system), Choice("light", R.string.theme_light), Choice("dark", R.string.theme_dark))
            ) {
                AppSettings.put(ctx, "theme", it)
                onRecreate()
            }
            RowDivider()
            Text(
                stringResource(R.string.accent),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                textAlign = TextAlign.Center
            )
            val current = AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                maxItemsInEachRow = 6,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ACCENT_COLORS.forEach { argb ->
                    val value = argb.toInt()
                    val selected = value == current
                    Box(
                        Modifier
                            .size(44.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                AppSettings.put(ctx, "accent", value)
                                onRecreate()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(if (selected) 42.dp else 34.dp)
                                .clip(CircleShape)
                                .background(Color(value))
                                .border(
                                    if (selected) 3.dp else 0.dp,
                                    if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }

        Section(R.string.sec_times) {
            PrefChoice(
                stringResource(R.string.method),
                AppSettings.method(ctx).toString(),
                listOf(
                    Choice("13", R.string.method_13),
                    Choice("3", R.string.method_3),
                    Choice("2", R.string.method_2),
                    Choice("4", R.string.method_4),
                    Choice("5", R.string.method_5),
                )
            ) {
                AppSettings.put(ctx, "method", it.toInt())
                Thread {
                    PrayerRepo.refresh(ctx.applicationContext)
                    Alarms.scheduleNext(ctx.applicationContext)
                    Alarms.updateStatus(ctx.applicationContext)
                    PrayerWidget.refreshAll(ctx.applicationContext)
                }.start()
            }
            RowDivider()
            var refreshDays by remember { mutableFloatStateOf(AppSettings.refreshDays(ctx).toFloat()) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "${stringResource(R.string.refresh)}: ${stringResource(R.string.refresh_days, refreshDays.roundToInt())}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = refreshDays,
                    onValueChange = { refreshDays = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    onValueChangeFinished = {
                        AppSettings.put(ctx, "refresh_days", refreshDays.roundToInt())
                        RefreshWorker.schedule(ctx)
                    }
                )
            }
        }

        Section(R.string.sec_alarms) {
            GroupDialogRow(
                title = stringResource(R.string.group_prayer_alarms),
                subtitle = stringResource(R.string.sec_alarms)
            ) {
                Prayer.values().filter { it.notify }.forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    PrefSwitch(stringResource(R.string.alarm_for, stringResource(p.labelRes)), "alarm_${p.name}") {
                        Alarms.scheduleNext(ctx)
                    }
                }
            }
            RowDivider()
            PrefSwitch(stringResource(R.string.ramadan_mode), "ramadan_mode", def = false) {
                Alarms.scheduleSahur(ctx)
            }
            Text(
                stringResource(R.string.ramadan_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            RowDivider()
            NotificationSoundPickerRow()
        }

        Section(R.string.sec_reminders) {
            PrefSwitch(stringResource(R.string.jumuah_reminder), "jumuah_reminder", def = false) {
                Alarms.scheduleJumuah(ctx)
            }
            Text(
                stringResource(R.string.jumuah_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            RowDivider()
            var qadaOn by remember { mutableStateOf(AppSettings.bool(ctx, "qada_reminder", def = true)) }
            SwitchRow(stringResource(R.string.qada_reminder), null, qadaOn) {
                qadaOn = it
                AppSettings.put(ctx, "qada_reminder", it)
                Alarms.scheduleQada(ctx)
            }
            Text(
                stringResource(R.string.qada_desc_setting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (qadaOn) {
                var qadaHour by remember { mutableFloatStateOf(AppSettings.int(ctx, "qada_reminder_hour", 21).toFloat()) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.qada_reminder_time, "%02d:00".format(qadaHour.roundToInt())),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = qadaHour,
                        onValueChange = { qadaHour = it },
                        valueRange = 0f..23f,
                        steps = 22,
                        onValueChangeFinished = {
                            AppSettings.put(ctx, "qada_reminder_hour", qadaHour.roundToInt())
                            Alarms.scheduleQada(ctx)
                        }
                    )
                }
            }
        }

        Section(R.string.sec_silence) {
            val dndGranted = ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
            var silenceOn by remember { mutableStateOf(AppSettings.bool(ctx, "silence_on", def = false)) }
            SwitchRow(stringResource(R.string.silence_on), null, silenceOn) {
                silenceOn = it
                AppSettings.put(ctx, "silence_on", it)
            }
            Text(
                stringResource(R.string.silence_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (silenceOn) {
                RowDivider()
                SilencePrayersRow()
            }
            RowDivider()
            var silenceCuma by remember { mutableStateOf(AppSettings.bool(ctx, "silence_cuma", def = dndGranted)) }
            SwitchRow(stringResource(R.string.silence_cuma), null, silenceCuma) {
                silenceCuma = it
                AppSettings.put(ctx, "silence_cuma", it)
            }
            Text(
                stringResource(R.string.silence_cuma_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (silenceOn || silenceCuma) {
                RowDivider()
                var silenceMin by remember { mutableFloatStateOf(AppSettings.int(ctx, "silence_min", 20).toFloat()) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "${stringResource(R.string.silence_minutes)}: ${silenceMin.roundToInt()} ${stringResource(R.string.min_short)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = silenceMin,
                        onValueChange = { silenceMin = it },
                        valueRange = 10f..60f,
                        steps = 9,
                        onValueChangeFinished = {
                            AppSettings.put(ctx, "silence_min", silenceMin.roundToInt())
                        }
                    )
                }
            }
        }

        Section(R.string.sec_notif) {
            var notifOn by remember { mutableStateOf(AppSettings.bool(ctx, "notif_on", def = true)) }
            var newNotifStyle by remember {
                mutableStateOf(AppSettings.bool(ctx, "new_notif_style", def = AppSettings.isHyperOs3Plus()))
            }
            GroupDialogRow(
                title = stringResource(R.string.group_notif_settings),
                subtitle = stringResource(R.string.notif_on)
            ) {
                SwitchRow(stringResource(R.string.notif_on), null, notifOn) {
                    notifOn = it
                    AppSettings.put(ctx, "notif_on", it)
                    Alarms.updateStatus(ctx)
                }
                if (notifOn) {
                    RowDivider()
                    PrefSwitch(stringResource(R.string.notif_times), "notif_times") { Alarms.updateStatus(ctx) }
                    RowDivider()
                    var realMoon by remember { mutableStateOf(AppSettings.bool(ctx, "real_moon_icon", def = true)) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RealMoonPreview(realMoon)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.real_moon_icon), Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        IosSwitch(checked = realMoon) {
                            realMoon = it
                            AppSettings.put(ctx, "real_moon_icon", it)
                            Alarms.updateStatus(ctx)
                        }
                    }
                    RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                newNotifStyle = !newNotifStyle
                                AppSettings.put(ctx, "new_notif_style", newNotifStyle)
                                if (newNotifStyle) Alarms.triggerRestingPeek(ctx, autoHide = !Alarms.appInForeground)
                                else {
                                    Alarms.cancelRestingPeek(ctx)
                                    Alarms.updateStatus(ctx)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.new_notif_style), style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.beta_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                stringResource(R.string.new_notif_style_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IosSwitch(
                            checked = newNotifStyle,
                            onCheckedChange = {
                                newNotifStyle = it
                                AppSettings.put(ctx, "new_notif_style", it)
                                if (it) Alarms.triggerRestingPeek(ctx, autoHide = !Alarms.appInForeground)
                                else {
                                    Alarms.cancelRestingPeek(ctx)
                                    Alarms.updateStatus(ctx)
                                }
                            }
                        )
                    }
                    RowDivider()
                    PrefSwitch(stringResource(R.string.notif_hide_lockscreen), "notif_hide_lockscreen", def = false) {
                        Alarms.updateStatus(ctx)
                    }
                }
                RowDivider()
                PrefSwitch(stringResource(R.string.adhan_hide_lockscreen), "adhan_hide_lockscreen", def = false) {}
            }
            if (android.os.Build.VERSION.SDK_INT >= 36 && notifOn && newNotifStyle) {
                RowDivider()
                GroupDialogRow(
                    title = islandStr(R.string.group_island_settings, R.string.group_live_settings),
                    subtitle = islandStr(R.string.island_live, R.string.live_update_on)
                ) {
                    var islandLive by remember { mutableStateOf(AppSettings.bool(ctx, "island_live", def = true)) }
                    SwitchRow(islandStr(R.string.island_live, R.string.live_update_on), null, islandLive) {
                        islandLive = it
                        AppSettings.put(ctx, "island_live", it)
                        if (it) Alarms.triggerRestingPeek(ctx, autoHide = !Alarms.appInForeground)
                        else {
                            Alarms.cancelRestingPeek(ctx)
                            Alarms.updateStatus(ctx)
                        }
                    }
                    if (islandLive) {
                        RowDivider()
                        var islandVakit by remember {
                            mutableStateOf(AppSettings.str(ctx, "island_vakit", "current"))
                        }
                        InlineChoiceGroup(
                            islandStr(R.string.island_vakit, R.string.live_update_vakit),
                            islandVakit,
                            listOf(
                                Choice("next", R.string.island_vakit_next),
                                Choice("current", R.string.island_vakit_current),
                            )
                        ) {
                            islandVakit = it
                            AppSettings.put(ctx, "island_vakit", it)
                            Alarms.triggerRestingPeek(ctx, autoHide = !Alarms.appInForeground)
                        }
                        RowDivider()
                        var restingMode by remember {
                            mutableStateOf(AppSettings.str(ctx, "resting_island_mode", "timeout"))
                        }
                        InlineChoiceGroup(
                            islandStr(R.string.resting_island_mode, R.string.resting_live_mode),
                            restingMode,
                            listOf(
                                Choice("always", R.string.resting_island_always),
                                Choice("timeout", R.string.resting_island_timeout),
                                Choice("never", R.string.resting_island_never),
                            )
                        ) {
                            restingMode = it
                            AppSettings.put(ctx, "resting_island_mode", it)
                            if (it == "timeout") Alarms.triggerRestingPeek(ctx, autoHide = !Alarms.appInForeground)
                            else {
                                Alarms.cancelRestingPeek(ctx)
                                Alarms.updateStatus(ctx)
                            }
                        }
                        if (restingMode == "timeout") {
                            var restingSec by remember {
                                mutableFloatStateOf(AppSettings.int(ctx, "resting_island_sec", 5).coerceIn(2, 30).toFloat())
                            }
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.resting_island_sec),
                                        Modifier.weight(1f).padding(end = 8.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "${restingSec.roundToInt()} ${stringResource(R.string.sec_short)}",
                                        Modifier.widthIn(min = 56.dp),
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Slider(
                                    value = restingSec,
                                    onValueChange = { restingSec = it.roundToInt().coerceIn(2, 30).toFloat() },
                                    valueRange = 2f..30f,
                                    steps = 27,
                                    onValueChangeFinished = {
                                        AppSettings.put(ctx, "resting_island_sec", restingSec.roundToInt())
                                        Alarms.triggerRestingPeek(ctx, autoHide = !Alarms.appInForeground)
                                    }
                                )
                            }
                        }
                    }
                    RowDivider()
                    PrefSwitch(islandStr(R.string.island_adhan, R.string.live_update_adhan), "island_adhan", def = true) {}
                }
            } else if (android.os.Build.VERSION.SDK_INT >= 36) {
                RowDivider()
                PrefSwitch(islandStr(R.string.island_adhan, R.string.live_update_adhan), "island_adhan", def = true) {}
            }
            RowDivider()
            GroupDialogRow(
                title = stringResource(R.string.group_kerahat_settings),
                subtitle = stringResource(R.string.kerahat_setting)
            ) {
                var kerahatShow by remember { mutableStateOf(AppSettings.bool(ctx, "kerahat", def = true)) }
                SwitchRow(stringResource(R.string.kerahat_setting), null, kerahatShow) {
                    kerahatShow = it
                    AppSettings.put(ctx, "kerahat", it)
                    Alarms.updateStatus(ctx)
                    PrayerWidget.refreshAll(ctx)
                }
                if (kerahatShow) {
                    var kerahatMin by remember { mutableFloatStateOf(AppSettings.int(ctx, "kerahat_min", 45).toFloat()) }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.kerahat_minutes),
                                Modifier.weight(1f).padding(end = 8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${kerahatMin.roundToInt()} ${stringResource(R.string.min_short)}",
                                Modifier.widthIn(min = 56.dp),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Slider(
                            value = kerahatMin,
                            onValueChange = { kerahatMin = it },
                            valueRange = 20f..60f,
                            steps = 7,
                            onValueChangeFinished = {
                                AppSettings.put(ctx, "kerahat_min", kerahatMin.roundToInt())
                                Alarms.updateStatus(ctx)
                                PrayerWidget.refreshAll(ctx)
                            }
                        )
                    }
                }
                RowDivider()
                var kerahatAlarmOn by remember { mutableStateOf(AppSettings.bool(ctx, "kerahat_alarm", def = true)) }
                SwitchRow(stringResource(R.string.kerahat_alarm), null, kerahatAlarmOn) {
                    kerahatAlarmOn = it
                    AppSettings.put(ctx, "kerahat_alarm", it)
                    Alarms.scheduleKerahat(ctx)
                }
                if (kerahatAlarmOn) {
                    var leadMin by remember {
                        mutableFloatStateOf(((AppSettings.int(ctx, "kerahat_alarm_min", 15).coerceIn(5, 50) + 2) / 5 * 5).toFloat())
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.kerahat_alarm_min),
                                Modifier.weight(1f).padding(end = 8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${leadMin.roundToInt()} ${stringResource(R.string.min_short)}",
                                Modifier.widthIn(min = 56.dp),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Slider(
                            value = leadMin,
                            onValueChange = { leadMin = ((it / 5f).roundToInt() * 5).coerceIn(5, 50).toFloat() },
                            valueRange = 5f..50f,
                            steps = 8,
                            onValueChangeFinished = {
                                AppSettings.put(ctx, "kerahat_alarm_min", leadMin.roundToInt())
                                Alarms.scheduleKerahat(ctx)
                            }
                        )
                    }
                }
            }
        }

        Section(R.string.sec_widget) {
            GroupDialogRow(
                title = stringResource(R.string.group_widget_settings),
                subtitle = stringResource(R.string.sec_widget)
            ) {
                var opacity by remember { mutableFloatStateOf(AppSettings.int(ctx, "widget_opacity", 100).toFloat()) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "${stringResource(R.string.widget_opacity)}: ${opacity.roundToInt()}%",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 20f..100f,
                        onValueChangeFinished = {
                            AppSettings.put(ctx, "widget_opacity", opacity.roundToInt())
                            PrayerWidget.refreshAll(ctx)
                        }
                    )
                }
                RowDivider()
                PrefSwitch(stringResource(R.string.widget_show_next), "widget_next") { PrayerWidget.refreshAll(ctx) }
                RowDivider()
                PrefSwitch(stringResource(R.string.widget_show_times), "widget_times") { PrayerWidget.refreshAll(ctx) }
                RowDivider()
                PrefSwitch(stringResource(R.string.widget_fill_square), "widget_fill_square", def = false) {
                    PrayerWidget.refreshAll(ctx)
                }
            }
        }

        Section(R.string.sec_integrations) {
            var iqamaDelay by remember { mutableFloatStateOf(AppSettings.int(ctx, "iqama_delay", 0).toFloat()) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.iqama_delay_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.iqama_delay_value, iqamaDelay.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = iqamaDelay,
                    onValueChange = { iqamaDelay = it },
                    valueRange = 0f..30f,
                    steps = 29,
                    onValueChangeFinished = { AppSettings.put(ctx, "iqama_delay", iqamaDelay.roundToInt()) }
                )
                Text(
                    stringResource(R.string.iqama_delay_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RowDivider()
            var smartHome by remember { mutableStateOf(AppSettings.str(ctx, "smart_home_url", "")) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.smart_home_title), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = smartHome,
                    onValueChange = {
                        smartHome = it
                        AppSettings.put(ctx, "smart_home_url", it.trim())
                    },
                    placeholder = { Text(stringResource(R.string.smart_home_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.smart_home_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Section(R.string.sec_permissions) {
            GroupDialogRow(
                title = stringResource(R.string.group_permissions),
                subtitle = stringResource(R.string.sec_permissions)
            ) {
                val tick = rememberPermissionTick()
                val dndGranted = remember(tick) { isDndGranted(ctx) }
                val exactGranted = remember(tick) { canExactAlarms(ctx) }
                val notifGranted = remember(tick) { areNotificationsOn(ctx) }
                PermissionRow(stringResource(R.string.perm_notif_policy), dndGranted) {
                    ctx.startActivity(
                        Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                RowDivider()
                PermissionRow(stringResource(R.string.perm_exact), exactGranted) {
                    ctx.startActivity(
                        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                RowDivider()
                PermissionRow(stringResource(R.string.perm_notif), notifGranted) {
                    ctx.startActivity(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                if (android.os.Build.VERSION.SDK_INT >= 36) {
                    val promotedGranted = remember(tick) { canPromotedNotifs(ctx) }
                    RowDivider()
                    PermissionRow(islandStr(R.string.perm_island_title, R.string.perm_live_title), promotedGranted) {
                        val intent = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            ctx.startActivity(intent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            ctx.startActivity(
                                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
                RowDivider()
                ListItemRow(
                    title = stringResource(R.string.background_settings),
                    subtitle = stringResource(R.string.background_settings_desc)
                ) { openBackgroundSettings(ctx) }
            }
        }

        Section(R.string.sec_about) {
            val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.kofi_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                GlassPrimaryButton(
                    text = stringResource(R.string.kofi),
                    accent = Color(AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)),
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/kaecr")))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
    }
}

private fun openBackgroundSettings(ctx: android.content.Context) {
    val vendorIntent = Intent().setComponent(
        android.content.ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    )
    val appDetails = Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${ctx.packageName}")
    )
    try {
        ctx.startActivity(vendorIntent)
    } catch (_: android.content.ActivityNotFoundException) {
        ctx.startActivity(appDetails)
    }
}

private fun isDndGranted(ctx: android.content.Context): Boolean =
    ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted

private fun canExactAlarms(ctx: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 31 || ctx.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()

private fun areNotificationsOn(ctx: android.content.Context): Boolean =
    androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()

private fun canPromotedNotifs(ctx: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 36 ||
        ctx.getSystemService(android.app.NotificationManager::class.java).canPostPromotedNotifications()

/** Counter that increments on each resume, so permission checks re-run after returning from system settings. */
@Composable
private fun rememberPermissionTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (granted) R.string.perm_granted else R.string.perm_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(12.dp)) {
                    val stroke = 2.dp.toPx()
                    drawPath(
                        Path().apply {
                            moveTo(0f, size.height * 0.5f)
                            lineTo(size.width * 0.38f, size.height * 0.9f)
                            lineTo(size.width, size.height * 0.1f)
                        },
                        color = Color.White,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        } else {
            Chevron()
        }
    }
}

@Composable
private fun SilencePrayersRow() {
    var open by remember { mutableStateOf(false) }
    ListItemRow(stringResource(R.string.silence_prayers), "") { open = true }
    if (open) {
        GlassAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.silence_pick_title)) },
            text = {
                Column {
                    Prayer.values().filter { it.notify }.forEachIndexed { i, prayer ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SilencePrayerCheck(prayer)
                    }
                }
            },
            confirmButton = { GlassDialogAction(stringResource(R.string.done)) { open = false } }
        )
    }
}

@Composable
private fun SilencePrayerCheck(prayer: Prayer) {
    val ctx = LocalContext.current
    val key = "silence_${prayer.name}"
    var checked by remember(prayer) { mutableStateOf(AppSettings.bool(ctx, key, true)) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                AppSettings.put(ctx, key, checked)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(prayer.labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ListItemRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun Section(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
internal fun islandStr(hyperOsRes: Int, genericRes: Int): String =
    stringResource(if (remember { AppSettings.isHyperOs() }) hyperOsRes else genericRes)

@Composable
private fun InlineChoiceGroup(title: String, value: String, choices: List<Choice>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        choices.forEach { ch ->
            val selected = ch.value == value
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(ch.value) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = { onPick(ch.value) })
                Text(stringResource(ch.labelRes), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun RealMoonPreview(realMoon: Boolean) {
    val moonBmp = remember { Alarms.moonPhaseIcon().asImageBitmap() }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xFF1C1C1E))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (realMoon) {
            Image(
                bitmap = moonBmp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IosSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** Flat iOS-style pill toggle: accent-coloured track when on, white pill thumb that slides between the states. */
@Composable
internal fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, label = "ios-switch")
    val accent = Color(AppSettings.int(LocalContext.current, "accent", DEFAULT_ACCENT_ARGB))
    val track = lerp(Color(0xFF39393D), accent, progress)
    Box(
        Modifier
            .size(width = 51.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = 18.5.dp * progress)
                .size(width = 28.5.dp, height = 20.5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

/** Boolean preference stored in AppSettings. [onChange] runs after the value is saved. */
@Composable
private fun PrefSwitch(title: String, key: String, def: Boolean = true, onChange: () -> Unit) {
    val ctx = LocalContext.current
    var checked by remember(key) { mutableStateOf(AppSettings.bool(ctx, key, def)) }
    SwitchRow(title, null, checked) {
        checked = it
        AppSettings.put(ctx, key, it)
        onChange()
    }
}

@Composable
private fun Chevron() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
        val stroke = 2.dp.toPx()
        drawPath(
            Path().apply {
                moveTo(stroke, stroke)
                lineTo(size.width - stroke, size.height / 2f)
                lineTo(stroke, size.height - stroke)
            },
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun PrefChoice(title: String, current: String, choices: List<Choice>, onPick: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    var open by remember { mutableStateOf(false) }
    val label = choices.firstOrNull { it.value == value }?.labelRes ?: choices.first().labelRes

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Chevron()
    }

    if (open) {
        GlassAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    value = c.value
                                    open = false
                                    onPick(c.value)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = value == c.value, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(c.labelRes))
                        }
                    }
                }
            },
            confirmButton = { GlassDialogAction(stringResource(R.string.cancel)) { open = false } }
        )
    }
}

@Composable
private fun GroupDialogRow(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Chevron()
    }
    if (open) {
        GlassAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), content = content)
            },
            confirmButton = { GlassDialogAction(stringResource(R.string.done)) { open = false } }
        )
    }
}

@Composable
private fun NotificationSoundPickerRow() {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(AppSettings.str(ctx, "notif_sound_mode", "default")) }
    var uriStr by remember { mutableStateOf(AppSettings.str(ctx, "notif_sound_uri", "")) }
    var open by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val picked = res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (picked == null) {
            mode = "silent"
            uriStr = ""
        } else {
            mode = "custom"
            uriStr = picked.toString()
        }
        AppSettings.put(ctx, "notif_sound_mode", mode)
        AppSettings.put(ctx, "notif_sound_uri", uriStr)
        Alarms.adhanChannelId(ctx)
        open = false
    }

    val customFallback = stringResource(R.string.sound_custom)
    val currentLabel = when (mode) {
        "silent" -> stringResource(R.string.sound_silent)
        "custom" -> runCatching {
            if (uriStr.isEmpty()) customFallback
            else RingtoneManager.getRingtone(ctx, Uri.parse(uriStr))?.getTitle(ctx) ?: customFallback
        }.getOrElse { customFallback }
        else -> stringResource(R.string.sound_default)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.notif_sound), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(currentLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Chevron()
    }

    if (open) {
        GlassAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.notif_sound)) },
            text = {
                Column {
                    listOf(
                        "default" to R.string.sound_default,
                        "silent" to R.string.sound_silent,
                        "custom" to R.string.sound_custom
                    ).forEach { (v, resId) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (v == "custom") {
                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            if (uriStr.isNotEmpty()) {
                                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(uriStr))
                                            }
                                        }
                                        picker.launch(intent)
                                    } else {
                                        mode = v
                                        AppSettings.put(ctx, "notif_sound_mode", v)
                                        Alarms.adhanChannelId(ctx)
                                        open = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mode == v, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(resId))
                        }
                    }
                }
            },
            confirmButton = { GlassDialogAction(stringResource(R.string.cancel)) { open = false } }
        )
    }
}
