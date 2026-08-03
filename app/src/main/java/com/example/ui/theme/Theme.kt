package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

private val PDFCraftDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.Black,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = EmeraldPrimaryLight,
    onSecondary = Color.Black,
    secondaryContainer = CharcoalHighlight,
    onSecondaryContainer = TextPrimary,
    tertiary = SkyAccent,
    onTertiary = Color.Black,
    tertiaryContainer = SkyContainer,
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = CharcoalBackground,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = TextSecondary,
    outline = CharcoalCardBorder,
    outlineVariant = CharcoalCardBorder,
    error = RedDelete,
    onError = Color.White
)

private val PDFCraftLightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),            // Vibrant Emerald 600
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),   // Soft Emerald 100
    onPrimaryContainer = Color(0xFF065F46), // Dark Emerald 800
    secondary = Color(0xFF0284C7),          // Sky Blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9), // Light Slate 100 highlight
    onSecondaryContainer = Color(0xFF0F172A), // Slate 900
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF075985),
    background = Color(0xFFF8FAFC),         // Slate 50 clean background
    onBackground = Color(0xFF0F172A),       // Slate 900 text
    surface = Color(0xFFFFFFFF),            // White header / surface
    onSurface = Color(0xFF0F172A),          // Slate 900 text on surface
    surfaceVariant = Color(0xFFFFFFFF),     // Pure White Cards on Slate 50
    onSurfaceVariant = Color(0xFF64748B),   // Slate 500 secondary text
    outline = Color(0xFFE2E8F0),            // Slate 200 outline
    outlineVariant = Color(0xFFCBD5E1),     // Slate 300 outline
    error = RedDelete,
    onError = Color.White
)

@Composable
fun PDFCraftTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = true, // Legacy parameter fallback
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colors = if (isDark) PDFCraftDarkColorScheme else PDFCraftLightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PDFCraftTheme(themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT, content = content)
}
