package ua.school.localmumble.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = lightColorScheme(
    primary = Color(0xFF1753C7), onPrimary = Color.White,
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF14213D),
    surface = Color.White, onSurface = Color(0xFF14213D),
    onSurfaceVariant = Color(0xFF5D687B), error = Color(0xFFB3261E),
)

@Composable
fun LocalMumbleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
