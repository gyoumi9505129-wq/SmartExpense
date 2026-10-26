package com.smartexpense.ui.club.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.entity.club.EventExpenseEntity
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.repository.club.EventExpenseRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.ui.club.member.MemberStatusFilter
import com.smartexpense.ui.club.member.matches
import com.smartexpense.ui.club.transaction.toFullDateLabel
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventExpenseViewModel @Inject constructor(
    eventExpenseRepository: EventExpenseRepository,
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard
) : ViewModel() {

    private val refreshTrigger = ViewModelRefreshTrigger()
    private val statusFilter = MutableStateFlow(MemberStatusFilter.ACTIVE)
    private val koreanNameCollator = Collator.getInstance(Locale.KOREA)

    init {
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshTrigger.refresh()
    }

    fun selectStatusFilter(filter: MemberStatusFilter) {
        statusFilter.value = filter
    }

    val uiState: StateFlow<EventExpenseUiState> = refreshTrigger.tick.flatMapLatest {
        combine(
            eventExpenseRepository.observeSummary(),
            eventExpenseRepository.observeTotalAmount(),
            eventExpenseRepository.observeAll(),
            statusFilter
        ) { summaries, totalClubExpense, allRows, filter ->
            val detailsByMemberId = allRows.groupBy { it.memberId }

            val allMemberSummaries = summaries.map { row ->
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

            val filtered = allMemberSummaries
                .filter { filter.matches(it.memberStatus) }
                .sortedWith(memberSummaryComparator(filter))

            EventExpenseUiState(
                totalClubExpense = totalClubExpense,
                memberSummaries = filtered,
                memberCount = allMemberSummaries.size,
                activeCount = allMemberSummaries.count { it.memberStatus == MemberStatus.ACTIVE },
                dormantCount = allMemberSummaries.count { it.memberStatus == MemberStatus.DORMANT },
                withdrawnCount = allMemberSummaries.count {
                    it.memberStatus == MemberStatus.WITHDRAWN
                },
                statusFilter = filter,
                isEmpty = allMemberSummaries.isEmpty()
            )
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EventExpenseUiState()
        )

    /**
     * 전체: 활동중 → 휴면 → 탈퇴, 각 그룹 내 가나다순.
     * 개별 상태 탭: 가나다순.
     */
    private fun memberSummaryComparator(filter: MemberStatusFilter) =
        if (filter == MemberStatusFilter.ALL) {
            compareBy<MemberEventExpenseUi> {
                when (it.memberStatus) {
                    MemberStatus.ACTIVE -> 0
                    MemberStatus.DORMANT -> 1
                    MemberStatus.WITHDRAWN -> 2
                }
            }.thenBy(koreanNameCollator) { it.memberName }
        } else {
            compareBy(koreanNameCollator) { it.memberName }
        }

    private fun EventExpenseEntity.toDetailUi() = EventExpenseDetailUi(
        date = date.toFullDateLabel(),
        eventSubCategory = eventSubCategory.ifBlank { "미분류" },
        amount = amount,
        note = note
    )
}
