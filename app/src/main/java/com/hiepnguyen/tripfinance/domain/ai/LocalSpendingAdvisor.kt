package com.hiepnguyen.tripfinance.domain.ai

import com.hiepnguyen.tripfinance.domain.model.CategoryBreakdown
import com.hiepnguyen.tripfinance.domain.model.FinancialSummary
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings

/**
 * Bộ phân tích và gợi ý chi tiêu tài chính ngoại tuyến (Local-First On-Device Engine).
 * Chạy 100% trên thiết bị, không cần mạng Internet, bảo mật tuyệt đối dữ liệu đoàn.
 */
object LocalSpendingAdvisor {

    fun generateLocalInsight(
        tripTitle: String,
        summary: FinancialSummary,
        categories: List<CategoryBreakdown>,
        members: List<MemberFinancialStatus>,
        lang: String = "vi"
    ): String {
        val appLang = if (lang.lowercase() == "en") AppLanguage.EN else AppLanguage.VI
        val topCategory = categories.maxByOrNull { it.totalAmount }
        val topCatText = topCategory?.let {
            val catLabel = AppStrings.getCategoryName(it.category, appLang)
            "$catLabel (${it.percentage.toInt()}%)"
        } ?: AppStrings.insightNoCategory(appLang)

        val fundStatus = if (summary.remainingFund >= 0) {
            AppStrings.insightFundSurplus(summary.remainingFund, appLang)
        } else {
            AppStrings.insightFundDeficit(-summary.remainingFund, appLang)
        }

        val balancedStatus = if (summary.isBalanced) {
            AppStrings.insightBalanced(appLang)
        } else {
            AppStrings.insightDiscrepancy(summary.balanceDiscrepancy, appLang)
        }

        return AppStrings.insightFullReport(
            tripTitle = tripTitle,
            topCatText = topCatText,
            fundPaid = summary.fundPaidExpenses,
            personalPaid = summary.personalPaidExpenses,
            fundStatus = fundStatus,
            balancedStatus = balancedStatus,
            pendingMemberCount = members.count { it.balance != 0L },
            lang = appLang
        )
    }
}

// --- AppStrings Localized Extensions for LocalSpendingAdvisor ---

fun AppStrings.insightNoCategory(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa có" else "None"

fun AppStrings.insightFundSurplus(amount: Long, lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "Quỹ đoàn đang thặng dư $amount đ, đáp ứng tốt các khoản chi chung sắp tới."
    else
        "Group fund has a surplus of $amount VND, well-prepared for upcoming expenses."

fun AppStrings.insightFundDeficit(amount: Long, lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "CẢNH BÁO: Quỹ đoàn đang bị âm $amount đ. Cần thu thêm quỹ đoàn gấp!"
    else
        "WARNING: Group fund is at a deficit of -$amount VND. Urgent fund collection needed!"

fun AppStrings.insightBalanced(lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "✅ Bút toán đối soát cân bằng hoàn hảo (Tổng số dư = 0đ)."
    else
        "✅ Perfectly reconciled (Total balance = 0 VND)."

fun AppStrings.insightDiscrepancy(amount: Long, lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "⚠️ Có chênh lệch đối soát $amount đ, hãy kiểm tra lại bảng phân bổ chi tiết."
    else
        "⚠️ Discrepancy detected: $amount VND, please check split distributions."

fun AppStrings.insightFullReport(
    tripTitle: String,
    topCatText: String,
    fundPaid: Long,
    personalPaid: Long,
    fundStatus: String,
    balancedStatus: String,
    pendingMemberCount: Int,
    lang: AppLanguage
): String {
    return if (lang == AppLanguage.VI) {
        """
        📊 PHÂN TÍCH TÀI CHÍNH ĐOÀN: $tripTitle

        1. Cơ cấu chi tiêu:
        • Danh mục chi tiêu chiếm tỷ trọng lớn nhất: $topCatText.
        • Chi từ quỹ chung: $fundPaid đ | Thành viên chi hộ: $personalPaid đ.

        2. Sức khỏe quỹ đoàn:
        • $fundStatus

        3. Đối soát & Quyết toán:
        • Trạng thái: $balancedStatus
        • Số lượng thành viên cần thanh toán: $pendingMemberCount người.
        """.trimIndent()
    } else {
        """
        📊 FINANCIAL ANALYSIS: $tripTitle

        1. Expense Structure:
        • Largest category: $topCatText.
        • Paid from Fund: $fundPaid VND | Personal: $personalPaid VND.

        2. Group Fund Health:
        • $fundStatus

        3. Reconciliation:
        • Status: $balancedStatus
        • Members with pending settlement: $pendingMemberCount.
        """.trimIndent()
    }
}
