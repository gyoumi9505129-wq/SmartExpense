package com.smartexpense.data.local.dao.club

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.smartexpense.data.local.entity.club.EventExpenseEntity
import com.smartexpense.data.local.model.club.EventExpenseSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface EventExpenseDao {

    @Query("DELETE FROM event_expenses WHERE club_id = :clubId")
    suspend fun deleteAll(clubId: Long)

    @Insert
    suspend fun insert(entity: EventExpenseEntity): Long

    @Insert
    suspend fun insertAll(entities: List<EventExpenseEntity>)

    @Update
    suspend fun update(entity: EventExpenseEntity)

    @Query(
        """
        SELECT * FROM event_expenses
        WHERE club_id = :clubId
        ORDER BY date DESC, id DESC
        """
    )
    fun observeAll(clubId: Long): Flow<List<EventExpenseEntity>>

    @Query(
        """
        SELECT * FROM event_expenses
        WHERE club_id = :clubId
        ORDER BY date DESC, id DESC
        """
    )
    suspend fun getAllOnce(clubId: Long): List<EventExpenseEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM event_expenses
        WHERE club_id = :clubId
        """
    )
    fun observeTotalAmount(clubId: Long): Flow<Int>

    @Query(
        """
        SELECT
            e.member_id AS memberId,
            m.name AS memberName,
            m.status AS memberStatus,
            COALESCE(SUM(e.amount), 0) AS totalAmount
        FROM event_expenses e
        INNER JOIN members m ON e.member_id = m.id AND m.club_id = :clubId
        WHERE e.club_id = :clubId
        GROUP BY e.member_id, m.name, m.status
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
    fun observeSummary(clubId: Long): Flow<List<EventExpenseSummaryRow>>

    @Query(
        """
        SELECT * FROM event_expenses
        WHERE club_id = :clubId AND linked_transaction_id = :transactionId
        LIMIT 1
        """
    )
    suspend fun getByLinkedTransactionId(clubId: Long, transactionId: Long): EventExpenseEntity?

    @Query(
        """
        SELECT * FROM event_expenses
        WHERE club_id = :clubId AND updated_at > :since
        ORDER BY date DESC, id DESC
        """
    )
    suspend fun getUpdatedSince(clubId: Long, since: Long): List<EventExpenseEntity>

    @Query(
        """
        UPDATE event_expenses SET cloud_doc_id = :cloudDocId
        WHERE id = :id AND club_id = :clubId
        """
    )
    suspend fun updateCloudDocId(id: Long, clubId: Long, cloudDocId: String)

    @Query(
        """
        DELETE FROM event_expenses
        WHERE club_id = :clubId AND linked_transaction_id = :transactionId
        """
    )
    suspend fun deleteByLinkedTransactionId(clubId: Long, transactionId: Long)
}
