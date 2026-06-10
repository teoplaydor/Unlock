package com.unlock.ui.tweaks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unlock.core.LocalStrings
import com.unlock.core.Prefs
import com.unlock.data.Profile
import com.unlock.data.Profiles
import com.unlock.data.Tweak
import com.unlock.data.TweakKind
import com.unlock.ui.components.MessageToast
import com.unlock.ui.theme.DangerRed
import com.unlock.ui.theme.OkGreen
import com.unlock.ui.theme.WarnAmber
import java.util.Locale
import kotlin.math.roundToInt

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
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text(s.tweaksSearch) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.query.isBlank()) {
                item(key = "profiles") {
                    ProfilesSection(ru = ru, enabled = state.shizukuReady) { p, apply -> vm.applyProfile(p, apply) }
                }
            }
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
                    val busy = row.tweak.id in state.busy
                    if (row.tweak.kind == TweakKind.SLIDER) {
                        SliderRow(row.tweak, ru, row.value, busy, state.shizukuReady) { v -> vm.setSlider(row.tweak, v) }
                    } else {
                        TweakRow(
                            title = row.tweak.title(ru),
                            desc = row.tweak.desc(ru),
                            risk = row.tweak.risk,
                            kind = row.tweak.kind,
                            isOn = row.isOn,
                            busy = busy,
                            enabled = state.shizukuReady,
                            onToggle = { vm.toggle(row.tweak, it) },
                            onAction = { vm.action(row.tweak) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilesSection(ru: Boolean, enabled: Boolean, onApply: (Profile, Boolean) -> Unit) {
    val s = LocalStrings.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            s.profilesTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        Profiles.all.forEach { p ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(p.title(ru), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        p.desc(ru),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onApply(p, true) }, enabled = enabled) { Text(s.tweaksApply) }
                        OutlinedButton(onClick = { onApply(p, false) }, enabled = enabled) { Text(s.profileRevert) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    tweak: Tweak,
    ru: Boolean,
    value: Float?,
    busy: Boolean,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    var pos by remember(tweak.id) { mutableStateOf(value ?: tweak.default) }
    LaunchedEffect(value) { if (value != null) pos = value }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(riskColor(tweak.risk)))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tweak.title(ru), style = MaterialTheme.typography.bodyLarge)
                if (tweak.desc(ru).isNotBlank()) {
                    Text(
                        tweak.desc(ru),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(sliderLabel(pos, tweak), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = { onChange(pos) },
            valueRange = tweak.min..tweak.max,
            steps = sliderSteps(tweak),
            enabled = enabled,
        )
    }
}

@Composable
private fun TweakRow(
    title: String,
    desc: String,
    risk: String,
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
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(riskColor(risk)))
        Spacer(Modifier.width(10.dp))
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
            kind == TweakKind.TOGGLE -> Switch(checked = isOn == true, enabled = enabled, onCheckedChange = onToggle)
            else -> FilledTonalButton(onClick = onAction, enabled = enabled) { Text(s.tweaksApply) }
        }
    }
}

private fun sliderSteps(t: Tweak): Int {
    val intervals = ((t.max - t.min) / t.step).roundToInt()
    return (intervals - 1).coerceAtLeast(0)
}

private fun sliderLabel(v: Float, t: Tweak): String {
    val shown = if (t.step >= 1f) v.roundToInt().toString()
    else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    return shown + t.unitLabel
}

private fun riskColor(risk: String): Color = when (risk.lowercase()) {
    "safe" -> OkGreen
    "caution" -> WarnAmber
    "risky", "risk", "dangerous" -> DangerRed
    else -> WarnAmber
}
