package com.kceryrk.prayertimes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun OnboardingScreen(
    accent: Color,
    localityLookup: (Double, Double) -> Pair<String?, String>,
    onAccentChanged: () -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val sky = rememberSkyState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), MaterialTheme.colorScheme.background)))
            .drawBehind { drawSky(sky) }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "onboarding-step"
        ) { page ->
            if (page == 0) {
                WelcomeStep(
                    accent = accent,
                    onAccentChanged = onAccentChanged,
                    onGetStarted = { step = 1 }
                )
            } else {
                SetupStep(
                    accent = accent,
                    localityLookup = localityLookup,
                    onAccentChanged = onAccentChanged,
                    onBack = { step = 0 },
                    onFinish = onFinish
                )
            }
        }
    }
}

@Composable
private fun AccentPickerRow(onAccentChanged: () -> Unit) {
    val ctx = LocalContext.current
    var currentAccent by remember { mutableIntStateOf(AppSettings.int(ctx, "accent", DEFAULT_ACCENT_ARGB)) }
    Text(
        stringResource(R.string.accent),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(0xFF00A86B, 0xFF0A84FF, 0xFFFF375F, 0xFFFF9F0A, 0xFFBF5AF2, 0xFF64D2FF).forEach { argb ->
            val value = argb.toInt()
            val selected = value == currentAccent
            val dotSize by animateDpAsState(if (selected) 44.dp else 30.dp, label = "accent-dot")
            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(Color(value))
                    .border(
                        if (selected) 3.dp else 0.dp,
                        if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        CircleShape
                    )
                    .clickable {
                        currentAccent = value
                        AppSettings.put(ctx, "accent", value)
                        onAccentChanged()
                    }
            )
        }
    }
}

