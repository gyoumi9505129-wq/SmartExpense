package com.smartexpense.ui.club.transaction

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.smartexpense.ui.club.components.ClubDeleteConfirmDialog
import com.smartexpense.ui.club.components.ClubMainTopAppBar
import com.smartexpense.ui.club.components.YearMonthPickerDialog
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onSwitchClub: () -> Unit = {},
    bankNotificationOpenTick: Int = 0,
    openEmptyTransactionForm: Boolean = false,
    isScreenActive: Boolean = true,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible(isScreenActive = isScreenActive) {
        viewModel.refreshOnVisible()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showYearMonthPicker by remember { mutableStateOf(false) }

    LaunchedEffect(bankNotificationOpenTick, isAdmin) {
        if (bankNotificationOpenTick > 0 && isAdmin) {
            if (openEmptyTransactionForm) {
                viewModel.openForm()
            } else {
                viewModel.openFormFromBankNotification()
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            // 입출금 시트가 떠 있으면 Snackbar가 가려지므로 Toast로 반드시 알린다.
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (!uiState.isFormVisible) {
                snackbarHostState.showSnackbar(message)
            }
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearInfoMessage()
        }
    }

    uiState.deleteTargetId?.let { targetId ->
        val transaction = uiState.displayTransactions.firstOrNull { it.id == targetId }
        ClubDeleteConfirmDialog(
            title = "입출금 삭제",
            message = "${transaction?.category ?: "해당"} 내역을 삭제할까요?",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDeleteConfirm
        )
    }

    if (showYearMonthPicker) {
        YearMonthPickerDialog(
            initialYearMonth = uiState.currentYearMonth,
            onDismiss = { showYearMonthPicker = false },
            onConfirm = { yearMonth ->
                viewModel.setYearMonth(yearMonth)
                showYearMonthPicker = false
            }
        )
    }

    if (uiState.showFilterSheet) {
        TransactionFilterBottomSheet(
            draft = uiState.filterDraft,
            memberOptions = uiState.memberOptions,
            categoryOptions = uiState.categoryFilterOptions,
            onStartDateChange = viewModel::updateFilterDraftStartDate,
            onEndDateChange = viewModel::updateFilterDraftEndDate,
            onMemberChange = viewModel::updateFilterDraftMember,
            onMemberClear = viewModel::clearFilterDraftMember,
            onCategoryChange = viewModel::updateFilterDraftCategory,
            onCategoryClear = viewModel::clearFilterDraftCategory,
            onMinAmountChange = viewModel::updateFilterDraftMinAmount,
            onMaxAmountChange = viewModel::updateFilterDraftMaxAmount,
            onHasReceiptChange = viewModel::updateFilterDraftHasReceipt,
            onApply = viewModel::applyFilter,
            onReset = viewModel::resetFilterDraft,
            onDismiss = viewModel::dismissFilterSheet
        )
    }

    if (uiState.isFormVisible) {
        TransactionRegistrationBottomSheet(
            form = uiState.form,
            memberOptions = uiState.memberOptions,
            accountOptions = uiState.accountOptions,
            isSaving = uiState.isSaving,
            isReadOnly = !isAdmin,
            onEntryModeChange = viewModel::updateEntryMode,
            onCategoryChange = viewModel::updateCategory,
            onTargetMemberChange = viewModel::updateTargetMemberId,
            onEventSubCategoryChange = viewModel::updateEventSubCategory,
            onAccountChange = viewModel::updateAccountId,
            onTransferToAccountChange = viewModel::updateTransferToAccountId,
            onDateChange = viewModel::updateDate,
            onAmountChange = viewModel::updateAmount,
            onNoteChange = viewModel::updateNote,
            onGalleryPicked = viewModel::setReceiptFromGallery,
            onCameraCaptured = viewModel::setReceiptFromCamera,
            onCreateCameraUri = viewModel::createCameraImageUri,
            onReceiptClear = viewModel::clearReceipt,
            onSave = viewModel::saveTransaction,
            onDismiss = viewModel::dismissForm,
            onConfirmDuesAutoMatch = viewModel::confirmDuesAutoMatch,
            onDismissDuesAutoMatchBanner = viewModel::dismissDuesAutoMatchBanner,
            onClearMemberDropdownFocus = viewModel::clearMemberDropdownFocus,
            unpaidDuesOptions = uiState.unpaidDuesOptions,
            duesMemoPreview = uiState.duesMemoPreview,
            onDuesLinkDetailChange = viewModel::updateDuesLinkDetailId,
            onDuesLedgerPaymentModeChange = viewModel::updateDuesLedgerPaymentMode
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
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = viewModel::openForm,
                    containerColor = TextPrimary,
                    contentColor = BackgroundBlack
                ) {
                    Icon(Icons.Default.Add, contentDescription = "입출금 등록")
                }
            }
        }
    ) { innerPadding ->
        TransactionDashboardScreen(
            uiState = uiState,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onYearMonthClick = { showYearMonthPicker = true },
            onReturnToCurrentMonth = viewModel::goToCurrentMonth,
            onListModeChange = viewModel::setListMode,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onClearSearch = viewModel::clearSearch,
            onOpenFilter = viewModel::openFilterSheet,
            onRemoveFilterChip = viewModel::removeFilterChip,
            onClearAllFilters = viewModel::clearAllFilters,
            onTransactionClick = viewModel::openEditForm,
            onTransactionLongClick = viewModel::requestDelete,
            isEditable = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
