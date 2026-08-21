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
            AppPreferences.ThemePalette.COSMIC -> if (darkTheme) cosmicDark else cosmicLight
            AppPreferences.ThemePalette.FLAMINGO -> if (darkTheme) flamingoDark else flamingoLight
            AppPreferences.ThemePalette.CITRUS -> if (darkTheme) citrusDark else citrusLight
        }
    }

    fun previewColor(palette: AppPreferences.ThemePalette): Color {
        return when (palette) {
            AppPreferences.ThemePalette.VIOLET -> Color(0xFF6C4FF5)
            AppPreferences.ThemePalette.OCEAN -> Color(0xFF006A6A)
            AppPreferences.ThemePalette.JADE -> Color(0xFF216D3B)
            AppPreferences.ThemePalette.SUNSET -> Color(0xFF9B4521)
            AppPreferences.ThemePalette.ROSE -> Color(0xFF9C405C)
            AppPreferences.ThemePalette.AMBER -> Color(0xFF795900)
            AppPreferences.ThemePalette.GRAPHITE -> Color(0xFF5F5E66)
            AppPreferences.ThemePalette.COSMIC -> Color(0xFF4B3DE8)
            AppPreferences.ThemePalette.FLAMINGO -> Color(0xFFB02E56)
            AppPreferences.ThemePalette.CITRUS -> Color(0xFF6D7100)
        }
    }

    fun previewSecondaryColor(palette: AppPreferences.ThemePalette): Color {
        return when (palette) {
            AppPreferences.ThemePalette.VIOLET -> Color(0xFFF06292)
            AppPreferences.ThemePalette.OCEAN -> Color(0xFF006B8F)
            AppPreferences.ThemePalette.JADE -> Color(0xFF4D6353)
            AppPreferences.ThemePalette.SUNSET -> Color(0xFF8A4F62)
            AppPreferences.ThemePalette.ROSE -> Color(0xFF7B5260)
            AppPreferences.ThemePalette.AMBER -> Color(0xFF735B2E)
            AppPreferences.ThemePalette.GRAPHITE -> Color(0xFF5E5E68)
            AppPreferences.ThemePalette.COSMIC -> Color(0xFF00A6C8)
            AppPreferences.ThemePalette.FLAMINGO -> Color(0xFFFF7FA9)
            AppPreferences.ThemePalette.CITRUS -> Color(0xFF9DA514)
        }
    }

    private val violetLight = materialLightScheme(
        primary = Color(0xFF6C4FF5),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4DDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF9A405F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD9E4),
        onSecondaryContainer = Color(0xFF3E001D),
        tertiary = Color(0xFF8C4B2F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDBCF),
        onTertiaryContainer = Color(0xFF351000)
    )

    private val violetDark = materialDarkScheme(
        primary = Color(0xFFD2C2FF),
        onPrimary = Color(0xFF3B248D),
        primaryContainer = Color(0xFF5339C7),
        onPrimaryContainer = Color(0xFFE8DDFF),
        secondary = Color(0xFFFFB0CA),
        onSecondary = Color(0xFF5E1134),
        secondaryContainer = Color(0xFF7C294B),
        onSecondaryContainer = Color(0xFFFFD9E4),
        tertiary = Color(0xFFFFB59B),
        onTertiary = Color(0xFF542100),
        tertiaryContainer = Color(0xFF70371A),
        onTertiaryContainer = Color(0xFFFFDBCF)
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

    // COSMIC — electric indigo with hot-magenta and cyan accents (M3E poster energy)
    private val cosmicLight = materialLightScheme(
        primary = Color(0xFF4B3DE8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE2DEFF),
        onPrimaryContainer = Color(0xFF160A78),
        secondary = Color(0xAA2F96),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD6F1),
        onSecondaryContainer = Color(0xFF3A0032),
        tertiary = Color(0xFF00687A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFB1ECFF),
        onTertiaryContainer = Color(0xFF001F28)
    )

    private val cosmicDark = materialDarkScheme(
        primary = Color(0xFFC3BEFF),
        onPrimary = Color(0xFF240096),
        primaryContainer = Color(0xFF3A2BC9),
        onPrimaryContainer = Color(0xFFE2DEFF),
        secondary = Color(0xFFFFACE5),
        onSecondary = Color(0xFF5D0B51),
        secondaryContainer = Color(0xFF802779),
        onSecondaryContainer = Color(0xFFFFD6F1),
        tertiary = Color(0xFF7FD0E6),
        onTertiary = Color(0xFF003543),
        tertiaryContainer = Color(0xFF004D60),
        onTertiaryContainer = Color(0xFFB1ECFF)
    )

    // FLAMINGO — raspberry pink with teal counterpoint
    private val flamingoLight = materialLightScheme(
        primary = Color(0xFFB02E56),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9DF),
        onPrimaryContainer = Color(0xFF3F0020),
        secondary = Color(0xFF74565F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDCE4),
        onSecondaryContainer = Color(0xFF2B151C),
        tertiary = Color(0xFF00696D),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF9CF1F3),
        onTertiaryContainer = Color(0xFF002022)
    )

    private val flamingoDark = materialDarkScheme(
        primary = Color(0xFFFFB1C2),
        onPrimary = Color(0xFF65002D),
        primaryContainer = Color(0xFF8C1543),
        onPrimaryContainer = Color(0xFFFFD9DF),
        secondary = Color(0xFFE3BDC6),
        onSecondary = Color(0xFF432932),
        secondaryContainer = Color(0xFF5B3F48),
        onSecondaryContainer = Color(0xFFFFDCE4),
        tertiary = Color(0xFF80D4D7),
        onTertiary = Color(0xFF003738),
        tertiaryContainer = Color(0xFF004F52),
        onTertiaryContainer = Color(0xFF9CF1F3)
    )

    // CITRUS — acid lime with deep-green contrast
    private val citrusLight = materialLightScheme(
        primary = Color(0xFF6D7100),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6E86C),
        onPrimaryContainer = Color(0xFF202200),
        secondary = Color(0xFF606043),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6E4BF),
        onSecondaryContainer = Color(0xFF1C1D05),
        tertiary = Color(0xFF256E49),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFAAF6C7),
        onTertiaryContainer = Color(0xFF002110)
    )

    private val citrusDark = materialDarkScheme(
        primary = Color(0xFFCBCB4B),
        onPrimary = Color(0xFF333800),
        primaryContainer = Color(0xFF4B5000),
        onPrimaryContainer = Color(0xFFE6E86C),
        secondary = Color(0xFFCAC8A5),
        onSecondary = Color(0xFF31321A),
        secondaryContainer = Color(0xFF48492F),
        onSecondaryContainer = Color(0xFFE6E4BF),
        tertiary = Color(0xFF8ED9AB),
        onTertiary = Color(0xFF00391F),
        tertiaryContainer = Color(0xFF085233),
        onTertiaryContainer = Color(0xFFAAF6C7)
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
