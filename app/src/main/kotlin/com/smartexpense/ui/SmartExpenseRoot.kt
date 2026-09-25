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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.smartexpense.ApplicationEntryPoint
import com.smartexpense.data.session.IdleSessionUiState
import com.smartexpense.navigation.ClubNavHost
import com.smartexpense.ui.auth.AuthGateState
import com.smartexpense.ui.auth.AuthGateViewModel
import com.smartexpense.ui.auth.EmailAuthScreen
import com.smartexpense.ui.bootstrap.AppBootstrapViewModel
import com.smartexpense.ui.common.AppSplashScreen
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

    LaunchedEffect(Unit) {
        appBootstrapViewModel.startInitialization()
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

                        EmailAuthScreen(
                            uiState = authUiState,
                            onGoogleSignIn = {
                                googleSignInLauncher.launch(authGateViewModel.getGoogleSignInIntent())
                            },
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
