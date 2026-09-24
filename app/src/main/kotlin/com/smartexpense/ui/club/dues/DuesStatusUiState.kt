package com.smartexpense.ui.club.dues

import com.smartexpense.data.mapper.dues.MemberDuesSummary

data class MemberDuesStatusRowUi(
    val memberId: Long,
    val memberName: String,
    val totalUnpaidAmount: Int,
    val unpaidDetails: String,
    val isFullyPaid: Boolean,
    val hasRegisteredDues: Boolean = true
)

data class DuesStatusUiState(
    val selectedYear: Int? = null,
    val filterMemberId: Long? = null,
    val totalClubUnpaidAmount: Int = 0,
    val memberRows: List<MemberDuesStatusRowUi> = emptyList(),
    val showDetailSheet: Boolean = false,
    val detailMemberId: Long? = null,
    val detailMemberName: String? = null,
    val detailItems: List<MemberDuesDetailItemUi> = emptyList(),
    val detailDraftItems: List<MemberDuesDetailItemUi> = emptyList(),
    val isSavingDetail: Boolean = false,
    val editingDetailId: Long? = null
)

fun MemberDuesSummary.toStatusRowUi() = MemberDuesStatusRowUi(
    memberId = memberId,
    memberName = memberName,
    totalUnpaidAmount = totalUnpaidAmount,
    unpaidDetails = unpaidDetails,
    isFullyPaid = isFullyPaid,
    hasRegisteredDues = true
)

fun unregisteredMemberStatusRow(memberId: Long, memberName: String) = MemberDuesStatusRowUi(
    memberId = memberId,
    memberName = memberName,
    totalUnpaidAmount = 0,
    unpaidDetails = "미등록",
    isFullyPaid = false,
    hasRegisteredDues = false
)
