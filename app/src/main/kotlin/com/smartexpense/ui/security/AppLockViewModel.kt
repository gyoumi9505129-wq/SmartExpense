package com.smartexpense.ui.security

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.prefs.AppRestartFlags
import com.smartexpense.data.repository.club.AppNavigationGuard
import com.smartexpense.data.repository.security.AppLockRepository
import com.smartexpense.data.session.AppLockSessionManager
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.session.AuthSessionManager
import com.smartexpense.domain.security.AppLockUnlockMethod
import com.smartexpense.security.BiometricAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 로그인 화면용 PIN/패턴/생체 인증.
 * 등록·변경은 허브 설정에서 계정별로 수행합니다.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockRepository: AppLockRepository,
    private val biometricAuthManager: BiometricAuthManager,
    private val sessionManager: AppLockSessionManager,
    private val authSessionManager: AuthSessionManager,
    private val authRefreshGuard: AuthRefreshGuard,
    private val appNavigationGuard: AppNavigationGuard
) : ViewModel() {

    private val _baseUiState = MutableStateFlow(AppLockUiState())
    /** PIN/패턴으로 매칭된 계정 이메일 (생체는 null → 마지막 저장 계정) */
    private val _matchedLoginEmail = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppLockUiState> = combine(
        _baseUiState,
        sessionManager.isSessionUnlocked
    ) { base, sessionUnlocked ->
        base.copy(isUnlocked = sessionUnlocked)
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppLockUiState()
        )

    private var isAuthenticating = false
    private var authStartedAtMs = 0L
    private var resumeAuthObserver: LifecycleEventObserver? = null

    val hasPinRegistered: StateFlow<Boolean> = appLockRepository.hasPinRegistered
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasPatternRegistered: StateFlow<Boolean> = appLockRepository.hasPatternRegistered
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isBiometricEnabledFlow: StateFlow<Boolean> = appLockRepository.isBiometricEnabled
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            appLockRepository.clearBackgroundTime()
            appLockRepository.clearPendingUnlockSetup()
            AppRestartFlags.consumeRestartState(context)
            resetBiometricSession()
        }
        viewModelScope.launch {
            authSessionManager.sessionCleared.collect {
                sessionManager.clearSession()
                resetBiometricSession()
                updateBaseUiState {
                    it.copy(
                        pinInput = "",
                        patternInput = emptyList(),
                        errorMessage = null,
                        lockEpoch = it.lockEpoch + 1
                    )
                }
            }
        }
        viewModelScope.launch {
            appLockRepository.isBiometricEnabled
                .distinctUntilChanged()
                .collect { enabled ->
                    updateBaseUiState { it.copy(biometricEnabled = enabled) }
                }
        }
    }

    /** 로그인 화면에서 고른 PIN/패턴으로 잠금 해제 UI를 준비합니다. */
    fun prepareLoginUnlock(method: AppLockUnlockMethod) {
        sessionManager.markLocked()
        resetBiometricSession()
        updateBaseUiState {
            it.copy(
                unlockMethod = method,
                biometricEnabled = false,
                pinInput = "",
                patternInput = emptyList(),
                errorMessage = null,
                lockEpoch = it.lockEpoch + 1
            )
        }
    }

    fun prepareBiometricLoginUnlock() {
        sessionManager.markLocked()
        resetBiometricSession()
        viewModelScope.launch {
            val enabled = appLockRepository.isBiometricEnabledOnce()
            updateBaseUiState {
                it.copy(
                    biometricEnabled = enabled,
                    pinInput = "",
                    patternInput = emptyList(),
                    errorMessage = null,
                    lockEpoch = it.lockEpoch + 1
                )
            }
        }
    }

    fun unlock(graceMs: Long = AppLockSessionManager.DEFAULT_UNLOCK_GRACE_MS) {
        authRefreshGuard.onAuthenticatedAgain()
        appNavigationGuard.setSuppressClubListRedirect(false)
        AppRestartFlags.recordAuthentication(context)
        sessionManager.markUnlocked(graceMs)
        resetBiometricSession()
        viewModelScope.launch(Dispatchers.IO) {
            appLockRepository.clearBackgroundTime()
        }
        updateBaseUiState {
            it.copy(
                errorMessage = null,
                pinInput = "",
                patternInput = emptyList()
            )
        }
    }

    fun updatePinInput(value: String) {
        val filtered = value.filter { ch -> ch.isDigit() }.take(PIN_LENGTH)
        val shouldAutoSubmit = filtered.length == PIN_LENGTH &&
            !sessionManager.isSessionUnlocked.value
        updateBaseUiState { it.copy(pinInput = filtered, errorMessage = null) }
        if (shouldAutoSubmit) {
            verifyAndUnlockPin(filtered)
        }
    }

    fun submitPinUnlock() {
        if (sessionManager.isSessionUnlocked.value) return
        verifyAndUnlockPin(_baseUiState.value.pinInput)
    }

    private fun verifyAndUnlockPin(pin: String) {
        if (sessionManager.isSessionUnlocked.value) return
        if (pin.length < PIN_LENGTH) {
            updateBaseUiState { it.copy(errorMessage = "PIN ${PIN_LENGTH}자리를 입력해 주세요.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val matchedEmail = appLockRepository.findEmailByPin(pin)
            withContext(Dispatchers.Main.immediate) {
                if (matchedEmail != null) {
                    _matchedLoginEmail.value = matchedEmail
                    unlock()
                } else {
                    updateBaseUiState {
                        it.copy(errorMessage = "PIN이 올바르지 않습니다.", pinInput = "")
                    }
                }
            }
        }
    }

    /** PIN/패턴으로 찾은 계정 이메일을 꺼내고 초기화합니다. */
    fun consumeMatchedLoginEmail(): String? {
        val email = _matchedLoginEmail.value
        _matchedLoginEmail.value = null
        return email
    }

    fun updatePatternInput(cells: List<Int>) {
        updateBaseUiState { it.copy(patternInput = cells, errorMessage = null) }
    }

    fun submitPatternDrawn(cells: List<Int>) {
        if (sessionManager.isSessionUnlocked.value) return
        if (cells.size < PATTERN_MIN_LENGTH) {
            updateBaseUiState {
                it.copy(patternInput = emptyList(), errorMessage = "패턴이 너무 짧습니다.")
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val matchedEmail = appLockRepository.findEmailByPattern(cells)
            withContext(Dispatchers.Main.immediate) {
                if (matchedEmail != null) {
                    _matchedLoginEmail.value = matchedEmail
                    unlock()
                } else {
                    updateBaseUiState {
                        it.copy(
                            patternInput = emptyList(),
                            errorMessage = "패턴이 올바르지 않습니다."
                        )
                    }
                }
            }
        }
    }

    fun requestBiometric(activity: FragmentActivity) {
        if (sessionManager.isSessionUnlocked.value) return
        if (!_baseUiState.value.biometricEnabled) return
        clearStaleAuthStateIfNeeded()
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            scheduleBiometricWhenResumed(activity)
            return
        }
        if (isAuthenticating) return

        isAuthenticating = true
        authStartedAtMs = System.currentTimeMillis()
        val shown = biometricAuthManager.showBiometricPrompt(
            activity = activity,
            onSuccess = { completeBiometricUnlock() },
            onError = { message -> updateBaseUiState { it.copy(errorMessage = message) } },
            onNegativeButton = { },
            onFinished = { isAuthenticating = false },
            negativeButtonText = "취소"
        )
        if (!shown) {
            isAuthenticating = false
        }
    }

    fun canUseBiometric(): Boolean = biometricAuthManager.canAuthenticateWithBiometric()

    private fun completeBiometricUnlock() {
        if (sessionManager.isSessionUnlocked.value) return
        // 생체는 계정 구분 불가 → 마지막 저장 계정으로 로그인
        _matchedLoginEmail.value = null
        unlock(graceMs = AppLockSessionManager.POST_RESTART_GRACE_MS)
    }

    private fun resetBiometricSession() {
        isAuthenticating = false
        authStartedAtMs = 0L
        biometricAuthManager.resetPromptState()
    }

    private fun clearStaleAuthStateIfNeeded() {
        if (!isAuthenticating) return
        if (System.currentTimeMillis() - authStartedAtMs > AUTH_STALE_MS) {
            resetBiometricSession()
        }
    }

    private fun scheduleBiometricWhenResumed(activity: FragmentActivity) {
        if (sessionManager.isSessionUnlocked.value) return
        resumeAuthObserver?.let { activity.lifecycle.removeObserver(it) }
        lateinit var observer: LifecycleEventObserver
        observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                activity.lifecycle.removeObserver(observer)
                resumeAuthObserver = null
                requestBiometric(activity)
            }
        }
        resumeAuthObserver = observer
        activity.lifecycle.addObserver(observer)
    }

    private inline fun updateBaseUiState(block: (AppLockUiState) -> AppLockUiState) {
        _baseUiState.update(block)
    }

    companion object {
        const val PIN_LENGTH = 4
        const val PATTERN_MIN_LENGTH = 4
        private const val AUTH_STALE_MS = 10_000L
    }
}
