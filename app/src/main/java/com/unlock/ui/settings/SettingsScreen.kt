package com.unlock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unlock.core.Format
import com.unlock.core.Permissions
import com.unlock.core.ServiceLocator
import com.unlock.data.ActionLog
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    var usage by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Shizuku ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Shizuku — full power without root", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(2.dp))
                Text(
                    when (shizuku) {
                        ShizukuManager.State.READY -> "Connected ✓  (running as ${if (ShizukuManager.isRoot()) "root" else "shell"})"
                        ShizukuManager.State.NEEDS_PERMISSION -> "Shizuku is running — permission needed."
                        ShizukuManager.State.NOT_RUNNING -> "Not connected."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.padding(4.dp))
                when (shizuku) {
                    ShizukuManager.State.NEEDS_PERMISSION ->
                        Button(onClick = { ShizukuManager.requestPermission() }) { Text("Grant permission") }
                    ShizukuManager.State.READY ->
                        Button(onClick = {
                            scope.launch {
                                val r = ServiceLocator.appActions.grantSelfPrivileges()
                                message = "Self-grant: ${r.count { it.success }}/${r.size} ok"
                                usage = Permissions.hasUsageAccess(context)
                            }
                        }) { Text("Grant Unlock extra privileges") }
                    ShizukuManager.State.NOT_RUNNING -> {
                        Text(
                            "How to start Shizuku (one-time, no root):\n" +
                                "1. Install Shizuku from GitHub / Play.\n" +
                                "2. Enable Developer options → Wireless debugging (Android 11+).\n" +
                                "3. In Shizuku, tap “Start” (pairing) — or start it from a PC with:\n" +
                                "     adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                                "4. Come back here and grant permission.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.padding(4.dp))
                        OutlinedButton(onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (launch != null) context.startActivity(launch) else message = "Shizuku app not installed."
                        }) { Text("Open Shizuku") }
                    }
                }
            }
        }

        // ---- Usage access ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Usage access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (usage) "Granted ✓ — sizes, battery blame and last-used are available."
                    else "Not granted — needed for app sizes and usage stats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.padding(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { context.startActivity(Permissions.usageAccessSettingsIntent()) }) { Text("Open settings") }
                    OutlinedButton(onClick = { usage = Permissions.hasUsageAccess(context) }) { Text("Re-check") }
                }
            }
        }

        // ---- Action history + undo ----
        val actions by ActionLog.records.collectAsStateWithLifecycle()
        if (actions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Recent actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { ActionLog.clear() }) { Text("Clear") }
                    }
                    actions.asReversed().take(12).forEach { rec ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${rec.action} · ${rec.pkg}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    Format.timeAgo(rec.time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            if (rec.reversible) {
                                TextButton(
                                    enabled = shizuku == ShizukuManager.State.READY,
                                    onClick = {
                                        scope.launch {
                                            val r = ActionLog.undo(rec)
                                            message = when {
                                                r == null -> "Not reversible"
                                                r.success -> "Undone: ${rec.action}"
                                                else -> "Failed: ${r.text.take(80)}"
                                            }
                                        }
                                    },
                                ) { Text("Undo") }
                            }
                        }
                    }
                }
            }
        }

        // ---- About / safety ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Safety", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "• `Uninstall` removes apps for the current user only (--user 0). Most are restorable with " +
                        "“Restore”, and a factory reset brings system apps back.\n" +
                        "• `Disable` is fully reversible.\n" +
                        "• Don't remove core packages (SystemUI, phone, providers) — it can cause a bootloop.\n" +
                        "• Unlock never requires root and sends nothing off-device.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
