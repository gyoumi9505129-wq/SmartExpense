package com.smartexpense.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartexpense.di.BankNotificationEntryPoint
import com.smartexpense.ui.club.hub.MeetingHubScreen
import com.smartexpense.ui.club.history.ClubHistoryScreen
import com.smartexpense.ui.club.dues.DuesStatusScreen
import com.smartexpense.ui.club.event.EventExpenseDashboardScreen
import com.smartexpense.ui.club.member.MemberManagementScreen
import com.smartexpense.ui.club.report.YearlyReportScreen
import com.smartexpense.ui.club.transaction.TransactionScreen
import com.smartexpense.ui.club.transaction.TransactionViewModel
import com.smartexpense.ui.settings.AccountsAdminScreen
import com.smartexpense.ui.settings.SettingsScreen
import com.smartexpense.ui.settings.account.ClubAccountScreen
import com.smartexpense.ui.settings.bank.BankParsingSettingsScreen
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.ui.club.ClubNameViewModel
import com.smartexpense.ui.startup.StartupViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

private const val TRANSACTION_TAB_INDEX = 2

private data class ClubBottomNavItem(
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    ClubBottomNavItem("회원", Icons.Default.Group),
    ClubBottomNavItem("회비", Icons.AutoMirrored.Filled.List),
    ClubBottomNavItem("장부", Icons.Default.ReceiptLong),
    ClubBottomNavItem("경조", Icons.Default.Celebration),
    ClubBottomNavItem("결산", Icons.Default.Assessment)
)

/** 개설자·시스템관리자에게만 노출되는 「관리」 탭(계정 강퇴/삭제 등). [bottomNavItems] 뒤에 조건부로 붙습니다. */
private val adminNavItem = ClubBottomNavItem("관리", Icons.Default.AdminPanelSettings)

