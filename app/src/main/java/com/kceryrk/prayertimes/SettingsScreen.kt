package com.kceryrk.prayertimes

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
            PrefChoice(
                stringResource(R.string.language),
                AppSettings.str(ctx, "language", "system"),
                listOf(Choice("system", R.string.lang_system), Choice("en", R.string.lang_en), Choice("tr", R.string.lang_tr))
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0xFF00A86B, 0xFF0A84FF, 0xFFFF375F, 0xFFFF9F0A, 0xFFBF5AF2, 0xFF64D2FF).forEach { argb ->
                    val value = argb.toInt()
                    val selected = value == current
                    Box(
                        Modifier
                            .size(if (selected) 46.dp else 36.dp)
                            .clip(CircleShape)
                            .background(Color(value))
                            .border(
                                if (selected) 3.dp else 0.dp,
                                if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                CircleShape
                            )
                            .clickable {
                                AppSettings.put(ctx, "accent", value)
                                onRecreate()
                            }
                    )
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
            Prayer.values().filter { it.notify }.forEachIndexed { i, p ->
                if (i > 0) RowDivider()
                PrefSwitch(stringResource(R.string.alarm_for, stringResource(p.labelRes)), "alarm_${p.name}") {
                    Alarms.scheduleNext(ctx)
                }
            }
        }

        Section(R.string.sec_notif) {
            PrefSwitch(stringResource(R.string.notif_on), "notif_on") { Alarms.updateStatus(ctx) }
            RowDivider()
            PrefSwitch(stringResource(R.string.notif_times), "notif_times") { Alarms.updateStatus(ctx) }
            RowDivider()
            PrefSwitch(stringResource(R.string.kerahat_setting), "kerahat", def = true) {
                Alarms.updateStatus(ctx)
                PrayerWidget.refreshAll(ctx)
            }
            RowDivider()
            var kerahatMin by remember { mutableFloatStateOf(AppSettings.int(ctx, "kerahat_min", 45).toFloat()) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "${stringResource(R.string.kerahat_minutes)}: ${kerahatMin.roundToInt()} ${stringResource(R.string.min_short)}",
                    style = MaterialTheme.typography.bodyLarge
                )
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

        Section(R.string.sec_widget) {
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
        }

        Section(R.string.sec_permissions) {
            ListItemRow(
                title = stringResource(R.string.background_settings),
                subtitle = stringResource(R.string.background_settings_desc)
            ) { openBackgroundSettings(ctx) }
        }

        Section(R.string.sec_about) {
            val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
            val scope = rememberCoroutineScope()
            var checking by remember { mutableStateOf(false) }
            var checkMessage by remember { mutableStateOf<String?>(null) }
            var update by remember { mutableStateOf<UpdateInfo?>(null) }
            PrefSwitch(stringResource(R.string.updates_on), "updates_on") { }
            RowDivider()
            Button(
                onClick = {
                    checking = true
                    checkMessage = null
                    scope.launch {
                        val info = UpdateChecker.check(ctx)
                        checking = false
                        if (info != null) update = info
                        else checkMessage = ctx.getString(R.string.update_none)
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) { Text(stringResource(if (checking) R.string.update_checking else R.string.update_check)) }
            checkMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            update?.let { UpdateDialog(it) { update = null } }
            RowDivider()
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
                Button(
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/kaecr")))
                    },
                    Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.kofi)) }
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

/** Shows an available update with a button that opens the APK (or release page) in the browser. */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // -1 = not started, 0..100 = downloading, 101 = download finished and installer launched
    var progress by remember { mutableIntStateOf(-1) }
    var failed by remember { mutableStateOf(false) }
    val apkUrl = info.apkUrl
    val busy = progress >= 0 && progress <= 100

    GlassAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.update_available, info.version)) },
        text = {
            when {
                failed -> Text(stringResource(R.string.update_failed))
                progress >= 0 -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 6.dp
                        )
                        Text("${progress.coerceIn(0, 100)}%", style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> Text(stringResource(R.string.update_desc))
            }
        },
        confirmButton = {
            if (!busy) GlassDialogAction(stringResource(R.string.update_download)) {
                if (apkUrl == null) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)))
                    onDismiss()
                    return@GlassDialogAction
                }
                failed = false
                progress = 0
                scope.launch {
                    try {
                        val apk = UpdateChecker.download(ctx, apkUrl) { progress = it }
                        progress = 100
                        if (UpdateChecker.install(ctx, apk)) onDismiss() else progress = -1
                    } catch (e: Exception) {
                        failed = true
                        progress = -1
                    }
                }
            }
        },
        dismissButton = {
            if (!busy) GlassDialogAction(stringResource(R.string.update_later), onDismiss)
        }
    )
}

/** Flat iOS-style pill toggle: accent-coloured track when on, white pill thumb that slides between the states. */
@Composable
private fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
                Column {
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