@Composable
private fun WelcomeStep(accent: Color, onAccentChanged: () -> Unit, onGetStarted: () -> Unit) {
    val contrast = if (accent.luminance() > 0.5f) Color.Black else Color.White

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        HilalifyHeader(accent)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(24.dp))

        FeatureGlassCard(
            accent = accent,
            icon = Icons.Filled.Home,
            title = stringResource(R.string.welcome_feat1_title),
            desc = stringResource(R.string.welcome_feat1_desc)
        )
        Spacer(Modifier.height(12.dp))
        FeatureGlassCard(
            accent = accent,
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.welcome_feat2_title),
            desc = stringResource(R.string.welcome_feat2_desc)
        )
        Spacer(Modifier.height(12.dp))
        FeatureGlassCard(
            accent = accent,
            icon = Icons.Filled.Build,
            title = stringResource(R.string.welcome_feat3_title),
            desc = stringResource(R.string.welcome_feat3_desc)
        )

        Spacer(Modifier.height(24.dp))
        AccentPickerRow(onAccentChanged)

        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGetStarted,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = contrast),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(stringResource(R.string.get_started), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FeatureGlassCard(accent: Color, icon: ImageVector, title: String, desc: String) {
    GlassCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetupStep(
    accent: Color,
    localityLookup: (Double, Double) -> Pair<String?, String>,
    onAccentChanged: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contrast = if (accent.luminance() > 0.5f) Color.Black else Color.White

    var permTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    var gpsMode by remember { mutableStateOf(PrayerRepo.isGpsMode(ctx)) }
    var detecting by remember { mutableStateOf(false) }
    var locationLabel by remember(permTick) { mutableStateOf(PrayerRepo.locationLabel(ctx)) }
    var query by remember { mutableStateOf("") }
    var cityResults by remember { mutableStateOf<List<CityHit>>(emptyList()) }

    val defaultMethod = remember {
        if (!AppSettings.prefs(ctx).contains("method")) {
            val m = AppSettings.defaultMethod()
            AppSettings.put(ctx, "method", m)
            m
        } else AppSettings.method(ctx)
    }
    var selectedMethod by remember { mutableIntStateOf(defaultMethod) }
    var notifOn by remember { mutableStateOf(AppSettings.bool(ctx, "notif_on", true)) }
    var kerahatOn by remember { mutableStateOf(AppSettings.bool(ctx, "kerahat", true)) }
    var realMoonIcon by remember { mutableStateOf(AppSettings.bool(ctx, "real_moon_icon", true)) }

    fun detectGps() {
        detecting = true
        LocationHelper.current(ctx) { loc ->
            if (loc != null) {
                scope.launch {
                    val (label, cc) = withContext(Dispatchers.IO) { localityLookup(loc.latitude, loc.longitude) }
                    PrayerRepo.saveGps(ctx, loc.latitude, loc.longitude, label, cc)
                    locationLabel = PrayerRepo.locationLabel(ctx) ?: "${"%.2f".format(loc.latitude)}, ${"%.2f".format(loc.longitude)}"
                    detecting = false
                    withContext(Dispatchers.IO) { PrayerRepo.refresh(ctx) }
                }
            } else {
                detecting = false
            }
        }
    }

    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        if (hasLocPerm(ctx)) detectGps()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permTick++
    }

    LaunchedEffect(query) {
        if (query.trim().length < 3) {
            cityResults = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        cityResults = withContext(Dispatchers.IO) { PrayerRepo.searchCities(query.trim(), Locale.getDefault().language) }
    }

    val locGranted = remember(permTick) { hasLocPerm(ctx) }
    val notifGranted = remember(permTick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val exactGranted = remember(permTick) {
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val dndGranted = remember(permTick) {
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }
    val promotedGranted = remember(permTick) {
        Build.VERSION.SDK_INT < 36 || ctx.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // 1. Calculation Source (Diyanet pre-selected when system language is Turkish)
            SetupHeader(stringResource(R.string.setup_source_title), accent)
            GlassCard {
                listOf(
                    13 to R.string.method_13,
                    3 to R.string.method_3,
                    2 to R.string.method_2,
                    4 to R.string.method_4,
                    5 to R.string.method_5
                ).forEachIndexed { idx, (id, labelRes) ->
                    if (idx > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMethod = id
                                AppSettings.put(ctx, "method", id)
                                scope.launch(Dispatchers.IO) { PrayerRepo.refresh(ctx) }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedMethod == id, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // 2. Location (GPS or City search)
            SetupHeader(stringResource(R.string.setup_location_title), accent)
            GlassCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.use_gps), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = gpsMode,
                            onCheckedChange = {
                                gpsMode = it
                                PrayerRepo.setGpsMode(ctx, it)
                                if (it && locGranted) detectGps()
                            }
                        )
                    }
                    if (gpsMode) {
                        Spacer(Modifier.height(8.dp))
                        val statusText = when {
                            detecting -> stringResource(R.string.setup_detecting)
                            locationLabel != null -> stringResource(R.string.gps_city, locationLabel!!)
                            else -> stringResource(R.string.gps_city_unknown)
                        }
                        Text(statusText, style = MaterialTheme.typography.bodyMedium, color = accent)
                        if (!locGranted) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    gpsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.setup_grant_gps))
                            }
                        } else if (locationLabel == null && !detecting) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { detectGps() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.setup_grant_gps))
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        GlassSearchField(query, { query = it }, placeholder = stringResource(R.string.city))
                        if (locationLabel != null && query.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(locationLabel!!, style = MaterialTheme.typography.bodyMedium, color = accent)
                        }
                        if (cityResults.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            cityResults.take(5).forEachIndexed { idx, hit ->
                                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                Text(
                                    hit.label(ctx.resources.configuration.locales[0]),
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            PrayerRepo.saveCity(ctx, hit)
                                            locationLabel = PrayerRepo.locationLabel(ctx)
                                            query = ""
                                            cityResults = emptyList()
                                            scope.launch(Dispatchers.IO) { PrayerRepo.refresh(ctx) }
                                        }
                                        .padding(vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // 3. Permissions
            SetupHeader(stringResource(R.string.setup_perms_title), accent)
            GlassCard {
                SetupPermissionRow(stringResource(R.string.perm_notif), notifGranted) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                SetupPermissionRow(stringResource(R.string.perm_exact), exactGranted) {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                SetupPermissionRow(stringResource(R.string.perm_notif_policy), dndGranted) {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                if (Build.VERSION.SDK_INT >= 36) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    SetupPermissionRow("Live Updates / Dynamic Island", promotedGranted) {
                        val i = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        try { ctx.startActivity(i) } catch (_: Exception) {
                            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // 4. Quick Preferences
            SetupHeader(stringResource(R.string.setup_prefs_title), accent)
            GlassCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.notif_on), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = notifOn, onCheckedChange = {
                        notifOn = it
                        AppSettings.put(ctx, "notif_on", it)
                    })
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.kerahat_setting), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = kerahatOn, onCheckedChange = {
                        kerahatOn = it
                        AppSettings.put(ctx, "kerahat", it)
                    })
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.real_moon_icon), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = realMoonIcon, onCheckedChange = {
                        realMoonIcon = it
                        AppSettings.put(ctx, "real_moon_icon", it)
                    })
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AccentPickerRow(onAccentChanged)
                }
            }
        }

        // Bottom navigation bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text(stringResource(R.string.back))
            }
            Button(
                onClick = {
                    AppSettings.put(ctx, "onboarding_done", true)
                    onFinish()
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = contrast),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1.7f).height(52.dp)
            ) {
                Text(stringResource(R.string.setup_finish), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SetupHeader(text: String, accent: Color) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val rimTop = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, spotColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
            .drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(rimTop.copy(alpha = 0.85f), Color.Transparent, Color.Black.copy(alpha = 0.35f))),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(1.dp.toPx())
                )
            },
        content = content
    )
}

@Composable
private fun SetupPermissionRow(title: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onGrant)
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
                Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF34C759)),
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
            Text(stringResource(R.string.perm_missing), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun hasLocPerm(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
