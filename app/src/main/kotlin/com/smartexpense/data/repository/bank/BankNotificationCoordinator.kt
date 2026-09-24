package com.smartexpense.data.repository.bank

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * NotificationListenerService·MainActivity 등 UI 밖에서
 * 장부 등록 폼(회비 자동 매칭) 열기를 요청할 때 사용합니다.
 */
@Singleton
class BankNotificationCoordinator @Inject constructor() {
    private val _openTransactionFormSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openTransactionFormSignal: SharedFlow<Unit> = _openTransactionFormSignal.asSharedFlow()
    private val openRequestPending = AtomicBoolean(false)
    private val openEmptyFormRequestPending = AtomicBoolean(false)

    fun requestOpenTransactionFormFromBankNotification() {
        openEmptyFormRequestPending.set(false)
        openRequestPending.set(true)
        _openTransactionFormSignal.tryEmit(Unit)
    }

    fun requestOpenEmptyTransactionForm() {
        openEmptyFormRequestPending.set(true)
        openRequestPending.set(true)
        _openTransactionFormSignal.tryEmit(Unit)
    }

    /** 동일 요청에 대해 한 번만 true를 반환합니다. */
    fun consumeOpenRequest(): Boolean = openRequestPending.compareAndSet(true, false)

    /** 장부 등록 숏컷 등 — 은행 draft 없이 빈 폼을 열 때 true */
    fun consumeEmptyFormRequest(): Boolean = openEmptyFormRequestPending.compareAndSet(true, false)
}
