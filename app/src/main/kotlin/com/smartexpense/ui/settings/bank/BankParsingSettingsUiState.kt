package com.smartexpense.ui.settings.bank

import com.smartexpense.data.model.bank.CustomBankUiModel
import com.smartexpense.data.model.bank.InstalledAppInfo

data class BankParsingSettingsUiState(
    val templateEnabled: Map<String, Boolean> = emptyMap(),
    val customBanks: List<CustomBankUiModel> = emptyList(),
    val isNotificationAccessGranted: Boolean = false,
    val showAppPicker: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val filteredApps: List<InstalledAppInfo> = emptyList(),
    val appSearchQuery: String = "",
    val isLoadingApps: Boolean = false,
    val snackbarMessage: String? = null
)
