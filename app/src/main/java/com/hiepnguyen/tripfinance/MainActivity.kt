package com.hiepnguyen.tripfinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import com.hiepnguyen.tripfinance.ui.components.AutoFitSingleLineText
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.screens.*
import com.hiepnguyen.tripfinance.ui.theme.*
import com.hiepnguyen.tripfinance.ui.viewmodel.TripFinanceViewModel

enum class NavigationTab(val tabKey: String, val defaultTitle: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    DASHBOARD("DASHBOARD", "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    EXPENSES("EXPENSES", "Expenses", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    FUND("FUND", "Fund", Icons.Filled.Savings, Icons.Outlined.Savings),
    SETTLEMENT("SETTLEMENT", "Settlement", Icons.Filled.Payments, Icons.Outlined.Payments),
    MEMBERS("SETTINGS", "Members", Icons.Filled.Group, Icons.Outlined.Group)
}

class MainActivity : ComponentActivity() {

    private val viewModel: TripFinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TripFinanceTheme {
                val dbState by viewModel.databaseInitState.collectAsStateWithLifecycle()
                when (val state = dbState) {
                    is com.hiepnguyen.tripfinance.ui.viewmodel.DatabaseInitState.Initializing -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EmeraldPrimary)
                        }
                    }
                    is com.hiepnguyen.tripfinance.ui.viewmodel.DatabaseInitState.Error -> {
                        val lang by viewModel.language.collectAsStateWithLifecycle()
                        EmergencyDatabaseRecoveryScreen(
                            error = state,
                            lang = lang,
                            onRetry = { viewModel.retryDatabaseInit() },
                            onRestoreBackup = { json, password ->
                                viewModel.restoreFromEmergencyBackup(json, password)
                            },
                            onResetFresh = { viewModel.resetDatabaseFresh() }
                        )
                    }
                    is com.hiepnguyen.tripfinance.ui.viewmodel.DatabaseInitState.Ready -> {
                        TripFinanceApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripFinanceApp(viewModel: TripFinanceViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lang by viewModel.language.collectAsStateWithLifecycle()
    val aiInsight by viewModel.aiInsight.collectAsStateWithLifecycle()
    val isLoadingAi by viewModel.isLoadingAi.collectAsStateWithLifecycle()
    val isExportingPdf by viewModel.isExportingPdf.collectAsStateWithLifecycle()
    val isExportingXlsx by viewModel.isExportingXlsx.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    // Dialog state controllers
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddFundDialog by remember { mutableStateOf(false) }
    var showCreateTripDialog by remember { mutableStateOf(false) }
    var showExportReportDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showUserGuideScreen by remember { mutableStateOf(false) }

    // Intercept back button for modal screens & dialogs
    BackHandler(enabled = showUserGuideScreen) {
        showUserGuideScreen = false
    }
    BackHandler(enabled = showExportReportDialog) {
        showExportReportDialog = false
    }
    BackHandler(enabled = showBackupRestoreDialog) {
        showBackupRestoreDialog = false
    }
    BackHandler(enabled = showAddExpenseDialog) {
        showAddExpenseDialog = false
    }
    BackHandler(enabled = showAddFundDialog) {
        showAddFundDialog = false
    }
    BackHandler(enabled = showCreateTripDialog) {
        showCreateTripDialog = false
    }
    BackHandler(enabled = aiInsight != null || isLoadingAi) {
        viewModel.clearAiInsight()
    }
    BackHandler(enabled = !showUserGuideScreen && !showExportReportDialog && !showBackupRestoreDialog && !showAddExpenseDialog && !showAddFundDialog && !showCreateTripDialog && currentTab != NavigationTab.DASHBOARD) {
        currentTab = NavigationTab.DASHBOARD
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deletedExpenseEvent.collectLatest { deletedUndo ->
            val currentLang = viewModel.language.value
            val message = if (currentLang == AppLanguage.VI) "Đã xóa. Hoàn tác" else "Deleted. Undo"
            val actionLabel = if (currentLang == AppLanguage.VI) "Hoàn tác" else "Undo"
            val result = withTimeoutOrNull(5000L) {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Indefinite
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteExpense(deletedUndo)
            }
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides lang) {
        Scaffold(
            topBar = {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isVeryNarrow = maxWidth < 360.dp
                    val isCompactPhone = maxWidth < 410.dp
                    val actionSlotWidth = when {
                        isVeryNarrow -> 34.dp
                        isCompactPhone -> 38.dp
                        else -> 46.dp
                    }
                    val actionIconSize = if (isVeryNarrow) 20.dp else 22.dp
                    val brandIconSize = if (isVeryNarrow) 20.dp else 22.dp
                    val brandGap = if (isVeryNarrow) 5.dp else 7.dp
                    val maxBrandFont = if (isVeryNarrow) 16.sp else 18.5.sp

                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(brandIconSize)
                                )
                                Spacer(modifier = Modifier.width(brandGap))
                                AutoFitSingleLineText(
                                    text = "TripFinance",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = maxBrandFont,
                                    minFontSize = 11.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("top_bar_brand_title")
                                )
                            }
                        },
                        actions = {
                            CompositionLocalProvider(
                                LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 2.dp)
                                ) {
                                    // Language Switcher Button in TopAppBar
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Surface(
                                            onClick = { viewModel.toggleLanguage() },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (lang == AppLanguage.VI) EmeraldPrimaryContainer else Color(0xFFEDE9FE),
                                            border = BorderStroke(1.dp, if (lang == AppLanguage.VI) EmeraldPrimary else Color(0xFF7C3AED)),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .testTag("toggle_language_top_button")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = if (isVeryNarrow) 6.dp else 8.dp,
                                                    vertical = 3.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (lang == AppLanguage.VI) "🇻🇳 VI" else "🇬🇧 EN",
                                                    fontSize = if (isVeryNarrow) 10.5.sp else 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Clip,
                                                    color = if (lang == AppLanguage.VI) EmeraldOnPrimaryContainer else Color(0xFF5B21B6)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(if (isVeryNarrow) 2.dp else 4.dp))

                                    // Export Report Button in TopAppBar
                                    IconButton(
                                        onClick = { showExportReportDialog = true },
                                        modifier = Modifier
                                            .size(width = actionSlotWidth, height = 48.dp)
                                            .testTag("export_report_top_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Description,
                                            contentDescription = AppStrings.exportReport(lang),
                                            tint = IndigoSecondary,
                                            modifier = Modifier.size(actionIconSize)
                                        )
                                    }

                                    // User Guide Button in TopAppBar
                                    IconButton(
                                        onClick = { showUserGuideScreen = true },
                                        modifier = Modifier
                                            .size(width = actionSlotWidth, height = 48.dp)
                                            .testTag("user_guide_top_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.HelpOutline,
                                            contentDescription = AppStrings.topBarUserGuideCd(lang),
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(actionIconSize)
                                        )
                                    }

                                    // Backup & Recovery Button in TopAppBar
                                    IconButton(
                                        onClick = { showBackupRestoreDialog = true },
                                        modifier = Modifier
                                            .size(width = actionSlotWidth, height = 48.dp)
                                            .testTag("backup_restore_top_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudSync,
                                            contentDescription = AppStrings.topBarBackupRestoreCd(lang),
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(actionIconSize)
                                        )
                                    }

                                    // Quick trip selector dropdown if user has multiple trips
                                    var tripMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { tripMenuExpanded = true },
                                            modifier = Modifier
                                                .size(width = actionSlotWidth, height = 48.dp)
                                                .testTag("switch_trip_top_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.SwapHoriz,
                                                contentDescription = AppStrings.selectTrip(lang),
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(actionIconSize)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = tripMenuExpanded,
                                            onDismissRequest = { tripMenuExpanded = false }
                                        ) {
                                            Text(
                                                text = AppStrings.selectTrip(lang),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                            uiState.allTrips.forEach { trip ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = trip.title,
                                                            fontWeight = if (trip.id == uiState.currentTrip?.id) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        viewModel.selectTrip(trip.id)
                                                        tripMenuExpanded = false
                                                    },
                                                    leadingIcon = {
                                                        if (trip.id == uiState.currentTrip?.id) {
                                                            Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldPrimary)
                                                        }
                                                    }
                                                )
                                            }
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text(AppStrings.createNewTrip(lang)) },
                                                onClick = {
                                                    showCreateTripDialog = true
                                                    tripMenuExpanded = false
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val tabTitle = AppStrings.getTabName(tab.name, lang)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tabTitle
                                )
                            },
                            label = {
                                AutoFitSingleLineText(
                                    text = tabTitle,
                                    maxFontSize = 11.sp,
                                    minFontSize = 8.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    style = LocalTextStyle.current.copy(letterSpacing = 0.sp),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldPrimary,
                                selectedTextColor = EmeraldPrimary,
                                indicatorColor = EmeraldPrimaryContainer
                            )
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Content per selected tab
                when (currentTab) {
                    NavigationTab.DASHBOARD -> DashboardScreen(
                        uiState = uiState,
                        onNavigateToExpenses = { currentTab = NavigationTab.EXPENSES },
                        onNavigateToFund = { currentTab = NavigationTab.FUND },
                        onNavigateToSettlement = { currentTab = NavigationTab.SETTLEMENT },
                        onRequestAiInsight = { viewModel.requestAiSpendingInsight() },
                        onOpenAddExpense = {
                            if (uiState.currentTrip?.isSettled != true) {
                                showAddExpenseDialog = true
                            }
                        },
                        onOpenAddFund = {
                            if (uiState.currentTrip?.isSettled != true) {
                                showAddFundDialog = true
                            }
                        },
                        onOpenExportReport = { showExportReportDialog = true },
                        onOpenCreateTrip = { showCreateTripDialog = true },
                        onOpenUserGuide = { showUserGuideScreen = true },
                        onSwitchUser = { memberId -> viewModel.switchUserPersona(memberId) },
                        onSelectTrip = { tripId -> viewModel.selectTrip(tripId) },
                        onEditTrip = { trip, title, desc, start, end, baseCurr ->
                            viewModel.editTrip(trip.id, title, desc, start, end, baseCurr)
                        },
                        onDeleteTrip = { trip -> viewModel.deleteTrip(trip.id) }
                    )
                    NavigationTab.EXPENSES -> ExpensesScreen(
                        uiState = uiState,
                        onOpenAddExpense = {
                            if (uiState.currentTrip?.isSettled != true) {
                                showAddExpenseDialog = true
                            }
                        },
                        onEditExpense = { expId, title, cat, payerType, payerId, amount, curr, rate, splitType, splits, note, time ->
                            viewModel.editExpense(expId, title, cat, payerType, payerId, amount, curr, rate, splitType, splits, note, time)
                        },
                        onDeleteExpense = { exp -> viewModel.deleteExpense(exp) },
                        onCategoryFilterChange = { cat -> viewModel.setCategoryFilter(cat) },
                        onSearchChange = { q -> viewModel.setSearchQuery(q) }
                    )
                    NavigationTab.FUND -> FundScreen(
                        uiState = uiState,
                        onOpenAddFund = {
                            if (uiState.currentTrip?.isSettled != true) {
                                showAddFundDialog = true
                            }
                        }
                    )
                    NavigationTab.SETTLEMENT -> SettlementScreen(
                        uiState = uiState,
                        onFinalizeSettlement = { title -> viewModel.finalizeSettlement(title) },
                        onReopenSettlement = { viewModel.reopenSettlement() },
                        onToggleTransferCompleted = { viewModel.toggleTransferCompleted(it) },
                        onOpenExportReport = { showExportReportDialog = true }
                    )
                    NavigationTab.MEMBERS -> MembersAndSettingsScreen(
                        uiState = uiState,
                        onAddMember = { name, role ->
                            viewModel.addMember(name, role)
                        },
                        onEditMember = { member, name, role ->
                            viewModel.editMember(member, name, role)
                        },
                        onUpdateRole = { member, newRole ->
                            viewModel.updateMemberRole(member, newRole)
                        },
                        onRemoveOrDeactivate = { member ->
                            viewModel.removeOrDeactivateMember(member)
                        },
                        onUpdateRate = { code, rate ->
                            viewModel.updateExchangeRate(code, rate)
                        },
                        onSelectLanguage = { newLang ->
                            viewModel.setLanguage(newLang)
                        },
                        onOpenUserGuide = { showUserGuideScreen = true },
                        onOpenBackupRestore = { showBackupRestoreDialog = true },
                        onEditTrip = { trip, title, desc, sDate, eDate, baseCurr ->
                            viewModel.editTrip(trip.id, title, desc, sDate, eDate, baseCurr)
                        },
                        onDeleteTrip = { trip ->
                            viewModel.deleteTrip(trip.id)
                        }
                    )
                }
            }
        }

        // Modal Dialogs
        if (showBackupRestoreDialog) {
            BackupRestoreDialog(
                viewModel = viewModel,
                lang = lang,
                onDismiss = { showBackupRestoreDialog = false }
            )
        }

        if (showExportReportDialog) {
            ExportReportDialog(
                uiState = uiState,
                isExportingPdf = isExportingPdf,
                onExportPdf = { viewModel.exportAndSharePdf(it) },
                isExportingXlsx = isExportingXlsx,
                onExportXlsx = { viewModel.exportAndShareXlsx(it) },
                lang = lang,
                onDismiss = { showExportReportDialog = false }
            )
        }

        // Modal Dialogs
        if (showAddExpenseDialog) {
            AddExpenseDialog(
                members = uiState.members,
                exchangeRates = uiState.exchangeRates,
                currentMemberId = uiState.currentMember?.id,
                baseCurrency = uiState.currentTrip?.baseCurrency ?: "VND",
                lang = lang,
                onDismiss = { showAddExpenseDialog = false },
                onSave = { title, category, payerType, payerMemberId, amount, currency, rate, splitType, splits, note, timestamp ->
                    viewModel.addExpense(
                        title, category, payerType, payerMemberId, amount, currency, rate, splitType, splits, note, timestamp
                    )
                }
            )
        }

        if (showAddFundDialog) {
            AddFundContributionDialog(
                members = uiState.members,
                baseCurrency = uiState.currentTrip?.baseCurrency ?: "VND",
                lang = lang,
                onDismiss = { showAddFundDialog = false },
                onSave = { memberId, amount, currency, rate, note ->
                    viewModel.addFundContribution(memberId, amount, currency, rate, note)
                }
            )
        }

        if (showCreateTripDialog) {
            CreateTripDialog(
                lang = lang,
                onDismiss = { showCreateTripDialog = false },
                onConfirm = { title, desc, start, end, admin, baseCurr ->
                    viewModel.createTrip(
                        title = title,
                        description = desc,
                        joinCode = "",
                        startDate = start,
                        endDate = end,
                        adminName = admin,
                        baseCurrency = baseCurr
                    )
                    showCreateTripDialog = false
                }
            )
        }

        if (showUserGuideScreen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showUserGuideScreen = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                CompositionLocalProvider(LocalAppLanguage provides lang) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        UserGuideScreen(
                            lang = lang,
                            onBack = { showUserGuideScreen = false }
                        )
                    }
                }
            }
        }

        if (aiInsight != null || isLoadingAi) {
            com.hiepnguyen.tripfinance.ui.components.AiSpendingAdvisorDialog(
                insight = aiInsight,
                isLoading = isLoadingAi,
                lang = lang,
                onDismiss = { viewModel.clearAiInsight() }
            )
        }
    }
}

// --- AppStrings Localized Extensions for MainActivity ---

fun AppStrings.topBarUserGuideCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hướng dẫn sử dụng" else "User Guide"

fun AppStrings.topBarBackupRestoreCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sao lưu & Khôi phục" else "Backup & Recover"

