package com.smartexpense.data.repository.club

import androidx.room.withTransaction
import com.smartexpense.data.excelimport.ExcelImportException
import com.smartexpense.data.excelimport.ExcelImportHeaders
import com.smartexpense.data.excelimport.ExcelImportResult
import com.smartexpense.data.excelimport.ExcelParsedData
import com.smartexpense.data.firebase.SystemAdminConfig
import com.smartexpense.data.local.entity.club.ClubAccountEntity
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.YearlyDuesEntity
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClubDataImportRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val clubRepository: ClubRepository
) {
    private val database get() = databaseGateway.database()
    private val clubDao get() = databaseGateway.clubDao()
    private val memberDao get() = databaseGateway.memberDao()
    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val yearlyDuesDao get() = databaseGateway.yearlyDuesDao()
    private val clubAccountDao get() = databaseGateway.clubAccountDao()

    /**
     * Smart Overwrite: [targetClubId] 모임에 연결된 데이터만 삭제한 뒤 엑셀 내용으로 채웁니다.
     * 엑셀에 기록된 과거 club_id는 무시하며, 모든 레코드는 [targetClubId]로 insert 됩니다.
     */
    suspend fun importParsedDataForClub(
        targetClubId: Long,
        parsed: ExcelParsedData
    ): ExcelImportResult {
        ensureSingleClubBackupFile(parsed)

        return database.withTransaction {
            clubRepository.clearClubScopedDataForRestore(targetClubId)

            parsed.clubInfo?.clubSlogan?.takeIf { it.isNotBlank() }?.let { slogan ->
                clubDao.updateSlogan(targetClubId, slogan)
            }

            val members = parsed.members.filterNot { row ->
                SystemAdminConfig.isClubMembershipRecord(email = row.email, name = row.name)
            }
            if (members.isNotEmpty()) {
                memberDao.insertAll(
                    members.map { row ->
                        MemberEntity(
                            clubId = targetClubId,
                            joinDate = row.joinDate,
                            name = row.name,
                            birthDate = row.birthDate,
                            address = row.address,
                            detailAddress = row.detailAddress,
                            residenceRegion = row.residenceRegion,
                            phone = row.phone,
                            email = row.email,
                            status = row.status,
                            role = row.role,
                            isLunarBirth = row.isLunarBirth,
                            suspensionDate = row.suspensionDate
                        )
                    }
                )
            }

            // 회원 insert 후 새로 발급된 ID를 이름 기준으로 매핑 (과거 member_id는 사용하지 않음)
            val memberIdByName = memberDao.getAllOnce(targetClubId).associate { it.name to it.id }

            // 2단계: 회비 상세 insert → 과거 회비상세ID → 새 ID 맵 생성
            val duesDetailIdMap = mutableMapOf<Long, Long>()
            var duesDetailCount = 0
            if (parsed.dues.isNotEmpty()) {
                parsed.dues.groupBy { Triple(it.memberName, it.year, it.paymentMethod) }
                    .forEach { (key, detailRows) ->
                        val (memberName, year, paymentMethod) = key
                        val memberId = memberIdByName[memberName]
                            ?: throw ExcelImportException(
                                "회비내역의 회원 '$memberName'을 회원목록에서 찾을 수 없습니다."
                            )

                        val yearlyDuesId = yearlyDuesDao.insertYearlyDuesForImport(
                            YearlyDuesEntity(
                                clubId = targetClubId,
                                memberId = memberId,
                                year = year,
                                totalTargetAmount = detailRows.sumOf { it.amount },
                                paymentMethod = paymentMethod
                            )
                        )
                        val detailEntities = detailRows.map { row ->
                            DuesDetailEntity(
                                clubId = targetClubId,
                                yearlyDuesId = yearlyDuesId,
                                termLabel = row.termLabel,
                                amount = row.amount,
                                paidAmount = row.paidAmount,
                                isPaid = row.isPaid,
                                payDate = row.payDate
                            )
                        }
                        val newDetailIds = yearlyDuesDao.insertDetailsForImport(detailEntities)
                        detailRows.zip(newDetailIds).forEach { (row, newDetailId) ->
                            row.sourceDetailId?.let { sourceId ->
                                recordDuesDetailIdMapping(duesDetailIdMap, sourceId, newDetailId)
                            }
                        }
                        duesDetailCount += detailRows.size
                    }
            }

            // 3단계: 장부 insert — linked_dues_detail_id를 새 회비 ID로 치환
            val transactions = parsed.transactions
            var expectedLinkedCount = 0
            if (transactions.isNotEmpty()) {
                clubTransactionDao.insertAll(
                    transactions.map { row ->
                        val targetMemberId = row.targetMemberName?.takeIf { it.isNotBlank() }?.let { name ->
                            memberIdByName[name]
                                ?: throw ExcelImportException(
                                    "장부내역의 대상회원 '$name'을 회원목록에서 찾을 수 없습니다."
                                )
                        }
                        val linkedDuesDetailId = row.linkedDuesDetailId?.let { sourceLinkedId ->
                            expectedLinkedCount++
                            resolveLinkedDuesDetailId(sourceLinkedId, duesDetailIdMap)
                        }
                        ClubTransactionEntity(
                            clubId = targetClubId,
                            date = row.date,
                            type = row.type,
                            category = row.category,
                            incomeAmount = row.incomeAmount,
                            expenseAmount = row.expenseAmount,
                            note = row.note,
                            balanceAfter = row.balanceAfter,
                            targetMemberId = targetMemberId,
                            linkedDuesDetailId = linkedDuesDetailId
                        )
                    }
                )
            }

            verifyDuesLedgerIntegrity(
                clubId = targetClubId,
                expectedLinkedCount = expectedLinkedCount
            )

            val accounts = parsed.accounts
            if (accounts.isNotEmpty()) {
                clubAccountDao.insertAll(
                    accounts.map { row ->
                        ClubAccountEntity(
                            clubId = targetClubId,
                            bankName = row.bankName,
                            accountNumber = row.accountNumber,
                            holderName = row.holderName
                        )
                    }
                )
            }

            ExcelImportResult(
                memberCount = members.size,
                transactionCount = transactions.size,
                duesDetailCount = duesDetailCount,
                accountCount = accounts.size,
                clubId = targetClubId
            )
        }
    }

    private fun recordDuesDetailIdMapping(
        duesDetailIdMap: MutableMap<Long, Long>,
        sourceDetailId: Long,
        newDetailId: Long
    ) {
        duesDetailIdMap.put(sourceDetailId, newDetailId)?.let { previous ->
            if (previous != newDetailId) {
                throw ExcelImportException(
                    "회비상세ID($sourceDetailId)가 백업 파일 내에서 중복되어 있습니다."
                )
            }
        }
    }

    private fun resolveLinkedDuesDetailId(
        sourceLinkedId: Long,
        duesDetailIdMap: Map<Long, Long>
    ): Long =
        duesDetailIdMap[sourceLinkedId]
            ?: throw ExcelImportException(
                "장부내역의 연동회비ID($sourceLinkedId)에 해당하는 회비 상세를 찾을 수 없습니다. " +
                    "회비내역 시트에 회비상세ID가 포함된 백업 파일인지 확인해 주세요."
            )

    /**
     * 복원 후 장부↔회비 linked_dues_detail_id 무결성을 검증합니다.
     */
    private suspend fun verifyDuesLedgerIntegrity(
        clubId: Long,
        expectedLinkedCount: Int
    ) {
        val transactions = clubTransactionDao.getAllOnce(clubId)
        val linkedTransactions = transactions.filter { it.linkedDuesDetailId != null }
        if (linkedTransactions.size != expectedLinkedCount) {
            throw ExcelImportException(
                "장부-회비 연동 검증 실패: 연동 대상 ${expectedLinkedCount}건 중 " +
                    "${linkedTransactions.size}건만 저장되었습니다."
            )
        }

        val linkedByDetailId = mutableMapOf<Long, Long>()
        linkedTransactions.forEach { transaction ->
            val detailId = transaction.linkedDuesDetailId ?: return@forEach
            yearlyDuesDao.getDetailById(detailId, clubId)
                ?: throw ExcelImportException(
                    "장부-회비 연동 검증 실패: 연동 회비 상세 ID($detailId)를 DB에서 찾을 수 없습니다."
                )
            linkedByDetailId.put(detailId, transaction.id)?.let {
                throw ExcelImportException(
                    "장부-회비 연동 검증 실패: 회비 상세 ID($detailId)에 여러 장부가 연결되어 있습니다."
                )
            }
            clubTransactionDao.findByLinkedDuesDetail(clubId, detailId)
                ?: throw ExcelImportException(
                    "장부-회비 연동 검증 실패: 회비 상세 ID($detailId)로 장부를 조회할 수 없습니다."
                )
        }
    }

    /**
     * 단일 모임 백업 파일인지 확인합니다.
     * 엑셀의 과거 club_id·모임명과 현재 활성 모임이 달라도 복원을 허용합니다.
     */
    private fun ensureSingleClubBackupFile(parsed: ExcelParsedData) {
        val distinctClubNames = collectClubNames(parsed)
            .filter {
                it.isNotEmpty() &&
                    it != ExcelImportHeaders.LEGACY_DEFAULT_CLUB_NAME &&
                    it != ExcelImportHeaders.TEMPLATE_EXAMPLE_CLUB_NAME
            }
        if (distinctClubNames.size > 1) {
            throw ExcelImportException(
                "여러 모임(${distinctClubNames.joinToString(", ")}) 데이터가 한 파일에 포함되어 있습니다. " +
                    "현재 선택 모임용 백업 파일 하나만 복원할 수 있습니다."
            )
        }
    }

    private fun collectClubNames(parsed: ExcelParsedData): Set<String> =
        buildSet {
            parsed.clubInfo?.clubName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            parsed.members.forEach { add(it.clubName.trim()) }
            parsed.transactions.forEach { add(it.clubName.trim()) }
            parsed.dues.forEach { add(it.clubName.trim()) }
            parsed.accounts.forEach { add(it.clubName.trim()) }
        }
}
