package com.smartexpense.data.repository.club

import com.smartexpense.data.firebase.MemberLoginPolicy
import com.smartexpense.data.firebase.SystemAdminConfig
import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.firestore.JoinRequestFirestoreRepository
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberDoc
import com.smartexpense.data.firebase.firestore.MemberFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberStatusLabels
import com.smartexpense.data.firebase.firestore.UserProfileFirestoreRepository
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingMemberEnrollmentRepository @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val userProfileFirestoreRepository: UserProfileFirestoreRepository,
    private val memberFirestoreRepository: MemberFirestoreRepository,
    private val joinRequestFirestoreRepository: JoinRequestFirestoreRepository,
    private val meetingFirestoreRepository: MeetingFirestoreRepository,
    private val memberLoginPolicy: MemberLoginPolicy
) {
    suspend fun ensureOwnerRegistered(meetingId: String) {
        val session = authRepository.currentSession()
            ?: throw IllegalStateException("로그인이 필요합니다.")
        if (SystemAdminConfig.matches(session.uid, session.email)) return
        val existing = memberFirestoreRepository.getMembersOnce(meetingId)
        if (existing.any { memberMatchesUser(it, session.uid, session.email) }) return
        val profile = userProfileFirestoreRepository.getProfile(session.uid)
        val name = profile?.displayName?.trim().orEmpty()
            .ifBlank { session.displayName?.trim().orEmpty() }
            .ifBlank { session.email?.substringBefore('@').orEmpty() }
            .ifBlank { "개설자" }
        memberFirestoreRepository.upsertMember(
            meetingId,
            MemberDoc(
                id = session.uid,
                name = name,
                status = MemberStatusLabels.toFirestore(MemberStatus.ACTIVE),
                joinDate = LocalDate.now().toString(),
                phone = profile?.phone.orEmpty(),
                email = session.email.orEmpty(),
                role = MemberRole.PRESIDENT.name,
                linkedUid = session.uid
            )
        )
    }

    suspend fun pruneSystemAdminMembers(meetingId: String) {
        memberFirestoreRepository.pruneSystemAdminMembers(meetingId)
    }

    suspend fun registerSelfAsMember(
        meetingId: String,
        name: String,
        phone: String,
        email: String,
        joinDate: String,
        birthDate: String,
        isLunarBirth: Boolean,
        residenceRegion: String,
        address: String,
        detailAddress: String
    ) {
        val session = authRepository.currentSession()
            ?: throw IllegalStateException("로그인이 필요합니다.")
        if (SystemAdminConfig.matches(session.uid, session.email)) {
            throw IllegalStateException("시스템관리자는 모임 회원으로 등록되지 않습니다.")
        }
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        require(trimmedName.isNotEmpty()) { "이름을 입력해 주세요." }
        require(trimmedPhone.isNotEmpty()) { "핸드폰번호를 입력해 주세요." }
        val existing = memberFirestoreRepository.getMembersOnce(meetingId)
        val byAccount = existing.firstOrNull { it.matchesAccount(session.uid, session.email) }
        val byName = existing.firstOrNull { member ->
            member.name.trim().equals(trimmedName, ignoreCase = true)
        }
        // 동일 이름 명단이 다른 계정에 이미 연결돼 있으면 덮어쓰지 않음
        if (byName != null && byAccount == null) {
            val linked = byName.linkedUid.trim().ifBlank {
                byName.id.takeIf { MemberDoc.looksLikeFirebaseUid(it) }.orEmpty()
            }
            if (linked.isNotBlank() && linked != session.uid) {
                throw IllegalStateException(
                    "같은 이름「${byName.name}」의 회원 정보가 다른 계정에 이미 연결되어 있습니다. 관리자에게 문의하세요."
                )
            }
        }
        // 계정 일치 우선, 없으면 동일 이름 명단을 수정(업데이트). 둘 다 없으면 신규 등록.
        val target = byAccount ?: byName
        val saved = MemberDoc(
            id = target?.id?.takeIf { it.isNotBlank() } ?: session.uid,
            name = trimmedName,
            status = MemberStatusLabels.toFirestore(MemberStatus.ACTIVE),
            joinDate = joinDate.trim().ifBlank { target?.joinDate.orEmpty() },
            phone = trimmedPhone,
            birthDate = birthDate.trim().ifBlank { target?.birthDate.orEmpty() },
            address = address.trim().ifBlank { target?.address.orEmpty() },
            detailAddress = detailAddress.trim().ifBlank { target?.detailAddress.orEmpty() },
            residenceRegion = residenceRegion.trim().ifBlank { target?.residenceRegion.orEmpty() },
            email = email.trim().ifBlank { session.email.orEmpty().ifBlank { target?.email.orEmpty() } },
            role = target?.role?.takeIf { it.isNotBlank() } ?: MemberRole.GENERAL.name,
            isLunarBirth = isLunarBirth,
            linkedUid = session.uid,
            selfEnrolled = true
        )
        memberFirestoreRepository.upsertMember(meetingId, saved)
        runCatching { memberLoginPolicy.syncCloudAccess(meetingId, saved) }
        runCatching { joinRequestFirestoreRepository.markMyProfileCompleted(meetingId) }
    }

    suspend fun hasSelfMemberRecord(meetingId: String): Boolean {
        val session = authRepository.currentSession() ?: return false
        return memberFirestoreRepository.getMembersOnce(meetingId)
            .any { it.matchesAccount(session.uid, session.email) }
    }

    private fun memberMatchesUser(
        member: MemberDoc,
        uid: String,
        email: String?
    ): Boolean = member.matchesAccount(uid, email)
}
