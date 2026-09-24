package com.smartexpense.data.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 화면 ON_RESUME 새로고침·인증 리다이렉트 전에 로그인 세션 상태를 확인합니다.
 * 명시적 로그아웃 직후에는 데이터 로드와 자동 인증 팝업을 차단합니다.
 */
@Singleton
class AuthRefreshGuard @Inject constructor(
    private val appLockSessionManager: AppLockSessionManager
) {
    private val _suppressAuthRedirects = MutableStateFlow(false)
    val suppressAuthRedirects: StateFlow<Boolean> = _suppressAuthRedirects.asStateFlow()

    fun shouldAllowDataRefresh(): Boolean {
        if (_suppressAuthRedirects.value) return false
        return appLockSessionManager.isSessionUnlocked.value
    }

    fun shouldAllowAuthRedirect(): Boolean = !_suppressAuthRedirects.value

    fun onExplicitLogoutStarted() {
        _suppressAuthRedirects.value = true
    }

    fun onExplicitLogoutCancelled() {
        _suppressAuthRedirects.value = false
    }

    fun onAuthenticatedAgain() {
        _suppressAuthRedirects.value = false
    }
}
