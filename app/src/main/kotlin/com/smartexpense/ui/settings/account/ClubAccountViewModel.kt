package com.smartexpense.ui.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.entity.club.ClubAccountEntity
import com.smartexpense.data.repository.club.ClubAccountRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.ui.common.observeIsAdmin
import com.smartexpense.data.firebase.MeetingRoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ClubAccountViewModel @Inject constructor(
    private val clubAccountRepository: ClubAccountRepository,
    private val selectedClubRepository: SelectedClubRepository,
    meetingRoleRepository: MeetingRoleRepository,
    private val ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = observeIsAdmin(meetingRoleRepository)

    private val _uiState = MutableStateFlow(ClubAccountUiState())
    val uiState: StateFlow<ClubAccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectedClubRepository.selectedClubId
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (_uiState.value.isSaving) return@collect
                    _uiState.value = ClubAccountUiState()
                }
        }
        viewModelScope.launch {
            clubAccountRepository.observeAll().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        if (_uiState.value.isSaving) return
        viewModelScope.launch { reloadAccounts() }
    }

    fun openAddDialog() {
        if (!isAdmin.value) return
        _uiState.update {
            it.copy(showAddDialog = true, form = ClubAccountFormState(), saveError = null)
        }
    }

    fun dismissAddDialog() {
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(showAddDialog = false, form = ClubAccountFormState(), saveError = null)
        }
    }

    fun updateBankName(value: String) {
        _uiState.update { it.copy(form = it.form.copy(bankName = value), saveError = null) }
    }

    fun updateAccountNumber(value: String) {
        _uiState.update {
            it.copy(
                form = it.form.copy(accountNumber = value.filter { ch -> ch.isDigit() || ch == '-' }),
                saveError = null
            )
        }
    }

    fun updateHolderName(value: String) {
        _uiState.update { it.copy(form = it.form.copy(holderName = value), saveError = null) }
    }

    fun saveAccount() {
        if (!isAdmin.value) return
        val form = _uiState.value.form
        if (!form.canSave || _uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            val insertResult = runCatching {
                withContext(NonCancellable) {
                    insertAccount(form)
                }
            }
            insertResult.onSuccess {
                withContext(NonCancellable) {
                    reloadAccounts()
                }
                ledgerRefreshNotifier.requestScreenRefresh()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showAddDialog = false,
                        form = ClubAccountFormState(),
                        saveError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = error.message ?: "계좌를 저장하지 못했습니다."
                    )
                }
            }
        }
    }

    fun deleteAccount(account: ClubAccountEntity) {
        if (!isAdmin.value) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                clubAccountRepository.delete(account)
                reloadAccounts()
            }
            ledgerRefreshNotifier.requestScreenRefresh()
        }
    }

    private suspend fun insertAccount(form: ClubAccountFormState): Long {
        return clubAccountRepository.insert(
            ClubAccountEntity(
                clubId = 0,
                bankName = form.bankName.trim(),
                accountNumber = form.accountNumber.trim(),
                holderName = form.holderName.trim()
            )
        )
    }

    private suspend fun reloadAccounts() {
        val accounts = runCatching { clubAccountRepository.getAllOnce() }
            .getOrElse { emptyList() }
        _uiState.update { it.copy(accounts = accounts) }
    }
}
