package com.smartexpense.data.local.dao.club



import androidx.room.Dao

import androidx.room.Delete

import androidx.room.Insert

import androidx.room.OnConflictStrategy

import androidx.room.Query

import androidx.room.Transaction

import androidx.room.Update

import com.smartexpense.data.local.entity.club.DuesDetailEntity

import com.smartexpense.data.local.entity.club.DuesTerm

import com.smartexpense.data.local.entity.club.YearlyDuesEntity

import com.smartexpense.data.local.model.club.MemberDuesStatusRow

import com.smartexpense.data.local.model.club.YearlyDuesAggregateRow

import com.smartexpense.data.local.model.club.YearlyDuesWithDetails

import kotlinx.coroutines.flow.Flow



@Dao

interface YearlyDuesDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)

    suspend fun insertYearlyDues(yearlyDues: YearlyDuesEntity): Long



    @Update

    suspend fun updateYearlyDues(yearlyDues: YearlyDuesEntity)



    @Delete

    suspend fun deleteYearlyDues(yearlyDues: YearlyDuesEntity)



    @Insert(onConflict = OnConflictStrategy.ABORT)

    suspend fun insertDetails(details: List<DuesDetailEntity>)



    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertYearlyDuesForImport(yearlyDues: YearlyDuesEntity): Long



    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertDetailsForImport(details: List<DuesDetailEntity>): List<Long>



    @Update

    suspend fun updateDetail(detail: DuesDetailEntity)



    @Query("SELECT * FROM dues_detail WHERE id = :id AND club_id = :clubId")

    suspend fun getDetailById(id: Long, clubId: Long): DuesDetailEntity?



    @Query("DELETE FROM dues_detail WHERE yearly_dues_id = :yearlyDuesId AND club_id = :clubId")

    suspend fun deleteDetailsByYearlyDuesId(yearlyDuesId: Long, clubId: Long)



    @Query("DELETE FROM dues_detail WHERE club_id = :clubId")

    suspend fun deleteAllDetails(clubId: Long)



    @Query("DELETE FROM yearly_dues WHERE club_id = :clubId")

    suspend fun deleteAllYearlyDues(clubId: Long)



    @Query("SELECT COUNT(*) FROM yearly_dues WHERE club_id = :clubId")

    suspend fun getCount(clubId: Long): Int



    @Transaction

    @Query("SELECT * FROM yearly_dues WHERE id = :id AND club_id = :clubId")

    suspend fun getWithDetails(id: Long, clubId: Long): YearlyDuesWithDetails?



    @Transaction

    @Query(

        """

        SELECT * FROM yearly_dues

        WHERE member_id = :memberId AND year = :year AND club_id = :clubId

        LIMIT 1

        """

    )

    suspend fun getYearlyDuesByMemberAndYear(

        memberId: Long,

        year: Int,

        clubId: Long

    ): YearlyDuesWithDetails?



    @Transaction

    @Query("SELECT * FROM yearly_dues WHERE club_id = :clubId ORDER BY year DESC, id DESC")

    fun observeAllWithDetails(clubId: Long): Flow<List<YearlyDuesWithDetails>>



    @Transaction

    @Query("SELECT * FROM yearly_dues WHERE club_id = :clubId ORDER BY year ASC, id ASC")

    suspend fun getAllWithDetailsOnce(clubId: Long): List<YearlyDuesWithDetails>



    @Transaction

    @Query(

        """

        SELECT yd.* FROM yearly_dues yd

        INNER JOIN members m ON m.id = yd.member_id

        WHERE yd.club_id = :clubId

          AND m.club_id = :clubId

          AND (:year IS NULL OR yd.year = :year)

          AND (:memberId IS NULL OR yd.member_id = :memberId)

        ORDER BY yd.year DESC, m.name COLLATE NOCASE ASC, yd.id DESC

        """

    )

    fun observeYearlyDuesWithDetailsFiltered(

        clubId: Long,

        year: Int?,

        memberId: Long?

    ): Flow<List<YearlyDuesWithDetails>>



    @Query(

        """

        SELECT EXISTS(

            SELECT 1 FROM yearly_dues

            WHERE member_id = :memberId AND year = :year AND club_id = :clubId

              AND (:excludeId IS NULL OR id != :excludeId)

        )

        """

    )

    suspend fun existsByMemberAndYear(

        memberId: Long,

        year: Int,

        clubId: Long,

        excludeId: Long? = null

    ): Boolean



    @Query(

        """

        SELECT id FROM yearly_dues

        WHERE member_id = :memberId AND year = :year AND club_id = :clubId

        LIMIT 1

        """

    )

    suspend fun findIdByMemberAndYear(memberId: Long, year: Int, clubId: Long): Long?



    @Query(

        """

        SELECT

            m.id AS member_id,

            m.name AS member_name,

            m.role AS role,

            m.status AS status,

            :year AS year,

            terms.term AS term,

            COALESCE(

                CASE terms.term

                    WHEN 'FIRST_HALF' THEN (

                        SELECT SUM(dd.paid_amount)

                        FROM yearly_dues yd

                        INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

                        WHERE yd.member_id = m.id

                          AND yd.year = :year

                          AND yd.club_id = :clubId

                          AND dd.paid_amount > 0

                          AND CAST(substr(dd.pay_date, 6, 2) AS INTEGER) <= 6

                    )

                    ELSE (

                        SELECT SUM(dd.paid_amount)

                        FROM yearly_dues yd

                        INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

                        WHERE yd.member_id = m.id

                          AND yd.year = :year

                          AND yd.club_id = :clubId

                          AND dd.paid_amount > 0

                          AND CAST(substr(dd.pay_date, 6, 2) AS INTEGER) >= 7

                    )

                END,

                0

            ) AS paid_amount,

            CASE terms.term

                WHEN 'FIRST_HALF' THEN (

                    SELECT MAX(dd.pay_date)

                    FROM yearly_dues yd

                    INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

                    WHERE yd.member_id = m.id

                      AND yd.year = :year

                      AND yd.club_id = :clubId

                      AND dd.is_paid = 1

                      AND CAST(substr(dd.pay_date, 6, 2) AS INTEGER) <= 6

                )

                ELSE (

                    SELECT MAX(dd.pay_date)

                    FROM yearly_dues yd

                    INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

                    WHERE yd.member_id = m.id

                      AND yd.year = :year

                      AND yd.club_id = :clubId

                      AND dd.is_paid = 1

                      AND CAST(substr(dd.pay_date, 6, 2) AS INTEGER) >= 7

                )

            END AS pay_date

        FROM members m

        CROSS JOIN (

            SELECT 'FIRST_HALF' AS term

            UNION ALL

            SELECT 'SECOND_HALF' AS term

        ) AS terms

        WHERE m.club_id = :clubId AND m.status != 'WITHDRAWN'

        ORDER BY m.name ASC, terms.term ASC

        """

    )

    fun observeMemberDuesStatusByYear(clubId: Long, year: Int): Flow<List<MemberDuesStatusRow>>



    @Query(

        """

        SELECT

            :year AS year,

            'FIRST_HALF' AS term,

            COALESCE(SUM(dd.paid_amount), 0) AS total_amount,

            COUNT(DISTINCT yd.member_id) AS payer_count

        FROM yearly_dues yd

        INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

        WHERE yd.year = :year

          AND yd.club_id = :clubId

          AND dd.paid_amount > 0

          AND CAST(substr(dd.pay_date, 6, 2) AS INTEGER) <= 6

        UNION ALL

        SELECT

            :year AS year,

            'SECOND_HALF' AS term,

            COALESCE(SUM(dd.paid_amount), 0) AS total_amount,

            COUNT(DISTINCT yd.member_id) AS payer_count

        FROM yearly_dues yd

        INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

        WHERE yd.year = :year

          AND yd.club_id = :clubId

          AND dd.paid_amount > 0

          AND CAST(substr(dd.pay_date, 6, 2) AS INTEGER) >= 7

        """

    )

    fun observeYearlyDuesAggregate(clubId: Long, year: Int): Flow<List<YearlyDuesAggregateRow>>



    @Query(

        """

        SELECT COALESCE(SUM(dd.paid_amount), 0)

        FROM yearly_dues yd

        INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

        WHERE yd.year = :year

          AND yd.club_id = :clubId

          AND dd.paid_amount > 0

        """

    )

    fun observeYearTotal(clubId: Long, year: Int): Flow<Int>



    @Query(

        """

        SELECT COALESCE(SUM(dd.paid_amount), 0)

        FROM yearly_dues yd

        INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

        WHERE yd.year = :year

          AND yd.club_id = :clubId

          AND dd.paid_amount > 0

        """

    )

    suspend fun getYearTotalOnce(clubId: Long, year: Int): Int



    @Query(

        """

        SELECT COALESCE(SUM(dd.paid_amount), 0)

        FROM yearly_dues yd

        INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

        WHERE yd.club_id = :clubId

          AND dd.paid_amount > 0

          AND (

            (dd.pay_date != '' AND dd.pay_date <= :endDate)

            OR (dd.pay_date = '' AND yd.year <= :year)

          )

        """

    )

    fun observeCumulativePaidUpTo(clubId: Long, endDate: String, year: Int): Flow<Int>



    @Query(

        """

        SELECT COALESCE(SUM(dd.paid_amount), 0)

        FROM yearly_dues yd

        INNER JOIN dues_detail dd ON dd.yearly_dues_id = yd.id AND dd.club_id = yd.club_id

        WHERE yd.year = :year

          AND yd.club_id = :clubId

          AND dd.paid_amount > 0

          AND (

            (:term = 'FIRST_HALF' AND CAST(substr(dd.pay_date, 6, 2) AS INTEGER) <= 6)

            OR (:term = 'SECOND_HALF' AND CAST(substr(dd.pay_date, 6, 2) AS INTEGER) >= 7)

          )

        """

    )

    fun observeTermTotal(clubId: Long, year: Int, term: DuesTerm): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(amount - paid_amount), 0)
        FROM dues_detail
        WHERE club_id = :clubId AND is_paid = 0 AND is_excluded = 0
        """
    )
    fun observeTotalUnpaidDues(clubId: Long): Flow<Long>

    @Query(
        """
        SELECT * FROM yearly_dues
        WHERE club_id = :clubId AND updated_at > :since
        """
    )
    suspend fun getYearlyUpdatedSince(clubId: Long, since: Long): List<YearlyDuesEntity>

    @Query(
        """
        SELECT DISTINCT yearly_dues_id FROM dues_detail
        WHERE club_id = :clubId AND updated_at > :since
        """
    )
    suspend fun getYearlyIdsWithDetailUpdatedSince(clubId: Long, since: Long): List<Long>

    @Transaction
    @Query(
        """
        SELECT * FROM yearly_dues
        WHERE club_id = :clubId AND id IN (:ids)
        """
    )
    suspend fun getWithDetailsByIds(clubId: Long, ids: List<Long>): List<YearlyDuesWithDetails>

    @Query(
        """
        UPDATE yearly_dues SET cloud_doc_id = :cloudDocId
        WHERE id = :id AND club_id = :clubId
        """
    )
    suspend fun updateYearlyCloudDocId(id: Long, clubId: Long, cloudDocId: String)

    @Query(
        """
        UPDATE dues_detail SET cloud_doc_id = :cloudDocId
        WHERE id = :id AND club_id = :clubId
        """
    )
    suspend fun updateDetailCloudDocId(id: Long, clubId: Long, cloudDocId: String)

}

