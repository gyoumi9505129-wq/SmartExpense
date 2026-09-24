package com.smartexpense.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.repository.club.SelectedClubRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

fun ViewModel.observeSelectedClubChanges(
    selectedClubRepository: SelectedClubRepository,
    onClubChanged: () -> Unit
) {
    viewModelScope.launch {
        selectedClubRepository.selectedClubId
            .distinctUntilChanged()
            .drop(1) // 초기 값 수집은 무시하고, 실제 모임 전환 시에만 폼/필터 초기화
            .collect { onClubChanged() }
    }
}
