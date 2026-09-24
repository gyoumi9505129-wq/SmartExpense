package com.smartexpense.ui.club.dues

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.ui.club.components.ClubDropdownField
import com.smartexpense.ui.club.components.ClubEmptyState
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.club.components.ClubMainTopAppBar
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.DividerSubtle
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import com.smartexpense.ui.club.ClubNameViewModel
import com.smartexpense.util.DuesReminderMessenger
import java.util.Locale.KOREA

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuesStatusScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onSwitchClub: () -> Unit = {},
    isScreenActive: Boolean = true,
    statusViewModel: DuesStatusViewModel = hiltViewModel(),
    paymentViewModel: DuesPaymentViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible(isScreenActive = isScreenActive) {
        statusViewModel.refreshOnVisible()
        paymentViewModel.refreshOnVisible()
    }

    val uiState by statusViewModel.uiState.collectAsStateWithLifecycle()
    val isAdmin by statusViewModel.isAdmin.collectAsStateWithLifecycle()
    val members by statusViewModel.members.collectAsStateWithLifecycle()
    val paymentUiState by paymentViewModel.uiState.collectAsStateWithLifecycle()
    val paymentIsAdmin by paymentViewModel.isAdmin.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canRegisterPayment = isAdmin && paymentIsAdmin
    val yearFilterOptions: List<Pair<Int?, String>> =
        listOf(null to "전체") + duesYearOptions.map { (year, label) -> year to label }
    val memberFilterOptions: List<Pair<Long?, String>> =
        listOf(null to "전체") + members.map { it.id to it.name }

    LaunchedEffect(paymentUiState.errorMessage) {
        paymentUiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            paymentViewModel.clearError()
        }
    }

    val detailMessage by statusViewModel.detailMessage.collectAsStateWithLifecycle()
    LaunchedEffect(detailMessage) {
        detailMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            statusViewModel.clearDetailMessage()
        }
    }

    if (uiState.showDetailSheet) {
        val detailMemberName = uiState.detailMemberName
            ?: uiState.memberRows.firstOrNull { it.memberId == uiState.detailMemberId }?.memberName
            ?: "회원"
        DuesDetailBottomSheet(
            memberName = detailMemberName,
            items = uiState.detailDraftItems,
            isSaving = uiState.isSavingDetail,
            isEditable = isAdmin,
            onItemClick = { item -> statusViewModel.openDetailEdit(item.detailId) },
            onSave = statusViewModel::saveMemberDetail,
            onDismiss = statusViewModel::dismissMemberDetail
        )
    }

    uiState.editingDetailId?.let { editingId ->
        uiState.detailDraftItems.firstOrNull { it.detailId == editingId }?.let { item ->
            DuesEditDialog(
                item = item,
                isReadOnly = !isAdmin,
                onConfirm = statusViewModel::confirmDetailEdit,
                onDismiss = statusViewModel::dismissDetailEdit
            )
        }
    }

    if (paymentUiState.isFormVisible) {
        DuesPaymentBottomSheet(
            form = paymentUiState.form,
            memberOptions = paymentUiState.memberOptions,
            isSaving = paymentUiState.isSaving,
            onMemberChange = paymentViewModel::updateMemberId,
            onYearChange = paymentViewModel::updateYear,
            onTotalTargetAmountChange = paymentViewModel::updateTotalTargetAmount,
            onPaymentMethodChange = paymentViewModel::updatePaymentMethod,
            onDetailAmountChange = paymentViewModel::updateDetailAmount,
            onDetailAdditionalPayAmountChange = paymentViewModel::updateDetailAdditionalPayAmount,
            onDetailPayDateChange = paymentViewModel::updateDetailPayDate,
            onDetailPaidChange = paymentViewModel::toggleDetailPaid,
            onDetailExcludedChange = paymentViewModel::toggleDetailExcluded,
            onAddMonthlyDetail = paymentViewModel::addMonthlyDetail,
            onRemoveMonthlyDetail = paymentViewModel::removeMonthlyDetail,
            onMonthlyMonthChange = paymentViewModel::updateMonthlyMonth,
            onSave = paymentViewModel::saveYearlyDues,
            onDismiss = paymentViewModel::dismissForm
        )
    }

    val clubName by hiltViewModel<ClubNameViewModel>().clubName.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        topBar = {
            ClubMainTopAppBar(
                onOpenSettings = onOpenSettings,
                onSwitchClub = onSwitchClub
            )
        },
        floatingActionButton = {
            if (canRegisterPayment) {
                ExtendedFloatingActionButton(
                    onClick = {
                        paymentViewModel.openForm()
                    },
                    containerColor = TextPrimary,
                    contentColor = BackgroundBlack,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = "납부 등록",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ClubDropdownField(
                    label = "연도",
                    options = yearFilterOptions,
                    selected = uiState.selectedYear,
                    onSelected = statusViewModel::updateSelectedYear,
                    modifier = Modifier.weight(1f),
                    menuMaxHeight = 280.dp,
                    scrollToValueOnExpand = java.time.Year.now().value
                )
                ClubDropdownField(
                    label = "회원",
                    options = memberFilterOptions,
                    selected = uiState.filterMemberId,
                    onSelected = statusViewModel::updateSelectedMember,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "총 미납액: %,d원".format(KOREA, uiState.totalClubUnpaidAmount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            DuesStatusTableHeader(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (uiState.memberRows.isEmpty()) {
                ClubEmptyState(
                    title = "표시할 회원이 없습니다",
                    description = when {
                        uiState.filterMemberId != null -> "선택한 회원을 찾을 수 없습니다."
                        else -> "등록된 활동 회원이 없습니다."
                    },
                    hint = "회원 탭에서 회원을 추가한 뒤, 우측 하단 「납부 등록」으로 회비를 등록해 보세요.",
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.memberRows, key = { it.memberId }) { member ->
                        MemberDuesStatusTableRow(
                            member = member,
                            isEditable = true,
                            onClick = {
                                if (member.hasRegisteredDues) {
                                    statusViewModel.openMemberDetail(member.memberId)
                                } else if (canRegisterPayment) {
                                    paymentViewModel.openFormForMember(member.memberId)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "아직 회비가 등록되지 않았습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onReminderClick = {
                                DuesReminderMessenger.sendViaKakaoTalk(
                                    context = context,
                                    message = DuesReminderMessenger.buildMessage(member, clubName)
                                )
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = DividerSubtle,
                            thickness = 0.5.dp
                        )
                    }
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }
    }
}



@Composable
private fun DuesStatusTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "회원",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = "미납금액",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.End
        )
        Text(
            text = "비고",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1.2f),
            textAlign = TextAlign.End
        )
    }
}



@Composable
private fun MemberDuesStatusTableRow(
    member: MemberDuesStatusRowUi,
    isEditable: Boolean,
    onClick: () -> Unit,
    onReminderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp)),
        color = SurfaceDeepGray
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isEditable) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(0.9f),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = member.memberName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (member.hasRegisteredDues && !member.isFullyPaid) {
                    IconButton(
                        onClick = onReminderClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "회비 안내 보내기",
                            tint = TextSecondary
                        )
                    }
                }
            }
            Text(
                text = when {
                    !member.hasRegisteredDues -> "-"
                    member.isFullyPaid -> "완납"
                    else -> "%,d원".format(KOREA, member.totalUnpaidAmount)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !member.hasRegisteredDues -> TextSecondary
                    member.isFullyPaid -> IncomeBlue
                    else -> ExpenseRed
                },
                modifier = Modifier.weight(0.9f),
                textAlign = TextAlign.End
            )
            Text(
                text = member.unpaidDetails.ifBlank { "-" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.End
            )
        }
    }
}

