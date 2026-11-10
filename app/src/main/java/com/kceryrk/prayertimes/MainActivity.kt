package com.kceryrk.prayertimes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.delay
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
                lifecycleScope.launch {
                    val (label, cc) = withContext(Dispatchers.IO) { localityOf(loc.latitude, loc.longitude) }
                    PrayerRepo.saveGps(this@MainActivity, loc.latitude, loc.longitude, label, cc)
                    refresh()
                }
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
        val clock by produceState(LocalDateTime.now()) {
            while (true) {
                value = LocalDateTime.now()
                delay(1_000)
            }
        }
        val times = remember(tick, clock.toLocalDate()) { PrayerRepo.today(this) }
        val upcoming = remember(tick, clock) { PrayerRepo.upcoming(this, clock) }
        val pagerState = rememberPagerState(pageCount = { 3 })
        val backdrop = rememberGraphicsLayer()
        val dialogSlot = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
        val bgColor = MaterialTheme.colorScheme.background
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalGlassDialogSlot provides dialogSlot) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .drawWithContent {
                        backdrop.record {
                            drawRect(bgColor)
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(backdrop)
                    }
            ) { page ->
                when (page) {
                    0 -> TimesPage(times, upcoming, clock)
                    1 -> ToolsScreen()
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
            // Glass dialogs draw here, above the blurred pager and nav.
            dialogSlot.value?.invoke()
        }
        if (showCityDialog) CityDialog()
        }
    }

    @Composable
    private fun TimesPage(times: Map<Prayer, java.time.LocalTime>?, upcoming: Upcoming?, clock: LocalDateTime) {
        val accent = accentColor()
        val sky = rememberSkyState()
        val nowTime = clock.toLocalTime()
        val current = times?.filter { !it.value.isAfter(nowTime) }?.maxByOrNull { it.value }?.key
        val locale = resources.configuration.locales[0]
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), MaterialTheme.colorScheme.background)))
                .drawBehind { drawSky(sky) }
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 120.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                HilalifyHeader(accent)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        clock.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        clock.format(DateTimeFormatter.ofPattern("d MMMM", locale)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrayerRepo.locationLabel(this@MainActivity)?.let {
                val comma = it.lastIndexOf(", ")
                val labelText = androidx.compose.ui.text.buildAnnotatedString {
                    if (comma >= 0) {
                        append(it.substring(0, comma + 2))
                        withStyle(SpanStyle(color = accent)) { append(it.substring(comma + 2)) }
                    } else append(it)
                }
                Text(
                    labelText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { showCityDialog = true }
                )
            }
            Spacer(Modifier.height(20.dp))

            if (upcoming != null) {
                val totalSecs = Duration.between(clock, upcoming.at).seconds.coerceAtLeast(0)
                val mins = totalSecs / 60
                val heroShape = RoundedCornerShape(20.dp)
                val contrast = if (accent.luminance() > 0.5f) Color.Black else Color.White
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shadow(14.dp, heroShape, spotColor = accent)
                        .clip(heroShape)
                        .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.25f), accent)))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(upcoming.prayer.labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = contrast
                        )
                        Text(
                            when {
                                mins >= 60 -> stringResource(R.string.countdown_hm, mins / 60, mins % 60)
                                mins >= 10 -> stringResource(R.string.countdown_m, mins)
                                else -> stringResource(R.string.countdown_ms, mins, totalSecs % 60)
                            },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.35f),
                                    offset = Offset(2f, 3f),
                                    blurRadius = 8f
                                )
                            ),
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }


            if (times == null) {
                Text(stringResource(R.string.no_times), color = MaterialTheme.colorScheme.onSurface)
            } else {
                val listShape = RoundedCornerShape(20.dp)
                val prayers = Prayer.values()
                val kerWins = Kerahat.windows(this@MainActivity)
                val kerahatBand = accent.copy(alpha = 0.6f)
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
                        .drawWithContent {
                            drawContent()
                            // Dotted track along the whole right edge of the list.
                            val dotX = size.width - 2.dp.toPx()
                            val step = 6.dp.toPx()
                            var dotY = step / 2
                            while (dotY < size.height) {
                                drawCircle(accent.copy(alpha = 0.35f), radius = 1.dp.toPx(), center = Offset(dotX, dotY))
                                dotY += step
                            }
                        }
                ) {
                    prayers.forEachIndexed { i, p ->
                        val isCurrent = p == current
                        val highlighted = isCurrent
                        val isPast = current != null && i < prayers.indexOf(current)
                        val rowStart = times[p]!!.toSecondOfDay()
                        val rowTotal = (((times[prayers[(i + 1) % prayers.size]]?.toSecondOfDay() ?: rowStart) - rowStart + 86400) % 86400).coerceAtLeast(1)
                        // Kerahat windows as fractions of this row's time span, from its top (start) to bottom (end).
                        val bands = kerWins.mapNotNull { (s, e) ->
                            val a = (s.toLocalTime().toSecondOfDay() - rowStart + 86400) % 86400
                            val bRaw = (e.toLocalTime().toSecondOfDay() - rowStart + 86400) % 86400
                            val b = if (bRaw == 0) 86400 else bRaw
                            if (a >= rowTotal || b <= a) null
                            else a.toFloat() / rowTotal to minOf(b, rowTotal).toFloat() / rowTotal
                        }
                        val timeLeft: Float? = if (isCurrent && times != null && upcoming != null) {
                            val startSec = times[p]!!.toSecondOfDay()
                            val nextSec = upcoming.at.toLocalTime().toSecondOfDay()
                            val nowSec = nowTime.toSecondOfDay()
                            val total = ((nextSec - startSec + 86400) % 86400).coerceAtLeast(1)
                            (((nextSec - nowSec + 86400) % 86400).toFloat() / total).coerceIn(0f, 1f)
                        } else null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) accent.copy(alpha = 0.22f) else Color.Transparent)
                                .then(
                                    if (timeLeft != null) Modifier.drawBehind {
                                        val w = 10.dp.toPx()
                                        val h = 1.5.dp.toPx()
                                        val y = (size.height - h) * (1f - timeLeft)
                                        drawRect(
                                            accent,
                                            topLeft = Offset(size.width - w, y),
                                            size = Size(w, h)
                                        )
                                    } else Modifier
                                )
                                .padding(horizontal = 20.dp, vertical = 15.dp)
                                .drawWithContent {
                                    drawContent()
                                    // Centered on the dotted track (track is at width - 2dp).
                                    val bw = 3.dp.toPx()
                                    val bx = size.width - 2.dp.toPx() - bw / 2
                                    bands.forEach { (f1, f2) ->
                                        drawRect(kerahatBand, topLeft = Offset(bx, size.height * f1), size = Size(bw, size.height * (f2 - f1)))
                                    }
                                }
                        ) {

                            Text(
                                stringResource(p.labelRes),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isCurrent -> accent
                                    isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                times[p]?.toString() ?: "--:--",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isCurrent -> accent
                                    isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
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
        val names = listOf(R.string.tab_times, R.string.tab_tools, R.string.settings)
        val accent = accentColor()
        val bgColor = MaterialTheme.colorScheme.background
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
                        drawRect(bgColor)
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
            1 -> Icon(Icons.Filled.Build, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            else -> Icon(Icons.Filled.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }

    @Composable
    private fun CityDialog() {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<CityHit>>(emptyList()) }
        val savedList = remember { PrayerRepo.savedCities(this@MainActivity) }
        LaunchedEffect(query) {
            if (query.trim().length < 3) {
                results = emptyList()
                return@LaunchedEffect
            }
            delay(400)
            val lang = Locale.getDefault().language
            results = withContext(Dispatchers.IO) { PrayerRepo.searchCities(query.trim(), lang) }
        }
        GlassAlertDialog(
            onDismissRequest = { showCityDialog = false },
            title = { Text(stringResource(R.string.set_city)) },
            text = {
                Column {
                    GlassSearchField(query, { query = it }, placeholder = stringResource(R.string.city))
                    if (query.trim().length < 3) {
                        Text(stringResource(R.string.city_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    val shown = if (query.trim().length >= 3) results else savedList
                    if (shown.isNotEmpty()) {
                        if (query.trim().length < 3) {
                            Text(
                                stringResource(R.string.saved_places),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            )
                        }
                        val cardShape = RoundedCornerShape(20.dp)
                        val rimTop = MaterialTheme.colorScheme.outlineVariant
                        Column(
                            Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                                .shadow(12.dp, cardShape, spotColor = Color.Black.copy(alpha = 0.6f))
                                .clip(cardShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                                .drawBehind {
                                    // Same emboss as the home list: light top edge fading to dark bottom edge.
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(listOf(rimTop.copy(alpha = 0.9f), Color.Transparent, Color.Black.copy(alpha = 0.45f))),
                                        cornerRadius = CornerRadius(20.dp.toPx()),
                                        style = Stroke(1.dp.toPx())
                                    )
                                }
                        ) {
                            shown.forEachIndexed { i, hit ->
                                Text(
                                    hit.label(resources.configuration.locales[0]),
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            PrayerRepo.saveCity(this@MainActivity, hit)
                                            showCityDialog = false
                                            refresh()
                                        }
                                        .padding(horizontal = 20.dp, vertical = 15.dp),
                                    textAlign = TextAlign.Center
                                )
                                if (i < shown.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                GlassDialogAction(stringResource(R.string.cancel)) { showCityDialog = false }
            }
        )
    }

    private fun localityOf(lat: Double, lon: Double): Pair<String?, String> = try {
        val addr = Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
        (addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea) to (addr?.countryCode ?: "")
    } catch (_: Exception) {
        null to ""
    }
}
