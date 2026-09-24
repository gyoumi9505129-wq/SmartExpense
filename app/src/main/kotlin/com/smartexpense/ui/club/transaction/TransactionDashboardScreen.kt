package com.smartexpense.ui.club.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.club.components.ClubPeriodNavBar
import com.smartexpense.ui.club.components.rememberSyncedTextFieldValue
import com.smartexpense.ui.theme.DividerSubtle
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import com.smartexpense.ui.theme.TransferPurple
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

@Composable
fun TransactionDashboardScreen(
    uiState: TransactionUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onYearMonthClick: () -> Unit,
    onReturnToCurrentMonth: () -> Unit,
    onListModeChange: (TransactionListMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onRemoveFilterChip: (TransactionFilterKey) -> Unit,
    onClearAllFilters: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onTransactionLongClick: (Long) -> Unit,
    isEditable: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currencyFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val yearMonth = uiState.currentYearMonth
    var isSearchVisible by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun finalizeSearch() {
        focusManager.clearFocus()
        keyboardController?.hide()
        isSearchVisible = false
    }

    LaunchedEffect(uiState.listMode) {
        if (uiState.listMode != TransactionListMode.ALL) {
            isSearchVisible = false
        }
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            searchFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.listMode == TransactionListMode.MONTHLY,
                    onClick = { onListModeChange(TransactionListMode.MONTHLY) },
                    label = { Text("월별") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SurfaceDeepGray,
                        selectedLabelColor = TextPrimary,
                        labelColor = TextSecondary
                    )
                )
                FilterChip(
                    selected = uiState.listMode == TransactionListMode.YEARLY,
                    onClick = { onListModeChange(TransactionListMode.YEARLY) },
                    label = { Text("연도별") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SurfaceDeepGray,
                        selectedLabelColor = TextPrimary,
                        labelColor = TextSecondary
                    )
                )
                FilterChip(
                    selected = uiState.listMode == TransactionListMode.ALL,
                    onClick = { onListModeChange(TransactionListMode.ALL) },
                    label = { Text("전체 내역") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SurfaceDeepGray,
                        selectedLabelColor = TextPrimary,
                        labelColor = TextSecondary
                    )
                )
            }
            if (uiState.listMode == TransactionListMode.ALL) {
                IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "비고 검색",
                        tint = if (isSearchVisible || uiState.searchQuery.isNotBlank()) {
                            TextPrimary
                        } else {
                            TextSecondary
                        }
                    )
                }
            }
            IconButton(onClick = onOpenFilter) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "고급 필터",
                    tint = if (uiState.hasActiveFilters) TextPrimary else TextSecondary
                )
            }
        }

        if (isSearchVisible && uiState.listMode == TransactionListMode.ALL) {
            Spacer(modifier = Modifier.height(8.dp))
            val searchTextFieldState = rememberSyncedTextFieldValue(uiState.searchQuery)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchTextFieldState.value,
                    onValueChange = { updated ->
                        searchTextFieldState.value = updated
                        onSearchQueryChange(updated.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester),
                    placeholder = {
                        Text("비고·메모 검색", color = TextSecondary)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "검색어 지우기",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finalizeSearch() }),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TextSecondary,
                        unfocusedBorderColor = DividerSubtle,
                        cursorColor = TextPrimary
                    )
                )
            }
        }

        if (uiState.activeFilterChips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(uiState.activeFilterChips, key = { "${it.key}-${it.label}" }) { chip ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveFilterChip(chip.key) },
                        label = { Text(chip.label) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "필터 해제",
                                modifier = Modifier.padding(2.dp)
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = SurfaceDeepGray,
                            selectedLabelColor = TextPrimary,
                            selectedTrailingIconColor = TextSecondary
                        )
                    )
                }
                item(key = "clear_all_filters") {
                    AssistChip(
                        onClick = onClearAllFilters,
                        label = { Text("초기화") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }

        if (uiState.isSearchOrFilterActive &&
            (uiState.listMode == TransactionListMode.MONTHLY ||
                uiState.listMode == TransactionListMode.YEARLY)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (uiState.listMode) {
                    TransactionListMode.YEARLY -> "필터가 적용된 연도별 내역입니다."
                    else -> "필터가 적용된 월별 내역입니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (uiState.listMode) {
            TransactionListMode.MONTHLY -> {
                ClubPeriodNavBar(
                    title = "${yearMonth.year}년 ${yearMonth.monthValue}월",
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    onTitleClick = onYearMonthClick,
                    showReturnToCurrent = yearMonth != YearMonth.now(),
                    returnBadgeLabel = YearMonth.now().toReturnBadgeLabel(),
                    onReturnToCurrent = onReturnToCurrentMonth,
                    previousContentDescription = "이전 달",
                    nextContentDescription = "다음 달"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            TransactionListMode.YEARLY -> {
                ClubPeriodNavBar(
                    title = "${yearMonth.year}년",
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    onTitleClick = onYearMonthClick,
                    showReturnToCurrent = yearMonth.year != YearMonth.now().year,
                    returnBadgeLabel = YearMonth.now().toYearOnlyReturnBadgeLabel(),
                    onReturnToCurrent = onReturnToCurrentMonth,
                    previousContentDescription = "이전 연도",
                    nextContentDescription = "다음 연도"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            TransactionListMode.ALL -> Unit
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDeepGray)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SummaryRow(
                    label = when (uiState.listMode) {
                        TransactionListMode.MONTHLY -> "이번 달 수입"
                        TransactionListMode.YEARLY -> "올해 수입"
                        TransactionListMode.ALL -> "총 수입"
                    },
                    amountText = "+ ${currencyFormat.format(uiState.totalIncome)}원",
                    amountColor = IncomeBlue
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(
                    label = when (uiState.listMode) {
                        TransactionListMode.MONTHLY -> "이번 달 지출"
                        TransactionListMode.YEARLY -> "올해 지출"
                        TransactionListMode.ALL -> "총 지출"
                    },
                    amountText = "− ${currencyFormat.format(uiState.totalExpense)}원",
                    amountColor = ExpenseRed
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = DividerSubtle
                )
                SummaryRow(
                    label = "현재 잔액",
                    amountText = "${currencyFormat.format(uiState.balance)}원",
                    amountColor = if (uiState.balance >= 0) TextPrimary else ExpenseRed,
                    labelStyle = MaterialTheme.typography.titleMedium,
                    amountStyle = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "장부 전체 수입 − 지출 (이월금 거래 포함, 선택한 월과 무관)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "거래 내역",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        TransactionTableHeader(showBalance = uiState.listMode == TransactionListMode.ALL)

        if (uiState.isEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        uiState.isSearchOrFilterActive -> "조건에 맞는 내역이 없습니다."
                        uiState.listMode == TransactionListMode.ALL -> "입출금 내역이 없습니다."
                        uiState.listMode == TransactionListMode.YEARLY -> "이번 해 입출금 내역이 없습니다."
                        else -> "이번 달 입출금 내역이 없습니다."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.displayTransactions, key = { it.id }) { tx ->
                    TransactionTableRow(
                        transaction = tx,
                        currencyFormat = currencyFormat,
                        showBalance = uiState.listMode == TransactionListMode.ALL,
                        isEditable = isEditable,
                        onClick = { onTransactionClick(tx.id) },
                        onLongClick = { onTransactionLongClick(tx.id) }
                    )
                    HorizontalDivider(color = DividerSubtle, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amountText: String,
    amountColor: androidx.compose.ui.graphics.Color,
    labelStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    amountStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = labelStyle, color = TextPrimary)
        Text(
            text = amountText,
            style = amountStyle,
            fontWeight = FontWeight.Bold,
            color = amountColor
        )
    }
}

@Composable
private fun TransactionTableHeader(
    showBalance: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "날짜",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = "분류",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = "금액",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
        if (showBalance) {
            Text(
                text = "잔고",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        Text(
            text = "비고",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(if (showBalance) 1.2f else 1.3f),
            textAlign = TextAlign.Start
        )
    }
    HorizontalDivider(color = DividerSubtle, thickness = 0.5.dp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionTableRow(
    transaction: ClubTransactionItemUi,
    currencyFormat: NumberFormat,
    showBalance: Boolean,
    isEditable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = transaction.typeLabel == "수입"
    val isTransfer = transaction.isTransfer
    val amountColor = when {
        isTransfer -> TransferPurple
        isIncome -> IncomeBlue
        else -> ExpenseRed
    }
    val sign = when {
        isTransfer -> "↔"
        isIncome -> "+"
        else -> "−"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = transaction.date.toFullDateLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1.1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.weight(0.9f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transaction.category,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (transaction.hasReceipt) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "증빙 첨부",
                    tint = TextSecondary,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        Text(
            text = "$sign${currencyFormat.format(transaction.amount)}원",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = amountColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showBalance) {
            Text(
                text = transaction.balanceAfter?.let { "${currencyFormat.format(it)}원" } ?: "−",
                style = MaterialTheme.typography.bodySmall,
                color = if (transaction.balanceAfter != null) TextPrimary else TextSecondary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = transaction.note.orEmpty().ifBlank { "−" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (transaction.note.isNullOrBlank()) TextSecondary else TextPrimary,
            modifier = Modifier
                .weight(if (showBalance) 1.2f else 1.3f)
                .padding(start = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
