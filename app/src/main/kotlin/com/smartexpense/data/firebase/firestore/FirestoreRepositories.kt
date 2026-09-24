package com.smartexpense.data.firebase.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.smartexpense.data.firebase.FirebaseInitializer
import com.smartexpense.data.firebase.PrivilegedAuthConfig
import com.smartexpense.data.firebase.SystemAdminConfig
import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.auth.FirebaseAuthSession
import com.smartexpense.data.local.ClubConstants
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.MemberStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

sealed interface MeetingInviteResult {
    data class Joined(val uid: String) : MeetingInviteResult
    data class PendingEmail(val email: String) : MeetingInviteResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MeetingFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    /** 로그인 사용자: 전체 모임 목록 (검색·탐색) */
    fun observeAllMeetings(): Flow<List<MeetingDoc>> =
        authRepository.authState.flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                observeQuery(db.collection(FirestorePaths.meetings()))
            }
        }

    /** 선택된 모임 문서. 역할(개설자/운영관리자) 판별에 사용합니다. */
    fun observeMeeting(meetingId: String): Flow<MeetingDoc?> {
        if (meetingId.isBlank()) return flowOf(null)
        return callbackFlow {
            val registration: ListenerRegistration =
                db.document(FirestorePaths.meeting(meetingId))
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(null)
                            return@addSnapshotListener
                        }
                        val meeting = snapshot?.takeIf { it.exists() }?.let { MeetingDoc.from(it) }
                        trySend(meeting)
                    }
            awaitClose { registration.remove() }
        }
    }

    /** 가입(승인)된 모임 + 내가 만든 모임 */
    fun observeMeetings(): Flow<List<MeetingDoc>> = observeJoinedMeetings()

    fun observeJoinedMeetings(): Flow<List<MeetingDoc>> =
        authRepository.authState.flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else if (isElevated(session)) {
                observeQuery(db.collection(FirestorePaths.meetings()))
            } else {
                val col = db.collection(FirestorePaths.meetings())
                combine(
                    observeQuery(col.whereArrayContains("sharedWith", session.uid)),
                    observeQuery(col.whereEqualTo("ownerUid", session.uid))
                ) { shared, owned ->
                    (shared + owned)
                        .distinctBy { it.id }
                        .filter { meeting ->
                            meeting.ownerUid == session.uid ||
                                meeting.adminUid == session.uid ||
                                session.uid !in meeting.inactiveMemberUids
                        }
                        .sortedBy { it.name }
                }
            }
        }

    fun observeInactiveMeetings(): Flow<List<MeetingDoc>> =
        authRepository.authState.flatMapLatest { session ->
            if (session == null || isElevated(session)) {
                flowOf(emptyList())
            } else {
                observeQuery(
                    db.collection(FirestorePaths.meetings())
                        .whereArrayContains("inactiveMemberUids", session.uid)
                )
            }
        }

    private fun isElevated(session: FirebaseAuthSession): Boolean =
        PrivilegedAuthConfig.isPrivilegedAccount(session.uid, session.email)

    private fun observeQuery(query: Query): Flow<List<MeetingDoc>> = callbackFlow {
        val registration: ListenerRegistration =
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val meetings = snapshot?.documents?.map { MeetingDoc.from(it) }.orEmpty()
                trySend(meetings.sortedBy { it.name })
            }
        awaitClose { registration.remove() }
    }

    suspend fun getAllMeetingsOnce(fromServer: Boolean = false): List<MeetingDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.meetings()).get(querySource(fromServer)).await()
        return snapshot.documents.map { MeetingDoc.from(it) }.sortedBy { it.name }
    }

    /**
     * 모든 로그인 사용자가 검색에 쓰는 공개 디렉터리.
     * meetings 컬렉션 전체 list 권한/캐시 이슈와 무관하게 이름을 노출합니다.
     */
    suspend fun getSearchableMeetingsOnce(fromServer: Boolean = false): List<MeetingDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.meetingDirectory())
            .get(querySource(fromServer))
            .await()
        return snapshot.documents.map { directoryToMeeting(it) }.sortedBy { it.name }
    }

    /** 검색용 디렉터리 실시간 구독. 가입 모임 목록과 분리해 덮어쓰기를 막습니다. */
    fun observeMeetingDirectory(): Flow<List<MeetingDoc>> =
        authRepository.authState.flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val registration: ListenerRegistration =
                        db.collection(FirestorePaths.meetingDirectory())
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    trySend(emptyList())
                                    return@addSnapshotListener
                                }
                                val meetings = snapshot?.documents
                                    ?.map { directoryToMeeting(it) }
                                    .orEmpty()
                                    .sortedBy { it.name }
                                trySend(meetings)
                            }
                    awaitClose { registration.remove() }
                }
            }
        }

    suspend fun publishMeetingDirectory(meeting: MeetingDoc) {
        if (meeting.id.isBlank()) return
        authRepository.requireUid()
        db.document(FirestorePaths.meetingDirectoryEntry(meeting.id))
            .set(
                mapOf(
                    "name" to meeting.name,
                    "description" to meeting.description,
                    "slogan" to meeting.slogan,
                    "createdAt" to meeting.createdAt,
                    "ownerUid" to meeting.ownerUid,
                    "adminUid" to meeting.adminUid
                ),
                SetOptions.merge()
            )
            .await()
    }

    /** 시스템관리자: meetings → meetingDirectory 동기화 (검색에 한우리 등이 보이게) */
    suspend fun syncMeetingDirectoryFromMeetings(): Int {
        val meetings = getAllMeetingsOnce()
        meetings.forEach { publishMeetingDirectory(it) }
        return meetings.size
    }

    private fun directoryToMeeting(snapshot: com.google.firebase.firestore.DocumentSnapshot): MeetingDoc =
        MeetingDoc(
            id = snapshot.id,
            name = snapshot.getString("name").orEmpty(),
            description = snapshot.getString("description").orEmpty(),
            slogan = snapshot.getString("slogan").orEmpty(),
            createdAt = snapshot.readCreatedAtString(),
            ownerUid = snapshot.getString("ownerUid").orEmpty(),
            adminUid = snapshot.getString("adminUid").orEmpty(),
            sharedWith = emptyList()
        )

    fun matchesSearch(meeting: MeetingDoc, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (meeting.name.contains(q, ignoreCase = true)) return true
        if (meeting.description.contains(q, ignoreCase = true)) return true
        if (meeting.slogan.contains(q, ignoreCase = true)) return true
        // 「한우리」검색 시 「한우리(기본 모임)」등 시드 이름도 매칭
        if (ClubConstants.isHanuriSeedClub(q) || q == ClubConstants.HANURI_SEED_CLUB_NAME) {
            return ClubConstants.isHanuriSeedClub(meeting.name)
        }
        return false
    }

    suspend fun getMeetingsOnce(fromServer: Boolean = false): List<MeetingDoc> {
        val session = authRepository.currentSession()
            ?: throw IllegalStateException("로그인이 필요합니다.")
        if (isElevated(session)) {
            return getAllMeetingsOnce(fromServer = fromServer)
        }
        val col = db.collection(FirestorePaths.meetings())
        val source = querySource(fromServer)
        val shared = col.whereArrayContains("sharedWith", session.uid).get(source).await()
            .documents.map { MeetingDoc.from(it) }
        val owned = col.whereEqualTo("ownerUid", session.uid).get(source).await()
            .documents.map { MeetingDoc.from(it) }
        return (shared + owned)
            .distinctBy { it.id }
            .filter { meeting ->
                meeting.ownerUid == session.uid ||
                    meeting.adminUid == session.uid ||
                    session.uid !in meeting.inactiveMemberUids
            }
            .sortedBy { it.name }
    }

    suspend fun createMeeting(
        name: String,
        description: String,
        slogan: String = ""
    ): MeetingDoc {
        val uid = authRepository.requireUid()
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "모임 이름을 입력해 주세요." }
        val docRef = db.collection(FirestorePaths.meetings()).document()
        val now = java.time.LocalDate.now().toString()
        val meeting = MeetingDoc(
            id = docRef.id,
            name = trimmedName,
            description = description.trim(),
            slogan = slogan.trim(),
            createdAt = now,
            ownerUid = uid,
            adminUid = "",
            sharedWith = listOf(uid),
            duesPaymentMethod = DuesPaymentMethod.DEFAULT.name
        )
        docRef.set(meeting.toMap()).await()
        // 서버에 반영된 뒤에야 다른 계정이 검색할 수 있습니다.
        docRef.get(Source.SERVER).await()
        publishMeetingDirectory(meeting)
        return meeting
    }

    suspend fun addMemberToSharedWith(meetingId: String, memberUid: String) {
        authRepository.requireUid()
        require(memberUid.isNotBlank()) { "추가할 사용자 UID가 비어 있습니다." }
        db.document(FirestorePaths.meeting(meetingId))
            .update(
                mapOf(
                    "sharedWith" to FieldValue.arrayUnion(memberUid),
                    "inactiveMemberUids" to FieldValue.arrayRemove(memberUid),
                    "inactiveMemberReasons.$memberUid" to FieldValue.delete()
                )
            )
            .await()
    }

    /**
     * 일반 회원의 클라우드 접근을 상태(활동중/휴면·탈퇴)에 맞춰 맞춘다.
     * 개설자·지정 운영관리자 UID는 변경하지 않는다.
     */
    suspend fun setRegularMemberAccess(
        meetingId: String,
        memberUid: String,
        allowed: Boolean,
        inactiveReason: MemberStatus? = null
    ) {
        authRepository.requireUid()
        require(memberUid.isNotBlank()) { "사용자 UID가 비어 있습니다." }
        val meeting = getMeeting(meetingId) ?: return
        if (meeting.ownerUid == memberUid) return
        if (meeting.adminUid.isNotBlank() && meeting.adminUid == memberUid) return
        val updates: Map<String, Any> = if (allowed) {
            mapOf(
                "sharedWith" to FieldValue.arrayUnion(memberUid),
                "inactiveMemberUids" to FieldValue.arrayRemove(memberUid),
                "inactiveMemberReasons.$memberUid" to FieldValue.delete()
            )
        } else {
            val reason = when (inactiveReason) {
                MemberStatus.WITHDRAWN -> MemberStatus.WITHDRAWN.name
                else -> MemberStatus.DORMANT.name
            }
            mapOf(
                "sharedWith" to FieldValue.arrayRemove(memberUid),
                "inactiveMemberUids" to FieldValue.arrayUnion(memberUid),
                "inactiveMemberReasons.$memberUid" to reason
            )
        }
        db.document(FirestorePaths.meeting(meetingId)).update(updates).await()
    }

    suspend fun getMeetingsByOwner(uid: String): List<MeetingDoc> =
        getMeetingsWhereEqualTo("ownerUid", uid)

    suspend fun getMeetingsByAdmin(uid: String): List<MeetingDoc> =
        getMeetingsWhereEqualTo("adminUid", uid)

    suspend fun getMeetingsBySharedWith(uid: String): List<MeetingDoc> =
        getMeetingsWhereArrayContains("sharedWith", uid)

    suspend fun getMeetingsByInactiveMember(uid: String): List<MeetingDoc> =
        getMeetingsWhereArrayContains("inactiveMemberUids", uid)

    private suspend fun getMeetingsWhereEqualTo(
        field: String,
        value: String,
        fromServer: Boolean = false
    ): List<MeetingDoc> {
        if (value.isBlank()) return emptyList()
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.meetings())
            .whereEqualTo(field, value)
            .get(querySource(fromServer))
            .await()
        return snapshot.documents.map { MeetingDoc.from(it) }
    }

    private suspend fun getMeetingsWhereArrayContains(
        field: String,
        value: String,
        fromServer: Boolean = false
    ): List<MeetingDoc> {
        if (value.isBlank()) return emptyList()
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.meetings())
            .whereArrayContains(field, value)
            .get(querySource(fromServer))
            .await()
        return snapshot.documents.map { MeetingDoc.from(it) }
    }

    private fun querySource(fromServer: Boolean): Source =
        if (fromServer) Source.SERVER else Source.DEFAULT

    suspend fun getMeeting(meetingId: String): MeetingDoc? {
        val snapshot = db.document(FirestorePaths.meeting(meetingId)).get().await()
        return if (snapshot.exists()) MeetingDoc.from(snapshot) else null
    }

    suspend fun upsertMeeting(meeting: MeetingDoc): String {
        val uid = authRepository.requireUid()
        val col = db.collection(FirestorePaths.meetings())
        val docRef = if (meeting.id.isBlank()) col.document() else col.document(meeting.id)
        val existing = if (meeting.id.isNotBlank()) {
            runCatching { docRef.get().await() }.getOrNull()
        } else {
            null
        }
        val existingShared = (existing?.get("sharedWith") as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty()
        val sharedWith = when {
            meeting.sharedWith.isNotEmpty() -> meeting.sharedWith
            existingShared.isNotEmpty() -> existingShared
            else -> listOf(uid)
        }.distinct()
        val ownerUid = meeting.ownerUid.ifBlank {
            existing?.getString("ownerUid") ?: uid
        }
        // 지정 운영관리자는 시스템관리자가 명시 설정. 신규 시 owner 로 자동 채우지 않음.
        val adminUid = meeting.adminUid.ifBlank {
            existing?.getString("adminUid").orEmpty()
        }
        val saved = meeting.copy(
            id = docRef.id,
            ownerUid = ownerUid,
            adminUid = adminUid,
            sharedWith = sharedWith
        )
        docRef.set(saved.toMap(), SetOptions.merge()).await()
        publishMeetingDirectory(saved)
        return docRef.id
    }

    /**
     * meetings/{meetingId} 문서의 currentBalance·initialBalance를 강제로 덮어씁니다.
     */
    suspend fun updateBalanceFields(
        meetingId: String,
        currentBalance: Long,
        initialBalance: Long
    ) {
        authRepository.requireUid()
        db.document(FirestorePaths.meeting(meetingId))
            .set(
                mapOf(
                    "currentBalance" to currentBalance,
                    "initialBalance" to initialBalance
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun updateDuesPaymentMethod(meetingId: String, method: String) {
        authRepository.requireUid()
        require(method.isNotBlank()) { "회비 납부 방식을 선택해 주세요." }
        db.document(FirestorePaths.meeting(meetingId))
            .set(mapOf("duesPaymentMethod" to method), SetOptions.merge())
            .await()
    }

    /** 모임관리자(개설자) 또는 시스템관리자가 지정 운영관리자를 설정합니다. */
    suspend fun setDesignatedTreasurer(meetingId: String, treasurerUid: String) {
        val uid = authRepository.requireUid()
        require(treasurerUid.isNotBlank()) { "운영관리자로 지정할 계정 UID가 비어 있습니다." }
        val meeting = getMeeting(meetingId)
            ?: throw IllegalStateException("모임을 찾을 수 없습니다.")
        val shared = meeting.sharedWith.toMutableList()
        if (!shared.contains(treasurerUid)) {
            shared += treasurerUid
        }
        db.document(FirestorePaths.meeting(meetingId))
            .set(
                mapOf(
                    "adminUid" to treasurerUid,
                    "sharedWith" to shared.distinct(),
                    "updatedBy" to uid
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun clearDesignatedTreasurer(meetingId: String) {
        authRepository.requireUid()
        db.document(FirestorePaths.meeting(meetingId))
            .set(
                mapOf("adminUid" to ""),
                SetOptions.merge()
            )
            .await()
    }

    /** 본인이 모임에서 나가 접근을 끊는다. 탈퇴 시 사용 중지 목록에 남긴다. */
    suspend fun removeSelfAccess(
        meetingId: String,
        markInactive: Boolean
    ) {
        val uid = authRepository.requireUid()
        val meeting = getMeeting(meetingId) ?: return
        if (meeting.ownerUid == uid) {
            throw IllegalStateException("개설자는 모임에서 탈퇴할 수 없습니다.")
        }
        if (meeting.adminUid == uid) {
            throw IllegalStateException("지정 운영관리자는 모임에서 탈퇴할 수 없습니다. 먼저 지정 해제가 필요합니다.")
        }
        val updates = if (markInactive) {
            mapOf(
                "sharedWith" to FieldValue.arrayRemove(uid),
                "inactiveMemberUids" to FieldValue.arrayUnion(uid),
                "inactiveMemberReasons.$uid" to MemberStatus.WITHDRAWN.name
            )
        } else {
            mapOf(
                "sharedWith" to FieldValue.arrayRemove(uid),
                "inactiveMemberUids" to FieldValue.arrayRemove(uid),
                "inactiveMemberReasons.$uid" to FieldValue.delete()
            )
        }
        db.document(FirestorePaths.meeting(meetingId)).update(updates).await()
    }

    /** 관리자가 회원을 강제 탈퇴할 때 접근·제한 목록에서 완전히 제거한다. */
    suspend fun purgeMemberAccess(meetingId: String, memberUid: String) {
        authRepository.requireUid()
        require(memberUid.isNotBlank()) { "사용자 UID가 비어 있습니다." }
        val meeting = getMeeting(meetingId) ?: return
        if (meeting.ownerUid == memberUid) {
            throw IllegalStateException("개설자는 강제 탈퇴할 수 없습니다.")
        }
        if (meeting.adminUid.isNotBlank() && meeting.adminUid == memberUid) {
            throw IllegalStateException("지정 운영관리자는 강제 탈퇴할 수 없습니다. 먼저 지정 해제가 필요합니다.")
        }
        db.document(FirestorePaths.meeting(meetingId))
            .update(
                mapOf(
                    "sharedWith" to FieldValue.arrayRemove(memberUid),
                    "inactiveMemberUids" to FieldValue.arrayRemove(memberUid),
                    "inactiveMemberReasons.$memberUid" to FieldValue.delete()
                )
            )
            .await()
    }

    suspend fun inviteUid(meetingId: String, inviteeUid: String) {
        authRepository.requireUid()
        require(inviteeUid.isNotBlank()) { "초대할 사용자 UID가 비어 있습니다." }
        db.document(FirestorePaths.meeting(meetingId))
            .update(
                mapOf(
                    "sharedWith" to FieldValue.arrayUnion(inviteeUid),
                    "inactiveMemberUids" to FieldValue.arrayRemove(inviteeUid),
                    "inactiveMemberReasons.$inviteeUid" to FieldValue.delete()
                )
            )
            .await()
    }

    /**
     * 이메일 또는 UID로 초대.
     * - 로그인 이력이 있으면 sharedWith에 즉시 추가하고 명단 linkedUid를 연결
     * - 명단에만 있고 아직 미로그인인 기존 회원이면 invitedEmails에 예약
     */
    suspend fun inviteByEmailOrUid(meetingId: String, rawInput: String): MeetingInviteResult {
        authRepository.requireUid()
        val raw = rawInput.trim()
        require(raw.isNotBlank()) { "이메일 또는 UID를 입력해 주세요." }
        val myUid = authRepository.requireUid()

        if (!raw.contains('@')) {
            require(raw != myUid) { "본인은 이미 이 모임에 포함되어 있습니다." }
            inviteUid(meetingId, raw)
            linkMemberAccountIfPresent(meetingId, uid = raw, email = null)
            return MeetingInviteResult.Joined(raw)
        }

        val email = raw.lowercase()
        require(email.contains('@')) { "이메일 형식이 올바르지 않습니다." }

        val profileUid = findUidByEmailInProfiles(email)
        val member = findMemberByEmail(meetingId, email)
        val memberUid = member?.linkedAccountUid()
        val joinUid = findUidInJoinRequests(meetingId, email)
        val inviteeUid = profileUid ?: memberUid ?: joinUid

        if (!inviteeUid.isNullOrBlank()) {
            require(inviteeUid != myUid) { "본인은 이미 이 모임에 포함되어 있습니다." }
            inviteUid(meetingId, inviteeUid)
            linkMemberAccountIfPresent(meetingId, uid = inviteeUid, email = email)
            db.document(FirestorePaths.meeting(meetingId))
                .update("invitedEmails", FieldValue.arrayRemove(email))
                .await()
            return MeetingInviteResult.Joined(inviteeUid)
        }

        if (member != null) {
            db.document(FirestorePaths.meeting(meetingId))
                .update("invitedEmails", FieldValue.arrayUnion(email))
                .await()
            return MeetingInviteResult.PendingEmail(email)
        }

        throw IllegalStateException(
            "해당 이메일의 사용자를 찾지 못했습니다. 명단에 있는 기존 회원이거나, 상대가 앱에 한 번 로그인한 뒤 다시 초대해 주세요."
        )
    }

    /** 로그인 계정이 invitedEmails에 있으면 sharedWith로 합류 */
    suspend fun claimPendingEmailInvites(uid: String, email: String?): Int {
        val normalized = email?.trim()?.lowercase().orEmpty()
        if (uid.isBlank() || normalized.isBlank()) return 0
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.meetings())
            .whereArrayContains("invitedEmails", normalized)
            .get()
            .await()
        var claimed = 0
        snapshot.documents.forEach { doc ->
            runCatching {
                doc.reference.update(
                    mapOf(
                        "sharedWith" to FieldValue.arrayUnion(uid),
                        "invitedEmails" to FieldValue.arrayRemove(normalized),
                        "inactiveMemberUids" to FieldValue.arrayRemove(uid),
                        "inactiveMemberReasons.$uid" to FieldValue.delete()
                    )
                ).await()
                linkMemberAccountIfPresent(doc.id, uid = uid, email = normalized)
                claimed += 1
            }
        }
        return claimed
    }

    private suspend fun findUidByEmailInProfiles(email: String): String? {
        val snapshot = db.collection(FirestorePaths.USER_PROFILES)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        return doc.getString("uid") ?: doc.id
    }

    private suspend fun findMemberByEmail(meetingId: String, email: String): MemberDoc? {
        val snapshot = db.collection(FirestorePaths.members(meetingId)).get().await()
        return snapshot.documents.map { MemberDoc.from(it) }
            .withoutSystemAdminMembership()
            .firstOrNull { it.email.trim().equals(email, ignoreCase = true) }
    }

    private suspend fun findUidInJoinRequests(meetingId: String, email: String): String? {
        val snapshot = db.collection(FirestorePaths.joinRequests(meetingId)).get().await()
        return snapshot.documents.map { JoinRequestDoc.from(it) }
            .firstOrNull { it.email.trim().equals(email, ignoreCase = true) && it.uid.isNotBlank() }
            ?.uid
    }

    private suspend fun linkMemberAccountIfPresent(
        meetingId: String,
        uid: String,
        email: String?
    ) {
        val col = db.collection(FirestorePaths.members(meetingId))
        val byUid = runCatching { col.document(uid).get().await() }.getOrNull()
        if (byUid != null && byUid.exists()) {
            col.document(uid).set(mapOf("linkedUid" to uid), SetOptions.merge()).await()
            return
        }
        val linkedSnap = runCatching {
            col.whereEqualTo("linkedUid", uid).limit(1).get().await()
        }.getOrNull()
        if (linkedSnap != null && !linkedSnap.isEmpty) return
        val normalized = email?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return
        val candidates = listOf(normalized, email?.trim().orEmpty()).filter { it.isNotBlank() }.distinct()
        candidates.forEach { candidate ->
            val snap = runCatching {
                col.whereEqualTo("email", candidate).limit(1).get().await()
            }.getOrNull() ?: return@forEach
            val doc = snap.documents.firstOrNull() ?: return@forEach
            doc.reference.set(mapOf("linkedUid" to uid), SetOptions.merge()).await()
            return
        }
    }

    suspend fun deleteMeeting(meetingId: String) {
        authRepository.requireUid()
        deleteDirectoryEntry(meetingId)
        db.document(FirestorePaths.meeting(meetingId)).delete().await()
        deleteDirectoryEntry(meetingId)
    }

    suspend fun deleteMeetingWithContents(meetingId: String) {
        authRepository.requireUid()
        // 디렉터리를 모임 문서보다 먼저 지운다. 모임이 없어지면
        // owner 검사가 실패해 검색 목록에 유령 모임이 남는다.
        deleteDirectoryEntry(meetingId)
        deleteCollection(FirestorePaths.members(meetingId))
        deleteCollection(FirestorePaths.transactions(meetingId))
        deleteCollection(FirestorePaths.dues(meetingId))
        deleteCollection(FirestorePaths.accounts(meetingId))
        deleteCollection(FirestorePaths.eventExpenses(meetingId))
        deleteCollection(FirestorePaths.histories(meetingId))
        deleteCollection(FirestorePaths.joinRequests(meetingId))
        deleteCollection(FirestorePaths.security(meetingId))
        db.document(FirestorePaths.meeting(meetingId)).delete().await()
        deleteDirectoryEntry(meetingId)
    }

    /** 모임 문서는 없고 검색 디렉터리만 남은 항목을 정리합니다. */
    suspend fun pruneOrphanDirectoryEntries() {
        authRepository.requireUid()
        val directory = getSearchableMeetingsOnce()
        directory.forEach { dir ->
            val meeting = getMeeting(dir.id)
            if (meeting == null) {
                deleteDirectoryEntry(dir.id)
            }
        }
    }

    private suspend fun deleteDirectoryEntry(meetingId: String) {
        runCatching {
            db.document(FirestorePaths.meetingDirectoryEntry(meetingId)).delete().await()
        }
    }

    private suspend fun deleteCollection(path: String) {
        val snapshot = db.collection(path).get().await()
        snapshot.documents.forEach { doc ->
            doc.reference.delete().await()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class JoinRequestFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository,
    private val meetingFirestoreRepository: MeetingFirestoreRepository,
    private val memberFirestoreRepository: MemberFirestoreRepository,
    private val userProfileFirestoreRepository: UserProfileFirestoreRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    fun observeMyJoinRequests(): Flow<List<Pair<String, JoinRequestDoc>>> =
        authRepository.authState.flatMapLatest { session ->
            val uid = session?.uid
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val registration = db.collectionGroup(FirestorePaths.JOIN_REQUESTS)
                        .whereEqualTo("uid", uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                trySend(emptyList())
                                return@addSnapshotListener
                            }
                            val items = snapshot?.documents?.map { doc ->
                                val meetingId = doc.reference.parent.parent?.id.orEmpty()
                                meetingId to JoinRequestDoc.from(doc)
                            }.orEmpty()
                            trySend(items)
                        }
                    awaitClose { registration.remove() }
                }
            }
        }

    fun observePendingJoinRequests(meetingId: String): Flow<List<JoinRequestDoc>> = callbackFlow {
        authRepository.requireUid()
        val registration = db.collection(FirestorePaths.joinRequests(meetingId))
            .whereEqualTo("status", JoinRequestStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { JoinRequestDoc.from(it) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun getMyJoinRequestsOnce(
        meetingIds: List<String> = emptyList(),
        fromServer: Boolean = false
    ): Map<String, JoinRequestDoc> {
        val uid = authRepository.currentSession()?.uid ?: return emptyMap()
        val source = if (fromServer) Source.SERVER else Source.DEFAULT
        val grouped = runCatching {
            val snapshot = runCatching {
                db.collectionGroup(FirestorePaths.JOIN_REQUESTS)
                    .whereEqualTo("uid", uid)
                    .get(source)
                    .await()
            }.getOrElse {
                if (!fromServer) throw it
                db.collectionGroup(FirestorePaths.JOIN_REQUESTS)
                    .whereEqualTo("uid", uid)
                    .get(Source.DEFAULT)
                    .await()
            }
            snapshot.documents.mapNotNull { doc ->
                val meetingId = doc.reference.parent.parent?.id.orEmpty()
                if (meetingId.isBlank()) return@mapNotNull null
                if (!fromServer && meetingIds.isNotEmpty() && meetingId !in meetingIds) {
                    return@mapNotNull null
                }
                meetingId to JoinRequestDoc.from(doc)
            }.groupBy({ it.first }, { it.second })
        }.getOrDefault(emptyMap())
        val picked = grouped.mapNotNull { (meetingId, docs) ->
            pickPreferredJoinRequest(docs)?.let { meetingId to it }
        }.toMap().toMutableMap()
        val confirmIds = meetingIds.filter { it.isNotBlank() }.distinct()
        val idsToConfirm = if (fromServer) confirmIds else confirmIds.filter { it !in picked }
        idsToConfirm.forEach { meetingId ->
            val result = runCatching { getMyJoinRequest(meetingId, uid, fromServer) }
            result.onSuccess { request ->
                if (request != null) {
                    picked[meetingId] = pickPreferredJoinRequest(
                        listOfNotNull(picked[meetingId], request)
                    ) ?: request
                } else if (fromServer) {
                    picked.remove(meetingId)
                }
            }
        }
        return picked
    }

    suspend fun getMyJoinRequest(
        meetingId: String,
        uid: String,
        fromServer: Boolean = false
    ): JoinRequestDoc? {
        val col = db.collection(FirestorePaths.joinRequests(meetingId)).whereEqualTo("uid", uid)
        val snapshot = runCatching {
            col.get(if (fromServer) Source.SERVER else Source.DEFAULT).await()
        }.getOrElse {
            if (!fromServer) throw it
            col.get(Source.DEFAULT).await()
        }
        return pickPreferredJoinRequest(snapshot.documents.map { JoinRequestDoc.from(it) })
    }

    private fun pickPreferredJoinRequest(docs: List<JoinRequestDoc>): JoinRequestDoc? {
        if (docs.isEmpty()) return null
        return docs.firstOrNull { it.needsMemberProfile() }
            ?: docs.firstOrNull { it.status == JoinRequestStatus.PENDING }
            ?: docs.maxByOrNull { it.requestedAt }
            ?: docs.last()
    }

    suspend fun getPendingJoinRequestsOnce(meetingId: String): List<JoinRequestDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.joinRequests(meetingId))
            .whereEqualTo("status", JoinRequestStatus.PENDING.name)
            .get()
            .await()
        return snapshot.documents.map { JoinRequestDoc.from(it) }
    }

    suspend fun getAllPendingJoinRequestsOnce(): List<Pair<String, JoinRequestDoc>> {
        authRepository.requireUid()
        val snapshot = db.collectionGroup(FirestorePaths.JOIN_REQUESTS)
            .whereEqualTo("status", JoinRequestStatus.PENDING.name)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val meetingId = doc.reference.parent.parent?.id.orEmpty()
            if (meetingId.isBlank()) return@mapNotNull null
            meetingId to JoinRequestDoc.from(doc)
        }
    }

    suspend fun submitJoinRequest(meetingId: String, message: String = "") {
        val session = authRepository.currentSession()
            ?: throw IllegalStateException("로그인이 필요합니다.")
        val uid = session.uid
        val meeting = meetingFirestoreRepository.getMeeting(meetingId)
            ?: throw IllegalStateException("모임을 찾을 수 없습니다.")
        if (meeting.sharedWith.contains(uid) || meeting.ownerUid == uid) {
            throw IllegalStateException("이미 가입된 모임입니다.")
        }
        if (uid in meeting.inactiveMemberUids) {
            throw IllegalStateException("휴면 또는 탈퇴 회원입니다. 관리자가 활동중으로 변경해야 이용할 수 있습니다.")
        }
        val existingMember = runCatching {
            memberFirestoreRepository.getMembersOnce(meetingId)
        }.getOrDefault(emptyList()).firstOrNull {
            it.matchesAccount(uid, session.email)
        }
        if (existingMember != null && existingMember.statusEnum() != MemberStatus.ACTIVE) {
            throw IllegalStateException("휴면 또는 탈퇴 회원입니다. 관리자가 활동중으로 변경해야 이용할 수 있습니다.")
        }
        val existing = getMyJoinRequest(meetingId, uid)
        if (existing?.status == JoinRequestStatus.PENDING) {
            throw IllegalStateException("이미 가입 요청을 보냈습니다.")
        }
        if (existing?.status == JoinRequestStatus.APPROVED) {
            if (!existing.profileCompleted) {
                throw IllegalStateException("가입이 승인되었습니다. 회원 정보를 등록해 주세요.")
            }
            throw IllegalStateException("이미 승인된 모임입니다.")
        }
        val profile = userProfileFirestoreRepository.getProfile(uid)
        val now = java.time.Instant.now().toString()
        val col = db.collection(FirestorePaths.joinRequests(meetingId))
        val payload = JoinRequestDoc(
            id = existing?.id.orEmpty(),
            uid = uid,
            email = session.email.orEmpty(),
            displayName = profile?.displayName.orEmpty(),
            phone = profile?.phone.orEmpty(),
            message = message.trim(),
            status = JoinRequestStatus.PENDING,
            requestedAt = now,
            decidedAt = "",
            decidedBy = ""
        )
        try {
            if (existing?.status == JoinRequestStatus.REJECTED ||
                existing?.status == JoinRequestStatus.WITHDRAWN
            ) {
                val docRef = col.document(existing.id)
                docRef.set(payload.copy(id = docRef.id).toMap()).await()
            } else {
                val docRef = col.document()
                docRef.set(payload.copy(id = docRef.id).toMap()).await()
            }
        } catch (error: Throwable) {
            val detail = error.message.orEmpty()
            if (detail.contains("PERMISSION", ignoreCase = true) ||
                detail.contains("permission", ignoreCase = true)
            ) {
                throw IllegalStateException(
                    "가입 재요청 권한이 없습니다. Firestore 규칙 배포 후 다시 시도해 주세요."
                )
            }
            throw error
        }
    }

    suspend fun approveJoinRequest(meetingId: String, requestId: String) {
        val uid = authRepository.requireUid()
        val docRef = db.collection(FirestorePaths.joinRequests(meetingId)).document(requestId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) throw IllegalStateException("가입 요청을 찾을 수 없습니다.")
        val request = JoinRequestDoc.from(snapshot)
        if (request.status != JoinRequestStatus.PENDING) {
            throw IllegalStateException("이미 처리된 요청입니다.")
        }
        val now = java.time.Instant.now().toString()
        docRef.update(
            mapOf(
                "status" to JoinRequestStatus.APPROVED.name,
                "decidedAt" to now,
                "decidedBy" to uid,
                "profileCompleted" to false
            )
        ).await()
        val accessUid = runCatching {
            memberFirestoreRepository.getMembersOnce(meetingId)
        }.getOrDefault(emptyList()).firstOrNull {
            it.matchesAccount(request.uid, request.email)
        }?.linkedAccountUid() ?: request.uid
        meetingFirestoreRepository.setRegularMemberAccess(
            meetingId = meetingId,
            memberUid = accessUid,
            allowed = true
        )
        meetingFirestoreRepository.addMemberToSharedWith(meetingId, request.uid)
    }

    suspend fun markProfileCompleted(meetingId: String, requestId: String) {
        val uid = authRepository.requireUid()
        val docRef = db.collection(FirestorePaths.joinRequests(meetingId)).document(requestId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) throw IllegalStateException("가입 요청을 찾을 수 없습니다.")
        val request = JoinRequestDoc.from(snapshot)
        if (request.uid != uid) {
            throw IllegalStateException("본인 가입 요청만 완료할 수 있습니다.")
        }
        if (request.status != JoinRequestStatus.APPROVED) {
            throw IllegalStateException("승인된 가입 요청만 회원 정보를 등록할 수 있습니다.")
        }
        if (request.profileCompleted) return
        val now = java.time.Instant.now().toString()
        docRef.update(
            mapOf(
                "profileCompleted" to true,
                "profileCompletedAt" to now
            )
        ).await()
    }

    suspend fun markMyProfileCompleted(meetingId: String) {
        val uid = authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.joinRequests(meetingId))
            .whereEqualTo("uid", uid)
            .get()
            .await()
        val incomplete = snapshot.documents.map { JoinRequestDoc.from(it) }
            .filter { it.needsMemberProfile() }
        if (incomplete.isEmpty()) return
        incomplete.forEach { request ->
            runCatching { markProfileCompleted(meetingId, request.id) }
        }
    }

    suspend fun markMyRequestWithdrawn(meetingId: String) {
        val uid = authRepository.requireUid()
        val existing = getMyJoinRequest(meetingId, uid)
        val now = java.time.Instant.now().toString()
        val col = db.collection(FirestorePaths.joinRequests(meetingId))
        if (existing != null && existing.id.isNotBlank()) {
            col.document(existing.id).update(
                mapOf(
                    "status" to JoinRequestStatus.WITHDRAWN.name,
                    "decidedAt" to now,
                    "decidedBy" to uid,
                    "profileCompleted" to false
                )
            ).await()
            return
        }
        val session = authRepository.currentSession()
            ?: throw IllegalStateException("로그인이 필요합니다.")
        val profile = userProfileFirestoreRepository.getProfile(uid)
        val docRef = col.document()
        docRef.set(
            JoinRequestDoc(
                id = docRef.id,
                uid = uid,
                email = session.email.orEmpty(),
                displayName = profile?.displayName.orEmpty(),
                phone = profile?.phone.orEmpty(),
                message = "회원 탈퇴",
                status = JoinRequestStatus.WITHDRAWN,
                requestedAt = now,
                decidedAt = now,
                decidedBy = uid,
                profileCompleted = false
            ).toMap()
        ).await()
    }

    suspend fun rejectJoinRequest(meetingId: String, requestId: String) {
        val uid = authRepository.requireUid()
        val docRef = db.collection(FirestorePaths.joinRequests(meetingId)).document(requestId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) throw IllegalStateException("가입 요청을 찾을 수 없습니다.")
        val now = java.time.Instant.now().toString()
        docRef.update(
            mapOf(
                "status" to JoinRequestStatus.REJECTED.name,
                "decidedAt" to now,
                "decidedBy" to uid
            )
        ).await()
    }

    suspend fun deleteJoinRequestsForAccount(
        meetingId: String,
        uid: String?,
        email: String?
    ) {
        authRepository.requireUid()
        joinRequestDocumentsMatching(meetingId, uid, email).forEach { doc ->
            doc.reference.delete().await()
        }
    }

    suspend fun completeJoinRequestsForAccount(
        meetingId: String,
        uid: String?,
        email: String?
    ) {
        authRepository.requireUid()
        val now = java.time.Instant.now().toString()
        joinRequestDocumentsMatching(meetingId, uid, email).forEach { doc ->
            val request = JoinRequestDoc.from(doc)
            if (request.status == JoinRequestStatus.APPROVED && !request.profileCompleted) {
                doc.reference.update(
                    mapOf(
                        "profileCompleted" to true,
                        "profileCompletedAt" to now
                    )
                ).await()
            }
        }
    }

    suspend fun findAccountUid(meetingId: String, email: String?): String? {
        val normalized = email?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return null
        val match = joinRequestDocumentsMatching(meetingId, uid = null, email = email)
            .map { JoinRequestDoc.from(it) }
            .firstOrNull { it.uid.isNotBlank() }
        return match?.uid?.takeIf { it.isNotBlank() }
    }

    private suspend fun joinRequestDocumentsMatching(
        meetingId: String,
        uid: String?,
        email: String?
    ): List<com.google.firebase.firestore.DocumentSnapshot> {
        val snapshot = db.collection(FirestorePaths.joinRequests(meetingId)).get().await()
        val normalizedEmail = email?.trim()?.lowercase().orEmpty()
        val rawEmail = email?.trim().orEmpty()
        return snapshot.documents.filter { doc ->
            val request = JoinRequestDoc.from(doc)
            val requestEmail = request.email.trim()
            (!uid.isNullOrBlank() && request.uid == uid) ||
                (normalizedEmail.isNotBlank() && requestEmail.equals(normalizedEmail, ignoreCase = true)) ||
                (rawEmail.isNotBlank() && requestEmail.equals(rawEmail, ignoreCase = true))
        }
    }
}

@Singleton
class UserProfileFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    suspend fun getProfile(uid: String): UserProfileDoc? {
        if (uid.isBlank()) return null
        val snapshot = db.document(FirestorePaths.userProfile(uid)).get().await()
        return if (snapshot.exists()) UserProfileDoc.from(snapshot) else null
    }

    suspend fun upsertProfile(uid: String, email: String?, displayName: String?) {
        val now = java.time.LocalDate.now().toString()
        val existing = getProfile(uid)
        val authName = displayName?.trim().orEmpty()
        // Auth/Google 최신 이름이 있으면 Firestore 옛 이름을 덮어쓴다
        val resolvedName = authName.ifBlank { existing?.displayName.orEmpty() }
        db.document(FirestorePaths.userProfile(uid)).set(
            mapOf(
                "uid" to uid,
                "email" to email?.trim()?.lowercase().orEmpty(),
                "displayName" to resolvedName,
                "phone" to existing?.phone.orEmpty(),
                "profileCompleted" to (existing?.profileCompleted == true),
                "createdAt" to (existing?.createdAt?.takeIf { it.isNotBlank() } ?: now),
                "updatedAt" to now
            ),
            SetOptions.merge()
        ).await()
    }

    /**
     * Google/Auth displayName이 Firestore와 다르면 최신 이름으로 동기화.
     * @return 반영된(또는 기존) 표시 이름
     */
    suspend fun syncDisplayNameFromAuth(
        uid: String,
        email: String?,
        authDisplayName: String?
    ): String {
        val authName = authDisplayName?.trim().orEmpty()
        if (uid.isBlank()) return authName
        val existing = getProfile(uid)
        val storedName = existing?.displayName?.trim().orEmpty()
        if (authName.isBlank()) {
            return storedName
        }
        if (authName == storedName && existing != null) {
            return storedName
        }
        upsertProfile(uid = uid, email = email, displayName = authName)
        return authName
    }

    suspend fun saveCompletedProfile(
        uid: String,
        email: String,
        displayName: String,
        phone: String
    ) {
        val now = java.time.LocalDate.now().toString()
        val existing = getProfile(uid)
        db.document(FirestorePaths.userProfile(uid)).set(
            mapOf(
                "uid" to uid,
                "email" to email.trim().lowercase(),
                "displayName" to displayName.trim(),
                "phone" to phone.filter { it.isDigit() },
                "profileCompleted" to true,
                "createdAt" to (existing?.createdAt?.takeIf { it.isNotBlank() } ?: now),
                "updatedAt" to now
            ),
            SetOptions.merge()
        ).await()
    }

    /** 이메일로 UID 조회. 상대가 앱에 한 번이라도 로그인한 경우에만 찾을 수 있습니다. */
    suspend fun findUidByEmail(email: String): String? {
        val normalized = email.trim().lowercase()
        require(normalized.isNotBlank()) { "이메일을 입력해 주세요." }
        val snapshot = db.collection(FirestorePaths.USER_PROFILES)
            .whereEqualTo("email", normalized)
            .limit(1)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        return doc.getString("uid") ?: doc.id
    }
}

@Singleton
class MemberFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    fun observeMembers(meetingId: String): Flow<List<MemberDoc>> = callbackFlow {
        runCatching { authRepository.requireUid() }.getOrElse {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = db.collection(FirestorePaths.members(meetingId))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents
                        ?.map { MemberDoc.from(it) }
                        .orEmpty()
                        .withoutSystemAdminMembership()
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun upsertMember(meetingId: String, member: MemberDoc): String {
        authRepository.requireUid()
        if (member.isSystemAdminClubMembership()) {
            throw IllegalStateException("시스템관리자는 모임 회원으로 등록되지 않습니다.")
        }
        val col = db.collection(FirestorePaths.members(meetingId))
        val docRef = if (member.id.isBlank()) col.document() else col.document(member.id)
        val data = member.copy(id = docRef.id).toMap()
            .filterValues { it != null }
            .mapValues { it.value as Any }
        docRef.set(data, SetOptions.merge()).await()
        return docRef.id
    }

    suspend fun getMember(meetingId: String, memberId: String): MemberDoc? {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.members(meetingId))
            .document(memberId)
            .get()
            .await()
        return if (snapshot.exists()) {
            MemberDoc.from(snapshot).takeUnless { it.isSystemAdminClubMembership() }
        } else {
            null
        }
    }

    suspend fun getMemberCount(meetingId: String): Int {
        return getMembersOnce(meetingId).size
    }

    suspend fun getMembersOnce(meetingId: String): List<MemberDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.members(meetingId)).get().await()
        return snapshot.documents.map { MemberDoc.from(it) }.withoutSystemAdminMembership()
    }

    /** 본인 명단. 휴면·탈퇴로 모임 접근이 없어도 상태를 확인할 때 사용. */
    suspend fun findMyMember(meetingId: String, uid: String, email: String?): MemberDoc? {
        if (meetingId.isBlank() || uid.isBlank()) return null
        authRepository.requireUid()
        val col = db.collection(FirestorePaths.members(meetingId))
        runCatching {
            val snapshot = col.document(uid).get().await()
            if (snapshot.exists()) {
                return MemberDoc.from(snapshot).takeUnless { it.isSystemAdminClubMembership() }
            }
        }
        runCatching {
            val snapshot = col.whereEqualTo("linkedUid", uid).limit(1).get().await()
            snapshot.documents.firstOrNull()?.let { doc ->
                return MemberDoc.from(doc).takeUnless { it.isSystemAdminClubMembership() }
            }
        }
        val trimmedEmail = email?.trim().orEmpty()
        if (trimmedEmail.isNotBlank()) {
            val emails = listOf(trimmedEmail, trimmedEmail.lowercase()).distinct()
            emails.forEach { candidate ->
                runCatching {
                    val snapshot = col.whereEqualTo("email", candidate).limit(1).get().await()
                    snapshot.documents.firstOrNull()?.let { doc ->
                        return MemberDoc.from(doc).takeUnless { it.isSystemAdminClubMembership() }
                    }
                }
            }
        }
        return null
    }

    suspend fun pruneSystemAdminMembers(meetingId: String) {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.members(meetingId)).get().await()
        snapshot.documents.forEach { doc ->
            if (MemberDoc.from(doc).isSystemAdminClubMembership()) {
                doc.reference.delete().await()
            }
        }
    }

    suspend fun deleteMember(meetingId: String, memberId: String) {
        authRepository.requireUid()
        db.collection(FirestorePaths.members(meetingId)).document(memberId).delete().await()
    }

    suspend fun deleteAllMembers(meetingId: String) {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.members(meetingId)).get().await()
        snapshot.documents.forEach { doc ->
            doc.reference.delete().await()
        }
    }
}

