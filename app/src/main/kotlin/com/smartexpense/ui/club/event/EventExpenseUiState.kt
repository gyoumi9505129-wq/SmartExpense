package com.smartexpense.ui.club.event

import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.ui.club.member.MemberStatusFilter

data class EventExpenseDetailUi(
    val date: String,
    val eventSubCategory: String,
    val amount: Int,
    val note: String?
)

data class MemberEventExpenseUi(
    val memberId: Long?,
    val memberName: String,
    val memberStatus: MemberStatus,
    val totalAmount: Int,
    val details: List<EventExpenseDetailUi>
)

data class EventExpenseUiState(
    val totalClubExpense: Int = 0,
    /** 현재 필터에 맞는 회원별 요약 */
    val memberSummaries: List<MemberEventExpenseUi> = emptyList(),
    val memberCount: Int = 0,
    val activeCount: Int = 0,
    val dormantCount: Int = 0,
    val withdrawnCount: Int = 0,
    val statusFilter: MemberStatusFilter = MemberStatusFilter.ACTIVE,
    /** 경조 데이터가 아예 없을 때 */
    val isEmpty: Boolean = true
)
