package com.smartexpense.data.firebase

import androidx.room.withTransaction
import com.smartexpense.data.firebase.firestore.ClubAccountDoc
import com.smartexpense.data.firebase.firestore.ClubHistoryDoc
import com.smartexpense.data.firebase.firestore.DuesDetailDoc
import com.smartexpense.data.firebase.firestore.DuesDoc
import com.smartexpense.data.firebase.firestore.DuesPaymentDoc
import com.smartexpense.data.firebase.firestore.EventExpenseDoc
import com.smartexpense.data.firebase.firestore.FirestoreBatchSet
import com.smartexpense.data.firebase.firestore.FirestoreBatchWriter
import com.smartexpense.data.firebase.firestore.FirestorePaths
import com.smartexpense.data.firebase.firestore.MeetingDoc
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberDoc
import com.smartexpense.data.firebase.firestore.MemberFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberStatusLabels
import com.smartexpense.data.firebase.firestore.TransactionDoc
import com.smartexpense.data.firebase.firestore.TransactionFirestoreRepository
import com.smartexpense.data.firebase.firestore.DuesFirestoreRepository
import com.smartexpense.data.firebase.firestore.ClubHistoryFirestoreRepository
import com.smartexpense.data.firebase.firestore.ClubAccountFirestoreRepository
import com.smartexpense.data.firebase.firestore.EventExpenseFirestoreRepository
import com.smartexpense.data.local.ClubConstants
import com.smartexpense.data.local.entity.club.ClubSettingEntity
import com.smartexpense.data.local.entity.club.ClubSettingKeys
import com.smartexpense.data.local.entity.club.ClubAccountEntity
import com.smartexpense.data.local.entity.club.ClubHistoryEntity
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.data.local.entity.club.EventExpenseEntity
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.YearlyDuesEntity
import com.smartexpense.data.local.model.club.YearlyDuesWithDetails
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.data.repository.club.ClubSettingsRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.repository.club.SelectedMeetingRepository
import com.smartexpense.data.repository.club.requireSelectedClubId
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.domain.ledger.FreshLedgerBalance
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class CloudSyncMode {
    FULL,
    INCREMENTAL
}

data class CloudMigrationResult(
    val meetingId: String,
    val memberCount: Int,
    val transactionCount: Int,
    val duesCount: Int = 0,
    val historyCount: Int = 0,
    val accountCount: Int = 0,
    val eventExpenseCount: Int = 0,
    val slogan: String = "",
    val syncMode: CloudSyncMode = CloudSyncMode.FULL,
    /** 거래 합산으로 재계산한 잔액. meeting.currentBalance와 무관. */
    val calculatedBalance: Long? = null
)

data class LocalClubDataCounts(
    val memberCount: Int,
    val transactionCount: Int,
    val duesCount: Int,
    val historyCount: Int,
    val accountCount: Int,
    val eventExpenseCount: Int
)

/**
 * 로컬 Room ↔ Firestore 수동 올리기/내리기.
 * 올리기는 최초 전체 이행(FULL)과 이후 변경분(INCREMENTAL) 하이브리드입니다.
 */
