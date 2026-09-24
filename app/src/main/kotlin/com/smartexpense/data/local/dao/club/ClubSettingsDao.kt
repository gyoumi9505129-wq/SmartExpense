package com.smartexpense.data.local.dao.club

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartexpense.data.local.entity.club.ClubSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubSettingsDao {
    @Query("SELECT * FROM club_settings WHERE club_id = :clubId")
    fun observeByClubId(clubId: Long): Flow<List<ClubSettingEntity>>

    @Query("SELECT * FROM club_settings WHERE club_id = :clubId AND setting_key = :key LIMIT 1")
    suspend fun getEntry(clubId: Long, key: String): ClubSettingEntity?

    @Query("SELECT * FROM club_settings WHERE club_id = :clubId")
    suspend fun getAllOnce(clubId: Long): List<ClubSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ClubSettingEntity)

    @Query("DELETE FROM club_settings WHERE club_id = :clubId AND setting_key = :key")
    suspend fun deleteKey(clubId: Long, key: String)

    @Query("SELECT COUNT(*) FROM club_settings WHERE club_id = :clubId")
    suspend fun countByClubId(clubId: Long): Int

    @Query("DELETE FROM club_settings WHERE club_id = :clubId")
    suspend fun deleteAllByClubId(clubId: Long)
}
