package com.kceryrk.prayertimes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tool == 0, onClick = { select(0) }, label = { Text(stringResource(R.string.tools_compass)) })
            FilterChip(selected = tool == 1, onClick = { select(1) }, label = { Text(stringResource(R.string.tools_zikir)) })
        }
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

    val save = @Composable {
        Button(
            onClick = {
                if (count > 0) {
                    history = listOf(ZikirRecord(System.currentTimeMillis(), count, target)) + history
                    saveHistory(prefs, history)
                    count = 0
                    persist()
                }
            },
            modifier = Modifier.width(120.dp)
        ) { Text(stringResource(R.string.zikir_save)) }
    }
    val reset = @Composable {
        OutlinedButton(onClick = { count = 0; persist() }, modifier = Modifier.width(120.dp)) {
            Text(stringResource(R.string.zikir_reset))
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            targets.forEach { t ->
                FilterChip(
                    selected = target == t,
                    onClick = { target = t; persist() },
                    label = { Text(t.toString()) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !leftHand,
                onClick = { leftHand = false; prefs.edit().putBoolean("left_hand", false).apply() },
                label = { Text(stringResource(R.string.zikir_hand_left)) }
            )
            FilterChip(
                selected = leftHand,
                onClick = { leftHand = true; prefs.edit().putBoolean("left_hand", true).apply() },
                label = { Text(stringResource(R.string.zikir_hand_right)) }
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("$count / $target", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        // Big tap area sits in the thumb's arc; the secondary buttons sit on the opposite side.
        Row(
            Modifier.fillMaxWidth().height(260.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            if (leftHand) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    reset(); save()
                }
                TapButton(Modifier.weight(1f).fillMaxHeight()) { count++; persist() }
            } else {
                TapButton(Modifier.weight(1f).fillMaxHeight()) { count++; persist() }
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    reset(); save()
                }
            }
        }
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text(stringResource(R.string.zikir_history), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { history = emptyList(); saveHistory(prefs, history) }) {
                    Text(stringResource(R.string.zikir_clear))
                }
            }
            val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            history.forEach { r ->
                Text(
                    "${fmt.format(Date(r.time))}  ·  ${r.count} / ${r.target}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TapButton(modifier: Modifier, onTap: () -> Unit) {
    Button(onClick = onTap, modifier = modifier, shape = RoundedCornerShape(28.dp)) {
        Text("+", style = MaterialTheme.typography.displayLarge)
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
