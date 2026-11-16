package com.kceryrk.prayertimes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NextPrayerScreen() }
    }
}

@Composable
private fun NextPrayerScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    val prefs = remember(tick) { ctx.getSharedPreferences(NEXT_PRAYER_PREFS, 0) }
    val label = prefs.getString("label", null)
    val atMillis = prefs.getLong("at", 0L)
    val now = System.currentTimeMillis()

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (label == null || atMillis <= now) {
                Text(text = "—", style = MaterialTheme.typography.display1)
                Text(text = "Open the phone app", style = MaterialTheme.typography.caption1)
                return@Column
            }
            val time = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault())
                .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            val minutesLeft = (atMillis - now) / 60_000
            Text(text = label, style = MaterialTheme.typography.title3)
            Text(text = time, style = MaterialTheme.typography.display1)
            Text(
                text = "${minutesLeft / 60}h ${minutesLeft % 60}m left",
                style = MaterialTheme.typography.caption1
            )
        }
    }
}
