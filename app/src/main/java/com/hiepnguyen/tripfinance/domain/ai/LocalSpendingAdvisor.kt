package com.hiepnguyen.tripfinance.domain.ai

import com.hiepnguyen.tripfinance.domain.model.CategoryBreakdown
import com.hiepnguyen.tripfinance.domain.model.FinancialSummary
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus

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
        val topCategory = categories.maxByOrNull { it.totalAmount }
        val topCatText = topCategory?.let { "${it.labelVi} (${it.percentage.toInt()}%)" } ?: "Chưa có"
        val fundStatus = if (summary.remainingFund >= 0) {
            "Quỹ đoàn đang thặng dư ${summary.remainingFund} đ, đáp ứng tốt các khoản chi chung sắp tới."
        } else {
            "CẢNH BÁO: Quỹ đoàn đang bị âm ${-summary.remainingFund} đ. Cần thu thêm quỹ đoàn gấp!"
        }

        return if (lang == "vi") {
            """
            📊 PHÂN TÍCH TÀI CHÍNH ĐOÀN: $tripTitle

            1. Cơ cấu chi tiêu:
            • Danh mục chi tiêu chiếm tỷ trọng lớn nhất: $topCatText.
            • Chi từ quỹ chung: ${summary.fundPaidExpenses} đ | Thành viên chi hộ: ${summary.personalPaidExpenses} đ.

            2. Sức khỏe quỹ đoàn:
            • $fundStatus

            3. Đối soát & Quyết toán:
            • Trạng thái: ${if (summary.isBalanced) "✅ Bút toán đối soát cân bằng hoàn hảo (Tổng số dư = 0đ)." else "⚠️ Có chênh lệch đối soát ${summary.balanceDiscrepancy} đ, hãy kiểm tra lại bảng phân bổ chi tiết."}
            • Số lượng thành viên cần thanh toán: ${members.count { it.balance != 0L }} người.
            """.trimIndent()
        } else {
            """
            📊 FINANCIAL ANALYSIS: $tripTitle

            1. Expense Structure:
            • Largest category: $topCatText.
            • Paid from Fund: ${summary.fundPaidExpenses} VND | Personal: ${summary.personalPaidExpenses} VND.

            2. Group Fund Health:
            • $fundStatus

            3. Reconciliation:
            • Status: ${if (summary.isBalanced) "✅ Perfectly reconciled." else "⚠️ Discrepancy detected: ${summary.balanceDiscrepancy} VND."}
            • Members with pending settlement: ${members.count { it.balance != 0L }}.
            """.trimIndent()
        }
    }
}