private fun List<MemberDoc>.withoutSystemAdminMembership(): List<MemberDoc> =
    filterNot { it.isSystemAdminClubMembership() }

private fun MemberDoc.isSystemAdminClubMembership(): Boolean =
    SystemAdminConfig.isClubMembershipRecord(
        email = email,
        uid = linkedUid.ifBlank { id },
        name = name
    )

@Singleton
class TransactionFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    fun observeTransactions(meetingId: String): Flow<List<TransactionDoc>> = callbackFlow {
        runCatching { authRepository.requireUid() }.getOrElse {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = db.collection(FirestorePaths.transactions(meetingId))
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { TransactionDoc.from(it) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    fun observeTransactionsByDateRange(
        meetingId: String,
        startDate: String,
        endDate: String
    ): Flow<List<TransactionDoc>> = callbackFlow {
        runCatching { authRepository.requireUid() }.getOrElse {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = db.collection(FirestorePaths.transactions(meetingId))
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { TransactionDoc.from(it) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun upsertTransaction(meetingId: String, tx: TransactionDoc): String {
        authRepository.requireUid()
        val col = db.collection(FirestorePaths.transactions(meetingId))
        val docRef = if (tx.id.isBlank()) col.document() else col.document(tx.id)
        docRef.set(tx.copy(id = docRef.id).toMap(), SetOptions.merge()).await()
        return docRef.id
    }

    /**
     * 거래 문서들의 balanceAfter를 배치로 덮어씁니다.
     */
    suspend fun updateBalanceAfterBatch(
        meetingId: String,
        balanceByDocId: Map<String, Int>
    ) {
        if (balanceByDocId.isEmpty()) return
        authRepository.requireUid()
        val col = db.collection(FirestorePaths.transactions(meetingId))
        balanceByDocId.entries.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (docId, balance) ->
                if (docId.isBlank()) return@forEach
                batch.set(
                    col.document(docId),
                    mapOf("balanceAfter" to balance),
                    SetOptions.merge()
                )
            }
            batch.commit().await()
        }
    }

    suspend fun getTransaction(meetingId: String, transactionId: String): TransactionDoc? {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.transactions(meetingId))
            .document(transactionId)
            .get()
            .await()
        return if (snapshot.exists()) TransactionDoc.from(snapshot) else null
    }

    suspend fun getTransactionCount(meetingId: String): Int {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.transactions(meetingId)).get().await()
        return snapshot.size()
    }

    suspend fun getTransactionsOnce(meetingId: String): List<TransactionDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.transactions(meetingId)).get().await()
        return snapshot.documents.map { TransactionDoc.from(it) }
    }

    suspend fun getTransactionsByDateRangeOnce(
        meetingId: String,
        startDate: String,
        endDate: String
    ): List<TransactionDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.transactions(meetingId))
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.map { TransactionDoc.from(it) }
    }

    suspend fun getTransactionsByLinkedDuesDetailOnce(
        meetingId: String,
        linkedDuesDetailId: String
    ): List<TransactionDoc> {
        if (linkedDuesDetailId.isBlank()) return emptyList()
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.transactions(meetingId))
            .whereEqualTo("linkedDuesDetailId", linkedDuesDetailId)
            .get()
            .await()
        return snapshot.documents.map { TransactionDoc.from(it) }
    }

    suspend fun deleteTransaction(meetingId: String, transactionId: String) {
        authRepository.requireUid()
        db.collection(FirestorePaths.transactions(meetingId))
            .document(transactionId)
            .delete()
            .await()
    }

    suspend fun deleteAllTransactions(meetingId: String) {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.transactions(meetingId)).get().await()
        snapshot.documents.forEach { doc ->
            doc.reference.delete().await()
        }
    }
}

