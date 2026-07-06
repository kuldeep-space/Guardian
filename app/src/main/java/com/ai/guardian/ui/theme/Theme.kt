package com.ai.guardian.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Dark color scheme ────────────────────────────────────────────────────────

private val GuardianDarkColorScheme = darkColorScheme(
    primary              = GuardPrimary,
    onPrimary            = GuardBg,
    primaryContainer     = GuardPrimaryDim,
    onPrimaryContainer   = GuardPrimaryMid,

    secondary            = GuardPrimaryMid,
    onSecondary          = GuardBg,
    secondaryContainer   = GuardSurface2,
    onSecondaryContainer = GuardText,

    tertiary             = GuardSuccess,
    onTertiary           = GuardBg,
    tertiaryContainer    = GuardSuccessDim,
    onTertiaryContainer  = GuardSuccess,

    background           = GuardBg,
    onBackground         = GuardText,

    surface              = GuardSurface,
    onSurface            = GuardText,
    surfaceVariant       = GuardSurface2,
    onSurfaceVariant     = GuardTextSub,

    outline              = GuardRim,
    outlineVariant       = GuardSurface3,

    error                = GuardDanger,
    onError              = GuardBg,
    errorContainer       = GuardDangerDim,
    onErrorContainer     = GuardDanger,

    scrim                = GuardBg,
)

// ─── Light color scheme ───────────────────────────────────────────────────────

private val GuardianLightColorScheme = lightColorScheme(
    primary              = LightPrimaryDeep,
    onPrimary            = androidx.compose.ui.graphics.Color.White,
    primaryContainer     = LightPrimaryDim,
    onPrimaryContainer   = LightPrimaryDeep,

    secondary            = LightPrimaryDeep,
    onSecondary          = androidx.compose.ui.graphics.Color.White,
    secondaryContainer   = LightSurface2,
    onSecondaryContainer = LightText,

    tertiary             = LightSuccess,
    onTertiary           = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer    = LightSuccessDim,
    onTertiaryContainer  = LightSuccess,

    background           = LightBg,
    onBackground         = LightText,

    surface              = LightSurface,
    onSurface            = LightText,
    surfaceVariant       = LightSurface2,
    onSurfaceVariant     = LightTextSub,

    outline              = LightRim,
    outlineVariant       = LightSurface3,

    error                = LightDanger,
    onError              = LightBg,
    errorContainer       = LightDangerDim,
    onErrorContainer     = LightDanger,

    scrim                = LightBg,
)

// ─── Theme entry point ────────────────────────────────────────────────────────

/**
 * @param themeMode LIGHT / DARK / SYSTEM. Defaults to SYSTEM so callers that
 *                  have not been updated yet continue to work without changes.
 */
@Composable
fun GuardianAITheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemIsDark
    }

    val colorScheme = if (useDark) GuardianDarkColorScheme else GuardianLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars  = !useDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
