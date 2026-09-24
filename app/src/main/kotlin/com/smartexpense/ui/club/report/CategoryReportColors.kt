package com.smartexpense.ui.club.report

import androidx.compose.ui.graphics.Color
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.ui.theme.BalanceNeutral
import com.smartexpense.ui.theme.ChartExpenseGray
import com.smartexpense.ui.theme.ChartExpenseMuted
import com.smartexpense.ui.theme.ChartExpensePrimary
import com.smartexpense.ui.theme.ChartExpenseQuaternary
import com.smartexpense.ui.theme.ChartExpenseSecondary
import com.smartexpense.ui.theme.ChartExpenseTertiary
import com.smartexpense.ui.theme.ChartIncomePrimary
import com.smartexpense.ui.theme.ChartIncomeQuaternary
import com.smartexpense.ui.theme.ChartIncomeSecondary
import com.smartexpense.ui.theme.ChartIncomeTertiary
import com.smartexpense.ui.theme.InterestGold
import com.smartexpense.ui.theme.TextSecondary
import com.smartexpense.ui.theme.TransferPurple

fun categoryReportColor(category: String): Color {
    val normalized = ClubCategory.normalizeCategory(category)
    return when (normalized) {
        ClubCategory.INTEREST_INCOME -> InterestGold
        ClubCategory.REGULAR_DUES -> ChartIncomePrimary
        ClubCategory.DONATION -> ChartIncomeSecondary
        ClubCategory.OTHER_INCOME -> ChartIncomeTertiary
        ClubCategory.MEAL -> ChartExpensePrimary
        ClubCategory.VENUE_RENTAL -> ChartExpenseSecondary
        ClubCategory.EVENT -> ChartExpenseTertiary
        ClubCategory.SUPPLIES -> ChartIncomeQuaternary
        ClubCategory.CONDOLENCE -> ChartExpenseQuaternary
        ClubCategory.TRANSPORT -> ChartExpenseMuted
        ClubCategory.OTHER_EXPENSE -> ChartExpenseGray
        ClubCategory.TRANSFER -> TransferPurple
        else -> BalanceNeutral
    }
}

fun categoryShortLabel(category: String): String = ClubCategory.shortLabel(category)
