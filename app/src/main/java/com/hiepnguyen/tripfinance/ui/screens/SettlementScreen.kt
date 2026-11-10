package com.hiepnguyen.tripfinance.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.data.entity.SettlementSnapshotEntity
import com.hiepnguyen.tripfinance.domain.model.BalanceStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.ui.components.BalanceChip
import com.hiepnguyen.tripfinance.ui.components.NumberFormatUtils
import com.hiepnguyen.tripfinance.ui.components.RoleBadge
import com.hiepnguyen.tripfinance.ui.theme.*
import com.hiepnguyen.tripfinance.ui.viewmodel.UiState
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    uiState: UiState,
    onFinalizeSettlement: (String) -> Unit,
    onReopenSettlement: () -> Unit = {},
    onToggleTransferCompleted: (SettlementTransfer) -> Unit = {},
    onOpenExportReport: () -> Unit
) {
    val lang = LocalAppLanguage.current
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var showReopenDialog by remember { mutableStateOf(false) }
    var selectedSnapshotForView by remember { mutableStateOf<SettlementSnapshotEntity?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val isAdmin = uiState.currentMember?.role == "ADMIN"
    val isBalanced = uiState.financialSummary.isBalanced
    val isSettled = uiState.currentTrip?.isSettled == true
    val tripCurrency = uiState.currentTrip?.baseCurrency ?: "VND"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
    ) {
        // Settlement Algorithm Overview Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountTree, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.optimalSettlementHeader(lang), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x33FFFFFF)
                        ) {
                            Text(
                                text = "${uiState.settlementTransfers.size}",
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.settlementSubtitleShort(lang),
                        fontSize = 11.sp,
                        color = Color(0xEEFFFFFF)
                    )
                }
            }
        }

        // Finalize Settlement & Export Report Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(AppStrings.lockAndExportCardTitle(lang), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (isSettled) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFEE2E2)
                                    ) {
                                        Text(
                                            text = AppStrings.lockedLedgerBadge(lang),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isSettled)
                                    AppStrings.lockedLedgerExplanation(lang)
                                else
                                    AppStrings.exportAndSnapshotHint(lang),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = onOpenExportReport,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = IndigoSecondaryContainer,
                                    contentColor = IndigoOnSecondaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("export_report_screen_button")
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = AppStrings.reportShortBtn(lang), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(AppStrings.reportShortBtn(lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (!isSettled) {
                                Button(
                                    onClick = { showFinalizeDialog = true },
                                    enabled = isAdmin && isBalanced && uiState.members.isNotEmpty() && uiState.financialSummary.balanceDiscrepancy == 0L,
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("finalize_settlement_button")
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = AppStrings.lockLedgerBtn(lang), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(AppStrings.lockLedgerBtn(lang), fontSize = 11.sp)
                                }
                            } else if (isAdmin) {
                                OutlinedButton(
                                    onClick = { showReopenDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("reopen_settlement_button")
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = AppStrings.unlockLedgerShortBtn(lang), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(AppStrings.unlockLedgerShortBtn(lang), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (!isSettled) {
                        if (!isAdmin) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(AppStrings.adminOnlyLockWarning(lang), fontSize = 10.sp, color = Color(0xFFEF4444))
                        }
                        if (uiState.members.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(AppStrings.noMembersCannotLock(lang), fontSize = 10.sp, color = Color(0xFFEF4444))
                        } else if (!isBalanced || uiState.financialSummary.balanceDiscrepancy != 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val msg = if (uiState.financialSummary.balanceDiscrepancy > 0) {
                                AppStrings.settlementDiscrepancyWarning(NumberFormatUtils.formatCurrency(uiState.financialSummary.balanceDiscrepancy, tripCurrency), lang)
                            } else {
                                AppStrings.settlementDiscrepancyGeneric(lang)
                            }
                            Text(msg, fontSize = 10.sp, color = Color(0xFFEF4444))
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            AppStrings.settlementLockedNotice(lang),
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Reconciliation Error Banner (Requested for Settlement precision)
        if (uiState.reconciliationError != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.extendedColorScheme.dangerContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.extendedColorScheme.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reconciliation_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = AppStrings.discrepancyWarningCd(lang),
                            tint = MaterialTheme.extendedColorScheme.danger,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = AppStrings.discrepancyHeader(lang),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.extendedColorScheme.onDangerContainer
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = uiState.reconciliationError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColorScheme.onDangerContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Simplified Transfer Instructions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.debtNettingPlanHeader(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (copiedNotice != null) {
                        Text(
                            text = copiedNotice!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.extendedColorScheme.success,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (uiState.settlementTransfers.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val text = uiState.settlementTransfers.joinToString("\n") { t ->
                                    "${t.fromMember.name} → ${t.toMember.name}: ${NumberFormatUtils.formatCurrency(t.amount, tripCurrency)}"
                                }
                                clipboardManager.setText(AnnotatedString(text))
                                copiedNotice = AppStrings.listCopiedNotice(lang)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = AppStrings.copyListCd(lang), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppStrings.copyShortBtn(lang), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (uiState.settlementTransfers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.reconciliationError != null) {
                            Icon(Icons.Filled.Warning, contentDescription = AppStrings.warningCd(lang), tint = MaterialTheme.extendedColorScheme.danger, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.reconciliationError!!,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.extendedColorScheme.danger,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                AppStrings.recheckDiscrepancyPrompt(NumberFormatUtils.formatCurrency(uiState.financialSummary.balanceDiscrepancy, tripCurrency), lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.extendedColorScheme.textMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else if (!isBalanced) {
                            Icon(Icons.Filled.Warning, contentDescription = AppStrings.warningCd(lang), tint = MaterialTheme.extendedColorScheme.danger, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(AppStrings.cannotSettleYet(lang), fontWeight = FontWeight.Bold, color = MaterialTheme.extendedColorScheme.danger)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                AppStrings.discrepancyRecheckNotice(NumberFormatUtils.formatCurrency(uiState.financialSummary.balanceDiscrepancy, tripCurrency), lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.extendedColorScheme.textMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = AppStrings.balancedCd(lang), tint = EmeraldPrimary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(AppStrings.allMembersBalancedTitle(lang), fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            Text(AppStrings.allMembersBalancedSubtitle(lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extendedColorScheme.textMuted)
                        }
                    }
                }
            }
        } else {
            itemsIndexed(uiState.settlementTransfers, key = { _, transfer -> transfer.id }) { index, transfer ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.transactionHeader(index + 1, lang),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = NumberFormatUtils.formatCurrency(transfer.amount, tripCurrency),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Transfer Flow: Debtor -> Creditor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Debtor
                            Column(modifier = Modifier.weight(1f)) {
                                val isFundSender = transfer.fromMember.id == "FUND_ORGANIZATION"
                                Text(
                                    text = if (isFundSender) AppStrings.fromGroupFund(lang) else AppStrings.senderPayer(lang),
                                    fontSize = 10.sp,
                                    color = if (isFundSender) IndigoSecondary else DangerRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = transfer.fromMember.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Icon(
                                Icons.Filled.ArrowForward,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )

                            // Creditor
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                val isFundRecipient = transfer.toMember.id == "FUND_ORGANIZATION"
                                Text(
                                    text = if (isFundRecipient) AppStrings.refundToGroupFund(lang) else AppStrings.receiverPayee(lang),
                                    fontSize = 10.sp,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = transfer.toMember.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.extendedColorScheme.cardBorder)
                        Spacer(modifier = Modifier.height(8.dp))

                        val isSettled = uiState.currentTrip?.isSettled == true
                        val isTransferred = transfer.isEffectivelyCompleted
                        val hasAmountChanged = transfer.isAmountChangedAfterMarking

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox "Đã chuyển" - chỉ cho đánh dấu khi đoàn đã khóa sổ
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .testTag("transfer_checkbox_row_${transfer.id}")
                                    .clickable(enabled = isSettled) { onToggleTransferCompleted(transfer) }
                            ) {
                                Checkbox(
                                    checked = isTransferred,
                                    onCheckedChange = { if (isSettled) onToggleTransferCompleted(transfer) },
                                    enabled = isSettled,
                                    modifier = Modifier.testTag("transfer_checkbox_${transfer.id}"),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = EmeraldPrimary,
                                        checkmarkColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(
                                        text = AppStrings.transferredCheckboxLabel(lang),
                                        fontSize = 13.sp,
                                        fontWeight = if (isTransferred) FontWeight.Bold else FontWeight.Normal,
                                        color = if (!isSettled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                else if (isTransferred) EmeraldPrimary
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!isSettled) {
                                        Text(
                                            text = AppStrings.lockTripToMarkTransferred(lang),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            if (transfer.transferNote.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(transfer.transferNote))
                                        copiedNotice = AppStrings.memoCopiedNotice(lang)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = AppStrings.copyCd(lang), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(AppStrings.copyCd(lang), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (hasAmountChanged) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = AppStrings.transferAmountChangedNotice(lang),
                                fontSize = 11.sp,
                                color = DangerRed,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (transfer.transferNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = AppStrings.transferMemoLabel(transfer.transferNote, lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Full Balance Summary Table
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = AppStrings.memberFinancialLedgerTitle(lang),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.memberStatuses.forEach { status ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(status.member.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    RoleBadge(role = status.member.role)
                                }
                                Text(
                                    text = AppStrings.memberBreakdownStats(NumberFormatUtils.formatCurrency(status.outOfPocketPaid, tripCurrency), NumberFormatUtils.formatCurrency(status.fundContributed, tripCurrency), NumberFormatUtils.formatCurrency(status.totalOwed, tripCurrency), lang),
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            BalanceChip(balance = status.balance, status = status.status, currency = tripCurrency)
                        }
                        if (status != uiState.memberStatuses.last()) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }
        }

        // Snapshots History Section (SRS Section 4 & 2.4)
        if (uiState.snapshots.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = AppStrings.snapshotHistoryTitle(lang),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(uiState.snapshots, key = { it.id }) { snap ->
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(snap.snapshotTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(AppStrings.lockedAtLabel(dateFormat.format(Date(snap.createdAt)), lang), fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(AppStrings.totalSpentLabel(NumberFormatUtils.formatCurrency(snap.totalExpenses, tripCurrency), lang), fontSize = 11.sp, color = EmeraldPrimary)
                        }

                        OutlinedButton(
                            onClick = { selectedSnapshotForView = snap },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(AppStrings.viewDetailBtn(lang), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Finalize Confirmation Dialog
    if (showFinalizeDialog) {
        var snapshotTitle by remember { mutableStateOf(AppStrings.defaultSnapshotTitle(uiState.currentTrip?.title ?: "", lang)) }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val safeDismiss = {
            focusManager.clearFocus()
            keyboardController?.hide()
            showFinalizeDialog = false
        }

        BackHandler { safeDismiss() }

        AlertDialog(
            onDismissRequest = safeDismiss,
            properties = DialogProperties(decorFitsSystemWindows = true),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = IndigoSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.confirmLockSettlementHeader(lang), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = AppStrings.lockSettlementExplanation(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = snapshotTitle,
                        onValueChange = { snapshotTitle = it },
                        label = { Text(AppStrings.snapshotNameLabel(lang)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onFinalizeSettlement(snapshotTitle)
                        showFinalizeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary)
                ) {
                    Text(AppStrings.saveSnapshotAndLockBtn(lang))
                }
            },
            dismissButton = {
                TextButton(onClick = safeDismiss) {
                    Text(AppStrings.cancelSettlementBtn(lang))
                }
            }
        )
    }

    // Reopen Settlement Dialog
    if (showReopenDialog) {
        BackHandler { showReopenDialog = false }
        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            icon = { Icon(Icons.Filled.LockOpen, contentDescription = null, tint = IndigoSecondary) },
            title = { Text(AppStrings.reopenTripLedgerHeader(lang), fontWeight = FontWeight.Bold) },
            text = {
                Text(AppStrings.reopenTripPrompt(uiState.currentTrip?.title ?: "", lang))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReopenSettlement()
                        showReopenDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary)
                ) {
                    Text(AppStrings.unlockLedgerBtn(lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) {
                    Text(AppStrings.cancelSettlementBtn(lang))
                }
            }
        )
    }

    // Snapshot View Dialog
    selectedSnapshotForView?.let { snap ->
        BackHandler { selectedSnapshotForView = null }
        val parsedJson = remember(snap.settlementJson) {
            try {
                org.json.JSONObject(snap.settlementJson)
            } catch (_: Exception) {
                null
            }
        }

        AlertDialog(
            onDismissRequest = { selectedSnapshotForView = null },
            properties = DialogProperties(decorFitsSystemWindows = true),
            title = {
                Column {
                    Text(snap.snapshotTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val dateFormatted = remember(snap.createdAt) {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("vi", "VN")).format(java.util.Date(snap.createdAt))
                    }
                    Text(AppStrings.sealedAtLabel(dateFormatted, lang), fontSize = 11.sp, color = Color(0xFF64748B))
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (parsedJson != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(AppStrings.sealedFinancialOverviewHeader(lang), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569))
                                    Text(AppStrings.sealedTotalExpenses(NumberFormatUtils.formatCurrency(snap.totalExpenses, tripCurrency), lang), fontSize = 12.sp)
                                    Text(AppStrings.sealedTotalFundCollected(NumberFormatUtils.formatCurrency(snap.totalFundCollected, tripCurrency), lang), fontSize = 12.sp)
                                    Text(AppStrings.sealedTotalFundSpent(NumberFormatUtils.formatCurrency(snap.totalFundSpent, tripCurrency), lang), fontSize = 12.sp)
                                    Text(AppStrings.sealedRemainingFund(NumberFormatUtils.formatCurrency(snap.remainingFund, tripCurrency), lang), fontSize = 12.sp)
                                }
                            }
                        }

                        val membersArray = parsedJson.optJSONArray("members")
                        if (membersArray != null && membersArray.length() > 0) {
                            item {
                                Text(AppStrings.sealedMemberBalancesHeader(lang), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                            items(membersArray.length(), key = { idx -> "snap_mem_$idx" }) { idx ->
                                val memObj = membersArray.getJSONObject(idx)
                                val name = memObj.optString("name", "TV")
                                val role = memObj.optString("role", "MEMBER")
                                val balance = memObj.optLong("balance", 0L)
                                val balText = if (balance > 0) AppStrings.sealedBalancePositive(NumberFormatUtils.formatCurrency(balance, tripCurrency), lang)
                                    else if (balance < 0) AppStrings.sealedBalanceNegative(NumberFormatUtils.formatCurrency(balance, tripCurrency), lang)
                                    else AppStrings.sealedBalanceZero(NumberFormatUtils.formatCurrency(0L, tripCurrency), lang)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$name ($role)", fontSize = 11.5.sp, color = Color(0xFF334155))
                                    Text(
                                        balText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (balance > 0) EmeraldPrimary else if (balance < 0) Color(0xFFEF4444) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        val transfersArray = parsedJson.optJSONArray("transfers")
                        if (transfersArray != null && transfersArray.length() > 0) {
                            item {
                                Text(AppStrings.sealedNettingPlanHeader(lang), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            items(transfersArray.length(), key = { idx -> "snap_transfer_$idx" }) { idx ->
                                val trObj = transfersArray.getJSONObject(idx)
                                val from = trObj.optString("fromMemberName")
                                val to = trObj.optString("toMemberName")
                                val amount = trObj.optLong("amount", 0L)
                                val note = trObj.optString("transferNote")
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("$from ➔ $to: ${NumberFormatUtils.formatCurrency(amount, tripCurrency)}", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFF1E40AF))
                                        if (note.isNotBlank()) {
                                            Text(AppStrings.sealedNoteLabel(note, lang), fontSize = 10.5.sp, color = Color(0xFF3B82F6))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(snap.settlementJson, fontSize = 12.sp, color = Color(0xFF1E293B))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSnapshotForView = null }) {
                    Text(AppStrings.closeSettlementBtn(lang))
                }
            }
        )
    }
}


// =========================================================================
// AppStrings Localization Extensions for SettlementScreen
// =========================================================================

fun AppStrings.settlementGuideOverview(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Ứng dụng tự động tính bù trừ công nợ tối ưu (Min-Transfers). Các giao dịch chỉ mang tính chất gợi ý đối soát nội bộ, ứng dụng không thực hiện chuyển khoản."
    else "• Smart min-transfers algorithm simplifies debts. Suggestions are for internal bookkeeping only; the app does not execute transfers."

fun AppStrings.settlementDiscrepancyWarning(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Phát hiện chênh lệch đối soát ($amountStr). Khoản chi phân bổ chưa đủ hoặc có sai lệch dữ liệu. Không thể khóa sổ!"
    else "• Audit discrepancy detected ($amountStr). Expenses are not fully allocated. Ledger cannot be locked!"

fun AppStrings.settlementDiscrepancyGeneric(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Tổng số dư đoàn bị lệch hoặc dữ liệu chưa hợp lệ. Không thể khóa sổ!"
    else "• Total group balance has discrepancy. Ledger cannot be locked!"

fun AppStrings.settlementLockedNotice(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "🔒 Sổ chi tiêu đã được khóa. Người dùng không thể thêm, sửa, xóa các khoản chi và nộp quỹ."
    else "🔒 Expense ledger is locked. No expenses or fund contributions can be added, edited, or deleted."

fun AppStrings.discrepancyWarningCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cảnh báo lệch đối soát" else "Audit discrepancy warning"

fun AppStrings.discrepancyHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lệch Đối Soát Quyết Toán" else "Audit Settlement Discrepancy"

fun AppStrings.debtNettingPlanHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Kế Hoạch Bù Trừ Công Nợ" else "Debt Netting Plan"

fun AppStrings.listCopiedNotice(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã sao chép danh sách!" else "Plan copied to clipboard!"

fun AppStrings.copyListCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sao chép danh sách" else "Copy plan"

fun AppStrings.copyShortBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sao chép" else "Copy"

fun AppStrings.warningCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cảnh báo" else "Warning"

fun AppStrings.recheckDiscrepancyPrompt(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chênh lệch đối soát: $amountStr. Vui lòng kiểm tra lại các khoản chi để phân bổ đủ tiền cho các thành viên."
    else "Audit discrepancy: $amountStr. Please review expenses to ensure full allocation among members."

fun AppStrings.cannotSettleYet(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa thể tạo quyết toán!" else "Cannot generate settlement yet!"

fun AppStrings.discrepancyRecheckNotice(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Phát hiện chênh lệch đối soát ($amountStr). Vui lòng kiểm tra lại các khoản chi chưa phân bổ đủ."
    else "Audit discrepancy detected ($amountStr). Please review unallocated expenses."

fun AppStrings.balancedCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã cân bằng" else "Balanced"

fun AppStrings.allMembersBalancedTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tất cả thành viên đã cân bằng!" else "All members are balanced!"

fun AppStrings.allMembersBalancedSubtitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tất cả thành viên đã cân bằng công nợ" else "All member balances are cleared"

fun AppStrings.transactionHeader(index: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "GIAO DỊCH #$index" else "TRANSACTION #$index"

fun AppStrings.fromGroupFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xuất từ Quỹ chung" else "Disbursed from Group Fund"

fun AppStrings.senderPayer(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Người chuyển (Cần nộp)" else "Sender (To Pay)"

fun AppStrings.refundToGroupFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nộp bù Quỹ chung" else "Refund to Group Fund"

fun AppStrings.receiverPayee(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Người nhận (Được nhận)" else "Receiver (To Receive)"

fun AppStrings.transferMemoLabel(note: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nội dung: $note" else "Memo: $note"

fun AppStrings.memoCopiedNotice(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã chép nội dung!" else "Memo copied!"

fun AppStrings.copyCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chép" else "Copy"

fun AppStrings.memberFinancialLedgerTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bảng Tổng Hợp Thu Chi Từng Người" else "Member Financial Summary Ledger"

fun AppStrings.memberBreakdownStats(paid: String, fund: String, owed: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chi hộ: $paid | Quỹ: $fund | Chịu chi: $owed"
    else "Paid on behalf: $paid | Fund: $fund | Share: $owed"

fun AppStrings.snapshotHistoryTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lịch Sử Snapshot Quyết Toán (Bất Biến)" else "Settlement Snapshot History (Immutable)"

fun AppStrings.lockedAtLabel(dateStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khóa lúc: $dateStr" else "Locked at: $dateStr"

fun AppStrings.totalSpentLabel(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tổng chi: $amountStr" else "Total Expenses: $amountStr"

fun AppStrings.viewDetailBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xem chi tiết" else "View Details"

fun AppStrings.defaultSnapshotTitle(tripTitle: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quyết toán kết thúc chuyến đi $tripTitle"
    else "Final settlement for trip $tripTitle"

fun AppStrings.confirmLockSettlementHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xác Nhận Khóa Sổ Quyết Toán" else "Confirm Final Settlement & Lock"

fun AppStrings.lockSettlementExplanation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sau khi khóa sổ, hệ thống sẽ tạo một bản Snapshot kế toán bất biến. Dữ liệu quyết toán sẽ được bảo toàn chính xác."
    else "After locking, the system generates an immutable accounting snapshot. Settlement records will be permanently preserved."

fun AppStrings.snapshotNameLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tên bản Snapshot" else "Snapshot Title"

fun AppStrings.saveSnapshotAndLockBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu Snapshot & Khóa" else "Save Snapshot & Lock"

fun AppStrings.cancelSettlementBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hủy" else "Cancel"

fun AppStrings.reopenTripLedgerHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mở Lại Sổ Chuyến Đi" else "Reopen Trip Ledger"

fun AppStrings.reopenTripPrompt(tripTitle: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bạn có chắc chắn muốn mở khóa sổ chuyến đi '$tripTitle'? Thao tác này sẽ cho phép Trưởng đoàn tiếp tục thêm, sửa, xóa các khoản chi và nộp quỹ."
    else "Are you sure you want to unlock trip ledger '$tripTitle'? This will allow adding, editing, and deleting expenses and fund contributions again."

fun AppStrings.unlockLedgerBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mở khóa sổ" else "Unlock Ledger"

fun AppStrings.sealedAtLabel(dateStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Niêm phong lúc: $dateStr" else "Sealed at: $dateStr"

fun AppStrings.sealedFinancialOverviewHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "TỔNG QUAN TÀI CHÍNH NIÊM PHONG" else "SEALED FINANCIAL OVERVIEW"

fun AppStrings.sealedTotalExpenses(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Tổng chi tiêu: $amountStr" else "• Total expenses: $amountStr"

fun AppStrings.sealedTotalFundCollected(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Tổng quỹ đã thu: $amountStr" else "• Total fund collected: $amountStr"

fun AppStrings.sealedTotalFundSpent(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Quỹ đã chi: $amountStr" else "• Fund spent: $amountStr"

fun AppStrings.sealedRemainingFund(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Quỹ còn lại: $amountStr" else "• Remaining fund: $amountStr"

fun AppStrings.sealedMemberBalancesHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "SỐ DƯ TỪNG THÀNH VIÊN" else "INDIVIDUAL MEMBER BALANCES"

fun AppStrings.sealedBalancePositive(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "+$amountStr (Nhận lại)" else "+$amountStr (To Receive)"

fun AppStrings.sealedBalanceNegative(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "$amountStr (Cần nộp)" else "$amountStr (To Pay)"

fun AppStrings.sealedBalanceZero(amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "$amountStr (Đã cân bằng)" else "$amountStr (Balanced)"

fun AppStrings.sealedNettingPlanHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "KẾ HOẠCH BÙ TRỪ QUYẾT TOÁN" else "SETTLEMENT NETTING PLAN"

fun AppStrings.sealedNoteLabel(note: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nội dung: $note" else "Memo: $note"

fun AppStrings.closeSettlementBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đóng" else "Close"


fun AppStrings.optimalSettlementHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quyết Toán Tối Ưu" else "Optimal Settlement"

fun AppStrings.settlementSubtitleShort(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "tối ưu hóa bù trừ công nợ giữa các thành viên đoàn."
    else "optimizes debt netting among trip members."

fun AppStrings.lockAndExportCardTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khóa Sổ & Xuất Báo Cáo Đoàn" else "Lock Ledger & Export Reports"

fun AppStrings.lockedLedgerBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "ĐÃ KHÓA SỔ" else "LOCKED LEDGER"

fun AppStrings.lockedLedgerExplanation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi đã khóa sổ quyết toán. Dữ liệu chi tiêu & quỹ được niêm phong."
    else "Trip ledger is locked. Expense & fund data are sealed."

fun AppStrings.exportAndSnapshotHint(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xuất báo cáo tiếng Việt (PDF / Excel .xlsx) và lưu snapshot quyết toán"
    else "Export reports (PDF / Excel .xlsx) and record accounting snapshots"

fun AppStrings.reportShortBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Báo cáo" else "Reports"

fun AppStrings.lockLedgerBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khóa sổ" else "Lock Ledger"

fun AppStrings.unlockLedgerShortBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mở khóa" else "Unlock"

fun AppStrings.adminOnlyLockWarning(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Chỉ Trưởng đoàn (Admin) mới có quyền khóa sổ chuyến đi"
    else "• Only the Group Leader (Admin) can lock the trip ledger"

fun AppStrings.noMembersCannotLock(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Đoàn chưa có thành viên nào. Không thể khóa sổ!"
    else "• No members in group. Cannot lock ledger!"

fun AppStrings.transferredCheckboxLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã chuyển" else "Transferred"

