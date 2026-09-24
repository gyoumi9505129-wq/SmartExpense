package com.smartexpense.ui.startup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppStartManager @Inject constructor() {
    private val _isBootstrapComplete = MutableStateFlow(false)
    val isBootstrapComplete: StateFlow<Boolean> = _isBootstrapComplete.asStateFlow()

    private val _allowAuthentication = MutableStateFlow(false)
    val allowAuthentication: StateFlow<Boolean> = _allowAuthentication.asStateFlow()

    fun onColdStart() {
        _isBootstrapComplete.value = false
        _allowAuthentication.value = false
    }

    fun markBootstrapComplete() {
        _isBootstrapComplete.value = true
        _allowAuthentication.value = true
    }
}
