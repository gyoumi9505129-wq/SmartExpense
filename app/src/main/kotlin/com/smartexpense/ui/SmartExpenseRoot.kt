package com.smartexpense.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.smartexpense.ApplicationEntryPoint
import com.smartexpense.data.session.IdleSessionUiState
import com.smartexpense.domain.security.AppLockUnlockMethod
import com.smartexpense.navigation.ClubNavHost
import com.smartexpense.ui.auth.AuthGateState
import com.smartexpense.ui.auth.AuthGateViewModel
import com.smartexpense.ui.auth.EmailAuthScreen
import com.smartexpense.ui.auth.LoginEntryMethod
import com.smartexpense.ui.auth.LoginLockPanelState
import com.smartexpense.ui.auth.LoginQuickUnlockOptions
import com.smartexpense.ui.auth.commitAuthAutofill
import com.smartexpense.ui.bootstrap.AppBootstrapViewModel
import com.smartexpense.ui.common.AppSplashScreen
import com.smartexpense.ui.security.AppLockViewModel
import com.smartexpense.ui.session.SessionExtendDialog
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import dagger.hilt.android.EntryPointAccessors

@Composable
fun SmartExpenseRoot(
    activity: FragmentActivity,
    modifier: Modifier = Modifier,
    onAuthGateResolved: () -> Unit = {},
    appBootstrapViewModel: AppBootstrapViewModel = hiltViewModel(),
    authGateViewModel: AuthGateViewModel = hiltViewModel(activity)
) {
    val isDatabaseReady by appBootstrapViewModel.isDatabaseReady.collectAsStateWithLifecycle()
    val initializationError by appBootstrapViewModel.initializationError.collectAsStateWithLifecycle()
    val allowAuthentication by appBootstrapViewModel.allowAuthentication.collectAsStateWithLifecycle()
    val recoveryNotice by appBootstrapViewModel.recoveryNotice.collectAsStateWithLifecycle()
    val authGateState by authGateViewModel.gateState.collectAsStateWithLifecycle()
    val authUiState by authGateViewModel.authUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var previousAuthGate by remember { mutableStateOf<AuthGateState?>(null) }

    LaunchedEffect(Unit) {
        appBootstrapViewModel.startInitialization()
    }

    // 로그인·회원가입 성공 시 삼성 Pass / Google 비밀번호 저장 안내를 띄웁니다.
    LaunchedEffect(authGateState) {
        val previous = previousAuthGate
        if (previous is AuthGateState.NeedsSignIn &&
            (authGateState is AuthGateState.SignedIn || authGateState is AuthGateState.NeedsProfile)
        ) {
            context.commitAuthAutofill()
        }
        previousAuthGate = authGateState
    }

    val userSessionManager = rememberApplicationUserSession(activity)
    val idleSessionManager = rememberApplicationIdleSession(activity)
    val idleUiState by idleSessionManager.uiState.collectAsStateWithLifecycle()
    val idleStatusMessage by idleSessionManager.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userSessionManager) {
        userSessionManager.logoutCompleted.collect {
            activity.finishAndRemoveTask()
        }
    }

    LaunchedEffect(idleStatusMessage) {
        val message = idleStatusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        idleSessionManager.consumeStatusMessage()
    }

    LaunchedEffect(authGateState, isDatabaseReady, allowAuthentication) {
        if (!isDatabaseReady || !allowAuthentication) return@LaunchedEffect
        when (authGateState) {
            AuthGateState.Loading -> Unit
            AuthGateState.NeedsSignIn,
            is AuthGateState.NeedsProfile,
            is AuthGateState.SignedIn -> onAuthGateResolved()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !isDatabaseReady -> {
                AppSplashScreen(
                    errorMessage = initializationError,
                    onRetry = if (initializationError != null) {
                        appBootstrapViewModel::continueAfterInitializationFailure
                    } else {
                        null
                    },
                    showLoading = initializationError == null
                )
            }

            !allowAuthentication -> {
                AppSplashScreen(showLoading = true)
            }

            else -> {
                recoveryNotice?.let { message ->
                    AlertDialog(
                        onDismissRequest = appBootstrapViewModel::dismissRecoveryNotice,
                        title = {
                            Text(text = "데이터 초기화", color = TextPrimary)
                        },
                        text = {
                            Text(text = message, color = TextSecondary)
                        },
                        confirmButton = {
                            TextButton(onClick = appBootstrapViewModel::dismissRecoveryNotice) {
                                Text(text = "확인", color = TextPrimary)
                            }
                        }
                    )
                }

                when (authGateState) {
                    AuthGateState.Loading -> {
                        AppSplashScreen(showLoading = true)
                    }

                    AuthGateState.NeedsSignIn -> {
                        val appLockViewModel: AppLockViewModel = hiltViewModel(activity)
                        val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
                        val hasPin by appLockViewModel.hasPinRegistered.collectAsStateWithLifecycle()
                        val hasPattern by appLockViewModel.hasPatternRegistered.collectAsStateWithLifecycle()
                        val biometricEnabled by appLockViewModel.isBiometricEnabledFlow.collectAsStateWithLifecycle()
                        val entryMethod = authUiState.entryMethod
                        val awaitingQuickUnlock = entryMethod == LoginEntryMethod.PIN ||
                            entryMethod == LoginEntryMethod.PATTERN ||
                            entryMethod == LoginEntryMethod.BIOMETRIC
                        val googleSignInLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            if (result.data != null) {
                                authGateViewModel.handleGoogleSignInResult(result.data)
                                return@rememberLauncherForActivityResult
                            }
                            if (result.resultCode != Activity.RESULT_OK) {
                                authGateViewModel.onGoogleSignInCancelled()
                            }
                        }

                        LaunchedEffect(awaitingQuickUnlock, appLockUiState.isUnlocked) {
                            if (awaitingQuickUnlock && appLockUiState.isUnlocked) {
                                val matchedEmail = appLockViewModel.consumeMatchedLoginEmail()
                                authGateViewModel.signInWithSavedAccountAfterUnlock(matchedEmail)
                            }
                        }

                        EmailAuthScreen(
                            uiState = authUiState,
                            profileCompletionOnly = false,
                            onEmailChange = authGateViewModel::updateEmail,
                            onPasswordChange = authGateViewModel::updatePassword,
                            onPasswordConfirmChange = authGateViewModel::updatePasswordConfirm,
                            onDisplayNameChange = authGateViewModel::updateDisplayName,
                            onPhoneChange = authGateViewModel::updatePhone,
                            onRememberLoginChange = authGateViewModel::setRememberLogin,
                            onSelectSavedAccount = authGateViewModel::selectSavedAccount,
                            onRemoveSavedAccount = authGateViewModel::removeSavedAccount,
                            quickUnlockOptions = LoginQuickUnlockOptions(
                                hasPin = hasPin,
                                hasPattern = hasPattern,
                                biometricEnabled = biometricEnabled,
                                canUseBiometric = appLockViewModel.canUseBiometric(),
                                hasSavedAccount = authUiState.savedAccounts.any {
                                    it.password.isNotBlank()
                                }
                            ),
                            lockPanel = LoginLockPanelState(
                                pinInput = appLockUiState.pinInput,
                                patternInput = appLockUiState.patternInput,
                                errorMessage = appLockUiState.errorMessage
                            ),
                            onSelectEntryMethod = { method ->
                                authGateViewModel.setEntryMethod(method)
                                when (method) {
                                    LoginEntryMethod.PIN ->
                                        appLockViewModel.prepareLoginUnlock(AppLockUnlockMethod.PIN)
                                    LoginEntryMethod.PATTERN ->
                                        appLockViewModel.prepareLoginUnlock(AppLockUnlockMethod.PATTERN)
                                    LoginEntryMethod.BIOMETRIC ->
                                        appLockViewModel.prepareBiometricLoginUnlock()
                                    LoginEntryMethod.EMAIL_PASSWORD -> Unit
                                }
                            },
                            onPinInputChange = appLockViewModel::updatePinInput,
                            onSubmitPinUnlock = appLockViewModel::submitPinUnlock,
                            onPatternChange = appLockViewModel::updatePatternInput,
                            onPatternComplete = appLockViewModel::submitPatternDrawn,
                            onRequestBiometric = { appLockViewModel.requestBiometric(activity) },
                            onSubmit = authGateViewModel::submitAuth,
                            onGoogleSignIn = {
                                googleSignInLauncher.launch(authGateViewModel.getGoogleSignInIntent())
                            },
                            onSwitchToLogin = {
                                authGateViewModel.setMode(com.smartexpense.ui.auth.EmailAuthMode.LOGIN)
                            },
                            onSwitchToSignUp = {
                                authGateViewModel.setMode(com.smartexpense.ui.auth.EmailAuthMode.SIGN_UP)
                            },
                            onDismissError = authGateViewModel::dismissError,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is AuthGateState.NeedsProfile -> {
                        EmailAuthScreen(
                            uiState = authUiState,
                            profileCompletionOnly = true,
                            onEmailChange = authGateViewModel::updateEmail,
                            onPasswordChange = authGateViewModel::updatePassword,
                            onPasswordConfirmChange = authGateViewModel::updatePasswordConfirm,
                            onDisplayNameChange = authGateViewModel::updateDisplayName,
                            onPhoneChange = authGateViewModel::updatePhone,
                            onSubmit = authGateViewModel::submitProfileCompletion,
                            onSwitchToLogin = {},
                            onSwitchToSignUp = {},
                            onDismissError = authGateViewModel::dismissError,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is AuthGateState.SignedIn -> {
                        val signedInUid = (authGateState as AuthGateState.SignedIn).session.uid
                        LaunchedEffect(signedInUid) {
                            idleSessionManager.onSessionReady()
                        }
                        val navController = rememberNavController()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                            idleSessionManager.onUserInteraction()
                                        }
                                    }
                                }
                        ) {
                            ClubNavHost(navController = navController)

                            val warning = idleUiState as? IdleSessionUiState.Warning
                            if (warning != null) {
                                SessionExtendDialog(
                                    secondsRemaining = warning.secondsRemaining,
                                    onExtend = idleSessionManager::confirmExtend,
                                    onLogout = idleSessionManager::logoutNow
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun rememberApplicationUserSession(activity: Activity) =
    EntryPointAccessors.fromApplication(
        activity.applicationContext,
        ApplicationEntryPoint::class.java
    ).userSessionManager()

@Composable
private fun rememberApplicationIdleSession(activity: Activity) =
    remember(activity.applicationContext) {
        EntryPointAccessors.fromApplication(
            activity.applicationContext,
            ApplicationEntryPoint::class.java
        ).idleSessionManager()
    }
