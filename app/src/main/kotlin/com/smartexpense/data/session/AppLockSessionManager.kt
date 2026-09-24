package com.smartexpense.data.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 로그인(PIN/패턴/생체/이메일) 성공 후 세션 잠금 해제 상태.
 * 프로세스가 살아 있는 동안 유지되며, 로그아웃·데이터 초기화 시에만 false로 돌아갑니다.
 */
@Singleton
class AppLockSessionManager @Inject constructor() {

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    fun markUnlocked(graceMs: Long = DEFAULT_UNLOCK_GRACE_MS) {
        _isSessionUnlocked.value = true
    }

    fun markLocked() {
        _isSessionUnlocked.value = false
    }

    fun clearSession() {
        markLocked()
    }

    companion object {
        const val DEFAULT_UNLOCK_GRACE_MS = 15_000L
        const val POST_RESTART_GRACE_MS = 20_000L
    }
}
