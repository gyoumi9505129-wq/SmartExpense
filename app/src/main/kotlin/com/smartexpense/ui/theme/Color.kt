package com.smartexpense.ui.theme

import androidx.compose.ui.graphics.Color

// Deep Dark Mode — premium muted palette
val BackgroundBlack = Color(0xFF121212)
val SurfaceDeepGray = Color(0xFF1E1E1E)
val SurfaceElevated = Color(0xFF252525)

val TextPrimary = Color.White
val TextSecondary = Color(0xFFA0A0A0)

/** 수입 · 완납 — 부드러운 민트/틸 */
val IncomeBlue = Color(0xFF6DB5A8)

/** 지출 · 미납 — 차분한 로즈 */
val ExpenseRed = Color(0xFFB88A9A)

/** 이자 · 미납 강조 — 부드러운 앰버 */
val InterestGold = Color(0xFFD4AA6A)

/** 계좌 이체 — 차분한 라벤더 */
val TransferPurple = Color(0xFF9B8EC4)

/** 잔액 중립 — 소프트 그레이 */
val BalanceNeutral = Color(0xFF9AA3AB)

val BorderLine = Color(0xFF333333)
val DividerSubtle = Color(0x1AFFFFFF)

/** 결산 차트 — 수입(서로 다른 색상으로 구분) */
val ChartIncomePrimary = Color(0xFF4DB6A0)      // 정기 회비 — 민트
val ChartIncomeSecondary = Color(0xFF5BA3D9)    // 찬조/기부 — 스카이
val ChartIncomeTertiary = Color(0xFF7EC8E3)     // 기타 수입 — 라이트 시안
val ChartIncomeQuaternary = Color(0xFF8BC34A)   // 비품 등 — 라임

/** 결산 차트 — 지출(색상환 분산, 다크 배경에서도 구분) */
val ChartExpensePrimary = Color(0xFFE57373)     // 식대 — 코랄 레드
val ChartExpenseSecondary = Color(0xFFFFB74D)   // 장소 대관 — 앰버
val ChartExpenseTertiary = Color(0xFFBA68C8)    // 행사 — 퍼플
val ChartExpenseQuaternary = Color(0xFF64B5F6)  // 경조사 — 블루
val ChartExpenseMuted = Color(0xFF4DD0E1)       // 교통/통신 — 시안
val ChartExpenseGray = Color(0xFFAED581)       // 기타 지출 — 연두 (회색 대신)

/** @deprecated Use [IncomeBlue] — kept for call-site compatibility */
val IncomeGreen = IncomeBlue

/** Muted accent for uncategorized labels */
val UncategorizedMuted = TextSecondary

// Legacy aliases (other screens)
val TealPrimary = TextPrimary
val TealLight = TextSecondary
val TealDark = BackgroundBlack
val MintAccent = IncomeBlue
val UncategorizedOrange = UncategorizedMuted
val SurfaceLight = BackgroundBlack
val SurfaceDark = BackgroundBlack
