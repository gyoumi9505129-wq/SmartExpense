package com.smartexpense.domain.settings

import com.smartexpense.data.model.bank.BankAppMaster
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ClubSettingDefaults {
    private val json = Json { ignoreUnknownKeys = true }

    val defaultEnabledPackagesJson: String =
        json.encodeToString(BankAppMaster.defaultEnabledPackageNames.sorted())
}
