package com.smartexpense.data.local.dao.club

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartexpense.data.local.entity.club.CustomBankEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomBankDao {
    @Query("SELECT * FROM custom_banks WHERE club_id = :clubId ORDER BY app_name ASC")
    fun observeAll(clubId: Long): Flow<List<CustomBankEntity>>

    @Query("SELECT * FROM custom_banks WHERE club_id = :clubId AND package_name = :packageName LIMIT 1")
    suspend fun findByPackage(clubId: Long, packageName: String): CustomBankEntity?

    @Query("SELECT * FROM custom_banks WHERE club_id = :clubId AND id = :id LIMIT 1")
    suspend fun getById(clubId: Long, id: Int): CustomBankEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bank: CustomBankEntity): Long

    @Delete
    suspend fun delete(bank: CustomBankEntity)

    @Query("DELETE FROM custom_banks WHERE club_id = :clubId")
    suspend fun deleteAllByClubId(clubId: Long)
}
