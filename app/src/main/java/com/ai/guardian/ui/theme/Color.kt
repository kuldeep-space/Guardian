package com.ai.guardian.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Dark Theme Tokens ────────────────────────────────────────────────────────
val GuardBg          = Color(0xFF37353E)
val GuardSurface     = Color(0xFF44444E)
val GuardSurface2    = Color(0xFF4F4B5C)
val GuardSurface3    = Color(0xFF5A5568)
val GuardRim         = Color(0xFF715A5A)

val GuardPrimary     = Color(0xFF8CC0EB)
val GuardPrimaryMid  = Color(0xFFBFDDF0)
val GuardPrimaryDim  = Color(0xFF2A4A6A)

val GuardText        = Color(0xFFD3DAD9)
val GuardTextSub     = Color(0xFF9BA3A2)
val GuardTextDim     = Color(0xFF66706F)

val GuardSuccess     = Color(0xFF76B99A)
val GuardSuccessDim  = Color(0xFF1D3D2F)
val GuardDanger      = Color(0xFFCF7070)
val GuardDangerDim   = Color(0xFF3D1D1D)
val GuardWarning     = Color(0xFFC9985A)
val GuardWarningDim  = Color(0xFF3D2E14)

// ─── Light Theme Tokens ───────────────────────────────────────────────────────
// Derived strictly from the approved light palette:
//   Primary #8CC0EB · Secondary #BFDDF0 · Surface #FFEBCC · Background #FFF9D2
val LightBg          = Color(0xFFFFF9D2)  // Approved background
val LightSurface     = Color(0xFFFFEBCC)  // Approved surface
val LightSurface2    = Color(0xFFFFDCAA)  // Slightly deeper tint of surface
val LightSurface3    = Color(0xFFEED6A0)  // Pressed state
val LightRim         = Color(0xFFD4C49A)  // Subtle divider

val LightPrimary     = Color(0xFF8CC0EB)  // Approved primary
val LightPrimaryMid  = Color(0xFFBFDDF0)  // Approved secondary
val LightPrimaryDim  = Color(0xFFD6ECFA)  // Light tint for chip/indicator bg
val LightPrimaryDeep = Color(0xFF2A6496)  // Darker primary for contrast text

val LightText        = Color(0xFF1A1C1E)  // Near-black for readability
val LightTextSub     = Color(0xFF4A5056)  // Secondary label
val LightTextDim     = Color(0xFF8A9099)  // Placeholder / disabled

val LightSuccess     = Color(0xFF2D7A5A)  // Deeper green for light bg contrast
val LightSuccessDim  = Color(0xFFD4F0E4)
val LightDanger      = Color(0xFFB53535)  // Deeper red for light bg contrast
val LightDangerDim   = Color(0xFFFADDDD)
val LightWarning     = Color(0xFF8B6020)  // Deeper amber for light bg contrast
val LightWarningDim  = Color(0xFFF5E6C8)

// ─── Backward-compat aliases (dark values) ───────────────────────────────────
val ThemeBg             = GuardBg
val ThemeCardBg         = GuardSurface
val ThemeSurfaceVariant = GuardSurface2
val ThemeAccent         = GuardPrimary
val ThemeAccentGlow     = GuardPrimaryDim
val ThemeText           = GuardText
val ThemeTextMuted      = GuardTextSub
val ThemeBorder         = GuardRim
val ThemeSuccess        = GuardSuccess
val ThemeDanger         = GuardDanger
val ThemeWarning        = GuardWarning

