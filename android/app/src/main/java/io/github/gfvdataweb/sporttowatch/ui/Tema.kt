package io.github.gfvdataweb.sporttowatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sem dynamic color do Android 12+: mesma identidade em qualquer celular.

private val CoresClaras = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF4F6FB),
    onSurface = Color(0xFF0F172A),
    surfaceContainer = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF64748B),
)

private val CoresEscuras = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1120),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFE8EDF7),
    surface = Color(0xFF0B1120),
    onSurface = Color(0xFFE8EDF7),
    surfaceContainer = Color(0xFF131C31),
    onSurfaceVariant = Color(0xFF93A1BD),
)

/** Tema do app: claro/escuro seguindo o sistema. */
@Composable
fun SportToWatchTheme(
    escuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (escuro) CoresEscuras else CoresClaras, content = content)
}
