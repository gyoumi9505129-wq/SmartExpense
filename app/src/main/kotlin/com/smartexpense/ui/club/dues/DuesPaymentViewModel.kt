package com.smartexpense.ui.club.dues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.local.entity.club.YearlyDuesEntity
import com.smartexpense.data.local.model.club.YearlyDuesWithDetails
import com.smartexpense.data.mapper.dues.MemberDuesSummary
import com.smartexpense.data.mapper.dues.toMemberDuesSummaries
import com.smartexpense.data.local.prefs.DuesPaymentFilterPreferences
import com.smartexpense.data.repository.club.DuesRepository
import com.smartexpense.data.repository.club.ClubSettingsRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.repository.club.MemberRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.domain.dues.DuesTermSchedule
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import com.smartexpense.ui.common.observeSelectedClubChanges
import com.smartexpense.ui.common.observeIsAdmin
import com.smartexpense.ui.club.components.dateStorageToDigits
import com.smartexpense.ui.club.toDuesMemberOption
import com.smartexpense.ui.club.toEntityForSave
import com.smartexpense.ui.club.toFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DuesPaymentViewModel @Inject constructor(
    private val duesRepository: DuesRepository,
    private val memberRepository: MemberRepository,
    private val filterPreferences: DuesPaymentFilterPreferences,
    private val selectedClubRepository: SelectedClubRepository,
    private val clubSettingsRepository: ClubSettingsRepository,
    private val meetingRoleRepository: MeetingRoleRepository,
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = observeIsAdmin(meetingRoleRepository)

    private val refreshTrigger = ViewModelRefreshTrigger()

    private val defaultPaymentMethod = MutableStateFlow(DuesPaymentMethod.DEFAULT)
    private val meetingCreatedAt = MutableStateFlow<LocalDate?>(null)
    private var cachedMemberJoinDate: String? = null
    private val isFormVisible = MutableStateFlow(false)
    private val form = MutableStateFlow(emptyFormState())
    private val isSaving = MutableStateFlow(false)
    private val isEditMode = MutableStateFlow(false)
    private val deleteTargetId = MutableStateFlow<Long?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val selectedYear = MutableStateFlow<Int?>(null)
    private val selectedMemberId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            filterPreferences.filters.collect { saved ->
                selectedYear.value = saved.year
                selectedMemberId.value = saved.memberId
            }
        }
        viewModelScope.launch {
            clubSettingsRepository.observeSettings().collect { settings ->
                defaultPaymentMethod.value = settings.duesPaymentMethod
            }
        }
        viewModelScope.launch {
            meetingRoleRepository.selectedMeeting.collect { meeting ->
                meetingCreatedAt.value = DuesTermSchedule.effectiveMeetingCreatedAt(
                    meetingName = meeting?.name,
                    createdAtRaw = meeting?.createdAt
                )
            }
        }
        observeSelectedClubChanges(selectedClubRepository) {
            isFormVisible.value = false
            form.value = emptyFormState()
            deleteTargetId.value = null
            isEditMode.value = false
            cachedMemberJoinDate = null
        }
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshTrigger.refresh()
    }

    private val effectiveFilters = combine(selectedYear, selectedMemberId) { year, memberId ->
        DuesPaymentFilterPreferences.Filters(year = year, memberId = memberId)
    }

    private val yearlyDuesList = effectiveFilters.flatMapLatest { f ->
        duesRepository.observeYearlyDuesWithDetailsFiltered(year = f.year, memberId = f.memberId)
    }

    private val membersAndDues = combine(
        memberRepository.observeAllMembers(),
        yearlyDuesList,
        meetingCreatedAt
    ) { members, dues, createdAt ->
        Triple(members, dues, createdAt)
    }

    private val baseUi = combine(
        membersAndDues,
        isFormVisible,
        form,
        effectiveFilters
    ) { (allMembers, yearlyDues, createdAt), visible, formState, f ->
        val memberOptions = allMembers
            .filter { it.status == MemberStatus.ACTIVE || it.id == formState.memberId }
            .map { it.toDuesMemberOption() }
        val memberNameById = allMembers.associate { it.id to it.name }
        DuesPaymentUiState(
            memberOptions = memberOptions,
            memberSummaries = yearlyDues.toMemberDuesSummaries(
                memberNameById = memberNameById,
                meetingCreatedAt = createdAt
            ),
            isFormVisible = visible,
            form = formState,
            selectedYear = f.year,
            selectedMemberId = f.memberId
        )
    }

    val uiState: StateFlow<DuesPaymentUiState> = combine(
        refreshTrigger.tick,
        baseUi,
        isEditMode,
        isSaving,
        combine(deleteTargetId, errorMessage) { deleteId, error -> deleteId to error }
    ) { _, base, editMode, saving, extra ->
        base.copy(
            isEditMode = editMode,
            isSaving = saving,
            deleteTargetId = extra.first,
            errorMessage = extra.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DuesPaymentUiState(form = emptyFormState())
    )

    private fun canManageDues(): Boolean =
        isAdmin.value || meetingRoleRepository.isSystemAdminSession()

    fun openForm() {
        openFormInternal(preselectedMemberId = null)
    }

    fun openFormForMember(memberId: Long) {
        openFormInternal(preselectedMemberId = memberId)
    }

    private fun openFormInternal(preselectedMemberId: Long?) {
        if (canManageDues()) {
            presentNewPaymentForm(preselectedMemberId)
            return
        }
        viewModelScope.launch {
            val canEdit = runCatching { meetingRoleRepository.canEditNow() }.getOrDefault(false)
            if (!canEdit) {
                errorMessage.value = "모임 관리자만 회비를 등록할 수 있습니다."
                return@launch
            }
            presentNewPaymentForm(preselectedMemberId)
        }
    }

    private fun presentNewPaymentForm(preselectedMemberId: Long?) {
        isEditMode.value = false
        form.value = emptyFormState().copy(memberId = preselectedMemberId)
        isFormVisible.value = true
        if (preselectedMemberId == null) {
            cachedMemberJoinDate = null
            return
        }
        viewModelScope.launch {
            val member = runCatching {
                memberRepository.observeAllMembers().first()
                    .find { it.id == preselectedMemberId }
            }.getOrNull()
            cachedMemberJoinDate = member?.joinDate
            loadExistingOrBlankForm(
                memberId = preselectedMemberId,
                year = form.value.year,
                joinDate = member?.joinDate
            )
        }
    }

    fun loadYearlyDues(yearlyDuesId: Long) {
        viewModelScope.launch {
            if (!canManageDues() &&
                !runCatching { meetingRoleRepository.canEditNow() }.getOrDefault(false)
            ) {
                errorMessage.value = "모임 관리자만 회비를 수정할 수 있습니다."
                return@launch
            }
            val data = duesRepository.getYearlyDuesWithDetails(yearlyDuesId) ?: return@launch
            isEditMode.value = true
            form.value = buildFormStateFromSaved(data)
            isFormVisible.value = true
        }
    }

    fun openMemberSummary(summary: MemberDuesSummary) {
        loadYearlyDues(summary.defaultYearlyDuesId)
    }

    fun dismissForm() {
        isFormVisible.value = false
        isEditMode.value = false
        form.value = emptyFormState()
    }

    fun updateMemberId(id: Long) {
        // 드롭다운 선택이 즉시 보이도록 먼저 반영 (비동기 조회 전에 UI가 초기화되면 안 됨)
        updateForm { copy(memberId = id) }
        viewModelScope.launch {
            val member = runCatching {
                memberRepository.observeAllMembers().first().find { it.id == id }
            }.getOrNull()
            cachedMemberJoinDate = member?.joinDate
            loadExistingOrBlankForm(
                memberId = id,
                year = form.value.year,
                joinDate = member?.joinDate
            )
        }
    }

    fun updateYear(year: Int) {
        updateForm { copy(year = year) }
        viewModelScope.launch {
            val memberId = form.value.memberId
            if (memberId != null) {
                loadExistingOrBlankForm(
                    memberId = memberId,
                    year = year,
                    joinDate = cachedMemberJoinDate
                )
            } else {
                applyBlankYearChange(year)
            }
        }
    }

    /**
     * 회원·연도 조합에 기존 회비가 있으면 미납/부분납 상태를 폼에 로드하고,
     * 없으면 신규 등록 폼으로 전환한다.
     * 조회 실패 시에도 선택한 회원·연도는 유지한다.
     */
    private suspend fun loadExistingOrBlankForm(
        memberId: Long,
        year: Int,
        joinDate: String?
    ) {
        try {
            val existing = duesRepository.getYearlyDuesByMemberAndYear(
                memberId = memberId,
                year = year
            )
            if (existing != null) {
                isEditMode.value = true
                form.value = buildFormStateFromSaved(existing)
                return
            }
            isEditMode.value = false
            val blank = emptyFormState().copy(
                memberId = memberId,
                year = year,
                currentDuesId = null
            )
            form.value = blank.copy(
                details = blank.details.map { item ->
                    item.withAutoExclusion(year, blank.paymentMethod, joinDate)
                }
            )
        } catch (e: Exception) {
            // 네트워크/매핑 오류가 나도 회원 선택은 유지
            isEditMode.value = false
            updateForm {
                copy(
                    memberId = memberId,
                    year = year,
                    currentDuesId = null
                )
            }
            errorMessage.value =
                e.message?.takeIf { it.isNotBlank() }
                    ?: "기존 회비 정보를 불러오지 못했습니다. 신규로 입력해 주세요."
        }
    }

    private fun applyBlankYearChange(year: Int) {
        updateForm {
            val next = copy(year = year, currentDuesId = null)
            next.copy(
                details = next.details.map { item ->
                    val replaced = if (item.payDate.isNotBlank() && !item.isExcluded) {
                        replaceYearKeepingMonthDay(item.payDate, year)
                    } else if (item.isExcluded) {
                        ""
                    } else {
                        defaultPayDateDigits(next.paymentMethod, item.termLabel, year)
                    }
                    item.copy(payDate = replaced, paidAmount = 0L, isPaid = false)
                }.applyMeetingStartExclusion(year, next.paymentMethod)
            )
        }
        isEditMode.value = false
    }

    fun updateTotalTargetAmount(value: String) = updateForm {
        val digits = value.filter { it.isDigit() }
        copy(totalTargetAmount = digits).recalculateAutoDistributedAmounts()
    }

    fun updatePaymentMethod(method: DuesPaymentMethod) = updateForm {
        val regenerated = when (method) {
            DuesPaymentMethod.MONTHLY -> defaultMonthlyDetails(year)
            else -> defaultDetails(method, year)
        }.applyAutoDistributionFrom(totalTargetAmount)
            .applyMeetingStartExclusion(year, method)
        copy(paymentMethod = method, details = regenerated)
    }

    fun updateDetailAmount(localKey: String, value: String) = updateForm {
        val digits = value.filter { it.isDigit() }
        copy(
            details = details.map {
                if (it.localKey == localKey) it.copy(amount = digits, isCustomAmount = true) else it
            }
        )
    }

    fun updateDetailAdditionalPayAmount(localKey: String, value: String) = updateForm {
        val digits = value.filter { it.isDigit() }
        copy(
            details = details.map { item ->
                if (item.localKey != localKey) return@map item
                val remaining = item.remainingAmount.toLong().coerceAtLeast(0L)
                val entered = digits.toLongOrNull()
                val capped = when {
                    entered == null -> digits
                    remaining > 0L && entered > remaining -> remaining.toString()
                    else -> digits
                }
                item.copy(
                    additionalPayAmount = capped,
                    isPaid = false
                )
            }
        )
    }

    fun updateDetailPayDate(localKey: String, value: String) = updateForm {
        copy(
            details = details.map {
                if (it.localKey == localKey) it.copy(payDate = normalizePayDate(value)) else it
            }
        )
    }

    fun toggleDetailPaid(localKey: String, isPaid: Boolean) = updateForm {
        val today = LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        copy(
            details = details.map { item ->
                if (item.localKey != localKey) return@map item
                val target = item.targetAmount.toLong()
                if (isPaid) {
                    // 부분납이면 잔여만 채워 완납. 장부에는 증액분만 기장된다.
                    val nextPaid = target.coerceAtLeast(item.paidAmount)
                    item.copy(
                        isPaid = nextPaid > 0L && nextPaid >= target && target > 0L,
                        isExcluded = false,
                        payDate = when {
                            item.payDate.isBlank() -> today
                            item.isPartialPaid -> today
                            else -> item.payDate
                        },
                        paidAmount = nextPaid,
                        additionalPayAmount = ""
                    )
                } else {
                    val restored = item.initialPaidAmount.coerceIn(0L, target.coerceAtLeast(0L))
                    item.copy(
                        isPaid = restored > 0L && restored >= target && target > 0L,
                        payDate = if (restored > 0L) item.payDate else "",
                        paidAmount = restored,
                        additionalPayAmount = if (restored < target && target > 0L) {
                            (target - restored).toString()
                        } else {
                            ""
                        }
                    )
                }
            }
        )
    }

    fun toggleDetailExcluded(localKey: String, isExcluded: Boolean) = updateForm {
        copy(
            details = details.map { item ->
                if (item.localKey != localKey) return@map item
                item.copy(
                    isExcluded = isExcluded,
                    isPaid = if (isExcluded) false else item.isPaid,
                    payDate = if (isExcluded) "" else item.payDate
                )
            }
        )
    }

    fun addMonthlyDetail() {
        val formState = form.value
        if (formState.paymentMethod != DuesPaymentMethod.MONTHLY) return
        val usedMonths = formState.details.mapNotNull { it.monthNumber }.toSet()
        val nextMonth = (1..12).firstOrNull { it !in usedMonths }
        if (nextMonth == null) {
            errorMessage.value = "월별 납부는 최대 12개월까지 추가할 수 있습니다."
            return
        }
        updateForm {
            val newItem = DuesDetailItemState(
                termLabel = "${nextMonth}월",
                payDate = defaultPayDateDigits(DuesPaymentMethod.MONTHLY, "${nextMonth}월", year)
            ).withAutoExclusion(year, DuesPaymentMethod.MONTHLY)
            copy(details = details + newItem).recalculateAutoDistributedAmounts()
        }
    }

    fun removeMonthlyDetail(localKey: String) = updateForm {
        if (paymentMethod != DuesPaymentMethod.MONTHLY) return@updateForm this
        copy(details = details.filter { it.localKey != localKey }).recalculateAutoDistributedAmounts()
    }

    fun updateMonthlyMonth(localKey: String, month: Int) = updateForm {
        if (paymentMethod != DuesPaymentMethod.MONTHLY) return@updateForm this
        val label = "${month}월"
        copy(
            details = details.map { item ->
                if (item.localKey != localKey) item
                else item.copy(
                    termLabel = label,
                    payDate = if (item.isExcluded || item.payDate.isBlank()) {
                        if (item.isExcluded) "" else defaultPayDateDigits(
                            DuesPaymentMethod.MONTHLY,
                            label,
                            year
                        )
                    } else {
                        replaceMonthInPayDate(item.payDate, month, year)
                    }
                ).withAutoExclusion(year, DuesPaymentMethod.MONTHLY)
            }
        )
    }

    fun updateSelectedYear(year: Int?) {
        selectedYear.value = year
        viewModelScope.launch { filterPreferences.setYear(year) }
    }

    fun updateSelectedMember(memberId: Long?) {
        selectedMemberId.value = memberId
        viewModelScope.launch { filterPreferences.setMemberId(memberId) }
    }

    fun saveYearlyDues() {
        if (!canManageDues()) {
            errorMessage.value = "모임 관리자만 회비를 저장할 수 있습니다."
            return
        }
        val formState = form.value
        validateForm(formState)?.let { message ->
            errorMessage.value = message
            return
        }

        viewModelScope.launch {
            isSaving.value = true
            try {
                if (persistYearlyDues(formState)) {
                    resetFormAfterSave(savedYear = formState.year)
                }
            } catch (e: Exception) {
                errorMessage.value = e.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "회비를 저장하지 못했습니다. 다시 시도해 주세요."
            } finally {
                isSaving.value = false
            }
        }
    }

    /** @see saveYearlyDues */
    fun savePayment() = saveYearlyDues()

    /** @return ?? ?? ?? */
    private suspend fun persistYearlyDues(formState: DuesPaymentFormState): Boolean {
        val memberId = formState.memberId ?: return false
        val existing = duesRepository.getYearlyDuesByMemberAndYear(
            memberId = memberId,
            year = formState.year
        )

        if (isEditMode.value &&
            existing != null &&
            existing.yearlyDues.id != formState.currentDuesId
        ) {
            errorMessage.value = "이미 ${formState.year}년 회비가 다른 건으로 등록되어 있습니다."
            return false
        }

        val yearlyDuesId = when {
            isEditMode.value -> formState.currentDuesId
            existing != null -> existing.yearlyDues.id
            else -> null
        }
        val detailsForSave = if (existing != null && !isEditMode.value) {
            mergeIncomingOntoExisting(formState, existing)
        } else {
            mapDetailsForSave(formState.details)
        }

        val parent = YearlyDuesEntity(
            id = yearlyDuesId ?: 0,
            clubId = 0,
            memberId = memberId,
            year = formState.year,
            totalTargetAmount = formState.totalTargetAmount.toIntOrNull() ?: 0,
            paymentMethod = formState.paymentMethod
        )
        val detailEntities = detailsForSave.map {
            it.toEntityForSave(yearlyDuesId = parent.id)
        }

        if (yearlyDuesId != null) {
            duesRepository.updateYearlyDuesWithDetails(
                yearlyDues = parent.copy(id = yearlyDuesId),
                details = detailEntities.map { it.copy(yearlyDuesId = yearlyDuesId) }
            )
        } else {
            val newId = duesRepository.insertYearlyDuesWithDetails(
                yearlyDues = parent,
                details = detailEntities
            )
            if (newId <= 0L) {
                throw IllegalStateException("회비 저장에 실패했습니다.")
            }
        }
        return true
    }

    private fun mergeIncomingOntoExisting(
        formState: DuesPaymentFormState,
        existing: YearlyDuesWithDetails
    ): List<DuesDetailItemState> {
        val incomingByLabel = formState.details.associateBy { it.termLabel }
        val savedByLabel = existing.toFormState().details.associateBy { it.termLabel }
        val template = defaultDetails(formState.paymentMethod, formState.year)
            .applyAutoDistributionFrom(formState.totalTargetAmount)
        val merged = template.map { slot ->
            val incoming = incomingByLabel[slot.termLabel]
            val saved = savedByLabel[slot.termLabel]
            when {
                incoming?.isExcluded == true ->
                    incoming.copy(id = saved?.id ?: incoming.id)
                incoming?.isPaid == true ->
                    incoming.copy(id = saved?.id ?: incoming.id)
                saved != null -> saved
                incoming != null -> incoming
                else -> slot.withAutoExclusion(formState.year, formState.paymentMethod)
            }
        }
        return mapDetailsForSave(merged)
    }

    private fun mapDetailsForSave(
        details: List<DuesDetailItemState>
    ): List<DuesDetailItemState> {
        val today = LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        return details.map { item ->
            val amount = item.amount.toIntOrNull() ?: 0
            val effectivePaid = item.effectivePaidAmountForSave()
            val needsPayDate = !item.isExcluded && effectivePaid > 0L
            when {
                item.isExcluded -> item.copy(isPaid = false, payDate = "", additionalPayAmount = "")
                item.isPaid && amount <= 0 -> item.copy(isPaid = false)
                needsPayDate && item.payDate.isBlank() ->
                    item.copy(
                        payDate = today,
                        paidAmount = effectivePaid,
                        isPaid = amount > 0 && effectivePaid >= amount.toLong()
                    )
                item.additionalPayAmountValue > 0L ->
                    item.copy(
                        paidAmount = effectivePaid,
                        isPaid = amount > 0 && effectivePaid >= amount.toLong(),
                        payDate = if (item.payDate.isBlank()) today else item.payDate
                    )
                else -> item
            }
        }
    }

    private suspend fun buildFormStateFromSaved(data: YearlyDuesWithDetails): DuesPaymentFormState {
        val saved = data.toFormState()
        val template = defaultDetails(saved.paymentMethod, saved.year)
            .applyAutoDistributionFrom(saved.totalTargetAmount)
        val savedByLabel = saved.details.associateBy { it.termLabel }
        val mergedDetails = template.map { slot ->
            savedByLabel[slot.termLabel]?.let { existing ->
                slot.copy(
                    id = existing.id,
                    localKey = existing.localKey,
                    amount = existing.amount,
                    paidAmount = existing.paidAmount,
                    initialPaidAmount = existing.initialPaidAmount,
                    isPaid = existing.isPaid,
                    isExcluded = existing.isExcluded,
                    payDate = existing.payDate,
                    isCustomAmount = existing.isCustomAmount
                )
            } ?: slot.withAutoExclusion(saved.year, saved.paymentMethod)
        }
        val templateLabels = mergedDetails.map { it.termLabel }.toSet()
        val extras = saved.details.filter { it.termLabel !in templateLabels }
        val enriched = (mergedDetails + extras).map { item ->
            val histories = if (item.id > 0L) {
                runCatching { duesRepository.getPaymentHistoriesForDetail(item.id) }
                    .getOrDefault(emptyList())
                    .map { row ->
                        DuesPaymentEntryUi(
                            id = row.id,
                            payDate = row.payDate,
                            amount = row.amount,
                            linkedTransactionId = row.linkedTransactionId
                        )
                    }
            } else {
                emptyList()
            }
            val paidFromHistory = histories.sumOf { it.amount }
            val paid = maxOf(item.paidAmount, paidFromHistory)
            val target = item.targetAmount.toLong().coerceAtLeast(0L)
            val remaining = (target - paid).coerceAtLeast(0L)
            val isPaid = !item.isExcluded && target > 0L && paid >= target
            item.copy(
                paidAmount = paid,
                initialPaidAmount = paid,
                isPaid = isPaid,
                paymentHistory = histories,
                additionalPayAmount = when {
                    item.isExcluded || isPaid || remaining <= 0L -> ""
                    paid > 0L -> remaining.toString() // 부분납: 잔여액 기본 입력
                    else -> ""
                }
            )
        }
        return saved.copy(details = enriched)
    }

    private fun validateForm(formState: DuesPaymentFormState): String? = when {
        formState.memberId == null -> "회원을 선택해 주세요."
        formState.totalTargetAmount.toIntOrNull() == null ||
            (formState.totalTargetAmount.toIntOrNull() ?: 0) <= 0 -> "연회비 금액을 확인해 주세요."
        formState.paymentMethod == DuesPaymentMethod.MONTHLY &&
            formState.details.map { it.termLabel }.distinct().size != formState.details.size ->
            "월별 회차 라벨이 중복됩니다."
        formState.details.isEmpty() -> "납부 회차 내역이 없습니다."
        formState.details.none {
            it.isPaid || it.isExcluded || it.additionalPayAmountValue > 0L
        } ->
            "납부·부분 납부 금액·제외 중 하나 이상을 입력해 주세요."
        else -> {
            val overpay = formState.details.firstOrNull { item ->
                !item.isExcluded &&
                    !item.isPaid &&
                    item.additionalPayAmountValue > item.remainingAmount.toLong()
            }
            if (overpay != null) {
                "${overpay.termLabel} 추가 납부액이 잔여(${"%,d".format(overpay.remainingAmount)}원)를 초과합니다."
            } else {
            val invalidPayDateItem = formState.details
                .filter {
                    !it.isExcluded &&
                        (it.isPaid || it.additionalPayAmountValue > 0L) &&
                        (it.amount.toIntOrNull() ?: 0) > 0
                }
                .firstOrNull { dateStorageToDigits(it.payDate).length != DATE_DIGIT_LENGTH }
            if (invalidPayDateItem != null) {
                "${invalidPayDateItem.termLabel}의 납부일을 확인해 주세요."
            } else {
                null
            }
            }
        }
    }

    private fun resetFormAfterSave(savedYear: Int) {
        isFormVisible.value = false
        isEditMode.value = false
        form.value = emptyFormState()
        errorMessage.value = null
        selectedYear.value = savedYear
        viewModelScope.launch { filterPreferences.setYear(savedYear) }
    }

    fun requestDelete(id: Long) {
        if (!canManageDues()) return
        deleteTargetId.value = id
    }

    fun dismissDeleteConfirm() {
        deleteTargetId.value = null
    }

    fun confirmDelete() {
        if (!canManageDues()) return
        val id = deleteTargetId.value ?: return
        deleteTargetId.value = null
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    duesRepository.getYearlyDuesWithDetails(id)?.yearlyDues?.let {
                        duesRepository.deleteYearlyDues(it)
                    }
                }
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    private inline fun updateForm(block: DuesPaymentFormState.() -> DuesPaymentFormState) {
        form.update { it.block() }
    }

    private fun emptyFormState(): DuesPaymentFormState {
        val year = LocalDate.now().year
        val method = runCatching { defaultPaymentMethod.value }
            .getOrDefault(DuesPaymentMethod.DEFAULT)
        return DuesPaymentFormState(
            year = year,
            totalTargetAmount = DEFAULT_ANNUAL_DUES_AMOUNT,
            paymentMethod = method,
            details = defaultDetails(method, year)
                .applyAutoDistributionFrom(DEFAULT_ANNUAL_DUES_AMOUNT)
                .applyMeetingStartExclusion(year, method, joinDateRaw = null)
        )
    }

    private fun defaultDetails(
        method: DuesPaymentMethod,
        year: Int
    ): List<DuesDetailItemState> = when (method) {
        DuesPaymentMethod.MONTHLY -> defaultMonthlyDetails(year)
        DuesPaymentMethod.QUARTERLY ->
            (1..4).map { q ->
                DuesDetailItemState(
                    termLabel = "${q}분기",
                    payDate = defaultPayDateDigits(method, "${q}분기", year)
                )
            }
        DuesPaymentMethod.HALF_YEARLY ->
            listOf(
                DuesDetailItemState(
                    termLabel = "상반기",
                    payDate = defaultPayDateDigits(method, "상반기", year)
                ),
                DuesDetailItemState(
                    termLabel = "하반기",
                    payDate = defaultPayDateDigits(method, "하반기", year)
                )
            )
        DuesPaymentMethod.YEARLY ->
            listOf(
                DuesDetailItemState(
                    termLabel = "연간",
                    payDate = defaultPayDateDigits(method, "연간", year)
                )
            )
    }

    private fun defaultMonthlyDetails(year: Int): List<DuesDetailItemState> =
        (1..12).map { month ->
            DuesDetailItemState(
                termLabel = "${month}월",
                payDate = defaultPayDateDigits(DuesPaymentMethod.MONTHLY, "${month}월", year)
            )
        }

    private fun List<DuesDetailItemState>.applyMeetingStartExclusion(
        year: Int,
        method: DuesPaymentMethod,
        joinDateRaw: String? = selectedMemberJoinDate()
    ): List<DuesDetailItemState> = map { it.withAutoExclusion(year, method, joinDateRaw) }

    private fun DuesDetailItemState.withAutoExclusion(
        year: Int,
        method: DuesPaymentMethod,
        joinDateRaw: String? = selectedMemberJoinDate()
    ): DuesDetailItemState {
        if (isPaid) return this
        val start = DuesTermSchedule.effectiveMembershipStart(
            joinDate = DuesTermSchedule.parseMembershipDate(joinDateRaw),
            meetingCreatedAt = meetingCreatedAt.value
        )
        val shouldExclude = DuesTermSchedule.isTermBeforeMembershipStart(
            duesYear = year,
            method = method,
            termLabel = termLabel,
            membershipStart = start
        )
        return if (shouldExclude) {
            copy(isExcluded = true, isPaid = false, payDate = "")
        } else if (isExcluded) {
            copy(isExcluded = false)
        } else {
            this
        }
    }

    private fun selectedMemberJoinDate(): String? = cachedMemberJoinDate

    private fun defaultPayDateDigits(
        method: DuesPaymentMethod,
        termLabel: String,
        year: Int
    ): String {
        val month = when (method) {
            DuesPaymentMethod.MONTHLY -> termLabel.removeSuffix("월").toIntOrNull() ?: 1
            DuesPaymentMethod.QUARTERLY -> when (termLabel) {
                "1분기" -> 1
                "2분기" -> 4
                "3분기" -> 7
                "4분기" -> 10
                else -> 1
            }
            DuesPaymentMethod.HALF_YEARLY -> if (termLabel == "하반기") 7 else 1
            DuesPaymentMethod.YEARLY -> 1
        }
        return toStorageDate(year, month, 1)
    }

    private fun normalizePayDate(value: String): String {
        val digits = dateStorageToDigits(value)
        if (digits.length != DATE_DIGIT_LENGTH) return value
        val year = digits.take(4).toIntOrNull() ?: return value
        val month = digits.drop(4).take(2).toIntOrNull() ?: return value
        val day = digits.drop(6).take(2).toIntOrNull() ?: return value
        return toStorageDate(year, month, day)
    }

    private fun replaceYearKeepingMonthDay(existingDigits: String, year: Int): String {
        val digits = existingDigits.filter { it.isDigit() }
        if (digits.length != 8) return existingDigits
        val month = digits.substring(4, 6).toIntOrNull() ?: 1
        val day = digits.substring(6, 8).toIntOrNull() ?: 1
        return toStorageDate(year, month, day)
    }

    private fun replaceMonthInPayDate(payDate: String, month: Int, year: Int): String {
        val digits = payDate.filter { it.isDigit() }
        if (digits.length != 8) {
            return defaultPayDateDigits(DuesPaymentMethod.MONTHLY, "${month}월", year)
        }
        val day = digits.substring(6, 8).toIntOrNull() ?: 1
        return toStorageDate(year, month, day)
    }

    private fun toStorageDate(year: Int, month: Int, day: Int): String {
        val m = month.coerceIn(1, 12).toString().padStart(2, '0')
        val d = day.coerceIn(1, 31).toString().padStart(2, '0')
        return "$year-$m-$d"
    }

    private companion object {
        const val DATE_DIGIT_LENGTH = 8
    }

    private fun List<DuesDetailItemState>.applyAutoDistributionFrom(
        totalTargetDigits: String
    ): List<DuesDetailItemState> {
        val total = totalTargetDigits.toIntOrNull() ?: 0
        if (total <= 0 || isEmpty()) return this
        val n = size
        val base = total / n
        var remainder = total % n
        return map { item ->
            val extra = if (remainder > 0) {
                remainder -= 1
                1
            } else 0
            item.copy(amount = (base + extra).toString(), isCustomAmount = false)
        }
    }

    private fun DuesPaymentFormState.recalculateAutoDistributedAmounts(): DuesPaymentFormState {
        val total = totalTargetAmount.toIntOrNull() ?: 0
        if (total <= 0 || details.isEmpty()) return this
        val n = details.size
        val base = total / n
        var remainder = total % n
        val next = details.map { item ->
            if (item.isCustomAmount) return@map item
            val extra = if (remainder > 0) {
                remainder -= 1
                1
            } else 0
            item.copy(amount = (base + extra).toString())
        }
        return copy(details = next)
    }
}
