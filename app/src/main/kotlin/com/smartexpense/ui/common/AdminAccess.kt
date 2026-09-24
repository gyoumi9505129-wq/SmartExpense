package com.smartexpense.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.firebase.MeetingRoleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * 장부·회원 등 데이터 편집 가능 여부 ([MeetingRoleRepository.canEdit]).
 * 시스템관리자·모임관리자(개설자)·지정 운영관리자이면 true.
 * 승인된 일반 회원은 false(조회만).
 *
 * 함수명 `IsAdmin`은 역사적 이름이며, 시스템관리자 전용이 아닙니다.
 */
fun ViewModel.observeIsAdmin(
    meetingRoleRepository: MeetingRoleRepository
): StateFlow<Boolean> = meetingRoleRepository.canEdit
    .catch { emit(meetingRoleRepository.isSystemAdminSession()) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = meetingRoleRepository.isSystemAdminSession()
    )
