package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JioPanalDarkColorScheme = darkColorScheme(
    primary = RoyalGold,
    onPrimary = Color(0xFF140F02),
    primaryContainer = RoyalGoldContainer,
    onPrimaryContainer = RoyalGoldBright,
    secondary = EmeraldCredit,
    onSecondary = Color(0xFF031F16),
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = Color(0xFF6EE7B7),
    tertiary = AmberPending,
    onTertiary = Color(0xFF1F1402),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFFFCD34D),
    background = ObsidianBg,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = MutedSlate,
    outline = ObsidianOutline,
    error = RubyAlert,
    onError = Color.White,
    errorContainer = RubyContainer,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val JioPanalLightColorScheme = lightColorScheme(
    primary = GoldPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = GoldContainerLight,
    onPrimaryContainer = Color(0xFF3E2B02),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = PearlBg,
    onBackground = InkPrimary,
    surface = PearlSurface,
    onSurface = InkPrimary,
    surfaceVariant = PearlSurfaceHigh,
    onSurfaceVariant = SlateSecondary,
    outline = PearlOutline,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val JioPanalShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JioPanalTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JioPanalDarkColorScheme else JioPanalLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JioPanalTypography,
        shapes = JioPanalShapes,
        content = content
    )
}
