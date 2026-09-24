package com.smartexpense.ui.club.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.ui.club.components.ClubEmptyState
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.club.components.ClubMainTopAppBar
import com.smartexpense.ui.club.components.ClubYearSelector
import com.smartexpense.ui.club.components.YearPickerDialog
import com.smartexpense.ui.theme.AmountDisplayStyle
import com.smartexpense.ui.theme.BalanceNeutral
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.DividerSubtle
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.TransferPurple
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyReportScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onSwitchClub: () -> Unit = {},
    isScreenActive: Boolean = true,
    viewModel: YearlyReportViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible(isScreenActive = isScreenActive) {
        viewModel.refreshOnVisible()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory = uiState.selectedCategory
    var showYearPicker by remember { mutableStateOf(false) }

    if (showYearPicker) {
        YearPickerDialog(
            initialYear = uiState.selectedYear,
            onDismiss = { showYearPicker = false },
            onConfirm = { year ->
                viewModel.setSelectedYear(year)
                showYearPicker = false
            }
        )
    }

    if (uiState.showDetailSheet && selectedCategory != null) {
        CategoryDetailBottomSheet(
            year = uiState.selectedYear,
            category = selectedCategory,
            items = uiState.categoryDetailItems,
            onDismiss = viewModel::dismissCategoryDetail
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        topBar = {
            ClubMainTopAppBar(
                onOpenSettings = onOpenSettings,
                onSwitchClub = onSwitchClub
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "year_selector") {
                ClubYearSelector(
                    yearLabel = uiState.yearLabel,
                    onPreviousYear = viewModel::goToPreviousYear,
                    onNextYear = viewModel::goToNextYear,
                    canGoNext = uiState.canGoNextYear,
                    onYearClick = { showYearPicker = true },
                    showReturnToCurrentYear = uiState.selectedYear != LocalDate.now().year,
                    returnBadgeLabel = LocalDate.now().year.toString(),
                    onReturnToCurrentYear = viewModel::goToCurrentYear
                )
            }

            item(key = "summary_card") {
                YearlySummaryCard(uiState = uiState)
            }

            if (!uiState.isEmptyYear && (uiState.incomePieSlices.isNotEmpty() || uiState.expensePieSlices.isNotEmpty())) {
                item(key = "pie_charts") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDeepGray)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        CategoryPieChartSection(
                            title = "수입 구성",
                            slices = uiState.incomePieSlices
                        )
                        if (uiState.incomePieSlices.isNotEmpty() && uiState.expensePieSlices.isNotEmpty()) {
                            HorizontalDivider(color = DividerSubtle)
                        }
                        CategoryPieChartSection(
                            title = "지출 구성",
                            slices = uiState.expensePieSlices
                        )
                    }
                }
            }

            if (uiState.isEmptyYear) {
                item(key = "empty_year") {
                    ClubEmptyState(
                        title = "${uiState.selectedYear}년 결산 데이터 없음",
                        description = "선택한 연도에 회비 수납·장부 입출금 내역이 아직 없습니다.",
                        hint = "「납부」 또는 「장부」 탭에서 내역을 등록하면 결산이 표시됩니다."
                    )
                }
            }

            item(key = "category_header") {
                Text(
                    text = "카테고리별 내역",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            if (!uiState.isEmptyYear && uiState.categorySummaries.isEmpty()) {
                item(key = "empty") {
                    ClubEmptyState(
                        title = "카테고리별 내역 없음",
                        description = "장부에 등록된 입출금은 있지만, 카테고리별 집계할 데이터가 없습니다."
                    )
                }
            } else if (uiState.categorySummaries.isNotEmpty()) {
                items(uiState.categorySummaries, key = { it.category }) { item ->
                    CategorySummaryRow(
                        item = item,
                        onClick = { viewModel.openCategoryDetail(item.category) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = DividerSubtle,
                        thickness = 0.5.dp
                    )
                }
            }

            if (uiState.transferSummaries.isNotEmpty()) {
                item(key = "transfer_header") {
                    Text(
                        text = "계좌 간 이체 내역",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
                items(uiState.transferSummaries, key = { "transfer-${it.id}" }) { transfer ->
                    TransferSummaryRow(item = transfer)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = DividerSubtle,
                        thickness = 0.5.dp
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun YearlySummaryCard(
    uiState: YearlyReportUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDeepGray)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "${uiState.selectedYear}년 결산",
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary
        )

        SummaryLine(label = "회비 수납", amount = uiState.duesTotal, color = IncomeBlue)
        SummaryLine(label = "장부 수입", amount = uiState.ledgerIncome, color = IncomeBlue)
        SummaryLine(label = "장부 지출", amount = uiState.ledgerExpense, color = ExpenseRed)

        HorizontalDivider(color = DividerSubtle)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "현재 잔액 (${uiState.selectedYear}년 말 기준)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = "%,d원".format(Locale.KOREA, uiState.cumulativeBalance),
                style = AmountDisplayStyle.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = when {
                    uiState.cumulativeBalance > 0 -> IncomeBlue
                    uiState.cumulativeBalance < 0 -> ExpenseRed
                    else -> BalanceNeutral
                }
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    amount: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Text(
            text = "%,d원".format(Locale.KOREA, amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun TransferSummaryRow(
    item: TransferSummaryUi,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.date,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = "%,d원".format(Locale.KOREA, item.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TransferPurple
            )
        }
        Text(
            text = "${item.fromAccountLabel} → ${item.toAccountLabel}",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        item.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun CategorySummaryRow(
    item: CategorySummaryUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.category,
            style = MaterialTheme.typography.titleMedium,
            color = item.color.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: TextPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (item.income > 0) "+%,d원".format(Locale.KOREA, item.income) else "-",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (item.income > 0) item.color else TextSecondary
            )
            Text(
                text = if (item.expense > 0) "−%,d원".format(Locale.KOREA, item.expense) else "-",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (item.expense > 0) ExpenseRed else TextSecondary
            )
        }
    }
}
