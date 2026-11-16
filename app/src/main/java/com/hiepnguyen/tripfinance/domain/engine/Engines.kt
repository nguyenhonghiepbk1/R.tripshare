package com.hiepnguyen.tripfinance.domain.engine

import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.domain.model.SupportedCurrencies
import com.hiepnguyen.tripfinance.domain.model.sumOfSafe
import com.hiepnguyen.tripfinance.ui.components.NumberFormatUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

object SplitCalculator {

    /**
     * Chia đều tất cả thành viên (Equal Split)
     * Quy tắc làm tròn: Chia đều phần nguyên (totalAmount / count),
     * phần dư r (0 <= r < count) được phân bổ mỗi người +1 đơn vị nhỏ nhất (1 đơn vị tiền tệ với VND/THB/JPY/CNY/KRW,
     * hoặc 0.01 với các đồng tiền 2 chữ số thập phân) theo thứ tự ưu tiên vòng tròn bắt đầu từ primaryMemberId.
     * Đảm bảo mức chênh lệch giữa các thành viên KHÔNG BAO GIỜ vượt quá 1 đơn vị nhỏ nhất (1 hoặc 0.01).
     */
    fun calculateEqualSplit(
        totalAmount: Long,
        memberIds: List<String>,
        primaryMemberId: String? = null
    ): List<Pair<String, Long>> {
        if (memberIds.isEmpty() || totalAmount <= 0L) return emptyList()
        val count = memberIds.size
        val baseShare = totalAmount / count
        val remainder = (totalAmount % count).toInt()

        val priorityIndex = if (primaryMemberId != null && memberIds.contains(primaryMemberId)) {
            memberIds.indexOf(primaryMemberId)
        } else {
            0
        }

        return memberIds.mapIndexed { index, memberId ->
            val distanceFromPriority = (index - priorityIndex + count) % count
            val extra = if (distanceFromPriority < remainder) 1L else 0L
            memberId to (baseShare + extra)
        }
    }

    /**
     * Overload hỗ trợ số tiền thập phân theo quy chuẩn từng loại tiền tệ:
     * - VND, THB, JPY, CNY, KRW: làm tròn đến đơn vị đồng, sai lệch tối đa 1 đơn vị.
     * - Các đồng tiền khác (USD, EUR, SGD, GBP, AUD,...): làm tròn đến 2 chữ số sau dấu phẩy, sai lệch tối đa 0.01.
     */
    fun calculateEqualSplit(
        totalAmount: Double,
        memberIds: List<String>,
        currency: String,
        primaryMemberId: String? = null
    ): List<Pair<String, Double>> {
        val minorTotal = SupportedCurrencies.toMinorUnits(totalAmount, currency)
        if (memberIds.isEmpty() || minorTotal <= 0L) return emptyList()
        return calculateEqualSplit(minorTotal, memberIds, primaryMemberId).map { (memberId, minorShare) ->
            memberId to SupportedCurrencies.fromMinorUnits(minorShare, currency)
        }
    }

