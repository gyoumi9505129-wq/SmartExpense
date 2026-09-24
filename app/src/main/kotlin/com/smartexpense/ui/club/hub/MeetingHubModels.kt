package com.smartexpense.ui.club.hub

import com.smartexpense.data.firebase.PrivilegedAuthConfig
import com.smartexpense.data.firebase.firestore.JoinRequestDoc
import com.smartexpense.data.firebase.firestore.MeetingDoc
import com.smartexpense.data.firebase.firestore.MemberStatusLabels
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.domain.security.AppLockUnlockMethod
import com.smartexpense.domain.user.UserRole
import com.smartexpense.ui.club.member.MemberFormState

enum class MeetingHubTab {
    MY,
    SEARCH,
    APPROVALS,
    CREATE
}

enum class MeetingAccessStatus {
    /** 이 모임을 만든 모임관리자 — 운영관리자 지정·편집·삭제 */
    OWNER,
    /** 지정 운영관리자 — 편집·동기화 */
    TREASURER,
    /** 승인된 일반 회원 — 조회 */
    JOINED,
    PENDING,
    REJECTED,
    AVAILABLE,
    /** 휴면 — 활동 중지 */
    SUSPENDED,
    /** 탈퇴 — 활동 중지 */
    DISABLED,
    /** 시스템관리자 — 전체 모임 이용 가능 */
    ELEVATED
}

data class MeetingHubItem(
    val meeting: MeetingDoc,
    val accessStatus: MeetingAccessStatus,
    val joinRequest: JoinRequestDoc? = null,
    val pendingApprovalCount: Int = 0,
    /** 소속·관리 권한이 있을 때만 리스트 우측 배지로 표시 */
    val roleBadge: UserRole? = null,
    /** 승인 후 회원 장부 정보가 아직 없는 경우 */
    val needsMemberProfile: Boolean = false
)

data class MemberProfilePromptState(
    val meeting: MeetingDoc,
    val form: MemberFormState = MemberFormState(),
    val enterAfterSave: Boolean = false,
    val promptKey: Int = 0,
    val errorMessage: String? = null
)

data class HubSettingsState(
    val visible: Boolean = false,
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val userRole: UserRole = UserRole.MEMBER,
    val canChangePassword: Boolean = true,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val showLogoutConfirm: Boolean = false,
    val isLoggingOut: Boolean = false,
    // 보안 설정 (로그인 방식 · 생체 인증) — 설정에서만 등록, 잠금 화면 없음
    val unlockMethod: AppLockUnlockMethod = AppLockUnlockMethod.PIN,
    val isBiometricEnabled: Boolean = false,
    val canUseBiometric: Boolean = false,
    val hasPinRegistered: Boolean = false,
    val hasPatternRegistered: Boolean = false,
    val showUnlockMethodDialog: Boolean = false,
    val showPinSetupDialog: Boolean = false,
    /** true면 기존 PIN/패턴을 덮어쓰는 변경 모드 */
    val isChangingCredential: Boolean = false,
    val pinInput: String = "",
    val pinConfirmInput: String = "",
    val pinSetupError: String? = null,
    val showPatternSetupDialog: Boolean = false,
    val patternInput: List<Int> = emptyList(),
    val patternConfirmInput: List<Int> = emptyList(),
    val patternSetupError: String? = null
)

data class MeetingHubUiState(
    val selectedTab: MeetingHubTab = MeetingHubTab.MY,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isElevated: Boolean = false,
    val userRole: UserRole = UserRole.MEMBER,
    val canReviewJoins: Boolean = false,
    val myMeetings: List<MeetingHubItem> = emptyList(),
    val searchableMeetings: List<MeetingHubItem> = emptyList(),
    val pendingApprovals: List<Pair<MeetingDoc, JoinRequestDoc>> = emptyList(),
    val showCreateDialog: Boolean = false,
    val showApprovalSheet: Boolean = false,
    val showJoinDialog: Boolean = false,
    val newMeetingName: String = "",
    val newMeetingDescription: String = "",
    val newMeetingSlogan: String = "",
    val joinMessage: String = "",
    val joinTarget: MeetingDoc? = null,
    val dialogErrorMessage: String? = null,
    val settings: HubSettingsState = HubSettingsState(),
    val snackbarMessage: String? = null,
    val memberProfilePrompt: MemberProfilePromptState? = null
) {
    val visibleTabs: List<MeetingHubTab>
        get() = buildList {
            add(MeetingHubTab.MY)
            if (isElevated) {
                add(MeetingHubTab.APPROVALS)
            } else {
                add(MeetingHubTab.SEARCH)
                if (canReviewJoins) add(MeetingHubTab.APPROVALS)
            }
            add(MeetingHubTab.CREATE)
        }

    val pendingProfileMeetings: List<MeetingHubItem>
        get() = myMeetings.filter { it.needsMemberProfile }

    fun tabLabel(tab: MeetingHubTab): String = when (tab) {
        MeetingHubTab.MY -> if (isElevated) "전체 모임" else "내 모임"
        MeetingHubTab.SEARCH -> "모임 찾기"
        MeetingHubTab.APPROVALS -> {
            val count = pendingApprovals.size
            if (count > 0) "가입 대기($count)" else "가입 대기"
        }
        MeetingHubTab.CREATE -> "만들기"
    }

    fun filteredMyMeetings(): List<MeetingHubItem> {
        if (!isElevated) return myMeetings
        val query = searchQuery.trim()
        if (query.isBlank()) return myMeetings
        return myMeetings.filter { item ->
            item.meeting.name.contains(query, ignoreCase = true) ||
                item.meeting.description.contains(query, ignoreCase = true)
        }
    }
}

internal fun isElevatedAccount(uid: String?, email: String?): Boolean =
    PrivilegedAuthConfig.isPrivilegedAccount(uid, email)

internal fun MeetingDoc.restrictionStatusFor(uid: String?): MeetingAccessStatus? {
    if (uid.isNullOrBlank() || uid !in inactiveMemberUids) return null
    val raw = inactiveMemberReasons[uid].orEmpty()
    val status = MemberStatusLabels.fromFirestore(raw)
    return if (status == MemberStatus.WITHDRAWN) {
        MeetingAccessStatus.DISABLED
    } else {
        MeetingAccessStatus.SUSPENDED
    }
}
