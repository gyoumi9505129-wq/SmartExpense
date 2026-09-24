package com.smartexpense.ui.security

import com.smartexpense.domain.security.AppLockUnlockMethod

/**
 * 로그인 화면의 PIN/패턴/생체 언락 UI 상태.
 * (앱 잠금 게이트·최초 등록 플로우는 사용하지 않습니다.)
 */
data class AppLockUiState(
    val isUnlocked: Boolean = false,
    val unlockMethod: AppLockUnlockMethod = AppLockUnlockMethod.PIN,
    val biometricEnabled: Boolean = false,
    val pinInput: String = "",
    val patternInput: List<Int> = emptyList(),
    val errorMessage: String? = null,
    val lockEpoch: Int = 0
)
