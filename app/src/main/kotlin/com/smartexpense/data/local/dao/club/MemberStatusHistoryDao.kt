package com.smartexpense.data.local.dao.club



import androidx.room.Dao

import androidx.room.Insert

import androidx.room.Query

import com.smartexpense.data.local.entity.club.MemberStatusHistoryEntity

import kotlinx.coroutines.flow.Flow



@Dao

interface MemberStatusHistoryDao {

    @Insert

    suspend fun insert(history: MemberStatusHistoryEntity): Long



    @Query(

        """

        SELECT * FROM member_status_history

        WHERE member_id = :memberId AND club_id = :clubId

        ORDER BY change_date DESC

        """

    )

    fun observeByMemberId(memberId: Long, clubId: Long): Flow<List<MemberStatusHistoryEntity>>



    @Query(

        """

        SELECT * FROM member_status_history

        WHERE member_id = :memberId AND club_id = :clubId

        ORDER BY change_date DESC

        """

    )

    suspend fun getByMemberId(memberId: Long, clubId: Long): List<MemberStatusHistoryEntity>



    @Query(

        """

        SELECT * FROM member_status_history

        WHERE member_id = :memberId AND club_id = :clubId

        ORDER BY change_date DESC

        """

    )

    suspend fun getMemberHistory(memberId: Long, clubId: Long): List<MemberStatusHistoryEntity>

}

