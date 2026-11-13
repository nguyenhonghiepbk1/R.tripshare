package com.hiepnguyen.tripfinance.domain.export

import com.hiepnguyen.tripfinance.data.entity.ExpenseEntity
import com.hiepnguyen.tripfinance.data.entity.ExpenseSplitEntity
import com.hiepnguyen.tripfinance.data.entity.FundContributionEntity
import com.hiepnguyen.tripfinance.data.entity.TripEntity
import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.model.FinancialSummary
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.domain.model.sumOfSafe
import com.hiepnguyen.tripfinance.ui.components.NumberFormatUtils
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class ReportType(val title: String, val description: String) {
    FULL_SETTLEMENT("Báo Cáo Tổng Hợp Quyết Toán", "Đầy đủ thống kê, số dư từng người, kế hoạch bù trừ công nợ & đối soát"),
    EXPENSE_LEDGER("Bảng Kê Chi Tiết Thu - Chi", "Danh sách từng khoản chi tiêu, người thanh toán và đóng góp quỹ"),
    TRANSFER_INSTRUCTIONS("Kế Hoạch Bù Trừ Công Nợ", "Vắn tắt người nộp, người nhận, số tiền và nội dung đối soát"),
    MEMBER_BREAKDOWN("Báo Cáo Phân Bổ Theo Từng Thành Viên", "Bảng kê chi tiết tiền đã chi và tiền chịu chi của mỗi cá nhân")
}

