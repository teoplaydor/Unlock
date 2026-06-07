package com.unlock.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unlock.core.Format
import com.unlock.data.AppInfo
import com.unlock.ui.components.AppIcon
import com.unlock.ui.components.TagChip
import com.unlock.ui.theme.DangerRed
import com.unlock.ui.theme.WarnAmber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(vm: AppsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var sheetApp by remember { mutableStateOf<AppInfo?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search ${state.apps.size} apps") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        FilterRow(state.filter, state.sort, onFilter = vm::setFilter, onSort = vm::setSort)

        state.message?.let { msg ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = vm::clearMessage) { Text("OK") }
                }
            }
        }

        if (state.selected.isNotEmpty()) {
            SelectionBar(
                count = state.selected.size,
                shizukuReady = state.shizukuReady,
                onBatch = vm::batch,
                onSelectAll = vm::selectAllVisible,
                onClear = vm::clearSelection,
            )
        }

        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.visible, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        selected = app.packageName in state.selected,
                        selectionMode = state.selected.isNotEmpty(),
                        onClick = {
                            if (state.selected.isNotEmpty()) vm.toggleSelect(app.packageName)
                            else sheetApp = app
                        },
                        onLongClick = { vm.toggleSelect(app.packageName) },
                    )
                }
            }
        }
    }

    sheetApp?.let { app ->
        AppActionsSheet(
            app = app,
            shizukuReady = state.shizukuReady,
            vm = vm,
            onDismiss = { sheetApp = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    filter: AppsViewModel.Filter,
    sort: AppsViewModel.Sort,
    onFilter: (AppsViewModel.Filter) -> Unit,
    onSort: (AppsViewModel.Sort) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppsViewModel.Filter.entries.forEach { f ->
            FilterChip(
                selected = filter == f,
                onClick = { onFilter(f) },
                label = { Text(f.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
            )
        }
        Spacer(Modifier.width(4.dp))
        var menu by remember { mutableStateOf(false) }
        AssistChip(
            onClick = { menu = true },
            label = { Text("Sort") },
            leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = null) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            AppsViewModel.Sort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = { onSort(s); menu = false },
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    shizukuReady: Boolean,
    onBatch: (AppsViewModel.Batch) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            Text("$count", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.DoneAll, contentDescription = "Select all") }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = shizukuReady, onClick = { onBatch(AppsViewModel.Batch.SLEEP) }) { Text("Sleep") }
            TextButton(enabled = shizukuReady, onClick = { onBatch(AppsViewModel.Batch.DISABLE) }) { Text("Disable") }
            TextButton(enabled = shizukuReady, onClick = { onBatch(AppsViewModel.Batch.UNINSTALL) }) { Text("Uninstall") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                when (app.safetyTier) {
                    com.unlock.data.SafetyTier.PROTECTED -> TagChip("Core", DangerRed)
                    com.unlock.data.SafetyTier.DEBLOAT_SAFE -> TagChip("Debloat-safe", com.unlock.ui.theme.OkGreen)
                    else -> {}
                }
                if (app.isSystem) TagChip("System", WarnAmber)
                if (!app.isEnabled) TagChip("Disabled", DangerRed)
                if (app.hasBootReceiver) TagChip("Autostart")
                if (app.totalBytes >= 0) TagChip(Format.bytes(app.totalBytes), MaterialTheme.colorScheme.secondary)
                if (app.lastUsed > 0) TagChip("used ${Format.timeAgo(app.lastUsed)}", MaterialTheme.colorScheme.secondary)
            }
        }
        if (selectionMode) {
            androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
    }
}
