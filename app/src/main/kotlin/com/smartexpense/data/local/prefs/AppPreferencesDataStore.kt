package com.smartexpense.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "smart_expense_prefs"
)

