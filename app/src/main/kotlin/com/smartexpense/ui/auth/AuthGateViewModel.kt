package com.smartexpense.ui.auth

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.auth.LoginCredentialsStore
import com.smartexpense.data.firebase.CloudLedgerModeRepository
import com.smartexpense.data.firebase.InactiveMemberAccessException
import com.smartexpense.data.firebase.MemberAccessDecision
import com.smartexpense.data.firebase.MemberLoginPolicy
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.data.firebase.PrivilegedAuthConfig
import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.auth.FirebaseAuthSession
import com.smartexpense.data.firebase.auth.toEmailAuthErrorMessage
import com.smartexpense.data.local.prefs.UserPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.seed.SampleClubSeeder
import com.smartexpense.data.repository.club.SelectedClubRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface AuthGateState {
    data object Loading : AuthGateState
    data object NeedsSignIn : AuthGateState
    data class SignedIn(val session: FirebaseAuthSession) : AuthGateState
}

data class EmailAuthUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val meetingRoleRepository: MeetingRoleRepository,
    private val cloudLedgerModeRepository: CloudLedgerModeRepository,
    private val sampleClubSeeder: SampleClubSeeder,
    private val selectedClubRepository: SelectedClubRepository,
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
        viewModelScope.launch {
            runCatching { firebaseAuthRepository.restoreSessionIfNeeded() }
            combine(
                firebaseAuthRepository.authState.catch { emit(null) },
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
                    if (session != null &&
                        !canAccessCloud &&
                        !PrivilegedAuthConfig.isPrivilegedAccount(session.uid, session.email)
                    ) {
                        runCatching {
                            val sampleId = sampleClubSeeder.ensureSampleClubReady()
                            val current = selectedClubRepository.selectedClubId.first()
                            if (current != sampleId) {
                                selectedClubRepository.setSelectedClubId(sampleId)
                            }
                        }
                    }
                    _gateState.value = resolveGateState(session)
                }
        }
    }

    fun dismissError() = _authUiState.update { it.copy(errorMessage = null) }

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
                    throw IllegalStateException(
                        "회원 상태 확인이 지연되고 있습니다. 네트워크 연결 후 다시 시도해 주세요."
                    )
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
                _authUiState.update { it.copy(isSubmitting = false) }
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

    private fun resolveGateState(session: FirebaseAuthSession?): AuthGateState {
        if (session == null) return AuthGateState.NeedsSignIn
        return AuthGateState.SignedIn(session)
    }

    private suspend fun markAuthGateCompleted() {
        dataStore.safeEdit(context) { prefs ->
            prefs[UserPreferenceKeys.AUTH_GATE_COMPLETED] = true
        }
    }
}
