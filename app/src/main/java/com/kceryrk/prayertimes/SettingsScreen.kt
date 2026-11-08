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
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
            if (!gps) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onCity) { Text(stringResource(R.string.set_city)) }
                }
            }
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
            Text("Accent", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            val current = AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(0xFF00A86B, 0xFF0A84FF, 0xFFFF375F, 0xFFFF9F0A, 0xFFBF5AF2, 0xFF64D2FF).forEach { argb ->
                    val value = argb.toInt()
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(value))
                            .border(
                                if (value == current) 3.dp else 0.dp,
                                if (value == current) MaterialTheme.colorScheme.onSurface else Color.Transparent,
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
            PrefChoice(
                stringResource(R.string.refresh),
                AppSettings.refreshHours(ctx).toString(),
                listOf(Choice("6", R.string.refresh_6), Choice("12", R.string.refresh_12), Choice("24", R.string.refresh_24))
            ) {
                AppSettings.put(ctx, "refresh_hours", it.toInt())
                RefreshWorker.schedule(ctx)
            }
        }

        Section(R.string.sec_alarms) {
            Prayer.values().filter { it.notify }.forEach { p ->
                PrefSwitch(stringResource(R.string.alarm_for, stringResource(p.labelRes)), "alarm_${p.name}") {
                    Alarms.scheduleNext(ctx)
                }
            }
        }

        Section(R.string.sec_notif) {
            PrefSwitch(stringResource(R.string.notif_on), "notif_on") { Alarms.updateStatus(ctx) }
            PrefSwitch(stringResource(R.string.notif_transparent), "notif_transparent") { Alarms.updateStatus(ctx) }
            PrefSwitch(stringResource(R.string.notif_title), "notif_title") { Alarms.updateStatus(ctx) }
            PrefSwitch(stringResource(R.string.notif_next), "notif_next") { Alarms.updateStatus(ctx) }
            PrefSwitch(stringResource(R.string.notif_countdown), "notif_countdown") { Alarms.updateStatus(ctx) }
            PrefSwitch(stringResource(R.string.notif_times), "notif_times") { Alarms.updateStatus(ctx) }
            PrefSwitch(stringResource(R.string.notif_columns), "notif_columns") { Alarms.updateStatus(ctx) }
        }

        Section(R.string.sec_widget) {
            var opacity by remember { mutableFloatStateOf(AppSettings.int(ctx, "widget_opacity", 85).toFloat()) }
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
            PrefSwitch(stringResource(R.string.widget_show_next), "widget_next") { PrayerWidget.refreshAll(ctx) }
            PrefSwitch(stringResource(R.string.widget_show_times), "widget_times") { PrayerWidget.refreshAll(ctx) }
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
    }

    if (open) {
        AlertDialog(
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
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
