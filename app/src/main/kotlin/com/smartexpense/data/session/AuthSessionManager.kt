package com.smartexpense.data.session

import android.content.Context
import com.smartexpense.data.local.prefs.AppRestartFlags
import com.smartexpense.data.repository.security.AppLockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 인증 세션(메모리 StateFlow + SharedPreferences 플래그)을 일괄 초기화합니다.
 * 데이터 복원/초기화, 로그아웃, 프로세스 재시작 직전에 반드시 호출합니다.
 */
@Singleton
class AuthSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockSessionManager: AppLockSessionManager,
    private val appLockRepository: AppLockRepository
) {
    private val _sessionCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionCleared: SharedFlow<Unit> = _sessionCleared.asSharedFlow()

    suspend fun clearAuthSession() {
        clearInMemoryAndPersistedFlags()
        appLockRepository.clearBackgroundTime()
        _sessionCleared.tryEmit(Unit)
    }

    fun clearAuthSessionBlocking() {
        runBlocking(Dispatchers.IO) {
            clearAuthSession()
        }
    }

    fun clearInMemoryAndPersistedFlags() {
        appLockSessionManager.clearSession()
        AppRestartFlags.clearAllAuthSession(context)
    }
}
