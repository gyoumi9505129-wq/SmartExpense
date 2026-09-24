package com.smartexpense.ui.club.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.entity.club.ClubAccountEntity
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.model.club.ClubTransactionCategorySummaryRow
import com.smartexpense.data.local.model.club.YearlyClubTransactionSummaryRow
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.toDisplayLabel
import com.smartexpense.data.repository.club.ClubAccountRepository
import com.smartexpense.data.repository.club.ClubTransactionRepository
import com.smartexpense.data.repository.club.DuesRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import com.smartexpense.ui.club.transaction.toShortDateLabel
import com.smartexpense.ui.common.observeSelectedClubChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YearlyReportViewModel @Inject constructor(
    private val clubTransactionRepository: ClubTransactionRepository,
    private val clubAccountRepository: ClubAccountRepository,
    private val duesRepository: DuesRepository,
    private val selectedClubRepository: SelectedClubRepository,
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard
) : ViewModel() {

    private val refreshTrigger = ViewModelRefreshTrigger()
    private val selectedYear = MutableStateFlow(LocalDate.now().year)
    private val showDetailSheet = MutableStateFlow(false)
    private val selectedCategory = MutableStateFlow<String?>(null)

    init {
        observeSelectedClubChanges(selectedClubRepository) {
            showDetailSheet.value = false
            selectedCategory.value = null
        }
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshTrigger.refresh()
    }

    val uiState: StateFlow<YearlyReportUiState> = refreshTrigger.tick.flatMapLatest {
        combine(
            selectedYear,
            showDetailSheet,
            selectedCategory
        ) { year, showSheet, category ->
            Triple(year, showSheet, category)
        }.flatMapLatest { (year, showSheet, category) ->
        val endDate = yearEndDate(year)
        val baseState = combine(
            combine(
                clubTransactionRepository.observeYearlySummary(year),
                clubTransactionRepository.observeCategorySummaryByYear(year),
                clubTransactionRepository.observeTransfersByYear(year),
                clubAccountRepository.observeAll(),
                duesRepository.observeYearTotal(year)
            ) { ledgerSummary, categories, transfers, accounts, duesTotal ->
                YearlyLedgerSnapshot(
                    ledgerSummary = ledgerSummary,
                    categories = categories,
                    transfers = transfers,
                    accounts = accounts,
                    duesTotal = duesTotal
                )
            },
            combine(
                duesRepository.observeCumulativePaidUpTo(endDate, year),
                clubTransactionRepository.observeCumulativeIncomeUpTo(endDate),
                clubTransactionRepository.observeCumulativeExpenseUpTo(endDate),
                clubTransactionRepository.observeLatestBalanceUpTo(endDate)
            ) { cumulativeDues, cumulativeIncome, cumulativeExpense, ledgerBalance ->
                CumulativeBalanceSnapshot(
                    cumulativeDues = cumulativeDues,
                    cumulativeLedgerIncome = cumulativeIncome,
                    cumulativeLedgerExpense = cumulativeExpense,
                    ledgerBalanceAtYearEnd = ledgerBalance
                )
            }
        ) { ledgerSnapshot, cumulative ->
            val income = ledgerSnapshot.ledgerSummary?.totalIncome ?: 0
            val expense = ledgerSnapshot.ledgerSummary?.totalExpense ?: 0
            val currentYear = LocalDate.now().year
            val accountLabels = ledgerSnapshot.accounts.associate { it.id to it.toDisplayLabel() }
            val categoryUi = ledgerSnapshot.categories.map { row ->
                CategorySummaryUi(
                    category = row.category,
                    income = row.totalIncome,
                    expense = row.totalExpense,
                    color = categoryReportColor(row.category)
                )
            }
            val incomePieSlices = categoryUi
                .filter { it.income > 0 }
                .map {
                    PieSliceUi(
                        label = categoryShortLabel(it.category),
                        amount = it.income,
                        color = it.color
                    )
                }
            val expensePieSlices = categoryUi
                .filter { it.expense > 0 }
                .map {
                    PieSliceUi(
                        label = categoryShortLabel(it.category),
                        amount = it.expense,
                        color = it.color
                    )
                }
            YearlyReportUiState(
                selectedYear = year,
                yearLabel = "${year}년",
                canGoNextYear = year < currentYear,
                duesTotal = ledgerSnapshot.duesTotal,
                ledgerIncome = income,
                ledgerExpense = expense,
                cumulativeBalance = computeCumulativeBalance(
                    cumulativeDues = cumulative.cumulativeDues,
                    cumulativeLedgerIncome = cumulative.cumulativeLedgerIncome,
                    cumulativeLedgerExpense = cumulative.cumulativeLedgerExpense,
                    ledgerBalanceAtYearEnd = cumulative.ledgerBalanceAtYearEnd
                ),
                categorySummaries = categoryUi,
                incomePieSlices = incomePieSlices,
                expensePieSlices = expensePieSlices,
                transferSummaries = ledgerSnapshot.transfers.map { it.toTransferSummaryUi(accountLabels) },
                showDetailSheet = showSheet,
                selectedCategory = category
            )
        }

        if (showSheet && category != null) {
            combine(
                baseState,
                clubTransactionRepository.observeTransactionsByYearAndCategory(year, category)
            ) { state, transactions ->
                state.copy(
                    categoryDetailItems = transactions.map { it.toCategoryDetailItemUi() }
                )
            }
        } else {
            baseState.map { it.copy(categoryDetailItems = emptyList()) }
        }
    }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YearlyReportUiState(
            selectedYear = LocalDate.now().year,
            yearLabel = "${LocalDate.now().year}년"
        )
    )

    fun goToPreviousYear() {
        dismissCategoryDetail()
        selectedYear.value = selectedYear.value - 1
    }

    fun goToNextYear() {
        val next = selectedYear.value + 1
        if (next <= LocalDate.now().year) {
            dismissCategoryDetail()
            selectedYear.value = next
        }
    }

    fun goToCurrentYear() {
        dismissCategoryDetail()
        selectedYear.value = LocalDate.now().year
    }

    fun setSelectedYear(year: Int) {
        val currentYear = LocalDate.now().year
        val targetYear = year.coerceIn(2011, currentYear)
        if (targetYear == selectedYear.value) return
        dismissCategoryDetail()
        selectedYear.value = targetYear
    }

    fun openCategoryDetail(category: String) {
        selectedCategory.value = category
        showDetailSheet.value = true
    }

    fun dismissCategoryDetail() {
        showDetailSheet.value = false
        selectedCategory.value = null
    }

    private fun ClubTransactionEntity.toCategoryDetailItemUi() = CategoryDetailItemUi(
        id = id,
        date = date.toShortDateLabel(),
        note = note,
        amount = when {
            ClubCategory.isTransferCategory(category) -> expenseAmount
            type == ClubTransactionType.INCOME -> incomeAmount
            else -> expenseAmount
        },
        isIncome = type == ClubTransactionType.INCOME && !ClubCategory.isTransferCategory(category)
    )

    private fun ClubTransactionEntity.toTransferSummaryUi(
        accountLabels: Map<Int, String>
    ) = TransferSummaryUi(
        id = id,
        date = date.toShortDateLabel(),
        amount = expenseAmount,
        fromAccountLabel = accountId?.let(accountLabels::get) ?: "미지정 계좌",
        toAccountLabel = transferToAccountId?.let(accountLabels::get) ?: "미지정 계좌",
        note = note
    )

    private data class YearlyLedgerSnapshot(
        val ledgerSummary: YearlyClubTransactionSummaryRow?,
        val categories: List<ClubTransactionCategorySummaryRow>,
        val transfers: List<ClubTransactionEntity>,
        val accounts: List<ClubAccountEntity>,
        val duesTotal: Int
    )

    private data class CumulativeBalanceSnapshot(
        val cumulativeDues: Int,
        val cumulativeLedgerIncome: Int,
        val cumulativeLedgerExpense: Int,
        val ledgerBalanceAtYearEnd: Int?
    )
}
