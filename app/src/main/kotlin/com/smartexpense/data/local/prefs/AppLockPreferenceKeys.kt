package com.smartexpense.data.local.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object AppLockPreferenceKeys {
    /** 계정(이메일)별 PIN/패턴 JSON. 예: {"a@x.com":{"pinHash":"...","patternHash":"...","method":"pin"}} */
    val CREDENTIALS_BY_EMAIL = stringPreferencesKey("app_lock_credentials_by_email")

    // 레거시(기기 공용) — 마이그레이션 후 제거
    val PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
    val PATTERN_HASH = stringPreferencesKey("app_lock_pattern_hash")
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val UNLOCK_METHOD = stringPreferencesKey("app_lock_unlock_method")
    val PENDING_UNLOCK_SETUP = stringPreferencesKey("app_lock_pending_unlock_setup")
    /** 생체는 기기 공통 (계정 구분 불가) */
    val BIOMETRIC_ENABLED = booleanPreferencesKey("app_lock_biometric_enabled")
    val LOCK_GRACE_PERIOD_MS = longPreferencesKey("app_lock_grace_period_ms")
    val LAST_BACKGROUND_AT_MS = longPreferencesKey("app_lock_last_background_at_ms")
}
