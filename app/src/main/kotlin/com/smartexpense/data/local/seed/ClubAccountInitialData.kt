package com.smartexpense.data.local.seed

import com.smartexpense.data.local.entity.club.ClubAccountEntity

/**
 * 한우리 모임 기본 회비 입금 계좌.
 */
object ClubAccountInitialData {

    fun accounts(clubId: Long): List<ClubAccountEntity> = listOf(
        ClubAccountEntity(
            clubId = clubId,
            bankName = "하나은행",
            accountNumber = "47391022275807",
            holderName = "김성겸"
        )
    )
}
