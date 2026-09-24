package com.smartexpense.ui.settings.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.settings.SettingsItemDivider
import com.smartexpense.ui.settings.SettingsNavigationItem
import com.smartexpense.ui.settings.SettingsSection
import com.smartexpense.ui.settings.SettingsSwitchItem
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankParsingSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BankParsingSettingsViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible {
        viewModel.refreshOnVisible()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbarMessage()
        }
    }

    if (uiState.showAppPicker) {
        AppSelectionBottomSheet(
            apps = uiState.installedApps,
            filteredApps = uiState.filteredApps,
            searchQuery = uiState.appSearchQuery,
            onSearchQueryChange = viewModel::updateAppSearchQuery,
            onAppSelected = viewModel::selectInstalledApp,
            onDismiss = viewModel::dismissAppPicker
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "은행 알림 파싱",
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsSection(title = "알림 접근") {
                    SettingsNavigationItem(
                        title = "알림 접근 권한",
                        subtitle = if (uiState.isNotificationAccessGranted) {
                            "허용됨 · 은행 알림을 읽을 수 있습니다"
                        } else {
                            "미허용 · 시스템 설정에서 앱 알림 접근을 허용해 주세요"
                        },
                        onClick = {
                            viewModel.refreshNotificationAccess()
                            viewModel.openNotificationAccessSettings()
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "기본 제공 은행/증권사") {
                    Text(
                        text = "활성화한 앱의 입·출금 알림만 장부 등록에 반영됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    viewModel.templates.forEachIndexed { index, template ->
                        if (index > 0) {
                            SettingsItemDivider()
                        }
                        SettingsSwitchItem(
                            title = template.name,
                            subtitle = template.packageNames.joinToString(", "),
                            checked = uiState.templateEnabled[template.id] == true,
                            enabled = uiState.isNotificationAccessGranted,
                            onCheckedChange = { enabled ->
                                viewModel.onTemplateToggle(template.id, enabled)
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "직접 추가한 앱") {
                    if (uiState.customBanks.isEmpty()) {
                        Text(
                            text = "목록에 없는 은행·증권 앱은 아래에서 직접 추가할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    } else {
                        uiState.customBanks.forEachIndexed { index, bank ->
                            if (index > 0) {
                                SettingsItemDivider()
                            }
                            CustomBankSettingsRow(
                                bank = bank,
                                enabled = bank.isEnabled,
                                parsingEnabled = uiState.isNotificationAccessGranted,
                                onCheckedChange = { enabled ->
                                    viewModel.onCustomBankToggle(bank.packageName, enabled)
                                },
                                onDelete = { viewModel.removeCustomBank(bank.id) }
                            )
                        }
                        SettingsItemDivider()
                    }
                    AddCustomBankItem(
                        onClick = viewModel::openAppPicker,
                        isLoading = uiState.isLoadingApps,
                        modifier = Modifier.padding(
                            vertical = if (uiState.customBanks.isEmpty()) 0.dp else 4.dp
                        )
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
