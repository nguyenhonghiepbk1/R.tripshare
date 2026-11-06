package com.hiepnguyen.tripfinance.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.model.BalanceStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.ui.theme.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage

object NumberFormatUtils {
    private val symbols = DecimalFormatSymbols(Locale.getDefault()).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val formatter = DecimalFormat("#,###", symbols)
    private val decimalFormatter = DecimalFormat("#,##0.##", symbols)

    fun formatVnd(amount: Long): String {
        return "${formatter.format(amount)} đ"
    }

    fun formatVnd(amount: Double): String {
        return "${formatter.format(kotlin.math.round(amount).toLong())} đ"
    }

    fun formatCurrency(amount: Long, currency: String): String {
        val curr = currency.uppercase()
        return when (curr) {
            "VND" -> "${formatter.format(amount)} đ"
            "USD" -> "$${formatter.format(amount)}"
            "EUR" -> "€${formatter.format(amount)}"
            "JPY" -> "¥${formatter.format(amount)}"
            "KRW" -> "₩${formatter.format(amount)}"
            "GBP" -> "£${formatter.format(amount)}"
            "SGD" -> "S$${formatter.format(amount)}"
            "THB" -> "${formatter.format(amount)} ฿"
            "CNY" -> "${formatter.format(amount)} ¥"
            "AUD" -> "A$${formatter.format(amount)}"
            else -> "${formatter.format(amount)} $currency"
        }
    }

    fun formatCurrency(amount: Double, currency: String): String {
        val curr = currency.uppercase()
        return when (curr) {
            "VND" -> "${formatter.format(kotlin.math.round(amount).toLong())} đ"
            "USD" -> "$${decimalFormatter.format(amount)}"
            "EUR" -> "€${decimalFormatter.format(amount)}"
            "JPY" -> "¥${formatter.format(kotlin.math.round(amount).toLong())}"
            "KRW" -> "₩${formatter.format(kotlin.math.round(amount).toLong())}"
            "GBP" -> "£${decimalFormatter.format(amount)}"
            "SGD" -> "S$${decimalFormatter.format(amount)}"
            "THB" -> "${decimalFormatter.format(amount)} ฿"
            "CNY" -> "${decimalFormatter.format(amount)} ¥"
            "AUD" -> "A$${decimalFormatter.format(amount)}"
            else -> "${decimalFormatter.format(amount)} $currency"
        }
    }
}

@Composable
fun CategoryIcon(category: String, modifier: Modifier = Modifier, size: Int = 20) {
    val (icon, bgColor, tintColor) = when (category) {
        "FOOD" -> Triple(Icons.Filled.Restaurant, Color(0xFFFEF3C7), Color(0xFFD97706))
        "TRANSPORT" -> Triple(Icons.Filled.DirectionsCar, Color(0xFFDBEAFE), Color(0xFF2563EB))
        "HOTEL" -> Triple(Icons.Filled.Hotel, Color(0xFFF3E8FF), Color(0xFF7E22CE))
        "SIGHTSEEING" -> Triple(Icons.Filled.ConfirmationNumber, Color(0xFFDCFCE7), Color(0xFF16A34A))
        "ENTERTAINMENT" -> Triple(Icons.Filled.Celebration, Color(0xFFFFE4E6), Color(0xFFE11D48))
        "SHOPPING" -> Triple(Icons.Filled.ShoppingBag, Color(0xFFEDE9FE), Color(0xFF6D28D9))
        else -> Triple(Icons.Filled.ReceiptLong, Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category,
            tint = tintColor,
            modifier = Modifier.size(size.dp)
        )
    }
}

@Composable
fun RoleBadge(role: String, modifier: Modifier = Modifier) {
    val lang = LocalAppLanguage.current
    val (text, bg, textColor) = when (role) {
        "ADMIN" -> Triple(AppStrings.badgeRoleAdmin(lang), Color(0xFFFEE2E2), Color(0xFF991B1B))
        "TREASURER" -> Triple(AppStrings.badgeRoleTreasurer(lang), Color(0xFFCCFBF1), Color(0xFF115E59))
        "MEMBER" -> Triple(AppStrings.badgeRoleMember(lang), Color(0xFFE0E7FF), Color(0xFF3730A3))
        else -> Triple(AppStrings.badgeRoleViewer(lang), Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun BalanceChip(balance: Long, status: BalanceStatus, modifier: Modifier = Modifier, currency: String = "VND") {
    val lang = LocalAppLanguage.current
    val (text, bg, fg) = when (status) {
        BalanceStatus.RECEIVE -> Triple(
            "+${NumberFormatUtils.formatCurrency(balance, currency)} " + AppStrings.balanceStatusReceive(lang),
            Color(0xFFDCFCE7),
            Color(0xFF15803D)
        )
        BalanceStatus.PAY -> Triple(
            "${NumberFormatUtils.formatCurrency(balance, currency)} " + AppStrings.balanceStatusPay(lang),
            Color(0xFFFEE2E2),
            Color(0xFFB91C1C)
        )
        BalanceStatus.BALANCED -> Triple(
            "${NumberFormatUtils.formatCurrency(0L, currency)} " + AppStrings.balanceStatusBalanced(lang),
            Color(0xFFF1F5F9),
            Color(0xFF475569)
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// --- AppStrings Localized Extensions for UiComponents ---

fun AppStrings.badgeRoleAdmin(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Trưởng đoàn" else "Admin"

fun AppStrings.badgeRoleTreasurer(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thủ quỹ" else "Treasurer"

fun AppStrings.badgeRoleMember(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên" else "Member"

fun AppStrings.badgeRoleViewer(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Người xem" else "Viewer"

fun AppStrings.balanceStatusReceive(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "(Nhận lại)" else "(To receive)"

fun AppStrings.balanceStatusPay(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "(Cần nộp)" else "(To pay)"

fun AppStrings.balanceStatusBalanced(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "(Cân bằng)" else "(Balanced)"

