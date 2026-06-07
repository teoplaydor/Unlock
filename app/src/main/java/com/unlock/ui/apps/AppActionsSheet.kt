package com.unlock.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unlock.core.Format
import com.unlock.core.ServiceLocator
import com.unlock.data.AppInfo
import com.unlock.ui.components.AppIcon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppActionsSheet(
    app: AppInfo,
    shizukuReady: Boolean,
    vm: AppsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun close() = onDismiss()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row {
                AppIcon(app.packageName, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.size(12.dp))
            InfoLine("Version", "${app.versionName ?: "?"} (${app.versionCode})")
            InfoLine("Type", buildString {
                append(if (app.isSystem) "System" else "User")
                if (app.isUpdatedSystemApp) append(" (updated)")
                if (!app.isEnabled) append(" • Disabled")
            })
            if (app.totalBytes >= 0) {
                InfoLine("Size", "${Format.bytes(app.totalBytes)} (app ${Format.bytes(app.appBytes)}, data ${Format.bytes(app.dataBytes)}, cache ${Format.bytes(app.cacheBytes)})")
            }
            if (app.lastUsed > 0) InfoLine("Last used", Format.timeAgo(app.lastUsed))
            if (app.totalForegroundMs > 0) InfoLine("Foreground (30d)", Format.durationMs(app.totalForegroundMs))
            InfoLine("Target SDK", app.targetSdk.toString())
            app.installerPackage?.let { InfoLine("Installer", it) }

            if (app.isProtected) {
                Spacer(Modifier.size(10.dp))
                Text(
                    "⛔ Protected core package — removing or disabling it can bootloop the phone or break payments / Secure Folder. Destructive actions are blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.size(16.dp))

            val destructiveEnabled = shizukuReady && !app.isProtected
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton("Force stop", Icons.Filled.Stop, enabled = shizukuReady) { vm.forceStop(app.packageName); close() }
                SheetButton("Sleep", Icons.Filled.Bedtime, enabled = destructiveEnabled) { vm.sleep(app.packageName); close() }
                if (app.isEnabled) {
                    SheetButton("Disable", Icons.Filled.Block, enabled = destructiveEnabled) { vm.disable(app.packageName); close() }
                } else {
                    SheetButton("Enable", Icons.Filled.CheckCircle, enabled = shizukuReady) { vm.enable(app.packageName); close() }
                }
                SheetButton("Clear data", Icons.Filled.CleaningServices, enabled = destructiveEnabled) { vm.clearData(app.packageName); close() }
                if (app.isSystem) {
                    SheetButton("Restore", Icons.Filled.Restore, enabled = shizukuReady) { vm.reinstall(app.packageName); close() }
                }
                SheetButton("Uninstall", Icons.Filled.Delete, enabled = (shizukuReady || !app.isSystem) && !app.isProtected) {
                    if (shizukuReady) {
                        vm.uninstall(app.packageName)
                    } else {
                        context.startActivity(ServiceLocator.appActions.systemUninstallIntent(app.packageName))
                    }
                    close()
                }
                SheetButton("App info", Icons.Filled.Info, enabled = true) {
                    context.startActivity(ServiceLocator.appActions.appDetailsIntent(app.packageName))
                    close()
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SheetButton(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}
