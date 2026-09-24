package com.smartexpense.domain.dues

import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.EntityTimestamps

fun DuesDetailEntity.applyPayment(payDate: String, paidAmount: Long): DuesDetailEntity {
    val normalizedPaid = paidAmount.coerceAtLeast(0)
    return copy(
        paidAmount = normalizedPaid,
        payDate = if (normalizedPaid > 0) payDate else "",
        isPaid = normalizedPaid >= amount && amount > 0 && !isExcluded,
        updatedAt = EntityTimestamps.now()
    )
}

fun DuesDetailEntity.remainingAmount(): Int =
    (amount - paidAmount).coerceAtLeast(0).toInt()
