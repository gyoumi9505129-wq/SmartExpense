package com.smartexpense.data.repository.bank

import com.smartexpense.data.local.entity.club.CustomBankEntity
import com.smartexpense.data.model.bank.BankAppMaster
import com.smartexpense.data.model.bank.BankAppTemplate
import com.smartexpense.data.model.bank.CustomBankUiModel
import com.smartexpense.data.local.bootstrap.DatabaseBootstrap
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.data.repository.club.ClubSettingsRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.repository.club.requireSelectedClubId
import com.smartexpense.data.repository.club.scopedFlowOrDefault
import com.smartexpense.data.repository.club.scopedListFlow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BankNotificationSettingsRepository @Inject constructor(
    private val clubSettingsRepository: ClubSettingsRepository,
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val databaseBootstrap: DatabaseBootstrap
) {
    private val customBankDao get() = databaseGateway.customBankDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enabledPackagesCache = AtomicReference(BankAppMaster.defaultEnabledPackageNames)
    private val customBankLabelsCache = AtomicReference<Map<String, String>>(emptyMap())

    val templates: List<BankAppTemplate> = BankAppMaster.all

    init {
        scope.launch {
            databaseBootstrap.awaitReady()
            databaseGateway.roomFlowTyped {
                selectedClubRepository.selectedClubId.flatMapLatest { clubId ->
                    if (clubId == null) {
                        flowOf(Triple(emptySet<String>(), emptyMap(), emptySet()))
                    } else {
                        combine(
                            clubSettingsRepository.observeSettingsForClub(clubId),
                            customBankDao.observeAll(clubId)
                        ) { settings, customBanks ->
                            val enabled = clubSettingsRepository.decodeEnabledPackages(settings)
                            val labels = customBanks.associate { it.packageName to it.appName }
                            Triple(enabled, labels, BankAppMaster.allPackageNames + customBanks.map { it.packageName })
                        }
                    }
                }
            }.collect { (enabled, labels, _) ->
                enabledPackagesCache.set(enabled)
                customBankLabelsCache.set(labels)
            }
        }
    }

    fun enabledPackagesSnapshot(): Set<String> = enabledPackagesCache.get()

    fun isPackageEnabled(packageName: String): Boolean =
        packageName in enabledPackagesCache.get()

    fun labelForPackage(packageName: String): String =
        customBankLabelsCache.get()[packageName]
            ?: BankAppMaster.labelForPackage(packageName)

    fun enabledPackages(): Flow<Set<String>> =
        selectedClubRepository.scopedFlowOrDefault(databaseGateway, emptySet()) { clubId ->
            clubSettingsRepository.observeSettingsForClub(clubId).map { settings ->
                clubSettingsRepository.decodeEnabledPackages(settings)
            }
        }

    fun templateEnabledStates(): Flow<Map<String, Boolean>> = enabledPackages().map { enabled ->
        templates.associate { template ->
            template.id to template.packageNames.any { it in enabled }
        }
    }

    fun customBankStates(): Flow<List<CustomBankUiModel>> = combine(
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId -> customBankDao.observeAll(clubId) },
        enabledPackages()
    ) { banks, enabled ->
        banks.map { bank ->
            CustomBankUiModel(
                id = bank.id,
                appName = bank.appName,
                packageName = bank.packageName,
                isEnabled = bank.packageName in enabled
            )
        }
    }

    fun registeredPackageNames(): Flow<Set<String>> =
        selectedClubRepository.scopedFlowOrDefault(databaseGateway, emptySet()) { clubId ->
            customBankDao.observeAll(clubId).map { custom ->
                BankAppMaster.allPackageNames + custom.map { it.packageName }
            }
        }

    suspend fun setTemplateEnabled(templateId: String, enabled: Boolean) {
        val template = BankAppMaster.templateById(templateId) ?: return
        setPackagesEnabled(template.packageNames, enabled)
    }

    suspend fun setCustomBankEnabled(packageName: String, enabled: Boolean) {
        setPackagesEnabled(setOf(packageName), enabled)
    }

    suspend fun addCustomBank(appName: String, packageName: String): Boolean {
        val clubId = selectedClubRepository.requireSelectedClubId()
        if (packageName in BankAppMaster.allPackageNames) return false
        if (customBankDao.findByPackage(clubId, packageName) != null) return false

        customBankDao.insert(
            CustomBankEntity(
                clubId = clubId,
                appName = appName,
                packageName = packageName
            )
        )
        setPackagesEnabled(setOf(packageName), enabled = true)
        return true
    }

    suspend fun removeCustomBank(bank: CustomBankEntity) {
        customBankDao.delete(bank)
        setPackagesEnabled(setOf(bank.packageName), enabled = false)
    }

    suspend fun removeCustomBankById(id: Int): Boolean {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val bank = customBankDao.getById(clubId, id) ?: return false
        removeCustomBank(bank)
        return true
    }

    private suspend fun setPackagesEnabled(packages: Set<String>, enabled: Boolean) {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val current = clubSettingsRepository.decodeEnabledPackages(
            clubSettingsRepository.getSettingsOnce(clubId)
        ).toMutableSet()
        if (enabled) {
            current.addAll(packages)
        } else {
            current.removeAll(packages)
        }
        clubSettingsRepository.updateEnabledPackages(clubId, current)
    }
}
