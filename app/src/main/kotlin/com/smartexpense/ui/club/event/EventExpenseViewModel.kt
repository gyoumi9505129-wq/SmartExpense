package com.smartexpense.ui.club.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.entity.club.EventExpenseEntity
import com.smartexpense.data.repository.club.EventExpenseRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.ui.club.transaction.toFullDateLabel
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventExpenseViewModel @Inject constructor(
    eventExpenseRepository: EventExpenseRepository,
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard
) : ViewModel() {

    private val refreshTrigger = ViewModelRefreshTrigger()

    init {
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshTrigger.refresh()
    }

    val uiState: StateFlow<EventExpenseUiState> = refreshTrigger.tick.flatMapLatest {
        combine(
            eventExpenseRepository.observeSummary(),
            eventExpenseRepository.observeTotalAmount(),
            eventExpenseRepository.observeAll()
        ) { summaries, totalClubExpense, allRows ->
            val detailsByMemberId = allRows.groupBy { it.memberId }

            val memberSummaries = summaries.map { row ->
                val details = detailsByMemberId[row.memberId].orEmpty()
                    .sortedWith(
                        compareByDescending<EventExpenseEntity> { it.date }
                            .thenByDescending { it.id }
                    )
                    .map { it.toDetailUi() }

                MemberEventExpenseUi(
                    memberId = row.memberId,
                    memberName = row.memberName,
                    memberStatus = row.memberStatus,
                    totalAmount = row.totalAmount.toInt(),
                    details = details
                )
            }

            EventExpenseUiState(
                totalClubExpense = totalClubExpense,
                memberSummaries = memberSummaries,
                isEmpty = memberSummaries.isEmpty()
            )
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EventExpenseUiState()
        )

    private fun EventExpenseEntity.toDetailUi() = EventExpenseDetailUi(
        date = date.toFullDateLabel(),
        eventSubCategory = eventSubCategory.ifBlank { "미분류" },
        amount = amount,
        note = note
    )
}
