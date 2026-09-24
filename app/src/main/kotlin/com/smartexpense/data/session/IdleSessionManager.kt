package com.smartexpense.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.local.prefs.UserPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferencesFirst
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 금융 앱형 미활동 세션 관리.
 * - 터치/스크롤 등 인터랙션마다 [IDLE_TIMEOUT_MS] 타이머 리셋
 * - 백그라운드 체류 시간도 미활동으로 합산
 * - 타임아웃 시 60초 연장 안내 후, 미연장 시 자동 로그아웃
 */
@Singleton
class IdleSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val authRepository: FirebaseAuthRepository,
    private val userSessionManager: UserSessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _uiState = MutableStateFlow<IdleSessionUiState>(IdleSessionUiState.Active)
    val uiState: StateFlow<IdleSessionUiState> = _uiState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    @Volatile
    private var lastActivityAtMs: Long = System.currentTimeMillis()

    @Volatile
    private var isForeground: Boolean = true

    @Volatile
    private var sessionWatching: Boolean = false

    /** 연장 직후 짧은 유예 — 포그라운드 복원 레이스로 바로 다시 경고가 뜨는 것을 막는다. */
    @Volatile
    private var extendGraceUntilMs: Long = 0L

    private var monitorJob: Job? = null
    private var warningJob: Job? = null
    private var watchingUid: String? = null

    init {
        scope.launch {
            authRepository.authState.collect { session ->
                if (session == null) {
                    stopWatching()
                }
            }
        }
    }

    /** 로그인 완료 후 호출 — 미활동 감시 시작 (동일 UID면 타이머를 다시 치지 않음) */
    fun onSessionReady() {
        val uid = authRepository.currentSession()?.uid ?: return
        scope.launch {
            mutex.withLock {
                if (sessionWatching && watchingUid == uid) {
                    ensureMonitorLocked()
                    return@withLock
                }
                watchingUid = uid
                lastActivityAtMs = System.currentTimeMillis()
                extendGraceUntilMs = 0L
                sessionWatching = true
                clearPersistedActivity()
                cancelWarningLocked()
                _uiState.value = IdleSessionUiState.Active
                ensureMonitorLocked()
            }
        }
    }

    /** 루트 화면의 모든 포인터 이벤트에서 호출 */
    fun onUserInteraction() {
        if (!sessionWatching) return
        if (_uiState.value is IdleSessionUiState.Warning) return
        lastActivityAtMs = System.currentTimeMillis()
        scope.launch { persistLastActivity(lastActivityAtMs) }
    }

    fun onAppBackground() {
        if (!sessionWatching || authRepository.currentSession() == null) return
        isForeground = false
        scope.launch { persistLastActivity(lastActivityAtMs) }
    }

    fun onAppForeground() {
        isForeground = true
        scope.launch {
            mutex.withLock {
                if (!sessionWatching || authRepository.currentSession() == null) return@withLock
                mergePersistedActivityLocked()
                evaluateIdleLocked()
            }
        }
    }

    fun confirmExtend() {
        // 클릭 즉시 반영 — 코루틴/디스크 레이스로 경고가 재진입하지 않도록 한다.
        val now = System.currentTimeMillis()
        lastActivityAtMs = now
        extendGraceUntilMs = now + EXTEND_GRACE_MS
        scope.launch {
            mutex.withLock {
                lastActivityAtMs = maxOf(lastActivityAtMs, now)
                extendGraceUntilMs = maxOf(extendGraceUntilMs, now + EXTEND_GRACE_MS)
                cancelWarningLocked()
                _uiState.value = IdleSessionUiState.Active
            }
            persistLastActivity(lastActivityAtMs)
        }
    }

    fun logoutNow() {
        scope.launch { performIdleLogout(showNotice = true) }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    private fun ensureMonitorLocked() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                delay(MONITOR_TICK_MS)
                mutex.withLock {
                    if (!sessionWatching) return@withLock
                    if (authRepository.currentSession() == null) {
                        stopWatchingLocked()
                        return@withLock
                    }
                    if (_uiState.value is IdleSessionUiState.Warning) return@withLock
                    if (!isForeground) return@withLock
                    evaluateIdleLocked()
                }
            }
        }
    }

    private suspend fun evaluateIdleLocked() {
        if (!sessionWatching || authRepository.currentSession() == null) return
        if (_uiState.value is IdleSessionUiState.Warning) return
        if (!isForeground) return
        val now = System.currentTimeMillis()
        if (now < extendGraceUntilMs) return
        val elapsed = now - lastActivityAtMs
        if (elapsed >= IDLE_TIMEOUT_MS) {
            startWarningLocked()
        }
    }

    private fun startWarningLocked() {
        if (_uiState.value is IdleSessionUiState.Warning) return
        warningJob?.cancel()
        _uiState.value = IdleSessionUiState.Warning(EXTEND_COUNTDOWN_SECONDS)
        warningJob = scope.launch {
            try {
                for (remaining in EXTEND_COUNTDOWN_SECONDS downTo 0) {
                    if (!isActive) return@launch
                    _uiState.value = IdleSessionUiState.Warning(remaining)
                    if (remaining == 0) break
                    delay(1_000L)
                }
                if (_uiState.value is IdleSessionUiState.Warning) {
                    performIdleLogout(showNotice = true)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // 연장/중지 시 정상 취소
            }
        }
    }

    private fun cancelWarningLocked() {
        warningJob?.cancel()
        warningJob = null
        if (_uiState.value is IdleSessionUiState.Warning) {
            _uiState.value = IdleSessionUiState.Active
        }
    }

    private suspend fun performIdleLogout(showNotice: Boolean) {
        // warningJob 자기 취소로 로그아웃이 끊기지 않도록 보호
        withContext(NonCancellable) {
            mutex.withLock {
                warningJob = null
                sessionWatching = false
                watchingUid = null
                extendGraceUntilMs = 0L
                monitorJob?.cancel()
                monitorJob = null
                _uiState.value = IdleSessionUiState.Active
            }
            clearPersistedActivity()
            userSessionManager.logoutDueToInactivity()
            if (showNotice) {
                _statusMessage.value = AUTO_LOGOUT_MESSAGE
            }
        }
    }

    private suspend fun stopWatching() {
        mutex.withLock { stopWatchingLocked() }
    }

    private fun stopWatchingLocked() {
        sessionWatching = false
        watchingUid = null
        extendGraceUntilMs = 0L
        monitorJob?.cancel()
        monitorJob = null
        cancelWarningLocked()
        scope.launch { clearPersistedActivity() }
    }

    /**
     * 디스크 값이 메모리보다 최신일 때만 반영한다.
     * (연장 직후 오래된 디스크 값으로 덮어써 경고가 반복되던 문제 방지)
     */
    private suspend fun mergePersistedActivityLocked() {
        val persisted = dataStore.safePreferencesFirst(context)[
            UserPreferenceKeys.IDLE_LAST_ACTIVITY_AT_MS
        ] ?: 0L
        if (persisted > lastActivityAtMs) {
            lastActivityAtMs = persisted
        }
    }

    private suspend fun persistLastActivity(atMs: Long) {
        dataStore.safeEdit(context) { prefs ->
            val existing = prefs[UserPreferenceKeys.IDLE_LAST_ACTIVITY_AT_MS] ?: 0L
            // 더 오래된 시각으로 덮어쓰지 않음 (백그라운드/연장 레이스)
            if (atMs >= existing) {
                prefs[UserPreferenceKeys.IDLE_LAST_ACTIVITY_AT_MS] = atMs
            }
            prefs.remove(UserPreferenceKeys.IDLE_BACKGROUNDED_AT_MS)
        }
    }

    private suspend fun clearPersistedActivity() {
        dataStore.safeEdit(context) { prefs ->
            prefs.remove(UserPreferenceKeys.IDLE_LAST_ACTIVITY_AT_MS)
            prefs.remove(UserPreferenceKeys.IDLE_BACKGROUNDED_AT_MS)
        }
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        const val EXTEND_COUNTDOWN_SECONDS = 60
        private const val EXTEND_GRACE_MS = 5_000L
        private const val MONITOR_TICK_MS = 1_000L
        const val AUTO_LOGOUT_MESSAGE = "안전한 이용을 위해 자동 로그아웃 되었습니다."
    }
}

sealed interface IdleSessionUiState {
    data object Active : IdleSessionUiState
    data class Warning(val secondsRemaining: Int) : IdleSessionUiState
}
