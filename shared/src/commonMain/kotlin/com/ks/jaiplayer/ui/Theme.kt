package com.ks.jaiplayer.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes that change with the form factor: TV is read from three metres away. */
data class Metrics(
    val icon: Dp,
    val button: Dp,
    val gap: Dp,
    /** Margins kept clear on every screen; TV uses the suite's 48 dp / 27 dp overscan margins. */
    val safe: PaddingValues,
    val fontScale: Float,
)

val LocalMetrics = staticCompositionLocalOf { metricsFor(FormFactor.Desktop) }
val LocalFormFactor = staticCompositionLocalOf { FormFactor.Desktop }

fun metricsFor(form: FormFactor) = when (form) {
    FormFactor.Desktop -> Metrics(20.dp, 36.dp, 12.dp, PaddingValues(16.dp), 1f)
    FormFactor.Phone -> Metrics(24.dp, 44.dp, 12.dp, PaddingValues(16.dp), 1f)
    FormFactor.Tv -> Metrics(32.dp, 56.dp, 20.dp, PaddingValues(horizontal = 48.dp, vertical = 27.dp), 1.4f)
}

/**
 * KS JaiPlayer's colours: near-black so video stands out. [JaiTheme] takes the system's colours
 * instead outside the player where the platform offers them (Material You on Android 12+).
 */
private val colors = darkColorScheme(
    primary = Color(0xFF5B95FF), // the icon's blue
    onPrimary = Color(0xFF06163F),
    secondary = Color(0xFF9EC9FF),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF17191D),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF24272D),
    onSurfaceVariant = Color(0xFFA9AEB7),
    surfaceContainer = Color(0xFF1D2025),
    outline = Color(0xFF3A3E46),
    error = Color(0xFFFF8A80),
)

@Composable
fun JaiTheme(form: FormFactor, systemColors: ColorScheme? = null, content: @Composable () -> Unit) {
    val metrics = metricsFor(form)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalMetrics provides metrics,
        LocalFormFactor provides form,
        LocalDensity provides Density(density.density, density.fontScale * metrics.fontScale),
    ) {
        MaterialTheme(colorScheme = systemColors ?: colors, content = content)
    }
}
