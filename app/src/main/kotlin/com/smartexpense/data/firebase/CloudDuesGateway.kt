package com.smartexpense.data.firebase

import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.firestore.DuesDetailDoc
import com.smartexpense.data.firebase.firestore.DuesDoc
import com.smartexpense.data.firebase.firestore.DuesPaymentDoc
import com.smartexpense.data.firebase.firestore.DuesFirestoreRepository
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.YearlyDuesEntity
import com.smartexpense.data.local.model.club.YearlyDuesWithDetails
import com.smartexpense.data.repository.club.DuesDetailOption
import com.smartexpense.data.repository.club.DuesDetailPaymentUpdate
import com.smartexpense.data.repository.club.SelectedMeetingRepository
import com.smartexpense.domain.dues.DuesLedgerAutoEntry
import com.smartexpense.domain.dues.isEffectivelyPaid
import com.smartexpense.domain.dues.isPayableUnpaid
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CloudDuesGateway @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository,
    private val duesFirestoreRepository: DuesFirestoreRepository,
    private val cloudLedgerGateway: CloudLedgerGateway,
    private val idMapper: FirestoreIdMapper
) {
    fun observeYearlyDuesWithDetails(): Flow<List<YearlyDuesWithDetails>> =
        selectedMeetingRepository.selectedMeetingId.flatMapLatest { meetingId ->
            if (meetingId.isNullOrBlank() || authRepository.currentUser() == null) {
                flowOf(emptyList())
            } else {
                duesFirestoreRepository.observeDues(meetingId).map { docs ->
                    docs.map { it.toYearlyDuesWithDetails() }
                        .sortedWith(
                            compareByDescending<YearlyDuesWithDetails> { it.yearlyDues.year }
                                .thenBy { it.yearlyDues.memberId }
                        )
                }
            }
        }

    fun observePaymentHistories(): Flow<List<DuesPaymentHistoryEntity>> =
        selectedMeetingRepository.selectedMeetingId.flatMapLatest { meetingId ->
            if (meetingId.isNullOrBlank() || authRepository.currentUser() == null) {
                flowOf(emptyList())
            } else {
                duesFirestoreRepository.observeDues(meetingId).map { docs ->
                    docs.flatMap { doc -> doc.toPaymentHistoryEntities() }
                }
            }
        }

    suspend fun replaceAllDues(docs: List<DuesDoc>) {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        duesFirestoreRepository.deleteAllDues(meetingId)
        docs.forEach { duesFirestoreRepository.upsertDues(meetingId, it) }
    }

    suspend fun getYearlyDuesWithDetails(id: Long): YearlyDuesWithDetails? =
        loadAllYearly().find { it.yearlyDues.id == id }

    suspend fun getYearlyDuesByMemberAndYear(
        memberId: Long,
        year: Int
    ): YearlyDuesWithDetails? =
        loadAllYearly().find {
            it.yearlyDues.memberId == memberId && it.yearlyDues.year == year
        }

    suspend fun getDetailOption(detailId: Long): DuesDetailOption? {
        val row = findDetail(detailId) ?: return null
        val paymentSum = row.detail.payments.filter { it.amount > 0 }.sumOf { it.amount }
        val paid = maxOf(row.detail.paidAmount, paymentSum)
        return DuesDetailOption(
            detailId = detailId,
            memberId = row.memberLongId,
            year = row.doc.year,
            termLabel = row.detail.termLabel,
            amount = row.detail.amount,
            paidAmount = paid
        )
    }

    suspend fun getUnpaidDetailsForMember(memberId: Long): List<DuesDetailOption> {
        val meetingId = runCatching { selectedMeetingRepository.requireSelectedMeetingId() }
            .getOrNull()
            ?: return emptyList()
        return duesFirestoreRepository.getDuesOnce(meetingId)
            .asSequence()
            .filter { doc ->
                doc.memberId.isNotBlank() && idMapper.memberLongId(doc.memberId) == memberId
            }
            .flatMap { doc ->
                doc.details.asSequence().mapNotNull { detail ->
                    if (detail.isExcluded) return@mapNotNull null
                    val paymentSum = detail.payments.filter { it.amount > 0 }.sumOf { it.amount }
                    val paid = maxOf(detail.paidAmount, paymentSum)
                    // isPaid 플래그가 어긋나도 잔여가 있으면 납부 대상으로 노출
                    if (detail.amount <= 0 || paid >= detail.amount) return@mapNotNull null
                    val detailKey = detailDocKey(doc.id, detail)
                    DuesDetailOption(
                        detailId = idMapper.duesDetailLongId(detailKey),
                        memberId = memberId,
                        year = doc.year,
                        termLabel = detail.termLabel,
                        amount = detail.amount,
                        paidAmount = paid
                    )
                }
            }
            .sortedWith(compareBy({ it.year }, { it.termLabel }))
            .toList()
    }

    suspend fun insertYearlyDuesWithDetails(
        yearlyDues: YearlyDuesEntity,
        details: List<DuesDetailEntity>
    ): Long {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val memberDocId = idMapper.memberDocId(yearlyDues.memberId)
            ?: error("회원 문서 ID를 찾을 수 없습니다. 화면을 새로고침해 주세요.")
        val detailDocs = details.map { detail ->
            val payments = buildInitialPayments(detail)
            DuesDetailDoc(
                id = "",
                termLabel = detail.termLabel,
                amount = detail.amount,
                paidAmount = detail.paidAmount,
                isPaid = detail.isEffectivelyPaid(),
                isExcluded = detail.isExcluded,
                payDate = detail.payDate,
                payments = payments
            )
        }
        val savedId = duesFirestoreRepository.upsertDues(
            meetingId,
            DuesDoc(
                id = "",
                memberId = memberDocId,
                year = yearlyDues.year,
                totalTargetAmount = yearlyDues.totalTargetAmount,
                paymentMethod = yearlyDues.paymentMethod.name,
                details = detailDocs
            )
        )
        val yearlyLongId = idMapper.duesLongId(savedId)
        // 납부된 회차 → 장부 기장 + payment.linkedTransactionId 갱신
        val saved = duesFirestoreRepository.getDuesOnce(meetingId).first { it.id == savedId }
        val memberName = cloudLedgerGateway.getMember(yearlyDues.memberId)?.name.orEmpty()
        val syncedDetails = saved.details.map { detail ->
            syncPaymentsWithLedger(
                duesDocId = saved.id,
                detail = detail,
                memberId = yearlyDues.memberId,
                memberName = memberName,
                year = yearlyDues.year,
                createMissingLedger = true
            )
        }
        duesFirestoreRepository.upsertDues(meetingId, saved.copy(details = syncedDetails))
        return yearlyLongId
    }

    suspend fun updateYearlyDuesWithDetails(
        yearlyDues: YearlyDuesEntity,
        details: List<DuesDetailEntity>
    ) {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val duesDocId = idMapper.duesDocId(yearlyDues.id)
            ?: error("회비 문서 ID를 찾을 수 없습니다. 화면을 새로고침해 주세요.")
        val existing = duesFirestoreRepository.getDuesOnce(meetingId).firstOrNull { it.id == duesDocId }
            ?: error("클라우드 회비 문서를 찾을 수 없습니다.")
        val memberDocId = idMapper.memberDocId(yearlyDues.memberId)
            ?: existing.memberId
        val existingByTerm = existing.details.associateBy { it.termLabel }
        val newPaidTerms = details.filter { it.isEffectivelyPaid() || it.paidAmount > 0 }
            .map { it.termLabel }
            .toSet()

        // 납부 해제된 회차 → 연결 장부 삭제
        existing.details
            .filter { it.isPaid && it.termLabel !in newPaidTerms }
            .forEach { detail ->
                detail.payments.mapNotNull { it.linkedTransactionId }.forEach { txDocId ->
                    deleteLedgerByDocId(txDocId)
                }
            }

        val memberName = cloudLedgerGateway.getMember(yearlyDues.memberId)?.name.orEmpty()
        val expectedAmountByTerm = expectedTermAmounts(
            paymentMethod = yearlyDues.paymentMethod.name,
            totalTargetAmount = yearlyDues.totalTargetAmount,
            details = existing.details
        )
        val ledgerCache = runCatching { cloudLedgerGateway.getAllTransactionsOnce() }
            .getOrDefault(emptyList())
        val nextDetails = details.map { detail ->
            val prior = existingByTerm[detail.termLabel]
            val detailId = prior?.id?.takeIf { it.isNotBlank() }.orEmpty()
            val safeAmount = maxOf(
                detail.amount,
                prior?.amount ?: 0,
                detail.paidAmount.toInt().coerceAtLeast(0),
                expectedAmountByTerm[detail.termLabel] ?: 0
            )
            val payments = mergePaymentsPreservingInstallments(
                prior = prior,
                newPaidAmount = detail.paidAmount,
                newPayDate = detail.payDate
            )
            val paidFromPayments = payments.sumOf { it.amount }.takeIf { payments.isNotEmpty() }
                ?: detail.paidAmount
            val draft = DuesDetailDoc(
                id = detailId,
                termLabel = detail.termLabel,
                amount = safeAmount,
                paidAmount = paidFromPayments,
                isPaid = !detail.isExcluded && safeAmount > 0 && paidFromPayments >= safeAmount,
                isExcluded = detail.isExcluded,
                payDate = payments.maxOfOrNull { it.payDate }?.takeIf { it.isNotBlank() }
                    ?: detail.payDate,
                payments = payments
            )
            if (!draft.isExcluded && draft.paidAmount > 0 && draft.payDate.isNotBlank()) {
                syncPaymentsWithLedger(
                    duesDocId = duesDocId,
                    detail = draft,
                    memberId = yearlyDues.memberId,
                    memberName = memberName,
                    year = yearlyDues.year,
                    createMissingLedger = true,
                    ledgerCache = ledgerCache
                )
            } else {
                draft.copy(
                    payments = emptyList(),
                    isPaid = false,
                    payDate = "",
                    paidAmount = 0L,
                    isExcluded = draft.isExcluded
                )
            }
        }
        duesFirestoreRepository.upsertDues(
            meetingId,
            DuesDoc(
                id = duesDocId,
                memberId = memberDocId,
                year = yearlyDues.year,
                totalTargetAmount = yearlyDues.totalTargetAmount,
                paymentMethod = yearlyDues.paymentMethod.name,
                details = nextDetails
            )
        )
    }

    suspend fun deleteYearlyDues(yearlyDues: YearlyDuesEntity) {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val duesDocId = idMapper.duesDocId(yearlyDues.id)
            ?: error("회비 문서 ID를 찾을 수 없습니다.")
        val existing = duesFirestoreRepository.getDuesOnce(meetingId).firstOrNull { it.id == duesDocId }
            ?: return
        existing.details.forEach { detail ->
            detail.payments.mapNotNull { it.linkedTransactionId }.forEach { deleteLedgerByDocId(it) }
            // 레거시: detail에만 묶인 장부
            findLedgerByLinkedDetail(detailDocKey(existing.id, detail)).forEach { tx ->
                cloudLedgerGateway.deleteTransaction(tx)
            }
        }
        duesFirestoreRepository.deleteDues(meetingId, duesDocId)
    }

    suspend fun updateDetailPayments(updates: List<DuesDetailPaymentUpdate>) {
        val ledgerCache = runCatching { cloudLedgerGateway.getAllTransactionsOnce() }
            .getOrDefault(emptyList())
        updates.forEach { applyDetailPayment(it, createMissingLedger = true, ledgerCache = ledgerCache) }
    }

    suspend fun updateDetailPaid(detailId: Long, isPaid: Boolean) {
        val found = findDetail(detailId) ?: return
        val payDate = when {
            isPaid && found.detail.payDate.isBlank() -> java.time.LocalDate.now().toString()
            !isPaid -> ""
            else -> found.detail.payDate
        }
        val paidAmount = if (isPaid) found.detail.amount.toLong() else 0L
        applyDetailPayment(
            DuesDetailPaymentUpdate(
                detailId = detailId,
                payDate = payDate,
                paidAmount = paidAmount
            ),
            createMissingLedger = true
        )
    }

    /**
     * 장부에서 정기회비 수입 저장 → 회비 분납 이력 반영.
     */
    suspend fun saveTransactionWithDuesPayment(
        transaction: ClubTransactionEntity,
        duesDetailIds: List<Long>,
        payDate: String
    ): Long {
        val amount = when (transaction.type) {
            ClubTransactionType.INCOME -> transaction.incomeAmount.toLong()
            ClubTransactionType.EXPENSE -> transaction.expenseAmount.toLong()
        }.coerceAtLeast(0L)
        val primaryDetailId = duesDetailIds.singleOrNull()
        val ensuredDetailId = primaryDetailId?.let { detailId ->
            findDetail(detailId)?.let { found ->
                idMapper.duesDetailLongId(detailDocKey(found.doc.id, found.detail))
            } ?: detailId
        }
        val txToInsert = if (ensuredDetailId != null) {
            transaction.copy(linkedDuesDetailId = ensuredDetailId)
        } else {
            transaction
        }
        val insertedId = cloudLedgerGateway.insertTransaction(txToInsert)
        val txDocId = idMapper.transactionDocId(insertedId)
            ?: error("장부 거래 문서 ID를 찾을 수 없습니다.")

        if (duesDetailIds.size == 1) {
            appendInstallment(
                detailId = duesDetailIds.first(),
                payDate = payDate,
                amount = amount,
                linkedTxDocId = txDocId
            )
        } else {
            duesDetailIds.forEach { detailId ->
                val opt = getDetailOption(detailId) ?: return@forEach
                val remaining = opt.remainingAmount.toLong()
                if (remaining <= 0L) return@forEach
                appendInstallment(
                    detailId = detailId,
                    payDate = payDate,
                    amount = remaining,
                    linkedTxDocId = txDocId
                )
            }
        }
        return insertedId
    }

    suspend fun deleteLedgerTransactionWithDuesRevert(transaction: ClubTransactionEntity) {
        val txDocId = idMapper.transactionDocId(transaction.id)
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val allDues = duesFirestoreRepository.getDuesOnce(meetingId)
        val txAmount = when (transaction.type) {
            ClubTransactionType.INCOME -> transaction.incomeAmount.toLong()
            ClubTransactionType.EXPENSE -> transaction.expenseAmount.toLong()
        }.coerceAtLeast(0L)
        val txDate = transaction.date.trim()
        val txNote = transaction.note.orEmpty()
        val memberNameByDocId = allDues.associate { doc ->
            val memberLongId = if (doc.memberId.isBlank()) 0L else idMapper.memberLongId(doc.memberId)
            doc.id to if (memberLongId > 0L) {
                cloudLedgerGateway.getMember(memberLongId)?.name.orEmpty()
            } else {
                ""
            }
        }

        allDues.forEach { doc ->
            var docChanged = false
            val memberName = memberNameByDocId[doc.id].orEmpty()
            val nextDetails = doc.details.map { detail ->
                val detailKey = detailDocKey(doc.id, detail)
                val detailLongId = idMapper.duesDetailLongId(detailKey)

                // 1) payments[].linkedTransactionId 로 연결된 분납만 제거
                if (txDocId != null) {
                    val keptByLink = detail.payments.filter { it.linkedTransactionId != txDocId }
                    if (keptByLink.size != detail.payments.size) {
                        docChanged = true
                        return@map detailWithPayments(detail, keptByLink)
                    }
                }

                val linkedToThisDetail =
                    transaction.linkedDuesDetailId != null &&
                        transaction.linkedDuesDetailId == detailLongId
                val noteMatchesTerm =
                    txAmount > 0L &&
                        DuesLedgerAutoEntry.noteMatchesMemberTerm(
                            note = txNote,
                            memberName = memberName,
                            year = doc.year,
                            termLabel = detail.termLabel
                        )

                if (!linkedToThisDetail && !noteMatchesTerm) {
                    return@map detail
                }

                // 2) 링크 없는 레거시: 동일 금액·일자 분납 1건만 제거 (전체 초기화 금지)
                if (detail.payments.isNotEmpty() && txAmount > 0L) {
                    val matchIdx = detail.payments.indexOfLast { payment ->
                        payment.amount == txAmount &&
                            (payment.payDate == txDate || payment.linkedTransactionId.isNullOrBlank())
                    }.takeIf { it >= 0 }
                        ?: detail.payments.indexOfLast { it.amount == txAmount }.takeIf { it >= 0 }

                    if (matchIdx != null) {
                        val kept = detail.payments.filterIndexed { index, _ -> index != matchIdx }
                        docChanged = true
                        return@map detailWithPayments(detail, kept)
                    }
                }

                // 3) payments 비어 있고 paidAmount만 있는 경우: 해당 금액만큼만 차감
                if (detail.payments.isEmpty() && detail.paidAmount > 0L && txAmount > 0L) {
                    val newPaid = (detail.paidAmount - txAmount).coerceAtLeast(0L)
                    docChanged = true
                    return@map detail.copy(
                        paidAmount = newPaid,
                        payDate = if (newPaid > 0L) detail.payDate else "",
                        isPaid = newPaid >= detail.amount && detail.amount > 0 && !detail.isExcluded,
                        payments = emptyList()
                    )
                }

                detail
            }
            if (docChanged) {
                duesFirestoreRepository.upsertDues(meetingId, doc.copy(details = nextDetails))
            }
        }
        cloudLedgerGateway.deleteTransaction(transaction)
    }

    private fun detailWithPayments(
        detail: DuesDetailDoc,
        payments: List<DuesPaymentDoc>
    ): DuesDetailDoc {
        val paid = payments.sumOf { it.amount }
        val payDate = payments
            .filter { it.amount > 0 && it.payDate.isNotBlank() }
            .maxOfOrNull { it.payDate }
            .orEmpty()
        return detail.copy(
            payments = payments,
            paidAmount = paid,
            payDate = payDate,
            isPaid = paid >= detail.amount && detail.amount > 0 && !detail.isExcluded
        )
    }

    private suspend fun applyDetailPayment(
        update: DuesDetailPaymentUpdate,
        createMissingLedger: Boolean,
        ledgerCache: List<ClubTransactionEntity>? = null
    ) {
        val found = findDetail(update.detailId) ?: return
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val payments = update.payments
            ?.map {
                it.copy(
                    payDate = it.payDate.trim(),
                    amount = it.amount.coerceAtLeast(0)
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

        val existingPayments = found.detail.payments
        val used = mutableSetOf<Int>()
        data class Planned(
            val payDate: String,
            val amount: Long,
            val linkedTxDocId: String?
        )
        val planned = if (payments.isNotEmpty()) {
            payments.map { payment ->
                val paymentLinkDocId = payment.linkedTransactionId?.let { idMapper.transactionDocId(it) }
                val matchByLink = paymentLinkDocId?.let { linkId ->
                    existingPayments.indices.firstOrNull { idx ->
                        idx !in used && existingPayments[idx].linkedTransactionId == linkId
                    }
                }
                val matchIdx = matchByLink ?: existingPayments.indices.firstOrNull { idx ->
                    idx !in used &&
                        existingPayments[idx].payDate == payment.payDate &&
                        existingPayments[idx].amount == payment.amount
                }
                matchIdx?.let { used += it }
                val matched = matchIdx?.let { existingPayments[it] }
                Planned(
                    payDate = payment.payDate,
                    amount = payment.amount,
                    linkedTxDocId = matched?.linkedTransactionId ?: paymentLinkDocId
                )
            }
        } else if (paidAmount > 0 && payDate.isNotBlank()) {
            val matched = existingPayments.firstOrNull()
            matched?.let { used += existingPayments.indexOf(it) }
            listOf(
                Planned(
                    payDate = payDate,
                    amount = paidAmount,
                    linkedTxDocId = matched?.linkedTransactionId
                        ?: update.linkedTransactionId?.let { idMapper.transactionDocId(it) }
                )
            )
        } else {
            emptyList()
        }

        // 제거된 분납의 장부 삭제
        existingPayments.forEachIndexed { idx, old ->
            if (idx !in used) {
                old.linkedTransactionId?.let { deleteLedgerByDocId(it) }
            }
        }
        val detailKey = detailDocKey(found.doc.id, found.detail)
        val leftoverLedgers = collectReusableLedgers(
            detailKey = detailKey,
            detailLongId = idMapper.duesDetailLongId(detailKey),
            memberId = found.memberLongId,
            memberName = cloudLedgerGateway.getMember(found.memberLongId)?.name.orEmpty(),
            year = found.doc.year,
            termLabel = found.detail.termLabel,
            ledgerCache = ledgerCache
        ).toMutableList()
        if (planned.isEmpty()) {
            // 이 회차에 묶인·비고 일치 장부는 모두 제거 (삭제 후 복구 방지)
            leftoverLedgers.forEach { cloudLedgerGateway.deleteTransaction(it) }
            val nextDetail = found.detail.copy(
                paidAmount = 0L,
                payDate = "",
                isPaid = false,
                payments = emptyList()
            )
            val nextDetails = found.doc.details.map { d ->
                if (detailDocKey(found.doc.id, d) == detailKey) nextDetail else d
            }
            duesFirestoreRepository.upsertDues(meetingId, found.doc.copy(details = nextDetails))
            return
        }
        val memberName = cloudLedgerGateway.getMember(found.memberLongId)?.name.orEmpty()
        val ledgerDetail = found.detail.copy(
            paidAmount = paidAmount,
            payDate = payDate,
            isPaid = paidAmount >= found.detail.amount &&
                found.detail.amount > 0 &&
                !found.detail.isExcluded
        )
        val shouldCreateLedger = createMissingLedger && !found.detail.isExcluded
        val detailLongId = idMapper.duesDetailLongId(detailKey)
        val nextPayments = planned.map { item ->
            val reused = takeReusableLedger(
                pool = leftoverLedgers,
                preferredDocId = item.linkedTxDocId,
                payDate = item.payDate,
                amount = item.amount
            )
            val reusedDocId = reused?.let { idMapper.transactionDocId(it.id) } ?: item.linkedTxDocId
            val txDocId = when {
                reusedDocId != null ->
                    upsertInstallmentLedger(
                        detailDocId = detailKey,
                        detail = ledgerDetail,
                        memberId = found.memberLongId,
                        memberName = memberName,
                        year = found.doc.year,
                        payDate = item.payDate,
                        amount = item.amount,
                        existingTxDocId = reusedDocId
                    )
                shouldCreateLedger && item.amount > 0 && item.payDate.isNotBlank() ->
                    upsertInstallmentLedger(
                        detailDocId = detailKey,
                        detail = ledgerDetail,
                        memberId = found.memberLongId,
                        memberName = memberName,
                        year = found.doc.year,
                        payDate = item.payDate,
                        amount = item.amount,
                        existingTxDocId = null
                    )
                else -> null
            }
            DuesPaymentDoc(
                payDate = item.payDate,
                amount = item.amount,
                linkedTransactionId = txDocId
            )
        }
        leftoverLedgers.forEach { cloudLedgerGateway.deleteTransaction(it) }

        val nextDetail = found.detail.copy(
            paidAmount = paidAmount,
            payDate = if (paidAmount > 0) payDate else "",
            isPaid = paidAmount >= found.detail.amount &&
                found.detail.amount > 0 &&
                !found.detail.isExcluded,
            payments = nextPayments
        )
        val nextDetails = found.doc.details.map { d ->
            if (detailDocKey(found.doc.id, d) == detailKey) nextDetail else d
        }
        duesFirestoreRepository.upsertDues(meetingId, found.doc.copy(details = nextDetails))
    }

    private suspend fun appendInstallment(
        detailId: Long,
        payDate: String,
        amount: Long,
        linkedTxDocId: String
    ) {
        if (amount <= 0L || payDate.isBlank()) return
        val found = findDetail(detailId) ?: return
        if (found.detail.payments.any { it.linkedTransactionId == linkedTxDocId }) return

        // 목표액이 잔여액으로 깨진 경우 먼저 복구
        val healedDoc = healTermAmounts(found.doc)
        val healedDetail = healedDoc.details.firstOrNull {
            detailDocKey(healedDoc.id, it) == detailDocKey(found.doc.id, found.detail)
        } ?: found.detail

        val paymentSum = healedDetail.payments.filter { it.amount > 0 }.sumOf { it.amount }
        val currentPaid = maxOf(healedDetail.paidAmount, paymentSum)
        val remaining = (healedDetail.amount - currentPaid).coerceAtLeast(0).toLong()
        require(amount <= remaining) {
            "납부 금액이 잔여 회비(${remaining}원)를 초과합니다."
        }
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val payments = healedDetail.payments + DuesPaymentDoc(
            payDate = payDate,
            amount = amount,
            linkedTransactionId = linkedTxDocId
        )
        val newPaid = currentPaid + amount
        val nextDetail = healedDetail.copy(
            payments = payments,
            paidAmount = newPaid,
            payDate = payDate,
            isPaid = newPaid >= healedDetail.amount &&
                healedDetail.amount > 0 &&
                !healedDetail.isExcluded
        )
        val detailKey = detailDocKey(healedDoc.id, healedDetail)
        val nextDetails = healedDoc.details.map { d ->
            if (detailDocKey(healedDoc.id, d) == detailKey) nextDetail else d
        }
        duesFirestoreRepository.upsertDues(meetingId, healedDoc.copy(details = nextDetails))
    }

    private suspend fun syncPaymentsWithLedger(
        duesDocId: String,
        detail: DuesDetailDoc,
        memberId: Long,
        memberName: String,
        year: Int,
        createMissingLedger: Boolean,
        ledgerCache: List<ClubTransactionEntity>? = null
    ): DuesDetailDoc {
        if (detail.isExcluded || detail.paidAmount <= 0L || detail.payDate.isBlank()) {
            return detail.copy(
                payments = emptyList(),
                isPaid = false,
                paidAmount = 0L,
                payDate = "",
                isExcluded = detail.isExcluded
            )
        }
        val detailKey = if (detail.id.isNotBlank()) detail.id else "${duesDocId}_${detail.termLabel}"
        val detailLongId = idMapper.duesDetailLongId(detailKey)
        val basePayments = detail.payments.ifEmpty {
            listOf(DuesPaymentDoc(payDate = detail.payDate, amount = detail.paidAmount))
        }

        val reusablePool = collectReusableLedgers(
            detailKey = detailKey,
            detailLongId = detailLongId,
            memberId = memberId,
            memberName = memberName,
            year = year,
            termLabel = detail.termLabel,
            ledgerCache = ledgerCache
        ).toMutableList()

        val synced = basePayments.map { payment ->
            val preferredDocId = payment.linkedTransactionId
            val reused = takeReusableLedger(
                pool = reusablePool,
                preferredDocId = preferredDocId,
                payDate = payment.payDate,
                amount = payment.amount
            )
            val existingDocId = reused?.let { idMapper.transactionDocId(it.id) } ?: preferredDocId
            val txDocId = when {
                existingDocId != null ->
                    upsertInstallmentLedger(
                        detailDocId = detailKey,
                        detail = detail,
                        memberId = memberId,
                        memberName = memberName,
                        year = year,
                        payDate = payment.payDate,
                        amount = payment.amount,
                        existingTxDocId = existingDocId
                    )
                createMissingLedger ->
                    upsertInstallmentLedger(
                        detailDocId = detailKey,
                        detail = detail,
                        memberId = memberId,
                        memberName = memberName,
                        year = year,
                        payDate = payment.payDate,
                        amount = payment.amount,
                        existingTxDocId = null
                    )
                else -> payment.linkedTransactionId
            }
            payment.copy(linkedTransactionId = txDocId)
        }

        // 이 회차에 링크돼 있었으나 분납에서 빠진 고아 전표만 삭제
        reusablePool
            .filter { it.linkedDuesDetailId == detailLongId }
            .forEach { orphan ->
                runCatching { cloudLedgerGateway.deleteTransaction(orphan) }
            }

        return detail.copy(payments = synced)
    }

    private suspend fun collectReusableLedgers(
        detailKey: String,
        detailLongId: Long,
        memberId: Long,
        memberName: String,
        year: Int,
        termLabel: String,
        ledgerCache: List<ClubTransactionEntity>? = null
    ): List<ClubTransactionEntity> {
        val byLink = findLedgerByLinkedDetail(detailKey)
        // 전체 장부 재조회는 비용이 크므로, 캐시가 있을 때만 비고 매칭을 한다.
        val source = ledgerCache ?: return byLink
        val byNote = source.filter { tx ->
            tx.type == ClubTransactionType.INCOME &&
                tx.category == ClubCategory.REGULAR_DUES &&
                tx.incomeAmount > 0 &&
                (tx.linkedDuesDetailId == null || tx.linkedDuesDetailId == detailLongId) &&
                (
                    (tx.targetMemberId != null && tx.targetMemberId == memberId) ||
                        (memberName.isNotBlank() && tx.note.orEmpty().contains(memberName))
                    ) &&
                DuesLedgerAutoEntry.noteMatchesMemberTerm(
                    note = tx.note.orEmpty(),
                    memberName = memberName,
                    year = year,
                    termLabel = termLabel
                )
        }
        return (byLink + byNote).distinctBy { it.id }
    }

    private fun takeReusableLedger(
        pool: MutableList<ClubTransactionEntity>,
        preferredDocId: String?,
        payDate: String,
        amount: Long
    ): ClubTransactionEntity? {
        if (!preferredDocId.isNullOrBlank()) {
            val preferred = pool.firstOrNull { idMapper.transactionDocId(it.id) == preferredDocId }
            if (preferred != null) {
                pool.remove(preferred)
                return preferred
            }
        }
        val exact = pool.firstOrNull {
            it.date == payDate && it.incomeAmount.toLong() == amount
        }
        if (exact != null) {
            pool.remove(exact)
            return exact
        }
        val byAmount = pool.firstOrNull { it.incomeAmount.toLong() == amount }
        if (byAmount != null) {
            pool.remove(byAmount)
            return byAmount
        }
        return null
    }

    private suspend fun upsertInstallmentLedger(
        detailDocId: String,
        detail: DuesDetailDoc,
        memberId: Long,
        memberName: String,
        year: Int,
        payDate: String,
        amount: Long,
        existingTxDocId: String?
    ): String {
        val note = DuesLedgerAutoEntry.buildPaymentNote(
            memberName = memberName,
            year = year,
            termLabel = detail.termLabel
        )
        val detailLongId = idMapper.duesDetailLongId(detailDocId)
        val built = DuesLedgerAutoEntry.buildIncomeTransaction(
            clubId = 0L,
            payDate = payDate,
            paidAmount = amount,
            memberId = memberId,
            note = note,
            linkedDuesDetailId = detailLongId
        )
        if (existingTxDocId != null) {
            val existingLong = idMapper.transactionLongId(existingTxDocId)
            val existing = cloudLedgerGateway.getTransaction(existingLong)
            if (existing != null) {
                cloudLedgerGateway.updateTransaction(
                    existing.copy(
                        date = payDate,
                        incomeAmount = built.incomeAmount,
                        expenseAmount = 0,
                        targetMemberId = memberId,
                        note = note,
                        linkedDuesDetailId = detailLongId,
                        category = ClubCategory.REGULAR_DUES,
                        type = ClubTransactionType.INCOME
                    )
                )
                return existingTxDocId
            }
        }
        val newLongId = cloudLedgerGateway.insertTransaction(built)
        return idMapper.transactionDocId(newLongId)
            ?: error("장부 거래 문서 ID를 찾을 수 없습니다.")
    }

    private suspend fun deleteLedgerByDocId(txDocId: String) {
        val longId = idMapper.transactionLongId(txDocId)
        val tx = cloudLedgerGateway.getTransaction(longId) ?: return
        cloudLedgerGateway.deleteTransaction(tx)
    }

    private suspend fun findLedgerByLinkedDetail(detailDocId: String): List<ClubTransactionEntity> =
        runCatching {
            cloudLedgerGateway.getTransactionsByLinkedDuesDetail(detailDocId)
        }.getOrDefault(emptyList())

    private fun takeLinkedLedgerDocId(
        preferredDocId: String?,
        leftoverLedgers: MutableList<ClubTransactionEntity>
    ): String? {
        if (!preferredDocId.isNullOrBlank()) {
            leftoverLedgers.removeAll { idMapper.transactionDocId(it.id) == preferredDocId }
            return preferredDocId
        }
        val taken = leftoverLedgers.removeFirstOrNull() ?: return null
        return idMapper.transactionDocId(taken.id)
    }

    /**
     * 이미 납부됐지만 장부 거래가 없는 회차를 찾아 기장한다.
     * @return 문서를 갱신한 건수
     */
    suspend fun ensureMissingPaymentLedgers(): Int {
        val meetingId = runCatching { selectedMeetingRepository.requireSelectedMeetingId() }
            .getOrNull()
            ?: return 0
        val docs = duesFirestoreRepository.getDuesOnce(meetingId)
        var patched = 0
        for (doc in docs) {
            val memberLongId = if (doc.memberId.isBlank()) 0L else idMapper.memberLongId(doc.memberId)
            val memberName = cloudLedgerGateway.getMember(memberLongId)?.name.orEmpty()
            var changed = false
            val nextDetails = doc.details.map { detail ->
                if (detail.isExcluded || detail.paidAmount <= 0L || detail.payDate.isBlank()) {
                    return@map detail
                }
                val payments = detail.payments.ifEmpty {
                    listOf(DuesPaymentDoc(payDate = detail.payDate, amount = detail.paidAmount))
                }
                if (payments.all { !it.linkedTransactionId.isNullOrBlank() }) {
                    return@map detail
                }
                val synced = syncPaymentsWithLedger(
                    duesDocId = doc.id,
                    detail = detail,
                    memberId = memberLongId,
                    memberName = memberName,
                    year = doc.year,
                    createMissingLedger = true
                )
                if (synced != detail) changed = true
                synced
            }
            if (changed) {
                duesFirestoreRepository.upsertDues(meetingId, doc.copy(details = nextDetails))
                patched++
            }
        }
        return patched
    }

    suspend fun getPaymentHistoriesOnce(): List<DuesPaymentHistoryEntity> {
        val meetingId = runCatching { selectedMeetingRepository.requireSelectedMeetingId() }
            .getOrNull()
            ?: return emptyList()
        return duesFirestoreRepository.getDuesOnce(meetingId).flatMap { it.toPaymentHistoryEntities() }
    }

    private suspend fun loadAllYearly(): List<YearlyDuesWithDetails> {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        return duesFirestoreRepository.getDuesOnce(meetingId).map { it.toYearlyDuesWithDetails() }
    }

    private data class FoundDetail(
        val doc: DuesDoc,
        val detail: DuesDetailDoc,
        val memberLongId: Long
    )

    private suspend fun findDetail(detailLongId: Long): FoundDetail? {
        val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
        val docs = duesFirestoreRepository.getDuesOnce(meetingId)
        for (doc in docs) {
            for (detail in doc.details) {
                val key = detailDocKey(doc.id, detail)
                if (idMapper.duesDetailLongId(key) == detailLongId) {
                    val memberLong = if (doc.memberId.isBlank()) {
                        0L
                    } else {
                        idMapper.memberLongId(doc.memberId)
                    }
                    return FoundDetail(doc, detail, memberLong)
                }
            }
        }
        return null
    }

    private fun buildInitialPayments(detail: DuesDetailEntity): List<DuesPaymentDoc> =
        if (detail.paidAmount > 0 && detail.payDate.isNotBlank()) {
            listOf(DuesPaymentDoc(payDate = detail.payDate, amount = detail.paidAmount))
        } else {
            emptyList()
        }

    private fun DuesDoc.toYearlyDuesWithDetails(): YearlyDuesWithDetails {
        val yearlyId = idMapper.duesLongId(id)
        val memberLong = if (memberId.isBlank()) 0L else idMapper.memberLongId(memberId)
        val yearly = YearlyDuesEntity(
            id = yearlyId,
            clubId = 0L,
            memberId = memberLong,
            year = year,
            totalTargetAmount = totalTargetAmount,
            paymentMethod = runCatching { DuesPaymentMethod.valueOf(paymentMethod) }
                .getOrDefault(DuesPaymentMethod.YEARLY)
        )
        val detailEntities = details.map { detail ->
            val detailKey = detailDocKey(id, detail)
            DuesDetailEntity(
                id = idMapper.duesDetailLongId(detailKey),
                clubId = 0L,
                yearlyDuesId = yearlyId,
                termLabel = detail.termLabel,
                amount = detail.amount,
                paidAmount = detail.paidAmount,
                isPaid = isEffectivelyPaid(
                    isPaid = detail.isPaid,
                    paidAmount = detail.paidAmount,
                    amount = detail.amount,
                    isExcluded = detail.isExcluded
                ),
                isExcluded = detail.isExcluded,
                payDate = detail.payDate
            )
        }
        return YearlyDuesWithDetails(yearlyDues = yearly, details = detailEntities)
    }

    private fun DuesDoc.toPaymentHistoryEntities(): List<DuesPaymentHistoryEntity> =
        details.flatMap { detail ->
            val detailKey = detailDocKey(id, detail)
            val detailLongId = idMapper.duesDetailLongId(detailKey)
            val payments = detail.payments.ifEmpty {
                if (detail.paidAmount > 0 && detail.payDate.isNotBlank()) {
                    listOf(DuesPaymentDoc(payDate = detail.payDate, amount = detail.paidAmount))
                } else {
                    emptyList()
                }
            }
            payments.mapIndexed { index, payment ->
                val paymentKey = "${detailKey}_pay_${index}_${payment.payDate}_${payment.amount}"
                DuesPaymentHistoryEntity(
                    id = idMapper.duesDetailLongId(paymentKey),
                    clubId = 0L,
                    duesDetailId = detailLongId,
                    payDate = payment.payDate,
                    amount = payment.amount,
                    linkedTransactionId = payment.linkedTransactionId
                        ?.let { idMapper.transactionLongId(it) }
                )
            }
        }

    /**
     * 기존 분납 이력을 유지한 채 증액분만 새 결제 건으로 추가한다.
     * (부분납 100,000 → 완납 150,000 시 50,000만 신규 장부 반영)
     * 감액 요청이 와도 기존 분납을 지우지 않는다. (목표액 오입력 보호)
     */
    private fun mergePaymentsPreservingInstallments(
        prior: DuesDetailDoc?,
        newPaidAmount: Long,
        newPayDate: String
    ): List<DuesPaymentDoc> {
        val priorPayments = prior?.payments.orEmpty()
            .filter { it.amount > 0L && it.payDate.isNotBlank() }
        val priorSum = when {
            priorPayments.isNotEmpty() -> priorPayments.sumOf { it.amount }
            else -> prior?.paidAmount?.coerceAtLeast(0L) ?: 0L
        }
        if (newPaidAmount <= 0L) {
            return emptyList()
        }
        val effectivePayDate = newPayDate.ifBlank {
            priorPayments.maxOfOrNull { it.payDate }.orEmpty()
        }
        if (effectivePayDate.isBlank() && priorPayments.isEmpty()) {
            return emptyList()
        }
        if (priorPayments.isNotEmpty() && newPaidAmount <= priorSum) {
            // 감액/동일: 기존 분납 유지 (목표액을 잔여로 바꿔 저장하는 사고 방지)
            return priorPayments
        }
        if (priorSum > 0L && newPaidAmount > priorSum) {
            val delta = newPaidAmount - priorSum
            val kept = if (priorPayments.isNotEmpty()) {
                priorPayments
            } else {
                listOf(
                    DuesPaymentDoc(
                        payDate = prior?.payDate?.takeIf { it.isNotBlank() } ?: effectivePayDate,
                        amount = priorSum,
                        linkedTransactionId = null
                    )
                )
            }
            return kept + DuesPaymentDoc(
                payDate = effectivePayDate,
                amount = delta,
                linkedTransactionId = null
            )
        }
        if (priorPayments.size == 1 && priorPayments[0].amount == newPaidAmount) {
            return priorPayments
        }
        return listOf(
            DuesPaymentDoc(
                payDate = effectivePayDate,
                amount = newPaidAmount,
                linkedTransactionId = priorPayments.singleOrNull()?.linkedTransactionId
            )
        )
    }

    private fun expectedTermAmounts(
        paymentMethod: String,
        totalTargetAmount: Int,
        details: List<DuesDetailDoc>
    ): Map<String, Int> {
        if (totalTargetAmount <= 0) return emptyMap()
        if (!paymentMethod.equals("HALF_YEARLY", ignoreCase = true)) return emptyMap()
        val first = totalTargetAmount / 2
        val second = totalTargetAmount - first
        return details.associate { d ->
            d.termLabel to when (d.termLabel) {
                "상반기" -> first
                "하반기" -> second
                else -> d.amount
            }
        }
    }

    /**
     * 깨진 회차 목표액(잔여액으로 덮인 경우)과 분납 이력을 장부 기준으로 복구한다.
     */
    suspend fun repairCorruptedDuesDocuments(): Int {
        val meetingId = runCatching { selectedMeetingRepository.requireSelectedMeetingId() }
            .getOrNull()
            ?: return 0
        val docs = duesFirestoreRepository.getDuesOnce(meetingId)
        val allRegularDuesIncome = runCatching {
            cloudLedgerGateway.getAllTransactionsOnce()
        }.getOrDefault(emptyList()).filter { tx ->
            tx.type == ClubTransactionType.INCOME &&
                tx.category == ClubCategory.REGULAR_DUES &&
                tx.incomeAmount > 0
        }
        var patched = 0
        for (doc in docs) {
            val healed = recoverPaymentsFromLedger(healTermAmounts(doc), allRegularDuesIncome)
            val normalized = healed.copy(
                details = healed.details.map { detail ->
                    val paid = detail.payments
                        .filter { it.amount > 0 }
                        .sumOf { it.amount }
                        .takeIf { detail.payments.isNotEmpty() }
                        ?: detail.paidAmount
                    detail.copy(
                        paidAmount = paid,
                        payDate = detail.payments
                            .filter { it.amount > 0 && it.payDate.isNotBlank() }
                            .maxOfOrNull { it.payDate }
                            ?: detail.payDate,
                        isPaid = !detail.isExcluded &&
                            detail.amount > 0 &&
                            paid >= detail.amount
                    )
                }
            )
            if (normalized != doc) {
                duesFirestoreRepository.upsertDues(meetingId, normalized)
                patched += 1
            }
        }
        return patched
    }

    private fun healTermAmounts(doc: DuesDoc): DuesDoc {
        if (!doc.paymentMethod.equals("HALF_YEARLY", ignoreCase = true)) return doc
        if (doc.totalTargetAmount <= 0) return doc
        val first = doc.totalTargetAmount / 2
        val second = doc.totalTargetAmount - first
        val next = doc.details.map { d ->
            val expected = when (d.termLabel) {
                "상반기" -> first
                "하반기" -> second
                else -> d.amount
            }
            if (expected > d.amount) {
                d.copy(
                    amount = expected,
                    isPaid = !d.isExcluded && d.paidAmount >= expected && expected > 0
                )
            } else {
                d
            }
        }
        return doc.copy(details = next)
    }

    private suspend fun recoverPaymentsFromLedger(
        doc: DuesDoc,
        allRegularDuesIncome: List<ClubTransactionEntity>
    ): DuesDoc {
        val memberLongId = if (doc.memberId.isBlank()) 0L else idMapper.memberLongId(doc.memberId)
        val memberName = if (memberLongId > 0L) {
            cloudLedgerGateway.getMember(memberLongId)?.name.orEmpty()
        } else {
            ""
        }

        val next = doc.details.map { detail ->
            val key = detailDocKey(doc.id, detail)
            val detailLongId = idMapper.duesDetailLongId(key)

            val byLink = allRegularDuesIncome.filter { it.linkedDuesDetailId == detailLongId }
            val byNote = if (memberName.isBlank()) {
                emptyList()
            } else {
                allRegularDuesIncome.filter { tx ->
                    val note = tx.note.orEmpty()
                    val memberOk =
                        (tx.targetMemberId != null && tx.targetMemberId == memberLongId) ||
                            note.contains(memberName)
                    memberOk &&
                        DuesLedgerAutoEntry.noteMatchesMemberTerm(
                            note = note,
                            memberName = memberName,
                            year = doc.year,
                            termLabel = detail.termLabel
                        )
                }
            }
            val ledgers = (byLink + byNote)
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.date }, { it.id }))
            if (ledgers.isEmpty()) return@map detail

            // 링크 누락 장부는 회비 회차와 다시 연결
            ledgers.forEach { tx ->
                if (tx.linkedDuesDetailId == null) {
                    runCatching {
                        cloudLedgerGateway.updateTransaction(
                            tx.copy(linkedDuesDetailId = detailLongId)
                        )
                    }
                }
            }

            val fromLedger = ledgers.map { tx ->
                DuesPaymentDoc(
                    payDate = tx.date,
                    amount = tx.incomeAmount.toLong(),
                    linkedTransactionId = idMapper.transactionDocId(tx.id)
                )
            }
            val merged = mergeLedgerAndExistingPayments(detail.payments, fromLedger)
            val ledgerSum = merged.sumOf { it.amount }
            val paymentSum = detail.payments.sumOf { it.amount }
            val preferMerged =
                ledgerSum > paymentSum ||
                    merged.size > detail.payments.size ||
                    detail.payments.isEmpty() ||
                    (detail.amount > detail.paidAmount && ledgerSum > detail.paidAmount) ||
                    (detail.paidAmount > 0L && detail.payments.isEmpty())
            if (!preferMerged) return@map detail
            detail.copy(
                payments = merged,
                paidAmount = ledgerSum,
                payDate = merged
                    .filter { it.amount > 0 && it.payDate.isNotBlank() }
                    .maxOfOrNull { it.payDate }
                    .orEmpty(),
                isPaid = !detail.isExcluded && detail.amount > 0 && ledgerSum >= detail.amount
            )
        }
        return doc.copy(details = next)
    }

    /**
     * 장부·기존 분납을 합친다. 동일 장부 링크 또는 (일자+금액) 중복은 1건만 유지.
     */
    private fun mergeLedgerAndExistingPayments(
        existing: List<DuesPaymentDoc>,
        fromLedger: List<DuesPaymentDoc>
    ): List<DuesPaymentDoc> {
        val result = mutableListOf<DuesPaymentDoc>()
        fun addUnique(payment: DuesPaymentDoc) {
            if (payment.amount <= 0L || payment.payDate.isBlank()) return
            val dup = result.any { keep ->
                (!payment.linkedTransactionId.isNullOrBlank() &&
                    keep.linkedTransactionId == payment.linkedTransactionId) ||
                    (keep.payDate == payment.payDate && keep.amount == payment.amount)
            }
            if (!dup) result.add(payment)
        }
        fromLedger.forEach(::addUnique)
        existing.forEach(::addUnique)
        return result.sortedWith(compareBy({ it.payDate }, { it.amount }))
    }

    private fun detailDocKey(duesDocId: String, detail: DuesDetailDoc): String =
        if (detail.id.isNotBlank()) detail.id else "${duesDocId}_${detail.termLabel}"
}
