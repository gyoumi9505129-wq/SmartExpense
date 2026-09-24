package com.smartexpense.data.local.dao.club



import androidx.room.Dao

import androidx.room.Delete

import androidx.room.Insert

import androidx.room.Query

import androidx.room.Update

import com.smartexpense.data.local.entity.club.ClubHistoryEntity

import kotlinx.coroutines.flow.Flow



@Dao

interface ClubHistoryDao {

    @Query("SELECT COUNT(*) FROM club_history WHERE club_id = :clubId")

    suspend fun count(clubId: Long): Int



    @Query("DELETE FROM club_history WHERE club_id = :clubId")

    suspend fun deleteAll(clubId: Long)



    @Query("SELECT * FROM club_history WHERE club_id = :clubId ORDER BY date ASC, id ASC")
    suspend fun getAllOnce(clubId: Long): List<ClubHistoryEntity>

    @Query("SELECT * FROM club_history WHERE club_id = :clubId ORDER BY date ASC, id ASC")
    fun observeAllAsc(clubId: Long): Flow<List<ClubHistoryEntity>>



    @Query("SELECT * FROM club_history WHERE club_id = :clubId ORDER BY date DESC, id DESC")

    fun observeAllDesc(clubId: Long): Flow<List<ClubHistoryEntity>>



    @Query("SELECT * FROM club_history WHERE id = :id AND club_id = :clubId")

    suspend fun getById(id: Int, clubId: Long): ClubHistoryEntity?



    @Insert

    suspend fun insert(history: ClubHistoryEntity): Long



    @Insert

    suspend fun insertAll(histories: List<ClubHistoryEntity>)



    @Update

    suspend fun update(history: ClubHistoryEntity)



    @Delete
    suspend fun delete(history: ClubHistoryEntity)

    @Query(
        """
        SELECT * FROM club_history
        WHERE club_id = :clubId AND updated_at > :since
        ORDER BY date ASC, id ASC
        """
    )
    suspend fun getUpdatedSince(clubId: Long, since: Long): List<ClubHistoryEntity>

    @Query(
        """
        UPDATE club_history SET cloud_doc_id = :cloudDocId
        WHERE id = :id AND club_id = :clubId
        """
    )
    suspend fun updateCloudDocId(id: Int, clubId: Long, cloudDocId: String)
}

