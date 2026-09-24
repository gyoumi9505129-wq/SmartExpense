package com.smartexpense.domain.admission

data class AdmissionFeeBreakdown(
    val totalBalance: Long,
    val totalUnpaidDues: Long,
    val activeMemberCount: Int,
    val combinedAssets: Long,
    val rawFeePerMember: Long,
    val admissionFee: Long
)

object AdmissionFeeCalculator {
    private const val TRUNCATION_UNIT = 10_000L

    fun calculate(
        totalBalance: Long,
        totalUnpaidDues: Long,
        activeMemberCount: Int
    ): AdmissionFeeBreakdown? {
        if (activeMemberCount <= 0) return null
        val combinedAssets = totalBalance + totalUnpaidDues
        val rawFeePerMember = combinedAssets / activeMemberCount
        val admissionFee = (rawFeePerMember / TRUNCATION_UNIT) * TRUNCATION_UNIT
        return AdmissionFeeBreakdown(
            totalBalance = totalBalance,
            totalUnpaidDues = totalUnpaidDues,
            activeMemberCount = activeMemberCount,
            combinedAssets = combinedAssets,
            rawFeePerMember = rawFeePerMember,
            admissionFee = admissionFee
        )
    }
}
