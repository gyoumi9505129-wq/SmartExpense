package com.smartexpense.domain.ledger

import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity

data class YearBalanceEntry(
    val openingBalance: Long,
    val closingBalance: Long
)

/**
 * 연도별 BS 이월: opening(Y) = closing(Y-1).
 * 웹 yearBalances 맵과 동일 규칙.
 */
object YearLedgerBalances {
    const val FIRST_LEDGER_YEAR = 2011

    fun yearFromDate(date: String): Int? {
        if (date.length < 4) return null
        return date.take(4).toIntOrNull()?.takeIf { it > 1900 }
    }

    fun build(
        transactions: List<ClubTransactionEntity>,
        initialBalance: Long?,
        toYear: Int
    ): Pair<Map<String, YearBalanceEntry>, Long> {
        val initial = FreshLedgerBalance.sanitizeInitialBalance(initialBalance)
        val byYear = linkedMapOf<Int, MutableList<ClubTransactionEntity>>()
        var minYear = toYear
        for (tx in transactions) {
            val y = yearFromDate(tx.date) ?: continue
            if (y < FIRST_LEDGER_YEAR || y > toYear) continue
            byYear.getOrPut(y) { mutableListOf() }.add(tx)
            if (y < minYear) minYear = y
        }
        if (byYear.isEmpty()) {
            val map = mapOf(
                toYear.toString() to YearBalanceEntry(initial, initial)
            )
            return map to initial
        }

        val fromYear = minOf(minYear, FIRST_LEDGER_YEAR).coerceAtLeast(FIRST_LEDGER_YEAR)
        val yearBalances = linkedMapOf<String, YearBalanceEntry>()
        var opening = initial
        var current = initial
        for (year in fromYear..toYear) {
            val yearTxs = byYear[year].orEmpty()
            if (yearTxs.isEmpty() && year < toYear) continue
            var income = 0L
            var expense = 0L
            for (tx in yearTxs) {
                if (ClubCategory.isTransferCategory(tx.category)) continue
                income += tx.incomeAmount.toLong().coerceAtLeast(0L)
                expense += tx.expenseAmount.toLong().coerceAtLeast(0L)
            }
            val closing = FreshLedgerBalance.calculate(opening, income, expense)
            yearBalances[year.toString()] = YearBalanceEntry(opening, closing)
            opening = closing
            current = closing
        }
        if (!yearBalances.containsKey(toYear.toString())) {
            yearBalances[toYear.toString()] = YearBalanceEntry(current, current)
        }
        return yearBalances to current
    }

    fun toFirestoreMap(yearBalances: Map<String, YearBalanceEntry>): Map<String, Any> {
        return yearBalances.mapValues { (_, v) ->
            mapOf(
                "openingBalance" to v.openingBalance,
                "closingBalance" to v.closingBalance
            )
        }
    }
}
