package com.smartexpense.ui.auth

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.auth.LoginCredentialsStore
import com.smartexpense.data.auth.SavedLoginAccount
import com.smartexpense.data.firebase.CloudLedgerModeRepository
import com.smartexpense.data.firebase.InactiveMemberAccessException
import com.smartexpense.data.firebase.MemberAccessDecision
import com.smartexpense.data.firebase.MemberLoginPolicy
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.data.firebase.PrivilegedAuthConfig
import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.auth.FirebaseAuthSession
import com.smartexpense.data.firebase.auth.GmailAuthValidator
import com.smartexpense.data.firebase.auth.toEmailAuthErrorMessage
import com.smartexpense.data.firebase.firestore.UserProfileFirestoreRepository
import com.smartexpense.data.local.prefs.UserPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.repository.security.AppLockRepository
import com.smartexpense.data.session.AppLockSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface AuthGateState {
    data object Loading : AuthGateState
    data object NeedsSignIn : AuthGateState
    data class NeedsProfile(val session: FirebaseAuthSession) : AuthGateState
    data class SignedIn(val session: FirebaseAuthSession) : AuthGateState
}

enum class EmailAuthMode {
    LOGIN,
    SIGN_UP
}

/** 로그인 화면에서 고르는 방식 */
enum class LoginEntryMethod {
    EMAIL_PASSWORD,
    PIN,
    PATTERN,
    BIOMETRIC
}

