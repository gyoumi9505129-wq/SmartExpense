package com.smartexpense.data.local.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object UserPreferenceKeys {
    val FIREBASE_UID = stringPreferencesKey("firebase_uid")

    val SELECTED_MEETING_ID = stringPreferencesKey("selected_meeting_id")

    /** 로그인과 별개: true일 때만 Firestore 실시간 장부(회원/거래/회비)를 사용 */
    val CLOUD_LEDGER_ENABLED = booleanPreferencesKey("cloud_ledger_enabled")

    /**
     * 앱 진입 게이트를 한 번이라도 통과했는지(구글 로그인 또는 로컬만 사용).
     * true이면 구글 로그아웃 후에도 로그인 화면으로 돌아가지 않고 로컬 모드로 유지합니다.
     */
    val AUTH_GATE_COMPLETED = booleanPreferencesKey("auth_gate_completed")

    /** 마지막 사용자 활동 시각. 미활동 세션 만료에 사용합니다. */
    val IDLE_LAST_ACTIVITY_AT_MS = longPreferencesKey("idle_last_activity_at_ms")

    /** @deprecated [IDLE_LAST_ACTIVITY_AT_MS] 로 대체. 마이그레이션용으로만 제거합니다. */
    val IDLE_BACKGROUNDED_AT_MS = longPreferencesKey("idle_backgrounded_at_ms")
}
