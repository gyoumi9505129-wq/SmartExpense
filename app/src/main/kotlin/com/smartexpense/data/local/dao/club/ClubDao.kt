package com.smartexpense.data.local.dao.club

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartexpense.data.local.entity.club.ClubEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(club: ClubEntity): Long

    @Query("SELECT * FROM clubs ORDER BY created_at ASC, club_id ASC")
    fun getAllClubs(): Flow<List<ClubEntity>>

    @Query("SELECT * FROM clubs ORDER BY created_at ASC, club_id ASC")
    fun observeAll(): Flow<List<ClubEntity>>

    @Query("SELECT * FROM clubs ORDER BY created_at ASC, club_id ASC")
    suspend fun getAllOnce(): List<ClubEntity>

    @Query("SELECT * FROM clubs WHERE club_id = :clubId LIMIT 1")
    suspend fun getById(clubId: Long): ClubEntity?

    @Query("SELECT * FROM clubs WHERE club_id = :clubId LIMIT 1")
    fun observeById(clubId: Long): Flow<ClubEntity?>

    @Query("UPDATE clubs SET club_slogan = :slogan WHERE club_id = :clubId")
    suspend fun updateSlogan(clubId: Long, slogan: String)

    @Query("SELECT * FROM clubs WHERE firestore_meeting_id = :meetingId LIMIT 1")
    suspend fun getByFirestoreMeetingId(meetingId: String): ClubEntity?

    @Query("UPDATE clubs SET club_name = :name, club_slogan = :slogan, firestore_meeting_id = :meetingId, membership_status = :status, updated_at = :updatedAt WHERE club_id = :clubId")
    suspend fun updateMeetingLink(
        clubId: Long,
        name: String,
        slogan: String,
        meetingId: String,
        status: String,
        updatedAt: Long
    )

    @Query("SELECT COUNT(*) FROM clubs")
    suspend fun count(): Int

    @Query("DELETE FROM clubs WHERE club_id = :clubId")
    suspend fun deleteById(clubId: Long)
}
