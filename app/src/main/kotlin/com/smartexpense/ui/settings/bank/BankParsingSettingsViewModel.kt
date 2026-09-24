package com.smartexpense.ui.settings.bank

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.model.bank.InstalledAppInfo
import com.smartexpense.data.repository.bank.BankNotificationSettingsRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.util.InstalledAppsHelper
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import com.smartexpense.util.NotificationAccessHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BankParsingSettingsViewModel @Inject constructor(
    private val bankNotificationSettingsRepository: BankNotificationSettingsRepository,
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val templates = bankNotificationSettingsRepository.templates

    private val refreshTrigger = ViewModelRefreshTrigger()

    private val _uiState = MutableStateFlow(BankParsingSettingsUiState())
    val uiState: StateFlow<BankParsingSettingsUiState> = _uiState.asStateFlow()

    init {
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
        viewModelScope.launch {
            refreshTrigger.tick.flatMapLatest {
                combine(
                    bankNotificationSettingsRepository.templateEnabledStates(),
                    bankNotificationSettingsRepository.customBankStates()
                ) { templateStates, customBanks ->
                    templateStates to customBanks
                }
            }.collect { (templateStates, customBanks) ->
                _uiState.update {
                    it.copy(
                        templateEnabled = templateStates,
                        customBanks = customBanks,
                        isNotificationAccessGranted = NotificationAccessHelper
                            .isNotificationListenerEnabled(appContext)
                    )
                }
            }
        }
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshNotificationAccess()
        refreshTrigger.refresh()
    }

    fun refreshNotificationAccess() {
        _uiState.update {
            it.copy(
                isNotificationAccessGranted = NotificationAccessHelper
                    .isNotificationListenerEnabled(appContext)
            )
        }
    }

    fun openNotificationAccessSettings() {
        NotificationAccessHelper.openNotificationAccessSettings(appContext)
    }

    fun onTemplateToggle(templateId: String, enabled: Boolean) {
        viewModelScope.launch {
            bankNotificationSettingsRepository.setTemplateEnabled(templateId, enabled)
        }
    }

    fun onCustomBankToggle(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            bankNotificationSettingsRepository.setCustomBankEnabled(packageName, enabled)
        }
    }

    fun openAppPicker() {
        if (_uiState.value.isLoadingApps) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val excluded = bankNotificationSettingsRepository.registeredPackageNames().first()
            val apps = withContext(Dispatchers.Default) {
                InstalledAppsHelper.getLaunchableApps(appContext, excluded)
            }
            _uiState.update {
                it.copy(
                    showAppPicker = true,
                    isLoadingApps = false,
                    installedApps = apps,
                    filteredApps = apps,
                    appSearchQuery = ""
                )
            }
        }
    }

    fun dismissAppPicker() {
        _uiState.update {
            it.copy(
                showAppPicker = false,
                installedApps = emptyList(),
                filteredApps = emptyList(),
                appSearchQuery = "",
                isLoadingApps = false
            )
        }
    }

    fun updateAppSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                appSearchQuery = query,
                filteredApps = filterAppsByName(state.installedApps, query)
            )
        }
    }

    fun selectInstalledApp(app: InstalledAppInfo) {
        viewModelScope.launch {
            val added = bankNotificationSettingsRepository.addCustomBank(
                appName = app.appName,
                packageName = app.packageName
            )
            _uiState.update {
                it.copy(
                    snackbarMessage = if (added) {
                        "${app.appName} 앱이 파싱 목록에 추가되었습니다."
                    } else {
                        "이미 등록된 앱입니다."
                    }
                )
            }
        }
    }

    fun removeCustomBank(id: Int) {
        viewModelScope.launch {
            val bank = _uiState.value.customBanks.firstOrNull { it.id == id } ?: return@launch
            bankNotificationSettingsRepository.removeCustomBankById(id)
            _uiState.update {
                it.copy(snackbarMessage = "${bank.appName} 앱을 목록에서 제거했습니다.")
            }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun filterAppsByName(
        apps: List<InstalledAppInfo>,
        query: String
    ): List<InstalledAppInfo> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return apps
        return apps.filter {
            it.appName.contains(trimmed, ignoreCase = true) ||
                it.packageName.contains(trimmed, ignoreCase = true)
        }
    }
}
