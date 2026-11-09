package com.kceryrk.prayertimes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
fun ToolsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tools", android.content.Context.MODE_PRIVATE) }
    var tool by remember { mutableIntStateOf(prefs.getInt("last_tool", 0)) }
    fun select(t: Int) {
        tool = t
        prefs.edit().putInt("last_tool", t).apply()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        PillSegmented(
            options = listOf(stringResource(R.string.tools_compass), stringResource(R.string.tools_zikir)),
            selected = tool,
            onSelect = { select(it) }
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tool == 0) QiblaScreen() else ZikirScreen()
        }
    }
}

@Composable
fun ZikirScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("zikir", android.content.Context.MODE_PRIVATE) }
    var count by remember { mutableIntStateOf(prefs.getInt("count", 0)) }
    var target by remember { mutableIntStateOf(prefs.getInt("target", 33)) }
    var leftHand by remember { mutableStateOf(prefs.getBoolean("left_hand", false)) }
    var history by remember { mutableStateOf(loadHistory(prefs)) }
    val targets = listOf(33, 99, 100, 1000)

    fun persist() {
        prefs.edit().putInt("count", count).putInt("target", target).apply()
    }

    // Kaydet snapshots the current count to history; the counter keeps going.
    fun archive() {
        if (count > 0) {
            history = listOf(ZikirRecord(System.currentTimeMillis(), count, target)) + history
            saveHistory(prefs, history)
        }
    }
    val sideControls = @Composable {
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MinusButton { if (count > 0) { count--; persist() } }
            HandSwitch(
                leftHand = leftHand,
                onClick = {
                    leftHand = !leftHand
                    prefs.edit().putBoolean("left_hand", leftHand).apply()
                }
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Scrolls when history grows, and otherwise centers the counter vertically.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PillSegmented(
                options = targets.map { it.toString() },
                selected = targets.indexOf(target).coerceAtLeast(0),
                onSelect = { target = targets[it]; persist() },
                modifier = Modifier.widthIn(max = 360.dp)
            )
            Spacer(Modifier.height(16.dp))
            // Tap pad and secondary buttons slide to the other side when the hand is switched.
            AnimatedContent(
                targetState = leftHand,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(260.dp),
                label = "hand_side"
            ) { left ->
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val tap = @Composable {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            TapButton(count, target, Modifier.size(minOf(maxWidth, maxHeight))) { count++; persist() }
                        }
                    }
                    if (left) {
                        sideControls()
                        tap()
                    } else {
                        tap()
                        sideControls()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { count = 0; persist() },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(stringResource(R.string.zikir_reset))
                }
                Button(
                    onClick = { archive(); persist() },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(stringResource(R.string.zikir_save))
                }
            }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.zikir_history), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = { history = emptyList(); saveHistory(prefs, history) },
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.zikir_clear))
                    }
                }
                val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                Text(
                    stringResource(R.string.zikir_continue_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                history.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .clickable {
                                count = r.count
                                target = r.target
                                history = history.filter { it.time != r.time }
                                saveHistory(prefs, history)
                                persist()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.count} / ${r.target}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                fmt.format(Date(r.time)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Frosted pill surface used for the iOS-style controls. */
@Composable
private fun Modifier.glassPill(): Modifier = this
    .shadow(6.dp, CircleShape)
    .clip(CircleShape)
    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape)

/** Segmented pill control whose accent selector slides between options. */
@Composable
private fun PillSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .glassPill()
            .padding(4.dp)
    ) {
        val segWidth = maxWidth / options.size
        val selectorX by animateDpAsState(
            targetValue = segWidth * selected,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
            label = "selector_x"
        )
        Box(
            Modifier
                .offset(x = selectorX)
                .width(segWidth)
                .fillMaxHeight()
                .shadow(4.dp, CircleShape, spotColor = accent, ambientColor = accent)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.2f), accent)))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (i == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Round icon button; the chevron points toward the side the counter is shown on. */
@Composable
private fun HandSwitch(leftHand: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val chevronRotation by animateFloatAsState(
        targetValue = if (leftHand) 180f else 0f,
        animationSpec = tween(400),
        label = "chevron"
    )
    Box(
        Modifier
            .size(48.dp)
            .glassPill()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(28.dp).rotate(chevronRotation)) {
            val chevron = Path().apply {
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width * 0.8f, size.height * 0.5f)
                lineTo(size.width * 0.15f, size.height)
            }
            drawPath(
                chevron,
                accent,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/** Round minus button; white on dark backgrounds, black on light ones. */
@Composable
private fun MinusButton(onClick: () -> Unit) {
    val iconColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black
    Box(
        Modifier
            .size(48.dp)
            .glassPill()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            drawLine(
                iconColor,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/** Circular tap pad that shows the running count; ticks on every tap and pulses when the target is reached. */
@Composable
private fun TapButton(count: Int, target: Int, modifier: Modifier, onTap: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .shadow(16.dp, CircleShape, spotColor = accent, ambientColor = accent)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.25f), accent)))
            .clickable {
                haptic.performHapticFeedback(
                    if (count + 1 == target) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                )
                onTap()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$count",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1
            )
            Text(
                "/ $target",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }
    }
}


private data class ZikirRecord(val time: Long, val count: Int, val target: Int)

private fun loadHistory(prefs: android.content.SharedPreferences): List<ZikirRecord> {
    val arr = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ZikirRecord(o.getLong("t"), o.getInt("c"), o.getInt("g"))
    }
}

private fun saveHistory(prefs: android.content.SharedPreferences, list: List<ZikirRecord>) {
    val arr = JSONArray()
    list.forEach { r -> arr.put(JSONObject().put("t", r.time).put("c", r.count).put("g", r.target)) }
    prefs.edit().putString("history", arr.toString()).apply()
}
