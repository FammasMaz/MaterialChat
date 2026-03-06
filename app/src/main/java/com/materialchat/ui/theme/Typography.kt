package com.materialchat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.materialchat.R

/**
 * Material 3 Expressive Typography for MaterialChat.
 *
 * Supports 10 readable Google Fonts with dynamic font size scaling
 * for chat bubble content only. UI chrome uses the default Roboto Flex.
 */

/**
 * CompositionLocal for the user's chosen chat font size scale (0.85 - 1.4).
 * Only affects chat message bubble content, not app UI.
 */
val LocalChatFontSizeScale = compositionLocalOf { 1.0f }

/**
 * Google Fonts provider configuration.
 */
val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * Available font families for user selection.
 * All are highly readable sans-serif fonts suitable for M3 body/label text.
 */
data class AppFont(
    val name: String,
    val googleFontName: String
)

val availableFonts = listOf(
    AppFont("Roboto Flex", "Roboto Flex"),
    AppFont("Inter", "Inter"),
    AppFont("Noto Sans", "Noto Sans"),
    AppFont("Open Sans", "Open Sans"),
    AppFont("Lato", "Lato"),
    AppFont("Source Sans 3", "Source Sans 3"),
    AppFont("Work Sans", "Work Sans"),
    AppFont("DM Sans", "DM Sans"),
    AppFont("Nunito", "Nunito"),
    AppFont("Montserrat", "Montserrat")
)

/**
 * Creates a FontFamily from a Google Font name with all required weights.
 */
fun createFontFamily(googleFontName: String): FontFamily {
    val googleFont = GoogleFont(googleFontName)
    return FontFamily(
        Font(googleFont = googleFont, fontProvider = fontProvider, weight = FontWeight.Light),
        Font(googleFont = googleFont, fontProvider = fontProvider, weight = FontWeight.Normal),
        Font(googleFont = googleFont, fontProvider = fontProvider, weight = FontWeight.Medium),
        Font(googleFont = googleFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = googleFont, fontProvider = fontProvider, weight = FontWeight.Bold)
    )
}

/**
 * Default Roboto Flex font family (used as fallback and default).
 */
val RobotoFlexFontFamily = createFontFamily("Roboto Flex")

/**
 * Fallback to system default if Google Fonts fails to load.
 */
val DefaultFontFamily = FontFamily.Default

/**
 * Builds a Material 3 Typography with the specified font family and size scale.
 *
 * @param fontFamily The FontFamily to use for all text styles
 * @param sizeScale Multiplier for font sizes (1.0 = default M3 sizes)
 */
fun buildTypography(
    fontFamily: FontFamily = RobotoFlexFontFamily,
    sizeScale: Float = 1.0f
): Typography = Typography(
    // Display - Extra large, impactful text
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (57 * sizeScale).sp,
        lineHeight = (64 * sizeScale).sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (45 * sizeScale).sp,
        lineHeight = (52 * sizeScale).sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (36 * sizeScale).sp,
        lineHeight = (44 * sizeScale).sp,
        letterSpacing = 0.sp
    ),

    // Headline - Section headers, app bar titles
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (32 * sizeScale).sp,
        lineHeight = (40 * sizeScale).sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (28 * sizeScale).sp,
        lineHeight = (36 * sizeScale).sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (24 * sizeScale).sp,
        lineHeight = (32 * sizeScale).sp,
        letterSpacing = 0.sp
    ),

    // Title - Card titles, dialog titles, top bar
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (22 * sizeScale).sp,
        lineHeight = (28 * sizeScale).sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (16 * sizeScale).sp,
        lineHeight = (24 * sizeScale).sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (14 * sizeScale).sp,
        lineHeight = (20 * sizeScale).sp,
        letterSpacing = 0.1.sp
    ),

    // Body - Chat messages, paragraphs, descriptions
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (16 * sizeScale).sp,
        lineHeight = (24 * sizeScale).sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (14 * sizeScale).sp,
        lineHeight = (20 * sizeScale).sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (12 * sizeScale).sp,
        lineHeight = (16 * sizeScale).sp,
        letterSpacing = 0.4.sp
    ),

    // Label - Buttons, chips, tabs, captions
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (14 * sizeScale).sp,
        lineHeight = (20 * sizeScale).sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (12 * sizeScale).sp,
        lineHeight = (16 * sizeScale).sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (11 * sizeScale).sp,
        lineHeight = (16 * sizeScale).sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Default Material 3 Expressive Typography using Roboto Flex at standard size.
 */
val MaterialChatTypography = buildTypography()

/**
 * Typography extension for code blocks in chat messages.
 */
val CodeTypography = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

/**
 * Typography for inline code spans.
 */
val InlineCodeTypography = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

/**
 * Typography for message timestamps.
 */
val TimestampTypography = TextStyle(
    fontFamily = RobotoFlexFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.3.sp
)
