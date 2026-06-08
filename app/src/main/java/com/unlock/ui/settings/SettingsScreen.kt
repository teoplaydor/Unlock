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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.unlock.core.LocalStrings
import com.unlock.core.Permissions
import com.unlock.core.Prefs
import com.unlock.core.ServiceLocator
import com.unlock.data.ActionLog
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    val lang by Prefs.language.collectAsStateWithLifecycle()
    var usage by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Language ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(s.sLanguage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = lang == "en", onClick = { Prefs.setLanguage("en") }, label = { Text("English") })
                    FilterChip(selected = lang == "ru", onClick = { Prefs.setLanguage("ru") }, label = { Text("Русский") })
                }
            }
        }

        // ---- Shizuku ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(s.sShizukuTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(2.dp))
                Text(
                    when (shizuku) {
                        ShizukuManager.State.READY ->
                            String.format(s.sShizukuConnectedFmt, if (ShizukuManager.isRoot()) "root" else "shell")
                        ShizukuManager.State.NEEDS_PERMISSION -> s.sShizukuNeedsPerm
                        ShizukuManager.State.NOT_RUNNING -> s.sShizukuNotConnected
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.padding(4.dp))
                when (shizuku) {
                    ShizukuManager.State.NEEDS_PERMISSION ->
                        Button(onClick = { ShizukuManager.requestPermission() }) { Text(s.sGrantPermission) }
                    ShizukuManager.State.READY ->
                        Button(onClick = {
                            scope.launch {
                                val r = ServiceLocator.appActions.grantSelfPrivileges()
                                message = "Self-grant: ${r.count { it.success }}/${r.size} ok"
                                usage = Permissions.hasUsageAccess(context)
                            }
                        }) { Text(s.sGrantExtra) }
                    ShizukuManager.State.NOT_RUNNING -> {
                        Text(s.sShizukuHowto, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.padding(4.dp))
                        OutlinedButton(onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (launch != null) context.startActivity(launch) else message = "Shizuku not installed."
                        }) { Text(s.sOpenShizuku) }
                    }
                }
            }
        }

        // ---- Usage access ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(s.sUsageTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (usage) s.sUsageGranted else s.sUsageNotGranted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.padding(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { context.startActivity(Permissions.usageAccessSettingsIntent()) }) { Text(s.sOpenSettings) }
                    OutlinedButton(onClick = { usage = Permissions.hasUsageAccess(context) }) { Text(s.sRecheck) }
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
                            s.sRecentActions,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { ActionLog.clear() }) { Text(s.sClear) }
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
                                ) { Text(s.sUndo) }
                            }
                        }
                    }
                }
            }
        }

        // ---- Safety ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(s.sSafety, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(s.sSafetyBody, style = MaterialTheme.typography.bodySmall)
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