    /**
     * Chia theo tỷ lệ phần trăm (Ratio Split)
     * Ràng buộc: Tổng % phải bằng 100.0%.
     * Khi tổng % hợp lệ (xấp xỉ 100% trong sai số 0.01%), sử dụng phương pháp Hamilton (Largest Remainder Method)
     * với BigDecimal để phân bổ phần dư sao cho sai lệch làm tròn mỗi thành viên tối thiểu (<= 1 đơn vị nhỏ nhất)
     * và tổng các phần bằng đúng totalAmount.
     */
    fun calculateRatioSplit(
        totalAmount: Long,
        memberRatios: List<Pair<String, Double>>,
        primaryMemberId: String? = null
    ): Pair<List<Pair<String, Long>>, Boolean> {
        if (memberRatios.isEmpty()) return emptyList<Pair<String, Long>>() to false
        val totalRatio = memberRatios.sumOf { it.second }
        val isRatioValid = abs(totalRatio - 100.0) <= 0.015

        if (!isRatioValid || totalAmount <= 0L) {
            val rawSplits = memberRatios.map { (memberId, ratio) ->
                val safeRatio = ratio.coerceAtLeast(0.0)
                val share = if (totalAmount <= 0L) 0L else ((totalAmount * safeRatio) / 100.0).roundToLong().coerceAtLeast(0L)
                memberId to share
            }
            return rawSplits to isRatioValid
        }

        val count = memberRatios.size
        val priorityIndex = if (primaryMemberId != null) {
            memberRatios.indexOfFirst { it.first == primaryMemberId }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }

        val safeRatios = memberRatios.map { BigDecimal.valueOf(it.second.coerceAtLeast(0.0)) }
        val ratioSumBd = safeRatios.fold(BigDecimal.ZERO) { acc, bd -> acc.add(bd) }
        if (ratioSumBd.compareTo(BigDecimal.ZERO) <= 0) {
            return memberRatios.map { it.first to 0L } to false
        }

        val totalBd = BigDecimal.valueOf(totalAmount)
        val exactQuotas = safeRatios.map { r ->
            totalBd.multiply(r).divide(ratioSumBd, 12, RoundingMode.DOWN)
        }
        val floorShares = exactQuotas.map { it.toLong().coerceAtLeast(0L) }.toMutableList()
        val remainders = exactQuotas.mapIndexed { idx, exact ->
            exact.subtract(BigDecimal.valueOf(floorShares[idx]))
        }

        var remainingUnits = totalAmount - floorShares.sum()
        if (remainingUnits > 0L) {
            val rankedIndices = memberRatios.indices.sortedWith { a, b ->
                val cmp = remainders[b].compareTo(remainders[a])
                if (cmp != 0) cmp
                else {
                    val distA = (a - priorityIndex + count) % count
                    val distB = (b - priorityIndex + count) % count
                    distA.compareTo(distB)
                }
            }
            var cursor = 0
            while (remainingUnits > 0L && rankedIndices.isNotEmpty()) {
                val targetIdx = rankedIndices[cursor % rankedIndices.size]
                floorShares[targetIdx] = floorShares[targetIdx] + 1L
                remainingUnits--
                cursor++
            }
        }

        val splits = memberRatios.mapIndexed { idx, (memberId, _) ->
            memberId to floorShares[idx]
        }
        return splits to true
    }

    fun calculateRatioSplit(
        totalAmount: Double,
        memberRatios: List<Pair<String, Double>>,
        currency: String,
        primaryMemberId: String? = null
    ): Pair<List<Pair<String, Double>>, Boolean> {
        val minorTotal = SupportedCurrencies.toMinorUnits(totalAmount, currency)
        val (minorSplits, valid) = calculateRatioSplit(minorTotal, memberRatios, primaryMemberId)
        val majorSplits = minorSplits.map { (memberId, minorShare) ->
            memberId to SupportedCurrencies.fromMinorUnits(minorShare, currency)
        }
        return majorSplits to valid
    }

    /**
     * Chia theo nhóm người được chọn (Custom Participant Split)
     */
    fun calculateSelectedParticipantsSplit(
        totalAmount: Long,
        selectedMemberIds: List<String>,
        primaryMemberId: String? = null
    ): List<Pair<String, Long>> {
        return calculateEqualSplit(totalAmount, selectedMemberIds, primaryMemberId)
    }

    fun calculateSelectedParticipantsSplit(
        totalAmount: Double,
        selectedMemberIds: List<String>,
        currency: String,
        primaryMemberId: String? = null
    ): List<Pair<String, Double>> {
        return calculateEqualSplit(totalAmount, selectedMemberIds, currency, primaryMemberId)
    }

