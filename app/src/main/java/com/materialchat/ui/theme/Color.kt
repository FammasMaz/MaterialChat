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

// Primary colors - M3 default purple (neutral, works well as fallback)
val PrimaryLight = Color(0xFF6750A4)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE8DEF8)
val OnPrimaryContainerLight = Color(0xFF21005D)

val PrimaryDark = Color(0xFFD0BCFF)
val OnPrimaryDark = Color(0xFF381E72)
val PrimaryContainerDark = Color(0xFF4F378B)
val OnPrimaryContainerDark = Color(0xFFE8DEF8)

// Secondary colors - Warm neutral for accents
val SecondaryLight = Color(0xFF625B71)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE8DEF8)
val OnSecondaryContainerLight = Color(0xFF1D192B)

val SecondaryDark = Color(0xFFCCC2DC)
val OnSecondaryDark = Color(0xFF332D41)
val SecondaryContainerDark = Color(0xFF4A4458)
val OnSecondaryContainerDark = Color(0xFFE8DEF8)

// Tertiary colors - Warm terracotta/coral for variety
val TertiaryLight = Color(0xFF7D5260)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFD8E4)
val OnTertiaryContainerLight = Color(0xFF31111D)

val TertiaryDark = Color(0xFFEFB8C8)
val OnTertiaryDark = Color(0xFF492532)
val TertiaryContainerDark = Color(0xFF633B48)
val OnTertiaryContainerDark = Color(0xFFFFD8E4)

// Error colors
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Surface colors - Light theme (neutral warm tones)
val BackgroundLight = Color(0xFFFFFBFE)
val OnBackgroundLight = Color(0xFF1C1B1F)
val SurfaceLight = Color(0xFFFFFBFE)
val OnSurfaceLight = Color(0xFF1C1B1F)
val SurfaceVariantLight = Color(0xFFE7E0EC)
val OnSurfaceVariantLight = Color(0xFF49454F)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF7F2FA)
val SurfaceContainerLight = Color(0xFFF3EDF7)
val SurfaceContainerHighLight = Color(0xFFECE6F0)
val SurfaceContainerHighestLight = Color(0xFFE6E0E9)

// Surface colors - Dark theme
val BackgroundDark = Color(0xFF1C1B1F)
val OnBackgroundDark = Color(0xFFE6E1E5)
val SurfaceDark = Color(0xFF1C1B1F)
val OnSurfaceDark = Color(0xFFE6E1E5)
val SurfaceVariantDark = Color(0xFF49454F)
val OnSurfaceVariantDark = Color(0xFFCAC4D0)
val SurfaceContainerLowestDark = Color(0xFF0F0D13)
val SurfaceContainerLowDark = Color(0xFF1D1B20)
val SurfaceContainerDark = Color(0xFF211F26)
val SurfaceContainerHighDark = Color(0xFF2B2930)
val SurfaceContainerHighestDark = Color(0xFF36343B)

// Outline colors
val OutlineLight = Color(0xFF6F797A)
val OutlineVariantLight = Color(0xFFBFC8CA)

val OutlineDark = Color(0xFF899294)
val OutlineVariantDark = Color(0xFF3F484A)

// Inverse colors
val InverseSurfaceLight = Color(0xFF2C3133)
val InverseOnSurfaceLight = Color(0xFFECF2F3)
val InversePrimaryLight = Color(0xFF53D7F1)

val InverseSurfaceDark = Color(0xFFDEE3E5)
val InverseOnSurfaceDark = Color(0xFF2C3133)
val InversePrimaryDark = Color(0xFF006879)

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
