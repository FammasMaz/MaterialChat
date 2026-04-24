package com.materialchat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialchat.data.local.preferences.AppPreferences

/**
 * Curated Material 3 fallback palettes.
 *
 * Dynamic color still wins when enabled and available. These palettes make the app
 * feel intentionally colorful on older devices or when the user turns wallpaper
 * color off. Each palette keeps primary/secondary/tertiary hues related so chat
 * bubbles, controls, and accents feel like one Material You family.
 */
object MaterialChatThemePalettes {
    fun colorScheme(
        palette: AppPreferences.ThemePalette,
        darkTheme: Boolean
    ): ColorScheme {
        return when (palette) {
            AppPreferences.ThemePalette.VIOLET -> if (darkTheme) violetDark else violetLight
            AppPreferences.ThemePalette.OCEAN -> if (darkTheme) oceanDark else oceanLight
            AppPreferences.ThemePalette.JADE -> if (darkTheme) jadeDark else jadeLight
            AppPreferences.ThemePalette.SUNSET -> if (darkTheme) sunsetDark else sunsetLight
            AppPreferences.ThemePalette.ROSE -> if (darkTheme) roseDark else roseLight
            AppPreferences.ThemePalette.AMBER -> if (darkTheme) amberDark else amberLight
            AppPreferences.ThemePalette.GRAPHITE -> if (darkTheme) graphiteDark else graphiteLight
        }
    }

    fun previewColor(palette: AppPreferences.ThemePalette): Color {
        return when (palette) {
            AppPreferences.ThemePalette.VIOLET -> Color(0xFF6750A4)
            AppPreferences.ThemePalette.OCEAN -> Color(0xFF006A6A)
            AppPreferences.ThemePalette.JADE -> Color(0xFF216D3B)
            AppPreferences.ThemePalette.SUNSET -> Color(0xFF9B4521)
            AppPreferences.ThemePalette.ROSE -> Color(0xFF9C405C)
            AppPreferences.ThemePalette.AMBER -> Color(0xFF795900)
            AppPreferences.ThemePalette.GRAPHITE -> Color(0xFF5F5E66)
        }
    }

    fun previewSecondaryColor(palette: AppPreferences.ThemePalette): Color {
        return when (palette) {
            AppPreferences.ThemePalette.VIOLET -> Color(0xFF7D5260)
            AppPreferences.ThemePalette.OCEAN -> Color(0xFF006B8F)
            AppPreferences.ThemePalette.JADE -> Color(0xFF4D6353)
            AppPreferences.ThemePalette.SUNSET -> Color(0xFF8A4F62)
            AppPreferences.ThemePalette.ROSE -> Color(0xFF7B5260)
            AppPreferences.ThemePalette.AMBER -> Color(0xFF735B2E)
            AppPreferences.ThemePalette.GRAPHITE -> Color(0xFF5E5E68)
        }
    }

    private val violetLight = materialLightScheme(
        primary = Color(0xFF6750A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF31111D)
    )

