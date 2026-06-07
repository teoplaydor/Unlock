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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
            "Apps that start themselves on boot (${state.entries.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        state.message?.let { msg ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = vm::clearMessage) { Text("OK") }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.entries, key = { it.packageName + it.receiverClass + it.action }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(entry.packageName, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            entry.action.substringAfterLast('.'),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                            Text(entry.receiverClass.substringAfterLast('.'), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (entry.isProtected) TagChip("Core", MaterialTheme.colorScheme.error)
                            if (!entry.isEnabledComponent) TagChip("off", MaterialTheme.colorScheme.error)
                        }
                    }
                    Switch(
                        checked = entry.isEnabledComponent,
                        // Block turning OFF a protected package's autostart; turning ON is fine.
                        enabled = state.shizukuReady && !(entry.isProtected && entry.isEnabledComponent),
                        onCheckedChange = { vm.toggleComponent(entry) },
                    )
                }
            }
        }
    }
}
