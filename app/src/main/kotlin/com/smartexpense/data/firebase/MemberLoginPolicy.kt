package com.smartexpense.data.firebase

import com.smartexpense.data.firebase.PrivilegedAuthConfig
import com.smartexpense.data.firebase.auth.FirebaseAuthSession
import com.smartexpense.data.firebase.firestore.JoinRequestFirestoreRepository
import com.smartexpense.data.firebase.firestore.MeetingDoc
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberDoc
import com.smartexpense.data.firebase.firestore.MemberFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberStatusLabels
import com.smartexpense.data.firebase.firestore.UserProfileFirestoreRepository
import com.smartexpense.data.local.entity.club.MemberStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface MemberAccessDecision {
    data object Allowed : MemberAccessDecision
    data class Denied(val status: MemberStatus?, val message: String) : MemberAccessDecision
}

/**
 * 휴면 회원은 앱에 로그인하거나 해당 모임에 입장할 수 없다.
 * 탈퇴는 모임을 떠난 상태이므로 로그인은 허용하고, 해당 모임은 다시 가입 요청해야 한다.
 * 시스템관리자·개설자·지정 운영관리자는 회원 상태와 무관하게 허용한다.
 * 아직 회원 장부에 없는 신규 계정은 가입 요청을 위해 로그인을 허용한다.
 */