object ReportGenerator {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))

    fun generateTextReport(
        trip: TripEntity?,
        members: List<TripMemberEntity>,
        financialSummary: FinancialSummary,
        memberStatuses: List<MemberFinancialStatus>,
        settlementTransfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        fundContributions: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity> = emptyList(),
        reportType: ReportType,
        creatorName: String,
        language: AppLanguage = AppLanguage.VI
    ): String {
        val now = Date()
        val tripTitle = trip?.title ?: AppStrings.valDefaultTripTitle(language)
        val joinCode = trip?.joinCode?.takeIf { it.isNotBlank() }

        return when (reportType) {
            ReportType.FULL_SETTLEMENT -> generateFullSettlementReport(
                tripTitle, joinCode, members, financialSummary, memberStatuses, settlementTransfers, expenses, creatorName, now, language
            )
            ReportType.EXPENSE_LEDGER -> generateExpenseLedgerReport(
                tripTitle, joinCode, members, financialSummary, expenses, fundContributions, splits, creatorName, now, language
            )
            ReportType.TRANSFER_INSTRUCTIONS -> generateTransferInstructionsReport(
                tripTitle, joinCode, settlementTransfers, creatorName, now, language
            )
            ReportType.MEMBER_BREAKDOWN -> generateMemberBreakdownReport(
                tripTitle, joinCode, members, memberStatuses, creatorName, now, language
            )
        }
    }

    private fun generateFullSettlementReport(
        tripTitle: String,
        joinCode: String?,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        statuses: List<MemberFinancialStatus>,
        transfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        creatorName: String,
        date: Date,
        lang: AppLanguage
    ): String = buildString {
        val isVi = lang == AppLanguage.VI
        val fmt = if (isVi) dateFormat else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        appendLine("================================================================================")
        if (isVi) {
            appendLine("                       CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM")
            appendLine("                            Độc lập - Tự do - Hạnh phúc")
            appendLine("                                     -------o0o-------")
            appendLine()
            appendLine("                   BÁO CÁO TỔNG HỢP QUYẾT TOÁN TÀI CHÍNH")
            appendLine("                             ĐOÀN: ${tripTitle.uppercase()}")
            if (joinCode != null) appendLine("                               (Mã đoàn: $joinCode)")
        } else {
            appendLine("                  FINANCIAL SETTLEMENT & RECONCILIATION REPORT")
            appendLine("                             GROUP: ${tripTitle.uppercase()}")
            if (joinCode != null) appendLine("                               (Code: $joinCode)")
        }
        appendLine("================================================================================")
        if (isVi) {
            appendLine("Thời gian lập báo cáo : ${fmt.format(date)}")
            appendLine("Người lập báo cáo     : $creatorName")
            appendLine("Tổng số thành viên    : ${members.size} người")
            appendLine("Tình trạng đối soát   : ${if (summary.isBalanced) "HỢP LỆ (Tổng số dư cân bằng = 0 đ)" else "CẢNH BÁO LỆCH: " + NumberFormatUtils.formatVnd(summary.balanceDiscrepancy)}")
        } else {
            appendLine("Report Generated At   : ${fmt.format(date)}")
            appendLine("Prepared By           : $creatorName")
            appendLine("Total Members         : ${members.size} members")
            appendLine("Reconciliation Status : ${if (summary.isBalanced) "VALID (Total net balance = 0)" else "DISCREPANCY WARNING: " + NumberFormatUtils.formatVnd(summary.balanceDiscrepancy)}")
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine()
        if (isVi) {
            appendLine("I. TỔNG QUAN THU - CHI ĐOÀN")
            appendLine("  1. Tổng chi tiêu toàn đoàn       : ${NumberFormatUtils.formatVnd(summary.totalExpenses)}")
            appendLine("     - Thành viên tự chi hộ (Paid)  : ${NumberFormatUtils.formatVnd(summary.personalPaidExpenses)}")
            appendLine("     - Chi từ nguồn Quỹ chung đoàn  : ${NumberFormatUtils.formatVnd(summary.fundPaidExpenses)}")
            appendLine("  2. Tình hình Quỹ chung:")
            appendLine("     - Tổng tiền đã thu vào quỹ     : ${NumberFormatUtils.formatVnd(summary.totalFundCollected)}")
            appendLine("     - Tổng tiền quỹ đã giải ngân   : ${NumberFormatUtils.formatVnd(summary.fundPaidExpenses)}")
            appendLine("     - Số dư quỹ chung còn lại      : ${NumberFormatUtils.formatVnd(summary.remainingFund)}")
            appendLine()
            appendLine("II. BẢNG TỔNG HỢP SỐ DƯ & QUYẾT TOÁN TỪNG THÀNH VIÊN")
            appendLine(String.format("%-4s | %-18s | %-12s | %-12s | %-12s | %-18s", "STT", "Họ và tên", "Đã chi hộ", "Nộp quỹ", "Phải chịu", "Số dư / Trạng thái"))
        } else {
            appendLine("I. GROUP INCOME & EXPENSE OVERVIEW")
            appendLine("  1. Total Group Expenses          : ${NumberFormatUtils.formatVnd(summary.totalExpenses)}")
            appendLine("     - Personal Out-of-Pocket Paid  : ${NumberFormatUtils.formatVnd(summary.personalPaidExpenses)}")
            appendLine("     - Paid from Group Fund         : ${NumberFormatUtils.formatVnd(summary.fundPaidExpenses)}")
            appendLine("  2. Group Fund Summary:")
            appendLine("     - Total Fund Collected         : ${NumberFormatUtils.formatVnd(summary.totalFundCollected)}")
            appendLine("     - Total Disbursed from Fund    : ${NumberFormatUtils.formatVnd(summary.fundPaidExpenses)}")
            appendLine("     - Remaining Fund Balance       : ${NumberFormatUtils.formatVnd(summary.remainingFund)}")
            appendLine()
            appendLine("II. MEMBER BALANCE & SETTLEMENT SUMMARY")
            appendLine(String.format("%-4s | %-18s | %-12s | %-12s | %-12s | %-18s", "No.", "Full Name", "Paid", "Fund", "Owed", "Balance / Status"))
        }
        appendLine("--------------------------------------------------------------------------------")
        statuses.forEachIndexed { index, st ->
            val statusStr = if (isVi) {
                when {
                    st.balance > 0 -> "+${NumberFormatUtils.formatVnd(st.balance)} (Nhận lại)"
                    st.balance < 0 -> "${NumberFormatUtils.formatVnd(st.balance)} (Cần nộp)"
                    else -> "0 đ (Đã cân bằng)"
                }
            } else {
                when {
                    st.balance > 0 -> "+${NumberFormatUtils.formatVnd(st.balance)} (To receive)"
                    st.balance < 0 -> "${NumberFormatUtils.formatVnd(st.balance)} (To pay)"
                    else -> "0 (Settled)"
                }
            }
            appendLine(
                String.format(
                    "%-4d | %-18s | %-12s | %-12s | %-12s | %-18s",
                    index + 1,
                    st.member.name.take(18),
                    NumberFormatUtils.formatVnd(st.outOfPocketPaid),
                    NumberFormatUtils.formatVnd(st.fundContributed),
                    NumberFormatUtils.formatVnd(st.totalOwed),
                    statusStr
                )
            )
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine()
        appendLine(if (isVi) "III. KẾ HOẠCH BÙ TRỪ CÔNG NỢ THANH TOÁN (TỐI ƯU HÓA DÒNG TIỀN)" else "III. OPTIMAL DEBT SETTLEMENT PLAN (MIN-TRANSFERS)")
        if (transfers.isEmpty()) {
            appendLine(if (isVi) "  -> Tất cả các thành viên đã cân bằng tài chính. Không cần bù trừ thêm." else "  -> All members are financially balanced. No transfers needed.")
        } else {
            appendLine(
                if (isVi) "  (Thuật toán tham lam đã rút gọn còn ${transfers.size} giao dịch bù trừ tối thiểu):"
                else "  (Simplified to ${transfers.size} minimum settlement transfers):"
            )
            appendLine()
            val fundLabel = AppStrings.valGroupFundFull(lang)
            transfers.forEachIndexed { idx, tr ->
                val fromName = if (tr.fromMember.id == "FUND_ORGANIZATION") fundLabel else tr.fromMember.name
                val toName = if (tr.toMember.id == "FUND_ORGANIZATION") fundLabel else tr.toMember.name
                if (isVi) {
                    appendLine("  [Giao dịch #${idx + 1}]")
                    appendLine("  • Người chuyển (Nộp) : $fromName")
                    appendLine("  • Người nhận tiền    : $toName")
                    appendLine("  • Số tiền            : ${NumberFormatUtils.formatVnd(tr.amount)}")
                    appendLine("  • Nội dung gợi ý     : ${tr.transferNote}")
                } else {
                    appendLine("  [Transfer #${idx + 1}]")
                    appendLine("  • Payer (From)       : $fromName")
                    appendLine("  • Recipient (To)     : $toName")
                    appendLine("  • Amount             : ${NumberFormatUtils.formatVnd(tr.amount)}")
                    appendLine("  • Suggested Note     : ${tr.transferNote}")
                }
                appendLine()
            }
        }
        appendLine(if (isVi) "IV. PHÂN LOẠI CHI TIÊU THEO DANH MỤC" else "IV. EXPENSE BREAKDOWN BY CATEGORY")
        expenses.groupBy { it.category }.forEach { (cat, list) ->
            val sum = list.sumOfSafe { it.convertedTotalAmount }
            val pct = if (summary.totalExpenses > 0) (sum * 100.0 / summary.totalExpenses) else 0.0
            val catLabel = AppStrings.getCategoryName(cat, lang)
            val suffix = if (isVi) "khoản" else "items"
            appendLine(String.format("  • %-20s: %15s (%4.1f%%) - %d %s", catLabel, NumberFormatUtils.formatVnd(sum), pct, list.size, suffix))
        }
        appendLine()
        appendLine(
            if (isVi) "Ghi chú: Đây là gợi ý bù trừ công nợ, ứng dụng không thực hiện giao dịch chuyển khoản."
            else "Note: These are settlement suggestions only; the app does not execute financial transfers."
        )
        appendLine()
        appendLine("================================================================================")
        if (isVi) {
            appendLine("                                   XÁC NHẬN")
            appendLine("           TRƯỞNG ĐOÀN                                THỦ QUỸ")
            appendLine("        (Ký và ghi rõ họ tên)                  (Ký và ghi rõ họ tên)")
        } else {
            appendLine("                                 APPROVAL")
            appendLine("           GROUP LEADER                              TREASURER")
            appendLine("       (Signature & Full Name)                 (Signature & Full Name)")
        }
        appendLine()
        appendLine()
        appendLine("================================================================================")
    }

    private fun generateExpenseLedgerReport(
        tripTitle: String,
        joinCode: String?,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        expenses: List<ExpenseEntity>,
        funds: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity>,
        creatorName: String,
        date: Date,
        lang: AppLanguage
    ): String = buildString {
        val isVi = lang == AppLanguage.VI
        val fmt = if (isVi) dateFormat else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        appendLine("================================================================================")
        if (isVi) {
            appendLine("                   BẢNG KÊ CHI TIẾT CÁC KHOẢN THU - CHI ĐOÀN")
            appendLine("                             ĐOÀN: ${tripTitle.uppercase()}" + if (joinCode != null) " (Mã: $joinCode)" else "")
            appendLine("================================================================================")
            appendLine("Ngày lập: ${fmt.format(date)} | Người lập: $creatorName")
            appendLine()
            appendLine("PHẦN 1: DANH SÁCH CHI TIÊU (${expenses.size} khoản chi)")
            appendLine(String.format("%-4s | %-14s | %-20s | %-14s | %-12s | %-10s", "STT", "Thời gian", "Tên khoản chi", "Nguồn chi", "Số tiền", "Kiểu chia"))
        } else {
            appendLine("                    DETAILED EXPENSE & GROUP FUND LEDGER")
            appendLine("                             GROUP: ${tripTitle.uppercase()}" + if (joinCode != null) " (Code: $joinCode)" else "")
            appendLine("================================================================================")
            appendLine("Generated: ${fmt.format(date)} | By: $creatorName")
            appendLine()
            appendLine("PART 1: EXPENSE LIST (${expenses.size} items)")
            appendLine(String.format("%-4s | %-14s | %-20s | %-14s | %-12s | %-10s", "No.", "Date/Time", "Expense Title", "Payer", "Amount", "Split"))
        }
        appendLine("--------------------------------------------------------------------------------")
        expenses.sortedBy { it.timestamp }.forEachIndexed { idx, exp ->
            val payer = if (exp.payerType == "FUND") AppStrings.valGroupFund(lang) else (members.find { it.id == exp.payerMemberId }?.name ?: if (isVi) "Cá nhân" else "Member")
            val splitMode = AppStrings.getSplitTypeName(exp.splitType, lang)
            appendLine(
                String.format(
                    "%-4d | %-14s | %-20s | %-14s | %-12s | %-10s",
                    idx + 1,
                    fmt.format(Date(exp.timestamp)),
                    exp.title.take(20),
                    payer.take(14),
                    NumberFormatUtils.formatVnd(exp.convertedTotalAmount),
                    splitMode.take(10)
                )
            )

            val expSplits = splits.filter { it.expenseId == exp.id }
            if (expSplits.isNotEmpty()) {
                val defaultMem = if (isVi) "Thành viên" else "Member"
                val participantsDetail = expSplits.joinToString("; ") { sp ->
                    val memName = members.find { it.id == sp.memberId }?.name ?: defaultMem
                    "$memName: ${NumberFormatUtils.formatVnd(sp.amount)}"
                }
                appendLine(
                    if (isVi) "       -> Người cùng chịu (${expSplits.size} người): $participantsDetail"
                    else "       -> Shared by (${expSplits.size} members): $participantsDetail"
                )
            }

            if (exp.note.isNotBlank()) {
                appendLine((if (isVi) "       -> Ghi chú: " else "       -> Note: ") + exp.note)
            }
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine((if (isVi) "TỔNG CỘNG CHI TIÊU: " else "TOTAL EXPENSES: ") + NumberFormatUtils.formatVnd(summary.totalExpenses))
        appendLine()
        if (isVi) {
            appendLine("PHẦN 2: LỊCH SỬ ĐÓNG GÓP QUỸ (${funds.size} đợt nộp)")
            appendLine(String.format("%-4s | %-14s | %-20s | %-16s | %-20s", "STT", "Thời gian", "Người nộp", "Số tiền", "Ghi chú"))
        } else {
            appendLine("PART 2: GROUP FUND CONTRIBUTIONS (${funds.size} entries)")
            appendLine(String.format("%-4s | %-14s | %-20s | %-16s | %-20s", "No.", "Date/Time", "Contributor", "Amount", "Notes"))
        }
        appendLine("--------------------------------------------------------------------------------")
        funds.sortedBy { it.timestamp }.forEachIndexed { idx, f ->
            val contributor = members.find { it.id == f.memberId }?.name ?: if (isVi) "Thành viên" else "Member"
            appendLine(
                String.format(
                    "%-4d | %-14s | %-20s | %-16s | %-20s",
                    idx + 1,
                    fmt.format(Date(f.timestamp)),
                    contributor.take(20),
                    NumberFormatUtils.formatVnd(f.convertedAmount),
                    f.note.take(20)
                )
            )
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine((if (isVi) "TỔNG CỘNG THU QUỸ: " else "TOTAL FUND COLLECTED: ") + NumberFormatUtils.formatVnd(summary.totalFundCollected))
        appendLine("================================================================================")
    }

    private fun generateTransferInstructionsReport(
        tripTitle: String,
        joinCode: String?,
        transfers: List<SettlementTransfer>,
        creatorName: String,
        date: Date,
        lang: AppLanguage
    ): String = buildString {
        val isVi = lang == AppLanguage.VI
        val fmt = if (isVi) dateFormat else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        appendLine("================================================================================")
        if (isVi) {
            appendLine("             HƯỚNG DẪN THANH TOÁN BÙ TRỪ QUYẾT TOÁN ĐOÀN")
            appendLine("                      ĐOÀN: ${tripTitle.uppercase()}" + if (joinCode != null) " (Mã: $joinCode)" else "")
            appendLine("================================================================================")
            appendLine("Ngày xuất: ${fmt.format(date)} | Tạo bởi: $creatorName")
        } else {
            appendLine("                 DEBT SETTLEMENT TRANSFER INSTRUCTIONS")
            appendLine("                      GROUP: ${tripTitle.uppercase()}" + if (joinCode != null) " (Code: $joinCode)" else "")
            appendLine("================================================================================")
            appendLine("Exported: ${fmt.format(date)} | Created by: $creatorName")
        }
        appendLine()
        if (transfers.isEmpty()) {
            appendLine(if (isVi) "Tất cả thành viên đã hoàn thành quyết toán cân bằng 100%. Không cần bù trừ thêm." else "All members are 100% settled. No additional transfers needed.")
        } else {
            val fundLabel = AppStrings.valGroupFundFull(lang)
            transfers.forEachIndexed { i, t ->
                val fromName = if (t.fromMember.id == "FUND_ORGANIZATION") fundLabel else t.fromMember.name
                val toName = if (t.toMember.id == "FUND_ORGANIZATION") fundLabel else t.toMember.name
                appendLine("--------------------------------------------------------------------------------")
                if (isVi) {
                    appendLine("KHOẢN BÙ TRỪ #${i + 1}:")
                    appendLine("  • Người gửi (Cần nộp) : $fromName")
                    appendLine("  • Người nhận (Hưởng)  : $toName")
                    appendLine("  • Số tiền             : ${NumberFormatUtils.formatVnd(t.amount)}")
                    appendLine("  • Nội dung gợi ý      : ${t.transferNote}")
                } else {
                    appendLine("SETTLEMENT #${i + 1}:")
                    appendLine("  • Payer (From)        : $fromName")
                    appendLine("  • Recipient (To)      : $toName")
                    appendLine("  • Amount              : ${NumberFormatUtils.formatVnd(t.amount)}")
                    appendLine("  • Suggested Note      : ${t.transferNote}")
                }
            }
            appendLine("--------------------------------------------------------------------------------")
            appendLine(
                if (isVi) "Lưu ý: Đây là gợi ý bù trừ công nợ, ứng dụng không thực hiện giao dịch chuyển khoản."
                else "Note: These are settlement suggestions only; the app does not execute financial transfers."
            )
        }
    }

    private fun generateMemberBreakdownReport(
        tripTitle: String,
        joinCode: String?,
        members: List<TripMemberEntity>,
        statuses: List<MemberFinancialStatus>,
        creatorName: String,
        date: Date,
        lang: AppLanguage
    ): String = buildString {
        val isVi = lang == AppLanguage.VI
        val fmt = if (isVi) dateFormat else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        appendLine("================================================================================")
        if (isVi) {
            appendLine("                 BÁO CÁO PHÂN BỔ CHI PHÍ TỪNG THÀNH VIÊN")
            appendLine("                      ĐOÀN: ${tripTitle.uppercase()}" + if (joinCode != null) " (Mã: $joinCode)" else "")
            appendLine("================================================================================")
            appendLine("Ngày xuất: ${fmt.format(date)} | Người lập: $creatorName")
        } else {
            appendLine("                   MEMBER COST ALLOCATION BREAKDOWN REPORT")
            appendLine("                      GROUP: ${tripTitle.uppercase()}" + if (joinCode != null) " (Code: $joinCode)" else "")
            appendLine("================================================================================")
            appendLine("Exported: ${fmt.format(date)} | By: $creatorName")
        }
        appendLine()
        statuses.forEachIndexed { idx, st ->
            appendLine("--------------------------------------------------------------------------------")
            if (isVi) {
                appendLine("THÀNH VIÊN #${idx + 1}: ${st.member.name.uppercase()} (Vai trò: ${AppStrings.getRoleName(st.member.role, lang)})")
                appendLine("  • Tiền cá nhân tự chi hộ (Out-of-pocket) : ${NumberFormatUtils.formatVnd(st.outOfPocketPaid)}")
                appendLine("  • Tiền đã nộp vào Quỹ chung              : ${NumberFormatUtils.formatVnd(st.fundContributed)}")
                appendLine("  • TỔNG CỘNG ĐÃ BỎ RA (A)                 : ${NumberFormatUtils.formatVnd(st.totalPaid)}")
                appendLine("  • TỔNG CHI PHÍ PHẢI CHỊU (B)             : ${NumberFormatUtils.formatVnd(st.totalOwed)}")
                appendLine("  • KẾT QUẢ SỐ DƯ (A - B)                  : ${NumberFormatUtils.formatVnd(st.balance)}")
                if (st.balance > 0) {
                    appendLine("  ==> KẾT LUẬN: ĐƯỢC NHẬN LẠI ${NumberFormatUtils.formatVnd(st.balance)}")
                } else if (st.balance < 0) {
                    appendLine("  ==> KẾT LUẬN: CẦN NỘP THÊM ${NumberFormatUtils.formatVnd(-st.balance)}")
                } else {
                    appendLine("  ==> KẾT LUẬN: ĐÃ CÂN BẰNG TÀI CHÍNH")
                }
            } else {
                appendLine("MEMBER #${idx + 1}: ${st.member.name.uppercase()} (Role: ${AppStrings.getRoleName(st.member.role, lang)})")
                appendLine("  • Personal Out-of-Pocket Paid            : ${NumberFormatUtils.formatVnd(st.outOfPocketPaid)}")
                appendLine("  • Contributed to Group Fund              : ${NumberFormatUtils.formatVnd(st.fundContributed)}")
                appendLine("  • TOTAL PAID (A)                         : ${NumberFormatUtils.formatVnd(st.totalPaid)}")
                appendLine("  • TOTAL COST SHARE / OWED (B)            : ${NumberFormatUtils.formatVnd(st.totalOwed)}")
                appendLine("  • NET BALANCE (A - B)                    : ${NumberFormatUtils.formatVnd(st.balance)}")
                if (st.balance > 0) {
                    appendLine("  ==> RESULT: TO RECEIVE ${NumberFormatUtils.formatVnd(st.balance)}")
                } else if (st.balance < 0) {
                    appendLine("  ==> RESULT: TO PAY ${NumberFormatUtils.formatVnd(-st.balance)}")
                } else {
                    appendLine("  ==> RESULT: FINANCIALLY SETTLED")
                }
            }
        }
        appendLine("================================================================================")
    }

    /**
     * Xuất báo cáo tài chính đoàn dưới định dạng Microsoft Excel (.xlsx) chuẩn OpenXML gồm 5 sheet:
     * 1. "Tổng quan": Thông tin chung, số liệu tổng hợp thu - chi, quỹ đoàn và tình trạng đối soát.
     * 2. "Chi tiêu": Toàn bộ các khoản chi tiêu (ngày, danh mục, người trả, tiền gốc, tiền tệ, tỷ giá, quy đổi VND, kiểu phân bổ, ghi chú).
     * 3. "Theo thành viên": Bảng số dư từng cá nhân (đã chi, nộp quỹ, tổng chi, phải chịu, số dư, trạng thái).
     * 4. "Quỹ chung": Nhật ký các đợt nộp quỹ của các thành viên.
     * 5. "Gợi ý chuyển khoản": Danh sách gợi ý bù trừ công nợ tối ưu (chỉ gồm người trả, người nhận, số tiền, nội dung gợi ý, đã chuyển).
     */
    fun generateXlsxReport(
        outputStream: OutputStream,
        trip: TripEntity?,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        statuses: List<MemberFinancialStatus>,
        settlementTransfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        funds: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity> = emptyList(),
        language: AppLanguage = AppLanguage.VI
    ) {
        val now = Date()
        val tripTitle = trip?.title ?: AppStrings.valDefaultTripTitle(language)
        val joinCode = trip?.joinCode ?: "---"

        XlsxWriter(outputStream).use { writer ->
            // ==========================================
            // Sheet 1: Tổng quan / Overview
            // ==========================================
            writer.sheet(AppStrings.sheetOverview(language)) {
                headerRow(
                    AppStrings.colIndicator(language),
                    AppStrings.colValue(language),
                    AppStrings.colNotes(language)
                )
                row(AppStrings.rowTripName(language), tripTitle, "")
                row(AppStrings.rowJoinCode(language), joinCode, "")
                row(AppStrings.rowExportDate(language), dateFormat.format(now), "")
                row {
                    cell(AppStrings.rowTotalExpenses(language))
                    currencyCell(summary.totalExpenses)
                    cell(AppStrings.rowTotalExpensesNote(language))
                }
                row {
                    cell(AppStrings.rowPersonalPaid(language))
                    currencyCell(summary.personalPaidExpenses)
                    cell(AppStrings.rowPersonalPaidNote(language))
                }
                row {
                    cell(AppStrings.rowFundPaid(language))
                    currencyCell(summary.fundPaidExpenses)
                    cell(AppStrings.rowFundPaidNote(language))
                }
                row {
                    cell(AppStrings.rowTotalFundCollected(language))
                    currencyCell(summary.totalFundCollected)
                    cell(AppStrings.rowTotalFundCollectedNote(language))
                }
                row {
                    cell(AppStrings.rowRemainingFund(language))
                    currencyCell(summary.remainingFund)
                    cell(AppStrings.rowRemainingFundNote(language))
                }
                row {
                    cell(AppStrings.rowReconciliationStatus(language))
                    val balancedStatus = if (summary.isBalanced) {
                        AppStrings.rowBalancedStatus(language)
                    } else {
                        AppStrings.rowDiscrepancyWarning(language) + NumberFormatUtils.formatVnd(summary.balanceDiscrepancy)
                    }
                    cell(balancedStatus)
                    cell(if (summary.isBalanced) AppStrings.rowStatusValid(language) else AppStrings.rowStatusNeedsCheck(language))
                }
                row {
                    cell(AppStrings.rowTotalMembers(language))
                    cell(members.size)
                    cell(AppStrings.rowUnitMembers(language))
                }
            }

            // ==========================================
            // Sheet 2: Chi tiêu / Expenses
            // ==========================================
            writer.sheet(AppStrings.sheetExpenses(language)) {
                headerRow(
                    AppStrings.colNo(language),
                    AppStrings.colDate(language),
                    AppStrings.colExpenseTitle(language),
                    AppStrings.colCategory(language),
                    AppStrings.colPayer(language),
                    AppStrings.colOriginalAmount(language),
                    AppStrings.colCurrency(language),
                    AppStrings.colExchangeRate(language),
                    AppStrings.colConvertedVnd(language),
                    AppStrings.colSplitType(language),
                    AppStrings.colNote(language)
                )
                expenses.sortedBy { it.timestamp }.forEachIndexed { i, e ->
                    val payer = if (e.payerType == "FUND") {
                        AppStrings.valGroupFund(language)
                    } else {
                        (members.find { it.id == e.payerMemberId }?.name ?: "")
                    }
                    val catName = AppStrings.getCategoryName(e.category, language)
                    val splitTypeName = AppStrings.getSplitTypeName(e.splitType, language)
                    row {
                        cell(i + 1)
                        cell(dateOnlyFormat.format(Date(e.timestamp)))
                        cell(e.title)
                        cell(catName)
                        cell(payer)
                        if (e.currency == "VND") {
                            currencyCell(e.totalAmount.toLong())
                        } else {
                            decimalCell(e.totalAmount)
                        }
                        cell(e.currency)
                        cell(e.exchangeRate)
                        currencyCell(e.convertedTotalAmount)
                        cell(splitTypeName)
                        cell(e.note)
                    }
                }
            }

            // ==========================================
            // Sheet 3: Theo thành viên / By Member
            // ==========================================
            writer.sheet(AppStrings.sheetMembers(language)) {
                headerRow(
                    AppStrings.colNo(language),
                    AppStrings.colFullName(language),
                    AppStrings.colRole(language),
                    AppStrings.colPersonalPaidHeader(language),
                    AppStrings.colFundContributedHeader(language),
                    AppStrings.colTotalPaidHeader(language),
                    AppStrings.colTotalOwedHeader(language),
                    AppStrings.colBalanceHeader(language),
                    AppStrings.colSettlementStatus(language)
                )
                statuses.forEachIndexed { i, s ->
                    val statusText = when {
                        s.balance > 0 -> AppStrings.valToReceive(language)
                        s.balance < 0 -> AppStrings.valToPay(language)
                        else -> AppStrings.valReconciled(language)
                    }
                    val roleName = AppStrings.getRoleName(s.member.role, language)
                    row {
                        cell(i + 1)
                        cell(s.member.name)
                        cell(roleName)
                        currencyCell(s.outOfPocketPaid)
                        currencyCell(s.fundContributed)
                        currencyCell(s.totalPaid)
                        currencyCell(s.totalOwed)
                        currencyCell(s.balance)
                        cell(statusText)
                    }
                }
            }

            // ==========================================
            // Sheet 4: Quỹ chung / Group Fund
            // ==========================================
            writer.sheet(AppStrings.sheetGroupFund(language)) {
                headerRow(
                    AppStrings.colNo(language),
                    AppStrings.colTime(language),
                    AppStrings.colContributor(language),
                    AppStrings.colOriginalAmount(language),
                    AppStrings.colCurrency(language),
                    AppStrings.colExchangeRate(language),
                    AppStrings.colConvertedVnd(language),
                    AppStrings.colNote(language)
                )
                funds.sortedBy { it.timestamp }.forEachIndexed { i, f ->
                    val contributor = members.find { it.id == f.memberId }?.name ?: ""
                    row {
                        cell(i + 1)
                        cell(dateFormat.format(Date(f.timestamp)))
                        cell(contributor)
                        currencyCell(f.amount)
                        cell(f.currency)
                        cell(f.exchangeRate)
                        currencyCell(f.convertedAmount)
                        cell(f.note)
                    }
                }
            }

            // ==========================================
            // Sheet 5: Gợi ý chuyển khoản / Settlement Suggestions
            // ==========================================
            writer.sheet(AppStrings.sheetSettlementSuggestions(language)) {
                headerRow(
                    AppStrings.colNo(language),
                    AppStrings.colSender(language),
                    AppStrings.colReceiver(language),
                    AppStrings.colAmountVnd(language),
                    AppStrings.colSuggestedNote(language),
                    AppStrings.colTransferred(language)
                )
                val fundName = AppStrings.valGroupFundFull(language)
                settlementTransfers.forEachIndexed { i, t ->
                    val fromName = if (t.fromMember.id == "FUND_ORGANIZATION") fundName else t.fromMember.name
                    val toName = if (t.toMember.id == "FUND_ORGANIZATION") fundName else t.toMember.name
                    val isTransferred = t.isCompleted && t.completedAmount != null && t.completedAmount == t.amount
                    val transferredText = if (isTransferred) AppStrings.valTransferredYes(language) else AppStrings.valTransferredNo(language)
                    row {
                        cell(i + 1)
                        cell(fromName)
                        cell(toName)
                        currencyCell(t.amount)
                        cell(t.transferNote)
                        cell(transferredText)
                    }
                }
            }
        }
    }
}

// --- AppStrings Localized Extensions for ReportGenerator (Excel Sheets & Columns) ---

fun AppStrings.sheetOverview(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng quan" else "Overview"
fun AppStrings.sheetExpenses(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chi tiêu" else "Expenses"
fun AppStrings.sheetMembers(lang: AppLanguage) = if (lang == AppLanguage.VI) "Theo thành viên" else "By Member"
fun AppStrings.sheetGroupFund(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quỹ chung" else "Group Fund"
fun AppStrings.sheetSettlementSuggestions(lang: AppLanguage) = if (lang == AppLanguage.VI) "Gợi ý chuyển khoản" else "Settlement Suggestions"

fun AppStrings.colIndicator(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chỉ tiêu" else "Indicator"
fun AppStrings.colValue(lang: AppLanguage) = if (lang == AppLanguage.VI) "Giá trị" else "Value"
fun AppStrings.colNotes(lang: AppLanguage) = if (lang == AppLanguage.VI) "Ghi chú" else "Notes"

fun AppStrings.rowTripName(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tên chuyến đi / đoàn" else "Trip / Group Name"
fun AppStrings.rowJoinCode(lang: AppLanguage) = if (lang == AppLanguage.VI) "Mã đoàn" else "Trip Join Code"
fun AppStrings.rowExportDate(lang: AppLanguage) = if (lang == AppLanguage.VI) "Ngày xuất báo cáo" else "Report Export Date"
fun AppStrings.rowTotalExpenses(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng chi tiêu đoàn (VND)" else "Total Group Expenses (VND)"
fun AppStrings.rowTotalExpensesNote(lang: AppLanguage) = if (lang == AppLanguage.VI) "Bao gồm tiền thành viên tự chi và tiền quỹ" else "Includes member out-of-pocket and fund expenses"
fun AppStrings.rowPersonalPaid(lang: AppLanguage) = if (lang == AppLanguage.VI) "Thành viên tự chi hộ (VND)" else "Personal Out-of-Pocket (VND)"
fun AppStrings.rowPersonalPaidNote(lang: AppLanguage) = if (lang == AppLanguage.VI) "Các khoản cá nhân bỏ tiền túi chi hộ" else "Expenses paid personally by members"
fun AppStrings.rowFundPaid(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chi từ nguồn Quỹ chung (VND)" else "Paid from Group Fund (VND)"
fun AppStrings.rowFundPaidNote(lang: AppLanguage) = if (lang == AppLanguage.VI) "Các khoản chi xuất trực tiếp từ quỹ đoàn" else "Expenses disbursed directly from group fund"
fun AppStrings.rowTotalFundCollected(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng tiền đã thu vào Quỹ (VND)" else "Total Fund Collected (VND)"
fun AppStrings.rowTotalFundCollectedNote(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng đóng góp từ các thành viên" else "Total contributions from members"
fun AppStrings.rowRemainingFund(lang: AppLanguage) = if (lang == AppLanguage.VI) "Số dư Quỹ chung còn lại (VND)" else "Remaining Group Fund (VND)"
fun AppStrings.rowRemainingFundNote(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tiền quỹ còn tồn sau khi đã chi" else "Remaining fund balance after spending"
fun AppStrings.rowReconciliationStatus(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tình trạng đối soát" else "Reconciliation Status"
fun AppStrings.rowBalancedStatus(lang: AppLanguage) = if (lang == AppLanguage.VI) "CÂN BẰNG (0 đ)" else "BALANCED (0 VND)"
fun AppStrings.rowDiscrepancyWarning(lang: AppLanguage) = if (lang == AppLanguage.VI) "CẢNH BÁO LỆCH: " else "WARNING DISCREPANCY: "
fun AppStrings.rowStatusValid(lang: AppLanguage) = if (lang == AppLanguage.VI) "Hợp lệ" else "Valid"
fun AppStrings.rowStatusNeedsCheck(lang: AppLanguage) = if (lang == AppLanguage.VI) "Cần kiểm tra lại" else "Needs Review"
fun AppStrings.rowTotalMembers(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng số thành viên" else "Total Members"
fun AppStrings.rowUnitMembers(lang: AppLanguage) = if (lang == AppLanguage.VI) "người" else "members"

fun AppStrings.colNo(lang: AppLanguage) = if (lang == AppLanguage.VI) "STT" else "No."
fun AppStrings.colDate(lang: AppLanguage) = if (lang == AppLanguage.VI) "Ngày" else "Date"
fun AppStrings.colExpenseTitle(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tên khoản chi" else "Expense Title"
fun AppStrings.colCategory(lang: AppLanguage) = if (lang == AppLanguage.VI) "Danh mục" else "Category"
fun AppStrings.colPayer(lang: AppLanguage) = if (lang == AppLanguage.VI) "Người trả" else "Payer"
fun AppStrings.colOriginalAmount(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tiền gốc" else "Original Amount"
fun AppStrings.colCurrency(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tiền tệ" else "Currency"
fun AppStrings.colExchangeRate(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tỷ giá" else "Exchange Rate"
fun AppStrings.colConvertedVnd(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quy đổi (VND)" else "Converted (VND)"
fun AppStrings.colSplitType(lang: AppLanguage) = if (lang == AppLanguage.VI) "Kiểu phân bổ" else "Split Method"
fun AppStrings.colNote(lang: AppLanguage) = if (lang == AppLanguage.VI) "Ghi chú" else "Notes"
fun AppStrings.valGroupFund(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quỹ chung" else "Group Fund"

fun AppStrings.colFullName(lang: AppLanguage) = if (lang == AppLanguage.VI) "Họ và tên" else "Full Name"
fun AppStrings.colRole(lang: AppLanguage) = if (lang == AppLanguage.VI) "Vai trò" else "Role"
fun AppStrings.colPersonalPaidHeader(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chi hộ cá nhân (VND)" else "Personal Out-of-Pocket (VND)"
fun AppStrings.colFundContributedHeader(lang: AppLanguage) = if (lang == AppLanguage.VI) "Nộp quỹ (VND)" else "Fund Contributed (VND)"
fun AppStrings.colTotalPaidHeader(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng đã chi (VND)" else "Total Paid (VND)"
fun AppStrings.colTotalOwedHeader(lang: AppLanguage) = if (lang == AppLanguage.VI) "Phải chịu (VND)" else "Total Owed (VND)"
fun AppStrings.colBalanceHeader(lang: AppLanguage) = if (lang == AppLanguage.VI) "Số dư (VND)" else "Balance (VND)"
fun AppStrings.colSettlementStatus(lang: AppLanguage) = if (lang == AppLanguage.VI) "Trạng thái quyết toán" else "Settlement Status"
fun AppStrings.valToReceive(lang: AppLanguage) = if (lang == AppLanguage.VI) "Được nhận lại" else "To Receive"
fun AppStrings.valToPay(lang: AppLanguage) = if (lang == AppLanguage.VI) "Cần nộp thêm" else "To Pay"
fun AppStrings.valReconciled(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đã cân bằng" else "Reconciled"

fun AppStrings.colTime(lang: AppLanguage) = if (lang == AppLanguage.VI) "Thời gian" else "Time"
fun AppStrings.colContributor(lang: AppLanguage) = if (lang == AppLanguage.VI) "Người nộp" else "Contributor"
fun AppStrings.colSender(lang: AppLanguage) = if (lang == AppLanguage.VI) "Người trả" else "Sender"
fun AppStrings.colReceiver(lang: AppLanguage) = if (lang == AppLanguage.VI) "Người nhận" else "Receiver"
fun AppStrings.colAmountVnd(lang: AppLanguage) = if (lang == AppLanguage.VI) "Số tiền (VND)" else "Amount (VND)"
fun AppStrings.colSuggestedNote(lang: AppLanguage) = if (lang == AppLanguage.VI) "Nội dung gợi ý" else "Suggested Note"
fun AppStrings.colTransferred(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đã chuyển" else "Transferred"
fun AppStrings.valTransferredYes(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đã chuyển" else "Transferred"
fun AppStrings.valTransferredNo(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chưa chuyển" else "Pending"
fun AppStrings.valGroupFundFull(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quỹ chung đoàn" else "Group Fund"
fun AppStrings.valDefaultTripTitle(lang: AppLanguage) = if (lang == AppLanguage.VI) "ĐOÀN CÔNG TÁC / DU LỊCH" else "BUSINESS TRIP / TOUR"


