package com.smartexpense.data.firebase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.smartexpense.data.local.prefs.UserPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferences
import com.smartexpense.data.local.prefs.safePreferencesFirst
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 로그인 + 선택 모임 소속(개설자·운영관리자·일반 회원)이면 Firestore 장부를 사용합니다.
 * - 조회: 소속 회원 모두
 * - 쓰기: 화면·Firestore 규칙에서 개설자·운영관리자만 허용
 * 미로그인·모임 미선택이면 로컬(Room)만 사용합니다.
 */
@Singleton
class CloudLedgerModeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    val isEnabled: Flow<Boolean> = dataStore.safePreferences(context).map { prefs ->
        prefs[UserPreferenceKeys.CLOUD_LEDGER_ENABLED] ?: false
    }

    suspend fun isEnabledNow(): Boolean =
        dataStore.safePreferencesFirst(context)[UserPreferenceKeys.CLOUD_LEDGER_ENABLED] ?: false

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.safeEdit(context) { prefs ->
            if (enabled) {
                prefs[UserPreferenceKeys.CLOUD_LEDGER_ENABLED] = true
            } else {
                prefs.remove(UserPreferenceKeys.CLOUD_LEDGER_ENABLED)
            }
        }
    }
}
