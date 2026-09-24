package com.smartexpense.ui.settings.bank

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.core.graphics.drawable.toBitmap
import com.smartexpense.data.model.bank.InstalledAppInfo
import com.smartexpense.ui.components.rememberModalBottomSheetDismissAction
import com.smartexpense.ui.club.components.rememberSyncedTextFieldValue
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.BorderLine
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionBottomSheet(
    apps: List<InstalledAppInfo>,
    filteredApps: List<InstalledAppInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAppSelected: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismissSheet = rememberModalBottomSheetDismissAction(
        sheetState = sheetState,
        onDismiss = onDismiss
    )
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        containerColor = BackgroundBlack
    ) {
        BackHandler(onBack = dismissSheet)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "앱 선택",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildAppPickerSubtitle(apps),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            val searchTextFieldState = rememberSyncedTextFieldValue(searchQuery)
            OutlinedTextField(
                value = searchTextFieldState.value,
                onValueChange = { updated ->
                    searchTextFieldState.value = updated
                    onSearchQueryChange(updated.text)
                },
                label = { Text("은행·증권 앱 이름 검색") },
                placeholder = { Text("예: 하나, OK, KB, 토스") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus() },
                    onDone = { focusManager.clearFocus() }
                ),
                colors = appSearchFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            val financeApps = filteredApps.filter { it.isFinanceLikely }
            val otherApps = filteredApps.filter { !it.isFinanceLikely }
            val isSearching = searchQuery.isNotBlank()

            when {
                apps.isEmpty() -> {
                    Text(
                        text = "설치된 앱을 불러오지 못했습니다.\n앱을 재설치한 뒤 다시 시도해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                filteredApps.isEmpty() -> {
                    Text(
                        text = "\"$searchQuery\"에 해당하는 앱이 없습니다.\n다른 검색어(은행명·증권사명)를 입력해 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isSearching && financeApps.isNotEmpty()) {
                            item(key = "header_finance") {
                                AppListSectionHeader("은행·증권·카드 (${financeApps.size})")
                            }
                            items(financeApps, key = { "finance_${it.packageName}" }) { app ->
                                AppListItem(app = app, onAppSelected = onAppSelected, dismissSheet = dismissSheet, focusManager = focusManager)
                            }
                        }

                        if (!isSearching && otherApps.isNotEmpty()) {
                            item(key = "header_other") {
                                AppListSectionHeader("기타 설치 앱 (${otherApps.size})")
                            }
                            items(otherApps, key = { "other_${it.packageName}" }) { app ->
                                AppListItem(app = app, onAppSelected = onAppSelected, dismissSheet = dismissSheet, focusManager = focusManager)
                            }
                        }

                        if (isSearching) {
                            items(filteredApps, key = { "search_${it.packageName}" }) { app ->
                                AppListItem(app = app, onAppSelected = onAppSelected, dismissSheet = dismissSheet, focusManager = focusManager)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun buildAppPickerSubtitle(apps: List<InstalledAppInfo>): String {
    val financeCount = apps.count { it.isFinanceLikely }
    return if (financeCount > 0) {
        "금융 앱 ${financeCount}개 · 전체 ${apps.size}개 · 상단에 은행·증권 앱이 먼저 표시됩니다."
    } else {
        "전체 ${apps.size}개 · 검색창에 은행·증권 앱 이름을 입력해 찾을 수 있습니다."
    }
}

@Composable
private fun AppListSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun AppListItem(
    app: InstalledAppInfo,
    onAppSelected: (InstalledAppInfo) -> Unit,
    dismissSheet: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    InstalledAppRow(
        app = app,
        onClick = {
            focusManager.clearFocus()
            onAppSelected(app)
            dismissSheet()
        }
    )
    HorizontalDivider(color = BorderLine, thickness = 0.5.dp)
}

@Composable
private fun appSearchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = TextSecondary,
    unfocusedBorderColor = TextSecondary,
    focusedLabelColor = TextSecondary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = TextPrimary
)

@Composable
private fun InstalledAppRow(
    app: InstalledAppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconBitmap = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun CustomBankSettingsRow(
    bank: com.smartexpense.data.model.bank.CustomBankUiModel,
    enabled: Boolean,
    parsingEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bank.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (parsingEnabled) TextPrimary else TextSecondary
                )
                Text(
                    text = " · 직접 추가",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Text(
                text = bank.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
            enabled = parsingEnabled
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "삭제",
                tint = ExpenseRed
            )
        }
    }
}

@Composable
fun AddCustomBankItem(
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = TextPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = TextPrimary
            )
        }
        Text(
            text = if (isLoading) "앱 목록 불러오는 중…" else "직접 추가하기",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}
