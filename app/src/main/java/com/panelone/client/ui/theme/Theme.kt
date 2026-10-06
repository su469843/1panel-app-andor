package com.panelone.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF16C79A)
private val TealDark = Color(0xFF0E9E7A)
private val Sky = Color(0xFF38BDF8)
private val Violet = Color(0xFFA78BFA)
private val Amber = Color(0xFFFBBF24)
private val Rose = Color(0xFFFB7185)

private val DarkScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF04231B),
    primaryContainer = Color(0xFF0C3B31),
    onPrimaryContainer = Color(0xFFB9F5E5),
    secondary = Sky,
    onSecondary = Color(0xFF04212E),
    tertiary = Violet,
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF121A2A),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1B2536),
    onSurfaceVariant = Color(0xFFA9B6C9),
    error = Rose,
    outline = Color(0xFF33415A),
)

private val LightScheme = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F3E6),
    onPrimaryContainer = Color(0xFF04352A),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF4B5563),
    error = Color(0xFFE11D48),
    outline = Color(0xFFC3CCDA),
)

@Composable
fun PanelOneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}

/** 状态色：运行=绿，停止=灰，异常=红 */
@Composable
fun statusColor(state: String): Color {
    val s = state.lowercase()
    return when {
        s == "running" || s == "start" || s == "healthy" -> Teal
        s == "stopped" || s == "exited" || s == "stop" -> MaterialTheme.colorScheme.onSurfaceVariant
        s == "installing" || s == "restarting" || s == "uninstalling" -> Amber
        s.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Rose
    }
}
