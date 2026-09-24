package com.smartexpense.ui.club.dues

import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.DuesTerm
import com.smartexpense.data.mapper.dues.MemberDuesSummary
import java.util.UUID

data class DuesMemberOption(
    val id: Long,
    val name: String
)

data class DuesPaymentItemUi(
    val id: Long,
    val memberName: String,
    val year: Int,
    val paymentMethodLabel: String,
    val periodSummary: String,
    val totalTargetAmount: Int,
    val paidAmount: Int,
    val unpaidAmount: Int,
    val isFullyPaid: Boolean,
    val latestPayDate: String
)

fun DuesPaymentMethod.toDisplayLabel(): String = when (this) {
    DuesPaymentMethod.MONTHLY -> "월별"
    DuesPaymentMethod.QUARTERLY -> "분기별"
    DuesPaymentMethod.HALF_YEARLY -> "반기별"
    DuesPaymentMethod.YEARLY -> "연도별"
}

val duesPaymentMethodOptions: List<Pair<DuesPaymentMethod, String>> =
    DuesPaymentMethod.entries.map { it to it.toDisplayLabel() }

data class DuesDetailItemState(
    val localKey: String = UUID.randomUUID().toString(),
    val id: Long = 0,
    val termLabel: String,
    val amount: String = "",
    /** 이미 납부된 금액(분납 포함). 완납이면 보통 amount 이상. */
    val paidAmount: Long = 0L,
    /** 폼을 열 당시 납부액. 납부 체크 해제 시 복원용. */
    val initialPaidAmount: Long = 0L,
    val isPaid: Boolean = false,
    val isExcluded: Boolean = false,
    val payDate: String = "",
    val isCustomAmount: Boolean = false,
    /** 이번 저장에서 추가로 납부할 금액(부분납). */
    val additionalPayAmount: String = "",
    /** 기존 분납 이력(표시용). */
    val paymentHistory: List<DuesPaymentEntryUi> = emptyList()
) {
    val monthNumber: Int? get() = termLabel.removeSuffix("월").toIntOrNull()?.takeIf { it in 1..12 }

    val targetAmount: Int get() = amount.toIntOrNull() ?: 0

    val remainingAmount: Int
        get() = when {
            isExcluded -> 0
            isPaid -> 0
            else -> (targetAmount - paidAmount.toInt()).coerceAtLeast(0)
        }

    val isPartialPaid: Boolean
        get() = !isExcluded && !isPaid && paidAmount > 0L && remainingAmount > 0

    /** 납부 체크 시 새로 장부에 올라갈 금액(잔여). */
    val completionAmount: Int
        get() = when {
            isExcluded -> 0
            isPaid -> 0
            else -> remainingAmount
        }

    val additionalPayAmountValue: Long
        get() = additionalPayAmount.filter { it.isDigit() }.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    /** 저장 시 반영될 누적 납부액. */
    fun effectivePaidAmountForSave(): Long {
        val target = targetAmount.toLong().coerceAtLeast(0L)
        return when {
            isExcluded -> 0L
            isPaid && target > 0L -> target
            additionalPayAmountValue > 0L ->
                (initialPaidAmount + additionalPayAmountValue).coerceIn(0L, target.coerceAtLeast(0L))
            else -> paidAmount.coerceIn(0L, target.coerceAtLeast(0L))
        }
    }
}

data class DuesPaymentFormState(
    val currentDuesId: Long? = null,
    val memberId: Long? = null,
    val year: Int = java.time.LocalDate.now().year,
    val totalTargetAmount: String = "",
    val paymentMethod: DuesPaymentMethod = DuesPaymentMethod.DEFAULT,
    val details: List<DuesDetailItemState> = emptyList()
) {
    val isEditMode: Boolean get() = currentDuesId != null
    val excludedAmount: Int get() =
        details.filter { it.isExcluded }.sumOf { it.targetAmount }
    val paidAccumulatedAmount: Int get() =
        details.filter { !it.isExcluded }.sumOf { item ->
            when {
                item.isPaid -> item.targetAmount
                else -> item.paidAmount.toInt().coerceAtLeast(0)
            }
        }
    val unpaidAmount: Int get() {
        val total = totalTargetAmount.toIntOrNull() ?: 0
        return (total - excludedAmount - paidAccumulatedAmount).coerceAtLeast(0)
    }
    val isFullyPaid: Boolean get() {
        val total = totalTargetAmount.toIntOrNull() ?: 0
        val billable = total - excludedAmount
        return billable > 0 && unpaidAmount == 0
    }
    val hasDraftInput: Boolean get() = when {
        isEditMode ->
            memberId != null ||
                totalTargetAmount.isNotBlank() ||
                details.any {
                    it.amount.isNotBlank() || it.isPaid || it.isExcluded ||
                        it.payDate.isNotBlank() || it.additionalPayAmountValue > 0L
                }
        else ->
            memberId != null ||
                totalTargetAmount != DEFAULT_ANNUAL_DUES_AMOUNT ||
                details.any {
                    it.isPaid || it.isExcluded || it.amount.isNotBlank() ||
                        it.additionalPayAmountValue > 0L
                }
    }
}

data class DuesPaymentUiState(
    val memberOptions: List<DuesMemberOption> = emptyList(),
    val memberSummaries: List<MemberDuesSummary> = emptyList(),
    val isFormVisible: Boolean = false,
    val isEditMode: Boolean = false,
    val form: DuesPaymentFormState = DuesPaymentFormState(),
    val isSaving: Boolean = false,
    val deleteTargetId: Long? = null,
    val errorMessage: String? = null,
    val selectedYear: Int? = null,
    val selectedMemberId: Long? = null
)

fun DuesTerm.toDisplayLabel(): String = when (this) {
    DuesTerm.FIRST_HALF -> "상반기"
    DuesTerm.SECOND_HALF -> "하반기"
}

val duesYearOptions: List<Pair<Int, String>> =
    (java.time.Year.now().value downTo 2001).map { it to "${it}년" }

val duesMonthOptions: List<Pair<Int, String>> = (1..12).map { it to "${it}월" }

/** 신규 등록 폼 기본 연회비 (원) */
const val DEFAULT_ANNUAL_DUES_AMOUNT = "300000"