    private val violetDark = materialDarkScheme(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF492532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD8E4)
    )

    private val oceanLight = materialLightScheme(
        primary = Color(0xFF006A6A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF9CF1EF),
        onPrimaryContainer = Color(0xFF002020),
        secondary = Color(0xFF4A6363),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCCE8E7),
        onSecondaryContainer = Color(0xFF051F1F),
        tertiary = Color(0xFF006B8F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC5E7FF),
        onTertiaryContainer = Color(0xFF001E2E)
    )

    private val oceanDark = materialDarkScheme(
        primary = Color(0xFF80D5D3),
        onPrimary = Color(0xFF003737),
        primaryContainer = Color(0xFF00504F),
        onPrimaryContainer = Color(0xFF9CF1EF),
        secondary = Color(0xFFB0CCCB),
        onSecondary = Color(0xFF1C3535),
        secondaryContainer = Color(0xFF334B4B),
        onSecondaryContainer = Color(0xFFCCE8E7),
        tertiary = Color(0xFF83D2FF),
        onTertiary = Color(0xFF00344D),
        tertiaryContainer = Color(0xFF004C6E),
        onTertiaryContainer = Color(0xFFC5E7FF)
    )

    private val jadeLight = materialLightScheme(
        primary = Color(0xFF216D3B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA7F5B5),
        onPrimaryContainer = Color(0xFF00210A),
        secondary = Color(0xFF52634F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD5E8CF),
        onSecondaryContainer = Color(0xFF101F10),
        tertiary = Color(0xFF38656A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBCEBF0),
        onTertiaryContainer = Color(0xFF002023)
    )

    private val jadeDark = materialDarkScheme(
        primary = Color(0xFF8CD99B),
        onPrimary = Color(0xFF003916),
        primaryContainer = Color(0xFF005322),
        onPrimaryContainer = Color(0xFFA7F5B5),
        secondary = Color(0xFFB9CCB4),
        onSecondary = Color(0xFF253423),
        secondaryContainer = Color(0xFF3B4B39),
        onSecondaryContainer = Color(0xFFD5E8CF),
        tertiary = Color(0xFFA0CFD4),
        onTertiary = Color(0xFF00363B),
        tertiaryContainer = Color(0xFF1E4D52),
        onTertiaryContainer = Color(0xFFBCEBF0)
    )

    private val sunsetLight = materialLightScheme(
        primary = Color(0xFF9B4521),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCE),
        onPrimaryContainer = Color(0xFF351000),
        secondary = Color(0xFF77574C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBCE),
        onSecondaryContainer = Color(0xFF2C160F),
        tertiary = Color(0xFF705C00),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE17A),
        onTertiaryContainer = Color(0xFF221B00)
    )

    private val sunsetDark = materialDarkScheme(
        primary = Color(0xFFFFB596),
        onPrimary = Color(0xFF562000),
        primaryContainer = Color(0xFF79320B),
        onPrimaryContainer = Color(0xFFFFDBCE),
        secondary = Color(0xFFE7BDB0),
        onSecondary = Color(0xFF442A21),
        secondaryContainer = Color(0xFF5D4036),
        onSecondaryContainer = Color(0xFFFFDBCE),
        tertiary = Color(0xFFE6C449),
        onTertiary = Color(0xFF3A3000),
        tertiaryContainer = Color(0xFF554600),
        onTertiaryContainer = Color(0xFFFFE17A)
    )

    private val roseLight = materialLightScheme(
        primary = Color(0xFF9C405C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3F001B),
        secondary = Color(0xFF74565F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD9E2),
        onSecondaryContainer = Color(0xFF2B151D),
        tertiary = Color(0xFF7C5635),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDCC2),
        onTertiaryContainer = Color(0xFF2E1500)
    )

    private val roseDark = materialDarkScheme(
        primary = Color(0xFFFFB0C6),
        onPrimary = Color(0xFF5F1130),
        primaryContainer = Color(0xFF7E2945),
        onPrimaryContainer = Color(0xFFFFD9E2),
        secondary = Color(0xFFE2BDC7),
        onSecondary = Color(0xFF422932),
        secondaryContainer = Color(0xFF5A3F48),
        onSecondaryContainer = Color(0xFFFFD9E2),
        tertiary = Color(0xFFEFBD94),
        onTertiary = Color(0xFF48290D),
        tertiaryContainer = Color(0xFF623F1F),
        onTertiaryContainer = Color(0xFFFFDCC2)
    )

    private val amberLight = materialLightScheme(
        primary = Color(0xFF795900),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDEA1),
        onPrimaryContainer = Color(0xFF261900),
        secondary = Color(0xFF6C5D3F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E0BB),
        onSecondaryContainer = Color(0xFF241A04),
        tertiary = Color(0xFF4D6544),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCFEBC1),
        onTertiaryContainer = Color(0xFF0B2007)
    )

    private val amberDark = materialDarkScheme(
        primary = Color(0xFFF4BE48),
        onPrimary = Color(0xFF402D00),
        primaryContainer = Color(0xFF5C4300),
        onPrimaryContainer = Color(0xFFFFDEA1),
        secondary = Color(0xFFD8C4A0),
        onSecondary = Color(0xFF3B2F15),
        secondaryContainer = Color(0xFF53462A),
        onSecondaryContainer = Color(0xFFF5E0BB),
        tertiary = Color(0xFFB3CFA7),
        onTertiary = Color(0xFF203619),
        tertiaryContainer = Color(0xFF364D2E),
        onTertiaryContainer = Color(0xFFCFEBC1)
    )

    private val graphiteLight = materialLightScheme(
        primary = Color(0xFF5F5E66),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4E1EA),
        onPrimaryContainer = Color(0xFF1C1B20),
        secondary = Color(0xFF5F5E66),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4E1EA),
        onSecondaryContainer = Color(0xFF1C1B20),
        tertiary = Color(0xFF6B5D67),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF4DCEB),
        onTertiaryContainer = Color(0xFF251623)
    )

    private val graphiteDark = materialDarkScheme(
        primary = Color(0xFFC8C5D0),
        onPrimary = Color(0xFF303038),
        primaryContainer = Color(0xFF47464E),
        onPrimaryContainer = Color(0xFFE4E1EA),
        secondary = Color(0xFFC8C5D0),
        onSecondary = Color(0xFF303038),
        secondaryContainer = Color(0xFF47464E),
        onSecondaryContainer = Color(0xFFE4E1EA),
        tertiary = Color(0xFFD8BED0),
        onTertiary = Color(0xFF3B2A37),
        tertiaryContainer = Color(0xFF53404F),
        onTertiaryContainer = Color(0xFFF4DCEB)
    )
}

private fun materialLightScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceBrightLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = primaryContainer,
    scrim = Scrim,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight
)

private fun materialDarkScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    surfaceDim = SurfaceDimDark,
    surfaceBright = SurfaceBrightDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = primaryContainer,
    scrim = Scrim,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark
)
