package com.smartexpense.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 화면 재진입·탭 전환·DB 변경 시 Flow 파이프라인을 재구독하도록 tick을 올립니다.
 */
class ViewModelRefreshTrigger {
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    fun refresh() {
        _tick.update { it + 1 }
    }
}

fun ViewModel.bindScreenRefreshSignals(
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    trigger: ViewModelRefreshTrigger,
    authRefreshGuard: AuthRefreshGuard
) {
    viewModelScope.launch {
        ledgerRefreshNotifier.screenRefreshSignals.collect {
            if (authRefreshGuard.shouldAllowDataRefresh()) {
                trigger.refresh()
            }
        }
    }
}
