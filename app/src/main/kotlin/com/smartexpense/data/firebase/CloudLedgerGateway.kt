package com.smartexpense.data.firebase

import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.firestore.JoinRequestFirestoreRepository
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberDoc
import com.smartexpense.data.firebase.firestore.MemberFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberStatusLabels
import com.smartexpense.data.firebase.firestore.TransactionDoc
import com.smartexpense.data.firebase.firestore.TransactionFirestoreRepository
import com.smartexpense.data.firebase.firestore.UserProfileFirestoreRepository
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.repository.club.SelectedMeetingRepository
import com.smartexpense.domain.ledger.LedgerDate
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room Entity 형태로 UI/기존 ViewModel이 소비할 수 있도록
 * Firestore 실시간 스트림을 어댑트합니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CloudLedgerGateway @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository,
    private val memberFirestoreRepository: MemberFirestoreRepository,
    private val transactionFirestoreRepository: TransactionFirestoreRepository,
    private val meetingFirestoreRepository: MeetingFirestoreRepository,
    private val joinRequestFirestoreRepository: JoinRequestFirestoreRepository,
    private val userProfileFirestoreRepository: UserProfileFirestoreRepository,
    private val idMapper: FirestoreIdMapper,
    private val memberLoginPolicy: MemberLoginPolicy
) {
    private val koreanNameCollator = Collator.getInstance(Locale.KOREA)

    val isCloudSignedIn: Flow<Boolean> = authRepository.authState.map { it != null }

    fun observeMembersAsEntities(): Flow<List<MemberEntity>> =
        selectedMeetingRepository.selectedMeetingId.flatMapLatest { meetingId ->
            if (meetingId.isNullOrBlank() || authRepository.currentUser() == null) {
                flowOf(emptyList())
            } else {
                memberFirestoreRepository.observeMembers(meetingId).map { docs ->
                    docs.map { it.toMemberEntity() }
                        .sortedWith(
                            compareBy<MemberEntity> {
                                when (it.status) {
                                    MemberStatus.ACTIVE -> 0
                                    MemberStatus.DORMANT -> 1
                                    MemberStatus.WITHDRAWN -> 2
                                }
                            }.thenBy(koreanNameCollator) { it.name }
                        )
                }
            }
        }

    fun observeActiveMembersAsEntities(): Flow<List<MemberEntity>> =
        observeMembersAsEntities().map { list ->
            list.filter { it.status == MemberStatus.ACTIVE }
        }

    fun observeTransactionsAsEntities(): Flow<List<ClubTransactionEntity>> =
        selectedMeetingRepository.selectedMeetingId.flatMapLatest { meetingId ->
            if (meetingId.isNullOrBlank() || authRepository.currentUser() == null) {
                flowOf(emptyList())
            } else {
                transactionFirestoreRepository.observeTransactions(meetingId).map { docs ->
                    docs.map { it.toTransactionEntity() }
                }
            }
        }

    /** 월/기간 필터 없이 Firestore 거래 전체를 1회 조회합니다. */
    suspend fun getAllTransactionsOnce(): List<ClubTransactionEntity> {
        val meetingId = selectedMeetingRepository.selectedMeetingId.first()
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        if (authRepository.currentUser() == null) return emptyList()
        return transactionFirestoreRepository.getTransactionsOnce(meetingId)
            .map { it.toTransactionEntity() }
    }

    suspend fun getTransactionsByDateRangeOnce(
        startDate: String,
        endDate: String
    ): List<ClubTransactionEntity> {
        val meetingId = selectedMeetingRepository.selectedMeetingId.first()
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        if (authRepository.currentUser() == null) return emptyList()
        return transactionFirestoreRepository
            .getTransactionsByDateRangeOnce(meetingId, startDate, endDate)
            .map { it.toTransactionEntity() }
    }

    fun observeTransactionsByDateRange(
        startDate: String,
        endDate: String
    ): Flow<List<ClubTransactionEntity>> =
        selectedMeetingRepository.selectedMeetingId.flatMapLatest { meetingId ->
            if (meetingId.isNullOrBlank() || authRepository.currentUser() == null) {
                flowOf(emptyList())
            } else {
                transactionFirestoreRepository
                    .observeTransactionsByDateRange(meetingId, startDate, endDate)
                    .map { docs -> docs.map { it.toTransactionEntity() } }
            }
        }

    suspend fun insertMember(member: MemberEntity): Long {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val docId = memberFirestoreRepository.upsertMember(meetingId, member.toMemberDoc(docId = ""))
        runCatching {
            memberFirestoreRepository.getMember(meetingId, docId)?.let { saved ->
                memberLoginPolicy.syncCloudAccess(meetingId, saved)
            }
        }
        return idMapper.memberLongId(docId)
    }

    suspend fun updateMember(member: MemberEntity) {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val docId = idMapper.memberDocId(member.id)
            ?: throw IllegalStateException("회원 문서 ID를 찾을 수 없습니다. 화면을 새로고침해 주세요.")
        val existing = memberFirestoreRepository.getMember(meetingId, docId)
        val doc = member.toMemberDoc(docId = docId).copy(
            linkedUid = existing?.linkedUid.orEmpty(),
            selfEnrolled = existing?.selfEnrolled == true
        )
        memberFirestoreRepository.upsertMember(meetingId, doc)
        memberLoginPolicy.syncCloudAccess(meetingId, doc)
    }

    suspend fun deleteMember(member: MemberEntity) {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val docId = idMapper.memberDocId(member.id)
            ?: throw IllegalStateException("회원 문서 ID를 찾을 수 없습니다.")
        val existing = memberFirestoreRepository.getMember(meetingId, docId)
        if (existing != null && SystemAdminConfig.isClubMembershipRecord(
                email = existing.email,
                uid = existing.linkedAccountUid(),
                name = existing.name
            )
        ) {
            throw IllegalStateException("시스템관리자는 강제 탈퇴할 수 없습니다.")
        }
        val uid = existing?.linkedAccountUid()
            ?: existing?.email?.trim()?.takeIf { it.isNotBlank() }?.let { email ->
                runCatching { userProfileFirestoreRepository.findUidByEmail(email) }.getOrNull()
            }
            ?: existing?.email?.trim()?.takeIf { it.isNotBlank() }?.let { email ->
                runCatching { joinRequestFirestoreRepository.findAccountUid(meetingId, email) }.getOrNull()
            }
            ?: docId.takeIf { MemberDoc.looksLikeFirebaseUid(it) }
        if (!uid.isNullOrBlank()) {
            meetingFirestoreRepository.purgeMemberAccess(meetingId, uid)
        }
        joinRequestFirestoreRepository.deleteJoinRequestsForAccount(
            meetingId = meetingId,
            uid = uid,
            email = existing?.email ?: member.email
        )
        memberFirestoreRepository.deleteMember(meetingId, docId)
    }

    suspend fun getMember(id: Long): MemberEntity? {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val docId = idMapper.memberDocId(id) ?: return null
        return memberFirestoreRepository.getMember(meetingId, docId)?.toMemberEntity()
    }

    suspend fun insertTransaction(transaction: ClubTransactionEntity): Long {
        val docId = upsertTransaction(transaction.toTransactionDoc(docId = ""))
        return idMapper.transactionLongId(docId)
    }

    suspend fun updateTransaction(transaction: ClubTransactionEntity) {
        val docId = idMapper.transactionDocId(transaction.id)
            ?: throw IllegalStateException("거래 문서 ID를 찾을 수 없습니다. 화면을 새로고침해 주세요.")
        upsertTransaction(transaction.toTransactionDoc(docId = docId))
    }

    suspend fun deleteTransaction(transaction: ClubTransactionEntity) {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val docId = idMapper.transactionDocId(transaction.id)
            ?: throw IllegalStateException("거래 문서 ID를 찾을 수 없습니다.")
        transactionFirestoreRepository.deleteTransaction(meetingId, docId)
    }

    suspend fun getTransaction(id: Long): ClubTransactionEntity? {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val docId = idMapper.transactionDocId(id) ?: return null
        return transactionFirestoreRepository.getTransaction(meetingId, docId)?.toTransactionEntity()
    }

    suspend fun getTransactionsByLinkedDuesDetail(detailDocId: String): List<ClubTransactionEntity> {
        if (detailDocId.isBlank()) return emptyList()
        val meetingId = selectedMeetingRepository.selectedMeetingId.first()
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        if (authRepository.currentUser() == null) return emptyList()
        return transactionFirestoreRepository
            .getTransactionsByLinkedDuesDetailOnce(meetingId, detailDocId)
            .map { it.toTransactionEntity() }
    }

    private suspend fun upsertTransaction(tx: TransactionDoc): String {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        return transactionFirestoreRepository.upsertTransaction(meetingId, tx)
    }

    private fun MemberDoc.toMemberEntity(): MemberEntity {
        val longId = idMapper.memberLongId(id)
        return MemberEntity(
            id = longId,
            clubId = 0L,
            joinDate = joinDate,
            name = name,
            birthDate = birthDate,
            address = address,
            detailAddress = detailAddress,
            residenceRegion = residenceRegion,
            phone = phone,
            email = email,
            status = statusEnum(),
            role = runCatching { MemberRole.valueOf(role) }.getOrDefault(MemberRole.GENERAL),
            isLunarBirth = isLunarBirth,
            suspensionDate = suspensionDate
        )
    }

    private fun MemberEntity.toMemberDoc(docId: String): MemberDoc =
        MemberDoc(
            id = docId,
            name = name,
            status = MemberStatusLabels.toFirestore(status),
            joinDate = joinDate,
            phone = phone,
            birthDate = birthDate,
            address = address,
            detailAddress = detailAddress,
            residenceRegion = residenceRegion,
            email = email,
            role = role.name,
            isLunarBirth = isLunarBirth,
            suspensionDate = suspensionDate
        )

    private fun TransactionDoc.toTransactionEntity(): ClubTransactionEntity {
        val longId = idMapper.transactionLongId(id)
        val parsedType = ClubTransactionType.fromStorage(type)
        val isTransfer = category.contains("이체") || category.contains("자금 이동")
        val rawIncome = incomeAmount.toLong().coerceAtLeast(0L)
        val rawExpense = expenseAmount.toLong().coerceAtLeast(0L)
        val rawAmount = amount.coerceAtLeast(0L)
        val income = when {
            rawIncome > 0L -> rawIncome
            isTransfer -> rawAmount
            parsedType == ClubTransactionType.INCOME -> rawAmount
            else -> 0L
        }
        val expense = when {
            rawExpense > 0L -> rawExpense
            isTransfer -> rawAmount
            parsedType == ClubTransactionType.EXPENSE && !isTransfer -> rawAmount
            else -> 0L
        }
        val resolvedType = when {
            isTransfer -> ClubTransactionType.EXPENSE
            ClubCategory.typeFor(category) != null -> ClubCategory.typeFor(category)!!
            income > 0L && expense == 0L -> ClubTransactionType.INCOME
            expense > 0L && income == 0L -> ClubTransactionType.EXPENSE
            else -> parsedType
        }
        val memberLong = memberId?.let { idMapper.memberLongId(it) }
        return ClubTransactionEntity(
            id = longId,
            clubId = 0L,
            date = LedgerDate.toStorage(date),
            type = resolvedType,
            category = category,
            incomeAmount = income.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            expenseAmount = expense.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            note = note ?: description,
            balanceAfter = balanceAfter,
            targetMemberId = memberLong,
            eventSubCategory = null,
            receiptPath = null,
            accountId = null,
            transferToAccountId = null,
            linkedDuesDetailId = linkedDuesDetailId?.let { idMapper.duesDetailLongId(it) }
        )
    }

    private fun ClubTransactionEntity.toTransactionDoc(docId: String): TransactionDoc {
        val amountValue = when (type) {
            ClubTransactionType.INCOME -> incomeAmount.toLong()
            ClubTransactionType.EXPENSE -> expenseAmount.toLong()
        }
        return TransactionDoc(
            id = docId,
            date = date,
            type = type.name,
            category = category,
            amount = amountValue,
            incomeAmount = incomeAmount,
            expenseAmount = expenseAmount,
            description = note.orEmpty(),
            memberId = targetMemberId?.let { idMapper.memberDocId(it) },
            balanceAfter = balanceAfter,
            note = note,
            linkedDuesDetailId = linkedDuesDetailId?.let { idMapper.duesDetailDocId(it) }
        )
    }
}
