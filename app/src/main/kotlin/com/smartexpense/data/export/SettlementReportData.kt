package com.smartexpense.data.export

import com.smartexpense.data.local.model.club.ClubTransactionCategorySummaryRow

data class SettlementReportData(
    val year: Int,
    val clubName: String,
    val duesTotal: Int,
    val ledgerIncome: Int,
    val ledgerExpense: Int,
    val currentBalance: Int,
    val categorySummaries: List<ClubTransactionCategorySummaryRow>
)
