package com.smartexpense.ui.club.member

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.local.entity.club.MemberStatusHistoryEntity
import com.smartexpense.domain.admission.AdmissionFeeBreakdown
import com.smartexpense.data.repository.club.AdmissionFeeRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.repository.club.MemberRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.ui.club.toEntity
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import com.smartexpense.ui.common.observeIsAdmin
import com.smartexpense.ui.club.toFormState
import com.smartexpense.ui.club.toHistoryItemUi
import com.smartexpense.ui.common.observeSelectedClubChanges
import com.smartexpense.ui.club.toListItemUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemberManagementViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val admissionFeeRepository: AdmissionFeeRepository,
    private val selectedClubRepository: SelectedClubRepository,
    meetingRoleRepository: MeetingRoleRepository,
    ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val authRefreshGuard: AuthRefreshGuard
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = observeIsAdmin(meetingRoleRepository)

    private val refreshTrigger = ViewModelRefreshTrigger()
    private val koreanNameCollator = Collator.getInstance(Locale.KOREA)

    private val isFormVisible = MutableStateFlow(false)
    private val form = MutableStateFlow(MemberFormState())
    private val isSaving = MutableStateFlow(false)
    private val deleteTargetId = MutableStateFlow<Long?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val toastMessage = MutableStateFlow<String?>(null)
    private val historyTarget = MutableStateFlow<MemberHistoryTarget?>(null)
    private val showAdmissionFeeDialog = MutableStateFlow(false)
    private val statusFilter = MutableStateFlow(MemberStatusFilter.ACTIVE)

    private val members: StateFlow<List<MemberEntity>> = memberRepository.observeAllMembers()
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
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    init {
        observeSelectedClubChanges(selectedClubRepository) {
            isFormVisible.value = false
            form.value = MemberFormState()
            deleteTargetId.value = null
            historyTarget.value = null
            showAdmissionFeeDialog.value = false
            statusFilter.value = MemberStatusFilter.ACTIVE
        }
        viewModelScope.launch {
            selectedClubRepository.selectedClubId.collect { clubId ->
                if (clubId == null) {
                    showAdmissionFeeDialog.value = false
                }
            }
        }
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
    }

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshTrigger.refresh()
    }

    private val admissionFeeBreakdown = admissionFeeRepository.observeBreakdown()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val historyItems: StateFlow<List<MemberHistoryItemUi>> = historyTarget
        .flatMapLatest { target ->
            if (target == null) {
                flowOf(emptyList())
            } else {
                memberRepository.observeStatusHistory(target.memberId)
                    .map { history -> history.map { it.toHistoryItemUi() } }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val formSlice = combine(
        isFormVisible,
        form,
        isSaving,
        deleteTargetId
    ) { visible, formState, saving, deleteId ->
        FormSlice(visible, formState, saving, deleteId)
    }

    private val messageSlice = combine(
        errorMessage,
        toastMessage,
        historyTarget,
        historyItems
    ) { error, toast, target, items ->
        MessageSlice(error, toast, target, items)
    }

    val uiState: StateFlow<MemberManagementUiState> = combine(
        refreshTrigger.tick,
        members,
        statusFilter,
        formSlice,
        combine(messageSlice, showAdmissionFeeDialog, admissionFeeBreakdown) { messages, showFee, breakdown ->
            ExtraSlice(messages, showFee, breakdown)
        }
    ) { _, memberList, filter, forms, extra ->
        val allItems = memberList.map { it.toListItemUi() }
        val filtered = allItems.filter { filter.matches(it.status) }
        MemberManagementUiState(
            members = filtered,
            memberCount = allItems.size,
            activeCount = allItems.count { it.status == MemberStatus.ACTIVE },
            dormantCount = allItems.count { it.status == MemberStatus.DORMANT },
            withdrawnCount = allItems.count { it.status == MemberStatus.WITHDRAWN },
            statusFilter = filter,
            isFormVisible = forms.isFormVisible,
            form = forms.form,
            isSaving = forms.isSaving,
            deleteTargetId = forms.deleteTargetId,
            errorMessage = extra.messages.errorMessage,
            toastMessage = extra.messages.toastMessage,
            historyTarget = extra.messages.historyTarget,
            historyItems = extra.messages.historyItems,
            showAdmissionFeeDialog = extra.showAdmissionFeeDialog,
            admissionFeeBreakdown = extra.admissionFeeBreakdown
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemberManagementUiState()
    )

    fun selectStatusFilter(filter: MemberStatusFilter) {
        statusFilter.value = filter
    }

    fun openForm() {
        if (!isAdmin.value) {
            errorMessage.value = "회원을 등록할 권한이 없습니다."
            return
        }
        form.value = MemberFormState()
        isFormVisible.value = true
    }

    fun openEditForm(id: Long) {
        val cached = members.value.find { it.id == id }
        if (cached != null) {
            presentEditForm(cached)
            return
        }
        viewModelScope.launch {
            val member = runCatching { memberRepository.getMember(id) }
                .onFailure { error ->
                    errorMessage.value = error.message ?: "회원 정보를 불러오지 못했습니다."
                }
                .getOrNull()
            if (member == null) {
                if (errorMessage.value == null) {
                    errorMessage.value = "회원 정보를 찾을 수 없습니다. 화면을 새로고침해 주세요."
                }
                return@launch
            }
            presentEditForm(member)
        }
    }

    fun dismissForm() {
        isFormVisible.value = false
        form.value = MemberFormState()
    }

    fun updateName(value: String) = updateForm { copy(name = value) }
    fun updatePhone(value: String) = updateForm { copy(phone = value) }
    fun updateEmail(value: String) = updateForm { copy(email = value) }
    fun updateJoinDate(value: String) = updateForm { copy(joinDate = value) }
    fun updateBirthDate(value: String) = updateForm { copy(birthDate = value) }
    fun updateResidenceRegion(value: String) = updateForm { copy(residenceRegion = value) }
    fun updateAddress(value: String) = updateForm { copy(address = value) }
    fun updateDetailAddress(value: String) = updateForm { copy(detailAddress = value) }
    fun updateIsLunarBirth(value: Boolean) = updateForm { copy(isLunarBirth = value) }
    fun updateSuspensionDate(value: String) = updateForm { copy(suspensionDate = value) }
    fun updateStatusChangeReason(value: String) = updateForm { copy(statusChangeReason = value) }
    fun updateRole(value: MemberRole) = updateForm { copy(role = value) }
    fun updateStatus(value: MemberStatus) = updateForm {
        copy(
            status = value,
            suspensionDate = if (value == MemberStatus.ACTIVE) "" else suspensionDate,
            statusChangeReason = if (originalStatus == value) "" else statusChangeReason
        )
    }

    fun saveMember() {
        if (!isAdmin.value) {
            errorMessage.value = "회원을 수정할 권한이 없습니다."
            return
        }
        if (form.value.isEditing) {
            updateMember()
        } else {
            insertMember()
        }
    }

    fun insertMember() {
        if (!isAdmin.value) {
            errorMessage.value = "회원을 등록할 권한이 없습니다."
            return
        }
        val formState = form.value
        if (!isFormValid(formState)) {
            toastMessage.value = "입력값을 확인해 주세요."
            return
        }

        viewModelScope.launch {
            isSaving.value = true
            try {
                memberRepository.insertMember(formState.toEntity())
                dismissForm()
                toastMessage.value = "회원을 등록했습니다."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "회원 등록에 실패했습니다."
            } finally {
                isSaving.value = false
            }
        }
    }

    private fun updateMember() {
        if (!isAdmin.value) {
            errorMessage.value = "회원을 수정할 권한이 없습니다."
            return
        }
        val formState = form.value
        if (!isFormValid(formState)) {
            toastMessage.value = "입력값을 확인해 주세요."
            return
        }

        val memberId = formState.editingId ?: return

        viewModelScope.launch {
            isSaving.value = true
            try {
                val existing = findMember(memberId)
                if (existing == null) {
                    errorMessage.value = "회원 정보를 찾을 수 없습니다. 화면을 새로고침해 주세요."
                    return@launch
                }
                val updated = formState.toEntity()
                val statusHistory = if (existing.status != updated.status) {
                    MemberStatusHistoryEntity(
                        clubId = 0,
                        memberId = memberId,
                        oldStatus = existing.status.name,
                        newStatus = updated.status.name,
                        changeDate = LocalDateTime.now().format(STATUS_CHANGE_FORMAT),
                        reason = formState.statusChangeReason.trim().takeIf { it.isNotBlank() },
                        suspensionDate = updated.suspensionDate?.takeIf { it.isNotBlank() }
                    )
                } else {
                    null
                }
                memberRepository.updateMemberWithStatusHistory(updated, statusHistory)
                dismissForm()
                toastMessage.value = "회원 정보를 수정했습니다."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "회원 수정에 실패했습니다."
            } finally {
                isSaving.value = false
            }
        }
    }

    fun requestDelete(id: Long) {
        if (!isAdmin.value) return
        deleteTargetId.value = id
    }

    fun dismissDeleteConfirm() {
        deleteTargetId.value = null
    }

    fun openHistory(memberId: Long, memberName: String) {
        historyTarget.value = MemberHistoryTarget(memberId, memberName)
    }

    fun dismissHistory() {
        historyTarget.value = null
    }

    fun openAdmissionFeeDialog() {
        showAdmissionFeeDialog.value = true
    }

    fun dismissAdmissionFeeDialog() {
        showAdmissionFeeDialog.value = false
    }

    fun confirmDelete() {
        if (!isAdmin.value) return
        val id = deleteTargetId.value ?: return
        viewModelScope.launch {
            val member = findMember(id)
            if (member == null) {
                errorMessage.value = "삭제할 회원을 찾을 수 없습니다. 화면을 새로고침해 주세요."
                deleteTargetId.value = null
                return@launch
            }
            try {
                memberRepository.deleteMember(member)
                deleteTargetId.value = null
                if (form.value.editingId == id) {
                    isFormVisible.value = false
                    form.value = MemberFormState()
                }
                toastMessage.value = "강제 탈퇴했습니다. 해당 회원은 「모임 찾기」에서 다시 가입을 요청할 수 있습니다."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "강제 탈퇴에 실패했습니다."
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun clearToast() {
        toastMessage.value = null
    }

    private fun presentEditForm(member: MemberEntity) {
        form.value = member.toFormState()
        isFormVisible.value = true
    }

    private suspend fun findMember(id: Long): MemberEntity? =
        members.value.find { it.id == id } ?: memberRepository.getMember(id)

    private fun isFormValid(formState: MemberFormState): Boolean =
        formState.name.isNotBlank() &&
            formState.phone.isNotBlank() &&
            formState.emailError == null

    private inline fun updateForm(block: MemberFormState.() -> MemberFormState) {
        form.update { it.block() }
    }

    companion object {
        private val STATUS_CHANGE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

private data class FormSlice(
    val isFormVisible: Boolean,
    val form: MemberFormState,
    val isSaving: Boolean,
    val deleteTargetId: Long?
)

private data class MessageSlice(
    val errorMessage: String?,
    val toastMessage: String?,
    val historyTarget: MemberHistoryTarget?,
    val historyItems: List<MemberHistoryItemUi>
)

private data class ExtraSlice(
    val messages: MessageSlice,
    val showAdmissionFeeDialog: Boolean,
    val admissionFeeBreakdown: AdmissionFeeBreakdown?
)
