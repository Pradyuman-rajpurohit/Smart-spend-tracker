package dev.spendtracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Indigo Pulse palette. */
object SpendColors {
    val Background = Color(0xFF0E1130)
    val Surface = Color(0xFF171B45)
    val SurfaceHigh = Color(0xFF1E2352)
    val Border = Color(0xFF2A3068)
    val Divider = Color(0xFF1E2352)
    val Track = Color(0xFF262B5C)
    val Text = Color(0xFFEEF0FF)
    val Muted = Color(0xFFA4A9D9)
    val Inactive = Color(0xFF6C71A8)
    val Violet = Color(0xFF8F82FF)
    val VioletDeep = Color(0xFF4B3FA6)
    val VioletTint = Color(0xFF2A2160)
    val VioletText = Color(0xFFC4BCFF)
    val Cyan = Color(0xFF56D3E6)
    val CyanTint = Color(0xFF123A45)
    val Rose = Color(0xFFFF7A9A)
    val RoseTint = Color(0xFF4A1F33)
}

private val colorScheme = darkColorScheme(
    primary = SpendColors.Violet,
    onPrimary = SpendColors.Background,
    primaryContainer = SpendColors.VioletTint,
    onPrimaryContainer = SpendColors.VioletText,
    secondary = SpendColors.Cyan,
    onSecondary = SpendColors.Background,
    secondaryContainer = SpendColors.CyanTint,
    onSecondaryContainer = SpendColors.Cyan,
    tertiary = SpendColors.Rose,
    onTertiary = SpendColors.Background,
    tertiaryContainer = SpendColors.RoseTint,
    onTertiaryContainer = SpendColors.Rose,
    background = SpendColors.Background,
    onBackground = SpendColors.Text,
    surface = SpendColors.Background,
    onSurface = SpendColors.Text,
    surfaceVariant = SpendColors.Surface,
    onSurfaceVariant = SpendColors.Muted,
    surfaceContainer = SpendColors.Surface,
    surfaceContainerLow = SpendColors.Surface,
    surfaceContainerLowest = SpendColors.Background,
    surfaceContainerHigh = SpendColors.SurfaceHigh,
    surfaceContainerHighest = SpendColors.SurfaceHigh,
    outline = SpendColors.Border,
    outlineVariant = SpendColors.Divider,
    error = SpendColors.Rose,
    onError = SpendColors.Background,
    errorContainer = SpendColors.RoseTint,
    onErrorContainer = SpendColors.Rose,
)

private val typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SpendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content
    )
}