data class EmailAuthUiState(
    val mode: EmailAuthMode = EmailAuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val displayName: String = "",
    val phone: String = "",
    val rememberLogin: Boolean = true,
    val savedAccounts: List<SavedLoginAccount> = emptyList(),
    val entryMethod: LoginEntryMethod = LoginEntryMethod.EMAIL_PASSWORD,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val userProfileFirestoreRepository: UserProfileFirestoreRepository,
    private val meetingRoleRepository: MeetingRoleRepository,
    private val cloudLedgerModeRepository: CloudLedgerModeRepository,
    private val loginCredentialsStore: LoginCredentialsStore,
    private val appLockRepository: AppLockRepository,
    private val appLockSessionManager: AppLockSessionManager,
    private val memberLoginPolicy: MemberLoginPolicy
) : ViewModel() {

    private val _gateState = MutableStateFlow<AuthGateState>(AuthGateState.Loading)
    val gateState: StateFlow<AuthGateState> = _gateState.asStateFlow()

    private val _authUiState = MutableStateFlow(EmailAuthUiState())
    val authUiState: StateFlow<EmailAuthUiState> = _authUiState.asStateFlow()

    init {
        refreshSavedAccountsIntoState()
        viewModelScope.launch {
            runCatching { firebaseAuthRepository.restoreSessionIfNeeded() }
            combine(
                firebaseAuthRepository.authState.catch { emit(null) },
                // 일반 회원도 소속 모임 클라우드 조회 가능. 쓰기는 화면/규칙에서 개설자·지정 운영관리자만 허용.
                meetingRoleRepository.canAccessCloud.catch { emit(false) }
            ) { session, canAccessCloud ->
                session to canAccessCloud
            }
                .distinctUntilChanged()
                .collect { (session, canAccessCloud) ->
                    if (session != null &&
                        !PrivilegedAuthConfig.isPrivilegedAccount(session.uid, session.email)
                    ) {
                        val decision = memberLoginPolicy.evaluateLogin(
                            session = session,
                            failOpenOnError = true
                        )
                        if (decision is MemberAccessDecision.Denied) {
                            runCatching { firebaseAuthRepository.signOut() }
                            cloudLedgerModeRepository.setEnabled(false)
                            _authUiState.update { it.copy(errorMessage = decision.message) }
                            _gateState.value = AuthGateState.NeedsSignIn
                            return@collect
                        }
                    }
                    cloudLedgerModeRepository.setEnabled(session != null && canAccessCloud)
                    _gateState.value = resolveGateState(session)
                }
        }
    }

    fun setMode(mode: EmailAuthMode) {
        val saved = loginCredentialsStore.load()
        val accounts = loginCredentialsStore.loadAccounts()
        _authUiState.update {
            it.copy(
                mode = mode,
                errorMessage = null,
                password = if (mode == EmailAuthMode.LOGIN && saved.remember) saved.password else "",
                passwordConfirm = "",
                email = if (it.email.isBlank() && saved.remember) saved.email else it.email,
                rememberLogin = saved.remember,
                savedAccounts = accounts
            )
        }
    }

    fun updateEmail(value: String) = _authUiState.update { it.copy(email = value, errorMessage = null) }

    fun updatePassword(value: String) = _authUiState.update { it.copy(password = value, errorMessage = null) }

    fun setRememberLogin(remember: Boolean) =
        _authUiState.update { it.copy(rememberLogin = remember) }

    fun selectSavedAccount(email: String) {
        val account = loginCredentialsStore.loadAccounts()
            .firstOrNull { it.email.equals(email, ignoreCase = true) }
            ?: return
        _authUiState.update {
            it.copy(
                email = account.email,
                password = account.password,
                errorMessage = null
            )
        }
    }

    fun removeSavedAccount(email: String) {
        loginCredentialsStore.removeAccount(email)
        refreshSavedAccountsIntoState(keepCurrentFields = true)
    }

    fun updatePasswordConfirm(value: String) =
        _authUiState.update { it.copy(passwordConfirm = value, errorMessage = null) }

    fun updateDisplayName(value: String) =
        _authUiState.update { it.copy(displayName = value, errorMessage = null) }

    fun updatePhone(value: String) = _authUiState.update { it.copy(phone = value, errorMessage = null) }

    fun dismissError() = _authUiState.update { it.copy(errorMessage = null) }

    fun setEntryMethod(method: LoginEntryMethod) {
        _authUiState.update { it.copy(entryMethod = method, errorMessage = null) }
    }

    fun submitAuth() {
        val state = _authUiState.value
        if (state.isSubmitting) return
        when (state.mode) {
            EmailAuthMode.LOGIN -> submitLogin(state)
            EmailAuthMode.SIGN_UP -> _authUiState.update {
                it.copy(errorMessage = "회원가입은 Google 계정으로 본인 확인해야 합니다.")
            }
        }
    }

    fun getGoogleSignInIntent(): Intent = firebaseAuthRepository.createGoogleSignInIntent()

    fun onGoogleSignInCancelled() {
        _authUiState.update {
            it.copy(
                isSubmitting = false,
                errorMessage = "Google 계정 선택이 취소되었습니다."
            )
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        if (_authUiState.value.isSubmitting) return
        viewModelScope.launch {
            _authUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val session = firebaseAuthRepository.signInWithGoogleIntentData(data)
                val loginChecked = withTimeoutOrNull(15_000) {
                    enforceActiveMemberLogin(session)
                    true
                }
                if (loginChecked != true) {
                    throw IllegalStateException("회원 상태 확인이 지연되고 있습니다. 네트워크 연결 후 다시 시도해 주세요.")
                }
                session.email?.let { loginCredentialsStore.markGoogleSignInEmail(it) }
                val syncedName = session.displayName?.trim().orEmpty()
                if (syncedName.isNotBlank()) {
                    session.email?.let { loginCredentialsStore.updateAccountLabel(it, syncedName) }
                }
                markAuthGateCompleted()
                if (appLockRepository.isCredentialConfiguredOnce()) {
                    appLockSessionManager.markUnlocked()
                }
                _gateState.value = resolveGateState(session)
                _authUiState.update { current ->
                    current.copy(
                        isSubmitting = false,
                        email = session.email.orEmpty(),
                        // Google Auth 최신 이름을 우선 반영 (기존 폼 값에 묶이지 않음)
                        displayName = syncedName.ifBlank { current.displayName }
                    )
                }
            } catch (error: Throwable) {
                rejectBlockedSession(error)
                _authUiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.toEmailAuthErrorMessage()
                    )
                }
            }
        }
    }

    /**
     * PIN/패턴/생체 성공 후 저장 계정으로 Firebase 로그인합니다.
     * @param matchedEmail PIN/패턴으로 찾은 계정. null이면 마지막 저장 계정(생체).
     */
    fun signInWithSavedAccountAfterUnlock(
        matchedEmail: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _authUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val accounts = loginCredentialsStore.loadAccounts()
                val last = loginCredentialsStore.load()
                val targetEmail = when {
                    matchedEmail.isNullOrBlank() ||
                        matchedEmail == com.smartexpense.data.repository.security.AppLockRepository.LEGACY_EMAIL ->
                        last.email
                    else -> matchedEmail
                }
                val account = accounts.firstOrNull {
                    it.email.equals(targetEmail, ignoreCase = true)
                } ?: accounts.firstOrNull()
                if (account == null || account.password.isBlank()) {
                    throw IllegalStateException(
                        "저장된 로그인 정보가 없습니다. 이메일·비밀번호로 로그인해 주세요."
                    )
                }
                val session = firebaseAuthRepository.signInWithEmailPassword(
                    account.email,
                    account.password
                )
                enforceActiveMemberLogin(session)
                markAuthGateCompleted()
                appLockSessionManager.markUnlocked()
                _gateState.value = resolveGateState(session)
                _authUiState.update {
                    it.copy(
                        isSubmitting = false,
                        email = account.email,
                        password = account.password,
                        entryMethod = LoginEntryMethod.EMAIL_PASSWORD
                    )
                }
                onResult(true)
            } catch (error: Throwable) {
                rejectBlockedSession(error)
                appLockSessionManager.markLocked()
                _authUiState.update {
                    it.copy(
                        isSubmitting = false,
                        entryMethod = LoginEntryMethod.EMAIL_PASSWORD,
                        errorMessage = error.toEmailAuthErrorMessage()
                    )
                }
                onResult(false)
            }
        }
    }

    fun submitProfileCompletion() {
        val state = _authUiState.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            _authUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                firebaseAuthRepository.saveProfileForCurrentUser(
                    displayName = state.displayName,
                    phone = state.phone
                )
                val session = firebaseAuthRepository.currentSession()
                    ?: throw IllegalStateException("로그인 세션이 없습니다.")
                markAuthGateCompleted()
                _gateState.value = AuthGateState.SignedIn(session)
                _authUiState.update { it.copy(isSubmitting = false) }
            } catch (error: Throwable) {
                _authUiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.toEmailAuthErrorMessage()
                    )
                }
            }
        }
    }

    private fun submitLogin(state: EmailAuthUiState) {
        viewModelScope.launch {
            _authUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val session = firebaseAuthRepository.signInWithEmailPassword(state.email, state.password)
                enforceActiveMemberLogin(session)
                persistLoginCredentials(state)
                markAuthGateCompleted()
                // PIN/패턴이 이미 있으면 이메일 로그인만으로 이번 세션 통과.
                // 아직 없으면 잠금 화면에서 최초 등록을 진행합니다.
                if (appLockRepository.isCredentialConfiguredOnce()) {
                    appLockSessionManager.markUnlocked()
                }
                _gateState.value = resolveGateState(session)
                _authUiState.update { it.copy(isSubmitting = false) }
            } catch (error: Throwable) {
                rejectBlockedSession(error)
                _authUiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = loginErrorMessage(state.email, error)
                    )
                }
            }
        }
    }

    private fun loginErrorMessage(email: String, error: Throwable): String {
        val typed = GmailAuthValidator.normalizeEmail(email)
        val nearestAdmin = PrivilegedAuthConfig.resolvePrivilegedLoginEmail(typed)
        if (nearestAdmin != null && nearestAdmin != typed) {
            return "시스템관리자 이메일은 $nearestAdmin 입니다. 이메일을 다시 확인해 주세요."
        }
        if (loginCredentialsStore.isGoogleSignInEmail(typed) &&
            !PrivilegedAuthConfig.isPrivilegedEmail(typed)
        ) {
            return "이 계정은 Google 로그인으로 연결되어 있습니다. " +
                "Gmail 비밀번호가 아니라 「Google 계정으로 로그인」을 사용해 주세요."
        }
        if (PrivilegedAuthConfig.isPrivilegedEmail(typed)) {
            return "시스템관리자 계정은 앱 비밀번호로 로그인합니다. Gmail 비밀번호는 사용할 수 없습니다. " +
                "또는 「Google 계정으로 로그인」을 한 뒤 다시 시도해 주세요."
        }
        return error.toEmailAuthErrorMessage()
    }

    private suspend fun enforceActiveMemberLogin(session: FirebaseAuthSession) {
        if (PrivilegedAuthConfig.isPrivilegedAccount(session.uid, session.email)) return
        memberLoginPolicy.requireLogin(session)
    }

    private suspend fun rejectBlockedSession(error: Throwable) {
        if (error is InactiveMemberAccessException) {
            runCatching { firebaseAuthRepository.signOut() }
            _gateState.value = AuthGateState.NeedsSignIn
        }
    }

    private suspend fun resolveGateState(session: FirebaseAuthSession?): AuthGateState {
        if (session == null) return AuthGateState.NeedsSignIn
        // 시스템관리자는 프로필 입력 없이 바로 진입
        if (PrivilegedAuthConfig.isPrivilegedEmail(session.email)) {
            return AuthGateState.SignedIn(session)
        }
        val profile = runCatching {
            userProfileFirestoreRepository.getProfile(session.uid)
        }.getOrNull()
        return if (profile?.isComplete() == true) {
            AuthGateState.SignedIn(session)
        } else {
            AuthGateState.NeedsProfile(session)
        }
    }

    private fun persistLoginCredentials(state: EmailAuthUiState) {
        val label = when {
            PrivilegedAuthConfig.isPrivilegedEmail(state.email) ->
                PrivilegedAuthConfig.displayNameFor(state.email)
            else -> ""
        }
        loginCredentialsStore.save(
            email = state.email,
            password = state.password,
            remember = state.rememberLogin,
            label = label
        )
        refreshSavedAccountsIntoState(keepCurrentFields = true)
    }

    private fun refreshSavedAccountsIntoState(keepCurrentFields: Boolean = false) {
        val saved = loginCredentialsStore.load()
        val accounts = loginCredentialsStore.loadAccounts()
        _authUiState.update { current ->
            if (keepCurrentFields) {
                current.copy(
                    rememberLogin = saved.remember,
                    savedAccounts = accounts
                )
            } else {
                current.copy(
                    email = saved.email,
                    password = saved.password,
                    rememberLogin = saved.remember,
                    savedAccounts = accounts
                )
            }
        }
    }

    private suspend fun markAuthGateCompleted() {
        dataStore.safeEdit(context) { prefs ->
            prefs[UserPreferenceKeys.AUTH_GATE_COMPLETED] = true
        }
    }
}
