package com.smartexpense.ui.club.transaction

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.local.entity.club.toDisplayLabel
import com.smartexpense.data.model.bank.BankNotificationDraft
import com.smartexpense.data.repository.bank.BankNotificationDraftRepository
import com.smartexpense.data.repository.club.ClubAccountRepository
import com.smartexpense.data.repository.club.ClubSettingsRepository
import com.smartexpense.data.repository.club.ClubTransactionRepository
import com.smartexpense.data.repository.club.DuesRepository
import com.smartexpense.data.repository.club.MemberRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.storage.TransactionReceiptStorage
import com.smartexpense.domain.dues.DuesLedgerAutoEntry
import com.smartexpense.domain.dues.DuesMatcher
import com.smartexpense.domain.ledger.FreshLedgerBalance
import com.smartexpense.domain.ledger.LedgerBalanceCalculator
import com.smartexpense.ui.club.components.DATE_DIGIT_LENGTH
import com.smartexpense.ui.club.components.dateDigitsToStorage
import com.smartexpense.ui.club.components.dateStorageToDigits
import com.smartexpense.ui.club.dues.DEFAULT_ANNUAL_DUES_AMOUNT
import com.smartexpense.ui.club.toFormState
import com.smartexpense.ui.club.toListItemUi
import com.smartexpense.ui.common.observeIsAdmin
import com.smartexpense.ui.common.observeSelectedClubChanges
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val clubTransactionRepository: ClubTransactionRepository,
    private val clubAccountRepository: ClubAccountRepository,
    private val memberRepository: MemberRepository,
    private val duesRepository: DuesRepository,
    private val clubSettingsRepository: ClubSettingsRepository,
    private val bankNotificationDraftRepository: BankNotificationDraftRepository,
    private val receiptStorage: TransactionReceiptStorage,
    private val selectedClubRepository: SelectedClubRepository,
    private val meetingRoleRepository: MeetingRoleRepository
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = observeIsAdmin(meetingRoleRepository)

    private fun canManageLedger(): Boolean =
        isAdmin.value || meetingRoleRepository.isSystemAdminSession()

    private val isFormVisible = MutableStateFlow(false)
    private val form = MutableStateFlow(TransactionFormState(date = LocalDate.now().toString()))
    private val isSaving = MutableStateFlow(false)
    private val deleteTargetId = MutableStateFlow<Long?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val currentYearMonth = MutableStateFlow(YearMonth.now())
    private val listMode = MutableStateFlow(TransactionListMode.MONTHLY)
    private val searchQuery = MutableStateFlow("")
    private val appliedFilter = MutableStateFlow(TransactionFilterState())
    private val filterDraft = MutableStateFlow(TransactionFilterState())
    private val showFilterSheet = MutableStateFlow(false)
    private val infoMessage = MutableStateFlow<String?>(null)
    private val unpaidDuesOptions = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    private val duesMemoPreview = MutableStateFlow<String?>(null)

    init {
        observeSelectedClubChanges(selectedClubRepository) {
            bankNotificationDraftRepository.clear()
            isFormVisible.value = false
            form.value = TransactionFormState(date = LocalDate.now().toString())
            deleteTargetId.value = null
            showFilterSheet.value = false
            appliedFilter.value = TransactionFilterState()
            filterDraft.value = TransactionFilterState()
            searchQuery.value = ""
        }
    }

    private val listQueryInputs = combine(
        listMode,
        currentYearMonth,
        appliedFilter,
        searchQuery
    ) { mode, yearMonth, filter, query ->
        ListQueryInputs(mode, yearMonth, filter, query)
    }.distinctUntilChanged()

    /**
     * 장부 목록·잔액의 단일 소스.
     * 월을 바꿀 때마다 Firestore 구간 구독을 다시 걸지 않고, 이미 받은 전체 거래를 메모리에서 거른다.
     */
    private val allTransactions = clubTransactionRepository.getAllTransactions()

    private val filteredTransactions = combine(listQueryInputs, allTransactions) { inputs, all ->
        filterTransactionsForInputs(inputs, all)
    }

    /**
     * 상단 '현재 잔액' 유일한 소스.
     * 한우리 시드는 이월금이 이미 첫 수입 전표이므로 기초잔액을 다시 더하지 않는다.
     * 최종 잔액 = 전체 수입 − 전체 지출 (이체 제외).
     */
    private val allPeriodBalance: kotlinx.coroutines.flow.Flow<Long> =
        allTransactions.map { allPeriod ->
            LedgerBalanceCalculator.operatingIncome(allPeriod) -
                LedgerBalanceCalculator.operatingExpense(allPeriod)
        }

    private val listSnapshot = combine(
        combine(filteredTransactions, allTransactions, allPeriodBalance) { filtered, allPeriod, balance ->
            Triple(filtered, allPeriod, balance)
        },
        memberRepository.observeAllMembers(),
        clubAccountRepository.observeAll(),
        listQueryInputs
    ) { txBundle, members, accounts, inputs ->
        val (transactions, allPeriod, balance) = txBundle
        // 저장된 balanceAfter가 아니라 전체 거래를 다시 누적해 행 잔액을 맞춘다.
        val runningById = LedgerBalanceCalculator.runningBalances(allPeriod, initialBalance = 0L)
        val periodItems = transactions.map { tx ->
            tx.toListItemUi().copy(balanceAfter = runningById[tx.id])
        }
        val totalIncome = periodItems
            .filter { !it.isTransfer && it.typeLabel == ClubTransactionType.INCOME.toDisplayLabel() }
            .sumOf { it.amount.toLong() }
        val totalExpense = periodItems
            .filter { !it.isTransfer && it.typeLabel == ClubTransactionType.EXPENSE.toDisplayLabel() }
            .sumOf { it.amount.toLong() }
        val memberOptions = members
            .filter { it.status == MemberStatus.ACTIVE }
            .map { it.id to it.name }
            .sortedBy { it.second }
        LedgerListSnapshot(
            periodItems = periodItems,
            totalIncome = with(FreshLedgerBalance) { totalIncome.toDisplayInt() },
            totalExpense = with(FreshLedgerBalance) { totalExpense.toDisplayInt() },
            balance = with(FreshLedgerBalance) { balance.toDisplayInt() },
            listMode = inputs.mode,
            currentYearMonth = inputs.yearMonth,
            searchQuery = inputs.query,
            appliedFilter = inputs.filter,
            activeFilterChips = inputs.filter.toActiveChips(memberOptions),
            memberOptions = memberOptions,
            accountOptions = accounts.map { it.id to it.toDisplayLabel() }
        )
    }.flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    private val interactionSnapshot = combine(
        combine(isFormVisible, form, isSaving, deleteTargetId) { visible, formState, saving, deleteId ->
            FormInteraction(visible, formState, saving, deleteId)
        },
        combine(errorMessage, infoMessage, filterDraft, showFilterSheet) { error, info, draft, showSheet ->
            MessageInteraction(error, info, draft, showSheet)
        },
        combine(unpaidDuesOptions, duesMemoPreview) { options, preview -> options to preview }
    ) { formUi, messages, duesLink ->
        LedgerInteractionSnapshot(
            isFormVisible = formUi.isFormVisible,
            form = formUi.form,
            isSaving = formUi.isSaving,
            deleteTargetId = formUi.deleteTargetId,
            errorMessage = messages.errorMessage,
            infoMessage = messages.infoMessage,
            filterDraft = messages.filterDraft,
            showFilterSheet = messages.showFilterSheet,
            unpaidDuesOptions = duesLink.first,
            duesMemoPreview = duesLink.second
        )
    }

    val uiState: StateFlow<TransactionUiState> = combine(
        listSnapshot,
        interactionSnapshot
    ) { list, interaction ->
        TransactionUiState(
            listMode = list.listMode,
            currentYearMonth = list.currentYearMonth,
            displayTransactions = list.periodItems,
            monthlyTransactions = list.periodItems,
            totalIncome = list.totalIncome,
            totalExpense = list.totalExpense,
            balance = list.balance,
            searchQuery = list.searchQuery,
            appliedFilter = list.appliedFilter,
            filterDraft = interaction.filterDraft,
            showFilterSheet = interaction.showFilterSheet,
            activeFilterChips = list.activeFilterChips,
            categoryFilterOptions = filterCategoryOptions(),
            isFormVisible = interaction.isFormVisible,
            form = interaction.form,
            memberOptions = list.memberOptions,
            unpaidDuesOptions = interaction.unpaidDuesOptions,
            duesMemoPreview = interaction.duesMemoPreview,
            accountOptions = list.accountOptions,
            isSaving = interaction.isSaving,
            deleteTargetId = interaction.deleteTargetId,
            errorMessage = interaction.errorMessage,
            infoMessage = interaction.infoMessage
        )
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TransactionUiState(form = TransactionFormState(date = LocalDate.now().toString()))
        )

    fun notifyLedgerVisible() {
        // 실시간 구독이 목록을 유지한다. 화면 진입마다 전체 장부를 재다운로드하지 않는다.
    }

    fun refreshOnVisible() {
        notifyLedgerVisible()
    }

    private suspend fun persistBalanceSnapshotAfterMutation() {
        // 현재 잔액은 allPeriodBalance 라이브 합산. 저장 후 전체 재계산으로 화면을 막지 않는다.
    }

    fun nextMonth() {
        currentYearMonth.update { current ->
            when (listMode.value) {
                TransactionListMode.YEARLY -> current.plusYears(1)
                else -> current.plusMonths(1)
            }
        }
    }

    fun setListMode(mode: TransactionListMode) {
        listMode.value = mode
        if (mode != TransactionListMode.ALL) {
            searchQuery.value = ""
        }
    }

    fun openFilterSheet() {
        filterDraft.value = appliedFilter.value
        showFilterSheet.value = true
    }

    fun dismissFilterSheet() {
        filterDraft.value = appliedFilter.value
        showFilterSheet.value = false
    }

    fun applyFilter() {
        appliedFilter.value = filterDraft.value
        showFilterSheet.value = false
    }

    fun resetFilterDraft() {
        filterDraft.value = TransactionFilterState()
    }

    fun clearAllFilters() {
        appliedFilter.value = TransactionFilterState()
        filterDraft.value = TransactionFilterState()
        searchQuery.value = ""
    }

    fun removeFilterChip(key: TransactionFilterKey) {
        appliedFilter.update { current ->
            when (key) {
                TransactionFilterKey.DATE_RANGE,
                TransactionFilterKey.START_DATE,
                TransactionFilterKey.END_DATE -> current.copy(startDate = null, endDate = null)
                TransactionFilterKey.MEMBER -> current.copy(targetMemberId = null)
                TransactionFilterKey.CATEGORY -> current.copy(category = null)
                TransactionFilterKey.AMOUNT_RANGE -> current.copy(minAmount = null, maxAmount = null)
                TransactionFilterKey.MIN_AMOUNT -> current.copy(minAmount = null)
                TransactionFilterKey.MAX_AMOUNT -> current.copy(maxAmount = null)
                TransactionFilterKey.RECEIPT -> current.copy(hasReceipt = null)
            }
        }
        filterDraft.value = appliedFilter.value
    }

    fun updateFilterDraftStartDate(date: String) {
        filterDraft.update { it.copy(startDate = date.takeIf { value -> value.isNotBlank() }?.let(::epochMillisFromDateString)) }
    }

    fun updateFilterDraftEndDate(date: String) {
        filterDraft.update { it.copy(endDate = date.takeIf { value -> value.isNotBlank() }?.let(::epochMillisFromDateString)) }
    }

    fun updateFilterDraftMember(memberId: Long) {
        filterDraft.update { it.copy(targetMemberId = memberId) }
    }

    fun clearFilterDraftMember() {
        filterDraft.update { it.copy(targetMemberId = null) }
    }

    fun updateFilterDraftCategory(category: String) {
        filterDraft.update { it.copy(category = category) }
    }

    fun clearFilterDraftCategory() {
        filterDraft.update { it.copy(category = null) }
    }

    fun updateFilterDraftMinAmount(value: String) {
        filterDraft.update { it.copy(minAmount = value.filter(Char::isDigit).toLongOrNull()) }
    }

    fun updateFilterDraftMaxAmount(value: String) {
        filterDraft.update { it.copy(maxAmount = value.filter(Char::isDigit).toLongOrNull()) }
    }

    fun updateFilterDraftHasReceipt(checked: Boolean) {
        filterDraft.update { it.copy(hasReceipt = if (checked) true else null) }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearch() {
        searchQuery.value = ""
    }

    fun previousMonth() {
        currentYearMonth.update { current ->
            when (listMode.value) {
                TransactionListMode.YEARLY -> current.minusYears(1)
                else -> current.minusMonths(1)
            }
        }
    }

    fun setYearMonth(yearMonth: YearMonth) {
        currentYearMonth.value = yearMonth
    }

    fun goToCurrentMonth() {
        currentYearMonth.value = YearMonth.now()
    }

    fun openForm() {
        if (!canManageLedger()) return
        form.value = TransactionFormState(date = LocalDate.now().toString())
        isFormVisible.value = true
        viewModelScope.launch {
            val draft = bankNotificationDraftRepository.consume() ?: return@launch
            applyBankDraftAndShowForm(draft)
        }
    }

    fun openFormFromBankNotification() {
        if (!canManageLedger()) return
        viewModelScope.launch {
            if (isFormVisible.value) return@launch
            val draft = bankNotificationDraftRepository.consume() ?: return@launch
            applyBankDraftAndShowForm(draft)
        }
    }

    private suspend fun applyBankDraftAndShowForm(draft: BankNotificationDraft) {
        val members = memberRepository.observeAllMembers().first()
        val yearlyDues = duesRepository.getAllWithDetailsOnce()
        val matchResult = DuesMatcher.match(
            memo = draft.memo,
            amount = draft.amount,
            members = members,
            yearlyDuesRecords = yearlyDues
        )
        val autoMatchUi = matchResult.toAutoMatchUi()
        form.value = TransactionFormState(
            date = LocalDate.now().toString(),
            amount = draft.amount.toString(),
            note = draft.memo,
            type = ClubTransactionType.INCOME,
            category = ClubCategory.REGULAR_DUES,
            targetMemberId = matchResult.memberId,
            duesAutoMatch = autoMatchUi,
            focusMemberDropdown = autoMatchUi.needsMemberSelection
        )
        if (autoMatchUi.canAutoMatch) {
            infoMessage.value = "은행 알림에서 회비 납부 내역이 자동으로 매칭되었습니다."
        } else {
            infoMessage.value = "은행 알림 초안을 불러왔습니다."
        }
        isFormVisible.value = true
    }

    fun openEditForm(id: Long) {
        viewModelScope.launch {
            val transaction = clubTransactionRepository.getTransaction(id) ?: return@launch
            form.value = transaction.toFormState()
            isFormVisible.value = true
        }
    }

    fun dismissForm() {
        val draftPath = form.value.receiptPath
        val originalPath = form.value.originalReceiptPath
        if (draftPath != null && draftPath != originalPath) {
            receiptStorage.deleteReceipt(draftPath)
        }
        isFormVisible.value = false
        form.value = TransactionFormState(date = LocalDate.now().toString())
        clearDuesLinkUi()
    }

    fun updateCategory(category: String) {
        val normalized = ClubCategory.normalizeCategory(category)
        val type = ClubCategory.typeFor(normalized) ?: form.value.type
        val condolence = ClubCategory.isCondolenceCategory(normalized)
        val regularDues = normalized == ClubCategory.REGULAR_DUES
        val regularDuesWithMatch = regularDues && form.value.duesAutoMatch != null
        updateForm {
            copy(
                entryMode = if (ClubCategory.isTransferCategory(normalized)) {
                    TransactionEntryMode.TRANSFER
                } else if (type == ClubTransactionType.INCOME) {
                    TransactionEntryMode.INCOME
                } else {
                    TransactionEntryMode.EXPENSE
                },
                category = normalized,
                type = type,
                targetMemberId = when {
                    condolence -> targetMemberId
                    regularDuesWithMatch -> targetMemberId
                    regularDues -> targetMemberId
                    else -> null
                },
                eventSubCategory = if (condolence) eventSubCategory else null,
                duesAutoMatch = if (regularDues) duesAutoMatch else null,
                duesLinkDetailId = if (regularDues) duesLinkDetailId else null,
                duesLedgerPaymentMode = if (regularDues) {
                    duesLedgerPaymentMode
                } else {
                    DuesLedgerPaymentMode.FULL
                },
                duesLinkRemainingAmount = if (regularDues) duesLinkRemainingAmount else null
            )
        }
        viewModelScope.launch {
            if (regularDues && form.value.duesAutoMatch == null) {
                refreshDuesLinkUi(form.value.targetMemberId, form.value.duesLinkDetailId)
            } else {
                clearDuesLinkUi()
            }
        }
    }

    fun updateEntryMode(mode: TransactionEntryMode) {
        updateForm {
            when (mode) {
                TransactionEntryMode.INCOME -> copy(
                    entryMode = mode,
                    type = ClubTransactionType.INCOME,
                    category = ClubCategory.defaultCategory(ClubTransactionType.INCOME),
                    accountId = null,
                    transferToAccountId = null,
                    targetMemberId = null,
                    eventSubCategory = null,
                    duesAutoMatch = null,
                    duesLinkDetailId = null
                )
                TransactionEntryMode.EXPENSE -> copy(
                    entryMode = mode,
                    type = ClubTransactionType.EXPENSE,
                    category = ClubCategory.defaultCategory(ClubTransactionType.EXPENSE),
                    accountId = null,
                    transferToAccountId = null,
                    targetMemberId = null,
                    eventSubCategory = null,
                    duesAutoMatch = null,
                    duesLinkDetailId = null
                )
                TransactionEntryMode.TRANSFER -> copy(
                    entryMode = mode,
                    type = ClubTransactionType.EXPENSE,
                    category = ClubCategory.TRANSFER,
                    accountId = accountId,
                    transferToAccountId = transferToAccountId,
                    targetMemberId = null,
                    eventSubCategory = null,
                    duesAutoMatch = null,
                    duesLinkDetailId = null
                )
            }
        }
        clearDuesLinkUi()
    }

    fun updateAccountId(accountId: Int) = updateForm { copy(accountId = accountId) }

    fun updateTransferToAccountId(accountId: Int) = updateForm { copy(transferToAccountId = accountId) }

    fun updateTargetMemberId(memberId: Long) {
        updateForm {
            copy(
                targetMemberId = memberId,
                focusMemberDropdown = false,
                duesLinkDetailId = if (showManualDuesLinkUi) null else duesLinkDetailId,
                duesLinkRemainingAmount = if (showManualDuesLinkUi) null else duesLinkRemainingAmount,
                amount = if (showManualDuesLinkUi) "" else amount
            )
        }
        viewModelScope.launch {
            val current = form.value
            if (current.showManualDuesLinkUi) {
                refreshDuesLinkUi(memberId, null)
                return@launch
            }
            if (current.duesAutoMatch == null || current.isEditing) return@launch
            val amount = LedgerBalanceCalculator.parsePositiveAmount(current.amount) ?: return@launch
            val members = memberRepository.observeAllMembers().first()
            val yearlyDues = duesRepository.getAllWithDetailsOnce()
            val matchResult = DuesMatcher.matchForMember(
                memberId = memberId,
                amount = amount,
                members = members,
                yearlyDuesRecords = yearlyDues
            )
            updateForm {
                copy(duesAutoMatch = matchResult.toAutoMatchUi())
            }
        }
    }

    fun updateDuesLinkDetailId(detailId: Long) {
        updateForm { copy(duesLinkDetailId = detailId) }
        viewModelScope.launch {
            refreshDuesLinkUi(form.value.targetMemberId, detailId)
            val detail = duesRepository.getDetailOption(detailId) ?: return@launch
            val remaining = detail.remainingAmount
            applyDuesAmountForMode(form.value.duesLedgerPaymentMode, remaining)
        }
    }

    fun updateDuesLedgerPaymentMode(mode: DuesLedgerPaymentMode) {
        updateForm { copy(duesLedgerPaymentMode = mode) }
        viewModelScope.launch {
            val detailId = form.value.duesLinkDetailId ?: run {
                updateForm {
                    copy(
                        amount = if (mode == DuesLedgerPaymentMode.PARTIAL) "" else amount,
                        duesLinkRemainingAmount = duesLinkRemainingAmount
                    )
                }
                return@launch
            }
            val detail = duesRepository.getDetailOption(detailId) ?: return@launch
            applyDuesAmountForMode(mode, detail.remainingAmount)
        }
    }

    private fun applyDuesAmountForMode(mode: DuesLedgerPaymentMode, remaining: Int) {
        when (mode) {
            DuesLedgerPaymentMode.FULL -> updateForm {
                copy(
                    duesLedgerPaymentMode = mode,
                    duesLinkRemainingAmount = remaining.takeIf { it > 0 },
                    amount = if (remaining > 0) remaining.toString() else ""
                )
            }
            DuesLedgerPaymentMode.PARTIAL -> updateForm {
                copy(
                    duesLedgerPaymentMode = mode,
                    duesLinkRemainingAmount = remaining.takeIf { it > 0 },
                    // 부분 납부: 잔여 전액이 채워져 있으면 비워 직접 입력하게 함
                    amount = amount
                        .takeIf { raw ->
                            val parsed = LedgerBalanceCalculator.parsePositiveAmount(raw)
                            parsed != null && parsed < remaining
                        }
                        .orEmpty()
                )
            }
        }
    }

    fun updateEventSubCategory(subCategory: String) = updateForm { copy(eventSubCategory = subCategory) }

    fun updateDate(value: String) = updateForm { copy(date = normalizeFormDate(value)) }
    fun updateAmount(value: String) = updateForm { copy(amount = value) }
    fun updateNote(value: String) = updateForm { copy(note = value) }

    fun setReceiptFromGallery(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val previousDraft = form.value.receiptPath
                val original = form.value.originalReceiptPath
                val path = receiptStorage.saveFromUri(uri, form.value.editingId)
                if (previousDraft != null && previousDraft != original) {
                    receiptStorage.deleteReceipt(previousDraft)
                }
                updateForm { copy(receiptPath = path) }
            }.onFailure {
                errorMessage.value = "영수증 이미지를 저장하지 못했습니다."
            }
        }
    }

    fun setReceiptFromCamera(path: String) {
        val previousDraft = form.value.receiptPath
        val original = form.value.originalReceiptPath
        if (previousDraft != null && previousDraft != original && previousDraft != path) {
            receiptStorage.deleteReceipt(previousDraft)
        }
        updateForm { copy(receiptPath = path) }
    }

    fun createCameraImageUri(): Pair<Uri, String> {
        val (uri, file) = receiptStorage.createCameraImageUri()
        return uri to file.absolutePath
    }

    fun clearReceipt() {
        val path = form.value.receiptPath
        val original = form.value.originalReceiptPath
        if (path != null && path != original) {
            receiptStorage.deleteReceipt(path)
        }
        updateForm { copy(receiptPath = null) }
    }

    fun confirmDuesAutoMatch() {
        if (!canManageLedger()) return
        val formState = form.value
        val autoMatch = formState.duesAutoMatch
        if (autoMatch == null || !autoMatch.canAutoMatch) return
        if (formState.date.isBlank()) {
            errorMessage.value = "날짜를 입력해 주세요."
            return
        }
        val amount = LedgerBalanceCalculator.parsePositiveAmount(formState.amount)
        if (amount == null) {
            errorMessage.value = "올바른 금액을 입력해 주세요."
            return
        }
        val memberId = autoMatch.memberId ?: formState.targetMemberId
        if (memberId == null) {
            errorMessage.value = "회원을 선택해 주세요."
            updateForm { copy(focusMemberDropdown = true) }
            return
        }

        viewModelScope.launch {
            isSaving.value = true
            runCatching {
                val payDate = normalizeFormDate(formState.date)
                val members = memberRepository.observeAllMembers().first()
                val memberName = members.find { it.id == memberId }?.name
                    ?: error("회원을 찾을 수 없습니다.")
                val primaryDetailId = autoMatch.detailIds.singleOrNull()
                val note = if (primaryDetailId != null) {
                    val detail = duesRepository.getDetailOption(primaryDetailId)
                        ?: error("선택한 회비 회차를 찾을 수 없습니다.")
                    DuesLedgerAutoEntry.buildPaymentNote(
                        memberName = memberName,
                        year = detail.year,
                        termLabel = detail.termLabel
                    )
                } else {
                    formState.note.trim().ifBlank { null }
                }
                val entity = ClubTransactionEntity(
                    clubId = 0,
                    date = payDate,
                    type = ClubTransactionType.INCOME,
                    category = ClubCategory.REGULAR_DUES,
                    incomeAmount = amount,
                    expenseAmount = 0,
                    note = note,
                    targetMemberId = memberId,
                    receiptPath = formState.receiptPath,
                    linkedDuesDetailId = primaryDetailId
                )
                duesRepository.saveTransactionWithDuesPayment(
                    transaction = entity,
                    duesDetailIds = autoMatch.detailIds,
                    payDate = payDate
                )
                persistBalanceSnapshotAfterMutation()
            }.onSuccess {
                isSaving.value = false
                isFormVisible.value = false
                form.value = TransactionFormState(date = LocalDate.now().toString())
                clearDuesLinkUi()
                infoMessage.value = "거래가 저장되었습니다."
            }.onFailure {
                isSaving.value = false
                errorMessage.value = it.message ?: "거래를 저장하는 중 오류가 발생했습니다."
            }
        }
    }

    fun dismissDuesAutoMatchBanner() {
        updateForm { copy(duesAutoMatch = null, focusMemberDropdown = false) }
        viewModelScope.launch {
            val current = form.value
            if (current.showManualDuesLinkUi) {
                refreshDuesLinkUi(current.targetMemberId, current.duesLinkDetailId)
            }
        }
    }

    fun clearMemberDropdownFocus() {
        updateForm { copy(focusMemberDropdown = false) }
    }

    fun saveTransaction() {
        if (!canManageLedger()) {
            errorMessage.value = "모임 관리자만 장부를 저장할 수 있습니다."
            return
        }
        if (isSaving.value) return
        val formState = form.value
        if (dateStorageToDigits(formState.date).length != DATE_DIGIT_LENGTH) {
            errorMessage.value = "날짜를 입력해 주세요."
            return
        }
        val transactionDate = normalizeFormDate(formState.date)
        val amount = LedgerBalanceCalculator.parsePositiveAmount(formState.amount)
        if (amount == null) {
            errorMessage.value = "올바른 금액을 입력해 주세요."
            return
        }

        if (formState.isTransferMode) {
            val fromAccountId = formState.accountId
            val toAccountId = formState.transferToAccountId
            if (fromAccountId == null || toAccountId == null) {
                errorMessage.value = "출금·입금 계좌를 모두 선택해 주세요."
                return
            }
            if (fromAccountId == toAccountId) {
                errorMessage.value = "출금 계좌와 입금 계좌는 서로 달라야 합니다."
                return
            }
            viewModelScope.launch {
                isSaving.value = true
                val entity = ClubTransactionEntity(
                    id = formState.editingId ?: 0,
                    clubId = 0,
                    date = transactionDate,
                    type = ClubTransactionType.EXPENSE,
                    category = ClubCategory.TRANSFER,
                    incomeAmount = amount,
                    expenseAmount = amount,
                    note = formState.note.trim().ifBlank { null },
                    accountId = fromAccountId,
                    transferToAccountId = toAccountId,
                    receiptPath = formState.receiptPath
                )
                persistTransaction(formState, entity)
            }
            return
        }

        val category = ClubCategory.resolveCategory(formState.type, formState.category)
        val type = ClubCategory.resolveType(category, formState.type)
        if (category !in ClubCategory.incomeCategories + ClubCategory.expenseCategories) {
            errorMessage.value = "유효한 카테고리를 선택해 주세요."
            return
        }
        if (ClubCategory.isCondolenceCategory(category)) {
            if (formState.targetMemberId == null) {
                errorMessage.value = "경조사 대상 회원을 선택해 주세요."
                return
            }
            if (formState.eventSubCategory.isNullOrBlank()) {
                errorMessage.value = "경조사 세부 항목을 선택해 주세요."
                return
            }
        }

        if (
            category == ClubCategory.REGULAR_DUES &&
            !formState.isEditing &&
            formState.duesAutoMatch?.canAutoMatch != true
        ) {
            val memberId = formState.targetMemberId
            if (memberId == null) {
                errorMessage.value = "회원을 선택해 주세요."
                return
            }
            val detailId = formState.duesLinkDetailId
            if (detailId == null) {
                errorMessage.value = if (unpaidDuesOptions.value.isEmpty()) {
                    "미납·부분납 회비가 없습니다. 회비를 먼저 등록해 주세요."
                } else {
                    "납부 대상 월/기수를 선택해 주세요."
                }
                return
            }
            viewModelScope.launch {
                isSaving.value = true
                try {
                    saveRegularDuesWithLinkedDetail(
                        formState = formState,
                        memberId = memberId,
                        detailId = detailId,
                        amount = amount,
                        transactionDate = transactionDate
                    )
                    isFormVisible.value = false
                    form.value = TransactionFormState(date = LocalDate.now().toString())
                    clearDuesLinkUi()
                    infoMessage.value = "거래가 저장되었습니다."
                } catch (e: Exception) {
                    errorMessage.value = e.message ?: "거래를 저장하는 중 오류가 발생했습니다."
                } finally {
                    isSaving.value = false
                }
            }
            return
        }

        viewModelScope.launch {
            isSaving.value = true
            val isCondolence = ClubCategory.isCondolenceCategory(category)
            val entity = ClubTransactionEntity(
                id = formState.editingId ?: 0,
                clubId = 0,
                date = transactionDate,
                type = type,
                category = category,
                incomeAmount = if (type == ClubTransactionType.INCOME) amount else 0,
                expenseAmount = if (type == ClubTransactionType.EXPENSE) amount else 0,
                note = formState.note.trim().ifBlank { null },
                targetMemberId = if (isCondolence) formState.targetMemberId else null,
                eventSubCategory = if (isCondolence) formState.eventSubCategory else null,
                receiptPath = formState.receiptPath,
                accountId = formState.accountId
            )
            persistTransaction(formState, entity)
        }
    }

    private suspend fun saveRegularDuesWithLinkedDetail(
        formState: TransactionFormState,
        memberId: Long,
        detailId: Long,
        amount: Int,
        transactionDate: String
    ) {
        val detail = duesRepository.getDetailOption(detailId)
            ?: error("선택한 회비 회차를 찾을 수 없습니다.")
        if (detail.memberId != memberId) {
            error("선택한 회원과 회비 회차가 일치하지 않습니다.")
        }
        val remaining = detail.remainingAmount
        if (remaining <= 0) {
            error("이미 완납된 회비입니다.")
        }
        if (amount <= 0) {
            error("납부 금액을 입력해 주세요.")
        }
        if (amount > remaining) {
            error("잔여 회비(${"%,d".format(remaining)}원)를 초과할 수 없습니다.")
        }
        if (formState.duesLedgerPaymentMode == DuesLedgerPaymentMode.PARTIAL && amount >= remaining) {
            error("부분 납부는 잔여(${"%,d".format(remaining)}원)보다 적은 금액을 입력해 주세요. 전액은 완납을 선택하세요.")
        }
        val memberName = memberRepository.observeAllMembers().first()
            .find { it.id == memberId }?.name
            ?: error("회원을 찾을 수 없습니다.")
        val note = DuesLedgerAutoEntry.buildPaymentNote(
            memberName = memberName,
            year = detail.year,
            termLabel = detail.termLabel
        )
        val entity = ClubTransactionEntity(
            clubId = 0,
            date = transactionDate,
            type = ClubTransactionType.INCOME,
            category = ClubCategory.REGULAR_DUES,
            incomeAmount = amount,
            expenseAmount = 0,
            note = note,
            targetMemberId = memberId,
            receiptPath = formState.receiptPath,
            linkedDuesDetailId = detailId
        )
        duesRepository.saveTransactionWithDuesPayment(
            transaction = entity,
            duesDetailIds = listOf(detailId),
            payDate = transactionDate
        )
        persistBalanceSnapshotAfterMutation()
    }

    private suspend fun refreshDuesLinkUi(memberId: Long?, detailId: Long?) {
        if (memberId == null) {
            unpaidDuesOptions.value = emptyList()
            duesMemoPreview.value = null
            return
        }
        val year = dateStorageToDigits(form.value.date).take(4).toIntOrNull()
            ?: LocalDate.now().year
        val member = memberRepository.observeAllMembers().first().find { it.id == memberId }
        val meeting = meetingRoleRepository.selectedMeeting.first()
        val method = runCatching {
            clubSettingsRepository.observeSettings().first().duesPaymentMethod
        }.getOrDefault(DuesPaymentMethod.DEFAULT)
        val targetAmount = DEFAULT_ANNUAL_DUES_AMOUNT.toIntOrNull() ?: 300_000
        runCatching {
            duesRepository.ensurePayableDuesForMember(
                memberId = memberId,
                year = year,
                paymentMethod = method,
                totalTargetAmount = targetAmount,
                joinDateRaw = member?.joinDate,
                meetingName = meeting?.name,
                meetingCreatedAtRaw = meeting?.createdAt
            )
        }
        unpaidDuesOptions.value = duesRepository.getUnpaidDetailsForMember(memberId)
            .map { it.detailId to it.toDropdownLabel() }
        duesMemoPreview.value = buildDuesMemoPreview(memberId, detailId)
    }

    private suspend fun buildDuesMemoPreview(memberId: Long?, detailId: Long?): String? {
        if (memberId == null || detailId == null) return null
        val memberName = memberRepository.observeAllMembers().first()
            .find { it.id == memberId }?.name ?: return null
        val detail = duesRepository.getDetailOption(detailId) ?: return null
        if (detail.memberId != memberId) return null
        return DuesLedgerAutoEntry.buildPaymentNote(
            memberName = memberName,
            year = detail.year,
            termLabel = detail.termLabel
        )
    }

    private fun clearDuesLinkUi() {
        unpaidDuesOptions.value = emptyList()
        duesMemoPreview.value = null
        updateForm {
            copy(
                duesLinkRemainingAmount = null,
                duesLedgerPaymentMode = DuesLedgerPaymentMode.FULL
            )
        }
    }

    private suspend fun persistTransaction(formState: TransactionFormState, entity: ClubTransactionEntity) {
        try {
            if (formState.isEditing) {
                if (formState.originalReceiptPath != null &&
                    formState.originalReceiptPath != formState.receiptPath
                ) {
                    receiptStorage.deleteReceipt(formState.originalReceiptPath)
                }
                clubTransactionRepository.updateTransaction(entity)
            } else {
                clubTransactionRepository.insertTransaction(entity)
            }
            persistBalanceSnapshotAfterMutation()
            isFormVisible.value = false
            form.value = TransactionFormState(date = LocalDate.now().toString())
            clearDuesLinkUi()
            infoMessage.value = "거래가 저장되었습니다."
        } catch (e: Exception) {
            errorMessage.value = e.message?.takeIf { it.isNotBlank() }
                ?: "거래를 저장하는 중 오류가 발생했습니다."
        } finally {
            isSaving.value = false
        }
    }

    fun requestDelete(id: Long) {
        if (!canManageLedger()) return
        deleteTargetId.value = id
    }

    fun dismissDeleteConfirm() {
        deleteTargetId.value = null
    }

    fun confirmDelete() {
        if (!canManageLedger()) return
        val id = deleteTargetId.value ?: return
        deleteTargetId.value = null
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    clubTransactionRepository.getTransaction(id)?.let { transaction ->
                        receiptStorage.deleteReceipt(transaction.receiptPath)
                        clubTransactionRepository.deleteTransaction(transaction)
                    }
                }
            }.onSuccess {
                infoMessage.value = "거래가 삭제되었습니다."
            }.onFailure {
                errorMessage.value = it.message ?: "거래를 삭제하는 중 오류가 발생했습니다."
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun clearInfoMessage() {
        infoMessage.value = null
    }

    private data class LedgerListSnapshot(
        val periodItems: List<ClubTransactionItemUi>,
        val totalIncome: Int,
        val totalExpense: Int,
        val balance: Int,
        val listMode: TransactionListMode,
        val currentYearMonth: YearMonth,
        val searchQuery: String,
        val appliedFilter: TransactionFilterState,
        val activeFilterChips: List<ActiveFilterChipUi>,
        val memberOptions: List<Pair<Long, String>>,
        val accountOptions: List<Pair<Int, String>>
    )

    private data class FormInteraction(
        val isFormVisible: Boolean,
        val form: TransactionFormState,
        val isSaving: Boolean,
        val deleteTargetId: Long?
    )

    private data class MessageInteraction(
        val errorMessage: String?,
        val infoMessage: String?,
        val filterDraft: TransactionFilterState,
        val showFilterSheet: Boolean
    )

    private data class LedgerInteractionSnapshot(
        val isFormVisible: Boolean,
        val form: TransactionFormState,
        val isSaving: Boolean,
        val deleteTargetId: Long?,
        val errorMessage: String?,
        val infoMessage: String?,
        val filterDraft: TransactionFilterState,
        val showFilterSheet: Boolean,
        val unpaidDuesOptions: List<Pair<Long, String>>,
        val duesMemoPreview: String?
    )

    private inline fun updateForm(block: TransactionFormState.() -> TransactionFormState) {
        form.update { it.block() }
    }

    private fun normalizeFormDate(value: String): String {
        val digits = dateStorageToDigits(value)
        return if (digits.length == DATE_DIGIT_LENGTH) dateDigitsToStorage(digits) else value
    }

    private data class ListQueryInputs(
        val mode: TransactionListMode,
        val yearMonth: YearMonth,
        val filter: TransactionFilterState,
        val query: String
    )

    private fun filterTransactionsForInputs(
        inputs: ListQueryInputs,
        all: List<ClubTransactionEntity>
    ): List<ClubTransactionEntity> {
        val (queryStart, queryEnd) = resolveQueryDateRange(
            mode = inputs.mode,
            yearMonth = inputs.yearMonth,
            filter = inputs.filter
        )
        val noteQuery = inputs.query.takeIf { inputs.mode == TransactionListMode.ALL }
        return clubTransactionRepository.filterLoadedTransactions(
            all = all,
            startDate = queryStart,
            endDate = queryEnd,
            targetMemberId = inputs.filter.targetMemberId,
            category = inputs.filter.category,
            minAmount = inputs.filter.minAmount,
            maxAmount = inputs.filter.maxAmount,
            hasReceipt = inputs.filter.hasReceipt,
            noteQuery = noteQuery
        )
    }

    private fun resolveQueryDateRange(
        mode: TransactionListMode,
        yearMonth: YearMonth,
        filter: TransactionFilterState
    ): Pair<String?, String?> {
        val filterStart = filter.startDate?.toFilterDateString()
        val filterEnd = filter.endDate?.toFilterDateString()
        when (mode) {
            TransactionListMode.MONTHLY -> {
                val monthStart = yearMonth.atDay(1).toString()
                val monthEnd = yearMonth.atEndOfMonth().toString()
                val start = listOfNotNull(filterStart, monthStart).maxOrNull()
                val end = listOfNotNull(filterEnd, monthEnd).minOrNull()
                return start to end
            }
            TransactionListMode.YEARLY -> {
                val yearStart = "%04d-01-01".format(yearMonth.year)
                val yearEnd = "%04d-12-31".format(yearMonth.year)
                val start = listOfNotNull(filterStart, yearStart).maxOrNull()
                val end = listOfNotNull(filterEnd, yearEnd).minOrNull()
                return start to end
            }
            TransactionListMode.ALL -> return filterStart to filterEnd
        }
    }

    private fun filterCategoryOptions(): List<String> =
        ClubCategory.incomeCategories + ClubCategory.expenseCategories + listOf(ClubCategory.TRANSFER)
}
