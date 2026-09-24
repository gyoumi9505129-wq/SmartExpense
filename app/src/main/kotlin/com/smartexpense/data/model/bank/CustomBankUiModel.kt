package com.smartexpense.data.model.bank

data class CustomBankUiModel(
    val id: Int,
    val appName: String,
    val packageName: String,
    val isEnabled: Boolean
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isFinanceLikely: Boolean = false
)
