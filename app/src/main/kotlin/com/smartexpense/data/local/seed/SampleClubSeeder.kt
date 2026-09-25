package com.smartexpense.data.local.seed

import com.smartexpense.data.local.ClubConstants
import com.smartexpense.data.local.entity.club.ClubSettingEntity
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.local.entity.club.YearlyDuesEntity
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 승인 전(미승인) 일반 회원에게 보여줄 "체험용 샘플" 모임을 준비합니다.
 *
 * - 웹(`src/lib/sampleData.ts`)의 `SAMPLE_*` 와 동일하게 소량 가짜 데이터(회원 3명·거래 3건·회비 2건)만 담습니다.
 * - 회원명은 실명과 겹치지 않는 임의 이름(가람·나현·다솔)을 사용합니다.
 * - [ClubConstants.DEFAULT_CLUB_ID]에 있는 한우리 실(seed) 데이터와는 완전히 별개의 Room `clubId`를 사용하므로
 *   로그인은 했지만 아직 모임 멤버가 아닌 사용자가 실데이터/클라우드 데이터를 볼 수 없습니다.
 */
@Singleton
class SampleClubSeeder @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val clubRepository: ClubRepository
) {
    private val clubDao get() = databaseGateway.clubDao()
    private val memberDao get() = databaseGateway.memberDao()
    private val yearlyDuesDao get() = databaseGateway.yearlyDuesDao()
    private val duesPaymentHistoryDao get() = databaseGateway.duesPaymentHistoryDao()
    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val clubSettingsDao get() = databaseGateway.clubSettingsDao()

    /** 초기에 샘플에 들어갔던 실명 — 발견 시 임의 이름으로 재시드 */
    private val legacyRealSampleNames = setOf("강대화", "김민석", "김성겸", "김영섭")

    /**
     * 샘플 모임의 Room `clubId`를 준비해서 반환합니다.
     * 이미 있으면 그대로 재사용하고, 비어 있거나 실명이 남아 있거나
     * 샘플 콘텐츠 버전이 낮으면 임의 이름 데이터로 (재)채웁니다.
     */
    suspend fun ensureSampleClubReady(): Long {
        val existing = clubRepository.getAllClubsOnce()
            .firstOrNull { it.clubName == ClubConstants.SAMPLE_CLUB_NAME }
        val clubId = if (existing != null) {
            existing.clubId
        } else {
            val id = clubRepository.createClub(ClubConstants.SAMPLE_CLUB_NAME)
            clubDao.updateSlogan(id, ClubConstants.SAMPLE_CLUB_SLOGAN)
            id
        }
        val members = memberDao.getAllOnce(clubId)
        val storedVersion = clubSettingsDao.getEntry(clubId, SAMPLE_CONTENT_VERSION_KEY)
            ?.settingValue
            ?.toIntOrNull()
            ?: 0
        val needsReseed = members.isEmpty() ||
            storedVersion < SAMPLE_CONTENT_VERSION ||
            members.any { it.name in legacyRealSampleNames }
        if (needsReseed) {
            clearSampleContent(clubId)
            seedSampleData(clubId)
            clubSettingsDao.upsert(
                ClubSettingEntity(
                    clubId = clubId,
                    settingKey = SAMPLE_CONTENT_VERSION_KEY,
                    settingValue = SAMPLE_CONTENT_VERSION.toString(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return clubId
    }

    private suspend fun clearSampleContent(clubId: Long) {
        duesPaymentHistoryDao.deleteAll(clubId)
        yearlyDuesDao.deleteAllDetails(clubId)
        yearlyDuesDao.deleteAllYearlyDues(clubId)
        clubTransactionDao.deleteAll(clubId)
        memberDao.deleteAllMembers(clubId)
    }

    private suspend fun seedSampleData(clubId: Long) {
        val members = listOf(
            MemberEntity(
                clubId = clubId,
                joinDate = "2011-10-01",
                name = "가람",
                birthDate = "",
                address = "",
                residenceRegion = "대전",
                phone = "01000000001",
                status = MemberStatus.ACTIVE,
                role = MemberRole.GENERAL
            ),
            MemberEntity(
                clubId = clubId,
                joinDate = "2012-03-15",
                name = "나현",
                birthDate = "",
                address = "",
                residenceRegion = "서울",
                phone = "01000000002",
                status = MemberStatus.ACTIVE,
                role = MemberRole.PRESIDENT
            ),
            MemberEntity(
                clubId = clubId,
                joinDate = "2011-10-01",
                name = "다솔",
                birthDate = "",
                address = "",
                residenceRegion = "경기도 광명",
                phone = "01000000003",
                status = MemberStatus.ACTIVE,
                role = MemberRole.TREASURER
            )
        )
        memberDao.insertAll(members)
        val memberIdByName = memberDao.getAllOnce(clubId).associate { it.name to it.id }

        clubTransactionDao.insertAll(
            listOf(
                ClubTransactionEntity(
                    clubId = clubId,
                    date = "2026-07-12",
                    type = ClubTransactionType.INCOME,
                    category = ClubCategory.REGULAR_DUES,
                    incomeAmount = 300_000,
                    expenseAmount = 0,
                    note = "가람 회비(25년_하반기,26년_상반기)"
                ),
                ClubTransactionEntity(
                    clubId = clubId,
                    date = "2026-06-28",
                    type = ClubTransactionType.EXPENSE,
                    category = ClubCategory.MEAL,
                    incomeAmount = 0,
                    expenseAmount = 116_800,
                    note = "26년 상반기 모임 해장식대(어죽)"
                ),
                ClubTransactionEntity(
                    clubId = clubId,
                    date = "2026-06-28",
                    type = ClubTransactionType.EXPENSE,
                    category = ClubCategory.MEAL,
                    incomeAmount = 0,
                    expenseAmount = 84_000,
                    note = "26년 상반기 모임 주전부리"
                )
            )
        )

        seedDues(
            clubId = clubId,
            memberId = memberIdByName["가람"],
            year = 2026,
            firstHalfPaid = 0,
            secondHalfPaid = 0
        )
        seedDues(
            clubId = clubId,
            memberId = memberIdByName["나현"],
            year = 2026,
            firstHalfPaid = 150_000,
            firstHalfPayDate = "2026-03-01",
            secondHalfPaid = 0
        )
    }

    private suspend fun seedDues(
        clubId: Long,
        memberId: Long?,
        year: Int,
        firstHalfPaid: Long,
        firstHalfPayDate: String = "",
        secondHalfPaid: Long,
        secondHalfPayDate: String = ""
    ) {
        if (memberId == null) return
        val termAmount = 150_000
        val yearlyDuesId = yearlyDuesDao.insertYearlyDues(
            YearlyDuesEntity(
                clubId = clubId,
                memberId = memberId,
                year = year,
                totalTargetAmount = termAmount * 2,
                paymentMethod = DuesPaymentMethod.HALF_YEARLY
            )
        )
        val detailIds = yearlyDuesDao.insertDetailsForImport(
            listOf(
                DuesDetailEntity(
                    clubId = clubId,
                    yearlyDuesId = yearlyDuesId,
                    termLabel = "상반기",
                    amount = termAmount,
                    paidAmount = firstHalfPaid,
                    isPaid = firstHalfPaid >= termAmount,
                    payDate = firstHalfPayDate
                ),
                DuesDetailEntity(
                    clubId = clubId,
                    yearlyDuesId = yearlyDuesId,
                    termLabel = "하반기",
                    amount = termAmount,
                    paidAmount = secondHalfPaid,
                    isPaid = secondHalfPaid >= termAmount,
                    payDate = secondHalfPayDate
                )
            )
        )
        if (firstHalfPaid > 0 && detailIds.isNotEmpty()) {
            duesPaymentHistoryDao.insertAll(
                listOf(
                    DuesPaymentHistoryEntity(
                        clubId = clubId,
                        duesDetailId = detailIds[0],
                        payDate = firstHalfPayDate,
                        amount = firstHalfPaid
                    )
                )
            )
        }
    }

    companion object {
        /** 샘플 콘텐츠 버전 — 올리면 기존 샘플(실명 포함)을 지우고 다시 채웁니다. */
        private const val SAMPLE_CONTENT_VERSION = 2
        private const val SAMPLE_CONTENT_VERSION_KEY = "sample_content_version"
    }
}