@Singleton
class CloudDataMigrationRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository,
    private val meetingFirestoreRepository: MeetingFirestoreRepository,
    private val memberFirestoreRepository: MemberFirestoreRepository,
    private val transactionFirestoreRepository: TransactionFirestoreRepository,
    private val duesFirestoreRepository: DuesFirestoreRepository,
    private val clubHistoryFirestoreRepository: ClubHistoryFirestoreRepository,
    private val clubAccountFirestoreRepository: ClubAccountFirestoreRepository,
    private val eventExpenseFirestoreRepository: EventExpenseFirestoreRepository,
    private val clubRepository: ClubRepository,
    private val clubSettingsRepository: ClubSettingsRepository,
    private val idMapper: FirestoreIdMapper,
    private val batchWriter: FirestoreBatchWriter,
    private val syncCursorRepository: CloudSyncCursorRepository
) {
    suspend fun countLocalClubData(): LocalClubDataCounts {
        val clubId = selectedClubRepository.requireSelectedClubId()
        return LocalClubDataCounts(
            memberCount = databaseGateway.memberDao().getAllOnce(clubId).size,
            transactionCount = databaseGateway.clubTransactionDao().getAllOnce(clubId).size,
            duesCount = databaseGateway.yearlyDuesDao().getAllWithDetailsOnce(clubId).size,
            historyCount = databaseGateway.clubHistoryDao().getAllOnce(clubId).size,
            accountCount = databaseGateway.clubAccountDao().getAllOnce(clubId).size,
            eventExpenseCount = databaseGateway.eventExpenseDao().getAllOnce(clubId).size
        )
    }

    /**
     * 하이브리드 올리기: 최초 1회 전체 이행, 이후 변경분만 배치 업로드.
     */
    suspend fun syncCurrentClubToFirestore(
        clubName: String,
        clubSlogan: String = ""
    ): CloudMigrationResult {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val meetingId = ensureMeetingForManualSync(clubName)
        val slogan = clubSlogan.ifBlank {
            databaseGateway.clubDao().getById(clubId)?.clubSlogan.orEmpty()
        }
        val duesPaymentMethod = clubSettingsRepository.getSettingsOnce(clubId).duesPaymentMethod
        val lastSyncedAt = syncCursorRepository.getLastSyncedAt(meetingId)
        val remoteEmpty = isRemoteClubEmpty(meetingId)
        val fullSync = lastSyncedAt <= 0L || remoteEmpty
        val syncStartedAt = EntityTimestamps.now()

        meetingFirestoreRepository.upsertMeeting(
            MeetingDoc(
                id = meetingId,
                name = clubName.ifBlank { "내 모임" },
                slogan = slogan,
                createdAt = LocalDate.now().toString(),
                duesPaymentMethod = duesPaymentMethod.name
            )
        )

        if (fullSync) {
            clearRemoteClubCollections(meetingId)
        }

        val members = (if (fullSync) {
            databaseGateway.memberDao().getAllOnce(clubId)
        } else {
            databaseGateway.memberDao().getUpdatedSince(clubId, lastSyncedAt)
        }).filterNot { member ->
            SystemAdminConfig.isClubMembershipRecord(email = member.email, name = member.name)
        }
        val memberDocByRoomId = resolveMemberDocIds(clubId, members, fullSync)

        // 회원은 별도 배치로 먼저 올리고 건수를 검증한다.
        // (대용량 장부 배치 실패 시 회원이 비거나 일부만 남는 사고 방지)
        val memberBatchSets = mutableListOf<FirestoreBatchSet>()
        val memberCloudUpdates = mutableListOf<Pair<Long, String>>()
        for (member in members) {
            val docId = member.cloudDocId.ifBlank {
                CloudDocIds.member(clubId, member.id)
            }
            memberDocByRoomId[member.id] = docId
            memberBatchSets += FirestoreBatchSet(
                collectionPath = FirestorePaths.members(meetingId),
                documentId = docId,
                data = MemberDoc(
                    id = docId,
                    name = member.name,
                    status = MemberStatusLabels.toFirestore(member.status),
                    joinDate = member.joinDate,
                    phone = member.phone,
                    birthDate = member.birthDate,
                    address = member.address,
                    detailAddress = member.detailAddress,
                    residenceRegion = member.residenceRegion,
                    email = member.email,
                    role = member.role.name,
                    isLunarBirth = member.isLunarBirth,
                    suspensionDate = member.suspensionDate
                ).toMap()
            )
            if (member.cloudDocId != docId) {
                memberCloudUpdates += member.id to docId
            }
            idMapper.rememberMember(docId, member.id)
        }
        if (memberBatchSets.isNotEmpty()) {
            batchWriter.commit(sets = memberBatchSets)
            val remoteMemberCount = memberFirestoreRepository.getMemberCount(meetingId)
            if (fullSync && remoteMemberCount != members.size) {
                throw IllegalStateException(
                    "회원 클라우드 반영 검증 실패: 로컬 ${members.size}명 → 클라우드 ${remoteMemberCount}명. " +
                        "Firestore 규칙(시스템관리자/모임관리자 쓰기 권한)을 배포했는지 확인해 주세요."
                )
            }
        }
        runCatching { memberFirestoreRepository.pruneSystemAdminMembers(meetingId) }

        val transactions = if (fullSync) {
            databaseGateway.clubTransactionDao().getAllOnce(clubId)
        } else {
            databaseGateway.clubTransactionDao().getUpdatedSince(clubId, lastSyncedAt)
        }

        val duesRows = if (fullSync) {
            databaseGateway.yearlyDuesDao().getAllWithDetailsOnce(clubId)
        } else {
            loadChangedDuesRows(clubId, lastSyncedAt)
        }

        val histories = if (fullSync) {
            databaseGateway.clubHistoryDao().getAllOnce(clubId)
        } else {
            databaseGateway.clubHistoryDao().getUpdatedSince(clubId, lastSyncedAt)
        }

        val accounts = if (fullSync) {
            databaseGateway.clubAccountDao().getAllOnce(clubId)
        } else {
            databaseGateway.clubAccountDao().getUpdatedSince(clubId, lastSyncedAt)
        }

        val eventExpenses = if (fullSync) {
            databaseGateway.eventExpenseDao().getAllOnce(clubId)
        } else {
            databaseGateway.eventExpenseDao().getUpdatedSince(clubId, lastSyncedAt)
        }

        val detailIds = duesRows.flatMap { row -> row.details.map { it.id } }
        val historiesByDetailId = if (detailIds.isEmpty()) {
            emptyMap()
        } else {
            databaseGateway.duesPaymentHistoryDao()
                .getByDetailIds(detailIds, clubId)
                .groupBy { it.duesDetailId }
        }

        val batchSets = mutableListOf<FirestoreBatchSet>()
        val txCloudUpdates = mutableListOf<Pair<Long, String>>()
        val duesCloudUpdates = mutableListOf<Pair<Long, String>>()
        val detailCloudUpdates = mutableListOf<Pair<Long, String>>()
        val historyCloudUpdates = mutableListOf<Pair<Int, String>>()
        val accountCloudUpdates = mutableListOf<Pair<Int, String>>()
        val eventExpenseCloudUpdates = mutableListOf<Pair<Long, String>>()
        val txDocByRoomId = resolveTransactionDocIds(clubId, transactions, fullSync)

        // (회원은 위에서 이미 업로드)

        for (tx in transactions) {
            val docId = tx.cloudDocId.ifBlank {
                CloudDocIds.transaction(clubId, tx.id)
            }
            val amount = when (tx.type) {
                ClubTransactionType.INCOME -> tx.incomeAmount.toLong()
                ClubTransactionType.EXPENSE -> tx.expenseAmount.toLong()
            }
            val memberCloudId = tx.targetMemberId?.let { roomMemberId ->
                memberDocByRoomId[roomMemberId]
                    ?: databaseGateway.memberDao().getById(roomMemberId, clubId)
                        ?.let { m ->
                            m.cloudDocId.ifBlank { CloudDocIds.member(clubId, m.id) }
                                .also { memberDocByRoomId[roomMemberId] = it }
                        }
            }
            val linkedDetailCloudId = tx.linkedDuesDetailId?.let { detailRoomId ->
                databaseGateway.yearlyDuesDao().getDetailById(detailRoomId, clubId)
                    ?.let { d -> d.cloudDocId.ifBlank { CloudDocIds.detail(clubId, d.id) } }
            }
            batchSets += FirestoreBatchSet(
                collectionPath = FirestorePaths.transactions(meetingId),
                documentId = docId,
                data = TransactionDoc(
                    id = docId,
                    date = tx.date,
                    type = tx.type.name,
                    category = tx.category,
                    amount = amount,
                    incomeAmount = tx.incomeAmount,
                    expenseAmount = tx.expenseAmount,
                    description = tx.note.orEmpty(),
                    memberId = memberCloudId,
                    balanceAfter = tx.balanceAfter,
                    note = tx.note,
                    linkedDuesDetailId = linkedDetailCloudId
                ).toMap()
            )
            if (tx.cloudDocId != docId) {
                txCloudUpdates += tx.id to docId
            }
            txDocByRoomId[tx.id] = docId
            idMapper.rememberTransaction(docId, tx.id)
        }

        for (row in duesRows) {
            val firestoreMemberId = memberDocByRoomId[row.yearlyDues.memberId]
                ?: databaseGateway.memberDao().getById(row.yearlyDues.memberId, clubId)
                    ?.let { m ->
                        m.cloudDocId.ifBlank { CloudDocIds.member(clubId, m.id) }
                            .also { memberDocByRoomId[row.yearlyDues.memberId] = it }
                    }
                ?: continue
            val duesDocId = row.yearlyDues.cloudDocId.ifBlank {
                CloudDocIds.dues(clubId, row.yearlyDues.id)
            }
            val detailDocs = row.details.map { detail ->
                val detailDocId = detail.cloudDocId.ifBlank {
                    CloudDocIds.detail(clubId, detail.id)
                }
                if (detail.cloudDocId != detailDocId) {
                    detailCloudUpdates += detail.id to detailDocId
                }
                val historyRows = historiesByDetailId[detail.id].orEmpty()
                val payments = if (historyRows.isNotEmpty()) {
                    historyRows.map { history ->
                        DuesPaymentDoc(
                            payDate = history.payDate,
                            amount = history.amount,
                            linkedTransactionId = history.linkedTransactionId
                                ?.let { txDocByRoomId[it] }
                        )
                    }
                } else if (detail.paidAmount > 0 && detail.payDate.isNotBlank()) {
                    listOf(DuesPaymentDoc(payDate = detail.payDate, amount = detail.paidAmount))
                } else {
                    emptyList()
                }
                DuesDetailDoc(
                    id = detailDocId,
                    termLabel = detail.termLabel,
                    amount = detail.amount,
                    paidAmount = detail.paidAmount,
                    isPaid = detail.isPaid,
                    isExcluded = detail.isExcluded,
                    payDate = detail.payDate,
                    payments = payments
                )
            }
            batchSets += FirestoreBatchSet(
                collectionPath = FirestorePaths.dues(meetingId),
                documentId = duesDocId,
                data = DuesDoc(
                    id = duesDocId,
                    memberId = firestoreMemberId,
                    year = row.yearlyDues.year,
                    totalTargetAmount = row.yearlyDues.totalTargetAmount,
                    paymentMethod = row.yearlyDues.paymentMethod.name,
                    details = detailDocs
                ).toMap()
            )
            if (row.yearlyDues.cloudDocId != duesDocId) {
                duesCloudUpdates += row.yearlyDues.id to duesDocId
            }
        }

        for (history in histories) {
            val docId = history.cloudDocId.ifBlank {
                CloudDocIds.history(clubId, history.id)
            }
            batchSets += FirestoreBatchSet(
                collectionPath = FirestorePaths.histories(meetingId),
                documentId = docId,
                data = ClubHistoryDoc(
                    id = docId,
                    date = history.date,
                    content = history.content,
                    details = history.details,
                    note = history.note
                ).toMap()
            )
            if (history.cloudDocId != docId) {
                historyCloudUpdates += history.id to docId
            }
        }

        for (account in accounts) {
            val docId = account.cloudDocId.ifBlank {
                CloudDocIds.account(clubId, account.id)
            }
            batchSets += FirestoreBatchSet(
                collectionPath = FirestorePaths.accounts(meetingId),
                documentId = docId,
                data = ClubAccountDoc(
                    id = docId,
                    bankName = account.bankName,
                    accountNumber = account.accountNumber,
                    holderName = account.holderName
                ).toMap()
            )
            if (account.cloudDocId != docId) {
                accountCloudUpdates += account.id to docId
            }
        }

        for (event in eventExpenses) {
            val firestoreMemberId = memberDocByRoomId[event.memberId]
                ?: databaseGateway.memberDao().getById(event.memberId, clubId)
                    ?.let { m ->
                        m.cloudDocId.ifBlank { CloudDocIds.member(clubId, m.id) }
                            .also { memberDocByRoomId[event.memberId] = it }
                    }
                ?: continue
            val docId = event.cloudDocId.ifBlank {
                CloudDocIds.eventExpense(clubId, event.id)
            }
            val linkedTxCloudId = event.linkedTransactionId?.let { roomTxId ->
                txDocByRoomId[roomTxId]
                    ?: databaseGateway.clubTransactionDao().getById(roomTxId, clubId)
                        ?.let { tx ->
                            tx.cloudDocId.ifBlank { CloudDocIds.transaction(clubId, tx.id) }
                                .also { txDocByRoomId[roomTxId] = it }
                        }
            }
            batchSets += FirestoreBatchSet(
                collectionPath = FirestorePaths.eventExpenses(meetingId),
                documentId = docId,
                data = EventExpenseDoc(
                    id = docId,
                    memberId = firestoreMemberId,
                    date = event.date,
                    eventSubCategory = event.eventSubCategory,
                    amount = event.amount,
                    note = event.note,
                    linkedTransactionId = linkedTxCloudId,
                    isSeedOnly = event.isSeedOnly
                ).toMap()
            )
            if (event.cloudDocId != docId) {
                eventExpenseCloudUpdates += event.id to docId
            }
        }

        batchWriter.commit(sets = batchSets)

        databaseGateway.database().withTransaction {
            memberCloudUpdates.forEach { (id, docId) ->
                databaseGateway.memberDao().updateCloudDocId(id, clubId, docId)
            }
            txCloudUpdates.forEach { (id, docId) ->
                databaseGateway.clubTransactionDao().updateCloudDocId(id, clubId, docId)
            }
            duesCloudUpdates.forEach { (id, docId) ->
                databaseGateway.yearlyDuesDao().updateYearlyCloudDocId(id, clubId, docId)
            }
            detailCloudUpdates.forEach { (id, docId) ->
                databaseGateway.yearlyDuesDao().updateDetailCloudDocId(id, clubId, docId)
            }
            historyCloudUpdates.forEach { (id, docId) ->
                databaseGateway.clubHistoryDao().updateCloudDocId(id, clubId, docId)
            }
            accountCloudUpdates.forEach { (id, docId) ->
                databaseGateway.clubAccountDao().updateCloudDocId(id, clubId, docId)
            }
            eventExpenseCloudUpdates.forEach { (id, docId) ->
                databaseGateway.eventExpenseDao().updateCloudDocId(id, clubId, docId)
            }
        }

        syncCursorRepository.setLastSyncedAt(meetingId, syncStartedAt)

        return CloudMigrationResult(
            meetingId = meetingId,
            memberCount = members.size,
            transactionCount = transactions.size,
            duesCount = duesRows.size,
            historyCount = histories.size,
            accountCount = accounts.size,
            eventExpenseCount = eventExpenses.size,
            slogan = slogan,
            syncMode = if (fullSync) CloudSyncMode.FULL else CloudSyncMode.INCREMENTAL
        )
    }

    /** @deprecated Use [syncCurrentClubToFirestore]. Kept for call-site compatibility. */
    suspend fun replaceCurrentClubOnFirestore(
        clubName: String,
        clubSlogan: String = ""
    ): CloudMigrationResult {
        val meetingId = ensureMeetingForManualSync(clubName)
        syncCursorRepository.clearLastSyncedAt(meetingId)
        return syncCurrentClubToFirestore(clubName, clubSlogan)
    }

    /**
     * 클라우드 → 로컬: 회원·장부·회비·경조사비·슬로건·이력·계좌를 덮어씁니다.
     * 올리기와 동일하게 **현재 선택된 모임**을 사용합니다.
     * (예전처럼 '가장 많은 데이터' 모임으로 바꾸면, 방금 이행한 모임이 아닌
     *  과거 모임(활동 회원 누락본)을 받아오는 사고가 납니다.)
     */
    suspend fun downloadCloudToCurrentClub(
        preferredName: String = "한우리"
    ): CloudMigrationResult {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val existingMeetings = meetingFirestoreRepository.getMeetingsOnce()
        if (existingMeetings.isEmpty()) {
            throw IllegalStateException(
                "클라우드에 가져올 모임이 없습니다. 먼저 「올리기」를 하거나 초대를 받아 주세요."
            )
        }
        val meetingId = ensureMeetingForManualSync(preferredName.ifBlank { "한우리" })
        val meeting = meetingFirestoreRepository.getMeeting(meetingId)
        val members = memberFirestoreRepository.getMembersOnce(meetingId)
        val transactions = transactionFirestoreRepository.getTransactionsOnce(meetingId)
        val dues = duesFirestoreRepository.getDuesOnce(meetingId)
        val histories = clubHistoryFirestoreRepository.getHistoriesOnce(meetingId)
        val accounts = clubAccountFirestoreRepository.getAccountsOnce(meetingId)
        val eventExpenses = eventExpenseFirestoreRepository.getEventExpensesOnce(meetingId)
        val slogan = meeting?.slogan.orEmpty()
        val now = EntityTimestamps.now()

        if (members.isEmpty() && transactions.isEmpty() && dues.isEmpty() &&
            histories.isEmpty() && accounts.isEmpty() && eventExpenses.isEmpty() &&
            slogan.isBlank()
        ) {
            throw IllegalStateException(
                "선택한 클라우드 모임(${meeting?.name ?: meetingId})이 비어 있습니다. " +
                    "「초기 데이터 이행」또는 「올리기」로 먼저 데이터를 저장해 주세요."
            )
        }

        databaseGateway.database().withTransaction {
            databaseGateway.eventExpenseDao().deleteAll(clubId)
            databaseGateway.clubTransactionDao().deleteAll(clubId)
            databaseGateway.duesPaymentHistoryDao().deleteAll(clubId)
            databaseGateway.yearlyDuesDao().deleteAllDetails(clubId)
            databaseGateway.yearlyDuesDao().deleteAllYearlyDues(clubId)
            databaseGateway.memberDao().deleteAllStatusHistory(clubId)
            databaseGateway.memberDao().deleteAllMembers(clubId)
            databaseGateway.clubHistoryDao().deleteAll(clubId)
            databaseGateway.clubAccountDao().deleteAll(clubId)

            val firestoreMemberToRoom = LinkedHashMap<String, Long>(members.size)
            val firestoreTxToRoom = LinkedHashMap<String, Long>(transactions.size)
            val firestoreDetailToRoom = LinkedHashMap<String, Long>()
            for (doc in members) {
                if (SystemAdminConfig.isClubMembershipRecord(
                        email = doc.email,
                        uid = doc.linkedUid.ifBlank { doc.id },
                        name = doc.name
                    )
                ) {
                    continue
                }
                val roomId = databaseGateway.memberDao().insert(
                    MemberEntity(
                        clubId = clubId,
                        joinDate = doc.joinDate,
                        name = doc.name,
                        birthDate = doc.birthDate,
                        address = doc.address,
                        detailAddress = doc.detailAddress,
                        residenceRegion = doc.residenceRegion,
                        phone = doc.phone,
                        email = doc.email,
                        status = doc.statusEnum(),
                        role = runCatching { MemberRole.valueOf(doc.role) }
                            .getOrDefault(MemberRole.GENERAL),
                        isLunarBirth = doc.isLunarBirth,
                        suspensionDate = doc.suspensionDate,
                        updatedAt = now,
                        cloudDocId = doc.id
                    )
                )
                firestoreMemberToRoom[doc.id] = roomId
                idMapper.rememberMember(doc.id, roomId)
            }

            for (tx in transactions) {
                val (type, income, expense) = resolveTransactionAmounts(tx)
                val roomTxId = databaseGateway.clubTransactionDao().insert(
                    ClubTransactionEntity(
                        clubId = clubId,
                        date = tx.date,
                        type = type,
                        category = tx.category,
                        incomeAmount = income,
                        expenseAmount = expense,
                        note = tx.note ?: tx.description,
                        // 서버 balanceAfter/currentBalance는 신뢰하지 않음 — 아래에서 재계산
                        balanceAfter = null,
                        targetMemberId = tx.memberId?.let { firestoreMemberToRoom[it] },
                        linkedDuesDetailId = null, // 회비 detail Room ID는 아래에서 후처리
                        updatedAt = now,
                        cloudDocId = tx.id
                    )
                )
                firestoreTxToRoom[tx.id] = roomTxId
                idMapper.rememberTransaction(tx.id, roomTxId)
            }

            if (eventExpenses.isNotEmpty()) {
                databaseGateway.eventExpenseDao().insertAll(
                    eventExpenses.mapNotNull { doc ->
                        val roomMemberId = firestoreMemberToRoom[doc.memberId] ?: return@mapNotNull null
                        EventExpenseEntity(
                            clubId = clubId,
                            memberId = roomMemberId,
                            date = doc.date,
                            eventSubCategory = doc.eventSubCategory.ifBlank { "기타" },
                            amount = doc.amount,
                            note = doc.note,
                            linkedTransactionId = doc.linkedTransactionId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { firestoreTxToRoom[it] },
                            isSeedOnly = doc.isSeedOnly,
                            updatedAt = now,
                            cloudDocId = doc.id
                        )
                    }
                )
            }

            for (duesDoc in dues) {
                val roomMemberId = firestoreMemberToRoom[duesDoc.memberId] ?: continue
                val yearlyId = databaseGateway.yearlyDuesDao().insertYearlyDues(
                    YearlyDuesEntity(
                        clubId = clubId,
                        memberId = roomMemberId,
                        year = duesDoc.year,
                        totalTargetAmount = duesDoc.totalTargetAmount,
                        paymentMethod = runCatching {
                            DuesPaymentMethod.valueOf(duesDoc.paymentMethod)
                        }.getOrDefault(DuesPaymentMethod.YEARLY),
                        updatedAt = now,
                        cloudDocId = duesDoc.id
                    )
                )
                if (duesDoc.details.isNotEmpty()) {
                    val detailIds = databaseGateway.yearlyDuesDao().insertDetailsForImport(
                        duesDoc.details.map { detail ->
                            DuesDetailEntity(
                                clubId = clubId,
                                yearlyDuesId = yearlyId,
                                termLabel = detail.termLabel,
                                amount = detail.amount,
                                paidAmount = detail.paidAmount,
                                isPaid = detail.isPaid,
                                isExcluded = detail.isExcluded,
                                payDate = detail.payDate,
                                updatedAt = now,
                                cloudDocId = detail.id
                            )
                        }
                    )
                    val historyEntities = mutableListOf<DuesPaymentHistoryEntity>()
                    duesDoc.details.zip(detailIds).forEach { (detail, detailId) ->
                        if (detail.id.isNotBlank()) {
                            firestoreDetailToRoom[detail.id] = detailId
                        }
                        val payments = detail.payments.ifEmpty {
                            if (detail.paidAmount > 0 && detail.payDate.isNotBlank()) {
                                listOf(
                                    DuesPaymentDoc(
                                        payDate = detail.payDate,
                                        amount = detail.paidAmount
                                    )
                                )
                            } else {
                                emptyList()
                            }
                        }
                        payments.forEach { payment ->
                            if (payment.amount > 0 && payment.payDate.isNotBlank()) {
                                historyEntities += DuesPaymentHistoryEntity(
                                    clubId = clubId,
                                    duesDetailId = detailId,
                                    payDate = payment.payDate,
                                    amount = payment.amount,
                                    updatedAt = now,
                                    linkedTransactionId = payment.linkedTransactionId
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { firestoreTxToRoom[it] }
                                )
                            }
                        }
                    }
                    if (historyEntities.isNotEmpty()) {
                        databaseGateway.duesPaymentHistoryDao().insertAll(historyEntities)
                    }
                }
            }

            // 장부 ↔ 회비 detail 링크 후처리 (회비 Room ID가 생긴 뒤)
            for (tx in transactions) {
                val detailCloudId = tx.linkedDuesDetailId?.takeIf { it.isNotBlank() } ?: continue
                val roomTxId = firestoreTxToRoom[tx.id] ?: continue
                val roomDetailId = firestoreDetailToRoom[detailCloudId] ?: continue
                val entity = databaseGateway.clubTransactionDao().getById(roomTxId, clubId) ?: continue
                databaseGateway.clubTransactionDao().update(
                    entity.copy(linkedDuesDetailId = roomDetailId, updatedAt = now)
                )
            }

            if (histories.isNotEmpty()) {
                databaseGateway.clubHistoryDao().insertAll(
                    histories.map { doc ->
                        ClubHistoryEntity(
                            clubId = clubId,
                            date = doc.date,
                            content = doc.content,
                            details = doc.details,
                            note = doc.note,
                            updatedAt = now,
                            cloudDocId = doc.id
                        )
                    }
                )
            }

            if (accounts.isNotEmpty()) {
                databaseGateway.clubAccountDao().insertAll(
                    accounts.map { doc ->
                        ClubAccountEntity(
                            clubId = clubId,
                            bankName = doc.bankName,
                            accountNumber = doc.accountNumber,
                            holderName = doc.holderName,
                            updatedAt = now,
                            cloudDocId = doc.id
                        )
                    }
                )
            }
        }

        clubRepository.updateClubSlogan(clubId, slogan)
        meeting?.duesPaymentMethod?.takeIf { it.isNotBlank() }?.let { raw ->
            clubSettingsRepository.updateDuesPaymentMethod(
                clubId,
                DuesPaymentMethod.fromStorage(raw)
            )
        }
        syncCursorRepository.setLastSyncedAt(meetingId, now)

        // meeting.currentBalance(-10,839,133 등)는 무시하고 내려받은 거래로 원점 재계산
        val calculatedBalance = recalculateAndPersistBalanceAfterDownload(
            clubId = clubId,
            meetingId = meetingId,
            downloadedTransactions = transactions
        )

        return CloudMigrationResult(
            meetingId = meetingId,
            memberCount = members.size,
            transactionCount = transactions.size,
            duesCount = dues.size,
            historyCount = histories.size,
            accountCount = accounts.size,
            eventExpenseCount = eventExpenses.size,
            slogan = slogan,
            syncMode = CloudSyncMode.FULL,
            calculatedBalance = calculatedBalance
        )
    }

    /**
     * 클라우드 내려받기 직후: 서버 currentBalance 무시,
     * 거래 합산으로 잔액을 구해 Room·Firestore에 강제 저장.
     */
    private suspend fun recalculateAndPersistBalanceAfterDownload(
        clubId: Long,
        meetingId: String,
        downloadedTransactions: List<TransactionDoc>
    ): Long {
        val initial = 0L
        var totalIncome = 0L
        var totalExpense = 0L
        for (tx in downloadedTransactions) {
            val (_, income, expense) = resolveTransactionAmounts(tx)
            totalIncome += income.toLong()
            totalExpense += expense.toLong()
        }
        // Room SQL로 한 번 더 검증 (방금 insert한 데이터)
        val sqlIncome = runCatching {
            databaseGateway.clubTransactionDao().getSumIncomeOnce(clubId)
        }.getOrDefault(totalIncome)
        val sqlExpense = runCatching {
            databaseGateway.clubTransactionDao().getSumExpenseOnce(clubId)
        }.getOrDefault(totalExpense)
        val income = if (sqlIncome >= 0L) sqlIncome else totalIncome
        val expense = if (sqlExpense >= 0L) sqlExpense else totalExpense
        val calculated = FreshLedgerBalance.calculate(initial, income, expense)

        databaseGateway.clubSettingsDao().upsert(
            ClubSettingEntity(
                clubId = clubId,
                settingKey = ClubSettingKeys.CURRENT_BALANCE,
                settingValue = calculated.toString(),
                updatedAt = EntityTimestamps.now()
            )
        )
        if (initial > 0L) {
            databaseGateway.clubSettingsDao().upsert(
                ClubSettingEntity(
                    clubId = clubId,
                    settingKey = ClubSettingKeys.INITIAL_BALANCE,
                    settingValue = initial.toString(),
                    updatedAt = EntityTimestamps.now()
                )
            )
        }

        // balance_after 체인 원점 재기입
        val local = databaseGateway.clubTransactionDao().getAllOnce(clubId)
        val ordered = local.sortedWith(
            compareBy<ClubTransactionEntity> { it.date }.thenBy { it.id }
        )
        var running = initial
        val now = EntityTimestamps.now()
        for (tx in ordered) {
            running += tx.incomeAmount.toLong().coerceAtLeast(0L) -
                tx.expenseAmount.toLong().coerceAtLeast(0L)
            val bal = with(FreshLedgerBalance) { running.toDisplayInt() }
            if (tx.balanceAfter != bal) {
                databaseGateway.clubTransactionDao().update(
                    tx.copy(balanceAfter = bal, updatedAt = now)
                )
            }
        }

        runCatching {
            meetingFirestoreRepository.updateBalanceFields(
                meetingId = meetingId,
                currentBalance = calculated,
                initialBalance = initial
            )
        }.onFailure {
            // 오프라인이면 로컬만 갱신된 상태로 둔다.
        }

        return calculated
    }

    private fun resolveTransactionAmounts(
        tx: TransactionDoc
    ): Triple<ClubTransactionType, Int, Int> {
        val type = runCatching { ClubTransactionType.valueOf(tx.type) }
            .getOrDefault(ClubTransactionType.EXPENSE)
        val isTransfer = tx.category.contains("이체") || tx.category.contains("자금 이동")
        val rawIncome = tx.incomeAmount.toLong().coerceAtLeast(0L)
        val rawExpense = tx.expenseAmount.toLong().coerceAtLeast(0L)
        val rawAmount = tx.amount.coerceAtLeast(0L)
        val income = when {
            rawIncome > 0L -> rawIncome
            isTransfer -> rawAmount
            type == ClubTransactionType.INCOME -> rawAmount
            else -> 0L
        }
        val expense = when {
            rawExpense > 0L -> rawExpense
            isTransfer -> rawAmount
            type == ClubTransactionType.EXPENSE && !isTransfer -> rawAmount
            else -> 0L
        }
        return Triple(
            type,
            income.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            expense.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
    }

    private suspend fun isRemoteClubEmpty(meetingId: String): Boolean {
        val members = memberFirestoreRepository.getMemberCount(meetingId)
        if (members > 0) return false
        val txs = transactionFirestoreRepository.getTransactionCount(meetingId)
        if (txs > 0) return false
        val dues = duesFirestoreRepository.getDuesOnce(meetingId)
        if (dues.isNotEmpty()) return false
        val histories = clubHistoryFirestoreRepository.getHistoriesOnce(meetingId)
        if (histories.isNotEmpty()) return false
        val accounts = clubAccountFirestoreRepository.getAccountsOnce(meetingId)
        if (accounts.isNotEmpty()) return false
        val events = eventExpenseFirestoreRepository.getEventExpensesOnce(meetingId)
        return events.isEmpty()
    }

    private suspend fun clearRemoteClubCollections(meetingId: String) {
        batchWriter.deleteAllInCollection(FirestorePaths.members(meetingId))
        batchWriter.deleteAllInCollection(FirestorePaths.transactions(meetingId))
        batchWriter.deleteAllInCollection(FirestorePaths.dues(meetingId))
        batchWriter.deleteAllInCollection(FirestorePaths.histories(meetingId))
        batchWriter.deleteAllInCollection(FirestorePaths.accounts(meetingId))
        batchWriter.deleteAllInCollection(FirestorePaths.eventExpenses(meetingId))
    }

    private suspend fun resolveMemberDocIds(
        clubId: Long,
        changedMembers: List<MemberEntity>,
        fullSync: Boolean
    ): MutableMap<Long, String> {
        val map = LinkedHashMap<Long, String>()
        if (fullSync) {
            changedMembers.forEach { member ->
                map[member.id] = member.cloudDocId.ifBlank {
                    CloudDocIds.member(clubId, member.id)
                }
            }
        } else {
            databaseGateway.memberDao().getAllOnce(clubId).forEach { member ->
                map[member.id] = member.cloudDocId.ifBlank {
                    CloudDocIds.member(clubId, member.id)
                }
            }
        }
        return map
    }

    private suspend fun resolveTransactionDocIds(
        clubId: Long,
        changedTransactions: List<ClubTransactionEntity>,
        fullSync: Boolean
    ): MutableMap<Long, String> {
        val map = LinkedHashMap<Long, String>()
        if (fullSync) {
            changedTransactions.forEach { tx ->
                map[tx.id] = tx.cloudDocId.ifBlank {
                    CloudDocIds.transaction(clubId, tx.id)
                }
            }
        } else {
            databaseGateway.clubTransactionDao().getAllOnce(clubId).forEach { tx ->
                map[tx.id] = tx.cloudDocId.ifBlank {
                    CloudDocIds.transaction(clubId, tx.id)
                }
            }
        }
        return map
    }

    private suspend fun loadChangedDuesRows(
        clubId: Long,
        lastSyncedAt: Long
    ): List<YearlyDuesWithDetails> {
        val yearlyIds = linkedSetOf<Long>()
        databaseGateway.yearlyDuesDao()
            .getYearlyUpdatedSince(clubId, lastSyncedAt)
            .forEach { yearlyIds += it.id }
        databaseGateway.yearlyDuesDao()
            .getYearlyIdsWithDetailUpdatedSince(clubId, lastSyncedAt)
            .forEach { yearlyIds += it }
        databaseGateway.duesPaymentHistoryDao()
            .getYearlyIdsUpdatedSince(clubId, lastSyncedAt)
            .forEach { yearlyIds += it }
        if (yearlyIds.isEmpty()) return emptyList()
        return databaseGateway.yearlyDuesDao()
            .getWithDetailsByIds(clubId, yearlyIds.toList())
    }

    private suspend fun ensureMeetingForManualSync(preferredName: String): String {
        val current = selectedMeetingRepository.selectedMeetingId.first()?.takeIf { it.isNotBlank() }
        if (current != null) {
            val existing = meetingFirestoreRepository.getMeeting(current)
            if (existing != null) return current
        }
        val meetings = meetingFirestoreRepository.getMeetingsOnce()
        val preferred = preferredName.trim().ifBlank { ClubConstants.HANURI_SEED_CLUB_NAME }
        val match = meetings.firstOrNull { it.name.trim() == preferred }
            ?: meetings.firstOrNull {
                ClubConstants.isHanuriSeedClub(it.name) && ClubConstants.isHanuriSeedClub(preferred)
            }
            ?: meetings.firstOrNull()
        if (match != null) {
            selectedMeetingRepository.setSelectedMeetingId(match.id)
            return match.id
        }
        return selectedMeetingRepository.createMeeting(preferred)
    }
}
