package com.smartexpense.data.repository.club

data class DuesDetailOption(
    val detailId: Long,
    val memberId: Long,
    val year: Int,
    val termLabel: String,
    val amount: Int,
    val paidAmount: Long = 0L
) {
    val remainingAmount: Int
        get() = (amount - paidAmount).coerceAtLeast(0).toInt()

    fun toDropdownLabel(): String =
        if (paidAmount > 0L && remainingAmount > 0) {
            "${year}년 $termLabel · 잔여 ${"%,d".format(remainingAmount)}원"
        } else {
            "${year}년 $termLabel · ${"%,d".format(amount)}원"
        }
}
