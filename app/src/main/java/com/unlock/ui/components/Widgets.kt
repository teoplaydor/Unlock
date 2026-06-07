package com.unlock.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unlock.core.IconLoader
import com.unlock.diagnostics.SlowdownFinding
import com.unlock.ui.theme.DangerRed
import com.unlock.ui.theme.OkGreen
import com.unlock.ui.theme.Teal
import com.unlock.ui.theme.WarnAmber

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = IconLoader.load(context, packageName)
    }
    val img = image
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = packageName.substringAfterLast('.').take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SeverityDot(severity: SlowdownFinding.Severity, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(severity.color()),
    )
}

fun SlowdownFinding.Severity.color(): Color = when (this) {
    SlowdownFinding.Severity.OK -> OkGreen
    SlowdownFinding.Severity.INFO -> Teal
    SlowdownFinding.Severity.WARN -> WarnAmber
    SlowdownFinding.Severity.CRITICAL -> DangerRed
}

@Composable
fun TagChip(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
