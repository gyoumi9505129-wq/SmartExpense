package com.smartexpense.data.firebase

import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.auth.FirebaseAuthSession
import com.smartexpense.data.firebase.firestore.MeetingDoc
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.repository.club.SelectedMeetingRepository
import com.smartexpense.domain.user.UserRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 역할 판별 (선택 중인 모임 기준):
 * - 시스템관리자: [SystemAdminConfig] — 모든 모임 관리 가능
 * - 모임관리자(개설자): ownerUid — 운영관리자 지정·편집·동기화·삭제
 * - 지정 운영관리자: adminUid — 편집·동기화
 * - 승인된 일반 회원(sharedWith): 조회만
 *
 * 누구나 모임을 만들 수 있고, 만든 모임의 관리자가 됩니다.
 * 다른 모임에는 가입 요청 후 일반 회원으로 이용합니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MeetingRoleRepository @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository,
    private val meetingFirestoreRepository: MeetingFirestoreRepository
) {
    val selectedMeeting: Flow<MeetingDoc?> =
        selectedMeetingRepository.selectedMeetingId.flatMapLatest { meetingId ->
            if (meetingId.isNullOrBlank()) {
                flowOf(null)
            } else {
                meetingFirestoreRepository.observeMeeting(meetingId).catch { emit(null) }
            }
        }

    private val roleSnapshot: Flow<RoleSnapshot> = combine(
        authRepository.authState,
        selectedMeetingRepository.selectedMeetingId,
        selectedMeeting
    ) { session, meetingId, meeting ->
        RoleSnapshot(
            session = session,
            meetingId = meetingId,
            meeting = meeting
        )
    }.distinctUntilChanged()

    val isSystemAdmin: Flow<Boolean> = roleSnapshot
        .map { it.isSystemAdmin() }
        .catch { emit(false) }
        .distinctUntilChanged()

    val isMeetingOwner: Flow<Boolean> = roleSnapshot
        .map { it.isMeetingOwner() }
        .catch { emit(false) }
        .distinctUntilChanged()

    val isDesignatedTreasurer: Flow<Boolean> = roleSnapshot
        .map { it.isTreasurer() }
        .catch { emit(false) }
        .distinctUntilChanged()

    /** 개설자·지정 운영관리자·시스템관리자 */
    val canManageMeeting: Flow<Boolean> = roleSnapshot
        .map { it.canManageMeeting() }
        .catch { emit(false) }
        .distinctUntilChanged()

    val canEdit: Flow<Boolean> = roleSnapshot
        .map { it.canEdit() }
        .catch { emit(false) }
        .distinctUntilChanged()

    /** 클라우드 올리기/내리기 — 관리 권한과 동일 */
    val canSync: Flow<Boolean> = canEdit

    /**
     * 선택 모임에 소속되어 클라우드 조회가 가능한지.
     * 일반 회원(조회 전용)도 true.
     */
    val canAccessCloud: Flow<Boolean> = roleSnapshot
        .map { it.isMeetingMember() }
        .catch { emit(false) }
        .distinctUntilChanged()

    val canDangerousOps: Flow<Boolean> = isSystemAdmin

    val designatedTreasurerUid: Flow<String?> = roleSnapshot
        .map { it.meeting?.adminUid?.takeIf { uid -> uid.isNotBlank() } }
        .catch { emit(null) }
        .distinctUntilChanged()

    /** UI 배지용 현재 사용자 역할 (선택 모임 기준) */
    val currentUserRole: Flow<UserRole> = roleSnapshot
        .map { it.resolveUserRole() }
        .catch { emit(UserRole.MEMBER) }
        .distinctUntilChanged()

    fun isSystemAdminSession(): Boolean {
        val session = authRepository.currentSession()
        return SystemAdminConfig.matches(session?.uid, session?.email)
    }

    suspend fun isSystemAdminNow(): Boolean = currentSnapshot().isSystemAdmin()

    suspend fun currentUserRoleNow(): UserRole = currentSnapshot().resolveUserRole()

    suspend fun canEditNow(): Boolean = currentSnapshot().canEdit()

    suspend fun canSyncNow(): Boolean = canEditNow()

    suspend fun canAccessCloudNow(): Boolean = currentSnapshot().isMeetingMember()

    suspend fun canDangerousOpsNow(): Boolean = isSystemAdminNow()

    suspend fun canManageMeetingNow(): Boolean = currentSnapshot().canManageMeeting()

    suspend fun isMeetingOwnerNow(): Boolean = currentSnapshot().isMeetingOwner()

    suspend fun isDesignatedTreasurerNow(): Boolean = currentSnapshot().isTreasurer()

    private suspend fun currentSnapshot(): RoleSnapshot {
        val session = authRepository.currentSession()
        val meetingId = runCatching {
            selectedMeetingRepository.requireSelectedMeetingId()
        }.getOrNull()
        val meeting = if (meetingId.isNullOrBlank()) {
            null
        } else {
            runCatching { meetingFirestoreRepository.getMeeting(meetingId) }.getOrNull()
        }
        return RoleSnapshot(session = session, meetingId = meetingId, meeting = meeting)
    }

    private data class RoleSnapshot(
        val session: FirebaseAuthSession?,
        val meetingId: String?,
        val meeting: MeetingDoc?
    ) {
        fun isSystemAdmin(): Boolean {
            val uid = session?.uid ?: return false
            return SystemAdminConfig.matches(uid, session.email)
        }

        fun isMeetingOwner(): Boolean {
            val uid = session?.uid ?: return false
            return meeting?.ownerUid == uid
        }

        fun isMeetingAdminUid(): Boolean {
            val uid = session?.uid ?: return false
            val adminUid = meeting?.adminUid.orEmpty()
            return adminUid.isNotBlank() && adminUid == uid
        }

        fun isTreasurer(): Boolean = isMeetingAdminUid()

        fun canManageMeeting(): Boolean =
            isSystemAdmin() || isMeetingOwner() || isMeetingAdminUid()

        fun canEdit(): Boolean = canManageMeeting()

        /** 현재 선택 모임의 소속 여부 (개설자·지정 운영관리자·승인된 활동 회원·시스템관리자) */
        fun isMeetingMember(): Boolean {
            val uid = session?.uid ?: return false
            if (meetingId.isNullOrBlank() || meeting == null) return false
            if (canManageMeeting()) return true
            if (meeting.inactiveMemberUids.contains(uid)) return false
            return meeting.sharedWith.contains(uid)
        }

        fun resolveUserRole(): UserRole = UserRole.resolve(
            uid = session?.uid,
            email = session?.email,
            meeting = meeting
        )
    }
}
