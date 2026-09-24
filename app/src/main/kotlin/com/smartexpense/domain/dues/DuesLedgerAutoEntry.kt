package com.smartexpense.domain.dues

import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType

object DuesLedgerAutoEntry {

    fun buildPaymentNote(memberName: String, year: Int, termLabel: String): String =
        "$memberName ${year}년 $termLabel 회비 납부"

    /**
     * 장부 비고가 회원·연도·회차를 가리키는지 판별.
     * 표준(`유락준 2022년 하반기 회비 납부`)과
     * 시드 표기(`유락준 회비(22년_하반기 회비 월분납 …)`, `2012하반기`)를 모두 허용.
     */
    fun noteMatchesMemberTerm(
        note: String,
        memberName: String,
        year: Int,
        termLabel: String
    ): Boolean {
        if (note.isBlank() || memberName.isBlank() || termLabel.isBlank()) return false
        if (!note.contains(memberName)) return false

        val shortYear = (year % 100).toString().padStart(2, '0')
        val markers = listOf(
            "${year}년 $termLabel",
            "${year}년$termLabel",
            "${year}년_$termLabel",
            "${year}_$termLabel",
            "${year}$termLabel",
            "${shortYear}년_$termLabel",
            "${shortYear}년 $termLabel",
            "${shortYear}년$termLabel",
            "${shortYear}_$termLabel"
        )
        return markers.any { note.contains(it) }
    }

    fun buildIncomeTransaction(
        clubId: Long,
        payDate: String,
        paidAmount: Long,
        memberId: Long,
        note: String,
        linkedDuesDetailId: Long? = null
    ): ClubTransactionEntity {
        val incomeAmount = paidAmount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return ClubTransactionEntity(
            clubId = clubId,
            date = payDate,
            type = ClubTransactionType.INCOME,
            category = ClubCategory.REGULAR_DUES,
            incomeAmount = incomeAmount,
            expenseAmount = 0,
            note = note,
            targetMemberId = memberId,
            linkedDuesDetailId = linkedDuesDetailId
        )
    }
}
