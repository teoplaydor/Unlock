package com.unlock.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unlock.core.Format
import com.unlock.data.DrainEntry
import com.unlock.diagnostics.CpuCore
import com.unlock.diagnostics.DiagnosticsReport
import com.unlock.ui.components.SeverityDot

@Composable
fun DiagnosticsScreen(vm: DiagnosticsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("System diagnostics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::refresh) { Text(if (state.loading) "…" else "Refresh") }
        }

        state.message?.let { msg ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = vm::clearMessage) { Text("OK") }
                }
            }
        }

        state.gosPackage?.let { gos ->
            SectionCard("Samsung performance throttling") {
                Text(
                    "Game Optimizing Service ($gos) is active — it caps performance for thousands of apps by name. Disabling it removes that cap (reversible).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.padding(4.dp))
                FilledTonalButton(onClick = vm::disableGos, enabled = state.shizukuReady) {
                    Text(if (state.shizukuReady) "Disable GOS throttling" else "Needs Shizuku")
                }
            }
        }

        val report = state.report ?: return@Column

        FindingsSection(report)
        if (state.shizukuReady && state.drainers.isNotEmpty()) DrainSection(state.drainers)
        report.battery?.let { BatterySection(it) }
        ThermalSection(report)
        CpuSection(report)
        report.memory?.let { mem ->
            SectionCard("Memory") {
                LabeledBar("Used", mem.usedPercent)
                Text("${Format.bytes(mem.totalBytes - mem.availBytes)} of ${Format.bytes(mem.totalBytes)} used • ${Format.bytes(mem.availBytes)} free", style = MaterialTheme.typography.bodySmall)
                if (mem.lowMemory) Text("System reports LOW MEMORY", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        report.storage?.let { st ->
            SectionCard("Storage") {
                LabeledBar("Used", st.usedPercent)
                Text("${Format.bytes(st.freeBytes)} free of ${Format.bytes(st.totalBytes)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FindingsSection(report: DiagnosticsReport) {
    SectionCard("Why it might feel slow") {
        report.findings.forEach { f ->
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                SeverityDot(f.severity, modifier = Modifier.padding(top = 5.dp))
                Spacer(Modifier.padding(4.dp))
                Column {
                    Text(f.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(f.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun BatterySection(b: com.unlock.diagnostics.BatterySnapshot) {
    SectionCard("Battery & power") {
        KeyVal("Level", "${b.percent}%  (${b.status})")
        KeyVal("Health", b.health)
        KeyVal("Temperature", "${b.temperatureC}°C")
        KeyVal("Voltage", "${b.voltageMv} mV")
        if (b.currentNowMicroA != Int.MIN_VALUE) KeyVal("Current now", "${b.currentNowMicroA / 1000} mA")
        if (b.powerWatts != 0f) KeyVal("Power", String.format(java.util.Locale.US, "%.2f W", kotlin.math.abs(b.powerWatts)))
        if (b.chargeCounterMicroAh != Int.MIN_VALUE) KeyVal("Charge counter", "${b.chargeCounterMicroAh / 1000} mAh")
        b.technology?.let { KeyVal("Technology", it) }
    }
}

@Composable
private fun DrainSection(drainers: List<DrainEntry>) {
    SectionCard("Top battery drain (estimated, ${drainers.size})") {
        drainers.forEach { d ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(
                    d.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(String.format(java.util.Locale.US, "%.1f mAh", d.mAh), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ThermalSection(report: DiagnosticsReport) {
    val hasData = report.thermals.isNotEmpty() || report.thermalStatus >= 0 || !report.thermalHeadroom.isNaN()
    if (!hasData) return
    SectionCard("Thermals") {
        if (report.thermalStatus >= 0) KeyVal("Thermal status", thermalStatusText(report.thermalStatus))
        if (!report.thermalHeadroom.isNaN()) {
            KeyVal("Headroom", String.format(java.util.Locale.US, "%.2f  (1.0 = throttling)", report.thermalHeadroom))
        }
        report.thermals.sortedByDescending { it.tempC }.take(8).forEach {
            KeyVal(it.type, "${it.tempC}°C")
        }
    }
}

private fun thermalStatusText(s: Int) = when (s) {
    0 -> "None"; 1 -> "Light"; 2 -> "Moderate"; 3 -> "Severe"
    4 -> "Critical"; 5 -> "Emergency"; 6 -> "Shutdown"; else -> "Unknown"
}

@Composable
private fun CpuSection(report: DiagnosticsReport) {
    if (report.cores.isEmpty()) return
    SectionCard("CPU (${report.cores.size} cores${report.governor?.let { " • $it" } ?: ""})") {
        Text(
            "Clock ceiling at ${(report.avgThrottleRatio * 100).toInt()}% of hardware max",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.padding(2.dp))
        report.cores.forEach { core -> CoreRow(core) }
    }
}

@Composable
private fun CoreRow(core: CpuCore) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text("cpu${core.index}", modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelMedium)
        Text(
            if (!core.online) "offline" else "${core.curMhz} / ${core.maxMhz} MHz (hw ${core.hwMaxMhz})",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.padding(4.dp))
            content()
        }
    }
}

@Composable
private fun KeyVal(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(key, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LabeledBar(label: String, percent: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label: $percent%", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}
