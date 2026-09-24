package com.smartexpense.data.local.seed

import android.content.Context
import androidx.room.withTransaction
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.data.local.prefs.PostRestoreFlags
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.data.local.ClubConstants
import com.smartexpense.data.local.entity.club.ClubEntity
import com.smartexpense.data.local.entity.club.ClubSettingEntity
import com.smartexpense.data.local.entity.club.ClubSettingKeys
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.data.local.entity.club.EventExpenseEntity
import com.smartexpense.data.local.entity.club.EventSubCategory
import com.smartexpense.data.local.entity.club.YearlyDuesEntity
import com.smartexpense.data.repository.club.SelectedClubRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val clubRepository: ClubRepository
) {
    private val clubDao get() = databaseGateway.clubDao()
    private val memberDao get() = databaseGateway.memberDao()
    private val yearlyDuesDao get() = databaseGateway.yearlyDuesDao()
    private val duesPaymentHistoryDao get() = databaseGateway.duesPaymentHistoryDao()
    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val eventExpenseDao get() = databaseGateway.eventExpenseDao()
    private val clubHistoryDao get() = databaseGateway.clubHistoryDao()
    private val clubAccountDao get() = databaseGateway.clubAccountDao()
    private val database get() = databaseGateway.database()

    suspend fun bootstrapOnStartup() {
        if (PostRestoreFlags.isPending(context)) {
            ensureHanuriSeedClubExists()
            seedIfEmpty()
            return
        }
        if (clubRepository.isDatabaseEmpty()) {
            selectedClubRepository.clearSelectedClubId()
            resetSeedVersionPrefs()
            ensureHanuriSeedClubExists()
        }
        seedIfEmpty()
    }

    /** 한우리 시드 대상 모임이 없으면 1개만 생성합니다. 다른 모임은 만들지 않습니다. */
    private suspend fun ensureHanuriSeedClubExists() {
        val clubs = clubRepository.getAllClubsOnce()
        if (clubs.any { ClubConstants.isHanuriSeedClub(it.clubName) }) return
        val clubId = clubRepository.createClub(ClubConstants.HANURI_SEED_CLUB_NAME)
        selectedClubRepository.setSelectedClubId(clubId)
    }

    suspend fun seedIfEmpty() {
        val hanuriClub = resolveHanuriSeedClub() ?: run {
            markSeedMigrationComplete()
            return
        }
        val clubId = hanuriClub.clubId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt(KEY_INITIAL_DATA_VERSION, 0)
        if (currentVersion >= INITIAL_DATA_VERSION) return

        // v1~v8: Sheet2 경조사비가 장부에 섞여 잔액이 깨진 경우를 엑셀 560건으로 복구
        if (currentVersion in 1 until INITIAL_DATA_VERSION) {
            if (currentVersion <= 5) seedClubHistoryIfEmpty(clubId)
            if (currentVersion <= 6) seedClubAccountsIfEmpty(clubId)
            if (currentVersion <= 4) backfillMemberResidenceRegions(clubId)
            database.withTransaction {
                repairLedgerTransactionsFromExcelSeed(clubId)
                // v11 이하: 회비 시드가 엑셀 기준으로 재이행됨 — 도메인 테이블 전체 재구성
                rebuildDuesFromSeed(clubId)
            }
            prefs.edit().putInt(KEY_INITIAL_DATA_VERSION, INITIAL_DATA_VERSION).apply()
            return
        }

        database.withTransaction {
            clearAllSeedTables(clubId)
            seedAllFromInitialData(clubId)
        }

        prefs.edit().putInt(KEY_INITIAL_DATA_VERSION, INITIAL_DATA_VERSION).apply()
    }

    suspend fun resetToDefaultSeedData() {
        val clubId = selectedClubRepository.selectedClubId.first()
            ?: throw IllegalStateException("선택된 모임이 없습니다.")
        val club = clubRepository.getClub(clubId)
            ?: throw IllegalStateException("선택된 모임을 찾을 수 없습니다.")
        require(ClubConstants.isHanuriSeedClub(club.clubName)) {
            "초기 데이터 재설정은 한우리 모임에서만 사용할 수 있습니다."
        }
        database.withTransaction {
            clearAllSeedTables(clubId)
            seedAllFromInitialData(clubId)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INITIAL_DATA_VERSION, INITIAL_DATA_VERSION)
            .apply()
    }

    private suspend fun resolveHanuriSeedClub(): ClubEntity? {
        clubRepository.getClub(SelectedClubRepository.DEFAULT_CLUB_ID)
            ?.takeIf { ClubConstants.isHanuriSeedClub(it.clubName) }
            ?.let { return it }
        return clubRepository.getAllClubsOnce()
            .firstOrNull { ClubConstants.isHanuriSeedClub(it.clubName) }
    }

    private suspend fun clearAllSeedTables(clubId: Long) {
        clubTransactionDao.deleteAll(clubId)
        eventExpenseDao.deleteAll(clubId)
        duesPaymentHistoryDao.deleteAll(clubId)
        yearlyDuesDao.deleteAllDetails(clubId)
        yearlyDuesDao.deleteAllYearlyDues(clubId)
        memberDao.deleteAllStatusHistory(clubId)
        memberDao.deleteAllMembers(clubId)
        clubHistoryDao.deleteAll(clubId)
        clubAccountDao.deleteAll(clubId)
    }

    private suspend fun seedAllFromInitialData(clubId: Long) {
        seedClubSlogan(clubId)
        memberDao.insertAll(MemberInitialData.members(clubId))
        seedDues(clubId) // 회비 도메인만 — 장부 중복 insert 금지
        seedTransactions(clubId) // 통장 실입출금만
        seedEventExpenses(clubId) // 경조 도메인만 — 장부 금지
        seedClubHistory(clubId)
        seedClubAccounts(clubId)
        persistSeedLedgerBalance(clubId)
    }

    /**
     * 장부를 엑셀 통장 시드로 복구하고, 경조 Sheet2는 event_expenses에만 넣습니다.
     */
    private suspend fun repairLedgerTransactionsFromExcelSeed(clubId: Long) {
        clubTransactionDao.deleteAll(clubId)
        eventExpenseDao.deleteAll(clubId)
        seedTransactions(clubId)
        seedEventExpenses(clubId)
        persistSeedLedgerBalance(clubId)
    }

    /** 회비 도메인 테이블(연회비/상세/납부이력)을 시드 기준으로 전체 재구성. */
    private suspend fun rebuildDuesFromSeed(clubId: Long) {
        duesPaymentHistoryDao.deleteAll(clubId)
        yearlyDuesDao.deleteAllDetails(clubId)
        yearlyDuesDao.deleteAllYearlyDues(clubId)
        seedDues(clubId)
    }

    private suspend fun persistSeedLedgerBalance(clubId: Long) {
        val income = clubTransactionDao.getSumIncomeOnce(clubId)
        val expense = clubTransactionDao.getSumExpenseOnce(clubId)
        val balance = income - expense
        check(balance.toInt() == TransactionInitialData.SEED_FINAL_BALANCE) {
            "한우리 시드 잔액이 ${TransactionInitialData.SEED_FINAL_BALANCE}원이어야 하는데 ${balance}원입니다."
        }
        val now = System.currentTimeMillis()
        databaseGateway.clubSettingsDao().upsert(
            ClubSettingEntity(
                clubId = clubId,
                settingKey = ClubSettingKeys.CURRENT_BALANCE,
                settingValue = balance.toString(),
                updatedAt = now
            )
        )
        databaseGateway.clubSettingsDao().upsert(
            ClubSettingEntity(
                clubId = clubId,
                settingKey = ClubSettingKeys.INITIAL_BALANCE,
                settingValue = "0",
                updatedAt = now
            )
        )
    }

    private suspend fun seedClubSlogan(clubId: Long) {
        clubDao.updateSlogan(clubId, ClubConstants.HANURI_SEED_SLOGAN)
    }

    private suspend fun seedDues(clubId: Long) {
        val memberIdByName = memberDao.getAllOnce(clubId).associate { it.name to it.id }
        DuesInitialData.seedsByMember.forEach { (name, seed) ->
            val memberId = memberIdByName[name] ?: return@forEach
            seed.records.groupBy { it.year }.forEach { (year, yearRecords) ->
                val yearlyDuesId = yearlyDuesDao.insertYearlyDues(
                    YearlyDuesEntity(
                        clubId = clubId,
                        memberId = memberId,
                        year = year,
                        totalTargetAmount = DuesInitialData.totalTargetAmountForYear(seed.records, year),
                        paymentMethod = seed.paymentMethod
                    )
                )
                yearRecords.forEach { record ->
                    val paid = when {
                        record.payments != null -> record.payments.sumOf { it.amount }.toLong()
                        record.paidAmount != null -> record.paidAmount.toLong()
                        record.isPaid -> record.amount.toLong()
                        else -> 0L
                    }
                    val payDate = when {
                        record.payments != null ->
                            record.payments.filter { it.amount > 0 }.maxOfOrNull { it.payDate }.orEmpty()
                        else -> record.payDate
                    }
                    val detailIds = yearlyDuesDao.insertDetailsForImport(
                        listOf(
                            DuesDetailEntity(
                                clubId = clubId,
                                yearlyDuesId = yearlyDuesId,
                                termLabel = record.termLabel,
                                amount = record.amount,
                                paidAmount = paid,
                        isPaid = record.isPaid || (paid >= record.amount && record.amount > 0),
                                payDate = payDate
                            )
                        )
                    )
                    val detailId = detailIds.firstOrNull() ?: return@forEach
                    val paymentSeeds = record.payments
                        ?: if (paid > 0 && payDate.isNotBlank()) {
                            listOf(DuesInitialData.DuesPaymentSeed(payDate, paid.toInt()))
                        } else {
                            emptyList()
                        }
                    if (paymentSeeds.isNotEmpty()) {
                        duesPaymentHistoryDao.insertAll(
                            paymentSeeds.map { payment ->
                                DuesPaymentHistoryEntity(
                                    clubId = clubId,
                                    duesDetailId = detailId,
                                    payDate = payment.payDate,
                                    amount = payment.amount.toLong()
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun seedTransactions(clubId: Long) {
        // 엑셀 입출금 기록부(통장) — 잔액 SSOT. 회비/경조 "도메인 이력"과 별개.
        clubTransactionDao.insertAll(
            TransactionInitialData.records.map { record ->
                ClubTransactionEntity(
                    clubId = clubId,
                    date = record.date,
                    type = record.type,
                    category = record.category,
                    incomeAmount = record.incomeAmount,
                    expenseAmount = record.expenseAmount,
                    note = record.note,
                    balanceAfter = record.balanceAfter
                )
            }
        )
    }

    /** Sheet2 등 경조 누적 — event_expenses만. 장부에 넣지 않음. */
    private suspend fun seedEventExpenses(clubId: Long) {
        val memberIdByName = memberDao.getAllOnce(clubId).associate { it.name to it.id }
        val now = EntityTimestamps.now()
        val entities = EventExpenseInitialData.records.mapNotNull { record ->
            val memberId = memberIdByName[record.memberName] ?: return@mapNotNull null
            EventExpenseEntity(
                clubId = clubId,
                memberId = memberId,
                date = EventExpenseInitialData.SEED_DATE,
                eventSubCategory = EventSubCategory.fromExcelLabel(record.rawSubCategory),
                amount = record.amount,
                note = "기초 데이터 (${record.rawSubCategory})",
                linkedTransactionId = null,
                isSeedOnly = true,
                updatedAt = now
            )
        }
        if (entities.isNotEmpty()) {
            eventExpenseDao.insertAll(entities)
        }
    }

    private suspend fun seedClubHistoryIfEmpty(clubId: Long) {
        if (clubHistoryDao.count(clubId) > 0) return
        seedClubHistory(clubId)
    }

    private suspend fun seedClubHistory(clubId: Long) {
        clubHistoryDao.insertAll(ClubHistoryInitialData.toEntities(clubId))
    }

    private suspend fun seedClubAccountsIfEmpty(clubId: Long) {
        if (clubAccountDao.count(clubId) > 0) return
        seedClubAccounts(clubId)
    }

    private suspend fun seedClubAccounts(clubId: Long) {
        clubAccountDao.insertAll(ClubAccountInitialData.accounts(clubId))
    }

    private suspend fun backfillMemberResidenceRegions(clubId: Long) {
        memberDao.getAllOnce(clubId).forEach { member ->
            val region = MemberInitialData.residenceByName[member.name] ?: return@forEach
            if (member.residenceRegion.isBlank()) {
                memberDao.update(member.copy(residenceRegion = region))
            }
        }
    }

    /**
     * 기존 DB에 분할 납부가 1건으로만 남아 있는 경우,
     * 시드의 payments 목록으로 납부 이력을 다시 채웁니다.
     */
    private suspend fun rebuildDuesPaymentHistoriesFromSeed(clubId: Long) {
        val memberIdByName = memberDao.getAllOnce(clubId).associate { it.name to it.id }
        val detailsByKey = yearlyDuesDao.getAllWithDetailsOnce(clubId)
            .flatMap { record ->
                record.details.map { detail ->
                    Triple(record.yearlyDues.memberId, record.yearlyDues.year, detail.termLabel) to detail
                }
            }
            .toMap()

        DuesInitialData.seedsByMember.forEach { (name, seed) ->
            val memberId = memberIdByName[name] ?: return@forEach
            seed.records.forEach { record ->
                val detail = detailsByKey[Triple(memberId, record.year, record.termLabel)]
                    ?: return@forEach
                val paid = when {
                    record.payments != null -> record.payments.sumOf { it.amount }.toLong()
                    record.paidAmount != null -> record.paidAmount.toLong()
                    record.isPaid -> record.amount.toLong()
                    else -> 0L
                }
                val payDate = when {
                    record.payments != null ->
                        record.payments.filter { it.amount > 0 }.maxOfOrNull { it.payDate }.orEmpty()
                    else -> record.payDate
                }
                val paymentSeeds = record.payments
                    ?: if (paid > 0 && payDate.isNotBlank()) {
                        listOf(DuesInitialData.DuesPaymentSeed(payDate, paid.toInt()))
                    } else {
                        emptyList()
                    }

                yearlyDuesDao.updateDetail(
                    detail.copy(
                        paidAmount = paid,
                        isPaid = record.isPaid || (paid >= detail.amount && detail.amount > 0),
                        payDate = payDate
                    )
                )
                duesPaymentHistoryDao.deleteByDetailId(detail.id, clubId)
                if (paymentSeeds.isNotEmpty()) {
                    duesPaymentHistoryDao.insertAll(
                        paymentSeeds.map { payment ->
                            DuesPaymentHistoryEntity(
                                clubId = clubId,
                                duesDetailId = detail.id,
                                payDate = payment.payDate,
                                amount = payment.amount.toLong()
                            )
                        }
                    )
                }
            }
        }
    }

    private fun resetSeedVersionPrefs() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INITIAL_DATA_VERSION, 0)
            .apply()
    }

    private fun markSeedMigrationComplete() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INITIAL_DATA_VERSION, INITIAL_DATA_VERSION)
            .apply()
    }

    /** 엑셀 Clean Restore 후 초기 시드 데이터가 덮어쓰지 않도록 버전을 고정합니다. */
    fun markExternalImportComplete() {
        markSeedMigrationComplete()
    }

    companion object {
        private const val PREFS_NAME = "smart_expense_seed"
        private const val KEY_INITIAL_DATA_VERSION = "initial_data_version"
        private const val INITIAL_DATA_VERSION = 12
    }
}
