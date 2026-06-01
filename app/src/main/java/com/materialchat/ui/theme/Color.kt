package com.materialchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material 3 Expressive color palette for MaterialChat.
 *
 * These colors are used as fallback when dynamic colors are disabled
 * or on devices running Android 11 and below.
 *
 * The palette follows Material 3 Expressive color system with warm,
 * neutral tones that complement dynamic color when enabled.
 * 
 * IMPORTANT: Dynamic color from the system wallpaper takes precedence.
 * These fallback colors should be neutral/warm to provide a good
 * experience when dynamic color is not available.
 */

// Primary colors - PixelPlayer-inspired electric violet fallback
val PrimaryLight = Color(0xFF6C4FF5)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE4DDFF)
val OnPrimaryContainerLight = Color(0xFF21005D)

val PrimaryDark = Color(0xFFD2C2FF)
val OnPrimaryDark = Color(0xFF3B248D)
val PrimaryContainerDark = Color(0xFF5339C7)
val OnPrimaryContainerDark = Color(0xFFE8DDFF)

// Secondary colors - PixelPlayer pink for expressive accents and nav indicators
val SecondaryLight = Color(0xFF9A405F)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFFFD9E4)
val OnSecondaryContainerLight = Color(0xFF3E001D)

val SecondaryDark = Color(0xFFFFB0CA)
val OnSecondaryDark = Color(0xFF5E1134)
val SecondaryContainerDark = Color(0xFF7C294B)
val OnSecondaryContainerDark = Color(0xFFFFD9E4)

// Tertiary colors - PixelPlayer coral/orange for hero and temporary-chat moments
val TertiaryLight = Color(0xFF8C4B2F)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDBCF)
val OnTertiaryContainerLight = Color(0xFF351000)

val TertiaryDark = Color(0xFFFFB59B)
val OnTertiaryDark = Color(0xFF542100)
val TertiaryContainerDark = Color(0xFF70371A)
val OnTertiaryContainerDark = Color(0xFFFFDBCF)

// Error colors
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Surface colors - Light theme (PixelPlayer lavender paper)
val BackgroundLight = Color(0xFFF8F2FF)
val OnBackgroundLight = Color(0xFF1E1237)
val SurfaceLight = Color(0xFFFCF8FF)
val SurfaceDimLight = Color(0xFFE0D7EA)
val SurfaceBrightLight = Color(0xFFFFFBFF)
val OnSurfaceLight = Color(0xFF1E1237)
val SurfaceVariantLight = Color(0xFFE8DEF9)
val OnSurfaceVariantLight = Color(0xFF4D4165)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF7F0FF)
val SurfaceContainerLight = Color(0xFFF1E9FB)
val SurfaceContainerHighLight = Color(0xFFEAE1F5)
val SurfaceContainerHighestLight = Color(0xFFE3DAEF)

// Surface colors - Dark theme (PixelPlayer deep purple stage)
val BackgroundDark = Color(0xFF15101D)
val OnBackgroundDark = Color(0xFFEDE5F4)
val SurfaceDark = Color(0xFF15101D)
val SurfaceDimDark = Color(0xFF15101D)
val SurfaceBrightDark = Color(0xFF3D3447)
val OnSurfaceDark = Color(0xFFEDE5F4)
val SurfaceVariantDark = Color(0xFF51465E)
val OnSurfaceVariantDark = Color(0xFFD3C6DE)
val SurfaceContainerLowestDark = Color(0xFF0F0A16)
val SurfaceContainerLowDark = Color(0xFF1E1728)
val SurfaceContainerDark = Color(0xFF251D31)
val SurfaceContainerHighDark = Color(0xFF30263D)
val SurfaceContainerHighestDark = Color(0xFF3B3148)

// Outline colors
val OutlineLight = Color(0xFF7F7192)
val OutlineVariantLight = Color(0xFFCFC2DE)

val OutlineDark = Color(0xFF9D91AA)
val OutlineVariantDark = Color(0xFF51465E)

// Inverse colors
val InverseSurfaceLight = Color(0xFF332C3A)
val InverseOnSurfaceLight = Color(0xFFF7EFFA)
val InversePrimaryLight = Color(0xFFD2C2FF)

val InverseSurfaceDark = Color(0xFFEDE5F4)
val InverseOnSurfaceDark = Color(0xFF332C3A)
val InversePrimaryDark = Color(0xFF6C4FF5)

// Scrim
val Scrim = Color(0xFF000000)

/**
 * Semantic colors for chat UI that derive from MaterialTheme.colorScheme.
 * 
 * IMPORTANT: These functions must be called from a @Composable context
 * to properly derive colors from the current theme (including dynamic colors).
 * 
 * This ensures chat bubbles adapt to the system wallpaper just like
 * Google Messages and other M3 Expressive apps.
 */
object ChatColors {
    /**
     * User message bubble background - uses primaryContainer for brand identity.
     * Adapts to dynamic color from wallpaper.
     */
    val userBubbleColor: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer
    
    /**
     * User message text color - ensures proper contrast on primaryContainer.
     */
    val userBubbleTextColor: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
    
    /**
     * Assistant/AI message bubble background - uses surfaceContainerHigh for subtle distinction.
     * Adapts to dynamic color from wallpaper.
     */
    val assistantBubbleColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    
    /**
     * Assistant message text color - ensures proper contrast on surface.
     */
    val assistantBubbleTextColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface
    
    /**
     * System message bubble background - uses tertiaryContainer for special messages.
     */
    val systemBubbleColor: Color
        @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    
    /**
     * System message text color.
     */
    val systemBubbleTextColor: Color
        @Composable get() = MaterialTheme.colorScheme.onTertiaryContainer
}

// Code block colors - derive from M3 surface tokens for consistency
// Light: surfaceContainerLow, Dark: surfaceContainerHighest equivalent
val CodeBlockBackgroundLight = SurfaceContainerLowLight
val CodeBlockBackgroundDark = SurfaceContainerHighestDark
