package com.smartexpense.data.repository.club

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppNavigationGuard @Inject constructor() {
    private val _suppressClubListRedirect = MutableStateFlow(false)
    val suppressClubListRedirect: StateFlow<Boolean> = _suppressClubListRedirect.asStateFlow()

    fun setSuppressClubListRedirect(suppress: Boolean) {
        _suppressClubListRedirect.value = suppress
    }
}