    /**
     * Kiểm tra Toàn vẹn Dữ liệu (Integrity Check):
     * Hệ thống đảm bảo tổng các phần phân bổ bắt buộc phải bằng đúng tổng số tiền của khoản chi gốc.
     */
    fun validateSplits(totalAmount: Long, splits: List<Pair<String, Long>>): Boolean {
        if (splits.isEmpty() && totalAmount == 0L) return true
        return splits.sumOfSafe { it.second } == totalAmount
    }
}

data class SettlementCalculationResult(
    val transfers: List<SettlementTransfer>,
    val reconciliationError: String? = null
)

object SettlementEngine {

    // Ngưỡng sai số so sánh dập tắt hoàn toàn rủi ro sai số số học khiến vòng lặp chạy vô tận
    const val EPSILON = 0.001

    /**
     * Thuật toán Tham Lam (Greedy Algorithm) Tối Ưu Hóa Dòng Tiền:
     * - Ý tưởng cốt lõi: Luôn ưu tiên lấy bên nợ nhiều nhất (Max Debtor) trả cho bên đang được hệ thống nợ nhiều nhất (Max Creditor).
     * - Bằng cách triệt tiêu các khoản lớn trước, rút gọn đáng kể số lượng giao dịch bù trừ qua lại.
     * - Khi đoàn có Quỹ chung còn dư (remainingFund > 0) hoặc bị chi vượt (remainingFund < 0):
     *   Quỹ đoàn đóng vai trò là một bên tham gia quyết toán với số dư = -remainingFund.
     * - Trả về đối tượng kết quả SettlementCalculationResult(transfers, reconciliationError) phi trạng thái,
     *   hoàn toàn không sử dụng biến toàn cục có thể bị ghi đè khi gọi đồng thời (Race Condition).
     */
    fun computeSettlementWithStatus(
        memberStatuses: List<MemberFinancialStatus>,
        tripJoinCode: String,
        remainingFund: Long = 0L,
        fundHolder: TripMemberEntity? = null,
        baseCurrency: String = "VND"
    ): SettlementCalculationResult {
        if (memberStatuses.isEmpty()) {
            val errorMsg = "Đoàn không có thành viên nào! Không thể thực hiện đối soát và quyết toán."
            return SettlementCalculationResult(emptyList(), errorMsg)
        }

        val totalMemberBalance = memberStatuses.sumOfSafe { it.balance }
        // Kiểm tra đối soát: Tổng balance toàn đoàn trừ đi số quỹ còn lại phải bằng 0 tuyệt đối (Zero discrepancy tolerance)
        // Không cho phép bất kỳ dung sai cố định nào để chống thất thoát và lệch nhỏ tích lũy
        val discrepancy = totalMemberBalance - remainingFund
        if (discrepancy != 0L) {
            val formattedDiscrepancy = NumberFormatUtils.formatCurrency(discrepancy, baseCurrency)
            val errorMsg = "Phát hiện chênh lệch đối soát ($formattedDiscrepancy). Chưa thể tạo kế hoạch bù trừ công nợ do các khoản thu chi chưa cân bằng tuyệt đối."
            return SettlementCalculationResult(
                transfers = emptyList(),
                reconciliationError = errorMsg
            )
        }

        val transfers = computeTransfersInternal(
            memberStatuses = memberStatuses,
            tripJoinCode = tripJoinCode,
            remainingFund = remainingFund,
            fundHolder = fundHolder,
            totalMemberBalance = totalMemberBalance,
            discrepancy = discrepancy
        )
        return SettlementCalculationResult(transfers = transfers, reconciliationError = null)
    }

    fun computeSimplifiedTransfers(
        memberStatuses: List<MemberFinancialStatus>,
        tripJoinCode: String,
        remainingFund: Long = 0L,
        fundHolder: TripMemberEntity? = null,
        baseCurrency: String = "VND"
    ): List<SettlementTransfer> {
        return computeSettlementWithStatus(memberStatuses, tripJoinCode, remainingFund, fundHolder, baseCurrency).transfers
    }

