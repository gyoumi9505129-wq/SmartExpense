package com.smartexpense.data.local.dao.club



import androidx.room.Dao

import androidx.room.Delete

import androidx.room.Insert

import androidx.room.OnConflictStrategy

import androidx.room.Query

import androidx.room.Update

import com.smartexpense.data.local.entity.club.MemberEntity

import com.smartexpense.data.local.entity.club.MemberStatus

import com.smartexpense.data.local.entity.club.MemberStatusHistoryEntity

import kotlinx.coroutines.flow.Flow



@Dao

interface MemberDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insert(member: MemberEntity): Long



    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertAll(members: List<MemberEntity>)



    @Update

    suspend fun update(member: MemberEntity)



    @Delete

    suspend fun delete(member: MemberEntity)



    @Query("SELECT * FROM members WHERE id = :id AND club_id = :clubId")

    suspend fun getById(id: Long, clubId: Long): MemberEntity?



    @Query("DELETE FROM member_status_history WHERE club_id = :clubId")

    suspend fun deleteAllStatusHistory(clubId: Long)



    @Query("DELETE FROM members WHERE club_id = :clubId")

    suspend fun deleteAllMembers(clubId: Long)



    @Query("SELECT COUNT(*) FROM members WHERE club_id = :clubId")

    suspend fun getCount(clubId: Long): Int



    @Query("SELECT * FROM members WHERE club_id = :clubId ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'DORMANT' THEN 1 WHEN 'WITHDRAWN' THEN 2 ELSE 3 END, name ASC")

    suspend fun getAllOnce(clubId: Long): List<MemberEntity>



    @Query(

        """

        SELECT * FROM members

        WHERE club_id = :clubId AND status = 'ACTIVE'

        ORDER BY name ASC

        """

    )

    suspend fun getActiveMembersOnce(clubId: Long): List<MemberEntity>



    @Query("SELECT * FROM members WHERE club_id = :clubId ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'DORMANT' THEN 1 WHEN 'WITHDRAWN' THEN 2 ELSE 3 END, name ASC")

    fun observeAll(clubId: Long): Flow<List<MemberEntity>>



    @Query(

        """

        SELECT * FROM members

        WHERE club_id = :clubId AND status != 'WITHDRAWN'

        ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'DORMANT' THEN 1 WHEN 'WITHDRAWN' THEN 2 ELSE 3 END, name ASC

        """

    )

    fun observeActiveMembers(clubId: Long): Flow<List<MemberEntity>>



    @Query("SELECT COUNT(*) FROM members WHERE club_id = :clubId AND status = :status")

    fun observeCountByStatus(clubId: Long, status: MemberStatus): Flow<Int>



    @Query(

        """

        SELECT * FROM member_status_history

        WHERE member_id = :memberId AND club_id = :clubId

        ORDER BY change_date DESC

        """

    )

    fun observeMemberHistory(memberId: Long, clubId: Long): Flow<List<MemberStatusHistoryEntity>>



    @Query(

        """

        SELECT * FROM member_status_history

        WHERE member_id = :memberId AND club_id = :clubId

        ORDER BY change_date DESC

        """

    )

    suspend fun getMemberHistory(memberId: Long, clubId: Long): List<MemberStatusHistoryEntity>

    @Query(
        """
        SELECT * FROM members
        WHERE club_id = :clubId AND updated_at > :since
        ORDER BY id ASC
        """
    )
    suspend fun getUpdatedSince(clubId: Long, since: Long): List<MemberEntity>

    @Query(
        """
        UPDATE members SET cloud_doc_id = :cloudDocId
        WHERE id = :id AND club_id = :clubId
        """
    )
    suspend fun updateCloudDocId(id: Long, clubId: Long, cloudDocId: String)
}

