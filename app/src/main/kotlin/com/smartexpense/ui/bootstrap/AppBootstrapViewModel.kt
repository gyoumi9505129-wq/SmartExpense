package com.smartexpense.ui.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.bootstrap.DatabaseBootstrap
import com.smartexpense.ui.startup.AppStartManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppBootstrapViewModel @Inject constructor(
    private val databaseBootstrap: DatabaseBootstrap,
    appStartManager: AppStartManager
) : ViewModel() {

    val isDatabaseReady: StateFlow<Boolean> = databaseBootstrap.isReady
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val initializationError: StateFlow<String?> = databaseBootstrap.initializationError
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val allowAuthentication: StateFlow<Boolean> = appStartManager.allowAuthentication
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val recoveryNotice: StateFlow<String?> = databaseBootstrap.recoveryNotice
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun dismissRecoveryNotice() {
        databaseBootstrap.consumeRecoveryNotice()
    }

    fun continueAfterInitializationFailure() {
        databaseBootstrap.continueAfterInitializationFailure()
    }

    fun startInitialization() {
        if (isDatabaseReady.value) return
        viewModelScope.launch {
            databaseBootstrap.initialize()
        }
    }
}
