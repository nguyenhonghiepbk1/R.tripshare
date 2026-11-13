package com.hiepnguyen.tripfinance.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.data.entity.AuditLogEntity
import com.hiepnguyen.tripfinance.data.entity.ExchangeRateEntity
import com.hiepnguyen.tripfinance.data.entity.TripEntity
import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.ui.components.RoleBadge
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.theme.*
import com.hiepnguyen.tripfinance.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersAndSettingsScreen(
    uiState: UiState,
    onAddMember: (name: String, role: String) -> Unit,
    onEditMember: (member: TripMemberEntity, name: String, role: String) -> Unit = { _, _, _ -> },
    onUpdateRole: (TripMemberEntity, String) -> Unit,
    onRemoveOrDeactivate: (TripMemberEntity) -> Unit,
    onUpdateRate: (currencyCode: String, rate: Double) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onOpenUserGuide: () -> Unit = {},
    onOpenBackupRestore: () -> Unit = {},
    onEditTrip: (TripEntity, String, String, Long, Long, String) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteTrip: (TripEntity) -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val clipboardManager = LocalClipboardManager.current
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberToEdit by remember { mutableStateOf<TripMemberEntity?>(null) }
    var memberToDelete by remember { mutableStateOf<TripMemberEntity?>(null) }
    var showEditTripDialog by remember { mutableStateOf(false) }
    var showDeleteTripDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showAuditLogsDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var copiedCodeNotice by remember { mutableStateOf<String?>(null) }

    val currentMember = uiState.currentMember
    val isAdmin = currentMember?.role == "ADMIN"
    val isTreasurer = currentMember?.role == "TREASURER"
    val isSettled = uiState.currentTrip?.isSettled == true
    val canManageMembers = !isSettled && (isAdmin || isTreasurer)
    val canViewAuditLogs = isAdmin || isTreasurer // SRS 5: Member KHÔNG được xem Audit Log kỹ thuật

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
    ) {
        // User Guide Quick Link
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUserGuide() }
                    .testTag("settings_open_user_guide_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.userGuideAndFaqTitle(lang),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = AppStrings.userGuideAndFaqDesc(lang),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        // Language Settings Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Translate,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.displayLanguageHeader(lang),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isVi = lang == AppLanguage.VI
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isVi) EmeraldPrimaryContainer else Color(0xFFF8FAFC),
                            border = BorderStroke(if (isVi) 1.5.dp else 1.dp, if (isVi) EmeraldPrimary else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectLanguage(AppLanguage.VI) }
                                .testTag("language_vi_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("🇻🇳", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.vietnameseLanguageName(lang),
                                    fontWeight = if (isVi) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isVi) EmeraldOnPrimaryContainer else Color(0xFF334155)
                                )
                                if (isVi) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        val isEn = lang == AppLanguage.EN
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isEn) EmeraldPrimaryContainer else Color(0xFFF8FAFC),
                            border = BorderStroke(if (isEn) 1.5.dp else 1.dp, if (isEn) EmeraldPrimary else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectLanguage(AppLanguage.EN) }
                                .testTag("language_en_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("🇬🇧", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "English",
                                    fontWeight = if (isEn) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isEn) EmeraldOnPrimaryContainer else Color(0xFF334155)
                                )
                                if (isEn) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Offline & Data Storage Notice Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.pureOfflineModeHeader(lang),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = AppStrings.pureOfflineModeDesc(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Backup & Recovery Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = IndigoSecondary.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, IndigoSecondary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBackupRestore() }
                    .testTag("open_backup_restore_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(IndigoSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CloudSync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = AppStrings.backupAndRecoveryTitle(lang),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = AppStrings.backupAndRecoveryDesc(lang),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = IndigoSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Privacy Policy Card (Google Play User Data Policy Compliance)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPrivacyPolicyDialog = true }
                    .testTag("open_privacy_policy_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = AppStrings.privacyPolicyAndSecurityTitle(lang),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = AppStrings.privacyPolicyAndSecurityDesc(lang),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Trip Information & Settings Card
        uiState.currentTrip?.let { trip ->
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_trip_info_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.FlightTakeoff,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = trip.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("${AppStrings.baseCurrencyLabel(lang)}: ${trip.baseCurrency}")
                                            if (trip.joinCode.isNotBlank()) {
                                                append(" • ${AppStrings.joinCodeLabel(trip.joinCode, lang)}")
                                            }
                                        },
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            if (isAdmin && !isSettled) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { showEditTripDialog = true },
                                        modifier = Modifier.size(32.dp).testTag("settings_edit_trip_button")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = AppStrings.editTripAction(lang), tint = IndigoSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { showDeleteTripDialog = true },
                                        modifier = Modifier.size(32.dp).testTag("settings_delete_trip_button")
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = AppStrings.deleteTripAction(lang), tint = DangerRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        if (trip.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = trip.description,
                                fontSize = 12.sp,
                                color = Color(0xFF475569),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Quick Management Utilities (Exchange Rates & Audit Logs)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showRateDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CurrencyExchange, contentDescription = AppStrings.exchangeRatesHeaderWithCount(uiState.exchangeRates.size, lang), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppStrings.exchangeRatesHeaderWithCount(uiState.exchangeRates.size, lang), fontSize = 12.sp)
                }

                Button(
                    onClick = { showAuditLogsDialog = true },
                    enabled = canViewAuditLogs,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.HistoryEdu, contentDescription = "Audit Log", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audit Log", fontSize = 12.sp)
                }
            }
        }

        // Members Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.tripMembersHeaderWithCount(uiState.members.size, lang),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (canManageMembers) {
                    FilledTonalButton(
                        onClick = { showAddMemberDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).testTag("add_member_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = AppStrings.add(lang), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.add(lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Member Cards List
        items(uiState.members, key = { it.id }) { member ->
            var expandedRoleMenu by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (member.isActive) MaterialTheme.colorScheme.surface else Color(0xFFF8FAFC)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                    .background(if (member.isActive) EmeraldPrimaryContainer else Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (member.isActive) EmeraldOnPrimaryContainer else Color(0xFF64748B),
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = member.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!member.isActive) {
                                    Text(
                                        text = AppStrings.deactivatedBadge(lang),
                                        fontSize = 10.sp,
                                        color = DangerRed
                                    )
                                }
                            }
                        }

                        // Role Tag / Role Selector
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = isAdmin) { expandedRoleMenu = true }
                            ) {
                                RoleBadge(role = member.role)
                                if (isAdmin) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = if (lang == AppLanguage.VI) "Đổi vai trò" else "Change role", modifier = Modifier.size(16.dp))
                                }
                            }

                            DropdownMenu(
                                expanded = expandedRoleMenu,
                                onDismissRequest = { expandedRoleMenu = false }
                            ) {
                                listOf(
                                    "ADMIN" to AppStrings.roleAdminLong(lang),
                                    "TREASURER" to AppStrings.roleTreasurerLong(lang),
                                    "MEMBER" to AppStrings.roleMemberLong(lang),
                                    "VIEWER" to AppStrings.roleViewerLong(lang)
                                ).forEach { (rKey, rLabel) ->
                                    DropdownMenuItem(
                                        text = { Text(rLabel) },
                                        onClick = {
                                            onUpdateRole(member, rKey)
                                            expandedRoleMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Action buttons (Edit & Delete for Admin)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        if (isAdmin) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { memberToEdit = member },
                                    modifier = Modifier.size(28.dp).testTag("edit_member_${member.id}")
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = AppStrings.edit(lang),
                                        tint = IndigoSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (member.id != currentMember?.id) {
                                    IconButton(
                                        onClick = { memberToDelete = member },
                                        modifier = Modifier.size(28.dp).testTag("delete_member_${member.id}")
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = AppStrings.delete(lang),
                                            tint = DangerRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Member Dialog
    memberToEdit?.let { mem ->
        EditMemberDialog(
            member = mem,
            onDismiss = { memberToEdit = null },
            onSave = { name, role ->
                onEditMember(mem, name, role)
                memberToEdit = null
            }
        )
    }

    // Delete Member Confirmation Dialog
    memberToDelete?.let { mem ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text(
                    text = AppStrings.confirmDeactivateMemberTitle(lang),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = AppStrings.confirmDeactivateMemberPrompt(mem.name, lang)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveOrDeactivate(mem)
                        memberToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_member_button")
                ) {
                    Text(AppStrings.confirm(lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text(AppStrings.cancel(lang))
                }
            }
        )
    }

    // Add Member Dialog
    if (showAddMemberDialog) {
        AddMemberDialog(
            onDismiss = { showAddMemberDialog = false },
            onSave = { name, role ->
                onAddMember(name, role)
                showAddMemberDialog = false
            }
        )
    }

    // Exchange Rates Dialog
    if (showRateDialog) {
        ExchangeRatesDialog(
            rates = uiState.exchangeRates,
            baseCurrency = uiState.currentTrip?.baseCurrency ?: "VND",
            canEdit = canManageMembers,
            onDismiss = { showRateDialog = false },
            onSave = { code, rate ->
                onUpdateRate(code, rate)
            }
        )
    }

    // Edit Trip Dialog
    if (showEditTripDialog) {
        uiState.currentTrip?.let { currentTrip ->
            EditTripDialog(
                trip = currentTrip,
                lang = lang,
                onDismiss = { showEditTripDialog = false },
                onConfirm = { title, desc, sDate, eDate, baseCurr ->
                    onEditTrip(currentTrip, title, desc, sDate, eDate, baseCurr)
                    showEditTripDialog = false
                }
            )
        }
    }

    // Delete Trip Dialog
    if (showDeleteTripDialog) {
        uiState.currentTrip?.let { currentTrip ->
            DeleteTripDialog(
                tripTitle = currentTrip.title,
                lang = lang,
                onDismiss = { showDeleteTripDialog = false },
                onConfirm = {
                    onDeleteTrip(currentTrip)
                    showDeleteTripDialog = false
                }
            )
        }
    }

    // Audit Logs Dialog
    if (showAuditLogsDialog) {
        AuditLogsDialog(
            logs = uiState.auditLogs,
            onDismiss = { showAuditLogsDialog = false }
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            lang = lang,
            onDismiss = { showPrivacyPolicyDialog = false }
        )
    }
}

@Composable
fun AddMemberDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, role: String) -> Unit
) {
    val lang = LocalAppLanguage.current
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("MEMBER") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    BackHandler { safeDismiss() }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = { 
            Text(
                text = AppStrings.addMemberToTripHeader(lang),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(AppStrings.fullNameFieldLabel(lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = AppStrings.roleFieldLabel(lang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "MEMBER" to AppStrings.roleMemberShort(lang),
                        "TREASURER" to AppStrings.roleTreasurerShort(lang),
                        "VIEWER" to if (lang == AppLanguage.VI) "Xem" else "Viewer"
                    ).forEach { (rKey, rLabel) ->
                        FilterChip(
                            selected = role == rKey,
                            onClick = { role = rKey },
                            label = { Text(rLabel, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSave(name, role)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(AppStrings.add(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text(AppStrings.cancel(lang)) }
        }
    )
}

@Composable
fun ExchangeRatesDialog(
    rates: List<ExchangeRateEntity>,
    baseCurrency: String = "VND",
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    val lang = LocalAppLanguage.current
    val availableCurrencies: List<String> = remember(baseCurrency) {
        com.hiepnguyen.tripfinance.domain.model.SupportedCurrencies.LIST.map { it.code }.filter { it != baseCurrency }
    }
    var selectedCode by remember(baseCurrency) {
        mutableStateOf(availableCurrencies.firstOrNull() ?: "USD")
    }
    val existingRate = rates.find { it.currencyCode == selectedCode }?.rateToBase
    var rateValueText by remember(selectedCode, baseCurrency) {
        mutableStateOf(existingRate?.toString() ?: "1.0")
    }

    val rateValidation = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate(rateValueText, selectedCode, baseCurrency)
    val isRateValid = rateValidation is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    BackHandler { safeDismiss() }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CurrencyExchange, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.exchangeRatesManagementTitle(lang),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = AppStrings.fixedRatesDescription(baseCurrency, lang),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                rates.forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1 ${r.currencyCode}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("= ${r.rateToBase} $baseCurrency", fontSize = 14.sp, color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (canEdit) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = AppStrings.updateNewRateLabel(lang),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (c in availableCurrencies.take(6)) {
                            FilterChip(
                                selected = selectedCode == c,
                                onClick = {
                                    selectedCode = c
                                    val r = rates.find { it.currencyCode == c }?.rateToBase
                                    rateValueText = r?.toString() ?: "1.0"
                                },
                                label = { Text(c, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = rateValueText,
                        onValueChange = { rateValueText = it },
                        isError = rateValueText.isNotEmpty() && !isRateValid,
                        supportingText = {
                            if (rateValueText.isNotEmpty() && rateValidation is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Error) {
                                Text(rateValidation.message, color = DangerRed, fontSize = 11.sp)
                            }
                        },
                        label = { Text("1 $selectedCode = ? $baseCurrency", color = Color(0xFFDC2626)) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFDC2626),
                            unfocusedTextColor = Color(0xFFDC2626),
                            focusedBorderColor = Color(0xFFDC2626),
                            unfocusedBorderColor = Color(0xFFF87171),
                            focusedLabelColor = Color(0xFFDC2626),
                            unfocusedLabelColor = Color(0xFFDC2626),
                            cursorColor = Color(0xFFDC2626)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val parsed = (rateValidation as? com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success)?.rate
                        if (parsed != null && parsed > 0) {
                            onSave(selectedCode, parsed)
                            onDismiss()
                        }
                    },
                    enabled = isRateValid,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(AppStrings.saveRateBtn(lang))
                }
            } else {
                TextButton(onClick = safeDismiss) { Text(AppStrings.close(lang)) }
            }
        },
        dismissButton = {
            if (canEdit) TextButton(onClick = safeDismiss) { Text(AppStrings.close(lang)) }
        }
    )
}

@Composable
fun AuditLogsDialog(
    logs: List<AuditLogEntity>,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.HistoryEdu, contentDescription = null, tint = IndigoSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.auditLogsHeader(lang),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (logs.isEmpty()) {
                Text(
                    text = AppStrings.noAuditLogsFound(lang),
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = NeutralLightSurfaceCard),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.action, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoSecondary)
                                    Text(dateFormat.format(Date(log.timestamp)), fontSize = 9.sp, color = Color(0xFF94A3B8))
                                }
                                Text(log.description, fontSize = 12.sp, color = Color(0xFF1E293B))
                                Text(
                                    text = AppStrings.performedByLabel(log.actorName, lang),
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.close(lang)) }
        }
    )
}

@Composable
fun EditMemberDialog(
    member: TripMemberEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String) -> Unit
) {
    val lang = LocalAppLanguage.current
    var name by remember { mutableStateOf(member.name) }
    var role by remember { mutableStateOf(member.role) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    BackHandler { safeDismiss() }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = IndigoSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.editMemberInfoHeader(lang),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(AppStrings.fullNameFieldLabel(lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("edit_member_name_input")
                )

                Text(
                    text = AppStrings.roleFieldLabel(lang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "ADMIN" to AppStrings.roleAdminShort(lang),
                        "TREASURER" to AppStrings.roleTreasurerShort(lang),
                        "MEMBER" to AppStrings.roleMemberShort(lang),
                        "VIEWER" to if (lang == AppLanguage.VI) "Xem" else "Viewer"
                    ).forEach { (rKey, rLabel) ->
                        FilterChip(
                            selected = role == rKey,
                            onClick = { role = rKey },
                            label = { Text(rLabel, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSave(name, role)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("save_edit_member_button")
            ) {
                Text(AppStrings.save(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text(AppStrings.cancel(lang)) }
        }
    )
}

@Composable
fun PrivacyPolicyDialog(
    lang: AppLanguage = LocalAppLanguage.current,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        icon = {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = AppStrings.privacyAndSafetyDialogHeader(lang),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = AppStrings.privacySection1Header(lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = AppStrings.privacySection1Content(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }

                item {
                    Text(
                        text = AppStrings.privacySection2Header(lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = AppStrings.privacySection2Content(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }

                item {
                    Text(
                        text = AppStrings.privacySection3Header(lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = AppStrings.privacySection3Content(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }

                item {
                    Text(
                        text = AppStrings.privacySection4Header(lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = AppStrings.privacySection4Content(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(AppStrings.close(lang))
            }
        }
    )
}


// =========================================================================
// AppStrings Localization Extensions for MembersAndSettingsScreen
// =========================================================================

fun AppStrings.userGuideAndFaqTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hướng Dẫn Sử Dụng & FAQ" else "User Guide & FAQ"

fun AppStrings.userGuideAndFaqDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cẩm nang chi tiết: tạo đoàn, chia tiền, quỹ, bù trừ công nợ, ngoại tệ"
    else "Detailed handbook: create trips, split costs, group fund, debt netting, currency"

fun AppStrings.displayLanguageHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "NGÔN NGỮ HIỂN THỊ (LANGUAGE)" else "DISPLAY LANGUAGE"

fun AppStrings.vietnameseLanguageName(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tiếng Việt" else "Vietnamese"

fun AppStrings.pureOfflineModeHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "CHẾ ĐỘ HOẠT ĐỘNG THUẦN OFFLINE" else "PURE OFFLINE OPERATION"

fun AppStrings.pureOfflineModeDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Ứng dụng hoạt động hoàn toàn offline, 100% dữ liệu tài chính, chi tiêu và quỹ được lưu trữ cục bộ, bảo mật tuyệt đối trên thiết bị của bạn. Không cần kết nối internet hay tài khoản."
    else "The app works 100% offline. All financial data, expenses, and funds are stored securely on your local device without needing internet or account login."

fun AppStrings.backupAndRecoveryTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sao Lưu & Khôi Phục CSDL" else "Backup & Data Recovery"

fun AppStrings.backupAndRecoveryDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xuất JSON, phục hồi khi nâng cấp ứng dụng hoặc đổi máy"
    else "Export JSON, restore upon app upgrade or device migration"

fun AppStrings.privacyPolicyAndSecurityTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chính Sách Quyền Riêng Tư & Bảo Mật" else "Privacy & Data Protection"

fun AppStrings.privacyPolicyAndSecurityDesc(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bảo vệ thông tin tài chính ngoại tuyến theo chuẩn Google Play"
    else "Protect offline financial data following Google Play policies"

fun AppStrings.baseCurrencyLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tiền tệ gốc" else "Base currency"

fun AppStrings.joinCodeLabel(code: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mã: $code" else "Code: $code"

fun AppStrings.editTripInfoAction(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sửa đoàn" else "Edit Trip"

fun AppStrings.editTripAction(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sửa chuyến đi" else "Edit Trip"

fun AppStrings.deleteTripAction(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa đoàn" else "Delete Trip"

fun AppStrings.exchangeRatesHeaderWithCount(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tỷ giá ($count)" else "Exchange Rates ($count)"

fun AppStrings.tripMembersHeaderWithCount(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Danh Sách Thành Viên ($count)" else "Trip Members ($count)"

fun AppStrings.deactivatedBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Đã ngừng hoạt động" else "• Deactivated"

fun AppStrings.roleAdminLong(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Trưởng đoàn (Admin)" else "Admin"

fun AppStrings.roleTreasurerLong(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thủ quỹ (Treasurer)" else "Treasurer"

fun AppStrings.roleMemberLong(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên (Member)" else "Member"

fun AppStrings.roleViewerLong(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Người xem (Viewer)" else "Viewer"

fun AppStrings.confirmDeactivateMemberTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xác Nhận Xóa / Vô Hiệu Hóa Thành Viên" else "Confirm Remove / Deactivate Member"

fun AppStrings.confirmDeactivateMemberPrompt(name: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bạn có chắc chắn muốn xử lý thành viên '$name'? Nếu thành viên đã có lịch sử chi tiêu hoặc nộp quỹ, hệ thống sẽ chuyển sang trạng thái Ngừng hoạt động để bảo toàn số liệu tài chính."
    else "Are you sure you want to handle member '$name'? If this member has expense or fund history, status will be set to Deactivated to preserve financial data integrity."

fun AppStrings.addMemberToTripHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thêm Thành Viên Vào Đoàn" else "Add Member To Trip"

fun AppStrings.fullNameFieldLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Họ và tên thành viên *" else "Full Name *"

fun AppStrings.roleFieldLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Vai trò:" else "Role:"

fun AppStrings.roleMemberShort(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên" else "Member"

fun AppStrings.roleTreasurerShort(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thủ quỹ" else "Treasurer"

fun AppStrings.exchangeRatesManagementTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quản Lý Tỷ Giá Ngoại Tệ" else "Exchange Rates Management"

fun AppStrings.fixedRatesDescription(baseCurrency: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tỷ giá quy đổi cố định theo đoàn (Gốc: $baseCurrency):"
    else "Trip fixed exchange rates (Base: $baseCurrency):"

fun AppStrings.updateNewRateLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cập nhật tỷ giá mới:" else "Update exchange rate:"

fun AppStrings.saveRateBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu tỷ giá" else "Save Rate"

fun AppStrings.auditLogsHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhật Ký Kiểm Toán (Audit Logs)" else "Audit Logs"

fun AppStrings.noAuditLogsFound(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa có bản ghi nhật ký nào." else "No audit log entries found."

fun AppStrings.performedByLabel(actorName: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thực hiện bởi: $actorName" else "By: $actorName"

fun AppStrings.editMemberInfoHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉnh Sửa Thông Tin Thành Viên" else "Edit Member Info"

fun AppStrings.roleAdminShort(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Trưởng đoàn" else "Admin"

fun AppStrings.privacyAndSafetyDialogHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chính Sách Quyền Riêng Tư & An Toàn Dữ Liệu" else "Privacy & Data Safety Policy"

fun AppStrings.privacySection1Header(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "1. Kiến trúc Sandbox Cục bộ (Local-First)" else "1. Local-First Sandbox Architecture"

fun AppStrings.privacySection1Content(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Ứng dụng TripFinance lưu trữ 100% dữ liệu chi tiêu, danh sách thành viên và quỹ đoàn trong cơ sở dữ liệu SQLite cục bộ được mã hóa theo phân vùng bảo mật riêng của ứng dụng (Android App Sandbox). Ứng dụng đã vô hiệu hóa sao lưu đám mây tự động (allowBackup=false) để bảo vệ toàn vẹn dữ liệu cá nhân."
    else "TripFinance stores 100% of expense, member, and fund records inside an isolated local SQLite database (Android App Sandbox). Automatic cloud backup is disabled (allowBackup=false) to ensure personal financial confidentiality."

fun AppStrings.privacySection2Header(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "2. Dữ liệu Tài chính & Quyền Riêng Tư" else "2. Financial Data & User Privacy"

fun AppStrings.privacySection2Content(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Toàn bộ thông tin tài chính và chi tiêu do người dùng nhập chỉ được lưu trữ cục bộ trên thiết bị để hỗ trợ đối soát giữa các thành viên. Hệ thống cam kết không bao giờ thu thập, bán hay chia sẻ thông tin cho bất kỳ bên thứ ba hay mạng quảng cáo nào."
    else "All financial records and expenses entered by users are strictly stored locally to support bookkeeping among members. TripFinance never transmits, sells, or monetizes this financial info to third parties or ad networks."

fun AppStrings.privacySection3Header(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "3. Tính năng Gợi Ý Chi Tiêu Ngoại Tuyến (Local-First)" else "3. Offline Spending Insights (Local-First)"

fun AppStrings.privacySection3Content(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tính năng 'Gợi ý chi tiêu' được phân tích hoàn toàn nội bộ và cục bộ 100% trên thiết bị của bạn bằng thuật toán đối soát tài chính trên máy. Ứng dụng không sử dụng mạng Internet, không gửi dữ liệu ra bên ngoài thiết bị. Mọi dữ liệu tài chính của đoàn luôn được bảo mật tuyệt đối."
    else "Spending Insights are computed 100% locally on your device using on-device bookkeeping algorithms without Internet connection or external data transmission. All trip data remains confidential."

fun AppStrings.privacySection4Header(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "4. Quyền Hạn Thiết Bị & Lưu Trữ (Zero-Permission SAF)" else "4. Device Permissions & Storage (Zero-Permission SAF)"

fun AppStrings.privacySection4Content(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "TripFinance tuân thủ chính sách bảo mật Google Play: Không yêu cầu quyền đọc/ghi bộ nhớ ngoài diện rộng (READ_EXTERNAL_STORAGE). Mọi thao tác xuất báo cáo PDF/Excel và sao lưu JSON đều sử dụng khung lưu trữ an toàn tiêu chuẩn Android Storage Access Framework (SAF)."
    else "TripFinance complies strictly with Google Play policies: Zero broad storage permissions (READ_EXTERNAL_STORAGE) required. PDF/Excel reports and JSON backup export use standard Android Storage Access Framework (SAF)."
