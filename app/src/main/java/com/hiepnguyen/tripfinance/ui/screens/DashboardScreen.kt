package com.hiepnguyen.tripfinance.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiepnguyen.tripfinance.data.entity.TripEntity
import com.hiepnguyen.tripfinance.domain.model.BalanceStatus
import com.hiepnguyen.tripfinance.ui.components.*
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.theme.*
import com.hiepnguyen.tripfinance.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: UiState,
    onNavigateToExpenses: () -> Unit,
    onNavigateToFund: () -> Unit,
    onNavigateToSettlement: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenAddFund: () -> Unit,
    onOpenExportReport: () -> Unit,
    onOpenCreateTrip: () -> Unit,
    onOpenUserGuide: () -> Unit = {},
    onRequestAiInsight: () -> Unit = {},
    onSwitchUser: (String) -> Unit,
    onSelectTrip: (String) -> Unit = {},
    onEditTrip: (TripEntity, String, String, Long, Long, String) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteTrip: (TripEntity) -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val isSettled = uiState.currentTrip?.isSettled == true
    val tripCurrency = uiState.currentTrip?.baseCurrency ?: "VND"
    var showUserPicker by remember { mutableStateOf(false) }
    var tripToEdit by remember { mutableStateOf<TripEntity?>(null) }
    var tripToDelete by remember { mutableStateOf<TripEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
    ) {
        // Trip Banner & Persona Switcher
        item {
            TripHeaderCard(
                uiState = uiState,
                onOpenCreateTrip = onOpenCreateTrip,
                onOpenUserPicker = { showUserPicker = true },
                onSelectTrip = onSelectTrip,
                onEditTrip = { trip -> tripToEdit = trip },
                onDeleteTrip = { trip -> tripToDelete = trip }
            )
        }

        if (uiState.allTrips.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.extendedColorScheme.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.CardTravel,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = AppStrings.noTripsTitle(lang),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = AppStrings.noTripsDesc(lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColorScheme.textMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenCreateTrip,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = AppStrings.createTripBigBtn(lang), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.createTripBigBtn(lang))
                        }
                    }
                }
            }
        }

        if (isSettled) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.extendedColorScheme.cardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.extendedColorScheme.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = AppStrings.lockedBadgeCd(lang),
                            tint = MaterialTheme.extendedColorScheme.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = AppStrings.tripSettledTitle(lang),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = AppStrings.tripSettledDesc(lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.extendedColorScheme.textMuted
                            )
                        }
                    }
                }
            }
        }

        // User Guide Banner Link
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.extendedColorScheme.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenUserGuide() }
                    .testTag("dashboard_user_guide_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = AppStrings.userGuideCd(lang),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.userGuideBannerTitle(lang),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = AppStrings.userGuideBannerDesc(lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (lang == AppLanguage.VI) "Xem ngay" else "Read",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.currentTrip != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF5F3FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDD6FE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onRequestAiInsight() }
                        .testTag("dashboard_ai_advisor_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(Color(0xFF059669), Color(0xFF10B981))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Insights,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.aiSpendingInsights(lang),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46)
                                )
                                Text(
                                    text = AppStrings.aiSpendingInsightsDesc(lang),
                                    fontSize = 11.sp,
                                    color = Color(0xFF047857)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = com.hiepnguyen.tripfinance.ui.theme.EmeraldPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = AppStrings.aiAnalyzeBtn(lang),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Financial Overview Cards
        item {
            Text(
                text = AppStrings.financialOverviewTitle(lang),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Card 1: Total Expenses & Sources
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = EmeraldOnPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = AppStrings.totalTripExpensesLabel(lang),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = NumberFormatUtils.formatCurrency(uiState.financialSummary.totalExpenses, tripCurrency),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onNavigateToExpenses,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(AppStrings.detailsBtn(lang), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Breakdown of expense source: Member paid vs Fund paid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = AppStrings.memberPaidSource(lang),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = NumberFormatUtils.formatCurrency(uiState.financialSummary.personalPaidExpenses, tripCurrency),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = AppStrings.fundPaidSource(lang),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = NumberFormatUtils.formatCurrency(uiState.financialSummary.fundPaidExpenses, tripCurrency),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberTertiary
                                )
                            }
                        }
                    }
                }

                // Card 2: Group Fund (Quỹ đoàn)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AmberTertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Savings,
                                        contentDescription = null,
                                        tint = AmberOnTertiaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = AppStrings.groupFundBalanceLabel(lang),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = NumberFormatUtils.formatCurrency(uiState.financialSummary.remainingFund, tripCurrency),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberTertiary
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onNavigateToFund,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(AppStrings.groupFundBtn(lang), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = AppStrings.fundCollectedPrefix(NumberFormatUtils.formatCurrency(uiState.financialSummary.totalFundCollected, tripCurrency), lang),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = AppStrings.fundPaidPrefix(NumberFormatUtils.formatCurrency(uiState.financialSummary.fundPaidExpenses, tripCurrency), lang),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                // Card 3: Single Source of Truth Reconciliation Badge (SRS Section 2.4)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (uiState.financialSummary.isBalanced) MaterialTheme.extendedColorScheme.successContainer else MaterialTheme.extendedColorScheme.dangerContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (uiState.financialSummary.isBalanced) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = if (uiState.financialSummary.isBalanced) AppStrings.balanceStatusBalanced(lang) else AppStrings.balanceStatusDiscrepancy(lang),
                                tint = if (uiState.financialSummary.isBalanced) MaterialTheme.extendedColorScheme.success else MaterialTheme.extendedColorScheme.danger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (uiState.financialSummary.isBalanced) {
                                        AppStrings.auditValidZeroBalance(tripCurrency, lang)
                                    } else {
                                        AppStrings.auditWarningDiscrepancy(NumberFormatUtils.formatCurrency(uiState.financialSummary.balanceDiscrepancy, tripCurrency), lang)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.financialSummary.isBalanced) MaterialTheme.extendedColorScheme.onSuccessContainer else MaterialTheme.extendedColorScheme.onDangerContainer
                                )
                                Text(
                                    text = AppStrings.auditAlgorithmSafe(lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.financialSummary.isBalanced) MaterialTheme.extendedColorScheme.onSuccessContainer else MaterialTheme.extendedColorScheme.onDangerContainer
                                )
                            }
                        }

                        TextButton(
                            onClick = onNavigateToSettlement,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                AppStrings.settlementActionBtn(lang),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.financialSummary.isBalanced) MaterialTheme.extendedColorScheme.success else MaterialTheme.extendedColorScheme.danger
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onOpenAddExpense,
                    enabled = !isSettled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp)
                        .testTag("quick_add_expense_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = AppStrings.quickAddExpense(lang), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            AppStrings.quickAddExpense(lang),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenAddFund,
                    enabled = !isSettled && (uiState.currentMember?.role == "ADMIN" || uiState.currentMember?.role == "TREASURER"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AmberTertiaryContainer,
                        contentColor = AmberOnTertiaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp)
                        .testTag("quick_add_fund_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Savings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            AppStrings.quickAddFund(lang),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenExportReport,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = IndigoSecondaryContainer,
                        contentColor = IndigoOnSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp)
                        .testTag("quick_export_report_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            AppStrings.quickExportReport(lang),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenUserGuide,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFEFF6FF),
                        contentColor = Color(0xFF1D4ED8)
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp)
                        .testTag("quick_user_guide_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            AppStrings.quickUserGuide(lang),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Category Breakdown
        if (uiState.categoryBreakdowns.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppStrings.categoryBreakdownTitle(lang),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = AppStrings.expensesCountLabel(uiState.expenses.size, lang),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.categoryBreakdowns.forEach { cat ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CategoryIcon(category = cat.category, size = 16)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = cat.labelVi,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = " (${cat.count})",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Text(
                                        text = "${NumberFormatUtils.formatCurrency(cat.totalAmount, tripCurrency)} (${cat.percentage.toInt()}%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (cat.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = EmeraldPrimary,
                                    trackColor = Color(0xFFF1F5F9)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Member Balances Summary
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.memberBalancesTableTitle(lang),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = onNavigateToSettlement) {
                    Text(AppStrings.viewSettlementBtn(lang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    uiState.memberStatuses.forEach { status ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = status.member.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BalanceChip(balance = status.balance, status = status.status, currency = tripCurrency)
                                Text(
                                    text = AppStrings.memberPaidAndOwed(NumberFormatUtils.formatCurrency(status.totalPaid, tripCurrency), NumberFormatUtils.formatCurrency(status.totalOwed, tripCurrency), lang),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        if (status != uiState.memberStatuses.last()) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }
        }
    }

    // Role / Persona Switcher Dialog
    if (showUserPicker) {
        AlertDialog(
            onDismissRequest = { showUserPicker = false },
            title = {
                Text(
                    text = AppStrings.switchUserRoleTitle(lang),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = AppStrings.switchUserRoleDesc(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    uiState.members.forEach { m ->
                        val isSelected = m.id == uiState.currentMember?.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) EmeraldPrimaryContainer else NeutralLightSurfaceCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSwitchUser(m.id)
                                    showUserPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = m.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) EmeraldOnPrimaryContainer else Color(0xFF0F172A)
                                    )
                                    RoleBadge(role = m.role)
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserPicker = false }) {
                    Text(AppStrings.closeDialogBtn(lang))
                }
            }
        )
    }

    tripToEdit?.let { trip ->
        EditTripDialog(
            trip = trip,
            onDismiss = { tripToEdit = null },
            onConfirm = { title, desc, start, end, baseCurr ->
                onEditTrip(trip, title, desc, start, end, baseCurr)
                tripToEdit = null
            }
        )
    }

    tripToDelete?.let { trip ->
        DeleteTripDialog(
            tripTitle = trip.title,
            onDismiss = { tripToDelete = null },
            onConfirm = {
                onDeleteTrip(trip)
                tripToDelete = null
            }
        )
    }
}

@Composable
fun TripHeaderCard(
    uiState: UiState,
    onOpenCreateTrip: () -> Unit,
    onOpenUserPicker: () -> Unit,
    onSelectTrip: (String) -> Unit,
    onEditTrip: (TripEntity) -> Unit = {},
    onDeleteTrip: (TripEntity) -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    var showTripDropdown by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Top: Offline Badge + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33FFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = AppStrings.localOfflineStorageBadge(lang),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    uiState.currentTrip?.let { currTrip ->
                        FilledTonalIconButton(
                            onClick = { onEditTrip(currTrip) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0x33FFFFFF),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("dashboard_edit_active_trip_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = AppStrings.editTripInfoCd(lang), modifier = Modifier.size(15.dp))
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        FilledTonalIconButton(
                            onClick = { onDeleteTrip(currTrip) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0x33EF4444),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("dashboard_delete_active_trip_button")
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = AppStrings.deleteThisTripCd(lang), modifier = Modifier.size(15.dp))
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    FilledTonalIconButton(
                        onClick = onOpenCreateTrip,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0x33FFFFFF),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("dashboard_create_trip_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = AppStrings.createNewTripCd(lang), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prominent Dropdown Selector for Active Business Trip
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x26000000),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTripDropdown = true }
                        .testTag("dashboard_trip_dropdown_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.FlightTakeoff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AppStrings.selectedTripBadge(lang),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCCFBF1)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = uiState.currentTrip?.title ?: AppStrings.noTripSelected(lang),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    uiState.currentTrip?.let { curr ->
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0x33FFFFFF)
                                        ) {
                                            Text(
                                                text = curr.baseCurrency,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFCCFBF1),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = uiState.currentTrip?.description ?: AppStrings.tapToSwitchTrip(lang),
                                    fontSize = 11.sp,
                                    color = Color(0xCCFFFFFF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Dropdown Arrow Indicator Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x33FFFFFF)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = AppStrings.switchTripBtn(lang),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = AppStrings.dropdownListCd(lang),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Dropdown Menu List
                DropdownMenu(
                    expanded = showTripDropdown,
                    onDismissRequest = { showTripDropdown = false },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    // Header of the dropdown list
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AppStrings.selectBusinessTripTitle(lang),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = AppStrings.tripsCountLabel(uiState.allTrips.size, lang),
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (uiState.allTrips.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = AppStrings.noTripsInList(lang),
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            },
                            onClick = { showTripDropdown = false }
                        )
                    } else {
                        uiState.allTrips.forEach { trip ->
                            val isSelected = trip.id == uiState.currentTrip?.id
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = trip.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isSelected) EmeraldPrimaryContainer else Color(0xFFF1F5F9)
                                                ) {
                                                    Text(
                                                        text = trip.baseCurrency,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) EmeraldOnPrimaryContainer else Color(0xFF475569),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (trip.description.isNotBlank()) trip.description else AppStrings.localStorageFallback(lang),
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    showTripDropdown = false
                                                    onEditTrip(trip)
                                                },
                                                modifier = Modifier.size(28.dp).testTag("dropdown_edit_trip_${trip.id}")
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = AppStrings.editTripCd(lang), tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    showTripDropdown = false
                                                    onDeleteTrip(trip)
                                                },
                                                modifier = Modifier.size(28.dp).testTag("dropdown_delete_trip_${trip.id}")
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = AppStrings.deleteTripCd(lang), tint = DangerRed, modifier = Modifier.size(15.dp))
                                            }

                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = AppStrings.selectedTripCd(lang),
                                                    tint = EmeraldPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectTrip(trip.id)
                                    showTripDropdown = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.FlightTakeoff else Icons.Outlined.WorkOutline,
                                        contentDescription = trip.title,
                                        tint = if (isSelected) EmeraldPrimary else Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Quick Actions in dropdown menu
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = AppStrings.createNewTripDropdownItem(lang),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.AddCircleOutline, contentDescription = AppStrings.createNewTripDropdownItem(lang), tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showTripDropdown = false
                            onOpenCreateTrip()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0x33FFFFFF))
            Spacer(modifier = Modifier.height(8.dp))

            // User Persona switcher bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenUserPicker() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = AppStrings.switchRoleAction(lang),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = AppStrings.youUserPrefix(uiState.currentMember?.name ?: AppStrings.guestName(lang), lang),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = AppStrings.switchRoleAction(lang),
                        fontSize = 11.sp,
                        color = Color(0xFFCCFBF1),
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFCCFBF1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}


