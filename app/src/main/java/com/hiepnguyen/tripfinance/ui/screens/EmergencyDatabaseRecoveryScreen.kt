package com.hiepnguyen.tripfinance.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.theme.DangerRed
import com.hiepnguyen.tripfinance.ui.theme.EmeraldPrimary
import com.hiepnguyen.tripfinance.ui.viewmodel.DatabaseInitState

@Composable
fun EmergencyDatabaseRecoveryScreen(
    error: DatabaseInitState.Error,
    lang: AppLanguage = LocalAppLanguage.current,
    onRetry: () -> Unit,
    onRestoreBackup: (jsonContent: String, password: String?) -> Unit,
    onResetFresh: () -> Unit
) {
    BackHandler { onRetry() }
    val context = LocalContext.current
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var isReadingFile by remember { mutableStateOf(false) }
    var fileReadError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isReadingFile = true
            fileReadError = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isBlank()) {
                    fileReadError = AppStrings.recoveryEmptyFileError(lang)
                } else {
                    pendingBackupJson = content
                    val isEncrypted = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.isEncryptedBackup(content)
                    if (isEncrypted) {
                        showPasswordDialog = true
                    } else {
                        onRestoreBackup(content, null)
                    }
                }
            } catch (e: Exception) {
                fileReadError = AppStrings.recoveryReadError(e.message, lang)
            } finally {
                isReadingFile = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color(0xFFFEE2E2), CircleShape)
                    .border(2.dp, DangerRed.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = AppStrings.recoveryTitle(lang),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = AppStrings.recoverySubtitle(lang),
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AppStrings.recoveryDetailHeader(lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }

                    Text(
                        text = AppStrings.recoveryErrorCode(error.errorType.name, lang),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerRed,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = error.message,
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp
                    )

                    Text(
                        text = AppStrings.recoveryFailClosedNote(lang),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            AnimatedVisibility(visible = fileReadError != null) {
                fileReadError?.let {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = DangerRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = AppStrings.recoveryRetryBtn(lang))
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.recoveryRetryBtn(lang), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7))
            ) {
                if (isReadingFile) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.CloudUpload, contentDescription = AppStrings.recoveryRestoreBtn(lang))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.recoveryRestoreJsonBtn(lang), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            TextButton(
                onClick = { showResetConfirmDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = AppStrings.recoveryResetBtn(lang), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.recoveryResetBtn(lang), fontSize = 13.sp)
            }
        }
    }

    if (showPasswordDialog && pendingBackupJson != null) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                pendingBackupJson = null
                backupPassword = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.recoveryPasswordTitle(lang), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        AppStrings.recoveryPasswordPrompt(lang),
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text(AppStrings.recoveryPasswordLabel(lang)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = pendingBackupJson
                        val pass = backupPassword
                        showPasswordDialog = false
                        pendingBackupJson = null
                        backupPassword = ""
                        if (json != null) {
                            onRestoreBackup(json, pass.ifBlank { null })
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(AppStrings.recoveryUnlockRestoreBtn(lang))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    pendingBackupJson = null
                    backupPassword = ""
                }) {
                    Text(AppStrings.recoveryCancelBtn(lang))
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = DangerRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.recoveryConfirmResetTitle(lang), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    AppStrings.recoveryConfirmResetBody(lang),
                    fontSize = 13.sp,
                    color = Color(0xFF334155),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetFresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(AppStrings.recoveryConfirmResetBtn(lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(AppStrings.recoveryCancelBtn(lang))
                }
            }
        )
    }
}

fun AppStrings.recoveryEmptyFileError(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tệp sao lưu rỗng hoặc không thể đọc." else "Backup file is empty or unreadable."

fun AppStrings.recoveryReadError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khi đọc tệp sao lưu: ${err ?: ""}" else "Failed to read backup file: ${err ?: ""}"

fun AppStrings.recoveryTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bảo Vệ An Toàn Dữ Liệu" else "Data Security Protection"

fun AppStrings.recoverySubtitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hệ thống bảo mật phần cứng Android KeyStore hoặc thư viện mã hóa SQLCipher không thể mở khóa cơ sở dữ liệu."
    else "Android KeyStore hardware security or SQLCipher encryption could not unlock the database."

fun AppStrings.recoveryDetailHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chi tiết sự cố bảo mật (Fail-Closed):" else "Security Incident Details (Fail-Closed):"

fun AppStrings.recoveryErrorCode(code: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mã lỗi: $code" else "Error code: $code"

fun AppStrings.recoveryFailClosedNote(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Ứng dụng tuân thủ nguyên tắc bảo mật đóng (Fail-Closed), tuyệt đối không mở CSDL không mã hóa để ngăn ngừa rò rỉ thông tin tài chính của bạn."
    else "The app strictly follows a Fail-Closed security architecture and will never open an unencrypted database to prevent financial data leaks."

fun AppStrings.recoveryRetryBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thử Lại Mở Khóa" else "Retry Unlock"

fun AppStrings.recoveryRestoreBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khôi Phục Từ Tệp Sao Lưu" else "Restore From Backup"

fun AppStrings.recoveryRestoreJsonBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khôi Phục Từ Tệp Sao Lưu (.json)" else "Restore From Backup File (.json)"

fun AppStrings.recoveryResetBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa CSDL Hỏng & Tạo Mới Từ Đầu" else "Wipe Corrupted DB & Start Fresh"

fun AppStrings.recoveryPasswordTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mật Khẩu Sao Lưu" else "Backup Password"

fun AppStrings.recoveryPasswordPrompt(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bản sao lưu này đã được mã hóa bảo mật. Vui lòng nhập mật khẩu bạn đã đặt khi sao lưu để giải mã dữ liệu:"
    else "This backup is encrypted. Please enter the password used when creating the backup to decrypt your data:"

fun AppStrings.recoveryPasswordLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mật khẩu bảo vệ" else "Protection password"

fun AppStrings.recoveryUnlockRestoreBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mở Khóa & Phục Hồi" else "Unlock & Restore"

fun AppStrings.recoveryCancelBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hủy Bỏ" else "Cancel"

fun AppStrings.recoveryConfirmResetTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xác Nhận Xóa Dữ Liệu?" else "Confirm Data Wipe?"

fun AppStrings.recoveryConfirmResetBody(lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "Thao tác này sẽ xóa sạch CSDL hiện tại trên máy và tạo mới khóa bảo mật KeyStore hoàn toàn sạch sẽ. Toàn bộ dữ liệu chưa được sao lưu sẽ bị mất vĩnh viễn. Bạn có chắc chắn muốn tiếp tục?"
    else
        "This action will wipe the current database on this device and generate a fresh KeyStore security key. All unbacked-up data will be permanently lost. Are you sure you want to continue?"

fun AppStrings.recoveryConfirmResetBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xác Nhận Xóa & Tạo Mới" else "Confirm Wipe & Reset"
