package com.kceryrk.prayertimes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locate()
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var showCityDialog by mutableStateOf(false)
    private var refreshing by mutableStateOf(false)
    private var tick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarms.createChannels(this)
        RefreshWorker.schedule(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
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
            refreshing = false
            tick++
        }
    }

    @Composable
    private fun MainScreen() {
        val times = remember(tick) { PrayerRepo.today(this) }
        val upcoming = remember(tick) { PrayerRepo.upcoming(this) }
        var showQibla by remember { mutableStateOf(false) }

        if (showQibla) {
            QiblaScreen(onBack = { showQibla = false })
            return
        }

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))

            if (upcoming != null) {
                val mins = Duration.between(LocalDateTime.now(), upcoming.at).toMinutes()
                Text(stringResource(R.string.next_prayer, stringResource(upcoming.prayer.labelRes)), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.in_time, mins / 60, mins % 60),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))
            }

            if (times == null) {
                Text(stringResource(R.string.no_times))
            } else {
                Prayer.values().forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(stringResource(p.labelRes), Modifier.weight(1f))
                        Text(times[p]?.toString() ?: "--:--")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = { showCityDialog = true }) { Text(stringResource(R.string.set_city)) }
                Spacer(Modifier.padding(horizontal = 8.dp))
                Button(onClick = { showQibla = true }) { Text(stringResource(R.string.qibla)) }
            }
            if (refreshing) Text(stringResource(R.string.updating))
            if (!PrayerRepo.lastOk(this@MainActivity)) Text(stringResource(R.string.offline))
        }

        if (showCityDialog) CityDialog()
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
