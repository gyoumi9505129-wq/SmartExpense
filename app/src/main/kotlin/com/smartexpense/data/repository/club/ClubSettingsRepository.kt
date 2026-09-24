package com.smartexpense.data.repository.club

import com.smartexpense.data.local.entity.club.ClubSettingEntity
import com.smartexpense.data.local.entity.club.ClubSettingKeys
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.model.bank.BankAppMaster
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.domain.settings.ClubSettingDefaults
import com.smartexpense.domain.settings.ClubSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class ClubSettingsRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository
) {
    private val clubSettingsDao get() = databaseGateway.clubSettingsDao()
    private val json = Json { ignoreUnknownKeys = true }

    fun observeSettings(): Flow<ClubSettings> =
        selectedClubRepository.scopedFlow(databaseGateway) { clubId ->
            clubSettingsDao.observeByClubId(clubId).map { entries ->
                entries.toClubSettings(clubId)
            }
        }

    fun observeSettingsForClub(clubId: Long): Flow<ClubSettings> =
        databaseGateway.roomFlowTyped {
            clubSettingsDao.observeByClubId(clubId).map { entries ->
                entries.toClubSettings(clubId)
            }
        }

    suspend fun getSettingsOnce(clubId: Long): ClubSettings =
        clubSettingsDao.getAllOnce(clubId).toClubSettings(clubId)

    suspend fun initializeForNewClub(clubId: Long) {
        if (clubSettingsDao.countByClubId(clubId) > 0) return
        upsertValue(clubId, ClubSettingKeys.BANK_PARSE_ENABLED_PACKAGES, ClubSettingDefaults.defaultEnabledPackagesJson)
    }

    suspend fun ensureSettingsExist(clubId: Long) {
        initializeForNewClub(clubId)
    }

    suspend fun updateDuesPaymentMethod(clubId: Long, method: DuesPaymentMethod) {
        upsertValue(clubId, ClubSettingKeys.DUES_PAYMENT_METHOD, method.name)
    }

    suspend fun updateEnabledPackages(clubId: Long, packages: Set<String>) {
        upsertValue(clubId, ClubSettingKeys.BANK_PARSE_ENABLED_PACKAGES, encodePackages(packages))
    }

    suspend fun updateDuesPaymentFilter(
        clubId: Long,
        year: Int?,
        memberId: Long?
    ) {
        if (year == null) {
            clubSettingsDao.deleteKey(clubId, ClubSettingKeys.DUES_PAYMENT_FILTER_YEAR)
        } else {
            upsertValue(clubId, ClubSettingKeys.DUES_PAYMENT_FILTER_YEAR, year.toString())
        }
        if (memberId == null) {
            clubSettingsDao.deleteKey(clubId, ClubSettingKeys.DUES_PAYMENT_FILTER_MEMBER_ID)
        } else {
            upsertValue(clubId, ClubSettingKeys.DUES_PAYMENT_FILTER_MEMBER_ID, memberId.toString())
        }
    }

    fun decodeEnabledPackages(settings: ClubSettings): Set<String> =
        decodePackages(settings.bankParseEnabledPackagesJson)

    fun encodePackages(packages: Set<String>): String =
        json.encodeToString(packages.sorted())

    private suspend fun upsertValue(clubId: Long, key: String, value: String) {
        clubSettingsDao.upsert(
            ClubSettingEntity(
                clubId = clubId,
                settingKey = key,
                settingValue = value
            )
        )
    }

    private fun decodePackages(raw: String): Set<String> =
        runCatching { json.decodeFromString<List<String>>(raw).toSet() }
            .getOrDefault(BankAppMaster.defaultEnabledPackageNames)

    private fun List<ClubSettingEntity>.toClubSettings(clubId: Long): ClubSettings {
        if (isEmpty()) return ClubSettings.emptyDefaults(clubId)
        val byKey = associate { it.settingKey to it.settingValue }
        return ClubSettings(
            clubId = clubId,
            bankParseEnabledPackagesJson = byKey[ClubSettingKeys.BANK_PARSE_ENABLED_PACKAGES]
                ?: ClubSettingDefaults.defaultEnabledPackagesJson,
            duesPaymentFilterYear = byKey[ClubSettingKeys.DUES_PAYMENT_FILTER_YEAR]?.toIntOrNull(),
            duesPaymentFilterMemberId = byKey[ClubSettingKeys.DUES_PAYMENT_FILTER_MEMBER_ID]?.toLongOrNull(),
            duesPaymentMethod = DuesPaymentMethod.fromStorage(
                byKey[ClubSettingKeys.DUES_PAYMENT_METHOD]
            )
        )
    }
}
