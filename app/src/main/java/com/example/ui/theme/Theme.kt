package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class CampusStatusColors(
    val present: Color,
    val presentContainer: Color,
    val onPresentContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val absent: Color,
    val absentContainer: Color,
    val onAbsentContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

val LocalCampusStatusColors = staticCompositionLocalOf {
    CampusStatusColors(
        present = EmeraldPrimary,
        presentContainer = EmeraldSoft,
        onPresentContainer = EmeraldDarkBg,
        warning = AmberWarning,
        warningContainer = AmberSoft,
        onWarningContainer = AmberDarkBg,
        absent = CrimsonAlert,
        absentContainer = CrimsonSoft,
        onAbsentContainer = CrimsonDarkBg,
        info = CobaltInfo,
        infoContainer = CobaltSoft,
        onInfoContainer = CobaltDarkBg
    )
}

private val LightCampusColorScheme = lightColorScheme(
    primary = OxfordNavy,
    onPrimary = Color.White,
    primaryContainer = SkyMist,
    onPrimaryContainer = OxfordNavyDark,
    secondary = EmeraldPrimary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSoft,
    onSecondaryContainer = EmeraldDarkBg,
    tertiary = CobaltSlate,
    onTertiary = Color.White,
    tertiaryContainer = PaleIce,
    onTertiaryContainer = OxfordNavy,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFDFF),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE7EEF6),
    surfaceContainerHighest = Color(0xFFDDE6F0),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonSoft,
    onErrorContainer = CrimsonDarkBg
)

private val DarkCampusColorScheme = darkColorScheme(
    primary = Color(0xFF7CC0FF),
    onPrimary = OxfordNavyDark,
    primaryContainer = CobaltSlate,
    onPrimaryContainer = SkyMist,
    secondary = EmeraldBright,
    onSecondary = Color(0xFF002116),
    secondaryContainer = EmeraldDarkBg,
    onSecondaryContainer = EmeraldSoft,
    tertiary = Color(0xFF93C5FD),
    onTertiary = OxfordNavyDark,
    tertiaryContainer = Color(0xFF1E3A5F),
    onTertiaryContainer = PaleIce,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF070D17),
    surfaceContainerLow = Color(0xFF0D1726),
    surfaceContainer = Color(0xFF132033),
    surfaceContainerHigh = Color(0xFF1A2A42),
    surfaceContainerHighest = Color(0xFF223552),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = CrimsonBright,
    onError = Color(0xFF4C0519),
    errorContainer = CrimsonDarkBg,
    onErrorContainer = CrimsonSoft
)

private val LightStatusColors = CampusStatusColors(
    present = EmeraldPrimary,
    presentContainer = EmeraldSoft,
    onPresentContainer = EmeraldDarkBg,
    warning = AmberWarning,
    warningContainer = AmberSoft,
    onWarningContainer = AmberDarkBg,
    absent = CrimsonAlert,
    absentContainer = CrimsonSoft,
    onAbsentContainer = CrimsonDarkBg,
    info = CobaltInfo,
    infoContainer = CobaltSoft,
    onInfoContainer = CobaltDarkBg
)

private val DarkStatusColors = CampusStatusColors(
    present = EmeraldBright,
    presentContainer = Color(0xFF064E3B),
    onPresentContainer = EmeraldSoft,
    warning = AmberBright,
    warningContainer = Color(0xFF5C2D0C),
    onWarningContainer = AmberSoft,
    absent = CrimsonBright,
    absentContainer = Color(0xFF6B102B),
    onAbsentContainer = CrimsonSoft,
    info = Color(0xFF60A5FA),
    infoContainer = Color(0xFF1E3A8A),
    onInfoContainer = CobaltSoft
)

val CampusRollShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CampusRollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkCampusColorScheme else LightCampusColorScheme
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalCampusStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CampusRollTypography,
            shapes = CampusRollShapes,
            content = content
        )
    }
}
