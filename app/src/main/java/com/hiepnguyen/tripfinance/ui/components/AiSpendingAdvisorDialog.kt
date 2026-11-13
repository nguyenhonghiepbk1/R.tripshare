package com.hiepnguyen.tripfinance.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.theme.EmeraldPrimary
import com.hiepnguyen.tripfinance.ui.theme.extendedColorScheme

@Composable
fun AiSpendingAdvisorDialog(
    insight: String?,
    isLoading: Boolean,
    lang: AppLanguage = LocalAppLanguage.current,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalAppLanguage provides lang) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.80f)
                    .testTag("ai_advisor_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header with Insight Icon
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
                                    .background(EmeraldPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Insights,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = AppStrings.advisorDialogTitle(lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = AppStrings.advisorDialogSubtitle(lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = AppStrings.advisorCloseCd(lang))
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.extendedColorScheme.cardBorder
                    )

                    // Content
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = EmeraldPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = AppStrings.advisorLoadingText(lang),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.extendedColorScheme.textMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = insight ?: AppStrings.advisorEmptyText(lang),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Safety & Privacy Note
                    Surface(
                        color = MaterialTheme.extendedColorScheme.successContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.extendedColorScheme.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.advisorPrivacyNote(lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.extendedColorScheme.onSuccessContainer,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_advisor_close_button")
                    ) {
                        Text(AppStrings.advisorGotItCloseBtn(lang), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun AppStrings.advisorDialogTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Gợi ý chi tiêu" else "Spending Insights"

fun AppStrings.advisorDialogSubtitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Phân tích cục bộ 100% ngoại tuyến" else "100% offline local analysis"

fun AppStrings.advisorCloseCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đóng" else "Close"

fun AppStrings.advisorLoadingText(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đang phân tích dữ liệu tài chính đoàn..." else "Analyzing group financial data..."

fun AppStrings.advisorEmptyText(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa có thông tin phân tích." else "No analysis data available."

fun AppStrings.advisorPrivacyNote(lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "Gợi ý chi tiêu được xử lý cục bộ 100% trên thiết bị của bạn nhằm hỗ trợ quản lý tài chính đoàn minh bạch, không gửi dữ liệu ra mạng."
    else
        "Spending insights are processed 100% locally on your device to help keep trip finances transparent, with zero network data transmission."

fun AppStrings.advisorGotItCloseBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã Hiểu & Đóng" else "Got It & Close"
