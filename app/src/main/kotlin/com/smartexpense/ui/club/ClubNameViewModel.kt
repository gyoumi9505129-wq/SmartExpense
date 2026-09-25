package com.smartexpense.ui.club

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.data.repository.club.SelectedClubDisplayNotifier
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.domain.user.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ClubNameViewModel @Inject constructor(
    clubRepository: ClubRepository,
    private val selectedClubRepository: SelectedClubRepository,
    displayNotifier: SelectedClubDisplayNotifier,
    meetingRoleRepository: MeetingRoleRepository
) : ViewModel() {

    val userRole: StateFlow<UserRole> = meetingRoleRepository.currentUserRole
        .catch { emit(UserRole.MEMBER) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserRole.MEMBER
        )

    /** 시스템관리자 전용 — 바텀 네비게이션 「관리」 탭(가입승인·강퇴/삭제) 노출 */
    val canManageAccounts: StateFlow<Boolean> = meetingRoleRepository.isSystemAdmin
        .catch { emit(false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private val pushedSlogan = MutableStateFlow<SelectedClubDisplayNotifier.SloganUpdate?>(null)

    init {
        viewModelScope.launch {
            displayNotifier.sloganUpdates.collect { update ->
                pushedSlogan.value = update
            }
        }
    }

    val clubName: StateFlow<String> = clubRepository.observeSelectedClubName()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "모임"
        )

    val clubSlogan: StateFlow<String> = combine(
        clubRepository.observeSelectedClubSlogan(),
        pushedSlogan,
        selectedClubRepository.selectedClubId
    ) { roomSlogan, pushed, selectedId ->
        if (pushed != null && selectedId == pushed.clubId) {
            pushed.slogan
        } else {
            roomSlogan
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )

    fun leaveCurrentClub(onDone: () -> Unit) {
        viewModelScope.launch {
            selectedClubRepository.clearSelectedClubId()
            onDone()
        }
    }
}
