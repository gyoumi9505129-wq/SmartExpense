package com.smartexpense.data.repository.club

import androidx.room.withTransaction
import com.smartexpense.data.firebase.CloudDuesGateway
import com.smartexpense.data.firebase.CloudLedgerReadPolicy
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.DuesTerm
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.data.local.entity.club.YearlyDuesEntity
import com.smartexpense.data.local.model.club.MemberDuesStatusRow
import com.smartexpense.data.local.model.club.YearlyDuesAggregateRow
import com.smartexpense.data.local.model.club.YearlyDuesWithDetails
import com.smartexpense.data.mapper.dues.MemberDuesSummary
import com.smartexpense.data.mapper.dues.UnpaidLabelStyle
import com.smartexpense.data.mapper.dues.toMemberDuesSummaries
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.domain.dues.DuesLedgerAutoEntry
import com.smartexpense.domain.dues.DuesLedgerSyncSource
import com.smartexpense.domain.dues.DuesTermSchedule
import com.smartexpense.domain.dues.applyPayment
import com.smartexpense.domain.dues.isPayableUnpaid
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DuesRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val cloudLedgerReadPolicy: CloudLedgerReadPolicy,
    private val cloudDuesGateway: CloudDuesGateway
) {
    private val database get() = databaseGateway.database()
    private val yearlyDuesDao get() = databaseGateway.yearlyDuesDao()
    private val duesPaymentHistoryDao get() = databaseGateway.duesPaymentHistoryDao()
    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val memberDao get() = databaseGateway.memberDao()

    private suspend fun isCloudMode(): Boolean = cloudLedgerReadPolicy.isCloudWriteMode()

    fun observeYearlyDuesWithDetailsFiltered(
        year: Int?,
        memberId: Long?
    ): Flow<List<YearlyDuesWithDetails>> =
        cloudLedgerReadPolicy.observeList(
            cloud = {
                cloudDuesGateway.observeYearlyDuesWithDetails().map { list ->
                    list.filter { row ->
                        (year == null || row.yearlyDues.year == year) &&
                            (memberId == null || row.yearlyDues.memberId == memberId)
                    }
                }
            },
            room = {
                selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
                    yearlyDuesDao.observeYearlyDuesWithDetailsFiltered(
                        clubId = clubId,
                        year = year,
                        memberId = memberId
                    )
                }
            }
        )

    fun observeMemberDuesStatusByYear(year: Int): Flow<List<MemberDuesStatusRow>> =
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
            yearlyDuesDao.observeMemberDuesStatusByYear(clubId, year)
        }

    fun observeYearlyDuesAggregate(year: Int): Flow<List<YearlyDuesAggregateRow>> =
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
            yearlyDuesDao.observeYearlyDuesAggregate(clubId, year)
        }

    fun observeTermTotal(year: Int, term: DuesTerm): Flow<Int> =
        selectedClubRepository.scopedFlow(databaseGateway) { clubId ->
            yearlyDuesDao.observeTermTotal(clubId, year, term)
        }

    fun observeYearTotal(year: Int): Flow<Int> =
        selectedClubRepository.scopedFlow(databaseGateway) { clubId ->
            yearlyDuesDao.observeYearTotal(clubId, year)
        }

    suspend fun getYearTotalOnce(year: Int): Int {
        val clubId = selectedClubRepository.requireSelectedClubId()
        return yearlyDuesDao.getYearTotalOnce(clubId, year)
    }

    fun observeCumulativePaidUpTo(endDate: String, year: Int): Flow<Int> =
        selectedClubRepository.scopedFlow(databaseGateway) { clubId ->
            yearlyDuesDao.observeCumulativePaidUpTo(clubId, endDate, year)
        }

    suspend fun getYearlyDuesWithDetails(id: Long): YearlyDuesWithDetails? {
        if (isCloudMode()) {
            return cloudDuesGateway.getYearlyDuesWithDetails(id)
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        return yearlyDuesDao.getWithDetails(id, clubId)
    }

    suspend fun getYearlyDuesByMemberAndYear(
        memberId: Long,
        year: Int
    ): YearlyDuesWithDetails? {
        if (isCloudMode()) {
            return cloudDuesGateway.getYearlyDuesByMemberAndYear(memberId, year)
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        return yearlyDuesDao.getYearlyDuesByMemberAndYear(
            memberId = memberId,
            year = year,
            clubId = clubId
        )
    }

    /** 신규 연회비 등록: YearlyDues + 회차 저장 후, 납부 처리된 회차는 장부에 기장한다. */
    suspend fun insertYearlyDuesWithDetails(
        yearlyDues: YearlyDuesEntity,
        details: List<DuesDetailEntity>
    ): Long {
        if (isCloudMode()) {
            val newId = cloudDuesGateway.insertYearlyDuesWithDetails(yearlyDues, details)
            ledgerRefreshNotifier.notifyTransactionChanged()
            ledgerRefreshNotifier.notifyDuesChanged()
            return newId
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        val now = EntityTimestamps.now()
        val newId = database.withTransaction {
            val newId = yearlyDuesDao.insertYearlyDues(
                yearlyDues.copy(id = 0, clubId = clubId, updatedAt = now)
            )
            if (details.isNotEmpty()) {
                yearlyDuesDao.insertDetails(
                    details.map {
                        it.copy(id = 0, yearlyDuesId = newId, clubId = clubId, updatedAt = now)
                    }
                )
            }
            // 납부 처리된 회차 → 장부 수입 기장 + 납부 이력 생성(신규이므로 장부 새로 생성)
            yearlyDuesDao.getWithDetails(newId, clubId)?.details
                ?.filter { it.isPaid && it.paidAmount > 0 && it.payDate.isNotBlank() }
                ?.forEach { paidDetail ->
                    applyDetailPaymentWithLedger(
                        clubId = clubId,
                        update = DuesDetailPaymentUpdate(
                            detailId = paidDetail.id,
                            payDate = paidDetail.payDate,
                            paidAmount = paidDetail.paidAmount
                        )
                    )
                }
            newId
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
        ledgerRefreshNotifier.notifyDuesChanged()
        return newId
    }

    /** 연회비 수정: 부모 갱신 후 회차 재구성, 납부 회차는 장부 링크를 보존하며 기장한다. */
    suspend fun updateYearlyDuesWithDetails(
        yearlyDues: YearlyDuesEntity,
        details: List<DuesDetailEntity>
    ) {
        if (isCloudMode()) {
            cloudDuesGateway.updateYearlyDuesWithDetails(yearlyDues, details)
            ledgerRefreshNotifier.notifyTransactionChanged()
            ledgerRefreshNotifier.notifyDuesChanged()
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        val now = EntityTimestamps.now()
        database.withTransaction {
            val existing = yearlyDuesDao.getWithDetails(yearlyDues.id, clubId)
            val newPaidTerms = details.filter { it.isPaid }.map { it.termLabel }.toSet()
            existing?.details
                ?.filter { it.isPaid && it.termLabel !in newPaidTerms }
                ?.forEach { paidDetail ->
                    revertDetailToUnpaid(clubId, paidDetail.id)
                }
            // 유지되는 회차의 분납 이력을 termLabel 기준으로 보존
            val existingHistoriesByTerm = existing?.details.orEmpty().associate { d ->
                d.termLabel to duesPaymentHistoryDao.getByDetailId(d.id, clubId)
            }
            yearlyDuesDao.updateYearlyDues(yearlyDues.copy(clubId = clubId, updatedAt = now))
            yearlyDuesDao.deleteDetailsByYearlyDuesId(yearlyDues.id, clubId)
            if (details.isNotEmpty()) {
                yearlyDuesDao.insertDetails(
                    details.map {
                        it.copy(id = 0, yearlyDuesId = yearlyDues.id, clubId = clubId, updatedAt = now)
                    }
                )
            }
            // 납부 회차 → 장부 반영(기존 분납 유지, 증액분만 신규 기장)
            yearlyDuesDao.getWithDetails(yearlyDues.id, clubId)?.details
                ?.filter { it.paidAmount > 0 && it.payDate.isNotBlank() && !it.isExcluded }
                ?.forEach { paidDetail ->
                    val priorHistories = existingHistoriesByTerm[paidDetail.termLabel].orEmpty()
                    val priorSum = priorHistories.sumOf { it.amount }
                    val paymentUpdates = when {
                        priorHistories.isNotEmpty() && paidDetail.paidAmount > priorSum -> {
                            val delta = paidDetail.paidAmount - priorSum
                            priorHistories.map { h ->
                                DuesPaymentHistoryUpdate(
                                    id = h.id,
                                    payDate = h.payDate,
                                    amount = h.amount,
                                    linkedTransactionId = h.linkedTransactionId
                                )
                            } + DuesPaymentHistoryUpdate(
                                payDate = paidDetail.payDate,
                                amount = delta,
                                linkedTransactionId = null
                            )
                        }
                        priorHistories.isNotEmpty() && paidDetail.paidAmount == priorSum ->
                            priorHistories.map { h ->
                                DuesPaymentHistoryUpdate(
                                    id = h.id,
                                    payDate = h.payDate,
                                    amount = h.amount,
                                    linkedTransactionId = h.linkedTransactionId
                                )
                            }
                        else -> null
                    }
                    applyDetailPaymentWithLedger(
                        clubId = clubId,
                        update = DuesDetailPaymentUpdate(
                            detailId = paidDetail.id,
                            payDate = paidDetail.payDate,
                            paidAmount = paidDetail.paidAmount,
                            payments = paymentUpdates,
                            linkedTransactionId = priorHistories.firstOrNull()?.linkedTransactionId,
                            wasExisting = priorHistories.isNotEmpty()
                        )
                    )
                }
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
        ledgerRefreshNotifier.notifyDuesChanged()
    }

    suspend fun deleteYearlyDues(yearlyDues: YearlyDuesEntity) {
        if (isCloudMode()) {
            cloudDuesGateway.deleteYearlyDues(yearlyDues)
            ledgerRefreshNotifier.notifyTransactionChanged()
            ledgerRefreshNotifier.notifyDuesChanged()
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        database.withTransaction {
            yearlyDuesDao.getWithDetails(yearlyDues.id, clubId)
                ?.details
                ?.filter { it.isPaid }
                ?.forEach { paidDetail ->
                    revertDetailToUnpaid(clubId, paidDetail.id)
                }
            yearlyDuesDao.deleteYearlyDues(yearlyDues.copy(clubId = clubId))
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
        ledgerRefreshNotifier.notifyDuesChanged()
    }

    suspend fun updateDetailPaid(detailId: Long, isPaid: Boolean) {
        if (isCloudMode()) {
            cloudDuesGateway.updateDetailPaid(detailId, isPaid)
            ledgerRefreshNotifier.notifyTransactionChanged()
            ledgerRefreshNotifier.notifyDuesChanged()
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        val detail = yearlyDuesDao.getDetailById(detailId, clubId) ?: return
        val payDate = when {
            isPaid && detail.payDate.isBlank() -> java.time.LocalDate.now().toString()
            !isPaid -> ""
            else -> detail.payDate
        }
        val paidAmount = if (isPaid) detail.amount.toLong() else 0L
        database.withTransaction {
            applyDetailPaymentWithLedger(
                clubId = clubId,
                update = DuesDetailPaymentUpdate(
                    detailId = detailId,
                    payDate = payDate,
                    paidAmount = paidAmount
                )
            )
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
        ledgerRefreshNotifier.notifyDuesChanged()
    }

    fun observePaymentHistoriesByClub(): Flow<Map<Long, List<DuesPaymentHistoryEntity>>> =
        cloudLedgerReadPolicy.observeList(
            cloud = { cloudDuesGateway.observePaymentHistories() },
            room = {
                selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
                    duesPaymentHistoryDao.observeByClubId(clubId)
                }
            }
        ).map { rows -> rows.groupBy { it.duesDetailId } }

    suspend fun getPaymentHistoriesForDetail(detailId: Long): List<DuesPaymentHistoryEntity> {
        if (isCloudMode()) {
            return cloudDuesGateway.getPaymentHistoriesOnce()
                .filter { it.duesDetailId == detailId }
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        return duesPaymentHistoryDao.getByDetailId(detailId, clubId)
    }

    /**
     * 납부됐지만 장부에 연결되지 않은 회비를 찾아 자동 기장한다.
     * 회차 목표액이 잔여액으로 깨진 경우도 함께 복구한다.
     */
    suspend fun ensureMissingPaymentLedgers() {
        if (isCloudMode()) {
            val repaired = cloudDuesGateway.repairCorruptedDuesDocuments()
            val patched = cloudDuesGateway.ensureMissingPaymentLedgers()
            if (repaired <= 0 && patched <= 0) return
            ledgerRefreshNotifier.notifyTransactionChanged()
            ledgerRefreshNotifier.notifyDuesChanged()
        }
    }

    /** 깨진 회비 목표액·분납 이력을 장부 기준으로 복구 */
    suspend fun repairCorruptedDuesDocuments() {
        if (!isCloudMode()) return
        val repaired = cloudDuesGateway.repairCorruptedDuesDocuments()
        if (repaired <= 0) return
        ledgerRefreshNotifier.notifyDuesChanged()
        ledgerRefreshNotifier.notifyTransactionChanged()
    }

    suspend fun updateDetailPayments(updates: List<DuesDetailPaymentUpdate>) {
        if (isCloudMode()) {
            cloudDuesGateway.updateDetailPayments(updates)
            ledgerRefreshNotifier.notifyTransactionChanged()
            ledgerRefreshNotifier.notifyDuesChanged()
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        database.withTransaction {
            updates.forEach { update ->
                applyDetailPaymentWithLedger(clubId = clubId, update = update)
            }
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
        ledgerRefreshNotifier.notifyDuesChanged()
    }

    suspend fun getUnpaidSummariesForExport(year: Int? = null): List<MemberDuesSummary> {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val records = yearlyDuesDao.getAllWithDetailsOnce(clubId)
            .let { list ->
                if (year == null) list else list.filter { it.yearlyDues.year == year }
            }
        val memberNameById = memberDao.getAllOnce(clubId).associate { it.id to it.name }
        return records
            .toMemberDuesSummaries(memberNameById, UnpaidLabelStyle.DESCRIPTIVE)
            .filter { !it.isFullyPaid && it.totalUnpaidAmount > 0 }
    }

    /**
     * PDF ?? ??? ????? DB??? **???(ACTIVE) ?????* ???????
     * ?????????SQL ?????? ??????, ?? ????????/0?????????????
     */
    suspend fun getAllMemberDuesSummariesForExport(): List<MemberDuesSummary> {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val activeMembers = memberDao.getActiveMembersOnce(clubId)
        if (activeMembers.isEmpty()) return emptyList()

        val activeMemberIds = activeMembers.map { it.id }.toSet()
        val memberNameById = activeMembers.associate { it.id to it.name }
        val summariesById = yearlyDuesDao.getAllWithDetailsOnce(clubId)
            .asSequence()
            .filter { it.yearlyDues.memberId in activeMemberIds }
            .toList()
            .toMemberDuesSummaries(memberNameById, UnpaidLabelStyle.DESCRIPTIVE)
            .associateBy { it.memberId }

        return activeMembers.map { member ->
            val existing = summariesById[member.id]
            if (existing == null || existing.totalUnpaidAmount <= 0 || existing.isFullyPaid) {
                MemberDuesSummary(
                    memberId = member.id,
                    memberName = member.name,
                    totalUnpaidAmount = 0,
                    unpaidDetails = "???",
                    isFullyPaid = true,
                    defaultYearlyDuesId = existing?.defaultYearlyDuesId ?: 0L,
                    defaultYearlyDuesYear = existing?.defaultYearlyDuesYear ?: 0
                )
            } else {
                existing
            }
        }.sortedWith(
            compareByDescending<MemberDuesSummary> { it.totalUnpaidAmount }
                .thenBy { it.memberName }
        )
    }

    suspend fun getAllWithDetailsOnce(): List<YearlyDuesWithDetails> {
        val clubId = selectedClubRepository.requireSelectedClubId()
        return yearlyDuesDao.getAllWithDetailsOnce(clubId)
    }

    suspend fun getUnpaidDetailsForMember(memberId: Long): List<DuesDetailOption> {
        if (isCloudMode()) {
            return cloudDuesGateway.getUnpaidDetailsForMember(memberId)
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        return yearlyDuesDao.getAllWithDetailsOnce(clubId)
            .asSequence()
            .filter { it.yearlyDues.memberId == memberId }
            .flatMap { record ->
                record.details
                    .filter { it.isPayableUnpaid() }
                    .map { detail ->
                        DuesDetailOption(
                            detailId = detail.id,
                            memberId = memberId,
                            year = record.yearlyDues.year,
                            termLabel = detail.termLabel,
                            amount = detail.amount,
                            paidAmount = detail.paidAmount
                        )
                    }
            }
            .sortedWith(compareBy({ it.year }, { it.termLabel }))
            .toList()
    }

    /**
     * 장부에서 정기 회비를 납부할 수 있도록, 해당 연도 회비가 없으면 만들고
     * 하반기 가입자의 상반기만 제외로 맞춘다.
     */
    suspend fun ensurePayableDuesForMember(
        memberId: Long,
        year: Int,
        paymentMethod: DuesPaymentMethod,
        totalTargetAmount: Int,
        joinDateRaw: String?,
        meetingName: String?,
        meetingCreatedAtRaw: String?
    ) {
        val start = DuesTermSchedule.effectiveMembershipStart(
            joinDateRaw = joinDateRaw,
            meetingName = meetingName,
            meetingCreatedAtRaw = meetingCreatedAtRaw
        )
        val existing = getYearlyDuesByMemberAndYear(memberId, year)
        if (existing == null) {
            val labels = DuesTermSchedule.termLabelsFor(paymentMethod)
            if (labels.isEmpty() || totalTargetAmount <= 0) return
            val perTerm = (totalTargetAmount / labels.size).coerceAtLeast(0)
            val details = labels.map { label ->
                val excluded = DuesTermSchedule.isTermBeforeMembershipStart(
                    duesYear = year,
                    method = paymentMethod,
                    termLabel = label,
                    membershipStart = start
                )
                DuesDetailEntity(
                    clubId = 0,
                    termLabel = label,
                    amount = perTerm,
                    paidAmount = 0L,
                    isPaid = false,
                    isExcluded = excluded,
                    payDate = ""
                )
            }
            insertYearlyDuesWithDetails(
                YearlyDuesEntity(
                    clubId = 0,
                    memberId = memberId,
                    year = year,
                    totalTargetAmount = totalTargetAmount,
                    paymentMethod = paymentMethod
                ),
                details
            )
            return
        }
        val reconciled = existing.details.map { detail ->
            if (detail.isPaid || detail.paidAmount > 0L) {
                detail
            } else {
                val shouldExclude = DuesTermSchedule.isTermBeforeMembershipStart(
                    duesYear = year,
                    method = existing.yearlyDues.paymentMethod,
                    termLabel = detail.termLabel,
                    membershipStart = start
                )
                if (detail.isExcluded == shouldExclude) {
                    detail
                } else {
                    detail.copy(isExcluded = shouldExclude, isPaid = false, payDate = "")
                }
            }
        }
        if (reconciled != existing.details) {
            updateYearlyDuesWithDetails(existing.yearlyDues, reconciled)
        }
    }

    suspend fun getDetailOption(detailId: Long): DuesDetailOption? {
        if (isCloudMode()) {
            return cloudDuesGateway.getDetailOption(detailId)
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        val detail = yearlyDuesDao.getDetailById(detailId, clubId) ?: return null
        val yearlyDues = yearlyDuesDao.getWithDetails(detail.yearlyDuesId, clubId)?.yearlyDues ?: return null
        return DuesDetailOption(
            detailId = detail.id,
            memberId = yearlyDues.memberId,
            year = yearlyDues.year,
            termLabel = detail.termLabel,
            amount = detail.amount,
            paidAmount = detail.paidAmount
        )
    }

    /**
     * 장부에서 정기회비 수입을 저장하면서 회비 분납 이력을 추가합니다.
     * 거래 금액이 해당 회차의 분납금으로 반영되며, history ↔ transaction 1:1 연결됩니다.
     */
    suspend fun saveTransactionWithDuesPayment(
        transaction: ClubTransactionEntity,
        duesDetailIds: List<Long>,
        payDate: String
    ): Long {
        if (isCloudMode()) {
            val txId = cloudDuesGateway.saveTransactionWithDuesPayment(
                transaction = transaction,
                duesDetailIds = duesDetailIds,
                payDate = payDate
            )
            ledgerRefreshNotifier.notifyTransactionChanged()
            ledgerRefreshNotifier.notifyDuesChanged()
            return txId
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        val primaryDetailId = duesDetailIds.singleOrNull()
        val installmentAmount = when (transaction.type) {
            ClubTransactionType.INCOME -> transaction.incomeAmount.toLong()
            ClubTransactionType.EXPENSE -> transaction.expenseAmount.toLong()
        }.coerceAtLeast(0L)
        val txId = database.withTransaction {
            val insertedId = clubTransactionDao.insert(
                transaction.copy(
                    clubId = clubId,
                    linkedDuesDetailId = primaryDetailId ?: transaction.linkedDuesDetailId,
                    updatedAt = EntityTimestamps.now()
                )
            )
            if (duesDetailIds.size == 1) {
                appendInstallmentFromLedger(
                    clubId = clubId,
                    detailId = duesDetailIds.first(),
                    payDate = payDate,
                    amount = installmentAmount,
                    transactionId = insertedId
                )
            } else {
                // 자동매칭 등 복수 회차: 각 회차 잔여 전액을 분납으로 반영
                duesDetailIds.forEach { detailId ->
                    val detail = yearlyDuesDao.getDetailById(detailId, clubId) ?: return@forEach
                    val remaining = (detail.amount - detail.paidAmount).coerceAtLeast(0).toLong()
                    if (remaining <= 0L) return@forEach
                    appendInstallmentFromLedger(
                        clubId = clubId,
                        detailId = detailId,
                        payDate = payDate,
                        amount = remaining,
                        transactionId = insertedId
                    )
                }
            }
            insertedId
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
        ledgerRefreshNotifier.notifyDuesChanged()
        return txId
    }

    /**
     * 장부 거래 삭제 시, 연결된 분납 이력만 제거하고 회비 집계를 재계산합니다.
     */
    suspend fun deleteLedgerTransactionWithDuesRevert(transaction: ClubTransactionEntity) {
        if (isCloudMode()) {
            cloudDuesGateway.deleteLedgerTransactionWithDuesRevert(transaction)
            ledgerRefreshNotifier.notifyDuesChanged()
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        database.withTransaction {
            val linkedHistories = duesPaymentHistoryDao.getAllByLinkedTransactionId(transaction.id, clubId)
            if (linkedHistories.isNotEmpty()) {
                val detailIds = linkedHistories.map { it.duesDetailId }.distinct()
                linkedHistories.forEach { history ->
                    duesPaymentHistoryDao.deleteById(history.id, clubId)
                }
                detailIds.forEach { detailId ->
                    recalculateDetailFromHistories(clubId, detailId)
                }
            } else {
                resolveLinkedDetailFromTransaction(clubId, transaction)?.let { detailId ->
                    val txAmount = when (transaction.type) {
                        ClubTransactionType.INCOME -> transaction.incomeAmount.toLong()
                        ClubTransactionType.EXPENSE -> transaction.expenseAmount.toLong()
                    }.coerceAtLeast(0L)
                    val histories = duesPaymentHistoryDao.getByDetailId(detailId, clubId)
                    if (histories.isNotEmpty() && txAmount > 0L) {
                        val match = histories.lastOrNull {
                            it.amount == txAmount &&
                                (it.payDate == transaction.date || it.linkedTransactionId == null)
                        } ?: histories.lastOrNull { it.amount == txAmount }
                        if (match != null) {
                            duesPaymentHistoryDao.deleteById(match.id, clubId)
                            recalculateDetailFromHistories(clubId, detailId)
                        } else {
                            // 금액 매칭 실패 시에도 회차 전체 미납 초기화는 하지 않음
                            val detail = yearlyDuesDao.getDetailById(detailId, clubId)
                            if (detail != null && detail.paidAmount > 0L) {
                                val newPaid = (detail.paidAmount - txAmount).coerceAtLeast(0L)
                                yearlyDuesDao.updateDetail(
                                    detail.applyPayment(
                                        payDate = if (newPaid > 0L) detail.payDate else "",
                                        paidAmount = newPaid
                                    )
                                )
                            }
                        }
                    } else {
                        val detail = yearlyDuesDao.getDetailById(detailId, clubId)
                        if (detail != null && detail.paidAmount > 0L && txAmount > 0L) {
                            val newPaid = (detail.paidAmount - txAmount).coerceAtLeast(0L)
                            yearlyDuesDao.updateDetail(
                                detail.applyPayment(
                                    payDate = if (newPaid > 0L) detail.payDate else "",
                                    paidAmount = newPaid
                                )
                            )
                        }
                    }
                }
            }
            clubTransactionDao.delete(transaction.copy(clubId = clubId))
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
        ledgerRefreshNotifier.notifyDuesChanged()
    }

    private suspend fun appendInstallmentFromLedger(
        clubId: Long,
        detailId: Long,
        payDate: String,
        amount: Long,
        transactionId: Long
    ) {
        if (amount <= 0L || payDate.isBlank()) return
        val detail = yearlyDuesDao.getDetailById(detailId, clubId) ?: return
        val existing = duesPaymentHistoryDao.getByDetailId(detailId, clubId)
        if (existing.any { it.linkedTransactionId == transactionId }) return
        val currentPaid = maxOf(detail.paidAmount, existing.sumOf { it.amount })
        val remaining = (detail.amount - currentPaid).coerceAtLeast(0).toLong()
        require(amount <= remaining) {
            "납부 금액이 잔여 회비(${remaining}원)를 초과합니다."
        }
        val now = EntityTimestamps.now()
        duesPaymentHistoryDao.insert(
            DuesPaymentHistoryEntity(
                clubId = clubId,
                duesDetailId = detailId,
                payDate = payDate,
                amount = amount,
                updatedAt = now,
                linkedTransactionId = transactionId
            )
        )
        val newPaid = currentPaid + amount
        yearlyDuesDao.updateDetail(
            detail.applyPayment(payDate = payDate, paidAmount = newPaid)
        )
    }

    private suspend fun applyDetailPaymentWithLedger(
        clubId: Long,
        update: DuesDetailPaymentUpdate,
        syncSource: DuesLedgerSyncSource = DuesLedgerSyncSource.FROM_DUES_PAYMENT
    ) {
        val detail = yearlyDuesDao.getDetailById(update.detailId, clubId) ?: return
        val existingHistories = duesPaymentHistoryDao.getByDetailId(update.detailId, clubId)
        val payments = update.payments
            ?.map { payment ->
                payment.copy(
                    payDate = payment.payDate.trim(),
                    amount = payment.amount.coerceAtLeast(0)
                )
            }
            ?.filter { it.amount > 0 && it.payDate.isNotBlank() }
            .orEmpty()

        val paidAmount = if (payments.isNotEmpty()) {
            payments.sumOf { it.amount }
        } else {
            update.paidAmount.coerceAtLeast(0)
        }
        val payDate = if (payments.isNotEmpty()) {
            payments.maxOf { it.payDate }
        } else {
            update.payDate
        }

        val updatedDetail = detail.applyPayment(
            payDate = payDate,
            paidAmount = paidAmount
        )
        yearlyDuesDao.updateDetail(updatedDetail)

        if (syncSource == DuesLedgerSyncSource.FROM_LEDGER_ENTRY) {
            // 장부 경로에서는 appendInstallmentFromLedger가 history를 관리한다.
            return
        }

        val yearlyDues = yearlyDuesDao.getWithDetails(detail.yearlyDuesId, clubId)?.yearlyDues
        val member = yearlyDues?.let { memberDao.getById(it.memberId, clubId) }

        val usedExistingIds = mutableSetOf<Long>()
        data class Planned(
            val payDate: String,
            val amount: Long,
            val linkedTxId: Long?
        )
        val planned = if (payments.isNotEmpty()) {
            payments.map { payment ->
                val matched = when {
                    payment.id > 0L ->
                        existingHistories.firstOrNull { it.id == payment.id }
                    else -> null
                } ?: existingHistories.firstOrNull { old ->
                    old.id !in usedExistingIds &&
                        old.payDate == payment.payDate &&
                        old.amount == payment.amount
                }
                matched?.let { usedExistingIds += it.id }
                Planned(
                    payDate = payment.payDate,
                    amount = payment.amount,
                    linkedTxId = matched?.linkedTransactionId
                        ?: payment.linkedTransactionId
                )
            }
        } else if (paidAmount > 0 && payDate.isNotBlank()) {
            val matched = existingHistories.firstOrNull()
            matched?.let { usedExistingIds += it.id }
            listOf(
                Planned(
                    payDate = payDate,
                    amount = paidAmount,
                    linkedTxId = matched?.linkedTransactionId
                        ?: update.linkedTransactionId
                )
            )
        } else {
            emptyList()
        }

        // 제거된 분납 → 연결된 장부 거래 삭제
        existingHistories.filter { it.id !in usedExistingIds }.forEach { old ->
            old.linkedTransactionId?.let { txId ->
                clubTransactionDao.getById(txId, clubId)?.let { clubTransactionDao.delete(it) }
            }
        }

        val leftoverTx = clubTransactionDao.findAllByLinkedDuesDetail(clubId, detail.id)
            .toMutableList()
        duesPaymentHistoryDao.deleteByDetailId(update.detailId, clubId)
        val historyNow = EntityTimestamps.now()
        planned.forEach { item ->
            val reusedTxId = item.linkedTxId ?: leftoverTx.removeFirstOrNull()?.id
            if (reusedTxId != null) {
                leftoverTx.removeAll { it.id == reusedTxId }
            }
            val txId = when {
                reusedTxId != null && yearlyDues != null && member != null ->
                    upsertInstallmentLedger(
                        clubId = clubId,
                        detail = updatedDetail,
                        memberId = yearlyDues.memberId,
                        memberName = member.name,
                        year = yearlyDues.year,
                        payDate = item.payDate,
                        amount = item.amount,
                        existingTxId = reusedTxId
                    )
                yearlyDues != null && member != null &&
                    item.amount > 0 && item.payDate.isNotBlank() && !detail.isExcluded ->
                    upsertInstallmentLedger(
                        clubId = clubId,
                        detail = updatedDetail,
                        memberId = yearlyDues.memberId,
                        memberName = member.name,
                        year = yearlyDues.year,
                        payDate = item.payDate,
                        amount = item.amount,
                        existingTxId = null
                    )
                else -> reusedTxId
            }
            duesPaymentHistoryDao.insert(
                DuesPaymentHistoryEntity(
                    clubId = clubId,
                    duesDetailId = update.detailId,
                    payDate = item.payDate,
                    amount = item.amount,
                    updatedAt = historyNow,
                    linkedTransactionId = txId
                )
            )
        }
        leftoverTx.forEach { clubTransactionDao.delete(it) }
        // 잔액 재계산/네트워크 동기화는 반드시 트랜잭션 커밋 이후에 호출자에서 수행한다.
        // (Room withTransaction 내부에서 Firestore I/O·중첩 트랜잭션을 돌리면 교착이 발생함)
    }

    private suspend fun recalculateDetailFromHistories(clubId: Long, detailId: Long) {
        val detail = yearlyDuesDao.getDetailById(detailId, clubId) ?: return
        val histories = duesPaymentHistoryDao.getByDetailId(detailId, clubId)
        val paidAmount = histories.sumOf { it.amount }
        val payDate = histories
            .filter { it.amount > 0 && it.payDate.isNotBlank() }
            .maxOfOrNull { it.payDate }
            .orEmpty()
        yearlyDuesDao.updateDetail(
            detail.applyPayment(payDate = payDate, paidAmount = paidAmount)
        )
    }

    private suspend fun revertDetailToUnpaid(clubId: Long, detailId: Long) {
        val detail = yearlyDuesDao.getDetailById(detailId, clubId) ?: return
        val histories = duesPaymentHistoryDao.getByDetailId(detailId, clubId)
        histories.forEach { history ->
            history.linkedTransactionId?.let { txId ->
                clubTransactionDao.getById(txId, clubId)?.let { clubTransactionDao.delete(it) }
            }
        }
        duesPaymentHistoryDao.deleteByDetailId(detailId, clubId)
        yearlyDuesDao.updateDetail(
            detail.applyPayment(payDate = "", paidAmount = 0L)
        )
        clubTransactionDao.findAllByLinkedDuesDetail(clubId, detailId)
            .forEach { clubTransactionDao.delete(it) }
    }

    private suspend fun resolveLinkedDetailFromTransaction(
        clubId: Long,
        transaction: ClubTransactionEntity
    ): Long? {
        transaction.linkedDuesDetailId?.let { return it }
        if (transaction.type != ClubTransactionType.INCOME) return null
        if (transaction.category != ClubCategory.REGULAR_DUES) return null
        val memberId = transaction.targetMemberId ?: return null
        val note = transaction.note ?: return null
        val memberName = memberDao.getById(memberId, clubId)?.name ?: return null

        val termLabel = when {
            note.contains("하반기") -> "하반기"
            note.contains("상반기") -> "상반기"
            else -> return null
        }
        val years = (
            Regex("""(20\d{2})년?""").findAll(note).map { it.groupValues[1].toInt() } +
                Regex("""(?<!\d)(\d{2})년""").findAll(note).map { 2000 + it.groupValues[1].toInt() }
            ).distinct()
        for (year in years) {
            if (!DuesLedgerAutoEntry.noteMatchesMemberTerm(note, memberName, year, termLabel)) {
                continue
            }
            yearlyDuesDao.getYearlyDuesByMemberAndYear(
                memberId = memberId,
                year = year,
                clubId = clubId
            )?.details?.firstOrNull { it.termLabel == termLabel }?.id?.let { return it }
        }
        return null
    }

    private suspend fun upsertInstallmentLedger(
        clubId: Long,
        detail: DuesDetailEntity,
        memberId: Long,
        memberName: String,
        year: Int,
        payDate: String,
        amount: Long,
        existingTxId: Long?
    ): Long {
        val note = DuesLedgerAutoEntry.buildPaymentNote(
            memberName = memberName,
            year = year,
            termLabel = detail.termLabel
        )
        val now = EntityTimestamps.now()
        val built = DuesLedgerAutoEntry.buildIncomeTransaction(
            clubId = clubId,
            payDate = payDate,
            paidAmount = amount,
            memberId = memberId,
            note = note,
            linkedDuesDetailId = detail.id
        ).copy(updatedAt = now)

        if (existingTxId != null) {
            val existing = clubTransactionDao.getById(existingTxId, clubId)
            if (existing != null) {
                clubTransactionDao.update(
                    existing.copy(
                        date = payDate,
                        incomeAmount = built.incomeAmount,
                        expenseAmount = 0,
                        targetMemberId = memberId,
                        note = note,
                        linkedDuesDetailId = detail.id,
                        category = ClubCategory.REGULAR_DUES,
                        type = ClubTransactionType.INCOME,
                        updatedAt = now
                    )
                )
                return existingTxId
            }
        }
        return clubTransactionDao.insert(built)
    }
}

data class DuesPaymentHistoryUpdate(
    val id: Long = 0L,
    val payDate: String,
    val amount: Long,
    val linkedTransactionId: Long? = null,
)

data class DuesDetailPaymentUpdate(
    val detailId: Long,
    val payDate: String,
    val paidAmount: Long,
    val payments: List<DuesPaymentHistoryUpdate>? = null,
    // 총액 단건(payments 미지정) 경로에서 기존 장부 거래를 이어붙일 때 사용
    val linkedTransactionId: Long? = null,
    // 편집 전 이미 납부 이력이 있던 회차인지. 장부 기장 여부에는 사용하지 않는다.
    val wasExisting: Boolean = false,
)
