package com.smartexpense.ui.club.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.local.entity.club.ClubHistoryEntity
import com.smartexpense.ui.club.ClubNameViewModel
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.club.components.ClubDeleteConfirmDialog
import com.smartexpense.ui.club.components.ClubEmptyState
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.BorderLine
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClubHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClubHistoryViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible {
        viewModel.refreshOnVisible()
    }

    val clubName by hiltViewModel<ClubNameViewModel>().clubName.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()

    uiState.deleteTarget?.let { target ->
        ClubDeleteConfirmDialog(
            title = "모임 이력 삭제",
            message = "${target.content} 이력을 삭제할까요?",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDeleteConfirm
        )
    }

    if (uiState.isFormVisible) {
        HistoryFormBottomSheet(
            form = uiState.form,
            isSaving = uiState.isSaving,
            onDateChange = viewModel::updateDate,
            onContentChange = viewModel::updateContent,
            onDetailsChange = viewModel::updateDetails,
            onNoteChange = viewModel::updateNote,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::dismissForm
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "$clubName 이력",
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
                    onClick = viewModel::openCreateForm,
                    containerColor = TextPrimary,
                    contentColor = BackgroundBlack
                ) {
                    Icon(Icons.Default.Add, contentDescription = "이력 추가")
                }
            }
        }
    ) { innerPadding ->
        if (uiState.items.isEmpty()) {
            ClubEmptyState(
                title = "등록된 모임 이력이 없습니다",
                description = "우측 하단 + 버튼으로 창단, 회장 선출 등\n모임의 발자취를 기록해 보세요.",
                hint = "항목을 눌러 수정, 길게 눌러 삭제할 수 있습니다.",
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
                items(uiState.items, key = { it.id }) { item ->
                    ClubHistoryTimelineCard(
                        item = item,
                        isEditable = isAdmin,
                        onClick = { viewModel.openEditForm(item) },
                        onLongClick = { viewModel.requestDelete(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClubHistoryTimelineCard(
    item: ClubHistoryEntity,
    isEditable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isEditable) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = SurfaceDeepGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(72.dp)
            ) {
                Text(
                    text = formatClubHistoryTimelineYear(item.date),
                    style = MaterialTheme.typography.labelLarge,
                    color = IncomeBlue,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatClubHistoryTimelineMonthDay(item.date),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(BorderLine)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                item.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundBlack.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
