package com.unlock.ui.autostart

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unlock.core.LocalStrings
import com.unlock.ui.components.AppIcon
import com.unlock.ui.components.MessageToast
import com.unlock.ui.components.TagChip

@Composable
fun AutostartScreen(vm: AutostartViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current

    MessageToast(state.message) { vm.clearMessage() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            String.format(s.autostartTitleFmt, state.apps.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (!state.shizukuReady) {
            Text(
                s.connectShizukuAutostart,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.apps, key = { it.packageName }) { app ->
                val stopped = app.packageName in state.stopped
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.packageName, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (stopped) s.autostartStopped else String.format(s.autostartAllowedFmt, app.receivers.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (stopped) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        if (app.isProtected) {
                            Row(modifier = Modifier.padding(top = 2.dp)) { TagChip(s.tagCore, MaterialTheme.colorScheme.error) }
                        }
                    }
                    if (app.packageName in state.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Switch(
                            checked = !stopped, // ON = autostart allowed
                            enabled = state.shizukuReady && !app.isProtected,
                            onCheckedChange = { allowed -> vm.setStopped(app, !allowed) },
                        )
                    }
                }
            }
        }
    }
}
