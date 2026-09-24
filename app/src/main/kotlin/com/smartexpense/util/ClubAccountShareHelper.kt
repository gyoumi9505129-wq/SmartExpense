package com.smartexpense.util

import com.smartexpense.data.local.entity.club.ClubAccountEntity

object ClubAccountShareHelper {
    fun buildShareText(account: ClubAccountEntity, clubName: String): String = buildString {
        appendLine("[$clubName 계좌 안내]")
        appendLine("■ 은행: ${account.bankName}")
        appendLine("■ 계좌번호: ${account.accountNumber}")
        appendLine("■ 예금주: ${account.holderName}")
        appendLine()
        append("회비 납부 시 위 계좌로 입금 부탁드립니다.")
    }
}
