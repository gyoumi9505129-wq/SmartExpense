package com.smartexpense.data.local.dao.club



import androidx.room.Dao

import androidx.room.Delete

import androidx.room.Insert

import androidx.room.OnConflictStrategy

import androidx.room.Query

import androidx.room.Update

import com.smartexpense.data.local.entity.club.ClubTransactionEntity

import com.smartexpense.data.local.entity.club.ClubTransactionType

import com.smartexpense.data.local.model.club.ClubTransactionCategorySummaryRow

import com.smartexpense.data.local.model.club.ClubTransactionExportRow

import com.smartexpense.data.local.model.club.EventExpenseSummaryRow

import com.smartexpense.data.local.model.club.YearlyClubTransactionSummaryRow

import kotlinx.coroutines.flow.Flow



@Dao

interface ClubTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insert(transaction: ClubTransactionEntity): Long



    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertAll(transactions: List<ClubTransactionEntity>)



    @Query("SELECT * FROM club_transactions WHERE club_id = :clubId ORDER BY date ASC, id ASC")

    suspend fun getAllOnce(clubId: Long): List<ClubTransactionEntity>



    @Query("SELECT COUNT(*) FROM club_transactions WHERE club_id = :clubId")

    suspend fun getCount(clubId: Long): Int



    @Query("DELETE FROM club_transactions WHERE club_id = :clubId")

    suspend fun deleteAll(clubId: Long)



    @Update

    suspend fun update(transaction: ClubTransactionEntity)



    @Delete

    suspend fun delete(transaction: ClubTransactionEntity)



    @Query("SELECT * FROM club_transactions WHERE id = :id AND club_id = :clubId")

    suspend fun getById(id: Long, clubId: Long): ClubTransactionEntity?

    @Query(
        """
        SELECT * FROM club_transactions
        WHERE club_id = :clubId AND updated_at > :since
        ORDER BY date ASC, id ASC
        """
    )
    suspend fun getUpdatedSince(clubId: Long, since: Long): List<ClubTransactionEntity>

    @Query(
        """
        UPDATE club_transactions SET cloud_doc_id = :cloudDocId
        WHERE id = :id AND club_id = :clubId
        """
    )
    suspend fun updateCloudDocId(id: Long, clubId: Long, cloudDocId: String)



    @Query("SELECT * FROM club_transactions WHERE club_id = :clubId ORDER BY date DESC, id DESC")

    fun getAllTransactions(clubId: Long): Flow<List<ClubTransactionEntity>>



    @Query("SELECT * FROM club_transactions WHERE club_id = :clubId ORDER BY date DESC, id DESC")

    fun observeAll(clubId: Long): Flow<List<ClubTransactionEntity>>



    @Query(

        """

        SELECT

            t.date AS date,

            t.category AS category,

            t.income_amount AS income_amount,

            t.expense_amount AS expense_amount,

            t.note AS note,

            m.name AS member_name

        FROM club_transactions t

        LEFT JOIN members m ON t.target_member_id = m.id AND m.club_id = :clubId

        WHERE t.club_id = :clubId

        ORDER BY t.date ASC, t.id ASC

        """

    )

    suspend fun getAllForExport(clubId: Long): List<ClubTransactionExportRow>

    @Query(
        """
        SELECT
            t.date AS date,
            t.category AS category,
            t.income_amount AS income_amount,
            t.expense_amount AS expense_amount,
            t.note AS note,
            m.name AS member_name
        FROM club_transactions t
        LEFT JOIN members m ON t.target_member_id = m.id AND m.club_id = :clubId
        WHERE t.club_id = :clubId
          AND t.date >= :startDate
          AND t.date <= :endDate
        ORDER BY t.date ASC, t.id ASC
        """
    )
    suspend fun getForExportByDateRange(
        clubId: Long,
        startDate: String,
        endDate: String
    ): List<ClubTransactionExportRow>



    @Query(

        """

        SELECT COALESCE(SUM(income_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

          AND category != :transferCategory

        """

    )

    suspend fun getTotalIncomeByYearOnce(clubId: Long, year: Int, transferCategory: String): Int



    @Query(

        """

        SELECT COALESCE(SUM(expense_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

          AND category != :transferCategory

        """

    )

    suspend fun getTotalExpenseByYearOnce(clubId: Long, year: Int, transferCategory: String): Int



    @Query(

        """

        SELECT

            category AS category,

            COALESCE(SUM(income_amount), 0) AS total_income,

            COALESCE(SUM(expense_amount), 0) AS total_expense

        FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

          AND category != :transferCategory

        GROUP BY category

        ORDER BY category ASC

        """

    )

    suspend fun getCategorySummaryByYearOnce(

        clubId: Long,

        year: Int,

        transferCategory: String

    ): List<ClubTransactionCategorySummaryRow>



    @Query(

        """

        SELECT COALESCE(SUM(income_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND date <= :endDate

          AND category != :transferCategory

        """

    )

    suspend fun getCumulativeIncomeUpToOnce(

        clubId: Long,

        endDate: String,

        transferCategory: String

    ): Int



    @Query(

        """

        SELECT COALESCE(SUM(expense_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND date <= :endDate

          AND category != :transferCategory

        """

    )

    suspend fun getCumulativeExpenseUpToOnce(

        clubId: Long,

        endDate: String,

        transferCategory: String

    ): Int



    @Query(

        """

        SELECT balance_after FROM club_transactions

        WHERE club_id = :clubId

          AND date <= :endDate AND balance_after IS NOT NULL

        ORDER BY date DESC, id DESC

        LIMIT 1

        """

    )

    suspend fun getLatestBalanceUpToOnce(clubId: Long, endDate: String): Int?



    @Query(

        """

        SELECT * FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

        ORDER BY date DESC, id DESC

        """

    )

    fun observeByYear(clubId: Long, year: Int): Flow<List<ClubTransactionEntity>>



    @Query(

        """

        SELECT * FROM club_transactions

        WHERE club_id = :clubId

          AND type = :type

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

        ORDER BY date DESC, id DESC

        """

    )

    fun observeByYearAndType(

        clubId: Long,

        year: Int,

        type: ClubTransactionType

    ): Flow<List<ClubTransactionEntity>>



    @Query(

        """

        SELECT * FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

          AND category = :category

        ORDER BY date DESC, id DESC

        """

    )

    fun observeByYearAndCategory(

        clubId: Long,

        year: Int,

        category: String

    ): Flow<List<ClubTransactionEntity>>



    @Query(

        """

        SELECT

            :year AS year,

            COALESCE(SUM(income_amount), 0) AS total_income,

            COALESCE(SUM(expense_amount), 0) AS total_expense

        FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

          AND category != :transferCategory

        """

    )

    fun observeYearlySummary(

        clubId: Long,

        year: Int,

        transferCategory: String

    ): Flow<YearlyClubTransactionSummaryRow?>



    @Query(

        """

        SELECT

            category AS category,

            COALESCE(SUM(income_amount), 0) AS total_income,

            COALESCE(SUM(expense_amount), 0) AS total_expense

        FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

          AND category != :transferCategory

        GROUP BY category

        ORDER BY category ASC

        """

    )

    fun observeCategorySummaryByYear(

        clubId: Long,

        year: Int,

        transferCategory: String

    ): Flow<List<ClubTransactionCategorySummaryRow>>



    @Query(

        """

        SELECT COALESCE(SUM(income_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

          AND category != :transferCategory

        """

    )

    fun observeTotalIncomeByYear(

        clubId: Long,

        year: Int,

        transferCategory: String

    ): Flow<Int>



    @Query(

        """

        SELECT COALESCE(SUM(expense_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

          AND category != :transferCategory

        """

    )

    fun observeTotalExpenseByYear(

        clubId: Long,

        year: Int,

        transferCategory: String

    ): Flow<Int>



    @Query(

        """

        SELECT COALESCE(SUM(income_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND date <= :endDate

          AND category != :transferCategory

        """

    )

    fun observeCumulativeIncomeUpTo(

        clubId: Long,

        endDate: String,

        transferCategory: String

    ): Flow<Int>



    @Query(

        """

        SELECT COALESCE(SUM(expense_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND date <= :endDate

          AND category != :transferCategory

        """

    )

    fun observeCumulativeExpenseUpTo(

        clubId: Long,

        endDate: String,

        transferCategory: String

    ): Flow<Int>



    @Query(

        """

        SELECT balance_after FROM club_transactions

        WHERE club_id = :clubId

          AND date <= :endDate AND balance_after IS NOT NULL

        ORDER BY date DESC, id DESC

        LIMIT 1

        """

    )

    fun observeLatestBalanceUpTo(clubId: Long, endDate: String): Flow<Int?>



    @Query(

        """

        SELECT * FROM club_transactions

        WHERE club_id = :clubId

          AND category = :category

          AND type = 'EXPENSE'

          AND target_member_id IS NOT NULL

        ORDER BY date DESC, id DESC

        """

    )

    fun observeCondolenceExpenses(clubId: Long, category: String): Flow<List<ClubTransactionEntity>>



    @Query(

        """

        SELECT COALESCE(SUM(expense_amount), 0) FROM club_transactions

        WHERE club_id = :clubId

          AND category = :category

          AND type = 'EXPENSE'

          AND target_member_id IS NOT NULL

        """

    )

    fun observeTotalCondolenceExpense(clubId: Long, category: String): Flow<Int>



    @Query(

        """

        SELECT

            m.id AS memberId,

            m.name AS memberName,

            m.status AS memberStatus,

            COALESCE(SUM(t.expense_amount), 0) AS totalAmount

        FROM club_transactions t

        INNER JOIN members m ON t.target_member_id = m.id

        WHERE t.club_id = :clubId

          AND m.club_id = :clubId

          AND t.category = :category

          AND t.type = 'EXPENSE'

        GROUP BY m.id, m.name, m.status

        ORDER BY CASE m.status
            WHEN 'ACTIVE' THEN 0
            WHEN 'DORMANT' THEN 1
            WHEN 'WITHDRAWN' THEN 2
            ELSE 3
        END ASC,
        totalAmount DESC,
        m.name ASC

        """

    )

    fun observeEventExpenseSummary(clubId: Long, category: String): Flow<List<EventExpenseSummaryRow>>



    @Query(

        """

        SELECT * FROM club_transactions

        WHERE club_id = :clubId

          AND category = :category

          AND type = 'EXPENSE'

          AND target_member_id = :memberId

        ORDER BY date DESC, id DESC

        """

    )

    fun observeCondolenceExpensesByMember(

        clubId: Long,

        category: String,

        memberId: Long

    ): Flow<List<ClubTransactionEntity>>



    @Query(

        """

        DELETE FROM club_transactions

        WHERE club_id = :clubId

          AND category = :category

          AND type = 'EXPENSE'

          AND target_member_id IS NULL

        """

    )

    suspend fun deleteUntargetedCondolenceExpenses(category: String, clubId: Long)



    @Query(

        """

        SELECT * FROM club_transactions

        WHERE club_id = :clubId

          AND category = :transferCategory

          AND substr(date, 1, 4) = CAST(:year AS TEXT)

        ORDER BY date DESC, id DESC

        """

    )

    fun observeTransfersByYear(

        clubId: Long,

        year: Int,

        transferCategory: String

    ): Flow<List<ClubTransactionEntity>>



    @Query(

        """

        SELECT * FROM club_transactions

        WHERE club_id = :clubId

          AND (:startDate IS NULL OR date >= :startDate)

          AND (:endDate IS NULL OR date <= :endDate)

          AND (:targetMemberId IS NULL OR target_member_id = :targetMemberId)

          AND (:category IS NULL OR category = :category)

          AND (:minAmount IS NULL OR MAX(income_amount, expense_amount) >= :minAmount)

          AND (:maxAmount IS NULL OR MAX(income_amount, expense_amount) <= :maxAmount)

          AND (

            :hasReceipt IS NULL OR

            (:hasReceipt = 1 AND receipt_path IS NOT NULL AND receipt_path != '') OR

            (:hasReceipt = 0 AND (receipt_path IS NULL OR receipt_path = ''))

          )

          AND (:noteQuery IS NULL OR :noteQuery = '' OR note LIKE '%' || :noteQuery || '%')

        ORDER BY date DESC, id DESC

        """

    )

    fun observeFiltered(

        clubId: Long,

        startDate: String?,

        endDate: String?,

        targetMemberId: Long?,

        category: String?,

        minAmount: Long?,

        maxAmount: Long?,

        hasReceipt: Int?,

        noteQuery: String?

    ): Flow<List<ClubTransactionEntity>>

    @Query(
        """
        SELECT * FROM club_transactions
        WHERE club_id = :clubId
          AND (:startDate IS NULL OR date >= :startDate)
          AND (:endDate IS NULL OR date <= :endDate)
          AND (:targetMemberId IS NULL OR target_member_id = :targetMemberId)
          AND (:category IS NULL OR category = :category)
          AND (:minAmount IS NULL OR MAX(income_amount, expense_amount) >= :minAmount)
          AND (:maxAmount IS NULL OR MAX(income_amount, expense_amount) <= :maxAmount)
          AND (
            :hasReceipt IS NULL OR
            (:hasReceipt = 1 AND receipt_path IS NOT NULL AND receipt_path != '') OR
            (:hasReceipt = 0 AND (receipt_path IS NULL OR receipt_path = ''))
          )
          AND (:noteQuery IS NULL OR :noteQuery = '' OR note LIKE '%' || :noteQuery || '%')
        ORDER BY date DESC, id DESC
        """
    )
    suspend fun getFilteredOnce(
        clubId: Long,
        startDate: String?,
        endDate: String?,
        targetMemberId: Long?,
        category: String?,
        minAmount: Long?,
        maxAmount: Long?,
        hasReceipt: Int?,
        noteQuery: String?
    ): List<ClubTransactionEntity>

    @Query(
        """
        SELECT * FROM club_transactions
        WHERE club_id = :clubId
          AND date >= :startDate
          AND date <= :endDate
        ORDER BY date DESC, id DESC
        """
    )
    fun observeByDateRange(
        clubId: Long,
        startDate: String,
        endDate: String
    ): Flow<List<ClubTransactionEntity>>

    @Query(
        """
        SELECT * FROM club_transactions
        WHERE club_id = :clubId
          AND date >= :startDate
          AND date <= :endDate
        ORDER BY date DESC, id DESC
        """
    )
    suspend fun getByDateRangeOnce(
        clubId: Long,
        startDate: String,
        endDate: String
    ): List<ClubTransactionEntity>

    @Query(
        """
        SELECT balance_after FROM club_transactions
        WHERE club_id = :clubId AND balance_after IS NOT NULL
        ORDER BY date DESC, id DESC
        LIMIT 1
        """
    )
    fun observeLatestBalance(clubId: Long): Flow<Int?>

    @Query(
        """
        SELECT COALESCE(SUM(CAST(income_amount AS INTEGER)), 0)
             - COALESCE(SUM(CAST(expense_amount AS INTEGER)), 0)
        FROM club_transactions
        WHERE club_id = :clubId
        """
    )
    fun observeNetBalance(clubId: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(CAST(income_amount AS INTEGER)), 0)
        FROM club_transactions
        WHERE club_id = :clubId
        """
    )
    suspend fun getSumIncomeOnce(clubId: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(CAST(expense_amount AS INTEGER)), 0)
        FROM club_transactions
        WHERE club_id = :clubId
        """
    )
    suspend fun getSumExpenseOnce(clubId: Long): Long

    @Query(
        """
        SELECT * FROM club_transactions
        WHERE club_id = :clubId
          AND type = 'INCOME'
          AND category = :category
          AND target_member_id = :memberId
          AND note = :note
        LIMIT 1
        """
    )
    suspend fun findAutoDuesLedgerEntry(
        clubId: Long,
        memberId: Long,
        category: String,
        note: String
    ): ClubTransactionEntity?

    @Query(
        """
        SELECT * FROM club_transactions
        WHERE club_id = :clubId
          AND linked_dues_detail_id = :detailId
        LIMIT 1
        """
    )
    suspend fun findByLinkedDuesDetail(
        clubId: Long,
        detailId: Long
    ): ClubTransactionEntity?

    @Query(
        """
        SELECT * FROM club_transactions
        WHERE club_id = :clubId
          AND linked_dues_detail_id = :detailId
        ORDER BY date ASC, id ASC
        """
    )
    suspend fun findAllByLinkedDuesDetail(
        clubId: Long,
        detailId: Long
    ): List<ClubTransactionEntity>

}

