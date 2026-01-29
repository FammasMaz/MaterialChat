package com.materialchat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialchat.data.local.preferences.AppPreferences

/**
 * Light color scheme for MaterialChat.
 * Used when dynamic colors are disabled or on Android 11 and below.
 */
private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
    scrim = Scrim,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight
)

/**
 * Dark color scheme for MaterialChat.
 * Used when dynamic colors are disabled or on Android 11 and below.
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
    scrim = Scrim,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark
)

/**
 * Checks if dynamic color is supported on the current device.
 * Dynamic colors are available on Android 12 (API 31) and above.
 */
fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Main theme composable for MaterialChat.
 *
 * This theme provides:
 * - Material 3 Expressive color schemes
 * - Dynamic color support for Android 12+
 * - Light/Dark mode based on user preference or system setting
 * - Expressive typography and shapes
 * - Spring-based motion system
 *
 * @param themeMode The theme mode preference (SYSTEM, LIGHT, or DARK)
 * @param dynamicColor Whether to use dynamic colors derived from wallpaper (Android 12+)
 * @param content The composable content to apply the theme to
 */
@Composable
fun MaterialChatTheme(
    themeMode: AppPreferences.ThemeMode = AppPreferences.ThemeMode.SYSTEM,
    dynamicColor: Boolean = isDynamicColorSupported(),
    content: @Composable () -> Unit
) {
    // Determine if we should use dark theme
    val darkTheme = when (themeMode) {
        AppPreferences.ThemeMode.LIGHT -> false
        AppPreferences.ThemeMode.DARK -> true
        AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Select the appropriate color scheme
    val colorScheme = selectColorScheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor
    )

    // Update status bar and navigation bar colors
    // Only apply to Activity contexts (not WindowContext from VoiceInteractionSession)
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        if (activity != null) {
            SideEffect {
                val window = activity.window
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()

                // Set light/dark appearance for status bar icons
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialChatTypography,
        shapes = MaterialChatShapes,
        content = content
    )
}

/**
 * Selects the appropriate color scheme based on theme and dynamic color settings.
 *
 * @param darkTheme Whether to use dark theme colors
 * @param dynamicColor Whether to use dynamic colors (if supported)
 * @return The selected ColorScheme
 */
@Composable
private fun selectColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme {
    val context = LocalContext.current

    return when {
        // Use dynamic colors on Android 12+ if enabled
        dynamicColor && isDynamicColorSupported() -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        // Use static color schemes
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
}


/**
 * Object containing helper functions for theme-related operations.
 */
object MaterialChatThemeUtils {

    /**
     * Returns whether the current theme is dark.
     */
    @Composable
    fun isDarkTheme(themeMode: AppPreferences.ThemeMode): Boolean {
        return when (themeMode) {
            AppPreferences.ThemeMode.LIGHT -> false
            AppPreferences.ThemeMode.DARK -> true
            AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    }
}
