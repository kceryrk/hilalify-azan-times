package com.kceryrk.prayertimes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Canvas
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.roundToInt

/** Default app accent (vibrant emerald), stored as ARGB in prefs under "accent". */
internal val DEFAULT_ACCENT_ARGB: Int = 0xFF00A86B.toInt()

class MainActivity : ComponentActivity() {

    private fun accentColor(): Color = Color(AppSettings.int(this, "accent", DEFAULT_ACCENT_ARGB))

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locate()
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var showCityDialog by mutableStateOf(false)
    private var refreshing by mutableStateOf(false)
    private var tick by mutableStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarms.createChannels(this)
        RefreshWorker.schedule(this)
        setContent {
            val themePref = AppSettings.str(this, "theme", "system")
            val dark = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val accent = Color(AppSettings.int(this, "accent", DEFAULT_ACCENT_ARGB))
            val baseScheme = if (dark) darkColorScheme() else lightColorScheme()
            val scheme: ColorScheme = baseScheme.copy(
                primary = accent,
                secondary = accent,
                tertiary = accent,
                primaryContainer = accent.copy(alpha = 0.25f),
                onPrimaryContainer = accent
            )
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) {
                    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
                    LaunchedEffect(Unit) {
                        if (AppSettings.updatesEnabled(this@MainActivity)) {
                            availableUpdate = UpdateChecker.check(this@MainActivity)
                        }
                    }
                    availableUpdate?.let { UpdateDialog(it) { availableUpdate = null } }
                    MainScreen()
                }
            }
        }
        if (PrayerRepo.prefs(this).getString("mode", null) == "city") {
            refresh()
        } else {
            locate()
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Asks for permission only when it's missing. With permission, it takes a fresh GPS fix. */
    private fun locate() {
        if (!hasLocationPermission()) {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33 && !hasNotifPermission()) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            permLauncher.launch(perms.toTypedArray())
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifPermission()) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        refreshing = true
        LocationHelper.current(this) { loc ->
            if (loc != null) {
                PrayerRepo.saveGps(this, loc.latitude, loc.longitude)
                refresh()
            } else {
                refreshing = false
                if (PrayerRepo.savedGps(this) == null) showCityDialog = true else refresh()
            }
        }
    }

    private fun hasNotifPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        refreshing = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { PrayerRepo.refresh(this@MainActivity) }
            Alarms.scheduleNext(this@MainActivity)
            Alarms.updateStatus(this@MainActivity)
            PrayerWidget.refreshAll(this@MainActivity)
            refreshing = false
            tick++
        }
    }

    @Composable
    private fun MainScreen() {
        val times = remember(tick) { PrayerRepo.today(this) }
        val upcoming = remember(tick) { PrayerRepo.upcoming(this) }
        val pagerState = rememberPagerState(pageCount = { 3 })
        val backdrop = rememberGraphicsLayer()
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    backdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop)
                }
            ) { page ->
                when (page) {
                    0 -> TimesPage(times, upcoming)
                    1 -> QiblaScreen()
                    else -> SettingsScreen(
                        onGpsToggle = { on ->
                            PrayerRepo.setGpsMode(this@MainActivity, on)
                            if (on) locate() else showCityDialog = true
                        },
                        onCity = { showCityDialog = true },
                        onRecreate = { recreate() }
                    )
                }
            }
            GlassNav(pagerState, backdrop)
        }

        if (showCityDialog) CityDialog()
    }

    @Composable
    private fun TimesPage(times: Map<Prayer, java.time.LocalTime>?, upcoming: Upcoming?) {
        val accent = accentColor()
        val nowTime = LocalDateTime.now().toLocalTime()
        val current = times?.filter { !it.value.isAfter(nowTime) }?.maxByOrNull { it.value }?.key
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), MaterialTheme.colorScheme.background)))
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 120.dp)
        ) {
            Text(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            if (upcoming != null) {
                val mins = Duration.between(LocalDateTime.now(), upcoming.at).toMinutes()
                val heroShape = RoundedCornerShape(24.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(14.dp, heroShape, spotColor = accent)
                        .clip(heroShape)
                        .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.25f), accent)))
                        .padding(20.dp)
                ) {
                    Text(
                        stringResource(R.string.next_prayer, stringResource(upcoming.prayer.labelRes)),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        stringResource(R.string.in_time, mins / 60, mins % 60),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (times == null) {
                Text(stringResource(R.string.no_times), color = MaterialTheme.colorScheme.onSurface)
            } else {
                val listShape = RoundedCornerShape(20.dp)
                val prayers = Prayer.values()
                val rimTop = MaterialTheme.colorScheme.outlineVariant
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, listShape, spotColor = Color.Black.copy(alpha = 0.6f))
                        .clip(listShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                        .drawBehind {
                            // Emboss: light top edge fading to dark bottom edge.
                            drawRoundRect(
                                brush = Brush.verticalGradient(listOf(rimTop.copy(alpha = 0.9f), Color.Transparent, Color.Black.copy(alpha = 0.45f))),
                                cornerRadius = CornerRadius(20.dp.toPx()),
                                style = Stroke(1.dp.toPx())
                            )
                        }
                ) {
                    prayers.forEachIndexed { i, p ->
                        val isCurrent = p == current
                        val highlighted = isCurrent
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) accent.copy(alpha = 0.22f) else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 15.dp)
                        ) {
                            Text(
                                stringResource(p.labelRes),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                                color = if (highlighted) accent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                times[p]?.toString() ?: "--:--",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (i < prayers.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            if (refreshing) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.updating), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!PrayerRepo.lastOk(this@MainActivity)) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.offline), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    /**
     * Icon-only floating pill. A glass thumb slides under the selected icon and can be dragged
     * across the bar, which scrolls the pager. The thumb stretches while it moves.
     */
    @Composable
    private fun BoxScope.GlassNav(
        pagerState: androidx.compose.foundation.pager.PagerState,
        backdrop: GraphicsLayer
    ) {
        val names = listOf(R.string.tab_times, R.string.qibla, R.string.settings)
        val accent = accentColor()
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        var barSize by remember { mutableStateOf(IntSize.Zero) }
        var barPos by remember { mutableStateOf(Offset.Zero) }
        var lensPos by remember { mutableStateOf(Offset.Zero) }
        var dragFraction by remember { mutableFloatStateOf(-1f) }
        val lens = remember { if (Build.VERSION.SDK_INT >= 33) GlassLens() else null }

        val holding = dragFraction >= 0f
        val barScale by animateFloatAsState(if (holding) 1.08f else 1f, label = "navScale")
        val position = if (dragFraction >= 0f) dragFraction
        else pagerState.currentPage + pagerState.currentPageOffsetFraction
        val slotWidth: Dp = 84.dp
        val slotHeight: Dp = 60.dp
        val inset = 6.dp
        val slotPx = with(density) { slotWidth.toPx() }
        val slotHeightPx = with(density) { slotHeight.toPx() }
        val barWidthPx = barSize.width.toFloat()
        val barHeightPx = barSize.height.toFloat()

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .graphicsLayer { scaleX = barScale; scaleY = barScale }
                .onSizeChanged { barSize = it }
                .onGloballyPositioned { barPos = it.positionInRoot() }
        ) {
            // Glass background only; refraction is applied here so the thumb and icons stay undistorted.
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= 33 && barSize.width > 0) Modifier.graphicsLayer {
                            clip = true
                            shape = CircleShape
                            renderEffect = lens?.effectFor(
                                barWidthPx / 2f, barHeightPx / 2f, barWidthPx / 2f, barHeightPx / 2f,
                                barHeightPx / 2f, with(density) { 14.dp.toPx() }, with(density) { 10.dp.toPx() }, with(density) { 2.dp.toPx() }
                            )
                        } else Modifier.clip(CircleShape)
                    )
                    .drawBehind {
                        withTransform({ translate(-barPos.x, -barPos.y) }) { drawLayer(backdrop) }
                        drawRect(Color.Black.copy(alpha = 0.25f))
                    }
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f))),
                        CircleShape
                    )
            )
            Box(
                Modifier
                    .padding(inset)
                    .pointerInput(barSize) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragFraction = (offset.x / with(density) { slotWidth.toPx() } - 0.5f).coerceIn(0f, 2f)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                dragFraction = (change.position.x / with(density) { slotWidth.toPx() } - 0.5f).coerceIn(0f, 2f)
                            },
                            onDragEnd = {
                                val target = dragFraction.roundToInt().coerceIn(0, 2)
                                dragFraction = -1f
                                scope.launch { pagerState.animateScrollToPage(target) }
                            },
                            onDragCancel = { dragFraction = -1f }
                        )
                    }
            ) {
                val selectorColor = accent
                // Selector fill sits beneath the icons, as in Mornye's pill.
                Box(
                    Modifier
                        .offset { IntOffset((position * slotPx).roundToInt(), 0) }
                        .size(slotWidth, slotHeight)
                        .clip(CircleShape)
                        .onGloballyPositioned { lensPos = it.positionInRoot() }
                        .then(if (holding) Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape) else Modifier)
                        .drawBehind {
                            drawRect(accent.copy(alpha = 0.22f))
                        }
                )
                Row(
                    Modifier
                        .height(slotHeight)
                        .graphicsLayer {
                            // Offscreen so the SrcAtop mask below recolors only the icon pixels.
                            compositingStrategy = CompositingStrategy.Offscreen
                            if (Build.VERSION.SDK_INT >= 33 && slotPx > 0f) {
                                // Icons bend only around the selector's inside edge.
                                renderEffect = lens?.effectFor(
                                    position * slotPx + slotPx / 2f, slotHeightPx / 2f,
                                    slotPx / 2f, slotHeightPx / 2f,
                                    slotHeightPx / 2f, with(density) { 14.dp.toPx() }, with(density) { 10.dp.toPx() }
                                )
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            // Recolors icon pixels inside the selector to the active tint, like Mornye's mask.
                            drawRoundRect(
                                color = selectorColor,
                                topLeft = Offset(position * slotPx, 0f),
                                size = Size(slotPx, slotHeightPx),
                                cornerRadius = CornerRadius(slotHeightPx / 2f),
                                blendMode = BlendMode.SrcAtop
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    names.forEachIndexed { i, nameRes ->
                        val tint = if (position.roundToInt() == i) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            Modifier
                                .width(slotWidth)
                                .height(slotHeight)
                                .clip(CircleShape)
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            NavGlyph(i, tint)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(nameRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NavGlyph(index: Int, tint: Color) {
        when (index) {
            0 -> Icon(Icons.Filled.Home, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            1 -> CompassGlyph(tint)
            else -> Icon(Icons.Filled.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }

    /** Compass rose: ring plus a filled north-pointing needle. */
    @Composable
    private fun CompassGlyph(tint: Color) {
        Canvas(Modifier.size(26.dp)) {
            val r = size.minDimension / 2f
            val c = center
            drawCircle(tint, radius = r - 1.5.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            val needle = Path().apply {
                moveTo(c.x, c.y - r * 0.6f)
                lineTo(c.x + r * 0.22f, c.y)
                lineTo(c.x, c.y + r * 0.6f)
                lineTo(c.x - r * 0.22f, c.y)
                close()
            }
            drawPath(needle, tint)
        }
    }

    @Composable
    private fun CityDialog() {
        var city by remember { mutableStateOf("") }
        var country by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCityDialog = false },
            title = { Text(stringResource(R.string.set_city)) },
            text = {
                Column {
                    OutlinedTextField(city, { city = it }, label = { Text(stringResource(R.string.city)) })
                    OutlinedTextField(country, { country = it }, label = { Text(stringResource(R.string.country)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (city.isNotBlank() && country.isNotBlank()) {
                        PrayerRepo.saveCity(this@MainActivity, city.trim(), country.trim())
                        showCityDialog = false
                        refresh()
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showCityDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
