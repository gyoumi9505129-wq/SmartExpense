package com.smartexpense.ui.club.event

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.ui.club.components.ClubEmptyState
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.club.components.ClubMainTopAppBar
import com.smartexpense.ui.club.ClubNameViewModel
import com.smartexpense.ui.club.member.toDisplayLabel
import com.smartexpense.ui.theme.AmountDisplayStyle
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.DividerSubtle
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.SurfaceElevated
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventExpenseDashboardScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onSwitchClub: () -> Unit = {},
    isScreenActive: Boolean = true,
    viewModel: EventExpenseViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible(isScreenActive = isScreenActive) {
        viewModel.refreshOnVisible()
    }

    val clubName by hiltViewModel<ClubNameViewModel>().clubName.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currencyFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    var expandedMemberKey by remember { mutableStateOf<String?>(null) }

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
        if (uiState.isEmpty) {
            ClubEmptyState(
                title = "경조사비 내역이 없습니다",
                description = "장부에서 카테고리를 '경조사비'로 등록하면\n회원별 지급 내역이 여기에 집계됩니다.",
                hint = "대상 회원과 세부 항목을 함께 입력해 주세요.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "total_header") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDeepGray)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$clubName 총 경조사비 지출액",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${currencyFormat.format(uiState.totalClubExpense)}원",
                                style = AmountDisplayStyle.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ExpenseRed
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "회원별 지급 누적액",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(
                    items = uiState.memberSummaries,
                    key = { summary -> summary.memberId ?: summary.memberName.hashCode().toLong() }
                ) { summary ->
                    val cardKey = summary.memberId?.toString() ?: summary.memberName
                    val isExpanded = expandedMemberKey == cardKey

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedMemberKey = if (isExpanded) null else cardKey
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDeepGray)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = summary.memberName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        MemberStatusBadge(status = summary.memberStatus)
                                    }
                                    Text(
                                        text = "총 ${currencyFormat.format(summary.totalAmount)}원",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = ExpenseRed,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    contentDescription = if (isExpanded) "접기" else "펼치기",
                                    tint = TextSecondary
                                )
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                ) {
                                    HorizontalDivider(color = DividerSubtle)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    summary.details.forEachIndexed { index, detail ->
                                        if (index > 0) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = detail.eventSubCategory,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = detail.date,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                                detail.note?.takeIf { it.isNotBlank() }?.let { note ->
                                                    Text(
                                                        text = note,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextSecondary,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${currencyFormat.format(detail.amount)}원",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MemberStatusBadge(
    status: MemberStatus,
    modifier: Modifier = Modifier
) {
    val (background, foreground) = when (status) {
        MemberStatus.ACTIVE -> IncomeBlue.copy(alpha = 0.22f) to IncomeBlue
        MemberStatus.DORMANT -> SurfaceElevated to TextSecondary
        MemberStatus.WITHDRAWN -> ExpenseRed.copy(alpha = 0.22f) to ExpenseRed
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = status.toDisplayLabel(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = foreground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
