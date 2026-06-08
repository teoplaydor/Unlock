package com.unlock.ui.running

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.unlock.core.LocalStrings
import com.unlock.ui.components.AppIcon
import com.unlock.ui.components.MessageToast
import com.unlock.ui.components.TagChip

@Composable
fun RunningScreen(vm: RunningViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.shizukuReady) String.format(s.liveProcessesFmt, state.processes.size)
                else s.recentlyActive,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        }

        MessageToast(state.message) { vm.clearMessage() }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.processes, key = { it.processName }) { proc ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(proc.packageName, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(proc.processName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (proc.importance.isNotBlank() && proc.importance != "recent") {
                            TagChip(proc.importance, MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (state.shizukuReady) {
                        TextButton(onClick = { vm.sleep(proc.packageName) }) { Text(s.actSleep) }
                        TextButton(onClick = { vm.forceStop(proc.packageName) }) {
                            Text(s.actStop, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