    private fun computeTransfersInternal(
        memberStatuses: List<MemberFinancialStatus>,
        tripJoinCode: String,
        remainingFund: Long,
        fundHolder: TripMemberEntity?,
        totalMemberBalance: Long,
        discrepancy: Long
    ): List<SettlementTransfer> {
        data class BalanceEntry(val member: TripMemberEntity, var balance: Long)

        val balanceEntries = mutableListOf<BalanceEntry>()
        memberStatuses.forEach {
            balanceEntries.add(BalanceEntry(it.member, it.balance))
        }

        // Nếu quỹ còn dư hoặc bị chi vượt, đưa Quỹ đoàn vào quyết toán như một bên tham gia
        // Gán số dư Quỹ = -totalMemberBalance để triệt tiêu tuyệt đối mọi chênh lệch làm tròn
        if (remainingFund != 0L || discrepancy != 0L) {
            val fundMemberName = if (fundHolder != null) {
                if (fundHolder.role == "TREASURER") "Quỹ đoàn (Thủ quỹ: ${fundHolder.name})"
                else "Quỹ đoàn (${fundHolder.name})"
            } else "Quỹ chung đoàn"

            val fundMember = TripMemberEntity(
                id = "FUND_ORGANIZATION",
                tripId = fundHolder?.tripId ?: "",
                userId = "fund",
                name = fundMemberName,
                role = "TREASURER",
                isActive = true,
                joinedAt = 0L
            )
            balanceEntries.add(BalanceEntry(fundMember, -totalMemberBalance))
        }

        // 1. Phân loại và sắp xếp giảm dần:
        // Creditors: Bên có balance > 0 (được nhận lại tiền)
        val creditors = balanceEntries
            .filter { it.balance > 0L }
            .sortedByDescending { it.balance }
            .toMutableList()

        // Debtors: Bên có balance < 0 (phải trả / chuyển tiền đi)
        val debtors = balanceEntries
            .filter { it.balance < 0L }
            .map { BalanceEntry(it.member, -it.balance) }
            .sortedByDescending { it.balance }
            .toMutableList()

        val transfers = mutableListOf<SettlementTransfer>()
        var i = 0 // con trỏ debtors
        var j = 0 // con trỏ creditors

        // Vòng lặp tham lam giải quyết nợ từng cặp lớn nhất
        while (i < debtors.size && j < creditors.size) {
            val debtor = debtors[i]
            val creditor = creditors[j]

            if (debtor.balance <= 0L) {
                i++
                continue
            }
            if (creditor.balance <= 0L) {
                j++
                continue
            }

            // Triệt tiêu số tiền nhỏ hơn giữa 2 bên
            val settleAmount = minOf(debtor.balance, creditor.balance)

            if (settleAmount > 0L) {
                val transferNote = "${debtor.member.name} chuyển cho ${creditor.member.name}"
                val effectiveTripId = memberStatuses.firstOrNull()?.member?.tripId?.ifBlank { tripJoinCode }
                    ?: fundHolder?.tripId?.ifBlank { tripJoinCode }
                    ?: tripJoinCode

                transfers.add(
                    SettlementTransfer(
                        id = "tr_${effectiveTripId}_${debtor.member.id}_${creditor.member.id}",
                        fromMember = debtor.member,
                        toMember = creditor.member,
                        amount = settleAmount,
                        transferNote = transferNote
                    )
                )
            }

            debtor.balance -= settleAmount
            creditor.balance -= settleAmount

            if (debtor.balance <= 0L) i++
            if (creditor.balance <= 0L) j++
        }

        return transfers
    }
}

object CashFlowMinimizer {
    fun minimizeTransfers(
        memberStatuses: List<MemberFinancialStatus>,
        tripJoinCode: String = "",
        remainingFund: Long = 0L,
        fundHolder: TripMemberEntity? = null
    ): List<SettlementTransfer> {
        return SettlementEngine.computeSimplifiedTransfers(memberStatuses, tripJoinCode, remainingFund, fundHolder)
    }
}
