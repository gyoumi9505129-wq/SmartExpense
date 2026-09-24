package com.smartexpense.domain.dues

import com.smartexpense.data.local.entity.club.DuesDetailEntity

fun isEffectivelyPaid(
    isPaid: Boolean,
    paidAmount: Long,
    amount: Int,
    isExcluded: Boolean
): Boolean {
    if (isExcluded) return false
    if (amount <= 0) return false
    return isPaid || paidAmount >= amount
}

fun DuesDetailEntity.isEffectivelyPaid(): Boolean =
    isEffectivelyPaid(
        isPaid = isPaid,
        paidAmount = paidAmount,
        amount = amount,
        isExcluded = isExcluded
    )

fun DuesDetailEntity.isPayableUnpaid(): Boolean =
    !isExcluded && amount > 0 && paidAmount < amount
