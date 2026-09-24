package com.smartexpense.ui.club.dues

data class DuesPaymentEntryUi(
    val id: Long = 0,
    val payDate: String,
    val amount: Long,
    val linkedTransactionId: Long? = null,
)

data class MemberDuesDetailItemUi(
    val detailId: Long,
    val year: Int,
    val termLabel: String,
    val amount: Int,
    val paidAmount: Long,
    val payDate: String,
    val isPaid: Boolean,
    val isExcluded: Boolean = false,
    val paymentHistory: List<DuesPaymentEntryUi> = emptyList(),
) {
    val remainingAmount: Int
        get() = if (isExcluded) 0 else (amount - paidAmount).coerceAtLeast(0).toInt()
}

fun List<DuesPaymentEntryUi>.toAggregatePaidAmount(): Long = sumOf { it.amount.coerceAtLeast(0) }

fun List<DuesPaymentEntryUi>.toLatestPayDate(): String =
    filter { it.amount > 0 && it.payDate.isNotBlank() }
        .maxOfOrNull { it.payDate }
        .orEmpty()
