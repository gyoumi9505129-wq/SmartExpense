package com.smartexpense.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.bootstrap.DatabaseBootstrap
import com.smartexpense.data.repository.club.AppNavigationGuard
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val clubRepository: ClubRepository,
    private val databaseBootstrap: DatabaseBootstrap,
    selectedClubRepository: SelectedClubRepository,
    appNavigationGuard: AppNavigationGuard
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasLoadedClubSelection = MutableStateFlow(false)
    val hasLoadedClubSelection: StateFlow<Boolean> = _hasLoadedClubSelection.asStateFlow()

    val selectedClubId: StateFlow<Long?> = selectedClubRepository.selectedClubId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val suppressClubListRedirect: StateFlow<Boolean> = appNavigationGuard.suppressClubListRedirect
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                databaseBootstrap.awaitReady()
                selectedClubRepository.selectedClubId.first()
                withContext(Dispatchers.Main.immediate) {
                    _hasLoadedClubSelection.value = true
                }
                clubRepository.reconcileSelectedClub()
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    _isReady.value = true
                    _isLoading.value = false
                }
            }
        }
    }
}