@Singleton
class MemberLoginPolicy @Inject constructor(
    private val meetingFirestoreRepository: MeetingFirestoreRepository,
    private val memberFirestoreRepository: MemberFirestoreRepository,
    private val userProfileFirestoreRepository: UserProfileFirestoreRepository,
    private val joinRequestFirestoreRepository: JoinRequestFirestoreRepository
) {
    suspend fun evaluateLogin(
        session: FirebaseAuthSession,
        failOpenOnError: Boolean = true
    ): MemberAccessDecision {
        if (isPrivilegedSession(session)) {
            return MemberAccessDecision.Allowed
        }
        return try {
            evaluateLoginInternal(session)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (failOpenOnError) {
                MemberAccessDecision.Allowed
            } else {
                MemberAccessDecision.Denied(
                    status = null,
                    message = "회원 상태를 확인하지 못했습니다. 네트워크 연결 후 다시 시도해 주세요."
                )
            }
        }
    }

    suspend fun evaluateEnterMeeting(
        session: FirebaseAuthSession,
        meeting: MeetingDoc
    ): MemberAccessDecision {
        if (isPrivilegedSession(session)) {
            return MemberAccessDecision.Allowed
        }
        if (isMeetingManager(meeting, session.uid)) {
            return MemberAccessDecision.Allowed
        }
        if (meeting.inactiveMemberUids.contains(session.uid)) {
            val reason = MemberStatusLabels.fromFirestore(
                meeting.inactiveMemberReasons[session.uid].orEmpty()
            )
            if (reason == MemberStatus.DORMANT) {
                return deniedForEnter(MemberStatus.DORMANT)
            }
            if (!meeting.sharedWith.contains(session.uid)) {
                return deniedForEnter(MemberStatus.WITHDRAWN)
            }
        }
        val mine = runCatching {
            memberFirestoreRepository.findMyMember(meeting.id, session.uid, session.email)
        }.getOrNull() ?: return MemberAccessDecision.Allowed
        return when (mine.statusEnum()) {
            MemberStatus.ACTIVE -> MemberAccessDecision.Allowed
            MemberStatus.DORMANT,
            MemberStatus.WITHDRAWN -> deniedForEnter(mine.statusEnum())
        }
    }

    suspend fun requireLogin(session: FirebaseAuthSession) {
        when (val decision = evaluateLogin(session, failOpenOnError = false)) {
            MemberAccessDecision.Allowed -> Unit
            is MemberAccessDecision.Denied ->
                throw InactiveMemberAccessException(decision.message)
        }
    }

    suspend fun requireEnterMeeting(session: FirebaseAuthSession, meeting: MeetingDoc) {
        when (val decision = evaluateEnterMeeting(session, meeting)) {
            MemberAccessDecision.Allowed -> Unit
            is MemberAccessDecision.Denied ->
                throw InactiveMemberAccessException(decision.message)
        }
    }

    suspend fun syncCloudAccess(meetingId: String, member: MemberDoc) {
        if (SystemAdminConfig.matches(member.linkedAccountUid(), member.email) ||
            PrivilegedAuthConfig.isPrivilegedEmail(member.email)
        ) {
            return
        }
        val meeting = meetingFirestoreRepository.getMeeting(meetingId) ?: return
        val uid = resolveAccountUid(meetingId, member) ?: return
        if (isMeetingManager(meeting, uid)) return
        if (member.linkedUid.isBlank()) {
            runCatching {
                memberFirestoreRepository.upsertMember(meetingId, member.copy(linkedUid = uid))
            }
        }
        val allowed = member.statusEnum() == MemberStatus.ACTIVE
        meetingFirestoreRepository.setRegularMemberAccess(
            meetingId = meetingId,
            memberUid = uid,
            allowed = allowed,
            inactiveReason = member.statusEnum()
        )
    }

    suspend fun revokeCloudAccess(meetingId: String, member: MemberDoc) {
        if (SystemAdminConfig.matches(member.linkedAccountUid(), member.email) ||
            PrivilegedAuthConfig.isPrivilegedEmail(member.email)
        ) {
            return
        }
        val meeting = meetingFirestoreRepository.getMeeting(meetingId) ?: return
        val uid = resolveAccountUid(meeting.id, member) ?: return
        if (isMeetingManager(meeting, uid)) return
        meetingFirestoreRepository.setRegularMemberAccess(
            meetingId = meetingId,
            memberUid = uid,
            allowed = false,
            inactiveReason = member.statusEnum()
        )
    }

    private suspend fun evaluateLoginInternal(session: FirebaseAuthSession): MemberAccessDecision {
        val uid = session.uid
        val owned = meetingFirestoreRepository.getMeetingsByOwner(uid)
        val administered = meetingFirestoreRepository.getMeetingsByAdmin(uid)
        if (owned.isNotEmpty() || administered.isNotEmpty()) {
            return MemberAccessDecision.Allowed
        }
        val blocked = meetingFirestoreRepository.getMeetingsByInactiveMember(uid)
            .filter { meeting ->
                MemberStatusLabels.fromFirestore(
                    meeting.inactiveMemberReasons[uid].orEmpty()
                ) == MemberStatus.DORMANT
            }
        val joined = meetingFirestoreRepository.getMeetingsBySharedWith(uid)
        val statuses = joined.mapNotNull { meeting ->
            runCatching { memberFirestoreRepository.getMembersOnce(meeting.id) }
                .getOrNull()
                ?.firstOrNull { it.matchesAccount(uid, session.email) }
                ?.statusEnum()
        }
        if (statuses.any { it == MemberStatus.ACTIVE }) {
            return MemberAccessDecision.Allowed
        }
        if (blocked.isNotEmpty() || statuses.any { it == MemberStatus.DORMANT }) {
            return deniedForLogin(MemberStatus.DORMANT)
        }
        return MemberAccessDecision.Allowed
    }

    private suspend fun resolveAccountUid(meetingId: String, member: MemberDoc): String? {
        if (PrivilegedAuthConfig.isPrivilegedEmail(member.email) ||
            SystemAdminConfig.isClubMembershipRecord(email = member.email, name = member.name)
        ) {
            return null
        }
        member.linkedAccountUid()?.let { return it }
        val email = member.email.trim()
        if (email.isNotBlank()) {
            runCatching { userProfileFirestoreRepository.findUidByEmail(email) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            runCatching { joinRequestFirestoreRepository.findAccountUid(meetingId, email) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private fun isPrivilegedSession(session: FirebaseAuthSession): Boolean =
        SystemAdminConfig.matches(session.uid, session.email) ||
            PrivilegedAuthConfig.isPrivilegedEmail(session.email)

    private fun isMeetingManager(meeting: MeetingDoc, uid: String): Boolean =
        meeting.ownerUid == uid ||
            (meeting.adminUid.isNotBlank() && meeting.adminUid == uid)

    private fun deniedForLogin(status: MemberStatus?): MemberAccessDecision.Denied {
        val message = when (status) {
            MemberStatus.DORMANT ->
                "휴면 회원은 로그인할 수 없습니다. 활동중으로 변경된 뒤 다시 시도해 주세요."
            MemberStatus.WITHDRAWN ->
                "탈퇴 회원은 로그인할 수 없습니다."
            MemberStatus.ACTIVE, null ->
                "활동중인 회원만 로그인할 수 있습니다."
        }
        return MemberAccessDecision.Denied(status = status, message = message)
    }

    private fun deniedForEnter(status: MemberStatus?): MemberAccessDecision.Denied {
        val message = when (status) {
            MemberStatus.DORMANT ->
                "휴면 회원은 이 모임에 입장할 수 없습니다."
            MemberStatus.WITHDRAWN ->
                "탈퇴 회원은 이 모임에 입장할 수 없습니다."
            MemberStatus.ACTIVE, null ->
                "활동중인 회원만 이 모임에 입장할 수 있습니다."
        }
        return MemberAccessDecision.Denied(status = status, message = message)
    }
}

class InactiveMemberAccessException(message: String) : IllegalStateException(message)
