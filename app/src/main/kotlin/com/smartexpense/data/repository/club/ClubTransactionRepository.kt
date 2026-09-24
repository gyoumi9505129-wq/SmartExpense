package com.smartexpense.data.repository.club

import com.smartexpense.data.firebase.CloudLedgerGateway
import com.smartexpense.data.firebase.CloudLedgerReadPolicy
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.data.local.model.club.ClubTransactionCategorySummaryRow
import com.smartexpense.data.local.model.club.ClubTransactionExportRow
import com.smartexpense.data.local.model.club.YearlyClubTransactionSummaryRow
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.domain.ledger.LedgerBalanceCalculator
import com.smartexpense.domain.ledger.LedgerDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ClubTransactionRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val duesRepository: DuesRepository,
    private val eventExpenseRepository: EventExpenseRepository,
    private val cloudLedgerGateway: CloudLedgerGateway,
    private val cloudLedgerReadPolicy: CloudLedgerReadPolicy,
    private val ledgerBalanceSync: LedgerBalanceSync
) {
    private suspend fun isCloudMode(): Boolean = cloudLedgerReadPolicy.isCloudWriteMode()

    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val transferCategory = ClubCategory.TRANSFER

    fun getAllTransactions(): Flow<List<ClubTransactionEntity>> =
        cloudLedgerReadPolicy.observeList(
            cloud = { cloudLedgerGateway.observeTransactionsAsEntities() },
            room = {
                selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
                    clubTransactionDao.getAllTransactions(clubId)
                }
            }
        )

    fun observeAllTransactions(): Flow<List<ClubTransactionEntity>> =
        getAllTransactions()

    /**
     * 전체 기간 거래 1회 조회 (월별 필터 없음).
     * 잔액 원점 계산 전용.
     */
    suspend fun getAllTransactionsOnce(): List<ClubTransactionEntity> {
        return if (isCloudMode()) {
            cloudLedgerGateway.getAllTransactionsOnce()
        } else {
            clubTransactionDao.getAllOnce(selectedClubRepository.requireSelectedClubId())
        }
    }

    /**
     * 상단 현재 잔액 원점 재계산.
     * initial + 전체수입 − 전체지출. 상대 가감 없음.
     */
    suspend fun calculateFreshTotalBalance(): Long =
        ledgerBalanceSync.calculateFreshTotalBalance()

    /** 오염된 meetings.currentBalance 등을 거래 합산으로 강제 복구 */
    suspend fun forceRepairCorruptedBalance() =
        ledgerBalanceSync.forceRepairCorruptedBalance()

    @Deprecated("Use calculateFreshTotalBalance", ReplaceWith("calculateFreshTotalBalance()"))
    suspend fun calculateTotalBalance(): Long = calculateFreshTotalBalance()

    fun observeTransactionsByYear(year: Int): Flow<List<ClubTransactionEntity>> {
        val startDate = "%04d-01-01".format(year)
        val endDate = "%04d-12-31".format(year)
        return cloudLedgerReadPolicy.observeList(
            cloud = { cloudLedgerGateway.observeTransactionsByDateRange(startDate, endDate) },
            room = {
                selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
                    clubTransactionDao.observeByYear(clubId, year)
                }
            }
        )
    }

    fun observeTransactionsByYearAndType(
        year: Int,
        type: ClubTransactionType
    ): Flow<List<ClubTransactionEntity>> =
        observeTransactionsByYear(year).map { list -> list.filter { it.type == type } }

    fun observeTransactionsByYearAndCategory(
        year: Int,
        category: String
    ): Flow<List<ClubTransactionEntity>> =
        observeTransactionsByYear(year).map { list ->
            list.filter { it.category == category }
        }

    fun observeYearlySummary(year: Int): Flow<YearlyClubTransactionSummaryRow?> =
        observeTransactionsByYear(year).map { list ->
            val income = list
                .filter { it.category != transferCategory }
                .sumOf { it.incomeAmount }
            val expense = list
                .filter { it.category != transferCategory }
                .sumOf { it.expenseAmount }
            YearlyClubTransactionSummaryRow(
                year = year,
                totalIncome = income,
                totalExpense = expense
            )
        }

    fun observeCategorySummaryByYear(year: Int): Flow<List<ClubTransactionCategorySummaryRow>> =
        observeTransactionsByYear(year).map { list ->
            list
                .filter { it.category != transferCategory }
                .groupBy { it.category }
                .map { (category, rows) ->
                    ClubTransactionCategorySummaryRow(
                        category = category,
                        totalIncome = rows.sumOf { it.incomeAmount },
                        totalExpense = rows.sumOf { it.expenseAmount }
                    )
                }
                .sortedBy { it.category }
        }

    fun observeTotalIncomeByYear(year: Int): Flow<Int> =
        observeTransactionsByYear(year).map { list ->
            list.filter { it.category != transferCategory }.sumOf { it.incomeAmount }
        }

    fun observeTotalExpenseByYear(year: Int): Flow<Int> =
        observeTransactionsByYear(year).map { list ->
            list.filter { it.category != transferCategory }.sumOf { it.expenseAmount }
        }

    fun observeCumulativeIncomeUpTo(endDate: String): Flow<Int> =
        selectedClubRepository.scopedFlow(databaseGateway) { clubId ->
            clubTransactionDao.observeCumulativeIncomeUpTo(clubId, endDate, transferCategory)
        }

    fun observeCumulativeExpenseUpTo(endDate: String): Flow<Int> =
        selectedClubRepository.scopedFlow(databaseGateway) { clubId ->
            clubTransactionDao.observeCumulativeExpenseUpTo(clubId, endDate, transferCategory)
        }

    fun observeTransfersByYear(year: Int): Flow<List<ClubTransactionEntity>> =
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
            clubTransactionDao.observeTransfersByYear(clubId, year, transferCategory)
        }

    fun observeTransactionsByYearMonth(yearMonth: YearMonth): Flow<List<ClubTransactionEntity>> {
        val startDate = yearMonth.atDay(1).toString()
        val endDate = yearMonth.atEndOfMonth().toString()
        return cloudLedgerReadPolicy.observeList(
            cloud = { cloudLedgerGateway.observeTransactionsByDateRange(startDate, endDate) },
            room = {
                selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
                    clubTransactionDao.observeByDateRange(clubId, startDate, endDate)
                }
            }
        )
    }

    suspend fun getTransactionsByYearMonthOnce(yearMonth: YearMonth): List<ClubTransactionEntity> {
        val startDate = yearMonth.atDay(1).toString()
        val endDate = yearMonth.atEndOfMonth().toString()
        if (isCloudMode()) {
            return cloudLedgerGateway.getTransactionsByDateRangeOnce(startDate, endDate)
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        return clubTransactionDao.getByDateRangeOnce(clubId, startDate, endDate)
    }

    suspend fun getTransactionsByYearOnce(year: Int): List<ClubTransactionEntity> {
        val startDate = "%04d-01-01".format(year)
        val endDate = "%04d-12-31".format(year)
        if (isCloudMode()) {
            return cloudLedgerGateway.getTransactionsByDateRangeOnce(startDate, endDate)
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        return clubTransactionDao.getByDateRangeOnce(clubId, startDate, endDate)
    }

    fun observeFilteredTransactions(
        startDate: String?,
        endDate: String?,
        targetMemberId: Long?,
        category: String?,
        minAmount: Long?,
        maxAmount: Long?,
        hasReceipt: Boolean?,
        noteQuery: String?
    ): Flow<List<ClubTransactionEntity>> {
        val receiptFlag = when (hasReceipt) {
            true -> 1
            false -> 0
            null -> null
        }
        return cloudLedgerReadPolicy.observeList(
            cloud = {
                val base = when {
                    !startDate.isNullOrBlank() && !endDate.isNullOrBlank() ->
                        cloudLedgerGateway.observeTransactionsByDateRange(startDate, endDate)
                    else -> cloudLedgerGateway.observeTransactionsAsEntities()
                }
                base.map { list ->
                    list.filter { tx ->
                        matchesTransactionFilter(
                            tx = tx,
                            startDate = startDate,
                            endDate = endDate,
                            targetMemberId = targetMemberId,
                            category = category,
                            minAmount = minAmount,
                            maxAmount = maxAmount,
                            hasReceipt = hasReceipt,
                            noteQuery = noteQuery
                        )
                    }
                }
            },
            room = {
                selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
                    clubTransactionDao.observeFiltered(
                        clubId = clubId,
                        startDate = startDate,
                        endDate = endDate,
                        targetMemberId = targetMemberId,
                        category = category,
                        minAmount = minAmount,
                        maxAmount = maxAmount,
                        hasReceipt = receiptFlag,
                        noteQuery = noteQuery?.takeIf { it.isNotBlank() }
                    )
                }
            }
        )
    }

    suspend fun getFilteredTransactionsOnce(
        startDate: String?,
        endDate: String?,
        targetMemberId: Long?,
        category: String?,
        minAmount: Long?,
        maxAmount: Long?,
        hasReceipt: Boolean?,
        noteQuery: String?
    ): List<ClubTransactionEntity> {
        if (isCloudMode()) {
            val base = when {
                !startDate.isNullOrBlank() && !endDate.isNullOrBlank() ->
                    cloudLedgerGateway.getTransactionsByDateRangeOnce(startDate, endDate)
                else -> cloudLedgerGateway.getAllTransactionsOnce()
            }
            return base.filter { tx ->
                matchesTransactionFilter(
                    tx = tx,
                    startDate = startDate,
                    endDate = endDate,
                    targetMemberId = targetMemberId,
                    category = category,
                    minAmount = minAmount,
                    maxAmount = maxAmount,
                    hasReceipt = hasReceipt,
                    noteQuery = noteQuery
                )
            }
        }
        val receiptFlag = when (hasReceipt) {
            true -> 1
            false -> 0
            null -> null
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        return clubTransactionDao.getFilteredOnce(
            clubId = clubId,
            startDate = startDate,
            endDate = endDate,
            targetMemberId = targetMemberId,
            category = category,
            minAmount = minAmount,
            maxAmount = maxAmount,
            hasReceipt = receiptFlag,
            noteQuery = noteQuery?.takeIf { it.isNotBlank() }
        )
    }

    fun filterLoadedTransactions(
        all: List<ClubTransactionEntity>,
        startDate: String?,
        endDate: String?,
        targetMemberId: Long?,
        category: String?,
        minAmount: Long?,
        maxAmount: Long?,
        hasReceipt: Boolean?,
        noteQuery: String?
    ): List<ClubTransactionEntity> =
        all.filter { tx ->
            matchesTransactionFilter(
                tx = tx,
                startDate = startDate,
                endDate = endDate,
                targetMemberId = targetMemberId,
                category = category,
                minAmount = minAmount,
                maxAmount = maxAmount,
                hasReceipt = hasReceipt,
                noteQuery = noteQuery
            )
        }.sortedWith(
            compareByDescending<ClubTransactionEntity> { dateSortKey(it.date) }
                .thenByDescending { it.id }
        )

    private fun matchesTransactionFilter(
        tx: ClubTransactionEntity,
        startDate: String?,
        endDate: String?,
        targetMemberId: Long?,
        category: String?,
        minAmount: Long?,
        maxAmount: Long?,
        hasReceipt: Boolean?,
        noteQuery: String?
    ): Boolean {
        val txKey = dateSortKey(tx.date)
        val startKey = dateSortKey(startDate)
        val endKey = dateSortKey(endDate)
        if (startKey.isNotEmpty() && txKey < startKey) return false
        if (endKey.isNotEmpty() && txKey > endKey) return false
        if (targetMemberId != null && tx.targetMemberId != targetMemberId) return false
        if (!category.isNullOrBlank() && tx.category != category) return false
        val amount = when (tx.type) {
            ClubTransactionType.INCOME -> tx.incomeAmount.toLong()
            ClubTransactionType.EXPENSE -> tx.expenseAmount.toLong()
        }
        if (minAmount != null && amount < minAmount) return false
        if (maxAmount != null && amount > maxAmount) return false
        if (hasReceipt == true && tx.receiptPath.isNullOrBlank()) return false
        if (hasReceipt == false && !tx.receiptPath.isNullOrBlank()) return false
        val q = noteQuery?.trim()?.takeIf { it.isNotBlank() }
        if (q != null && tx.note.orEmpty().indexOf(q, ignoreCase = true) < 0) return false
        return true
    }

    private fun dateSortKey(value: String?): String = LedgerDate.sortKey(value)

    fun observeLatestBalanceUpTo(endDate: String): Flow<Int?> =
        selectedClubRepository.scopedFlowOrNull(databaseGateway) { clubId ->
            clubTransactionDao.observeLatestBalanceUpTo(clubId, endDate)
        }

    suspend fun getTransaction(id: Long): ClubTransactionEntity? {
        if (isCloudMode()) {
            return cloudLedgerGateway.getTransaction(id)
        }
        val clubId = runCatching { selectedClubRepository.requireSelectedClubId() }.getOrNull()
        val local = clubId?.let { clubTransactionDao.getById(id, it) }
        if (local != null) return local
        return runCatching { cloudLedgerGateway.getTransaction(id) }.getOrNull()
    }

    suspend fun insertTransaction(transaction: ClubTransactionEntity): Long {
        if (isCloudMode()) {
            val id = cloudLedgerGateway.insertTransaction(transaction)
            eventExpenseRepository.syncFromLedgerTransaction(transaction, id)
            ledgerRefreshNotifier.notifyTransactionChanged()
            return id
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        val id = clubTransactionDao.insert(
            transaction.copy(clubId = clubId, updatedAt = EntityTimestamps.now())
        )
        eventExpenseRepository.syncFromLedgerTransaction(
            transaction.copy(id = id, clubId = clubId),
            id
        )
        ledgerRefreshNotifier.notifyTransactionChanged()
        return id
    }

    suspend fun updateTransaction(transaction: ClubTransactionEntity) {
        if (isCloudMode()) {
            cloudLedgerGateway.updateTransaction(transaction)
            eventExpenseRepository.syncFromLedgerTransaction(transaction, transaction.id)
            ledgerRefreshNotifier.notifyTransactionChanged()
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        clubTransactionDao.update(
            transaction.copy(clubId = clubId, updatedAt = EntityTimestamps.now())
        )
        if (ClubCategory.isCondolenceCategory(transaction.category)) {
            eventExpenseRepository.syncFromLedgerTransaction(
                transaction.copy(clubId = clubId),
                transaction.id
            )
        } else {
            eventExpenseRepository.deleteLinkedToTransaction(transaction.id)
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
    }

    suspend fun deleteTransaction(transaction: ClubTransactionEntity) {
        eventExpenseRepository.deleteLinkedToTransaction(transaction.id)
        val duesLinked = transaction.linkedDuesDetailId != null ||
            transaction.category == ClubCategory.REGULAR_DUES
        if (duesLinked) {
            duesRepository.deleteLedgerTransactionWithDuesRevert(transaction)
        } else if (isCloudMode()) {
            cloudLedgerGateway.deleteTransaction(transaction)
        } else {
            val clubId = selectedClubRepository.requireSelectedClubId()
            clubTransactionDao.delete(transaction.copy(clubId = clubId))
        }
        ledgerRefreshNotifier.notifyTransactionChanged()
    }

    suspend fun getAllForExport() =
        clubTransactionDao.getAllForExport(selectedClubRepository.requireSelectedClubId())

    suspend fun getForExportByYear(year: Int): List<ClubTransactionExportRow> {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val startDate = "%04d-01-01".format(year)
        val endDate = "%04d-12-31".format(year)
        return clubTransactionDao.getForExportByDateRange(clubId, startDate, endDate)
    }

    suspend fun getSettlementSnapshot(year: Int): SettlementExportSnapshot {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val endDate = "$year-12-31"
        val income = clubTransactionDao.getTotalIncomeByYearOnce(clubId, year, transferCategory)
        val expense = clubTransactionDao.getTotalExpenseByYearOnce(clubId, year, transferCategory)
        val categories = clubTransactionDao.getCategorySummaryByYearOnce(
            clubId,
            year,
            transferCategory
        )
        val cumulativeIncome = clubTransactionDao.getCumulativeIncomeUpToOnce(
            clubId,
            endDate,
            transferCategory
        )
        val cumulativeExpense = clubTransactionDao.getCumulativeExpenseUpToOnce(
            clubId,
            endDate,
            transferCategory
        )
        val ledgerBalance = clubTransactionDao.getLatestBalanceUpToOnce(clubId, endDate)
        val recomputed = cumulativeIncome.toLong() - cumulativeExpense.toLong()
        val resolved = LedgerBalanceCalculator.resolveDisplayBalance(
            recomputed = recomputed,
            suspiciousStored = ledgerBalance?.toLong()
        )
        return SettlementExportSnapshot(
            year = year,
            ledgerIncome = income,
            ledgerExpense = expense,
            categorySummaries = categories,
            currentBalance = with(LedgerBalanceCalculator) { resolved.toSafeBalanceInt() }
        )
    }
}

data class SettlementExportSnapshot(
    val year: Int,
    val ledgerIncome: Int,
    val ledgerExpense: Int,
    val categorySummaries: List<com.smartexpense.data.local.model.club.ClubTransactionCategorySummaryRow>,
    val currentBalance: Int
)