@Singleton
class DuesFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    fun observeDues(meetingId: String): Flow<List<DuesDoc>> = callbackFlow {
        runCatching { authRepository.requireUid() }.getOrElse {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = db.collection(FirestorePaths.dues(meetingId))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { DuesDoc.from(it) }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun upsertDues(meetingId: String, dues: DuesDoc): String {
        authRepository.requireUid()
        val col = db.collection(FirestorePaths.dues(meetingId))
        val docRef = if (dues.id.isBlank()) col.document() else col.document(dues.id)
        val detailsWithIds = dues.details.map { detail ->
            if (detail.id.isNotBlank()) detail
            else detail.copy(id = col.document().id)
        }
        docRef.set(
            dues.copy(id = docRef.id, details = detailsWithIds).toMap(),
            SetOptions.merge()
        ).await()
        return docRef.id
    }

    suspend fun getDuesOnce(meetingId: String): List<DuesDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.dues(meetingId)).get().await()
        return snapshot.documents.map { DuesDoc.from(it) }
    }

    suspend fun deleteAllDues(meetingId: String) {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.dues(meetingId)).get().await()
        snapshot.documents.forEach { doc ->
            doc.reference.delete().await()
        }
    }

    suspend fun deleteDues(meetingId: String, duesId: String) {
        authRepository.requireUid()
        db.collection(FirestorePaths.dues(meetingId)).document(duesId).delete().await()
    }
}

