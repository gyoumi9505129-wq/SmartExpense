package com.smartexpense.domain.ledger

/**
 * 통장 현재 잔액은 오직 이 공식으로만 계산합니다.
 * 기존 currentBalance / balanceAfter 상대 가감은 사용하지 않습니다.
 *
 * 진짜 현재 잔액 = initialBalance + 전체수입합 − 전체지출합
 */
object FreshLedgerBalance {
    fun sanitizeInitialBalance(raw: Long?): Long {
        if (raw == null || raw < 0L) return 0L
        return raw
    }

    fun calculate(
        initialBalance: Long?,
        totalIncome: Long,
        totalExpense: Long
    ): Long {
        val initial = sanitizeInitialBalance(initialBalance)
        val income = totalIncome.coerceAtLeast(0L)
        val expense = totalExpense.coerceAtLeast(0L)
        return initial + income - expense
    }

    fun Long.toDisplayInt(): Int =
        coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
