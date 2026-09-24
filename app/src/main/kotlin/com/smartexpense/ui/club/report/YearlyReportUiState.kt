package com.smartexpense.ui.club.report

data class CategorySummaryUi(
    val category: String,
    val income: Int,
    val expense: Int,
    val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
)

data class TransferSummaryUi(
    val id: Long,
    val date: String,
    val amount: Int,
    val fromAccountLabel: String,
    val toAccountLabel: String,
    val note: String?
)

data class CategoryDetailItemUi(
    val id: Long,
    val date: String,
    val note: String?,
    val amount: Int,
    val isIncome: Boolean
)

data class YearlyReportUiState(
    val selectedYear: Int = java.time.LocalDate.now().year,
    val yearLabel: String = "",
    val canGoNextYear: Boolean = false,
    val duesTotal: Int = 0,
    val ledgerIncome: Int = 0,
    val ledgerExpense: Int = 0,
    val cumulativeBalance: Int = 0,
    val categorySummaries: List<CategorySummaryUi> = emptyList(),
    val incomePieSlices: List<PieSliceUi> = emptyList(),
    val expensePieSlices: List<PieSliceUi> = emptyList(),
    val transferSummaries: List<TransferSummaryUi> = emptyList(),
    val showDetailSheet: Boolean = false,
    val selectedCategory: String? = null,
    val categoryDetailItems: List<CategoryDetailItemUi> = emptyList()
) {
    val isEmptyYear: Boolean =
        duesTotal == 0 && ledgerIncome == 0 && ledgerExpense == 0 && categorySummaries.isEmpty()
}
