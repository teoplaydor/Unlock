package com.unlock.ui.autostart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unlock.ui.components.AppIcon
import com.unlock.ui.components.TagChip

@Composable
fun AutostartScreen(vm: AutostartViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "Apps that start themselves on boot (${state.apps.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (!state.shizukuReady) {
            Text(
                "Connect Shizuku to stop autostart. (Per-component disable isn't possible without root — Unlock stops the whole app's autostart instead, which works on Samsung.)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        state.message?.let { msg ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = vm::clearMessage) { Text("OK") }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.apps, key = { it.packageName }) { app ->
                AutostartRow(
                    app = app,
                    expanded = app.packageName in state.expanded,
                    busy = app.packageName in state.busy,
                    shizukuReady = state.shizukuReady,
                    onExpand = { vm.toggleExpand(app.packageName) },
                    onStop = { vm.stopAutostart(app) },
                    onDisable = { vm.disableApp(app) },
                )
            }
        }
    }
}

@Composable
private fun AutostartRow(
    app: AutostartApp,
    expanded: Boolean,
    busy: Boolean,
    shizukuReady: Boolean,
    onExpand: () -> Unit,
    onStop: () -> Unit,
    onDisable: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${app.receivers.size} autostart trigger(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    if (app.isProtected) {
                        Row(modifier = Modifier.padding(top = 2.dp)) { TagChip("Core", MaterialTheme.colorScheme.error) }
                    }
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onExpand) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Show triggers",
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.size(4.dp))
                app.receivers.forEach {
                    Text(
                        "• $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 52.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onStop, enabled = shizukuReady && !app.isProtected) {
                    Text("Stop autostart")
                }
                OutlinedButton(onClick = onDisable, enabled = shizukuReady && !app.isProtected) {
                    Text("Disable app")
                }
            }
            if (app.isProtected) {
                Text(
                    "Protected core package — actions blocked to avoid a bootloop.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
