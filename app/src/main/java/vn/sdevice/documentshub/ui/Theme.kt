package vn.sdevice.documentshub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Bảng màu lấy từ bản desktop (xanh ngọc, nền "mặt bàn" xám xanh)
private val Light = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE6), onPrimaryContainer = Color(0xFF06322D),
    background = Color(0xFFE9ECE9), onBackground = Color(0xFF16201B),
    surface = Color(0xFFF7F9F7), onSurface = Color(0xFF16201B),
    surfaceVariant = Color(0xFFDDE1DD), onSurfaceVariant = Color(0xFF56635B),
    surfaceContainer = Color(0xFFF1F4F1), surfaceContainerHigh = Color(0xFFE9ECE9),
    outline = Color(0xFF8B978F), outlineVariant = Color(0xFFCFD5D0),
    error = Color(0xFFB3261E)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF34BEA5), onPrimary = Color(0xFF061E19),
    primaryContainer = Color(0xFF0E4A41), onPrimaryContainer = Color(0xFFBDF1E6),
    background = Color(0xFF0E1311), onBackground = Color(0xFFE6ECE8),
    surface = Color(0xFF141B18), onSurface = Color(0xFFE6ECE8),
    surfaceVariant = Color(0xFF1E2823), onSurfaceVariant = Color(0xFF8C9C94),
    surfaceContainer = Color(0xFF18211D), surfaceContainerHigh = Color(0xFF1E2823),
    outline = Color(0xFF5E6E66), outlineVariant = Color(0xFF26312B),
    error = Color(0xFFF2B8B5)
)

@Composable
fun DocumentsHubTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val useDark = dark ?: isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (useDark) Dark else Light, content = content)
}
