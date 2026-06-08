package com.unlock.ui.tweaks

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unlock.core.LocalStrings
import com.unlock.core.Prefs
import com.unlock.data.TweakKind
import com.unlock.ui.components.MessageToast

@Composable
fun TweaksScreen(vm: TweaksViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val lang by Prefs.language.collectAsStateWithLifecycle()
    val ru = lang == "ru"

    MessageToast(state.message) { vm.clearMessage() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            state.deviceLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        if (!state.shizukuReady) {
            Text(
                s.tweaksNeedShizuku,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            state.byCategory.forEach { (category, rows) ->
                item(key = "h_$category") {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(rows, key = { it.tweak.id }) { row ->
                    TweakRow(
                        title = row.tweak.title(ru),
                        desc = row.tweak.desc(ru),
                        kind = row.tweak.kind,
                        isOn = row.isOn,
                        busy = row.tweak.id in state.busy,
                        enabled = state.shizukuReady,
                        onToggle = { vm.toggle(row.tweak, it) },
                        onAction = { vm.action(row.tweak) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TweakRow(
    title: String,
    desc: String,
    kind: TweakKind,
    isOn: Boolean?,
    busy: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onAction: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc.isNotBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            kind == TweakKind.TOGGLE -> Switch(
                checked = isOn == true,
                enabled = enabled,
                onCheckedChange = onToggle,
            )
            else -> FilledTonalButton(onClick = onAction, enabled = enabled) { Text(s.tweaksApply) }
        }
    }
}
