package com.materialchat.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 Expressive color palette for MaterialChat.
 *
 * These colors are used as fallback when dynamic colors are disabled
 * or on devices running Android 11 and below.
 *
 * The palette follows Material 3 color system with primary, secondary,
 * tertiary, and surface colors in both light and dark variants.
 */

// Primary colors - A vibrant teal/cyan for the main brand identity
val PrimaryLight = Color(0xFF006879)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFA8EDFF)
val OnPrimaryContainerLight = Color(0xFF001F26)

val PrimaryDark = Color(0xFF53D7F1)
val OnPrimaryDark = Color(0xFF00363F)
val PrimaryContainerDark = Color(0xFF004E5B)
val OnPrimaryContainerDark = Color(0xFFA8EDFF)

// Secondary colors - A warm coral/orange for accents
val SecondaryLight = Color(0xFF8B5000)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFFFDCBE)
val OnSecondaryContainerLight = Color(0xFF2D1600)

val SecondaryDark = Color(0xFFFFB86F)
val OnSecondaryDark = Color(0xFF4A2800)
val SecondaryContainerDark = Color(0xFF693C00)
val OnSecondaryContainerDark = Color(0xFFFFDCBE)

// Tertiary colors - A soft purple for additional accents
val TertiaryLight = Color(0xFF6B5778)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFF3DAFF)
val OnTertiaryContainerLight = Color(0xFF251431)

val TertiaryDark = Color(0xFFD6BEE4)
val OnTertiaryDark = Color(0xFF3B2948)
val TertiaryContainerDark = Color(0xFF533F5F)
val OnTertiaryContainerDark = Color(0xFFF3DAFF)

// Error colors
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Surface colors - Light theme
val BackgroundLight = Color(0xFFF5FAFB)
val OnBackgroundLight = Color(0xFF171D1E)
val SurfaceLight = Color(0xFFF5FAFB)
val OnSurfaceLight = Color(0xFF171D1E)
val SurfaceVariantLight = Color(0xFFDBE4E6)
val OnSurfaceVariantLight = Color(0xFF3F484A)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFEFF4F5)
val SurfaceContainerLight = Color(0xFFE9EEF0)
val SurfaceContainerHighLight = Color(0xFFE3E9EA)
val SurfaceContainerHighestLight = Color(0xFFDEE3E5)

// Surface colors - Dark theme
val BackgroundDark = Color(0xFF0F1416)
val OnBackgroundDark = Color(0xFFDEE3E5)
val SurfaceDark = Color(0xFF0F1416)
val OnSurfaceDark = Color(0xFFDEE3E5)
val SurfaceVariantDark = Color(0xFF3F484A)
val OnSurfaceVariantDark = Color(0xFFBFC8CA)
val SurfaceContainerLowestDark = Color(0xFF0A0F11)
val SurfaceContainerLowDark = Color(0xFF171D1E)
val SurfaceContainerDark = Color(0xFF1B2122)
val SurfaceContainerHighDark = Color(0xFF262B2D)
val SurfaceContainerHighestDark = Color(0xFF303638)

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

// Additional semantic colors for chat UI
val UserMessageBubbleLight = Color(0xFFA8EDFF) // Primary container
val UserMessageBubbleDark = Color(0xFF004E5B) // Primary container dark
val AssistantMessageBubbleLight = Color(0xFFDBE4E6) // Surface variant
val AssistantMessageBubbleDark = Color(0xFF3F484A) // Surface variant dark
val SystemMessageBubbleLight = Color(0xFFF3DAFF) // Tertiary container
val SystemMessageBubbleDark = Color(0xFF533F5F) // Tertiary container dark

// Code block colors
val CodeBlockBackgroundLight = Color(0xFFF0F0F0)
val CodeBlockBackgroundDark = Color(0xFF1E1E1E)
