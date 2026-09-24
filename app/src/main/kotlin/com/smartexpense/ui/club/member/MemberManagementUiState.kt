package com.smartexpense.ui.club.member

import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.domain.admission.AdmissionFeeBreakdown

data class MemberListItemUi(
    val id: Long,
    val name: String,
    val phone: String,
    val joinDate: String,
    val residenceRegion: String,
    val roleLabel: String,
    val statusLabel: String,
    val status: MemberStatus
)

data class MemberFormState(
    val editingId: Long? = null,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val joinDate: String = "",
    val birthDate: String = "",
    val isLunarBirth: Boolean = false,
    val residenceRegion: String = "",
    val address: String = "",
    val detailAddress: String = "",
    val role: MemberRole = MemberRole.GENERAL,
    val status: MemberStatus = MemberStatus.ACTIVE,
    val originalStatus: MemberStatus? = null,
    val suspensionDate: String = "",
    val statusChangeReason: String = "",
    val savedFingerprint: String = ""
) {
    val isEditing: Boolean get() = editingId != null
    val showSuspensionDate: Boolean get() = status != MemberStatus.ACTIVE
    val showStatusChangeReason: Boolean get() =
        isEditing && originalStatus != null && status != originalStatus
    val emailError: String? get() = validateEmail(email)
    val hasDraftInput: Boolean get() = listOf(
        name, phone, email, joinDate, birthDate, residenceRegion, address, detailAddress, suspensionDate, statusChangeReason
    ).any { it.isNotBlank() }
    val hasUnsavedChanges: Boolean
        get() = if (isEditing) contentFingerprint() != savedFingerprint else hasDraftInput

    fun contentFingerprint(): String = listOf(
        name,
        phone,
        email,
        joinDate,
        birthDate,
        isLunarBirth.toString(),
        residenceRegion,
        address,
        detailAddress,
        role.name,
        status.name,
        suspensionDate,
        statusChangeReason
    ).joinToString("\u0001")

    fun withSavedBaseline(): MemberFormState = copy(savedFingerprint = contentFingerprint())
}

private fun validateEmail(email: String): String? {
    if (email.isBlank()) return null
    val trimmed = email.trim()
    val atIndex = trimmed.indexOf('@')
    val dotIndex = trimmed.lastIndexOf('.')
    val isValid = atIndex > 0 &&
        dotIndex > atIndex + 1 &&
        dotIndex < trimmed.length - 1
    return if (isValid) null else "올바른 이메일 형식이 아닙니다"
}

data class MemberManagementUiState(
    val members: List<MemberListItemUi> = emptyList(),
    val memberCount: Int = 0,
    val activeCount: Int = 0,
    val dormantCount: Int = 0,
    val withdrawnCount: Int = 0,
    val statusFilter: MemberStatusFilter = MemberStatusFilter.ACTIVE,
    val isFormVisible: Boolean = false,
    val form: MemberFormState = MemberFormState(),
    val isSaving: Boolean = false,
    val deleteTargetId: Long? = null,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val historyTarget: MemberHistoryTarget? = null,
    val historyItems: List<MemberHistoryItemUi> = emptyList(),
    val showAdmissionFeeDialog: Boolean = false,
    val admissionFeeBreakdown: AdmissionFeeBreakdown? = null
)

/** 회원 목록 상단 상태 필터. [ALL]이면 전체 노출. */
enum class MemberStatusFilter {
    ALL,
    ACTIVE,
    DORMANT,
    WITHDRAWN
}

fun MemberStatusFilter.toChipLabel(count: Int): String = when (this) {
    MemberStatusFilter.ALL -> "전체 $count"
    MemberStatusFilter.ACTIVE -> "활동중 $count"
    MemberStatusFilter.DORMANT -> "휴면 $count"
    MemberStatusFilter.WITHDRAWN -> "탈퇴 $count"
}

fun MemberStatusFilter.matches(status: MemberStatus): Boolean = when (this) {
    MemberStatusFilter.ALL -> true
    MemberStatusFilter.ACTIVE -> status == MemberStatus.ACTIVE
    MemberStatusFilter.DORMANT -> status == MemberStatus.DORMANT
    MemberStatusFilter.WITHDRAWN -> status == MemberStatus.WITHDRAWN
}

data class MemberHistoryTarget(
    val memberId: Long,
    val memberName: String
)

data class MemberHistoryItemUi(
    val changeDate: String,
    val changeLabel: String,
    val suspensionDate: String?,
    val reason: String?
)

fun MemberRole.toDisplayLabel(): String = when (this) {
    MemberRole.GENERAL -> "일반"
    MemberRole.PRESIDENT -> "회장"
    MemberRole.TREASURER -> "운영관리자"
    MemberRole.EXECUTIVE -> "임원"
}

fun MemberStatus.toDisplayLabel(): String = when (this) {
    MemberStatus.ACTIVE -> "활동중"
    MemberStatus.DORMANT -> "휴면"
    MemberStatus.WITHDRAWN -> "탈퇴"
}

fun statusNameToDisplayLabel(statusName: String): String = runCatching {
    MemberStatus.valueOf(statusName).toDisplayLabel()
}.getOrElse { statusName }

val memberRoleOptions: List<Pair<MemberRole, String>> = MemberRole.entries.map { it to it.toDisplayLabel() }
val memberStatusOptions: List<Pair<MemberStatus, String>> = MemberStatus.entries.map { it to it.toDisplayLabel() }
