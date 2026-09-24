package com.smartexpense.data.local.prefs

import androidx.datastore.preferences.core.stringPreferencesKey

object BankNotificationPreferenceKeys {
    val ENABLED_PACKAGES_JSON = stringPreferencesKey("bank_parse_enabled_packages_json")
}
