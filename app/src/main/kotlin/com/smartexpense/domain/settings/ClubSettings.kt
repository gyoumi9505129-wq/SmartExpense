package com.smartexpense.domain.settings

import com.smartexpense.data.local.entity.club.DuesPaymentMethod

data class ClubSettings(
    val clubId: Long,
    val bankParseEnabledPackagesJson: String,
    val duesPaymentFilterYear: Int? = null,
    val duesPaymentFilterMemberId: Long? = null,
    val duesPaymentMethod: DuesPaymentMethod = DuesPaymentMethod.DEFAULT
) {
    companion object {
        fun emptyDefaults(clubId: Long): ClubSettings = ClubSettings(
            clubId = clubId,
            bankParseEnabledPackagesJson = ClubSettingDefaults.defaultEnabledPackagesJson
        )
    }
}
