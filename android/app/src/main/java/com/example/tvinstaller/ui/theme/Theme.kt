package com.example.tvinstaller.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Extra semantic tones that Material's scheme does not carry. */
@Immutable
data class TvColors(
    val success: Color,
    val waiting: Color,
    val waitingContainer: Color,
    val onWaitingContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val hairline: Color,
    val track: Color,
    val surfaceAlt: Color,
    val logInfo: Color,
    val logStep: Color,
    val logTv: Color,
    val logSuccess: Color,
    val logWarn: Color,
    val logError: Color,
)

private val LightScheme = lightColorScheme(
    primary = LightInk.primary,
    onPrimary = LightInk.onPrimary,
    primaryContainer = LightInk.primaryContainer,
    onPrimaryContainer = LightInk.onPrimaryContainer,
    inversePrimary = DarkInk.primary,
    secondary = LightInk.secondary,
    onSecondary = LightInk.onSecondary,
    secondaryContainer = LightInk.secondaryContainer,
    onSecondaryContainer = LightInk.onSecondaryContainer,
    tertiary = LightInk.waiting,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LightInk.waitingContainer,
    onTertiaryContainer = LightInk.onWaitingContainer,
    background = LightInk.background,
    onBackground = LightInk.ink,
    surface = LightInk.surface,
    onSurface = LightInk.ink,
    surfaceVariant = LightInk.surfaceAlt,
    onSurfaceVariant = LightInk.inkMuted,
    surfaceTint = LightInk.primary,
    inverseSurface = LightInk.inverseSurface,
    inverseOnSurface = LightInk.inverseOnSurface,
    error = LightInk.danger,
    onError = Color(0xFFFFFFFF),
    errorContainer = LightInk.dangerContainer,
    onErrorContainer = LightInk.onDangerContainer,
    outline = LightInk.outline,
    outlineVariant = LightInk.hairline,
)

private val DarkScheme = darkColorScheme(
    primary = DarkInk.primary,
    onPrimary = DarkInk.onPrimary,
    primaryContainer = DarkInk.primaryContainer,
    onPrimaryContainer = DarkInk.onPrimaryContainer,
    inversePrimary = LightInk.primary,
    secondary = DarkInk.secondary,
    onSecondary = DarkInk.onSecondary,
    secondaryContainer = DarkInk.secondaryContainer,
    onSecondaryContainer = DarkInk.onSecondaryContainer,
    tertiary = DarkInk.waiting,
    onTertiary = Color(0xFF2A1800),
    tertiaryContainer = DarkInk.waitingContainer,
    onTertiaryContainer = DarkInk.onWaitingContainer,
    background = DarkInk.background,
    onBackground = DarkInk.ink,
    surface = DarkInk.surface,
    onSurface = DarkInk.ink,
    surfaceVariant = DarkInk.surfaceAlt,
    onSurfaceVariant = DarkInk.inkMuted,
    surfaceTint = DarkInk.primary,
    inverseSurface = DarkInk.inverseSurface,
    inverseOnSurface = DarkInk.inverseOnSurface,
    error = DarkInk.danger,
    onError = Color(0xFF2C0907),
    errorContainer = DarkInk.dangerContainer,
    onErrorContainer = DarkInk.onDangerContainer,
    outline = DarkInk.outline,
    outlineVariant = DarkInk.hairline,
)

private val LightTvColors = TvColors(
    success = LightInk.primary,
    waiting = LightInk.waiting,
    waitingContainer = LightInk.waitingContainer,
    onWaitingContainer = LightInk.onWaitingContainer,
    danger = LightInk.danger,
    dangerContainer = LightInk.dangerContainer,
    onDangerContainer = LightInk.onDangerContainer,
    info = LightInk.info,
    hairline = LightInk.hairline,
    track = LightInk.track,
    surfaceAlt = LightInk.surfaceAlt,
    logInfo = LightInk.inkMuted,
    logStep = LightInk.primary,
    logTv = LightInk.info,
    logSuccess = LightInk.primary,
    logWarn = LightInk.waiting,
    logError = LightInk.danger,
)

private val DarkTvColors = TvColors(
    success = DarkInk.primary,
    waiting = DarkInk.waiting,
    waitingContainer = DarkInk.waitingContainer,
    onWaitingContainer = DarkInk.onWaitingContainer,
    danger = DarkInk.danger,
    dangerContainer = DarkInk.dangerContainer,
    onDangerContainer = DarkInk.onDangerContainer,
    info = DarkInk.info,
    hairline = DarkInk.hairline,
    track = DarkInk.track,
    surfaceAlt = DarkInk.surfaceAlt,
    logInfo = DarkInk.inkMuted,
    logStep = DarkInk.primary,
    logTv = DarkInk.info,
    logSuccess = DarkInk.primary,
    logWarn = DarkInk.waiting,
    logError = DarkInk.danger,
)

private val LocalTvColors = staticCompositionLocalOf { LightTvColors }

/** `MaterialTheme.tv.hairline` etc. */
val MaterialTheme.tv: TvColors
    @Composable
    @ReadOnlyComposable
    get() = LocalTvColors.current

@Composable
fun TvInstallerTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extras = if (darkTheme) DarkTvColors else LightTvColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalTvColors provides extras,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TvTypography,
            content = content,
        )
    }
}