// =========================================================================
// AppStrings Localization Extensions for DashboardScreen
// =========================================================================

fun AppStrings.noTripsTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa Có Đoàn Công Tác Nào" else "No Business Trips Yet"

fun AppStrings.noTripsDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Ứng dụng sạch sẽ và sẵn sàng sử dụng. Bắt đầu bằng cách tạo đoàn công tác mới để quản lý chi tiêu và chia tiền."
    else "Clean and ready to use. Start by creating a new business trip to manage expenses and splitting."

fun AppStrings.createTripBigBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tạo Đoàn Công Tác Mới" else "Create New Business Trip"

fun AppStrings.lockedBadgeCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã khóa" else "Locked"

fun AppStrings.tripSettledTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sổ Chuyến Đi Đã Được Khóa Quyết Toán" else "Trip Ledger Has Been Settled & Locked"

fun AppStrings.tripSettledDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mọi khoản chi tiêu và nộp quỹ đã được niêm phong an toàn để đối soát và bù trừ công nợ."
    else "All expenses and fund contributions are securely sealed for reconciliation and debt settlement."

fun AppStrings.userGuideCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hướng dẫn sử dụng" else "User Guide"

fun AppStrings.userGuideBannerTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cẩm Nang Hướng Dẫn Sử Dụng" else "User Guide & Handbook"

