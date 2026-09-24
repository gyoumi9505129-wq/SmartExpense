package com.smartexpense.ui.club.member

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.club.components.ClubDeleteConfirmDialog
import com.smartexpense.ui.club.components.ClubEmptyState
import com.smartexpense.ui.club.components.ClubMainTopAppBar
import com.smartexpense.ui.club.components.formatPhoneDigits
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.SurfaceElevated
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemberManagementScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onSwitchClub: () -> Unit = {},
    isScreenActive: Boolean = true,
    viewModel: MemberManagementViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible(isScreenActive = isScreenActive) {
        viewModel.refreshOnVisible()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    uiState.deleteTargetId?.let { targetId ->
        val memberName = uiState.members.firstOrNull { it.id == targetId }?.name ?: "회원"
        ClubDeleteConfirmDialog(
            title = "강제 탈퇴",
            message = "$memberName 회원을 강제 탈퇴할까요? 회원 정보가 삭제되며, 해당 회원은 「모임 찾기」에서 다시 가입을 요청할 수 있습니다.",
            confirmLabel = "강제 탈퇴",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDeleteConfirm
        )
    }

    if (uiState.isFormVisible) {
        MemberRegistrationBottomSheet(
            form = uiState.form,
            isSaving = uiState.isSaving,
            isReadOnly = !isAdmin,
            onNameChange = viewModel::updateName,
            onPhoneChange = viewModel::updatePhone,
            onEmailChange = viewModel::updateEmail,
            onJoinDateChange = viewModel::updateJoinDate,
            onBirthDateChange = viewModel::updateBirthDate,
            onIsLunarBirthChange = viewModel::updateIsLunarBirth,
            onResidenceRegionChange = viewModel::updateResidenceRegion,
            onAddressChange = viewModel::updateAddress,
            onDetailAddressChange = viewModel::updateDetailAddress,
            onRoleChange = viewModel::updateRole,
            onStatusChange = viewModel::updateStatus,
            onSuspensionDateChange = viewModel::updateSuspensionDate,
            onStatusChangeReasonChange = viewModel::updateStatusChangeReason,
            onSave = viewModel::saveMember,
            onDismiss = viewModel::dismissForm,
            showForceWithdraw = isAdmin && uiState.form.isEditing,
            onForceWithdraw = {
                uiState.form.editingId?.let(viewModel::requestDelete)
            }
        )
    }

    uiState.historyTarget?.let { target ->
        MemberHistoryBottomSheet(
            memberName = target.memberName,
            historyItems = uiState.historyItems,
            onDismiss = viewModel::dismissHistory
        )
    }

    if (uiState.showAdmissionFeeDialog) {
        AdmissionFeeBottomSheet(
            breakdown = uiState.admissionFeeBreakdown,
            onDismiss = viewModel::dismissAdmissionFeeDialog
        )
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.dismissAdmissionFeeDialog() }
    }

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ClubMainTopAppBar(
                    onOpenSettings = onOpenSettings,
                    onSwitchClub = {
                        viewModel.dismissAdmissionFeeDialog()
                        onSwitchClub()
                    },
                    additionalActions = {
                        IconButton(onClick = viewModel::openAdmissionFeeDialog) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "입회비 계산",
                                tint = TextSecondary
                            )
                        }
                    }
                )
                Text(
                    text = "회원 (${uiState.memberCount}명)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                )
                MemberStatusFilterRow(
                    uiState = uiState,
                    onSelect = viewModel::selectStatusFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = viewModel::openForm,
                    containerColor = TextPrimary,
                    contentColor = BackgroundBlack
                ) {
                    Icon(Icons.Default.Add, contentDescription = "회원 등록")
                }
            }
        }
    ) { innerPadding ->
        when {
            uiState.memberCount == 0 -> {
                ClubEmptyState(
                    title = "등록된 회원이 없습니다",
                    description = "우측 하단 + 버튼으로 회원을 등록하세요.",
                    hint = "항목을 눌러 수정하고, 길게 눌러 강제 탈퇴할 수 있습니다.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            uiState.members.isEmpty() -> {
                ClubEmptyState(
                    title = "해당 상태의 회원이 없습니다",
                    description = "다른 상태 필터를 선택해 보세요.",
                    hint = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.members, key = { it.id }) { member ->
                        MemberListCard(
                            member = member,
                            isEditable = isAdmin,
                            onClick = { viewModel.openEditForm(member.id) },
                            onLongClick = { viewModel.requestDelete(member.id) },
                            onHistoryClick = { viewModel.openHistory(member.id, member.name) }
                        )
                    }
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberStatusFilterRow(
    uiState: MemberManagementUiState,
    onSelect: (MemberStatusFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = listOf(
        MemberStatusFilter.ALL to uiState.memberCount,
        MemberStatusFilter.ACTIVE to uiState.activeCount,
        MemberStatusFilter.DORMANT to uiState.dormantCount,
        MemberStatusFilter.WITHDRAWN to uiState.withdrawnCount
    )
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips, key = { it.first.name }) { (filter, count) ->
            FilterChip(
                selected = uiState.statusFilter == filter,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = filter.toChipLabel(count),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SurfaceDeepGray,
                    selectedLabelColor = TextPrimary,
                    containerColor = SurfaceElevated,
                    labelColor = TextSecondary
                )
            )
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberListCard(
    member: MemberListItemUi,
    isEditable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedPhone = formatPhoneDigits(member.phone).ifBlank { "연락처 없음" }
    val joinDateLabel = member.joinDate.ifBlank { "-" }
    val statusBadgeColors = when (member.status) {
        MemberStatus.ACTIVE -> IncomeBlue.copy(alpha = 0.22f) to IncomeBlue
        MemberStatus.DORMANT -> SurfaceElevated to TextSecondary
        MemberStatus.WITHDRAWN -> ExpenseRed.copy(alpha = 0.22f) to ExpenseRed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDeepGray)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (isEditable) onLongClick else null
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Surface(
                        color = statusBadgeColors.first,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = member.statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = statusBadgeColors.second,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = member.roleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "가입 $joinDateLabel · $formattedPhone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (member.residenceRegion.isNotBlank()) {
                    Text(
                        text = "거주 ${member.residenceRegion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "이력 보기",
                    tint = TextSecondary
                )
            }
        }
    }
}
