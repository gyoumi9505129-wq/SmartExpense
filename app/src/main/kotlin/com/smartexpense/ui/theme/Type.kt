package com.smartexpense.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.smartexpense.R

/**
 * Pretendard (.otf) — res/font/
 */
val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold)
)

private val TightAmount = (-1.2).sp
private val TightDisplay = (-1.8).sp

private fun pretendardStyle(
    weight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
    letterSpacing: TextUnit = 0.sp
) = TextStyle(
    fontFamily = PretendardFontFamily,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing
)

/** 가독성 향상 — 전체 스케일 한 단계 상향 */
val Typography = Typography(
    displayLarge = pretendardStyle(
        weight = FontWeight.ExtraBold,
        fontSize = 42,
        lineHeight = 46,
        letterSpacing = TightDisplay
    ),
    displayMedium = pretendardStyle(
        weight = FontWeight.ExtraBold,
        fontSize = 34,
        lineHeight = 38,
        letterSpacing = TightDisplay
    ),
    displaySmall = pretendardStyle(
        weight = FontWeight.Bold,
        fontSize = 30,
        lineHeight = 34,
        letterSpacing = TightAmount
    ),
    headlineLarge = pretendardStyle(
        weight = FontWeight.Bold,
        fontSize = 26,
        lineHeight = 30,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = pretendardStyle(
        weight = FontWeight.Bold,
        fontSize = 22,
        lineHeight = 28,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = pretendardStyle(
        weight = FontWeight.Medium,
        fontSize = 20,
        lineHeight = 26
    ),
    titleLarge = pretendardStyle(
        weight = FontWeight.Bold,
        fontSize = 20,
        lineHeight = 26
    ),
    titleMedium = pretendardStyle(
        weight = FontWeight.Medium,
        fontSize = 18,
        lineHeight = 24
    ),
    titleSmall = pretendardStyle(
        weight = FontWeight.Medium,
        fontSize = 16,
        lineHeight = 22
    ),
    bodyLarge = pretendardStyle(
        weight = FontWeight.Normal,
        fontSize = 18,
        lineHeight = 26
    ),
    bodyMedium = pretendardStyle(
        weight = FontWeight.Normal,
        fontSize = 16,
        lineHeight = 24
    ),
    bodySmall = pretendardStyle(
        weight = FontWeight.Normal,
        fontSize = 14,
        lineHeight = 20
    ),
    labelLarge = pretendardStyle(
        weight = FontWeight.Medium,
        fontSize = 15,
        lineHeight = 20,
        letterSpacing = 0.4.sp
    ),
    labelMedium = pretendardStyle(
        weight = FontWeight.Medium,
        fontSize = 13,
        lineHeight = 18,
        letterSpacing = 0.5.sp
    ),
    labelSmall = pretendardStyle(
        weight = FontWeight.Medium,
        fontSize = 12,
        lineHeight = 16,
        letterSpacing = 0.6.sp
    )
)

/** Large balance / payment amounts */
val AmountDisplayStyle: TextStyle
    get() = Typography.displayLarge

val AmountListStyle: TextStyle
    get() = Typography.titleLarge.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = TightAmount
    )