fun AppStrings.userGuideBannerDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xem quy trình 4 bước, cách chia tiền, bù trừ công nợ & ngoại tệ"
    else "Learn workflows, split methods, debt netting & currency rates"

fun AppStrings.aiSpendingInsights(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Gợi ý chi tiêu" else "Spending Insights"

fun AppStrings.aiSpendingInsightsDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Phân tích ngân sách, cơ cấu chi tiêu và sức khỏe quỹ ngoại tuyến"
    else "Analyze budget, category split & fund health offline"

fun AppStrings.aiAnalyzeBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Phân tích" else "Analyze"

fun AppStrings.financialOverviewTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tổng Quan Tài Chính" else "Financial Overview"

fun AppStrings.totalTripExpensesLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "TỔNG CHI TIÊU ĐOÀN" else "TOTAL TRIP EXPENSES"

fun AppStrings.detailsBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chi tiết" else "Details"

fun AppStrings.memberPaidSource(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên chi hộ (Paid)" else "Member Paid"

fun AppStrings.fundPaidSource(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chi từ Quỹ chung" else "Fund Paid"

fun AppStrings.groupFundBalanceLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "SỐ DƯ QUỸ CHUNG" else "GROUP FUND BALANCE"

fun AppStrings.groupFundBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quỹ đoàn" else "Group Fund"

fun AppStrings.fundCollectedPrefix(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã thu quỹ: $amountStr" else "Fund Collected: $amountStr"

fun AppStrings.fundPaidPrefix(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã chi quỹ: $amountStr" else "Fund Spent: $amountStr"

fun AppStrings.balanceStatusBalanced(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cân bằng" else "Balanced"

fun AppStrings.balanceStatusDiscrepancy(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chênh lệch" else "Discrepancy"

fun AppStrings.auditValidZeroBalance(currency: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đối soát hợp lệ: Tổng Balance = 0 ($currency)"
    else "Reconciled: Total Balance = 0 ($currency)"

fun AppStrings.auditWarningDiscrepancy(discrepancyStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cảnh báo: Chênh lệch $discrepancyStr"
    else "Warning: Discrepancy $discrepancyStr"

fun AppStrings.auditAlgorithmSafe(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thuật toán kiểm tra liên tục đảm bảo không thất thoát"
    else "Continuous audit algorithm ensures zero loss"

fun AppStrings.settlementActionBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quyết toán" else "Settlement"

fun AppStrings.quickAddExpense(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thêm Chi" else "Expense"

fun AppStrings.quickAddFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nộp Quỹ" else "Fund"

fun AppStrings.quickExportReport(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Báo Cáo" else "Report"

fun AppStrings.quickUserGuide(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hướng Dẫn" else "Guide"

fun AppStrings.categoryBreakdownTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Phân Loại Chi Tiêu" else "Category Breakdown"

fun AppStrings.expensesCountLabel(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "$count khoản chi" else "$count expenses"

fun AppStrings.memberBalancesTableTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bảng Số Dư Thành Viên" else "Member Balances Ledger"

fun AppStrings.viewSettlementBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xem quyết toán" else "View Settlement"

fun AppStrings.memberPaidAndOwed(paidStr: String, owedStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã chi: $paidStr • Phải trả: $owedStr"
    else "Paid: $paidStr • Owed: $owedStr"

fun AppStrings.switchUserRoleTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyển Đổi Người Dùng / Vai Trò" else "Switch User / Role"

fun AppStrings.switchUserRoleDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chọn nhân vật để trải nghiệm phân quyền đầy đủ (Admin, Thủ quỹ, Thành viên, Người xem):"
    else "Select persona to test role permissions (Admin, Treasurer, Member, Viewer):"

fun AppStrings.closeDialogBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đóng" else "Close"

fun AppStrings.localOfflineStorageBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "DỮ LIỆU OFFLINE CỤC BỘ" else "LOCAL OFFLINE STORAGE"

fun AppStrings.editTripInfoCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉnh sửa thông tin đoàn" else "Edit trip info"

fun AppStrings.deleteThisTripCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa đoàn này" else "Delete this trip"

fun AppStrings.createNewTripCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tạo đoàn mới" else "Create new trip"

fun AppStrings.selectedTripBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "ĐOÀN CÔNG TÁC ĐANG CHỌN" else "SELECTED TRIP"

fun AppStrings.noTripSelected(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa chọn đoàn nào" else "No trip selected"

fun AppStrings.tapToSwitchTrip(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chạm để đổi đoàn công tác" else "Tap to switch trip"

fun AppStrings.switchTripBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đổi đoàn" else "Switch"

fun AppStrings.dropdownListCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Danh sách xổ xuống" else "Dropdown menu"

fun AppStrings.selectBusinessTripTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chọn Đoàn Công Tác" else "Select Business Trip"

fun AppStrings.tripsCountLabel(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "$count đoàn" else "$count trips"

fun AppStrings.noTripsInList(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa có đoàn nào trong danh sách" else "No trips available"

fun AppStrings.localStorageFallback(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu trữ cục bộ" else "Local storage"

fun AppStrings.editTripCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sửa đoàn" else "Edit trip"

fun AppStrings.deleteTripCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa đoàn" else "Delete trip"

fun AppStrings.selectedTripCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đang chọn" else "Selected"

fun AppStrings.createNewTripDropdownItem(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "+ Tạo đoàn công tác mới" else "+ Create New Trip"

fun AppStrings.youUserPrefix(name: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bạn: $name" else "You: $name"

fun AppStrings.guestName(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khách" else "Guest"

fun AppStrings.switchRoleAction(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đổi vai trò" else "Switch role"
