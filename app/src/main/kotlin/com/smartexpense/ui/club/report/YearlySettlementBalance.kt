package com.smartexpense.ui.club.report

import com.smartexpense.domain.ledger.LedgerBalanceCalculator

internal fun yearEndDate(year: Int): String = "$year-12-31"

/**
 * 연말 잔액 = 장부 누적(수입−지출).
 * ledgerBalanceAtYearEnd(구 balanceAfter)는 참고만 하고, 불일치 시 누적값을 채택합니다.
 * cumulativeDues는 장부 수입에 이미 포함된 회비와 이중 합산되지 않도록 사용하지 않습니다.
 */
internal fun computeCumulativeBalance(
    cumulativeDues: Int,
    cumulativeLedgerIncome: Int,
    cumulativeLedgerExpense: Int,
    ledgerBalanceAtYearEnd: Int?
): Int {
    val recomputed = cumulativeLedgerIncome.toLong() - cumulativeLedgerExpense.toLong()
    val resolved = LedgerBalanceCalculator.resolveDisplayBalance(
        recomputed = recomputed,
        suspiciousStored = ledgerBalanceAtYearEnd?.toLong()
    )
    return with(LedgerBalanceCalculator) { resolved.toSafeBalanceInt() }
}
