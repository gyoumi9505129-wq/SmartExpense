package com.smartexpense.data.repository.club

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferencesFirst
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.smartexpense.data.local.entity.club.ClubSettingEntity
import com.smartexpense.data.local.entity.club.ClubSettingKeys
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.domain.settings.ClubSettings
import com.smartexpense.data.local.prefs.BankNotificationPreferenceKeys
import com.smartexpense.data.model.bank.BankAppMaster
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class ClubSettingsLegacyMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val databaseGateway: ClubDatabaseGateway,
    private val clubSettingsRepository: ClubSettingsRepository,
    private val clubRepository: ClubRepository
) {
    private val clubSettingsDao get() = databaseGateway.clubSettingsDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun migrateIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val clubId = SelectedClubRepository.DEFAULT_CLUB_ID
        if (clubRepository.getClub(clubId) == null) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }
        clubSettingsRepository.ensureSettingsExist(clubId)
        val legacyPackages = readLegacyEnabledPackages()
        val legacyYear = readLegacyDuesFilterYear()
        val legacyMemberId = readLegacyDuesFilterMemberId()

        clubSettingsDao.upsert(
            ClubSettingEntity(
                clubId = clubId,
                settingKey = ClubSettingKeys.BANK_PARSE_ENABLED_PACKAGES,
                settingValue = clubSettingsRepository.encodePackages(legacyPackages)
            )
        )
        legacyYear?.let { year ->
            clubSettingsDao.upsert(
                ClubSettingEntity(
                    clubId = clubId,
                    settingKey = ClubSettingKeys.DUES_PAYMENT_FILTER_YEAR,
                    settingValue = year.toString()
                )
            )
        }
        legacyMemberId?.let { memberId ->
            clubSettingsDao.upsert(
                ClubSettingEntity(
                    clubId = clubId,
                    settingKey = ClubSettingKeys.DUES_PAYMENT_FILTER_MEMBER_ID,
                    settingValue = memberId.toString()
                )
            )
        }

        dataStore.safeEdit(context) { store ->
            store.remove(BankNotificationPreferenceKeys.ENABLED_PACKAGES_JSON)
            store.remove(KEY_DUES_YEAR)
            store.remove(KEY_DUES_MEMBER_ID)
            BankAppMaster.all.forEach { template ->
                store.remove(
                    androidx.datastore.preferences.core.booleanPreferencesKey(
                        "bank_parse_enabled_${template.id}"
                    )
                )
            }
        }

        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private suspend fun readLegacyEnabledPackages(): Set<String> {
        val prefs = dataStore.safePreferencesFirst(context)
        val raw = prefs[BankNotificationPreferenceKeys.ENABLED_PACKAGES_JSON]
        if (!raw.isNullOrBlank()) {
            return runCatching { json.decodeFromString<List<String>>(raw).toSet() }
                .getOrDefault(BankAppMaster.defaultEnabledPackageNames)
        }

        val migrated = BankAppMaster.all.flatMap { template ->
            val legacyKey = androidx.datastore.preferences.core.booleanPreferencesKey(
                "bank_parse_enabled_${template.id}"
            )
            val legacyValue = prefs[legacyKey]
            if (legacyValue == true) template.packageNames else emptySet()
        }.toSet()

        return migrated.ifEmpty { BankAppMaster.defaultEnabledPackageNames }
    }

    private suspend fun readLegacyDuesFilterYear(): Int? =
        dataStore.safePreferencesFirst(context)[KEY_DUES_YEAR]

    private suspend fun readLegacyDuesFilterMemberId(): Long? =
        dataStore.safePreferencesFirst(context)[KEY_DUES_MEMBER_ID]

    private companion object {
        const val PREFS_NAME = "smart_expense_settings_migration"
        const val KEY_MIGRATED = "club_settings_migrated_v19"
        val KEY_DUES_YEAR = intPreferencesKey("dues_payment_filter_year")
        val KEY_DUES_MEMBER_ID = longPreferencesKey("dues_payment_filter_member_id")
    }
}
