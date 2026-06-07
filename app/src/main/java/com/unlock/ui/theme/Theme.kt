package com.unlock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val BrandDark = darkColorScheme(
    primary = Teal,
    secondary = TealDark,
    background = Surface,
    surface = Surface,
    surfaceVariant = SurfaceElevated,
    onPrimary = Surface,
    onBackground = OnSurface,
    onSurface = OnSurface,
)

private val BrandLight = lightColorScheme(
    primary = TealDark,
    secondary = Teal,
)

@Composable
fun UnlockTheme(
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