@Composable
fun ClubNavHost(
    navController: NavHostController = rememberNavController()
) {
    val startupViewModel: StartupViewModel = hiltViewModel()
    val isStartupReady by startupViewModel.isReady.collectAsStateWithLifecycle()
    val isStartupLoading by startupViewModel.isLoading.collectAsStateWithLifecycle()
    val hasLoadedClubSelection by startupViewModel.hasLoadedClubSelection.collectAsStateWithLifecycle()
    val selectedClubId by startupViewModel.selectedClubId.collectAsStateWithLifecycle()
    val suppressClubListRedirect by startupViewModel.suppressClubListRedirect.collectAsStateWithLifecycle()

    LaunchedEffect(isStartupReady, hasLoadedClubSelection, selectedClubId, suppressClubListRedirect) {
        if (!isStartupReady || !hasLoadedClubSelection || suppressClubListRedirect) return@LaunchedEffect
        if (selectedClubId != null) return@LaunchedEffect

        val currentRoute = navController.currentDestination?.route.orEmpty()
        if (currentRoute.contains("ClubList")) return@LaunchedEffect

        navController.navigate(Route.ClubList) {
            popUpTo(Route.ClubList) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Route.ClubList,
            modifier = Modifier.fillMaxSize()
        ) {
        composable<Route.ClubList> {
            MeetingHubScreen(
                onMeetingEntered = {
                    navController.navigate(Route.ClubMain) {
                        popUpTo(Route.ClubList) { saveState = true }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            )
        }
        composable<Route.ClubMain> {
            if (selectedClubId == null && hasLoadedClubSelection && !suppressClubListRedirect) {
                LaunchedEffect(Unit) {
                    navController.navigate(Route.ClubList) {
                        popUpTo(Route.ClubList) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                Box(modifier = Modifier.fillMaxSize())
            } else if (selectedClubId != null) {
                ClubMainTabs(
                    onOpenSettings = {
                        navController.navigate(Route.Settings) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo<Route.ClubMain> { saveState = true }
                        }
                    },
                    onNavigateToClubList = {
                        navController.navigate(Route.ClubList) {
                            popUpTo(Route.ClubList) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable<Route.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSwitchClub = {
                    navController.navigate(Route.ClubList) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onLoggedOut = {
                    navController.navigate(Route.ClubList) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenClubHistory = {
                    navController.navigate(Route.ClubHistory) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo<Route.Settings> { saveState = true }
                    }
                },
                onOpenBankParsing = {
                    navController.navigate(Route.BankParsingSettings) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo<Route.Settings> { saveState = true }
                    }
                },
                onOpenClubAccount = {
                    navController.navigate(Route.ClubAccountManagement) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo<Route.Settings> { saveState = true }
                    }
                }
            )
        }
        composable<Route.BankParsingSettings> {
            BankParsingSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable<Route.ClubAccountManagement> {
            ClubAccountScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable<Route.ClubHistory> {
            ClubHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }

        if (isStartupLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TextPrimary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClubMainTabs(
    onOpenSettings: () -> Unit,
    onNavigateToClubList: () -> Unit
) {
    val clubNameViewModel: ClubNameViewModel = hiltViewModel()
    val onSwitchClub = {
        clubNameViewModel.leaveCurrentClub(onNavigateToClubList)
    }
    val canManageAccounts by clubNameViewModel.canManageAccounts.collectAsStateWithLifecycle()
    // 개설자·시스템관리자만 「관리」 탭이 추가되어 총 6개, 그 외 회원은 기본 5개 탭을 봅니다.
    val navItems = if (canManageAccounts) bottomNavItems + adminNavItem else bottomNavItems
    val adminTabIndex = bottomNavItems.size
    val context = LocalContext.current
    val bankNotificationCoordinator = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BankNotificationEntryPoint::class.java
        ).bankNotificationCoordinator()
    }
    var savedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedTabIndex,
        pageCount = { navItems.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var bankNotificationOpenTick by remember { mutableIntStateOf(0) }
    var openEmptyTransactionForm by remember { mutableStateOf(false) }
    val selectedTabIndex by remember {
        derivedStateOf { pagerState.settledPage }
    }
    val transactionViewModel: TransactionViewModel = hiltViewModel()

    LaunchedEffect(pagerState.settledPage) {
        savedTabIndex = pagerState.settledPage
    }

    LaunchedEffect(bankNotificationCoordinator) {
        suspend fun navigateToTransactionForm(useEmptyForm: Boolean) {
            openEmptyTransactionForm = useEmptyForm
            coroutineScope.launch {
                pagerState.animateScrollToPage(TRANSACTION_TAB_INDEX)
            }
            bankNotificationOpenTick++
        }
        if (bankNotificationCoordinator.consumeOpenRequest()) {
            navigateToTransactionForm(bankNotificationCoordinator.consumeEmptyFormRequest())
        }
        bankNotificationCoordinator.openTransactionFormSignal.collect {
            if (bankNotificationCoordinator.consumeOpenRequest()) {
                navigateToTransactionForm(bankNotificationCoordinator.consumeEmptyFormRequest())
            }
        }
    }

    Scaffold(
        containerColor = BackgroundBlack,
        bottomBar = {
            ClubBottomNavigationBar(
                items = navItems,
                selectedIndex = selectedTabIndex,
                onTabClick = { index ->
                    if (index != pagerState.settledPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> MemberManagementScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSettings = onOpenSettings,
                    onSwitchClub = onSwitchClub,
                    isScreenActive = selectedTabIndex == 0
                )
                1 -> DuesStatusScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSettings = onOpenSettings,
                    onSwitchClub = onSwitchClub,
                    isScreenActive = selectedTabIndex == 1
                )
                2 -> TransactionScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSettings = onOpenSettings,
                    onSwitchClub = onSwitchClub,
                    bankNotificationOpenTick = bankNotificationOpenTick,
                    openEmptyTransactionForm = openEmptyTransactionForm,
                    viewModel = transactionViewModel,
                    isScreenActive = selectedTabIndex == TRANSACTION_TAB_INDEX
                )
                3 -> EventExpenseDashboardScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSettings = onOpenSettings,
                    onSwitchClub = onSwitchClub,
                    isScreenActive = selectedTabIndex == 3
                )
                4 -> YearlyReportScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSettings = onOpenSettings,
                    onSwitchClub = onSwitchClub,
                    isScreenActive = selectedTabIndex == 4
                )
                adminTabIndex -> AccountsAdminScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSettings = onOpenSettings,
                    onSwitchClub = onSwitchClub,
                    isScreenActive = selectedTabIndex == adminTabIndex
                )
            }
        }
    }
}

@Composable
private fun ClubBottomNavigationBar(
    items: List<ClubBottomNavItem>,
    selectedIndex: Int,
    onTabClick: (Int) -> Unit
) {
    NavigationBar(
        containerColor = BackgroundBlack,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onTabClick(index) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TextPrimary,
                    selectedTextColor = TextPrimary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Color.White.copy(alpha = 0.08f)
                )
            )
        }
    }
}
