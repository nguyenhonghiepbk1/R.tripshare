package com.hiepnguyen.tripfinance.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.theme.EmeraldPrimary
import com.hiepnguyen.tripfinance.ui.theme.IndigoSecondary
import com.hiepnguyen.tripfinance.ui.viewmodel.TripFinanceViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupRestoreDialog(
    viewModel: TripFinanceViewModel,
    lang: AppLanguage = LocalAppLanguage.current,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val localBackups by viewModel.localBackups.collectAsState()
    val dateFormat = remember(lang) {
        SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            if (lang == AppLanguage.VI) Locale("vi", "VN") else Locale.US
        )
    }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }
    var isClearExistingSelected by remember { mutableStateOf(false) }

    // Encryption states for backup creation & export
    var showCreateOptionsDialog by remember { mutableStateOf(false) }
    var isCreateExportMode by remember { mutableStateOf(false) }
    var encryptBackupCheck by remember { mutableStateOf(true) }
    var backupPasswordField by remember { mutableStateOf("") }
    var backupPasswordVisible by remember { mutableStateOf(false) }
    var activeExportPassword by remember { mutableStateOf<String?>(null) }

    // Encryption states for restore
    var isPendingEncrypted by remember { mutableStateOf(false) }
    var restorePasswordField by remember { mutableStateOf("") }
    var restorePasswordVisible by remember { mutableStateOf(false) }

    // Share warning for unencrypted backup
    var pendingShareFile by remember { mutableStateOf<File?>(null) }
    var showShareWarningDialog by remember { mutableStateOf(false) }

    // Launcher for exporting backup to a user-selected URI (SAF)
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackupToUri(context, uri, activeExportPassword)
        }
    }

    // Launcher for opening/restoring backup from a user-selected URI (SAF)
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            pendingRestoreFile = null
            isPendingEncrypted = viewModel.isEncryptedBackupUri(context, uri)
            restorePasswordField = ""
            showConfirmRestoreDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLocalBackups(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalAppLanguage provides lang) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(IndigoSecondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CloudSync,
                                contentDescription = null,
                                tint = IndigoSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = AppStrings.backupRestoreDialogTitle(lang),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = AppStrings.backupIntegritySubtitle(lang),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = AppStrings.closeBackupDialogCd(lang))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Information banner
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppStrings.backupMigrationInfo(lang),
                            fontSize = 11.sp,
                            color = Color(0xFF166534),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons: Create local, Export to file, Import from file
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isCreateExportMode = false
                            encryptBackupCheck = true
                            backupPasswordField = ""
                            showCreateOptionsDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = AppStrings.createLocalBackupBtn(lang), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.createLocalBackupBtn(lang), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isCreateExportMode = true
                            encryptBackupCheck = true
                            backupPasswordField = ""
                            showCreateOptionsDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = AppStrings.exportBackupFileBtn(lang), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.exportBackupFileBtn(lang), fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { pickDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = AppStrings.importBackupFileBtn(lang), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.importBackupFileBtn(lang), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of local backup files
                Text(
                    text = AppStrings.localBackupsHeaderWithCount(localBackups.size, lang),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (localBackups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.FolderZip,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                AppStrings.noLocalBackupsYet(lang),
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                AppStrings.tapCreateBackupHint(lang),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localBackups, key = { it.absolutePath }) { file ->
                            val fileSizeKb = (file.length() / 1024.0)
                            val modifiedDate = dateFormat.format(Date(file.lastModified()))
                            val isEncrypted = viewModel.isEncryptedBackupFile(file)

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = file.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isEncrypted) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFEFF6FF)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Lock,
                                                            contentDescription = AppStrings.aes256EncryptedCd(lang),
                                                            tint = Color(0xFF2563EB),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            "AES-256",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2563EB)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$modifiedDate • ${String.format(Locale.US, "%.1f KB", fileSizeKb)}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Share
                                        IconButton(
                                            onClick = {
                                                if (isEncrypted) {
                                                    viewModel.shareBackupFile(context, file)
                                                } else {
                                                    pendingShareFile = file
                                                    showShareWarningDialog = true
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Share,
                                                contentDescription = AppStrings.shareBackupFileCd(lang),
                                                tint = IndigoSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Restore
                                        Button(
                                            onClick = {
                                                pendingRestoreFile = file
                                                pendingRestoreUri = null
                                                isPendingEncrypted = isEncrypted
                                                restorePasswordField = ""
                                                showConfirmRestoreDialog = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Restore, contentDescription = AppStrings.restoreBackupBtn(lang), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(AppStrings.restoreBackupBtn(lang), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom close button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(AppStrings.closeBackupBtn(lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        }
    }

    // Dialog for creating / exporting backup with optional AES-GCM password encryption
    if (showCreateOptionsDialog) {
        val titleText = if (isCreateExportMode) AppStrings.exportBackupOptionsTitle(lang) else AppStrings.createBackupOptionsTitle(lang)
        AlertDialog(
            onDismissRequest = { showCreateOptionsDialog = false },
            icon = {
                Icon(
                    if (encryptBackupCheck) Icons.Filled.Lock else Icons.Filled.Security,
                    contentDescription = null,
                    tint = if (encryptBackupCheck) Color(0xFF2563EB) else EmeraldPrimary
                )
            },
            title = {
                Text(titleText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        AppStrings.backupScopeExplanation(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = encryptBackupCheck,
                                    onCheckedChange = { encryptBackupCheck = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        AppStrings.encryptWithPasswordLabel(lang),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        AppStrings.aesGcmRecommendedLabel(lang),
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF2563EB),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (encryptBackupCheck) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    AppStrings.encryptionBenefitsExplanation(lang),
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = backupPasswordField,
                                    onValueChange = { backupPasswordField = it },
                                    label = { Text(AppStrings.backupPasswordLabel(lang), fontSize = 12.sp) },
                                    placeholder = { Text(AppStrings.backupPasswordPlaceholder(lang), fontSize = 11.sp) },
                                    singleLine = true,
                                    isError = backupPasswordField.isNotEmpty() && backupPasswordField.length < 6,
                                    supportingText = {
                                        if (backupPasswordField.isNotEmpty() && backupPasswordField.length < 6) {
                                            Text(AppStrings.passwordMinLengthError(lang), color = Color(0xFFDC2626), fontSize = 11.sp)
                                        }
                                    },
                                    visualTransformation = if (backupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { backupPasswordVisible = !backupPasswordVisible }) {
                                            Icon(
                                                if (backupPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (backupPasswordVisible) (if (lang == AppLanguage.VI) "Ẩn mật khẩu" else "Hide password") else (if (lang == AppLanguage.VI) "Hiện mật khẩu" else "Show password"),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        AppStrings.unencryptedNotice(lang),
                                        color = Color(0xFF92400E),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(8.dp),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val isConfirmEnabled = !encryptBackupCheck || backupPasswordField.trim().length >= 6
                Button(
                    onClick = {
                        showCreateOptionsDialog = false
                        val pwd = if (encryptBackupCheck) backupPasswordField.trim() else null
                        if (isCreateExportMode) {
                            activeExportPassword = pwd
                            val defaultName = "tripfinance_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                            exportDocumentLauncher.launch(defaultName)
                        } else {
                            viewModel.createLocalBackup(context, pwd)
                        }
                    },
                    enabled = isConfirmEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(if (isCreateExportMode) AppStrings.proceedExportBtn(lang) else AppStrings.createBackupNowBtn(lang))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateOptionsDialog = false }) {
                    Text(AppStrings.cancelBackupActionBtn(lang))
                }
            }
        )
    }

    // Confirmation dialog before restoring
    if (showConfirmRestoreDialog) {
        val targetName = pendingRestoreFile?.name ?: pendingRestoreUri?.lastPathSegment ?: AppStrings.defaultBackupFileName(lang)
        val canConfirm = !isPendingEncrypted || restorePasswordField.trim().length >= 6

        AlertDialog(
            onDismissRequest = { showConfirmRestoreDialog = false },
            icon = {
                Icon(
                    if (isPendingEncrypted) Icons.Filled.Lock else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isPendingEncrypted) Color(0xFF2563EB) else Color(0xFFD97706)
                )
            },
            title = {
                Text(
                    text = if (isPendingEncrypted) AppStrings.enterRestorePasswordTitle(lang) else AppStrings.confirmRestoreDatabaseTitle(lang),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppStrings.aboutToRestorePrompt(targetName, lang), fontSize = 12.5.sp)

                    if (isPendingEncrypted) {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    AppStrings.encryptedRestoreNotice(lang),
                                    fontSize = 11.sp,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = restorePasswordField,
                            onValueChange = { restorePasswordField = it },
                            label = { Text(AppStrings.decryptPasswordLabel(lang), fontSize = 12.sp) },
                            singleLine = true,
                            isError = restorePasswordField.isNotEmpty() && restorePasswordField.trim().length < 6,
                            supportingText = {
                                if (restorePasswordField.isNotEmpty() && restorePasswordField.trim().length < 6) {
                                    Text(AppStrings.passwordMinLengthError(lang), color = Color(0xFFDC2626), fontSize = 11.sp)
                                }
                            },
                            visualTransformation = if (restorePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { restorePasswordVisible = !restorePasswordVisible }) {
                                    Icon(
                                        if (restorePasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (restorePasswordVisible) (if (lang == AppLanguage.VI) "Ẩn mật khẩu" else "Hide password") else (if (lang == AppLanguage.VI) "Hiện mật khẩu" else "Show password"),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(AppStrings.restoreIntegrationExplanation(lang), fontSize = 11.5.sp, color = Color(0xFF64748B))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = isClearExistingSelected,
                            onCheckedChange = { isClearExistingSelected = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            AppStrings.clearExistingBeforeRestoreCheckbox(lang),
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRestoreDialog = false
                        val pwd = if (isPendingEncrypted) restorePasswordField.trim().ifBlank { null } else null
                        if (pendingRestoreFile != null) {
                            viewModel.restoreBackupFromFile(context, pendingRestoreFile!!, isClearExistingSelected, pwd)
                        } else if (pendingRestoreUri != null) {
                            viewModel.restoreBackupFromUri(context, pendingRestoreUri!!, isClearExistingSelected, pwd)
                        }
                    },
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(AppStrings.proceedRestoreBtn(lang))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmRestoreDialog = false }) {
                    Text(AppStrings.cancelBackupActionBtn(lang))
                }
            }
        )
    }

    // Warning dialog when sharing unencrypted backup file
    if (showShareWarningDialog && pendingShareFile != null) {
        val fileToShare = pendingShareFile!!
        AlertDialog(
            onDismissRequest = {
                showShareWarningDialog = false
                pendingShareFile = null
            },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706)
                )
            },
            title = {
                Text(AppStrings.shareUnencryptedBackupTitle(lang), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        AppStrings.fileNotEncryptedWarning(fileToShare.name, lang),
                        fontSize = 12.5.sp,
                        color = Color(0xFF1E293B)
                    )
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            AppStrings.plainTextSecurityNotice(lang),
                            color = Color(0xFF92400E),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 15.sp
                        )
                    }
                    Text(
                        AppStrings.continueSharingPrompt(lang),
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShareWarningDialog = false
                        viewModel.shareBackupFile(context, fileToShare)
                        pendingShareFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(AppStrings.continueSharingBtn(lang))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showShareWarningDialog = false
                        pendingShareFile = null
                    }
                ) {
                    Text(AppStrings.cancelBackupActionBtn(lang))
                }
            }
        )
    }
}

// --- AppStrings Localized Extensions for BackupRestoreDialog ---

fun AppStrings.backupRestoreDialogTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sao Lưu & Phục Hồi" else "Backup & Recovery"

fun AppStrings.backupIntegritySubtitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bảo vệ toàn vẹn dữ liệu khi nâng cấp ứng dụng" else "Preserve data integrity across app updates"

fun AppStrings.closeBackupDialogCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đóng" else "Close"

fun AppStrings.backupMigrationInfo(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "CSDL đã được bảo vệ nâng cấp (Anti-destructive migration). Bạn có thể sao lưu ra tệp JSON để gửi qua Google Drive, Zalo hoặc lưu trữ dự phòng."
    else "Database protected by anti-destructive migration. Back up to JSON files for Google Drive, messaging apps, or offline safety."

fun AppStrings.createLocalBackupBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tạo sao lưu" else "Create Backup"

fun AppStrings.exportBackupFileBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xuất tệp" else "Export File"

fun AppStrings.importBackupFileBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhập tệp" else "Import File"

fun AppStrings.localBackupsHeaderWithCount(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "BẢN SAO LƯU TRÊN THIẾT BỊ ($count)" else "DEVICE LOCAL BACKUPS ($count)"

fun AppStrings.noLocalBackupsYet(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa có bản sao lưu nội bộ nào" else "No local backups found"

fun AppStrings.tapCreateBackupHint(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhấn 'Tạo sao lưu' ở trên để lưu snapshot toàn bộ CSDL" else "Tap 'Create Backup' above to snapshot current database"

fun AppStrings.aes256EncryptedCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mã hóa AES-256" else "AES-256 Encrypted"

fun AppStrings.shareBackupFileCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia sẻ" else "Share"

fun AppStrings.restoreBackupBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nạp lại" else "Restore"

fun AppStrings.closeBackupBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đóng" else "Close"

fun AppStrings.exportBackupOptionsTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tùy Chọn Xuất Tệp Sao Lưu" else "Export Backup Options"

fun AppStrings.createBackupOptionsTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tùy Chọn Tạo Bản Sao Lưu" else "Create Backup Options"

fun AppStrings.backupScopeExplanation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bản sao lưu sẽ đóng gói toàn bộ danh sách đoàn, thành viên, các khoản chi, quỹ và lịch sử đối soát."
    else "The backup packages all trips, members, expenses, group funds, and audit history."

fun AppStrings.encryptWithPasswordLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mã hóa bảo vệ bằng mật khẩu" else "Password-protected encryption"

fun AppStrings.aesGcmRecommendedLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuẩn AES-256-GCM + PBKDF2 (Khuyến nghị)" else "AES-256-GCM + PBKDF2 standard (Recommended)"

fun AppStrings.encryptionBenefitsExplanation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mã hóa giúp bảo vệ an toàn tuyệt đối cho toàn bộ dữ liệu tài chính và chi tiêu cá nhân của các thành viên đoàn."
    else "Encryption secures all financial transactions and private member spendings."

fun AppStrings.backupPasswordLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mật khẩu bảo vệ sao lưu (tối thiểu 6 ký tự)" else "Backup encryption password (min 6 chars)"

fun AppStrings.backupPasswordPlaceholder(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhập mật khẩu tự chọn (>= 6 ký tự)" else "Enter custom password (>= 6 chars)"

fun AppStrings.passwordMinLengthError(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mật khẩu phải có tối thiểu 6 ký tự" else "Password must be at least 6 characters"

fun AppStrings.unencryptedNotice(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu ý: Bản sao lưu không mã hóa có thể đọc được bằng trình soạn thảo văn bản. Nên đặt mật khẩu nếu tệp chứa dữ liệu chi tiêu nội bộ."
    else "Note: Unencrypted backups are readable via text editors. A password is recommended for private trip data."

fun AppStrings.proceedExportBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tiếp tục xuất" else "Proceed to Export"

fun AppStrings.createBackupNowBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tạo sao lưu ngay" else "Create Backup Now"

fun AppStrings.cancelBackupActionBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hủy bỏ" else "Cancel"

fun AppStrings.defaultBackupFileName(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tệp sao lưu" else "Backup File"

fun AppStrings.enterRestorePasswordTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhập Mật Khẩu Khôi Phục" else "Enter Decryption Password"

fun AppStrings.confirmRestoreDatabaseTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xác nhận khôi phục CSDL" else "Confirm Database Restore"

fun AppStrings.aboutToRestorePrompt(targetName: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bạn sắp nạp dữ liệu từ: $targetName." else "You are restoring data from: $targetName."

fun AppStrings.encryptedRestoreNotice(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tệp sao lưu này được mã hóa bảo vệ (AES-256-GCM). Vui lòng nhập đúng mật khẩu để giải mã."
    else "This backup is password-protected (AES-256-GCM). Please enter the correct password to decrypt."

fun AppStrings.decryptPasswordLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mật khẩu giải mã (tối thiểu 6 ký tự)" else "Decryption password (min 6 chars)"

fun AppStrings.restoreIntegrationExplanation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Dữ liệu các chuyến đi, thành viên, quỹ và khoản chi sẽ được tích hợp vào ứng dụng."
    else "All trips, members, funds, and expenses will be integrated into the app."

fun AppStrings.clearExistingBeforeRestoreCheckbox(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa sạch dữ liệu hiện tại trước khi khôi phục (Khuyến nghị nếu chuyển máy mới)"
    else "Wipe existing data before restore (Recommended when migrating to a new device)"

fun AppStrings.proceedRestoreBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tiến hành khôi phục" else "Proceed with Restore"

fun AppStrings.shareUnencryptedBackupTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia Sẻ Bản Sao Lưu Không Mã Hóa" else "Share Unencrypted Backup"

fun AppStrings.fileNotEncryptedWarning(fileName: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tệp '$fileName' không được bảo vệ bằng mật khẩu mã hóa."
    else "File '$fileName' is not password-protected."

fun AppStrings.plainTextSecurityNotice(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu ý an toàn: Tệp này ở định dạng văn bản rõ không mã hóa. Hãy đảm bảo bạn chỉ chia sẻ với các thành viên trong đoàn."
    else "Safety notice: This file is in plain text format. Only share it with trusted trip members."

fun AppStrings.continueSharingPrompt(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bạn có muốn tiếp tục gửi tệp này qua ứng dụng khác không?"
    else "Do you wish to continue sharing this file via external apps?"

fun AppStrings.continueSharingBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tiếp tục chia sẻ" else "Continue Sharing"
