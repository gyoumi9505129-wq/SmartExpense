package com.smartexpense.data.repository.club

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 장부·회비 등 데이터 변경 및 화면 재진입 시 UI Flow를 즉시 재조회하도록 알립니다.
 */
@Singleton
class LedgerRefreshNotifier @Inject constructor() {
    private val _transactionSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signals: SharedFlow<Unit> = _transactionSignals.asSharedFlow()

    private val _screenRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val screenRefreshSignals: SharedFlow<Unit> = _screenRefresh.asSharedFlow()

    fun notifyTransactionChanged() {
        _transactionSignals.tryEmit(Unit)
        requestScreenRefresh()
    }

    fun notifyDuesChanged() {
        requestScreenRefresh()
    }

    /** 화면 ON_RESUME·탭 전환 등 사용자가 화면을 다시 볼 때 호출 */
    fun requestScreenRefresh() {
        _screenRefresh.tryEmit(Unit)
    }
}
