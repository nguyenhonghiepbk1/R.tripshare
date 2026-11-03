package com.hiepnguyen.tripfinance.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val EmeraldPrimary = Color(0xFF0D9488)
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldPrimaryContainer = Color(0xFFCCFBF1)
val EmeraldOnPrimaryContainer = Color(0xFF115E59)

val IndigoSecondary = Color(0xFF4F46E5)
val IndigoOnSecondary = Color(0xFFFFFFFF)
val IndigoSecondaryContainer = Color(0xFFE0E7FF)
val IndigoOnSecondaryContainer = Color(0xFF3730A3)

val AmberTertiary = Color(0xFFD97706)
val AmberOnTertiary = Color(0xFFFFFFFF)
val AmberTertiaryContainer = Color(0xFFFEF3C7)
val AmberOnTertiaryContainer = Color(0xFF92400E)

val SuccessGreen = Color(0xFF10B981)
val SuccessGreenLight = Color(0xFFECFDF5)
val DangerRed = Color(0xFFEF4444)
val DangerRedLight = Color(0xFFFEF2F2)
val WarningOrange = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF3B82F6)

val NeutralDarkBg = Color(0xFF0F172A)
val NeutralDarkSurface = Color(0xFF1E293B)
val NeutralDarkSurfaceCard = Color(0xFF334155)

val NeutralLightBg = Color(0xFFF8FAFC)
val NeutralLightSurface = Color(0xFFFFFFFF)
val NeutralLightSurfaceCard = Color(0xFFF1F5F9)
val NeutralBorder = Color(0xFFE2E8F0)

@Immutable
data class ExtendedColorScheme(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textMuted: Color
)

val LightExtendedColorScheme = ExtendedColorScheme(
    success = Color(0xFF10B981),
    onSuccess = Color.White,
    successContainer = Color(0xFFD1FAE5),
    onSuccessContainer = Color(0xFF065F46),
    warning = Color(0xFFF59E0B),
    onWarning = Color.White,
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF92400E),
    danger = Color(0xFFEF4444),
    onDanger = Color.White,
    dangerContainer = Color(0xFFFEE2E2),
    onDangerContainer = Color(0xFF991B1B),
    info = Color(0xFF3B82F6),
    onInfo = Color.White,
    infoContainer = Color(0xFFDBEAFE),
    onInfoContainer = Color(0xFF1E40AF),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE2E8F0),
    textMuted = Color(0xFF64748B)
)

val DarkExtendedColorScheme = ExtendedColorScheme(
    success = Color(0xFF34D399),
    onSuccess = Color(0xFF064E3B),
    successContainer = Color(0xFF065F46),
    onSuccessContainer = Color(0xFFA7F3D0),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF78350F),
    warningContainer = Color(0xFF92400E),
    onWarningContainer = Color(0xFFFDE68A),
    danger = Color(0xFFF87171),
    onDanger = Color(0xFF7F1D1D),
    dangerContainer = Color(0xFF991B1B),
    onDangerContainer = Color(0xFFFECACA),
    info = Color(0xFF60A5FA),
    onInfo = Color(0xFF1E3A8A),
    infoContainer = Color(0xFF1E40AF),
    onInfoContainer = Color(0xFFBFDBFE),
    cardBackground = Color(0xFF1E293B),
    cardBorder = Color(0xFF334155),
    textMuted = Color(0xFF94A3B8)
)
