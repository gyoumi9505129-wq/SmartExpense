package com.smartexpense.data.repository.club

import androidx.room.withTransaction
import com.smartexpense.data.firebase.CloudLedgerGateway
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.local.entity.club.ClubSettingEntity
import com.smartexpense.data.local.entity.club.ClubSettingKeys
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.domain.ledger.FreshLedgerBalance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class BalanceRepairResult(
    val repairedBalance: Long,
    val transactionCount: Int,
    val totalIncome: Long,
    val totalExpense: Long,
    val initialBalance: Long,
    val firestoreUpdated: Boolean,
    val previousMeetingBalance: Long?
)

/**
 * 거래 원장 합산으로 현재 잔액을 재계산합니다.
 * meetings/{id}.currentBalance 값은 계산에 사용하지 않습니다.
 */
@Singleton
class LedgerBalanceSync @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository,
    private val cloudLedgerGateway: CloudLedgerGateway,
    private val meetingFirestoreRepository: MeetingFirestoreRepository
) {
    private val database get() = databaseGateway.database()
    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val clubSettingsDao get() = databaseGateway.clubSettingsDao()

    suspend fun recalculateForSelectedClub() {
        forceRepairCorruptedBalance()
    }

    suspend fun recalculate(clubId: Long) {
        forceRepairCorruptedBalance()
    }

    suspend fun calculateTotalBalance(): Long = forceRepairCorruptedBalance().repairedBalance

    suspend fun calculateTotalBalance(clubId: Long): Long =
        forceRepairCorruptedBalance().repairedBalance

    suspend fun calculateFreshTotalBalance(): Long =
        forceRepairCorruptedBalance().repairedBalance

    /**
     * 잔액 강제 복구:
     * 1) initialBalance (없거나 음수 → 0)  — meeting.currentBalance는 무시
     * 2) Room + Firestore 거래 전체에서 수입/지출 합산
     * 3) fresh = initial + income - expense
     * 4) Room club_settings + Firestore meetings.currentBalance 강제 덮어쓰기
     */
    suspend fun forceRepairCorruptedBalance(): BalanceRepairResult {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val meetingId = selectedMeetingRepository.selectedMeetingId.first()
            ?.takeIf { it.isNotBlank() }

        val previousMeetingBalance = if (meetingId != null) {
            runCatching {
                meetingFirestoreRepository.getMeeting(meetingId)?.currentBalance
            }.getOrNull()
        } else {
            null
        }

        val initial = readInitialBalanceOnly(clubId)
        val (all, income, expense) = resolveAllPeriodSums(clubId, meetingId)
        val fresh = FreshLedgerBalance.calculate(initial, income, expense)

        // 1) 로컬 설정 덮어쓰기
        clubSettingsDao.upsert(
            ClubSettingEntity(
                clubId = clubId,
                settingKey = ClubSettingKeys.CURRENT_BALANCE,
                settingValue = fresh.toString(),
                updatedAt = EntityTimestamps.now()
            )
        )

        // 2) Room 거래 balance_after 체인 원점 재기입 (표시용)
        runCatching {
            database.withTransaction {
                val local = clubTransactionDao.getAllOnce(clubId)
                if (local.isNotEmpty()) {
                    rewriteBalanceAfter(local, initial)
                }
            }
        }

        // 3) Firestore meetings/{id}.currentBalance 강제 덮어쓰기
        var firestoreUpdated = false
        if (meetingId != null) {
            runCatching {
                meetingFirestoreRepository.updateBalanceFields(
                    meetingId = meetingId,
                    currentBalance = fresh,
                    initialBalance = initial
                )
                firestoreUpdated = true
            }.onFailure {
                // 오프라인이면 로컬만 갱신된 상태로 둔다.
            }
        }

        return BalanceRepairResult(
            repairedBalance = fresh,
            transactionCount = all.size,
            totalIncome = income,
            totalExpense = expense,
            initialBalance = initial,
            firestoreUpdated = firestoreUpdated,
            previousMeetingBalance = previousMeetingBalance
        )
    }

    /**
     * Room SQL 합산과 Firestore/Room 엔티티 합산 중,
     * 거래 건수가 많은 쪽을 쓰되 Room SQL이 양수이고 엔티티 합이 비정상 음수면 Room SQL 우선.
     */
    private suspend fun resolveAllPeriodSums(
        clubId: Long,
        meetingId: String?
    ): Triple<List<ClubTransactionEntity>, Long, Long> {
        val roomList = clubTransactionDao.getAllOnce(clubId)
        val cloudList = if (meetingId != null) {
            runCatching { cloudLedgerGateway.getAllTransactionsOnce() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val roomSqlIncome = runCatching { clubTransactionDao.getSumIncomeOnce(clubId) }.getOrDefault(0L)
        val roomSqlExpense = runCatching { clubTransactionDao.getSumExpenseOnce(clubId) }.getOrDefault(0L)

        val preferredList = when {
            cloudList.size > roomList.size -> cloudList
            roomList.isNotEmpty() -> roomList
            else -> cloudList
        }

        var entityIncome = 0L
        var entityExpense = 0L
        for (tx in preferredList) {
            if (com.smartexpense.data.local.entity.club.ClubCategory.isTransferCategory(tx.category)) {
                continue
            }
            entityIncome += tx.incomeAmount.toLong().coerceAtLeast(0L)
            entityExpense += tx.expenseAmount.toLong().coerceAtLeast(0L)
        }

        // Room SQL은 이체를 포함할 수 있어, 엔티티(이체 제외) 합을 우선한다.
        if (preferredList.isEmpty() && roomList.isNotEmpty()) {
            return Triple(roomList, roomSqlIncome, roomSqlExpense)
        }

        return Triple(preferredList, entityIncome, entityExpense)
    }

    /** initialBalance만 읽음. currentBalance는 계산에 사용하지 않음. */
    private suspend fun readInitialBalanceOnly(clubId: Long): Long {
        val fromSettings = clubSettingsDao
            .getEntry(clubId, ClubSettingKeys.INITIAL_BALANCE)
            ?.settingValue
            ?.toLongOrNull()
        return FreshLedgerBalance.sanitizeInitialBalance(fromSettings)
    }

    private suspend fun rewriteBalanceAfter(
        all: List<ClubTransactionEntity>,
        initialBalance: Long
    ) {
        val ordered = all.sortedWith(
            compareBy<ClubTransactionEntity> { it.date }.thenBy { it.id }
        )
        var running = FreshLedgerBalance.sanitizeInitialBalance(initialBalance)
        val now = EntityTimestamps.now()
        for (tx in ordered) {
            if (!com.smartexpense.data.local.entity.club.ClubCategory.isTransferCategory(tx.category)) {
                running += tx.incomeAmount.toLong().coerceAtLeast(0L) -
                    tx.expenseAmount.toLong().coerceAtLeast(0L)
            }
            val bal = with(FreshLedgerBalance) { running.toDisplayInt() }
            if (tx.balanceAfter != bal) {
                clubTransactionDao.update(tx.copy(balanceAfter = bal, updatedAt = now))
            }
        }
    }
}
