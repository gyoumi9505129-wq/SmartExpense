package com.smartexpense.data.local.dao.club



import androidx.room.Dao

import androidx.room.Delete

import androidx.room.Insert

import androidx.room.Query

import androidx.room.Update

import com.smartexpense.data.local.entity.club.ClubAccountEntity

import kotlinx.coroutines.flow.Flow



@Dao

interface ClubAccountDao {

    @Query("SELECT * FROM club_accounts WHERE club_id = :clubId ORDER BY id ASC")

    fun observeAll(clubId: Long): Flow<List<ClubAccountEntity>>



    @Query("SELECT * FROM club_accounts WHERE club_id = :clubId ORDER BY id ASC")

    suspend fun getAllOnce(clubId: Long): List<ClubAccountEntity>



    @Query("SELECT COUNT(*) FROM club_accounts WHERE club_id = :clubId")

    suspend fun count(clubId: Long): Int



    @Insert

    suspend fun insert(account: ClubAccountEntity): Long



    @Insert

    suspend fun insertAll(accounts: List<ClubAccountEntity>)



    @Query("DELETE FROM club_accounts WHERE club_id = :clubId")

    suspend fun deleteAll(clubId: Long)



    @Update

    suspend fun update(account: ClubAccountEntity)



    @Delete
    suspend fun delete(account: ClubAccountEntity)

    @Query(
        """
        SELECT * FROM club_accounts
        WHERE club_id = :clubId AND updated_at > :since
        ORDER BY id ASC
        """
    )
    suspend fun getUpdatedSince(clubId: Long, since: Long): List<ClubAccountEntity>

    @Query(
        """
        UPDATE club_accounts SET cloud_doc_id = :cloudDocId
        WHERE id = :id AND club_id = :clubId
        """
    )
    suspend fun updateCloudDocId(id: Int, clubId: Long, cloudDocId: String)
}

