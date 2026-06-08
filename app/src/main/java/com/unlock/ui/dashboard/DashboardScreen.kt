package com.unlock.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unlock.core.Format
import com.unlock.core.LocalStrings
import com.unlock.core.batteryHealthText
import com.unlock.diagnostics.DiagnosticsReport
import com.unlock.shizuku.ShizukuManager
import com.unlock.ui.components.SeverityDot
import com.unlock.ui.theme.DangerRed
import com.unlock.ui.theme.OkGreen
import com.unlock.ui.theme.WarnAmber

@Composable
fun DashboardScreen(
    onOpen: (String) -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.shizuku != ShizukuManager.State.READY) {
            ShizukuBanner(state.shizuku, onOpen = { onOpen("settings") })
        }

        HealthCard(state.report, loading = state.loading, onRefresh = vm::refresh)

        state.report?.let { report ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                report.battery?.let {
                    StatTile(Icons.Filled.BatteryFull, s.tBattery, "${it.percent}%", "${it.temperatureC.toInt()}°C • ${batteryHealthText(it.healthCode, s)}", Modifier.weight(1f))
                }
                report.maxTempC?.let {
                    StatTile(Icons.Filled.DeviceThermostat, s.tPeakTemp, "${it.toInt()}°C", "${report.thermals.size} ${s.sensorsSuffix}", Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                report.memory?.let {
                    StatTile(Icons.Filled.Memory, s.tRam, "${it.usedPercent}%", "${Format.bytes(it.availBytes)} ${s.freeSuffix}", Modifier.weight(1f))
                }
                report.storage?.let {
                    StatTile(Icons.Filled.Storage, s.tStorage, "${it.usedPercent}%", "${Format.bytes(it.freeBytes)} ${s.freeSuffix}", Modifier.weight(1f))
                }
            }
            report.battery?.takeIf { it.powerWatts != 0f }?.let {
                StatTile(
                    Icons.Filled.Bolt,
                    s.tPowerDraw,
                    String.format(java.util.Locale.US, "%.2f W", kotlin.math.abs(it.powerWatts)),
                    "${kotlin.math.abs(it.dischargingMilliAmps)} mA @ ${it.voltageMv} mV",
                    Modifier.fillMaxWidth(),
                )
            }
        }

        InventoryCard(
            userApps = state.userApps,
            systemApps = state.systemApps,
            disabled = state.disabledApps,
            autostart = state.autostartApps,
            onApps = { onOpen("apps") },
            onAutostart = { onOpen("autostart") },
        )
    }
}

@Composable
private fun HealthCard(report: DiagnosticsReport?, loading: Boolean, onRefresh: () -> Unit) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    if (loading) CircularProgressIndicator()
                    else Text(
                        "${report?.healthScore ?: "--"}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor(report?.healthScore ?: 0),
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.performanceHealth, style = MaterialTheme.typography.titleMedium)
                    Text(
                        report?.findings?.firstOrNull()?.title ?: s.analysing,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                TextButton(onClick = onRefresh) { Text(s.refresh) }
            }
            report?.findings?.take(4)?.forEach { f ->
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    SeverityDot(f.severity, modifier = Modifier.padding(top = 5.dp))
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(f.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(f.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun InventoryCard(
    userApps: Int,
    systemApps: Int,
    disabled: Int,
    autostart: Int,
    onApps: () -> Unit,
    onAutostart: () -> Unit,
) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(s.inventory, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Counter(s.invUser, userApps)
                Counter(s.invSystem, systemApps)
                Counter(s.invDisabled, disabled)
                Counter(s.invAutostart, autostart)
            }
            Spacer(Modifier.size(8.dp))
            Row {
                TextButton(onClick = onApps) { Text(s.manageApps) }
                TextButton(onClick = onAutostart) { Text(s.navAutostart) }
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ShizukuBanner(state: ShizukuManager.State, onOpen: () -> Unit) {
    val s = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(s.fullPowerOff, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    when (state) {
                        ShizukuManager.State.NEEDS_PERMISSION -> s.bannerNeedsPerm
                        else -> s.bannerNotRunning
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            TextButton(onClick = onOpen) { Text(s.setUp) }
        }
    }
}

private fun scoreColor(score: Int) = when {
    score >= 80 -> OkGreen
    score >= 50 -> WarnAmber
    else -> DangerRed
}
