package com.smartexpense.ui.club.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.entity.club.ClubHistoryEntity
import com.smartexpense.data.repository.club.ClubHistoryRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.ui.common.observeIsAdmin
import com.smartexpense.ui.common.observeSelectedClubChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClubHistoryViewModel @Inject constructor(
    private val clubHistoryRepository: ClubHistoryRepository,
    private val selectedClubRepository: SelectedClubRepository,
    meetingRoleRepository: MeetingRoleRepository,
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = observeIsAdmin(meetingRoleRepository)

    private val refreshTrigger = ViewModelRefreshTrigger()

    private val _uiState = MutableStateFlow(ClubHistoryUiState())
    val uiState: StateFlow<ClubHistoryUiState> = _uiState.asStateFlow()

    init {
        observeSelectedClubChanges(selectedClubRepository) {
            _uiState.value = ClubHistoryUiState()
        }
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
        viewModelScope.launch {
            refreshTrigger.tick.flatMapLatest {
                clubHistoryRepository.observeAllAsc()
            }.collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshTrigger.refresh()
    }

    fun openCreateForm() {
        if (!isAdmin.value) return
        _uiState.update {
            it.copy(
                isFormVisible = true,
                form = ClubHistoryFormState()
            )
        }
    }

    fun openEditForm(item: ClubHistoryEntity) {
        if (!isAdmin.value) return
        _uiState.update {
            it.copy(
                isFormVisible = true,
                form = ClubHistoryFormState(
                    editingId = item.id,
                    date = clubHistoryEpochMillisToDate(item.date),
                    content = item.content,
                    details = item.details,
                    note = item.note.orEmpty()
                )
            )
        }
    }

    fun dismissForm() {
        _uiState.update {
            it.copy(isFormVisible = false, form = ClubHistoryFormState())
        }
    }

    fun updateDate(value: String) {
        _uiState.update { it.copy(form = it.form.copy(date = value)) }
    }

    fun updateContent(value: String) {
        _uiState.update { it.copy(form = it.form.copy(content = value)) }
    }

    fun updateDetails(value: String) {
        _uiState.update { it.copy(form = it.form.copy(details = value)) }
    }

    fun updateNote(value: String) {
        _uiState.update { it.copy(form = it.form.copy(note = value)) }
    }

    fun saveForm() {
        if (!isAdmin.value) return
        val form = _uiState.value.form
        if (!form.canSave || _uiState.value.isSaving) return

        val epochMillis = clubHistoryDateToEpochMillis(form.date) ?: return
        val entity = ClubHistoryEntity(
            id = form.editingId ?: 0,
            clubId = 0,
            date = epochMillis,
            content = form.content.trim(),
            details = form.details.trim(),
            note = form.note.trim().ifBlank { null }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            if (form.isEditing) {
                clubHistoryRepository.update(entity)
            } else {
                clubHistoryRepository.insert(entity)
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isFormVisible = false,
                    form = ClubHistoryFormState()
                )
            }
        }
    }

    fun requestDelete(item: ClubHistoryEntity) {
        if (!isAdmin.value) return
        _uiState.update { it.copy(deleteTarget = item) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun confirmDelete() {
        if (!isAdmin.value) return
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            clubHistoryRepository.delete(target)
            _uiState.update { it.copy(deleteTarget = null) }
        }
    }
}
