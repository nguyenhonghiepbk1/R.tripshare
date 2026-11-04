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
        creatorName: String
    ): String {
        val now = Date()
        val tripTitle = trip?.title ?: "ĐOÀN CÔNG TÁC / DU LỊCH"
        val joinCode = trip?.joinCode ?: "---"

        return when (reportType) {
            ReportType.FULL_SETTLEMENT -> generateFullSettlementReport(
                tripTitle, joinCode, members, financialSummary, memberStatuses, settlementTransfers, expenses, creatorName, now
            )
            ReportType.EXPENSE_LEDGER -> generateExpenseLedgerReport(
                tripTitle, joinCode, members, financialSummary, expenses, fundContributions, splits, creatorName, now
            )
            ReportType.TRANSFER_INSTRUCTIONS -> generateTransferInstructionsReport(
                tripTitle, joinCode, settlementTransfers, creatorName, now
            )
            ReportType.MEMBER_BREAKDOWN -> generateMemberBreakdownReport(
                tripTitle, joinCode, members, memberStatuses, creatorName, now
            )
        }
    }

    private fun generateFullSettlementReport(
        tripTitle: String,
        joinCode: String,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        statuses: List<MemberFinancialStatus>,
        transfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        creatorName: String,
        date: Date
    ): String = buildString {
        appendLine("================================================================================")
        appendLine("                       CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM")
        appendLine("                            Độc lập - Tự do - Hạnh phúc")
        appendLine("                                     -------o0o-------")
        appendLine()
        appendLine("                   BÁO CÁO TỔNG HỢP QUYẾT TOÁN TÀI CHÍNH")
        appendLine("                             ĐOÀN: ${tripTitle.uppercase()}")
        appendLine("                               (Mã đoàn: $joinCode)")
        appendLine("================================================================================")
        appendLine("Thời gian lập báo cáo : ${dateFormat.format(date)}")
        appendLine("Người lập báo cáo     : $creatorName")
        appendLine("Tổng số thành viên    : ${members.size} người")
        appendLine("Tình trạng đối soát   : ${if (summary.isBalanced) "HỢP LỆ (Tổng số dư cân bằng = 0 đ)" else "CẢNH BÁO LỆCH: " + NumberFormatUtils.formatVnd(summary.balanceDiscrepancy)}")
        appendLine("--------------------------------------------------------------------------------")
        appendLine()
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
        appendLine("--------------------------------------------------------------------------------")
        statuses.forEachIndexed { index, st ->
            val statusStr = when {
                st.balance > 0 -> "+${NumberFormatUtils.formatVnd(st.balance)} (Nhận lại)"
                st.balance < 0 -> "${NumberFormatUtils.formatVnd(st.balance)} (Cần nộp)"
                else -> "0 đ (Đã cân bằng)"
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
        appendLine("III. KẾ HOẠCH BÙ TRỪ CÔNG NỢ THANH TOÁN (TỐI ƯU HÓA DÒNG TIỀN)")
        if (transfers.isEmpty()) {
            appendLine("  -> Tất cả các thành viên đã cân bằng tài chính. Không cần bù trừ thêm.")
        } else {
            appendLine("  (Thuật toán tham lam đã rút gọn còn ${transfers.size} giao dịch bù trừ tối thiểu):")
            appendLine()
            transfers.forEachIndexed { idx, tr ->
                val fromName = if (tr.fromMember.id == "FUND_ORGANIZATION") "Quỹ chung đoàn" else tr.fromMember.name
                val toName = if (tr.toMember.id == "FUND_ORGANIZATION") "Quỹ chung đoàn" else tr.toMember.name
                appendLine("  [Giao dịch #${idx + 1}]")
                appendLine("  • Người chuyển (Nộp) : $fromName")
                appendLine("  • Người nhận tiền    : $toName")
                appendLine("  • Số tiền            : ${NumberFormatUtils.formatVnd(tr.amount)}")
                appendLine("  • Nội dung gợi ý     : ${tr.transferNote}")
                appendLine()
            }
        }
        appendLine("IV. PHÂN LOẠI CHI TIÊU THEO DANH MỤC")
        val catMap = mapOf(
            "FOOD" to "Ăn uống",
            "TRANSPORT" to "Di chuyển",
            "HOTEL" to "Khách sạn/Lưu trú",
            "SIGHTSEEING" to "Vé tham quan",
            "ENTERTAINMENT" to "Vui chơi/Giải trí",
            "SHOPPING" to "Mua sắm",
            "OTHER" to "Chi phí khác"
        )
        expenses.groupBy { it.category }.forEach { (cat, list) ->
            val sum = list.sumOfSafe { it.convertedTotalAmount }
            val pct = if (summary.totalExpenses > 0) (sum * 100.0 / summary.totalExpenses) else 0.0
            appendLine(String.format("  • %-20s: %15s (%4.1f%%) - %d khoản", catMap[cat] ?: cat, NumberFormatUtils.formatVnd(sum), pct, list.size))
        }
        appendLine()
        appendLine("Ghi chú: Đây là gợi ý bù trừ công nợ, ứng dụng không thực hiện giao dịch chuyển khoản.")
        appendLine()
        appendLine("================================================================================")
        appendLine("                                   XÁC NHẬN")
        appendLine("           TRƯỞNG ĐOÀN                                THỦ QUỸ")
        appendLine("        (Ký và ghi rõ họ tên)                  (Ký và ghi rõ họ tên)")
        appendLine()
        appendLine()
        appendLine("================================================================================")
    }

    private fun generateExpenseLedgerReport(
        tripTitle: String,
        joinCode: String,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        expenses: List<ExpenseEntity>,
        funds: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity>,
        creatorName: String,
        date: Date
    ): String = buildString {
        appendLine("================================================================================")
        appendLine("                   BẢNG KÊ CHI TIẾT CÁC KHOẢN THU - CHI ĐOÀN")
        appendLine("                             ĐOÀN: ${tripTitle.uppercase()} (Mã: $joinCode)")
        appendLine("================================================================================")
        appendLine("Ngày lập: ${dateFormat.format(date)} | Người lập: $creatorName")
        appendLine()
        appendLine("PHẦN 1: DANH SÁCH CHI TIÊU (${expenses.size} khoản chi)")
        appendLine(String.format("%-4s | %-14s | %-20s | %-14s | %-12s | %-10s", "STT", "Thời gian", "Tên khoản chi", "Nguồn chi", "Số tiền", "Kiểu chia"))
        appendLine("--------------------------------------------------------------------------------")
        expenses.sortedBy { it.timestamp }.forEachIndexed { idx, exp ->
            val payer = if (exp.payerType == "FUND") "Quỹ đoàn" else (members.find { it.id == exp.payerMemberId }?.name ?: "Cá nhân")
            val splitMode = when (exp.splitType) {
                "EQUAL" -> "Chia đều"
                "CUSTOM_PARTICIPANT" -> "Chọn người"
                "RATIO" -> "Tỷ lệ %"
                "CUSTOM_AMOUNT" -> "Tùy nhập"
                else -> exp.splitType
            }
            appendLine(
                String.format(
                    "%-4d | %-14s | %-20s | %-14s | %-12s | %-10s",
                    idx + 1,
                    dateFormat.format(Date(exp.timestamp)),
                    exp.title.take(20),
                    payer.take(14),
                    NumberFormatUtils.formatVnd(exp.convertedTotalAmount),
                    splitMode
                )
            )

            // Danh sách người tham gia được chọn cho mỗi khoản chi
            val expSplits = splits.filter { it.expenseId == exp.id }
            if (expSplits.isNotEmpty()) {
                val participantsDetail = expSplits.joinToString("; ") { sp ->
                    val memName = members.find { it.id == sp.memberId }?.name ?: "Thành viên"
                    "$memName: ${NumberFormatUtils.formatVnd(sp.amount)}"
                }
                appendLine("       -> Người cùng chịu (${expSplits.size} người): $participantsDetail")
            }

            if (exp.note.isNotBlank()) {
                appendLine("       -> Ghi chú: ${exp.note}")
            }
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine("TỔNG CỘNG CHI TIÊU: ${NumberFormatUtils.formatVnd(summary.totalExpenses)}")
        appendLine()
        appendLine("PHẦN 2: LỊCH SỬ ĐÓNG GÓP QUỸ (${funds.size} đợt nộp)")
        appendLine(String.format("%-4s | %-14s | %-20s | %-16s | %-20s", "STT", "Thời gian", "Người nộp", "Số tiền", "Ghi chú"))
        appendLine("--------------------------------------------------------------------------------")
        funds.sortedBy { it.timestamp }.forEachIndexed { idx, f ->
            val contributor = members.find { it.id == f.memberId }?.name ?: "Thành viên"
            appendLine(
                String.format(
                    "%-4d | %-14s | %-20s | %-16s | %-20s",
                    idx + 1,
                    dateFormat.format(Date(f.timestamp)),
                    contributor.take(20),
                    NumberFormatUtils.formatVnd(f.convertedAmount),
                    f.note.take(20)
                )
            )
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine("TỔNG CỘNG THU QUỸ: ${NumberFormatUtils.formatVnd(summary.totalFundCollected)}")
        appendLine("================================================================================")
    }

    private fun generateTransferInstructionsReport(
        tripTitle: String,
        joinCode: String,
        transfers: List<SettlementTransfer>,
        creatorName: String,
        date: Date
    ): String = buildString {
        appendLine("================================================================================")
        appendLine("             HƯỚNG DẪN THANH TOÁN BÙ TRỪ QUYẾT TOÁN ĐOÀN")
        appendLine("                      ĐOÀN: ${tripTitle.uppercase()} (Mã: $joinCode)")
        appendLine("================================================================================")
        appendLine("Ngày xuất: ${dateFormat.format(date)} | Tạo bởi: $creatorName")
        appendLine()
        if (transfers.isEmpty()) {
            appendLine("Tất cả thành viên đã hoàn thành quyết toán cân bằng 100%. Không cần bù trừ thêm.")
        } else {
            transfers.forEachIndexed { i, t ->
                val fromName = if (t.fromMember.id == "FUND_ORGANIZATION") "Quỹ chung đoàn" else t.fromMember.name
                val toName = if (t.toMember.id == "FUND_ORGANIZATION") "Quỹ chung đoàn" else t.toMember.name
                appendLine("--------------------------------------------------------------------------------")
                appendLine("KHOẢN BÙ TRỪ #${i + 1}:")
                appendLine("  • Người gửi (Cần nộp) : $fromName")
                appendLine("  • Người nhận (Hưởng)  : $toName")
                appendLine("  • Số tiền             : ${NumberFormatUtils.formatVnd(t.amount)}")
                appendLine("  • Nội dung gợi ý      : ${t.transferNote}")
            }
            appendLine("--------------------------------------------------------------------------------")
            appendLine("Lưu ý: Đây là gợi ý bù trừ công nợ, ứng dụng không thực hiện giao dịch chuyển khoản.")
        }
    }

    private fun generateMemberBreakdownReport(
        tripTitle: String,
        joinCode: String,
        members: List<TripMemberEntity>,
        statuses: List<MemberFinancialStatus>,
        creatorName: String,
        date: Date
    ): String = buildString {
        appendLine("================================================================================")
        appendLine("                 BÁO CÁO PHÂN BỔ CHI PHÍ TỪNG THÀNH VIÊN")
        appendLine("                      ĐOÀN: ${tripTitle.uppercase()} (Mã: $joinCode)")
        appendLine("================================================================================")
        appendLine("Ngày xuất: ${dateFormat.format(date)} | Người lập: $creatorName")
        appendLine()
        statuses.forEachIndexed { idx, st ->
            appendLine("--------------------------------------------------------------------------------")
            appendLine("THÀNH VIÊN #${idx + 1}: ${st.member.name.uppercase()} (Vai trò: ${st.member.role})")
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
        }
        appendLine("================================================================================")
    }

    /**
     * Xuất báo cáo tài chính đoàn dưới định dạng Microsoft Excel (.xlsx) chuẩn OpenXML gồm 5 sheet:
     * 1. "Tổng quan": Thông tin chung, số liệu tổng hợp thu - chi, quỹ đoàn và tình trạng đối soát.
     * 2. "Chi tiêu": Toàn bộ các khoản chi tiêu (ngày, danh mục, người trả, tiền gốc, tiền tệ, tỷ giá, quy đổi VND, kiểu phân bổ, ghi chú).
     * 3. "Theo thành viên": Bảng số dư từng cá nhân (đã chi, nộp quỹ, tổng chi, phải chịu, số dư, trạng thái).
     *    TUYỆT ĐỐI KHÔNG chứa thông tin ngân hàng, số tài khoản hay chủ tài khoản.
     * 4. "Quỹ chung": Nhật ký các đợt nộp quỹ của các thành viên.
     * 5. "Gợi ý chuyển khoản": Danh sách gợi ý bù trừ công nợ tối ưu (chỉ gồm người trả, người nhận, số tiền, nội dung gợi ý).
     *    TUYỆT ĐỐI KHÔNG chứa cột ngân hàng, số tài khoản, chủ tài khoản hay mã QR.
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
        splits: List<ExpenseSplitEntity> = emptyList()
    ) {
        val now = Date()
        val tripTitle = trip?.title ?: "ĐOÀN CÔNG TÁC / DU LỊCH"
        val joinCode = trip?.joinCode ?: "---"

        XlsxWriter(outputStream).use { writer ->
            // ==========================================
            // Sheet 1: "Tổng quan"
            // ==========================================
            writer.sheet("Tổng quan") {
                headerRow("Chỉ tiêu", "Giá trị", "Ghi chú")
                row("Tên chuyến đi / đoàn", tripTitle, "")
                row("Mã đoàn", joinCode, "")
                row("Ngày xuất báo cáo", dateFormat.format(now), "")
                row {
                    cell("Tổng chi tiêu đoàn (VND)")
                    currencyCell(summary.totalExpenses)
                    cell("Bao gồm tiền thành viên tự chi và tiền quỹ")
                }
                row {
                    cell("Thành viên tự chi hộ (VND)")
                    currencyCell(summary.personalPaidExpenses)
                    cell("Các khoản cá nhân bỏ tiền túi chi hộ")
                }
                row {
                    cell("Chi từ nguồn Quỹ chung (VND)")
                    currencyCell(summary.fundPaidExpenses)
                    cell("Các khoản chi xuất trực tiếp từ quỹ đoàn")
                }
                row {
                    cell("Tổng tiền đã thu vào Quỹ (VND)")
                    currencyCell(summary.totalFundCollected)
                    cell("Tổng đóng góp từ các thành viên")
                }
                row {
                    cell("Số dư Quỹ chung còn lại (VND)")
                    currencyCell(summary.remainingFund)
                    cell("Tiền quỹ còn tồn sau khi đã chi")
                }
                row {
                    cell("Tình trạng đối soát")
                    cell(if (summary.isBalanced) "CÂN BẰNG (0 đ)" else "CẢNH BÁO LỆCH: " + NumberFormatUtils.formatVnd(summary.balanceDiscrepancy))
                    cell(if (summary.isBalanced) "Hợp lệ" else "Cần kiểm tra lại")
                }
                row {
                    cell("Tổng số thành viên")
                    cell(members.size)
                    cell("người")
                }
            }

            // ==========================================
            // Sheet 2: "Chi tiêu"
            // ==========================================
            writer.sheet("Chi tiêu") {
                headerRow(
                    "STT",
                    "Ngày",
                    "Tên khoản chi",
                    "Danh mục",
                    "Người trả",
                    "Tiền gốc",
                    "Tiền tệ",
                    "Tỷ giá",
                    "Quy đổi (VND)",
                    "Kiểu phân bổ",
                    "Ghi chú"
                )
                expenses.sortedBy { it.timestamp }.forEachIndexed { i, e ->
                    val payer = if (e.payerType == "FUND") "Quỹ chung" else (members.find { it.id == e.payerMemberId }?.name ?: "")
                    row {
                        cell(i + 1)
                        cell(dateOnlyFormat.format(Date(e.timestamp)))
                        cell(e.title)
                        cell(e.category)
                        cell(payer)
                        if (e.currency == "VND") {
                            currencyCell(e.totalAmount.toLong())
                        } else {
                            decimalCell(e.totalAmount)
                        }
                        cell(e.currency)
                        cell(e.exchangeRate)
                        currencyCell(e.convertedTotalAmount)
                        cell(e.splitType)
                        cell(e.note)
                    }
                }
            }

            // ==========================================
            // Sheet 3: "Theo thành viên"
            // ==========================================
            writer.sheet("Theo thành viên") {
                headerRow(
                    "STT",
                    "Họ và tên",
                    "Vai trò",
                    "Chi hộ cá nhân (VND)",
                    "Nộp quỹ (VND)",
                    "Tổng đã chi (VND)",
                    "Phải chịu (VND)",
                    "Số dư (VND)",
                    "Trạng thái quyết toán"
                )
                statuses.forEachIndexed { i, s ->
                    val statusText = when {
                        s.balance > 0 -> "Được nhận lại"
                        s.balance < 0 -> "Cần nộp thêm"
                        else -> "Đã cân bằng"
                    }
                    row {
                        cell(i + 1)
                        cell(s.member.name)
                        cell(s.member.role)
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
            // Sheet 4: "Quỹ chung"
            // ==========================================
            writer.sheet("Quỹ chung") {
                headerRow(
                    "STT",
                    "Thời gian",
                    "Người nộp",
                    "Số tiền gốc",
                    "Tiền tệ",
                    "Tỷ giá",
                    "Quy đổi (VND)",
                    "Ghi chú"
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
            // Sheet 5: "Gợi ý chuyển khoản"
            // ==========================================
            writer.sheet("Gợi ý chuyển khoản") {
                headerRow(
                    "STT",
                    "Người trả",
                    "Người nhận",
                    "Số tiền (VND)",
                    "Nội dung gợi ý"
                )
                settlementTransfers.forEachIndexed { i, t ->
                    val fromName = if (t.fromMember.id == "FUND_ORGANIZATION") "Quỹ chung đoàn" else t.fromMember.name
                    val toName = if (t.toMember.id == "FUND_ORGANIZATION") "Quỹ chung đoàn" else t.toMember.name
                    row {
                        cell(i + 1)
                        cell(fromName)
                        cell(toName)
                        currencyCell(t.amount)
                        cell(t.transferNote)
                    }
                }
            }
        }
    }
}
