package com.smartexpense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumDarkColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = BackgroundBlack,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = TextPrimary,
    secondaryContainer = SurfaceDeepGray,
    onSecondaryContainer = TextSecondary,
    tertiary = IncomeBlue,
    onTertiary = TextPrimary,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = BackgroundBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDeepGray,
    onSurfaceVariant = TextSecondary,
    outline = BorderLine,
    outlineVariant = DividerSubtle,
    error = ExpenseRed,
    onError = TextPrimary
)

@Composable
fun SmartExpenseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PremiumDarkColorScheme,
        typography = Typography,
        shapes = SmartExpenseShapes,
        content = content
    )
}
