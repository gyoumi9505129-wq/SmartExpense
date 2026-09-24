package com.smartexpense.domain.ledger

import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity

/**
 * 장부 잔액은 항상 거래 전체를 누적 합산합니다.
 * balanceAfter 필드에 의존하지 않습니다.
 *
 * 공식: 기초잔액 + Σ(수입 전표) − Σ(지출 전표)
 * 계좌 이체는 모임 통장 총액에 넣지 않습니다.
 */
object LedgerBalanceCalculator {
    /**
     * 금액 문자열을 양의 Int로 안전하게 변환합니다.
     * 콤마/공백 제거, 마이너스 기호·오버플로우·비숫자 거부.
     */
    fun parsePositiveAmount(raw: String): Int? {
        val cleaned = raw.trim()
            .replace(",", "")
            .replace(" ", "")
            .replace("원", "")
        if (cleaned.isEmpty() || cleaned.startsWith("-") || cleaned.startsWith("+")) {
            return null
        }
        val value = cleaned.toLongOrNull() ?: return null
        if (value <= 0L || value > Int.MAX_VALUE.toLong()) return null
        return value.toInt()
    }

    fun netBalance(transactions: Collection<ClubTransactionEntity>): Long =
        operatingIncome(transactions) - operatingExpense(transactions)

    /** 계좌 이체를 제외한 수입 합. 전표 유형이 깨져 있어도 incomeAmount를 본다. */
    fun operatingIncome(transactions: Collection<ClubTransactionEntity>): Long {
        var income = 0L
        for (tx in transactions) {
            if (ClubCategory.isTransferCategory(tx.category)) continue
            income += tx.incomeAmount.toLong().coerceAtLeast(0L)
        }
        return income
    }

    /** 계좌 이체를 제외한 지출 합. 전표 유형이 깨져 있어도 expenseAmount를 본다. */
    fun operatingExpense(transactions: Collection<ClubTransactionEntity>): Long {
        var expense = 0L
        for (tx in transactions) {
            if (ClubCategory.isTransferCategory(tx.category)) continue
            expense += tx.expenseAmount.toLong().coerceAtLeast(0L)
        }
        return expense
    }

    fun incomeTotal(transactions: Collection<ClubTransactionEntity>): Long =
        transactions.sumOf { it.incomeAmount.toLong() }

    fun expenseTotal(transactions: Collection<ClubTransactionEntity>): Long =
        transactions.sumOf { it.expenseAmount.toLong() }

    /**
     * 날짜·id 순으로 주행 잔액을 계산해 (transactionId → balanceAfter) 맵을 반환합니다.
     * 계좌 이체는 통장 총액과 동일하게 누적에서 제외합니다.
     * @param initialBalance 기초 이월금(음수면 호출 전 0으로 정제)
     */
    fun runningBalances(
        transactions: Collection<ClubTransactionEntity>,
        initialBalance: Long = 0L
    ): Map<Long, Int> {
        val ordered = transactions.sortedWith(
            compareBy<ClubTransactionEntity> { it.date }.thenBy { it.id }
        )
        var running = initialBalance.coerceAtLeast(0L)
        val result = LinkedHashMap<Long, Int>(ordered.size)
        for (tx in ordered) {
            if (!ClubCategory.isTransferCategory(tx.category)) {
                val inAmt = tx.incomeAmount.toLong().coerceAtLeast(0L)
                val outAmt = tx.expenseAmount.toLong().coerceAtLeast(0L)
                running += inAmt - outAmt
            }
            result[tx.id] = running.toSafeBalanceInt()
        }
        return result
    }

    /** 최종 잔액 = 기초이월 + 총수입 − 총지출 */
    fun totalBalance(initialBalance: Long, totalIncome: Long, totalExpense: Long): Long {
        val initial = initialBalance.takeIf { it >= 0L } ?: 0L
        return initial + totalIncome.coerceAtLeast(0L) - totalExpense.coerceAtLeast(0L)
    }

    fun Long.toSafeBalanceInt(): Int =
        coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    /**
     * 화면에 뿌리기 전 무결성 검사.
     * stored(예: 잘못된 balanceAfter)와 재계산값이 다르거나
     * 재계산은 양수인데 표시값이 음수면 재계산값을 채택합니다.
     */
    fun resolveDisplayBalance(
        recomputed: Long,
        suspiciousStored: Long? = null
    ): Long {
        if (suspiciousStored != null &&
            suspiciousStored < 0L &&
            recomputed >= 0L
        ) {
            return recomputed
        }
        return recomputed
    }
}
