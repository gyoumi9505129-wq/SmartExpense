package com.smartexpense.data.repository.bank

import com.smartexpense.data.model.bank.BankNotificationDraft
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class BankNotificationDraftRepository @Inject constructor() {
    private val _pendingDraft = MutableStateFlow<BankNotificationDraft?>(null)
    val pendingDraft: StateFlow<BankNotificationDraft?> = _pendingDraft.asStateFlow()

    fun publish(draft: BankNotificationDraft) {
        _pendingDraft.value = draft
    }

    fun consume(): BankNotificationDraft? {
        val draft = _pendingDraft.value ?: return null
        _pendingDraft.value = null
        return draft
    }

    fun peek(): BankNotificationDraft? = _pendingDraft.value

    fun clear() {
        _pendingDraft.value = null
    }
}
