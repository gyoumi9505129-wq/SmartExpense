package com.smartexpense.ui.club.dues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.local.model.club.YearlyDuesWithDetails
import com.smartexpense.data.mapper.dues.UnpaidLabelStyle
import com.smartexpense.data.mapper.dues.toMemberDuesSummaries
import com.smartexpense.data.repository.club.DuesDetailPaymentUpdate
import com.smartexpense.data.repository.club.DuesPaymentHistoryUpdate
import com.smartexpense.data.repository.club.DuesRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.repository.club.MemberRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.domain.dues.DuesTermSchedule
import com.smartexpense.domain.dues.isEffectivelyPaid
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import com.smartexpense.ui.common.observeIsAdmin
import com.smartexpense.ui.common.observeSelectedClubChanges
import com.smartexpense.ui.club.components.DATE_DIGIT_LENGTH
import com.smartexpense.ui.club.components.dateDigitsToStorage
import com.smartexpense.ui.club.components.dateStorageToDigits
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DuesStatusViewModel @Inject constructor(
    private val duesRepository: DuesRepository,
    memberRepository: MemberRepository,
    private val selectedClubRepository: SelectedClubRepository,
    private val meetingRoleRepository: MeetingRoleRepository,
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = observeIsAdmin(meetingRoleRepository)

    private val refreshTrigger = ViewModelRefreshTrigger()

    private val selectedYear = MutableStateFlow<Int?>(null)
    private val selectedMember = MutableStateFlow<MemberEntity?>(null)
    private val detailMemberId = MutableStateFlow<Long?>(null)
    private val detailDraftItems = MutableStateFlow<List<MemberDuesDetailItemUi>>(emptyList())
    private val isSavingDetail = MutableStateFlow(false)
    private val editingDetailId = MutableStateFlow<Long?>(null)
    private val koreanNameCollator = Collator.getInstance(Locale.KOREA)

    private val _detailMessage = MutableStateFlow<String?>(null)
    val detailMessage: StateFlow<String?> = _detailMessage
    private val meetingCreatedAt = MutableStateFlow<LocalDate?>(null)

    fun clearDetailMessage() {
        _detailMessage.value = null
    }

    init {
        observeSelectedClubChanges(selectedClubRepository) {
            selectedYear.value = null
            selectedMember.value = null
            detailMemberId.value = null
            detailDraftItems.value = emptyList()
            editingDetailId.value = null
        }
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
        viewModelScope.launch {
            meetingRoleRepository.selectedMeeting.collect { meeting ->
                meetingCreatedAt.value = DuesTermSchedule.effectiveMeetingCreatedAt(
                    meetingName = meeting?.name,
                    createdAtRaw = meeting?.createdAt
                )
            }
        }
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshTrigger.refresh()
        viewModelScope.launch {
            runCatching { duesRepository.repairCorruptedDuesDocuments() }
        }
    }

    val members: StateFlow<List<MemberEntity>> = memberRepository.observeAllMembers()
        .map { list ->
            list.sortedWith(
                compareBy<MemberEntity> {
                    when (it.status) {
                        MemberStatus.ACTIVE -> 0
                        MemberStatus.DORMANT -> 1
                        MemberStatus.WITHDRAWN -> 2
                    }
                }.thenBy(koreanNameCollator) { it.name }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val sheetFilters = combine(
        selectedYear,
        selectedMember,
        detailMemberId,
        detailDraftItems,
        editingDetailId
    ) { year, member, detailId, draftItems, editingId ->
        DetailSheetFilters(year, member, detailId, draftItems, editingId)
    }

    private val loadedDues = combine(
        isSavingDetail,
        members,
        meetingCreatedAt,
        duesRepository.observeYearlyDuesWithDetailsFiltered(year = null, memberId = null),
        duesRepository.observePaymentHistoriesByClub()
    ) { saving, memberList, createdAt, yearlyDues, historiesByDetailId ->
        LoadedDues(saving, memberList, createdAt, yearlyDues, historiesByDetailId)
    }

    val uiState: StateFlow<DuesStatusUiState> = combine(
        refreshTrigger.tick,
        sheetFilters,
        loadedDues
    ) { _, filters, data ->
        val yearlyDues = data.yearlyDues.filter { row ->
            (filters.year == null || row.yearlyDues.year == filters.year) &&
                (filters.member == null || row.yearlyDues.memberId == filters.member.id)
        }
        val memberNameById = data.memberList.associate { it.id to it.name }
        val summaries = yearlyDues.toMemberDuesSummaries(
            memberNameById = memberNameById,
            labelStyle = UnpaidLabelStyle.DESCRIPTIVE,
            meetingCreatedAt = data.createdAt
        )
        val summaryByMemberId = summaries.associateBy { it.memberId }
        val memberRows = data.memberList
            .filter { member ->
                val matchesNameFilter = filters.member == null || member.id == filters.member.id
                if (!matchesNameFilter) return@filter false
                member.status == MemberStatus.ACTIVE || member.id in summaryByMemberId
            }
            .map { member ->
                summaryByMemberId[member.id]?.toStatusRowUi()
                    ?: unregisteredMemberStatusRow(member.id, member.name)
            }
            .sortedWith(
                compareByDescending<MemberDuesStatusRowUi> { it.totalUnpaidAmount }
                    .thenByDescending { it.hasRegisteredDues }
                    .thenBy(koreanNameCollator) { it.memberName }
            )
        val detailRecords = filters.detailMemberId?.let { id ->
            yearlyDues.filter { it.yearlyDues.memberId == id }
        }.orEmpty()
        val baseItems = detailRecords.toDetailItemsUi(data.historiesByDetailId)
        val displayItems = filters.draftItems.ifEmpty { baseItems }
        val detailName = filters.detailMemberId?.let { id ->
            memberNameById[id] ?: memberRows.firstOrNull { it.memberId == id }?.memberName
        }

        DuesStatusUiState(
            selectedYear = filters.year,
            filterMemberId = filters.member?.id,
            totalClubUnpaidAmount = summaries.sumOf { it.totalUnpaidAmount },
            memberRows = memberRows,
            showDetailSheet = filters.detailMemberId != null,
            detailMemberId = filters.detailMemberId,
            detailMemberName = detailName,
            detailItems = baseItems,
            detailDraftItems = displayItems,
            isSavingDetail = data.saving,
            editingDetailId = filters.editingDetailId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DuesStatusUiState()
    )

    private fun canManageDues(): Boolean =
        isAdmin.value || meetingRoleRepository.isSystemAdminSession()

    fun updateSelectedYear(year: Int?) {
        selectedYear.value = year
    }

    fun updateSelectedMember(memberId: Long?) {
        selectedMember.value = memberId?.let { id ->
            members.value.firstOrNull { it.id == id }
        }
    }

    fun openMemberDetail(memberId: Long) {
        detailDraftItems.value = emptyList()
        editingDetailId.value = null
        detailMemberId.value = memberId
    }

    fun dismissMemberDetail() {
        if (isSavingDetail.value) return
        detailMemberId.value = null
        detailDraftItems.value = emptyList()
        editingDetailId.value = null
    }

    fun openDetailEdit(detailId: Long) {
        if (isSavingDetail.value) return
        val currentItems = detailDraftItems.value.ifEmpty { uiState.value.detailItems }
        if (currentItems.none { it.detailId == detailId }) return

        if (detailDraftItems.value.isEmpty() && currentItems.isNotEmpty()) {
            detailDraftItems.value = currentItems
        }

        // 편집 중 비동기 패치가 삭제를 되돌리지 않도록, 이력을 받은 뒤에만 다이얼로그를 연다.
        viewModelScope.launch {
            val histories = runCatching {
                withContext(Dispatchers.IO) {
                    duesRepository.getPaymentHistoriesForDetail(detailId)
                }
            }.getOrDefault(emptyList())

            val latestItems = detailDraftItems.value.ifEmpty { uiState.value.detailItems }
            val patched = latestItems.map { item ->
                if (item.detailId != detailId) return@map item
                val history = histories.map { row ->
                    DuesPaymentEntryUi(
                        id = row.id,
                        payDate = row.payDate,
                        amount = row.amount,
                        linkedTransactionId = row.linkedTransactionId
                    )
                }.ifEmpty {
                    if (item.paidAmount > 0 && item.payDate.isNotBlank()) {
                        listOf(
                            DuesPaymentEntryUi(
                                payDate = item.payDate,
                                amount = item.paidAmount,
                                linkedTransactionId = null
                            )
                        )
                    } else {
                        item.paymentHistory
                    }
                }
                val paidAmount = maxOf(item.paidAmount, history.toAggregatePaidAmount())
                item.copy(
                    paidAmount = paidAmount,
                    payDate = history.toLatestPayDate().ifBlank { item.payDate },
                    isPaid = isEffectivelyPaid(
                        isPaid = item.isPaid,
                        paidAmount = paidAmount,
                        amount = item.amount,
                        isExcluded = item.isExcluded
                    ),
                    paymentHistory = history
                )
            }
            detailDraftItems.value = patched
            editingDetailId.value = detailId
        }
    }

    fun dismissDetailEdit() {
        editingDetailId.value = null
    }

    fun confirmDetailEdit(payments: List<DuesPaymentEntryUi>) {
        if (!canManageDues()) return
        val detailId = editingDetailId.value ?: return
        val normalizedPayments = payments
            .map { entry ->
                entry.copy(
                    payDate = normalizePayDate(entry.payDate),
                    amount = entry.amount.coerceAtLeast(0)
                )
            }
            .filter { it.amount > 0 && it.payDate.isNotBlank() }
            .sortedBy { it.payDate }
        val currentItems = detailDraftItems.value.ifEmpty { uiState.value.detailItems }

        detailDraftItems.value = currentItems.map { item ->
            if (item.detailId != detailId) return@map item
            val paidAmount = normalizedPayments.toAggregatePaidAmount()
            item.copy(
                paidAmount = paidAmount,
                payDate = normalizedPayments.toLatestPayDate(),
                isPaid = isEffectivelyPaid(
                    isPaid = false,
                    paidAmount = paidAmount,
                    amount = item.amount,
                    isExcluded = item.isExcluded
                ),
                paymentHistory = normalizedPayments
            )
        }
        editingDetailId.value = null
    }

    fun saveMemberDetail() {
        if (!canManageDues()) return
        if (isSavingDetail.value) return
        val draft = detailDraftItems.value
        if (draft.isEmpty()) {
            detailMemberId.value = null
            return
        }
        val baseById = uiState.value.detailItems.associateBy { it.detailId }
        val changed = draft.filter { item ->
            val orig = baseById[item.detailId]
            orig == null ||
                orig.paidAmount != item.paidAmount ||
                orig.payDate != item.payDate ||
                orig.isPaid != item.isPaid ||
                orig.paymentHistory != item.paymentHistory
        }
        if (changed.isEmpty()) {
            detailDraftItems.value = emptyList()
            detailMemberId.value = null
            editingDetailId.value = null
            _detailMessage.value = "변경된 납부 내역이 없습니다."
            return
        }

        viewModelScope.launch {
            isSavingDetail.value = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    duesRepository.updateDetailPayments(
                        changed.map { item ->
                            DuesDetailPaymentUpdate(
                                detailId = item.detailId,
                                payDate = item.payDate,
                                paidAmount = item.paidAmount,
                                payments = item.paymentHistory.map { entry ->
                                    DuesPaymentHistoryUpdate(
                                        id = entry.id,
                                        payDate = entry.payDate,
                                        amount = entry.amount,
                                        linkedTransactionId = entry.linkedTransactionId
                                    )
                                }
                            )
                        }
                    )
                }
            }
            isSavingDetail.value = false
            result.onSuccess {
                detailDraftItems.value = emptyList()
                detailMemberId.value = null
                editingDetailId.value = null
                _detailMessage.value = "회비 납부 내역을 저장했습니다."
            }.onFailure { e ->
                _detailMessage.value = "저장에 실패했습니다: ${e.message ?: "알 수 없는 오류"}"
            }
        }
    }

    private fun List<YearlyDuesWithDetails>.toDetailItemsUi(
        historiesByDetailId: Map<Long, List<com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity>>
    ): List<MemberDuesDetailItemUi> =
        flatMap { record ->
            record.details.map { detail ->
                val mappedHistory = historiesByDetailId[detail.id].orEmpty().map { row ->
                    DuesPaymentEntryUi(
                        id = row.id,
                        payDate = row.payDate,
                        amount = row.amount,
                        linkedTransactionId = row.linkedTransactionId
                    )
                }
                val history = mappedHistory.ifEmpty {
                    if (detail.paidAmount > 0 && detail.payDate.isNotBlank()) {
                        listOf(
                            DuesPaymentEntryUi(
                                payDate = detail.payDate,
                                amount = detail.paidAmount
                            )
                        )
                    } else {
                        emptyList()
                    }
                }
                val historyPaid = history.toAggregatePaidAmount()
                // 이력이 비어 있어도 저장된 paidAmount를 0으로 덮지 않음
                val paidAmount = maxOf(detail.paidAmount, historyPaid)
                val payDate = history.toLatestPayDate().ifBlank { detail.payDate }
                MemberDuesDetailItemUi(
                    detailId = detail.id,
                    year = record.yearlyDues.year,
                    termLabel = detail.termLabel,
                    amount = detail.amount,
                    paidAmount = paidAmount,
                    payDate = payDate,
                    isPaid = isEffectivelyPaid(
                        isPaid = detail.isPaid,
                        paidAmount = paidAmount,
                        amount = detail.amount,
                        isExcluded = detail.isExcluded
                    ),
                    isExcluded = detail.isExcluded,
                    paymentHistory = history
                )
            }
        }.sortedWith(
            compareByDescending<MemberDuesDetailItemUi> { it.year }
                .thenBy { it.termLabel }
        )

    private fun normalizePayDate(value: String): String {
        val digits = dateStorageToDigits(value)
        return if (digits.length == DATE_DIGIT_LENGTH) {
            dateDigitsToStorage(digits)
        } else {
            value
        }
    }

    private data class DetailSheetFilters(
        val year: Int?,
        val member: MemberEntity?,
        val detailMemberId: Long?,
        val draftItems: List<MemberDuesDetailItemUi>,
        val editingDetailId: Long?
    )

    private data class LoadedDues(
        val saving: Boolean,
        val memberList: List<MemberEntity>,
        val createdAt: LocalDate?,
        val yearlyDues: List<YearlyDuesWithDetails>,
        val historiesByDetailId: Map<Long, List<com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity>>
    )
}
