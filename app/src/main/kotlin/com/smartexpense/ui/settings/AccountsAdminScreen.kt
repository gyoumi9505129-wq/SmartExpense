package com.smartexpense.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.ui.club.components.ClubMainTopAppBar
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.InterestGold
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

/**
 * 바텀 네비게이션 「관리」 탭. 시스템관리자 전용.
 * 웹 AdminAccountsPanel과 동일하게 「승인 대기」 + 「가입 계정」을 제공합니다.
 */
@Composable
fun AccountsAdminScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onSwitchClub: () -> Unit = {},
    isScreenActive: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible(isScreenActive = isScreenActive) {
        viewModel.refreshAccounts()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbarMessage()
        }
    }

    if (uiState.showPurgeAccountDialog) {
        SettingsActionConfirmDialog(
            title = "계정 강퇴/삭제",
            message = "「${uiState.purgeTargetLabel}」계정을 강퇴·삭제할까요?\n" +
                "접근·가입요청·프로필을 지워 재가입 시 중복이 생기지 않습니다.\nUID: ${uiState.purgeTargetUid.orEmpty()}",
            confirmLabel = if (uiState.isPurgingAccount) "삭제 중…" else "삭제",
            onConfirm = viewModel::confirmPurgeAccount,
            onDismiss = viewModel::dismissPurgeAccountDialog
        )
    }

    if (uiState.showKeepOnlyUidDialog) {
        SettingsActionConfirmDialog(
            title = "이 UID만 남기기",
            message = "이 UID만 남기고 같은 이메일의 다른 계정·요청·프로필을 삭제할까요?\n" +
                "UID: ${uiState.keepOnlyUidTarget.orEmpty()}",
            confirmLabel = if (uiState.isCleaningDuplicateAccounts) "정리 중…" else "남기기",
            onConfirm = viewModel::confirmKeepOnlyUid,
            onDismiss = viewModel::dismissKeepOnlyUidDialog
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ClubMainTopAppBar(
                onOpenSettings = onOpenSettings,
                onSwitchClub = onSwitchClub
            )
        }
    ) { innerPadding ->
        if (!uiState.isSystemAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "시스템관리자만 접근할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                PendingJoinSection(
                    uiState = uiState,
                    onRefresh = viewModel::refreshAccounts,
                    onApprove = viewModel::approvePendingJoinRequest,
                    onReject = viewModel::rejectPendingJoinRequest
                )
            }
            item {
                AccountManagementSection(
                    uiState = uiState,
                    onRefresh = viewModel::refreshAccounts,
                    onPurge = viewModel::requestPurgeAccount,
                    onKeepOnlyUid = viewModel::requestKeepOnlyUid
                )
            }
        }
    }
}

/** 웹 AdminAccountsPanel 「승인 대기」 섹션 대응. */
@Composable
private fun PendingJoinSection(
    uiState: SettingsUiState,
    onRefresh: () -> Unit,
    onApprove: (meetingId: String, requestId: String) -> Unit,
    onReject: (meetingId: String, requestId: String) -> Unit
) {
    SettingsSection(title = "승인 대기") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "승인 대기 (${uiState.pendingJoinRequests.size})",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                onClick = onRefresh,
                enabled = !uiState.isLoadingAccounts && !uiState.isDecidingJoinRequest
            ) {
                Text(
                    text = if (uiState.isLoadingAccounts) "불러오는 중…" else "새로고침",
                    color = TextSecondary
                )
            }
        }
        SettingsItemDivider()
        if (uiState.pendingJoinRequests.isEmpty() && !uiState.isLoadingAccounts) {
            Text(
                text = "대기 중인 요청이 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        uiState.pendingJoinRequests.forEachIndexed { index, request ->
            if (index > 0) SettingsItemDivider()
            PendingJoinRow(
                request = request,
                busy = uiState.isDecidingJoinRequest,
                onApprove = { onApprove(request.meetingId, request.requestId) },
                onReject = { onReject(request.meetingId, request.requestId) }
            )
        }
    }
}

@Composable
private fun PendingJoinRow(
    request: PendingJoinRequestUiModel,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = request.displayName.ifBlank { "(이름 없음)" },
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = request.email,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        if (request.message.isNotBlank()) {
            Text(
                text = request.message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Button(
                onClick = onApprove,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = IncomeBlue.copy(alpha = 0.15f),
                    contentColor = IncomeBlue,
                    disabledContainerColor = IncomeBlue.copy(alpha = 0.08f),
                    disabledContentColor = IncomeBlue.copy(alpha = 0.4f)
                )
            ) {
                Text("승인", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onReject,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextSecondary.copy(alpha = 0.12f),
                    contentColor = TextSecondary,
                    disabledContainerColor = TextSecondary.copy(alpha = 0.06f),
                    disabledContentColor = TextSecondary.copy(alpha = 0.4f)
                )
            ) {
                Text("거절", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * 가입 계정 목록 + 강퇴/삭제. 웹 AdminAccountsPanel의 「가입 계정」 섹션 대응.
 */
@Composable
private fun AccountManagementSection(
    uiState: SettingsUiState,
    onRefresh: () -> Unit,
    onPurge: (uid: String, label: String) -> Unit,
    onKeepOnlyUid: (uid: String, label: String) -> Unit
) {
    SettingsSection(title = "계정 관리") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "가입 계정 (${uiState.accountRows.size})",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = onRefresh, enabled = !uiState.isLoadingAccounts) {
                Text(
                    text = if (uiState.isLoadingAccounts) "불러오는 중…" else "새로고침",
                    color = TextSecondary
                )
            }
        }
        SettingsItemDivider()
        uiState.accountsError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = ExpenseRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (uiState.accountRows.isEmpty() && !uiState.isLoadingAccounts && uiState.accountsError == null) {
            Text(
                text = "가입된 계정이 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        uiState.accountRows.forEachIndexed { index, account ->
            if (index > 0) SettingsItemDivider()
            AccountRow(
                account = account,
                busy = uiState.isPurgingAccount || uiState.isCleaningDuplicateAccounts,
                onPurge = { onPurge(account.uid, account.label) },
                onKeepOnlyUid = { onKeepOnlyUid(account.uid, account.label) }
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountRowUiModel,
    busy: Boolean,
    onPurge: () -> Unit,
    onKeepOnlyUid: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = account.displayName.ifBlank { "(이름 없음)" },
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            if (account.isOwner) {
                Text(
                    text = "  모임관리자",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (account.isTreasurer) {
                Text(
                    text = "  총무",
                    style = MaterialTheme.typography.bodySmall,
                    color = IncomeBlue
                )
            }
        }
        Text(
            text = account.email,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = "UID ${account.uid}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary.copy(alpha = 0.6f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            if (account.isDuplicateEmail) {
                Button(
                    onClick = onKeepOnlyUid,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InterestGold.copy(alpha = 0.15f),
                        contentColor = InterestGold,
                        disabledContainerColor = InterestGold.copy(alpha = 0.08f),
                        disabledContentColor = InterestGold.copy(alpha = 0.4f)
                    )
                ) {
                    Text("이 UID만 남기기", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!account.isOwner) {
                Button(
                    onClick = onPurge,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed.copy(alpha = 0.15f),
                        contentColor = ExpenseRed,
                        disabledContainerColor = ExpenseRed.copy(alpha = 0.08f),
                        disabledContentColor = ExpenseRed.copy(alpha = 0.4f)
                    )
                ) {
                    Text("강퇴/삭제", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