@Singleton
class ClubHistoryFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    suspend fun upsertHistory(meetingId: String, history: ClubHistoryDoc): String {
        authRepository.requireUid()
        val col = db.collection(FirestorePaths.histories(meetingId))
        val docRef = if (history.id.isBlank()) col.document() else col.document(history.id)
        docRef.set(history.copy(id = docRef.id).toMap(), SetOptions.merge()).await()
        return docRef.id
    }

    suspend fun getHistoriesOnce(meetingId: String): List<ClubHistoryDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.histories(meetingId)).get().await()
        return snapshot.documents.map { ClubHistoryDoc.from(it) }
    }

    suspend fun deleteAllHistories(meetingId: String) {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.histories(meetingId)).get().await()
        snapshot.documents.forEach { doc -> doc.reference.delete().await() }
    }
}

@Singleton
class ClubAccountFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    suspend fun upsertAccount(meetingId: String, account: ClubAccountDoc): String {
        authRepository.requireUid()
        val col = db.collection(FirestorePaths.accounts(meetingId))
        val docRef = if (account.id.isBlank()) col.document() else col.document(account.id)
        docRef.set(account.copy(id = docRef.id).toMap(), SetOptions.merge()).await()
        return docRef.id
    }

    suspend fun getAccountsOnce(meetingId: String): List<ClubAccountDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.accounts(meetingId)).get().await()
        return snapshot.documents.map { ClubAccountDoc.from(it) }
    }

    suspend fun deleteAllAccounts(meetingId: String) {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.accounts(meetingId)).get().await()
        snapshot.documents.forEach { doc -> doc.reference.delete().await() }
    }
}

@Singleton
class EventExpenseFirestoreRepository @Inject constructor(
    private val firebaseInitializer: FirebaseInitializer,
    private val authRepository: FirebaseAuthRepository
) {
    private val db: FirebaseFirestore get() = firebaseInitializer.firestore()

    suspend fun getEventExpensesOnce(meetingId: String): List<EventExpenseDoc> {
        authRepository.requireUid()
        val snapshot = db.collection(FirestorePaths.eventExpenses(meetingId)).get().await()
        return snapshot.documents.map { EventExpenseDoc.from(it) }
    }
}
