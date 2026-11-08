package com.hiepnguyen.tripfinance.domain.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportExporter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))

    /**
     * Dọn dẹp các tệp báo cáo (PDF, XLSX) cũ hơn 24 giờ trong thư mục cache/reports khi ứng dụng khởi động
     */
    fun cleanOldReports(context: Context) {
        try {
            val reportsDir = File(context.cacheDir, "reports")
            if (reportsDir.exists() && reportsDir.isDirectory) {
                val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                reportsDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.endsWith(".pdf", ignoreCase = true) || file.name.endsWith(".xlsx", ignoreCase = true)) && file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("PdfReportExporter", "Không thể dọn dẹp file báo cáo cũ: ${e.message}")
        }
    }

    /**
     * Chia một đoạn văn bản thành các dòng không vượt quá maxAvailableWidth
     */
    private fun wrapText(text: String, paint: Paint, maxAvailableWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxAvailableWidth) {
                current.append(if (current.isEmpty()) word else " $word")
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                } else {
                    lines.add(word)
                    current = StringBuilder()
                }
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    suspend fun exportAndSharePdf(
        context: Context,
        trip: TripEntity?,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        statuses: List<MemberFinancialStatus>,
        settlementTransfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        funds: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity> = emptyList(),
        creatorName: String,
        language: AppLanguage = AppLanguage.VI
    ): File? = withContext(Dispatchers.IO) {
        var pdfDocument: PdfDocument? = null
        try {
            pdfDocument = PdfDocument()
            val pageWidth = 595 // Standard A4 width in PostScript points (72 dpi)
            val pageHeight = 842 // Standard A4 height in PostScript points

            val localDateFormat = if (language == AppLanguage.VI) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")) else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val localDateOnlyFormat = if (language == AppLanguage.VI) SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")) else SimpleDateFormat("yyyy-MM-dd", Locale.US)

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            val paint = Paint().apply {
                isAntiAlias = true
            }

            var currentY = 40f
            val margin = 36f
            val contentWidth = pageWidth - (margin * 2)

            fun drawFooter() {
                paint.color = Color.parseColor("#94A3B8")
                paint.textSize = 8f
                paint.typeface = Typeface.DEFAULT
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(AppStrings.pdfFooterSuggestion(language), margin, pageHeight - 20f, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(AppStrings.pdfPageNumber(pageNumber, language), pageWidth - margin, pageHeight - 20f, paint)
            }

            fun checkPageBreak(requiredHeight: Float) {
                if (currentY + requiredHeight > pageHeight - 40f) {
                    drawFooter()
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = 40f
                }
            }

            // =========================================================================
            // Header Banner (Primary Dark Indigo background)
            // =========================================================================
            val headerHeight = 70f
            paint.color = Color.parseColor("#1E1B4B")
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + headerHeight, 8f, 8f, paint)

            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 14.5f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(AppStrings.pdfBannerTitle(language), margin + (contentWidth / 2f), currentY + 28f, paint)

            paint.textSize = 10.5f
            paint.typeface = Typeface.DEFAULT
            paint.color = Color.parseColor("#E0E7FF")
            val tripTitle = trip?.title ?: AppStrings.pdfDefaultTripName(language)
            canvas.drawText(tripTitle.uppercase(), margin + (contentWidth / 2f), currentY + 46f, paint)

            paint.textSize = 8.5f
            paint.color = Color.parseColor("#C7D2FE")
            val dateStr = localDateFormat.format(Date())
            canvas.drawText(AppStrings.pdfMetaInfo(dateStr, creatorName, members.size, language), margin + (contentWidth / 2f), currentY + 60f, paint)

            currentY += headerHeight + 16f

            // =========================================================================
            // Section 1: Financial Overview Summary Box
            // =========================================================================
            checkPageBreak(90f)
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 80f, 6f, 6f, paint)
            paint.color = Color.parseColor("#E2E8F0")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 80f, 6f, 6f, paint)

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10f
            canvas.drawText(AppStrings.pdfSecOverview(language), margin + 12f, currentY + 18f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 9f
            paint.color = Color.parseColor("#334155")
            canvas.drawText(AppStrings.pdfMetricTotalExpenses(language) + NumberFormatUtils.formatVnd(summary.totalExpenses), margin + 12f, currentY + 34f, paint)
            canvas.drawText(AppStrings.pdfMetricPersonalPaid(language) + NumberFormatUtils.formatVnd(summary.personalPaidExpenses), margin + 12f, currentY + 48f, paint)
            canvas.drawText(AppStrings.pdfMetricFundPaid(language) + NumberFormatUtils.formatVnd(summary.fundPaidExpenses), margin + 12f, currentY + 62f, paint)

            val col2X = margin + (contentWidth / 2f) + 10f
            canvas.drawText(AppStrings.pdfMetricTotalFund(language) + NumberFormatUtils.formatVnd(summary.totalFundCollected), col2X, currentY + 34f, paint)
            canvas.drawText(AppStrings.pdfMetricRemainingFund(language) + NumberFormatUtils.formatVnd(summary.remainingFund), col2X, currentY + 48f, paint)
            val balanceText = if (summary.isBalanced) AppStrings.pdfAuditBalanced(language) else AppStrings.pdfAuditDiscrepancy(language) + NumberFormatUtils.formatVnd(summary.balanceDiscrepancy)
            canvas.drawText(AppStrings.pdfMetricAudit(language) + balanceText, col2X, currentY + 62f, paint)

            currentY += 92f

            // =========================================================================
            // Section 2: Member Balance & Settlement Table
            // =========================================================================
            checkPageBreak(40f + (statuses.size * 20f))
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10.5f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(AppStrings.pdfSecMemberBalances(language), margin, currentY, paint)
            currentY += 12f

            // Table Header
            paint.color = Color.parseColor("#047857") // Emerald Primary
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(AppStrings.pdfColNo(language), margin + 4f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColFullName(language), margin + 28f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColPaid(language), margin + 148f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColFund(language), margin + 220f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColOwed(language), margin + 292f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColBalance(language), margin + 372f, currentY + 13f, paint)
            currentY += 20f

            // Table Rows
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8.5f
            statuses.forEachIndexed { idx, st ->
                checkPageBreak(22f)
                if (idx % 2 == 1) {
                    paint.color = Color.parseColor("#F1F5F9")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
                }

                paint.color = Color.parseColor("#1E293B")
                paint.style = Paint.Style.FILL
                canvas.drawText("${idx + 1}", margin + 4f, currentY + 12f, paint)
                val memberNameDisplay = if (st.member.name.length > 20) st.member.name.take(19) + "…" else st.member.name
                canvas.drawText(memberNameDisplay, margin + 28f, currentY + 12f, paint)
                canvas.drawText(NumberFormatUtils.formatVnd(st.outOfPocketPaid), margin + 148f, currentY + 12f, paint)
                canvas.drawText(NumberFormatUtils.formatVnd(st.fundContributed), margin + 220f, currentY + 12f, paint)
                canvas.drawText(NumberFormatUtils.formatVnd(st.totalOwed), margin + 292f, currentY + 12f, paint)

                if (st.balance > 0) {
                    paint.color = Color.parseColor("#15803D") // Green
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("+${NumberFormatUtils.formatVnd(st.balance)} ${AppStrings.pdfBadgeToReceive(language)}", margin + 372f, currentY + 12f, paint)
                } else if (st.balance < 0) {
                    paint.color = Color.parseColor("#B91C1C") // Red
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("${NumberFormatUtils.formatVnd(st.balance)} ${AppStrings.pdfBadgeToPay(language)}", margin + 372f, currentY + 12f, paint)
                } else {
                    paint.color = Color.parseColor("#64748B")
                    paint.typeface = Typeface.DEFAULT
                    canvas.drawText(AppStrings.pdfBadgeBalanced(language), margin + 372f, currentY + 12f, paint)
                }

                paint.color = Color.parseColor("#E2E8F0")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                canvas.drawLine(margin, currentY + 18f, margin + contentWidth, currentY + 18f, paint)
                currentY += 18f
            }

            currentY += 14f

            // =========================================================================
            // Section 3: KẾ HOẠCH BÙ TRỪ CÔNG NỢ TỐI ƯU GIỮA CÁC THÀNH VIÊN
            // =========================================================================
            val totalTransferAmount = settlementTransfers.sumOfSafe { it.amount }
            checkPageBreak(50f + if (settlementTransfers.isEmpty()) 40f else (settlementTransfers.size * 22f + 30f))
            
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.style = Paint.Style.FILL
            paint.textSize = 10.5f
            canvas.drawText(AppStrings.pdfSecSettlementPlan(language), margin, currentY, paint)
            currentY += 13f

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8f
            paint.color = Color.parseColor("#475569")
            val settleDesc = if (settlementTransfers.isNotEmpty()) {
                AppStrings.pdfSettleDesc(settlementTransfers.size, NumberFormatUtils.formatVnd(totalTransferAmount), language)
            } else {
                AppStrings.pdfSettleDescEmpty(language)
            }
            canvas.drawText(settleDesc, margin, currentY, paint)
            currentY += 10f

            if (settlementTransfers.isEmpty()) {
                // Hộp thông báo đã cân bằng
                paint.color = Color.parseColor("#F0FDF4")
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 28f, 6f, 6f, paint)
                paint.color = Color.parseColor("#BBF7D0")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 28f, 6f, 6f, paint)

                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#15803D")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8.5f
                canvas.drawText(AppStrings.pdfSettleAllBalanced(language), margin + 12f, currentY + 18f, paint)
                currentY += 36f
            } else {
                // Bảng bù trừ tối ưu
                paint.color = Color.parseColor("#4338CA") // Indigo 700
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(AppStrings.pdfColSender(language), margin + 6f, currentY + 13f, paint)
                canvas.drawText(AppStrings.pdfColReceiver(language), margin + 110f, currentY + 13f, paint)
                canvas.drawText(AppStrings.pdfColAmount(language), margin + 215f, currentY + 13f, paint)
                canvas.drawText(AppStrings.pdfColAuditNote(language), margin + 305f, currentY + 13f, paint)
                canvas.drawText(AppStrings.pdfColTransferred(language), margin + 435f, currentY + 13f, paint)
                currentY += 20f

                val fundName = AppStrings.pdfFundName(language)
                paint.typeface = Typeface.DEFAULT
                settlementTransfers.forEachIndexed { i, tr ->
                    checkPageBreak(22f)
                    if (i % 2 == 1) {
                        paint.color = Color.parseColor("#EEF2FF")
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)
                    }

                    paint.color = Color.parseColor("#1E293B")
                    paint.style = Paint.Style.FILL
                    val fromName = if (tr.fromMember.id == "FUND_ORGANIZATION") fundName else tr.fromMember.name
                    val toName = if (tr.toMember.id == "FUND_ORGANIZATION") fundName else tr.toMember.name
                    canvas.drawText(fromName.take(18), margin + 6f, currentY + 13f, paint)
                    canvas.drawText(toName.take(18), margin + 110f, currentY + 13f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.color = Color.parseColor("#4338CA")
                    canvas.drawText(NumberFormatUtils.formatVnd(tr.amount), margin + 215f, currentY + 13f, paint)

                    paint.typeface = Typeface.DEFAULT
                    paint.color = Color.parseColor("#1E293B")
                    canvas.drawText(tr.transferNote.take(24), margin + 305f, currentY + 13f, paint)

                    if (tr.isCompleted) {
                        paint.color = Color.parseColor("#15803D")
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("✓ ${AppStrings.pdfStatusTransferred(language)}", margin + 435f, currentY + 13f, paint)
                    } else {
                        paint.color = Color.parseColor("#64748B")
                        paint.typeface = Typeface.DEFAULT
                        canvas.drawText("○ ${AppStrings.pdfStatusPending(language)}", margin + 435f, currentY + 13f, paint)
                    }

                    paint.color = Color.parseColor("#E0E7FF")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawLine(margin, currentY + 20f, margin + contentWidth, currentY + 20f, paint)
                    currentY += 20f
                }
                currentY += 14f
            }

            // =========================================================================
            // Section 4: BẢN KÊ CHI TIẾT CÁC KHOẢN CHI TIÊU
            // =========================================================================
            checkPageBreak(50f)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.style = Paint.Style.FILL
            paint.textSize = 10.5f
            canvas.drawText(AppStrings.pdfSecExpensesLedger(expenses.size, language), margin, currentY, paint)
            currentY += 12f

            // Expenses Table Header
            paint.color = Color.parseColor("#0284C7") // Sky Blue 600
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(AppStrings.pdfColNo(language), margin + 4f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColDate(language), margin + 26f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColExpenseTitleNote(language), margin + 110f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColPayer(language), margin + 270f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColAmount(language), margin + 360f, currentY + 13f, paint)
            canvas.drawText(AppStrings.pdfColSplit(language), margin + 450f, currentY + 13f, paint)
            currentY += 20f

            val sublinePaint = Paint().apply {
                isAntiAlias = true
                textSize = 7.5f
                color = Color.parseColor("#475569")
                typeface = Typeface.DEFAULT
            }
            val maxSublineWidth = contentWidth - 116f

            expenses.sortedBy { it.timestamp }.forEachIndexed { idx, exp ->
                val expSplits = splits.filter { it.expenseId == exp.id }
                
                // Chuẩn bị toàn bộ tên thành viên tham gia khoản chi đó
                val defaultMem = AppStrings.pdfDefaultMember(language)
                val participantsFullText: String = if (expSplits.isNotEmpty()) {
                    val formattedMembers = expSplits.map { sp ->
                        val mName = members.find { it.id == sp.memberId }?.name ?: defaultMem
                        "$mName (${NumberFormatUtils.formatVnd(sp.amount)})"
                    }.joinToString(", ")
                    AppStrings.pdfSplitParticipantsText(expSplits.size, formattedMembers, language)
                } else {
                    val allNames = members.joinToString(", ") { it.name }
                    AppStrings.pdfSplitAllText(members.size, allNames, language)
                }

                // Tách thành nhiều dòng vừa khít khổ trang
                val participantLines = wrapText(participantsFullText, sublinePaint, maxSublineWidth)
                val noteLines = if (exp.note.isNotBlank()) {
                    wrapText(AppStrings.pdfNotePrefix(language) + exp.note, sublinePaint, maxSublineWidth)
                } else emptyList()

                val lineSpacing = 10f
                val totalSublinesHeight = (participantLines.size + noteLines.size) * lineSpacing
                val rowHeight = 18f + totalSublinesHeight + 4f

                checkPageBreak(rowHeight + 4f)
                if (idx % 2 == 1) {
                    paint.color = Color.parseColor("#F0F9FF")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(margin, currentY, margin + contentWidth, currentY + rowHeight, paint)
                }

                // Main Row info
                paint.color = Color.parseColor("#1E293B")
                paint.style = Paint.Style.FILL
                paint.textSize = 8f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText("${idx + 1}", margin + 4f, currentY + 11f, paint)
                canvas.drawText(localDateOnlyFormat.format(Date(exp.timestamp)), margin + 26f, currentY + 11f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(exp.title.take(30), margin + 110f, currentY + 11f, paint)

                paint.typeface = Typeface.DEFAULT
                val payerName = if (exp.payerType == "FUND") AppStrings.pdfPayerFund(language) else (members.find { it.id == exp.payerMemberId }?.name ?: AppStrings.pdfPayerPersonal(language))
                canvas.drawText(payerName.take(16), margin + 270f, currentY + 11f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.parseColor("#0369A1")
                canvas.drawText(NumberFormatUtils.formatVnd(exp.convertedTotalAmount), margin + 360f, currentY + 11f, paint)

                paint.typeface = Typeface.DEFAULT
                paint.color = Color.parseColor("#475569")
                val splitModeStr = when (exp.splitType) {
                    "EQUAL" -> AppStrings.pdfSplitEqual(language)
                    "CUSTOM_PARTICIPANT" -> AppStrings.pdfSplitSelect(language)
                    "RATIO" -> AppStrings.pdfSplitRatio(language)
                    "CUSTOM_AMOUNT" -> AppStrings.pdfSplitCustom(language)
                    else -> AppStrings.getSplitTypeName(exp.splitType, language)
                }
                canvas.drawText(splitModeStr, margin + 450f, currentY + 11f, paint)

                var sublineY = currentY + 22f
                sublinePaint.color = Color.parseColor("#0369A1")
                participantLines.forEach { line ->
                    canvas.drawText(line, margin + 110f, sublineY, sublinePaint)
                    sublineY += lineSpacing
                }

                sublinePaint.color = Color.parseColor("#64748B")
                noteLines.forEach { line ->
                    canvas.drawText(line, margin + 110f, sublineY, sublinePaint)
                    sublineY += lineSpacing
                }

                paint.color = Color.parseColor("#E0F2FE")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                canvas.drawLine(margin, currentY + rowHeight, margin + contentWidth, currentY + rowHeight, paint)
                currentY += rowHeight
            }

            // Summary row of expenses
            checkPageBreak(20f)
            paint.color = Color.parseColor("#E0F2FE")
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
            paint.color = Color.parseColor("#0369A1")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8.5f
            canvas.drawText(AppStrings.pdfTotalExpensesLabel(language), margin + 110f, currentY + 12f, paint)
            canvas.drawText(NumberFormatUtils.formatVnd(summary.totalExpenses), margin + 360f, currentY + 12f, paint)
            currentY += 28f

            // =========================================================================
            // Section 5: BẢN KÊ THU QUỸ ĐOÀN (Nếu có nộp quỹ)
            // =========================================================================
            if (funds.isNotEmpty()) {
                checkPageBreak(50f + (funds.size * 20f))
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.parseColor("#0F172A")
                paint.style = Paint.Style.FILL
                paint.textSize = 10.5f
                canvas.drawText(AppStrings.pdfSecFundLedger(funds.size, language), margin, currentY, paint)
                currentY += 12f

                // Fund Table Header
                paint.color = Color.parseColor("#D97706") // Amber 600
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(AppStrings.pdfColNo(language), margin + 4f, currentY + 13f, paint)
                canvas.drawText(AppStrings.pdfColDate(language), margin + 26f, currentY + 13f, paint)
                canvas.drawText(AppStrings.pdfColContributor(language), margin + 110f, currentY + 13f, paint)
                canvas.drawText(AppStrings.pdfColContributedAmount(language), margin + 260f, currentY + 13f, paint)
                canvas.drawText(AppStrings.pdfColFundNote(language), margin + 380f, currentY + 13f, paint)
                currentY += 20f

                paint.typeface = Typeface.DEFAULT
                funds.sortedBy { it.timestamp }.forEachIndexed { idx, fund ->
                    checkPageBreak(18f)
                    if (idx % 2 == 1) {
                        paint.color = Color.parseColor("#FFFBEB")
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
                    }

                    paint.color = Color.parseColor("#1E293B")
                    paint.style = Paint.Style.FILL
                    paint.textSize = 8.5f
                    canvas.drawText("${idx + 1}", margin + 4f, currentY + 12f, paint)
                    canvas.drawText(localDateOnlyFormat.format(Date(fund.timestamp)), margin + 26f, currentY + 12f, paint)

                    val memName = members.find { it.id == fund.memberId }?.name ?: AppStrings.pdfDefaultMember(language)
                    canvas.drawText(memName.take(22), margin + 110f, currentY + 12f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.color = Color.parseColor("#B45309")
                    canvas.drawText(NumberFormatUtils.formatVnd(fund.convertedAmount), margin + 260f, currentY + 12f, paint)

                    paint.typeface = Typeface.DEFAULT
                    paint.color = Color.parseColor("#475569")
                    val noteDisplay = if (fund.note.isNotBlank()) fund.note else AppStrings.pdfDefaultFundNote(language)
                    canvas.drawText(noteDisplay.take(25), margin + 380f, currentY + 12f, paint)

                    paint.color = Color.parseColor("#FEF3C7")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawLine(margin, currentY + 18f, margin + contentWidth, currentY + 18f, paint)
                    currentY += 18f
                }

                // Summary row of funds
                checkPageBreak(20f)
                paint.color = Color.parseColor("#FEF3C7")
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
                paint.color = Color.parseColor("#B45309")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8.5f
                canvas.drawText(AppStrings.pdfTotalFundLabel(language), margin + 110f, currentY + 12f, paint)
                canvas.drawText(NumberFormatUtils.formatVnd(summary.totalFundCollected), margin + 260f, currentY + 12f, paint)
                currentY += 28f
            }

            // Signatures area
            checkPageBreak(65f)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 9.5f
            paint.textAlign = Paint.Align.CENTER

            val leftSignX = margin + (contentWidth * 0.25f)
            val rightSignX = margin + (contentWidth * 0.75f)

            canvas.drawText(AppStrings.pdfSignLeader(language), leftSignX, currentY + 12f, paint)
            canvas.drawText(AppStrings.pdfSignTreasurer(language), rightSignX, currentY + 12f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8f
            paint.color = Color.parseColor("#64748B")
            canvas.drawText(AppStrings.pdfSignHint(language), leftSignX, currentY + 24f, paint)
            canvas.drawText(AppStrings.pdfSignHint(language), rightSignX, currentY + 24f, paint)

            // Page Number and disclaimer for last page
            drawFooter()

            pdfDocument.finishPage(page)

            // Write PDF to cache directory
            val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
            val sanitizeTripTitle = (trip?.title ?: "Trip").replace("[^a-zA-Z0-9]".toRegex(), "_")
            val prefix = if (language == AppLanguage.VI) "BaoCaoQuyetToan" else "SettlementReport"
            val pdfFile = File(reportsDir, "${prefix}_${sanitizeTripTitle}_${System.currentTimeMillis()}.pdf")
            
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
                fos.flush()
            }

            // Open Share Intent for PDF on Main thread
            withContext(Dispatchers.Main) {
                sharePdfFile(context, pdfFile, tripTitle, language)
            }
            pdfFile
        } catch (e: Exception) {
            android.util.Log.e("PdfReportExporter", "Lỗi xuất file PDF: ${e.javaClass.simpleName}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, AppStrings.pdfExportErrorToast(language), Toast.LENGTH_LONG).show()
            }
            null
        } finally {
            try {
                pdfDocument?.close()
            } catch (_: Throwable) {}
        }
    }

    private fun sharePdfFile(context: Context, file: File, tripTitle: String, lang: AppLanguage) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, AppStrings.pdfShareSubject(tripTitle, lang))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, AppStrings.pdfShareChooser(lang))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    suspend fun exportAndShareXlsx(
        context: Context,
        trip: TripEntity?,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        statuses: List<MemberFinancialStatus>,
        settlementTransfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        funds: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity> = emptyList(),
        language: AppLanguage = AppLanguage.VI
    ): File? = withContext(Dispatchers.IO) {
        try {
            val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
            val sanitizeTripTitle = (trip?.title ?: "Trip").replace("[^a-zA-Z0-9]".toRegex(), "_")
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val prefix = if (language == AppLanguage.VI) "BaoCaoExcel" else "SettlementExcel"
            val xlsxFile = File(reportsDir, "${prefix}_${sanitizeTripTitle}_${dateStr}.xlsx")

            FileOutputStream(xlsxFile).use { fos ->
                ReportGenerator.generateXlsxReport(
                    outputStream = fos,
                    trip = trip,
                    members = members,
                    summary = summary,
                    statuses = statuses,
                    settlementTransfers = settlementTransfers,
                    expenses = expenses,
                    funds = funds,
                    splits = splits,
                    language = language
                )
                fos.flush()
            }

            withContext(Dispatchers.Main) {
                shareXlsxFile(context, xlsxFile, trip?.title ?: AppStrings.pdfDefaultTripName(language), language)
            }
            xlsxFile
        } catch (e: Exception) {
            android.util.Log.e("PdfReportExporter", "Lỗi xuất file Excel: ${e.javaClass.simpleName}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, AppStrings.xlsxExportErrorToast(language), Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    private fun shareXlsxFile(context: Context, file: File, tripTitle: String, lang: AppLanguage) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_SUBJECT, AppStrings.xlsxShareSubject(tripTitle, lang))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, AppStrings.xlsxShareChooser(lang))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

// --- AppStrings Localized Extensions for PdfReportExporter (PDF Table Headers, Columns & Labels) ---

fun AppStrings.pdfBannerTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "BÁO CÁO QUYẾT TOÁN TÀI CHÍNH & BẢNG KÊ CHI TIẾT"
    else "FINANCIAL SETTLEMENT REPORT & DETAILED LEDGER"

fun AppStrings.pdfDefaultTripName(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "ĐOÀN CÔNG TÁC" else "BUSINESS TRIP"

fun AppStrings.pdfMetaInfo(dateStr: String, creatorName: String, count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Ngày lập: $dateStr • Người lập: $creatorName • Thành viên: $count người"
    else "Date: $dateStr • Creator: $creatorName • Members: $count people"

fun AppStrings.pdfSecOverview(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "I. TỔNG QUAN TÀI CHÍNH TOÀN ĐOÀN"
    else "I. GROUP FINANCIAL OVERVIEW"

fun AppStrings.pdfMetricTotalExpenses(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Tổng chi tiêu toàn đoàn: " else "• Total group expenses: "

fun AppStrings.pdfMetricPersonalPaid(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Thành viên tự chi hộ: " else "• Member out-of-pocket: "

fun AppStrings.pdfMetricFundPaid(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Chi từ quỹ chung đoàn: " else "• Paid from group fund: "

fun AppStrings.pdfMetricTotalFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Tổng quỹ đoàn đã thu: " else "• Total fund collected: "

fun AppStrings.pdfMetricRemainingFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Số dư quỹ còn lại: " else "• Remaining fund balance: "

fun AppStrings.pdfMetricAudit(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Tình trạng đối soát: " else "• Reconciliation status: "

fun AppStrings.pdfAuditBalanced(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "HỢP LỆ (Cân bằng = 0 đ)" else "VALID (Balanced = 0 VND)"

fun AppStrings.pdfAuditDiscrepancy(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "CẢNH BÁO: Lệch " else "WARNING: Discrepancy "

fun AppStrings.pdfSecMemberBalances(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "II. BẢNG TỔNG HỢP SỐ DƯ & QUYẾT TOÁN TỪNG THÀNH VIÊN"
    else "II. MEMBER BALANCE & SETTLEMENT SUMMARY"

fun AppStrings.pdfColNo(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "STT" else "No."

fun AppStrings.pdfColFullName(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Họ và tên" else "Full Name"

fun AppStrings.pdfColPaid(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chi hộ (A)" else "Out-of-pocket (A)"

fun AppStrings.pdfColFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nộp quỹ (B)" else "Fund (B)"

fun AppStrings.pdfColOwed(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Phải chịu (C)" else "Share (C)"

fun AppStrings.pdfColBalance(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Số dư quyết toán (A+B-C)" else "Net Balance (A+B-C)"

fun AppStrings.pdfBadgeToReceive(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "(Nhận lại)" else "(To Receive)"

fun AppStrings.pdfBadgeToPay(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "(Cần nộp)" else "(To Pay)"

fun AppStrings.pdfBadgeBalanced(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "0 đ (Cân bằng)" else "0 VND (Balanced)"

fun AppStrings.pdfSecSettlementPlan(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "III. KẾ HOẠCH BÙ TRỪ CÔNG NỢ TỐI ƯU GIỮA CÁC THÀNH VIÊN"
    else "III. OPTIMAL DEBT NETTING PLAN BETWEEN MEMBERS"

fun AppStrings.pdfSettleDesc(count: Int, total: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "Thuật toán tham lam đã tối ưu hóa, rút gọn còn $count giao dịch bù trừ trực tiếp (Tổng số tiền luân chuyển: $total):"
    else
        "Greedy algorithm optimized down to $count direct netting transfers (Total cashflow: $total):"

fun AppStrings.pdfSettleDescEmpty(lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "Tất cả các thành viên đã cân bằng tài chính hoặc số dư = 0 đ. Không phát sinh giao dịch bù trừ nào."
    else
        "All members are financially balanced with 0 VND balance. No netting transfers needed."

fun AppStrings.pdfSettleAllBalanced(lang: AppLanguage) =
    if (lang == AppLanguage.VI)
        "✓ Toàn bộ các thành viên trong đoàn đã cân bằng số dư (0 đ). Quyết toán hoàn tất thành công!"
    else
        "✓ All members have reconciled with 0 VND balance. Settlement completed successfully!"

fun AppStrings.pdfColSender(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Người nộp bù" else "Sender / Payer"

fun AppStrings.pdfColReceiver(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Người nhận" else "Receiver"

fun AppStrings.pdfColAmount(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Số tiền (VND)" else "Amount (VND)"

fun AppStrings.pdfColAuditNote(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nội dung đối soát" else "Audit Note"

fun AppStrings.pdfFundName(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quỹ chung đoàn" else "Group Fund"

fun AppStrings.pdfSecExpensesLedger(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "IV. BẢN KÊ CHI TIẾT CÁC KHOẢN CHI TIÊU ($count khoản chi)"
    else "IV. DETAILED EXPENSE LEDGER ($count items)"

fun AppStrings.pdfColDate(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thời gian" else "Date"

fun AppStrings.pdfColExpenseTitleNote(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tên khoản chi & Ghi chú" else "Expense Title & Note"

fun AppStrings.pdfColPayer(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nguồn chi" else "Source / Payer"

fun AppStrings.pdfColSplit(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Phân bổ" else "Allocation"

fun AppStrings.pdfSplitParticipantsText(count: Int, members: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Thành viên cùng chịu ($count người): $members"
    else "• Co-sharing members ($count people): $members"

fun AppStrings.pdfSplitAllText(count: Int, allMembers: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Chia đều tất cả $count thành viên trong đoàn: $allMembers"
    else "• Split equally among all $count members: $allMembers"

fun AppStrings.pdfNotePrefix(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Ghi chú: " else "• Note: "

fun AppStrings.pdfPayerFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quỹ đoàn" else "Group Fund"

fun AppStrings.pdfPayerPersonal(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cá nhân" else "Personal"

fun AppStrings.pdfDefaultMember(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên" else "Member"

fun AppStrings.pdfSplitEqual(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia đều" else "Equal"

fun AppStrings.pdfSplitSelect(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chọn người" else "Selected"

fun AppStrings.pdfSplitRatio(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tỷ lệ %" else "Ratio %"

fun AppStrings.pdfSplitCustom(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tùy nhập" else "Custom"

fun AppStrings.pdfTotalExpensesLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "TỔNG CỘNG CHI TIÊU TOÀN ĐOÀN:" else "TOTAL GROUP EXPENSES:"

fun AppStrings.pdfSecFundLedger(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "V. BẢN KÊ ĐÓNG GÓP QUỸ ĐOÀN ($count đợt nộp)"
    else "V. GROUP FUND CONTRIBUTIONS ($count entries)"

fun AppStrings.pdfColContributor(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên nộp quỹ" else "Contributor"

fun AppStrings.pdfColContributedAmount(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Số tiền nộp (VND)" else "Contributed Amount (VND)"

fun AppStrings.pdfColFundNote(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Ghi chú / Đợt nộp" else "Note / Installment"

fun AppStrings.pdfDefaultFundNote(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nộp quỹ" else "Fund deposit"

fun AppStrings.pdfTotalFundLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "TỔNG CỘNG TIỀN QUỸ ĐÃ THU:" else "TOTAL GROUP FUND COLLECTED:"

fun AppStrings.pdfSignLeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "TRƯỞNG ĐOÀN" else "GROUP LEADER"

fun AppStrings.pdfSignTreasurer(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "THỦ QUỸ ĐOÀN" else "GROUP TREASURER"

fun AppStrings.pdfSignHint(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "(Ký và ghi rõ họ tên)" else "(Sign and write full name)"

fun AppStrings.pdfFooterSuggestion(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đây là gợi ý, ứng dụng không thực hiện giao dịch"
    else "Suggested only, the app does not execute financial transactions"

fun AppStrings.pdfPageNumber(page: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Trang $page" else "Page $page"

fun AppStrings.pdfShareSubject(tripTitle: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Báo cáo quyết toán tài chính PDF - $tripTitle"
    else "Financial Settlement Report PDF - $tripTitle"

fun AppStrings.pdfShareChooser(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia sẻ file Báo cáo PDF qua..."
    else "Share PDF Report via..."

fun AppStrings.xlsxShareSubject(tripTitle: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Báo cáo quyết toán tài chính Excel - $tripTitle"
    else "Financial Settlement Report Excel - $tripTitle"

fun AppStrings.xlsxShareChooser(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia sẻ file Báo cáo Excel qua..."
    else "Share Excel Report via..."

fun AppStrings.pdfExportErrorToast(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Không thể xuất file PDF báo cáo. Vui lòng thử lại sau."
    else "Cannot export PDF report. Please try again later."

fun AppStrings.xlsxExportErrorToast(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Không thể xuất file Excel báo cáo. Vui lòng thử lại sau."
    else "Cannot export Excel report. Please try again later."

fun AppStrings.pdfColTransferred(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã chuyển" else "Status"

fun AppStrings.pdfStatusTransferred(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã chuyển" else "Done"

fun AppStrings.pdfStatusPending(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa" else "Pending"

