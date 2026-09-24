package com.smartexpense.data.local.dao.club

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DuesPaymentHistoryDao {
    @Insert
    suspend fun insert(history: DuesPaymentHistoryEntity): Long

    @Insert
    suspend fun insertAll(histories: List<DuesPaymentHistoryEntity>): List<Long>

    @Query(
        """
        SELECT * FROM dues_payment_history
        WHERE dues_detail_id = :detailId AND club_id = :clubId
        ORDER BY pay_date ASC, id ASC
        """
    )
    suspend fun getByDetailId(detailId: Long, clubId: Long): List<DuesPaymentHistoryEntity>

    @Query(
        """
        SELECT * FROM dues_payment_history
        WHERE club_id = :clubId
        ORDER BY pay_date ASC, id ASC
        """
    )
    fun observeByClubId(clubId: Long): Flow<List<DuesPaymentHistoryEntity>>

    @Query(
        """
        SELECT * FROM dues_payment_history
        WHERE club_id = :clubId AND dues_detail_id IN (:detailIds)
        ORDER BY pay_date ASC, id ASC
        """
    )
    suspend fun getByDetailIds(detailIds: List<Long>, clubId: Long): List<DuesPaymentHistoryEntity>

    @Query(
        """
        SELECT * FROM dues_payment_history
        WHERE club_id = :clubId AND linked_transaction_id = :transactionId
        LIMIT 1
        """
    )
    suspend fun getByLinkedTransactionId(
        transactionId: Long,
        clubId: Long
    ): DuesPaymentHistoryEntity?

    @Query(
        """
        SELECT * FROM dues_payment_history
        WHERE club_id = :clubId AND linked_transaction_id = :transactionId
        """
    )
    suspend fun getAllByLinkedTransactionId(
        transactionId: Long,
        clubId: Long
    ): List<DuesPaymentHistoryEntity>

    @Query(
        """
        DELETE FROM dues_payment_history
        WHERE dues_detail_id = :detailId AND club_id = :clubId
        """
    )
    suspend fun deleteByDetailId(detailId: Long, clubId: Long)

    @Query(
        """
        DELETE FROM dues_payment_history
        WHERE id = :id AND club_id = :clubId
        """
    )
    suspend fun deleteById(id: Long, clubId: Long)

    @Query(
        """
        SELECT DISTINCT d.yearly_dues_id FROM dues_payment_history h
        INNER JOIN dues_detail d ON d.id = h.dues_detail_id
        WHERE h.club_id = :clubId AND h.updated_at > :since
        """
    )
    suspend fun getYearlyIdsUpdatedSince(clubId: Long, since: Long): List<Long>

    @Query("DELETE FROM dues_payment_history WHERE club_id = :clubId")
    suspend fun deleteAll(clubId: Long)
}
