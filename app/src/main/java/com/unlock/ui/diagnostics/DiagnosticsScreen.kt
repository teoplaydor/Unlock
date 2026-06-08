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
import androidx.compose.material3.Switch
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
import com.unlock.core.LocalStrings
import com.unlock.core.batteryHealthText
import com.unlock.core.batteryStatusText
import com.unlock.core.thermalStatusTextL
import com.unlock.data.DrainEntry
import com.unlock.data.MemEntry
import com.unlock.diagnostics.CpuCore
import com.unlock.ui.components.MessageToast
import com.unlock.diagnostics.DiagnosticsReport
import com.unlock.ui.components.SeverityDot

@Composable
fun DiagnosticsScreen(vm: DiagnosticsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s.systemDiagnostics, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::refresh) { Text(if (state.loading) "…" else s.refresh) }
        }

        MessageToast(state.message) { vm.clearMessage() }

        if (state.shizukuReady) {
            SectionCard(s.performanceAntiThrottle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.antiThrottleDesc,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(checked = state.antiThrottleOn, onCheckedChange = { vm.setAntiThrottle(it) })
                }
            }
        }

        val report = state.report ?: return@Column

        FindingsSection(report)
        if (state.shizukuReady && state.drainers.isNotEmpty()) DrainSection(state.drainers)
        if (state.shizukuReady && state.ramConsumers.isNotEmpty()) RamSection(state.ramConsumers)
        report.battery?.let { BatterySection(it) }
        ThermalSection(report)
        CpuSection(report)
        report.memory?.let { mem ->
            SectionCard(s.memory) {
                LabeledBar(s.usedLabel, mem.usedPercent)
                Text("${Format.bytes(mem.totalBytes - mem.availBytes)} / ${Format.bytes(mem.totalBytes)} • ${Format.bytes(mem.availBytes)} ${s.freeSuffix}", style = MaterialTheme.typography.bodySmall)
                if (mem.lowMemory) Text(s.lowMemory, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        report.storage?.let { st ->
            SectionCard(s.tStorage) {
                LabeledBar(s.usedLabel, st.usedPercent)
                Text("${Format.bytes(st.freeBytes)} ${s.freeSuffix} / ${Format.bytes(st.totalBytes)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FindingsSection(report: DiagnosticsReport) {
    val s = LocalStrings.current
    SectionCard(s.whySlow) {
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
    val s = LocalStrings.current
    SectionCard(s.batteryPower) {
        KeyVal(s.dLevel, "${b.percent}%  (${batteryStatusText(b.statusCode, s)})")
        KeyVal(s.dVoltage, "${b.voltageMv} mV")
        KeyVal(s.dHealth, batteryHealthText(b.healthCode, s))
        if (b.sohPercent in 1..100) KeyVal(s.dSoh, "${b.sohPercent}%")
        if (b.cycleCount >= 0) KeyVal(s.dCycles, "${b.cycleCount}")
        if (b.chargeFullUah > 0 && b.chargeFullDesignUah > 0) {
            KeyVal(s.dCapacity, "${b.chargeFullUah / 1000} / ${b.chargeFullDesignUah / 1000} mAh")
        }
        KeyVal(s.dTemperature, "${b.temperatureC}°C")
        if (b.currentNowMicroA != Int.MIN_VALUE) KeyVal(s.dCurrentNow, "${b.currentNowMicroA / 1000} mA")
        if (b.powerWatts != 0f) KeyVal(s.dPower, String.format(java.util.Locale.US, "%.2f W", kotlin.math.abs(b.powerWatts)))
        if (b.chargeCounterMicroAh != Int.MIN_VALUE) KeyVal(s.dChargeCounter, "${b.chargeCounterMicroAh / 1000} mAh")
        b.technology?.let { KeyVal(s.dTechnology, it) }
        Text(
            s.voltageNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun DrainSection(drainers: List<DrainEntry>) {
    val s = LocalStrings.current
    SectionCard(String.format(s.topDrainFmt, drainers.size)) {
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
private fun RamSection(items: List<MemEntry>) {
    val s = LocalStrings.current
    SectionCard(String.format(s.topRamFmt, items.size)) {
        items.forEach { e ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(
                    e.processName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(Format.bytes(e.pssKb * 1024), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ThermalSection(report: DiagnosticsReport) {
    val hasData = report.thermals.isNotEmpty() || report.thermalStatus >= 0 || !report.thermalHeadroom.isNaN()
    if (!hasData) return
    val s = LocalStrings.current
    SectionCard(s.thermals) {
        if (report.thermalStatus >= 0) KeyVal(s.thermalStatusLabel, thermalStatusTextL(report.thermalStatus, s))
        if (!report.thermalHeadroom.isNaN()) {
            KeyVal(s.headroom, String.format(java.util.Locale.US, "%.2f  (1.0 = throttling)", report.thermalHeadroom))
        }
        report.thermals.sortedByDescending { it.tempC }.take(8).forEach {
            KeyVal(it.type, "${it.tempC}°C")
        }
    }
}


@Composable
private fun CpuSection(report: DiagnosticsReport) {
    if (report.cores.isEmpty()) return
    val s = LocalStrings.current
    SectionCard(String.format(s.cpuTitleFmt, report.cores.size, report.governor?.let { " • $it" } ?: "")) {
        Text(
            String.format(s.clockCeilingFmt, (report.avgThrottleRatio * 100).toInt()),
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
