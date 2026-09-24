package com.smartexpense.data.excelimport

import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus

internal object ExcelCellFormatters {
    fun formatMemberStatus(status: MemberStatus): String = when (status) {
        MemberStatus.ACTIVE -> "활동"
        MemberStatus.DORMANT -> "휴면"
        MemberStatus.WITHDRAWN -> "탈퇴"
    }

    fun formatMemberRole(role: MemberRole): String = when (role) {
        MemberRole.GENERAL -> "일반"
        MemberRole.PRESIDENT -> "회장"
        MemberRole.TREASURER -> "운영관리자"
        MemberRole.EXECUTIVE -> "임원"
    }

    fun formatTransactionType(type: ClubTransactionType): String = when (type) {
        ClubTransactionType.INCOME -> "수입"
        ClubTransactionType.EXPENSE -> "지출"
    }

    fun formatPaymentMethod(method: DuesPaymentMethod): String = when (method) {
        DuesPaymentMethod.MONTHLY -> "월별"
        DuesPaymentMethod.QUARTERLY -> "분기별"
        DuesPaymentMethod.HALF_YEARLY -> "반기별"
        DuesPaymentMethod.YEARLY -> "연도별"
    }

    fun formatBooleanYn(value: Boolean): String = if (value) "Y" else "N"
}
