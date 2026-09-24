package com.smartexpense.ui.club.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartexpense.data.firebase.firestore.JoinRequestDoc
import com.smartexpense.data.firebase.firestore.MeetingDoc
import com.smartexpense.ui.club.components.ClubEmptyState
import com.smartexpense.ui.club.components.ClubTextField
import com.smartexpense.ui.common.UserRoleBadge
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.InterestGold
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@Composable
fun MeetingItemList(
    items: List<MeetingHubItem>,
    isSubmitting: Boolean,
    onEnter: (MeetingHubItem) -> Unit,
    onJoinRequest: (MeetingDoc) -> Unit,
    onCompleteProfile: (MeetingHubItem) -> Unit = {},
    emptyTitle: String = "모임 없음",
    emptyDescription: String = "표시할 모임이 없습니다."
) {
    if (items.isEmpty()) {
        ClubEmptyState(
            title = emptyTitle,
            description = emptyDescription,
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        items(items, key = { it.meeting.id }) { item ->
            MeetingHubCard(
                item = item,
                enabled = !isSubmitting,
                onEnter = { onEnter(item) },
                onJoinRequest = { onJoinRequest(item.meeting) },
                onCompleteProfile = { onCompleteProfile(item) }
            )
        }
    }
}

@Composable
private fun MeetingHubCard(
    item: MeetingHubItem,
    enabled: Boolean,
    onEnter: () -> Unit,
    onJoinRequest: () -> Unit,
    onCompleteProfile: () -> Unit
) {
    val statusLabel = when {
        item.accessStatus == MeetingAccessStatus.SUSPENDED ||
            item.accessStatus == MeetingAccessStatus.DISABLED -> "활동 중지"
        item.needsMemberProfile -> "회원정보 입력"
        item.accessStatus == MeetingAccessStatus.PENDING -> "승인 대기"
        item.accessStatus == MeetingAccessStatus.REJECTED -> "거절됨"
        item.accessStatus == MeetingAccessStatus.AVAILABLE -> "가입 가능"
        else -> null
    }
    val statusColor = when (item.accessStatus) {
        MeetingAccessStatus.SUSPENDED,
        MeetingAccessStatus.DISABLED -> ExpenseRed
        MeetingAccessStatus.PENDING,
        MeetingAccessStatus.REJECTED,
        MeetingAccessStatus.AVAILABLE -> IncomeBlue
        else -> IncomeBlue
    }
    val isRestricted = item.accessStatus == MeetingAccessStatus.SUSPENDED ||
        item.accessStatus == MeetingAccessStatus.DISABLED
    val canOpenProfile = item.needsMemberProfile && !isRestricted
    val canEnter = !item.needsMemberProfile && !isRestricted && (
        item.accessStatus == MeetingAccessStatus.OWNER ||
            item.accessStatus == MeetingAccessStatus.TREASURER ||
            item.accessStatus == MeetingAccessStatus.JOINED ||
            item.accessStatus == MeetingAccessStatus.ELEVATED
        )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDeepGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled && (canEnter || canOpenProfile),
                    onClick = {
                        if (canOpenProfile) onCompleteProfile() else onEnter()
                    }
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.meeting.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                )
                when {
                    isRestricted -> Text(
                        "활동 중지",
                        color = ExpenseRed,
                        style = MaterialTheme.typography.labelMedium
                    )
                    item.needsMemberProfile -> Text(
                        "회원정보 입력",
                        color = InterestGold,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    item.roleBadge != null -> UserRoleBadge(role = item.roleBadge)
                    statusLabel != null -> Text(
                        statusLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (item.meeting.description.isNotBlank()) {
                Text(
                    text = item.meeting.description,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (isRestricted) {
                Text(
                    text = "관리자에게 문의 바랍니다.",
                    color = ExpenseRed,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (item.pendingApprovalCount > 0) {
                Text(
                    text = "승인 대기 ${item.pendingApprovalCount}건",
                    color = InterestGold,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.accessStatus) {
                    MeetingAccessStatus.AVAILABLE,
                    MeetingAccessStatus.REJECTED -> {
                        TextButton(onClick = onJoinRequest, enabled = enabled) {
                            Text("가입 요청", color = IncomeBlue)
                        }
                    }
                    MeetingAccessStatus.SUSPENDED,
                    MeetingAccessStatus.DISABLED -> {
                        Text(
                            text = "활동 중지",
                            color = ExpenseRed
                        )
                    }
                    MeetingAccessStatus.PENDING -> {
                        Text("승인 대기 중", color = TextSecondary)
                    }
                    else -> {
                        if (item.needsMemberProfile) {
                            TextButton(onClick = onCompleteProfile, enabled = enabled) {
                                Text("회원정보 입력", color = InterestGold)
                            }
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateMeetingDialog(
    name: String,
    description: String,
    slogan: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSloganChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("모임 만들기", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ClubTextField(value = name, onValueChange = onNameChange, label = "모임 이름")
                ClubTextField(value = description, onValueChange = onDescriptionChange, label = "설명")
                ClubTextField(value = slogan, onValueChange = onSloganChange, label = "슬로건(선택)")
                errorMessage?.let { Text(it, color = ExpenseRed) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting) {
                Text(if (isSubmitting) "생성 중…" else "만들기", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = SurfaceDeepGray
    )
}

@Composable
fun JoinRequestDialog(
    meetingName: String,
    message: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onMessageChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「$meetingName」 가입 요청", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "승인되면 이 모임의 일반 회원으로 이용할 수 있습니다.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ClubTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    label = "메시지(선택)"
                )
                errorMessage?.let {
                    Text(it, color = ExpenseRed, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting) {
                Text("요청 보내기", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = TextSecondary) }
        },
        containerColor = SurfaceDeepGray
    )
}

@Composable
fun ApprovalSheetContent(
    pendingApprovals: List<Pair<MeetingDoc, JoinRequestDoc>>,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit
) {
    if (pendingApprovals.isEmpty()) {
        Text(
            "대기 중인 가입 요청이 없습니다.",
            color = TextSecondary,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pendingApprovals, key = { "${it.first.id}_${it.second.id}" }) { (meeting, request) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = BackgroundBlack),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(meeting.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${request.displayName} · ${request.email}",
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (request.message.isNotBlank()) {
                        Text(
                            request.message,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onReject(meeting.id, request.id) }) {
                            Text("거절", color = TextSecondary)
                        }
                        TextButton(onClick = { onApprove(meeting.id, request.id) }) {
                            Text("승인", color = IncomeBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateMeetingTabContent(onCreateClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "누구나 모임을 만들 수 있습니다.",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "만든 모임은 내가 모임관리자입니다. 운영관리자를 지정할 수 있고,\n다른 모임에는 「모임 찾기」에서 가입 요청 후 일반 회원으로 이용합니다.",
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(onClick = onCreateClick) {
                Text("모임 만들기", color = IncomeBlue)
            }
        }
    }
}

@Composable
fun MemberProfileRequestBanner(
    items: List<MeetingHubItem>,
    onCompleteProfile: (MeetingHubItem) -> Unit
) {
    if (items.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDeepGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "회원 정보 입력이 필요합니다",
                color = InterestGold,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "가입이 승인되었습니다. 회원 정보를 등록하면 모임 회원으로 반영됩니다.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            items.forEach { item ->
                TextButton(
                    onClick = { onCompleteProfile(item) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("「${item.meeting.name}」 정보 입력", color = IncomeBlue)
                }
            }
        }
    }
}
