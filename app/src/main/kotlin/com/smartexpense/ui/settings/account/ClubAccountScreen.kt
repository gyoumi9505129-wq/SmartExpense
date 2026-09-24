package com.smartexpense.ui.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.local.entity.club.ClubAccountEntity
import com.smartexpense.data.model.bank.BankAppMaster
import com.smartexpense.ui.club.components.ClubDropdownField
import com.smartexpense.ui.club.components.ClubEmptyState
import com.smartexpense.ui.club.components.ClubTextField
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.components.rememberClearInputOverlayAction
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import com.smartexpense.ui.club.ClubNameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubAccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClubAccountViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible {
        viewModel.refreshOnVisible()
    }

    val clubName by hiltViewModel<ClubNameViewModel>().clubName.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val bankOptions = BankAppMaster.bankNames.map { it to it }

    val safeDismiss = rememberClearInputOverlayAction(onAfterClear = viewModel::dismissAddDialog)
    val confirmSave = remember(viewModel, focusManager, keyboardController) {
        {
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.saveAccount()
        }
    }

    if (uiState.showAddDialog) {
        ClubAccountAddDialog(
            form = uiState.form,
            bankOptions = bankOptions,
            isSaving = uiState.isSaving,
            saveError = uiState.saveError,
            onBankNameChange = viewModel::updateBankName,
            onAccountNumberChange = viewModel::updateAccountNumber,
            onHolderNameChange = viewModel::updateHolderName,
            onSave = confirmSave,
            onDismiss = safeDismiss
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "$clubName 계좌 관리",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundBlack,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = viewModel::openAddDialog,
                    containerColor = TextPrimary,
                    contentColor = BackgroundBlack
                ) {
                    Icon(Icons.Default.Add, contentDescription = "계좌 추가")
                }
            }
        }
    ) { innerPadding ->
        if (uiState.accounts.isEmpty()) {
            ClubEmptyState(
                title = "등록된 계좌가 없습니다",
                description = "우측 하단 + 버튼으로\n회비 입금용 통장을 등록해 주세요.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.accounts, key = { it.id }) { account ->
                    ClubAccountCard(
                        account = account,
                        showDelete = isAdmin,
                        onDelete = { viewModel.deleteAccount(account) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun ClubAccountCard(
    account: ClubAccountEntity,
    showDelete: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    // 은행명 + 계좌번호 + 예금주를 한 줄로 복사
    val copyText = "${account.bankName} ${account.accountNumber} (${account.holderName})"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDeepGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = account.bankName,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Text(
                    text = account.accountNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "예금주: ${account.holderName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
            // 복사 버튼 (모든 사용자에게 표시)
            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(copyText))
            }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "계좌 복사",
                    tint = IncomeBlue
                )
            }
            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "계좌 삭제",
                        tint = ExpenseRed
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClubAccountAddDialog(
    form: ClubAccountFormState,
    bankOptions: List<Pair<String, String>>,
    isSaving: Boolean,
    saveError: String?,
    onBankNameChange: (String) -> Unit,
    onAccountNumberChange: (String) -> Unit,
    onHolderNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequesters = remember { List(2) { FocusRequester() } }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = { Text("계좌 정보 입력", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ClubDropdownField(
                    label = "은행명",
                    options = bankOptions,
                    selected = form.bankName.takeIf { it.isNotBlank() },
                    onSelected = onBankNameChange,
                    placeholder = "은행 선택"
                )
                ClubTextField(
                    value = form.accountNumber,
                    onValueChange = onAccountNumberChange,
                    label = "계좌번호",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusRequesters[1].requestFocus() },
                    focusRequester = focusRequesters[0]
                )
                ClubTextField(
                    value = form.holderName,
                    onValueChange = onHolderNameChange,
                    label = "예금주",
                    imeAction = ImeAction.Done,
                    focusRequester = focusRequesters[1]
                )
                saveError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseRed
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = form.canSave && !isSaving) {
                Text(if (isSaving) "저장 중…" else "확인", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = SurfaceDeepGray
    )
}
