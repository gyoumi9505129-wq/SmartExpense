package com.smartexpense.data.repository.club

import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.data.local.entity.club.EventExpenseEntity
import com.smartexpense.data.local.model.club.EventExpenseSummaryRow
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * 경조사비 도메인 테이블(`event_expenses`) 전용.
 * 통장 잔액 계산에는 절대 사용하지 않습니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EventExpenseRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository
) {
    private val eventExpenseDao get() = databaseGateway.eventExpenseDao()

    fun observeAll(): Flow<List<EventExpenseEntity>> =
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
            eventExpenseDao.observeAll(clubId)
        }

    fun observeSummary(): Flow<List<EventExpenseSummaryRow>> =
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
            eventExpenseDao.observeSummary(clubId)
        }

    fun observeTotalAmount(): Flow<Int> =
        selectedClubRepository.selectedClubId.flatMapLatest { clubId ->
            if (clubId == null) flowOf(0)
            else eventExpenseDao.observeTotalAmount(clubId)
        }

    /**
     * 장부 경조 지출 저장 후 도메인 테이블에 반영합니다.
     * (시드 전용 행과 별개 — linked_transaction_id로 연결)
     */
    suspend fun syncFromLedgerTransaction(tx: ClubTransactionEntity, transactionId: Long) {
        if (!ClubCategory.isCondolenceCategory(tx.category)) return
        val memberId = tx.targetMemberId ?: return
        if (tx.expenseAmount <= 0) return
        val clubId = selectedClubRepository.requireSelectedClubId()
        val existing = eventExpenseDao.getByLinkedTransactionId(clubId, transactionId)
        val entity = EventExpenseEntity(
            id = existing?.id ?: 0L,
            clubId = clubId,
            memberId = memberId,
            date = tx.date,
            eventSubCategory = tx.eventSubCategory ?: "기타",
            amount = tx.expenseAmount,
            note = tx.note,
            linkedTransactionId = transactionId,
            isSeedOnly = false,
            updatedAt = EntityTimestamps.now(),
            cloudDocId = existing?.cloudDocId.orEmpty()
        )
        if (existing == null) {
            eventExpenseDao.insert(entity)
        } else {
            eventExpenseDao.update(entity)
        }
    }

    suspend fun deleteLinkedToTransaction(transactionId: Long) {
        val clubId = runCatching { selectedClubRepository.requireSelectedClubId() }.getOrNull()
            ?: return
        eventExpenseDao.deleteByLinkedTransactionId(clubId, transactionId)
    }
}
