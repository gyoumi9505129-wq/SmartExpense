package com.smartexpense.ui.club.event

import com.smartexpense.data.local.entity.club.MemberStatus

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
    val memberSummaries: List<MemberEventExpenseUi> = emptyList(),
    val isEmpty: Boolean = true
)
