package com.smartexpense.ui.club.transaction

import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.domain.dues.DuesMatchResult
import com.smartexpense.domain.dues.DuesMemberMatchStatus
import java.time.YearMonth

data class DuesAutoMatchUi(
    val memberMatchStatus: DuesMemberMatchStatus,
    val memberId: Long? = null,
    val memberName: String? = null,
    val detailIds: List<Long> = emptyList(),
    val bannerMessage: String? = null,
    val canAutoMatch: Boolean = false,
    val needsMemberSelection: Boolean = false
)

fun DuesMatchResult.toAutoMatchUi(): DuesAutoMatchUi = DuesAutoMatchUi(
    memberMatchStatus = memberMatchStatus,
    memberId = memberId,
    memberName = memberName,
    detailIds = matchedDetails.map { it.detailId },
    bannerMessage = bannerMessage(),
    canAutoMatch = canAutoMatch,
    needsMemberSelection = needsMemberSelection
)

data class ClubTransactionItemUi(
    val id: Long,
    val date: String,
    val typeLabel: String,
    val category: String,
    val amount: Int,
    val note: String?,
    val balanceAfter: Int? = null,
    val hasReceipt: Boolean = false,
    val isTransfer: Boolean = false
)

enum class TransactionEntryMode {
    INCOME,
    EXPENSE,
    TRANSFER
}

/** 장부에서 정기회비 등록 시 납부 방식 */
enum class DuesLedgerPaymentMode {
    /** 잔여 전액 납부 */
    FULL,
    /** 잔여보다 적은 금액 분납 */
    PARTIAL
}

data class TransactionFormState(
    val editingId: Long? = null,
    val entryMode: TransactionEntryMode = TransactionEntryMode.INCOME,
    val type: ClubTransactionType = ClubTransactionType.INCOME,
    val category: String = ClubCategory.defaultCategory(ClubTransactionType.INCOME),
    val date: String = java.time.LocalDate.now().toString(),
    val amount: String = "",
    val note: String = "",
    val receiptPath: String? = null,
    val originalReceiptPath: String? = null,
    val targetMemberId: Long? = null,
    val eventSubCategory: String? = null,
    val originalTargetMemberId: Long? = null,
    val originalEventSubCategory: String? = null,
    val accountId: Int? = null,
    val transferToAccountId: Int? = null,
    val originalAccountId: Int? = null,
    val originalTransferToAccountId: Int? = null,
    val duesAutoMatch: DuesAutoMatchUi? = null,
    val focusMemberDropdown: Boolean = false,
    val duesLinkDetailId: Long? = null,
    val originalDuesLinkDetailId: Long? = null,
    val duesLedgerPaymentMode: DuesLedgerPaymentMode = DuesLedgerPaymentMode.FULL,
    /** 선택 회차의 잔여 회비(부분 납부 안내·검증용) */
    val duesLinkRemainingAmount: Int? = null
) {
    val isEditing: Boolean get() = editingId != null
    val isTransferMode: Boolean get() = entryMode == TransactionEntryMode.TRANSFER
    val isRegularDuesCategory: Boolean get() = category == ClubCategory.REGULAR_DUES && !isTransferMode
    val isCondolenceCategory: Boolean get() = ClubCategory.isCondolenceCategory(category)
    val showDuesMemberSelector: Boolean get() =
        duesAutoMatch?.needsMemberSelection == true && !isEditing
    val showManualDuesLinkUi: Boolean get() =
        isRegularDuesCategory && !isEditing && duesAutoMatch == null
    val showDuesAutoMatchBanner: Boolean get() =
        duesAutoMatch != null && !isEditing &&
            (duesAutoMatch.canAutoMatch || duesAutoMatch.bannerMessage != null)
    val hasDraftInput: Boolean get() =
        amount.isNotBlank() ||
            note.isNotBlank() ||
            receiptPath != originalReceiptPath ||
            targetMemberId != originalTargetMemberId ||
            eventSubCategory != originalEventSubCategory ||
            accountId != originalAccountId ||
            transferToAccountId != originalTransferToAccountId ||
            duesLinkDetailId != originalDuesLinkDetailId
}

enum class TransactionListMode {
    MONTHLY,
    YEARLY,
    ALL
}

data class TransactionUiState(
    val listMode: TransactionListMode = TransactionListMode.MONTHLY,
    val currentYearMonth: YearMonth = YearMonth.now(),
    val displayTransactions: List<ClubTransactionItemUi> = emptyList(),
    val monthlyTransactions: List<ClubTransactionItemUi> = emptyList(),
    val totalIncome: Int = 0,
    val totalExpense: Int = 0,
    val balance: Int = 0,
    val searchQuery: String = "",
    val appliedFilter: TransactionFilterState = TransactionFilterState(),
    val filterDraft: TransactionFilterState = TransactionFilterState(),
    val showFilterSheet: Boolean = false,
    val activeFilterChips: List<ActiveFilterChipUi> = emptyList(),
    val categoryFilterOptions: List<String> = emptyList(),
    val isFormVisible: Boolean = false,
    val form: TransactionFormState = TransactionFormState(),
    val memberOptions: List<Pair<Long, String>> = emptyList(),
    val unpaidDuesOptions: List<Pair<Long, String>> = emptyList(),
    val duesMemoPreview: String? = null,
    val accountOptions: List<Pair<Int, String>> = emptyList(),
    val isSaving: Boolean = false,
    val deleteTargetId: Long? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val isEmpty: Boolean get() = displayTransactions.isEmpty()
    val hasActiveFilters: Boolean get() = appliedFilter.isActive
    val isSearchOrFilterActive: Boolean get() = searchQuery.isNotBlank() || hasActiveFilters
}

fun ClubTransactionType.toDisplayLabel(): String = when (this) {
    ClubTransactionType.INCOME -> "수입"
    ClubTransactionType.EXPENSE -> "지출"
}

fun allCategoryOptions(): List<String> =
    ClubCategory.incomeCategories + ClubCategory.expenseCategories

fun categoriesForEntryMode(mode: TransactionEntryMode): List<String> = when (mode) {
    TransactionEntryMode.INCOME -> ClubCategory.incomeCategories
    TransactionEntryMode.EXPENSE -> ClubCategory.expenseCategories
    TransactionEntryMode.TRANSFER -> emptyList()
}

fun YearMonth.toReturnBadgeLabel(): String =
    String.format("%04d/%02d", year, monthValue)

fun YearMonth.toYearOnlyReturnBadgeLabel(): String =
    String.format("%04d", year)

fun YearMonth.toYearMonthKey(): String = String.format("%04d-%02d", year, monthValue)

fun String.toYearMonthKey(): String? = takeIf { length >= 7 }?.substring(0, 7)

fun String.toShortDateLabel(): String {
    val parts = split("-")
    if (parts.size < 3) return this
    return "${parts[1]}.${parts[2]}"
}

fun String.toFullDateLabel(): String {
    val parts = split("-")
    if (parts.size < 3) return this
    return "${parts[0]}.${parts[1]}.${parts[2]}"
}
