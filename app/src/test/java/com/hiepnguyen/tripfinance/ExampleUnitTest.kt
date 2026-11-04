package com.hiepnguyen.tripfinance

import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.engine.SettlementEngine
import com.hiepnguyen.tripfinance.domain.model.BalanceStatus
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    private fun createMember(id: String, name: String, role: String = "MEMBER"): TripMemberEntity {
        return TripMemberEntity(
            id = id,
            tripId = "trip_test",
            userId = "user_$id",
            name = name,
            role = role,
            isActive = true,
            bankName = "Bank $name",
            bankAccount = "123456789",
            bankAccountHolder = name.uppercase()
        )
    }

    @Test
    fun `test settlement with remaining fund positive - user exact scenario`() {
        // User example:
        // Quỹ thu 2.000.000đ, chi 650.000đ, còn 1.350.000đ
        // Hiệp được nhận lại 1.883.000đ
        // Quang được nhận lại 501.000đ
        // Mai phải nộp 517.000đ
        // Lan phải nộp 517.000đ
        val hiep = createMember("member_hiep", "Nguyễn Hồng Hiệp", "ADMIN")
        val mai = createMember("member_mai", "Trần Tuyết Mai", "TREASURER")
        val quang = createMember("member_quang", "Lê Nhật Quang", "MEMBER")
        val lan = createMember("member_lan", "Phạm Hương Lan", "MEMBER")

        val statuses = listOf(
            MemberFinancialStatus(hiep, totalPaid = 2900000L, outOfPocketPaid = 2400000L, fundContributed = 500000L, totalOwed = 1017000L, balance = 1883000L, status = BalanceStatus.RECEIVE),
            MemberFinancialStatus(quang, totalPaid = 1518000L, outOfPocketPaid = 1018000L, fundContributed = 500000L, totalOwed = 1017000L, balance = 501000L, status = BalanceStatus.RECEIVE),
            MemberFinancialStatus(mai, totalPaid = 500000L, outOfPocketPaid = 0L, fundContributed = 500000L, totalOwed = 1017000L, balance = -517000L, status = BalanceStatus.PAY),
            MemberFinancialStatus(lan, totalPaid = 500000L, outOfPocketPaid = 0L, fundContributed = 500000L, totalOwed = 1017000L, balance = -517000L, status = BalanceStatus.PAY)
        )

        val remainingFund = 1350000L
        val transfers = SettlementEngine.computeSimplifiedTransfers(
            memberStatuses = statuses,
            tripJoinCode = "DN2026",
            remainingFund = remainingFund,
            fundHolder = mai
        )

        assertFalse("Transfers should not be empty when there is remaining fund and non-zero balances", transfers.isEmpty())

        // Verify total money received by Hiệp = 1,883,000 VND
        val hiepReceived = transfers.filter { it.toMember.id == "member_hiep" }.sumOf { it.amount }
        assertEquals(1883000L, hiepReceived)

        // Verify total money received by Quang = 501,000 VND
        val quangReceived = transfers.filter { it.toMember.id == "member_quang" }.sumOf { it.amount }
        assertEquals(501000L, quangReceived)

        // Verify total money paid by Mai = 517,000 VND
        val maiPaid = transfers.filter { it.fromMember.id == "member_mai" }.sumOf { it.amount }
        assertEquals(517000L, maiPaid)

        // Verify total money paid by Lan = 517,000 VND
        val lanPaid = transfers.filter { it.fromMember.id == "member_lan" }.sumOf { it.amount }
        assertEquals(517000L, lanPaid)

        // Verify total money disbursed from Fund = 1,350,000 VND
        val fundPaid = transfers.filter { it.fromMember.id == "FUND_ORGANIZATION" }.sumOf { it.amount }
        assertEquals(1350000L, fundPaid)

        // Total transfers amount
        val totalTransfers = transfers.sumOf { it.amount }
        assertEquals(2384000L, totalTransfers)
    }

    @Test
    fun `test settlement with overspent fund negative`() {
        // Quỹ bị chi vượt: remainingFund = -500,000 VND
        // Quỹ chi 1.500.000đ nhưng chỉ thu 1.000.000đ
        // Thành viên A chi 0, chịu 500k -> balance = -500k
        val memA = createMember("mem_a", "Thành viên A")
        val treasurer = createMember("treasurer", "Thủ quỹ", "TREASURER")

        val statuses = listOf(
            MemberFinancialStatus(memA, totalPaid = 0L, outOfPocketPaid = 0L, fundContributed = 0L, totalOwed = 500000L, balance = -500000L, status = BalanceStatus.PAY)
        )

        val remainingFund = -500000L
        val transfers = SettlementEngine.computeSimplifiedTransfers(
            memberStatuses = statuses,
            tripJoinCode = "TRIP1",
            remainingFund = remainingFund,
            fundHolder = treasurer
        )

        assertEquals(1, transfers.size)
        val transfer = transfers[0]
        assertEquals("mem_a", transfer.fromMember.id)
        assertEquals("FUND_ORGANIZATION", transfer.toMember.id)
        assertEquals(500000L, transfer.amount)
        assertEquals("Thành viên A chuyển cho Quỹ đoàn (Thủ quỹ: Thủ quỹ)", transfer.transferNote)
        assertFalse(transfer.transferNote.contains("TRIP1"))
    }

    @Test
    fun `test settlement when all balanced and remaining fund zero`() {
        val hiep = createMember("member_hiep", "Hiệp")
        val statuses = listOf(
            MemberFinancialStatus(hiep, totalPaid = 100000L, outOfPocketPaid = 100000L, fundContributed = 0L, totalOwed = 100000L, balance = 0L, status = BalanceStatus.BALANCED)
        )

        val transfers = SettlementEngine.computeSimplifiedTransfers(
            memberStatuses = statuses,
            tripJoinCode = "TRIP0",
            remainingFund = 0L
        )

        assertTrue(transfers.isEmpty())
    }

    @Test
    fun `test SplitCalculator equal split remainder distribution`() {
        val members = listOf("user_1", "user_2", "user_3")
        // 100,000 VND / 3 = 33,333 per person with remainder 1 VND
        val splits = com.hiepnguyen.tripfinance.domain.engine.SplitCalculator.calculateEqualSplit(
            totalAmount = 100000L,
            memberIds = members,
            primaryMemberId = "user_2"
        )
        assertEquals(3, splits.size)
        val user1 = splits.find { it.first == "user_1" }?.second
        val user2 = splits.find { it.first == "user_2" }?.second
        val user3 = splits.find { it.first == "user_3" }?.second

        assertEquals(33333L, user1)
        assertEquals(33334L, user2) // primaryMemberId gets remainder 1 VND
        assertEquals(33333L, user3)
        assertEquals(100000L, splits.sumOf { it.second })
    }

    @Test
    fun `test SplitCalculator ratio split validation and exact rounding`() {
        // Test 1: Exactly 100%
        val ratiosValid = listOf("user_1" to 33.33, "user_2" to 33.33, "user_3" to 33.34)
        val (splitsValid, isValid) = com.hiepnguyen.tripfinance.domain.engine.SplitCalculator.calculateRatioSplit(
            totalAmount = 100000L,
            memberRatios = ratiosValid
        )
        assertTrue("Total 100% ratio should be valid", isValid)
        assertEquals(100000L, splitsValid.sumOf { it.second })

        // Test 2: Incomplete ratio (< 100%)
        val ratiosIncomplete = listOf("user_1" to 50.0, "user_2" to 30.0)
        val (_, isInvalid) = com.hiepnguyen.tripfinance.domain.engine.SplitCalculator.calculateRatioSplit(
            totalAmount = 100000L,
            memberRatios = ratiosIncomplete
        )
        assertFalse("80% total ratio should NOT be valid", isInvalid)
    }

    @Test
    fun `test TripCodeGenerator format charset and high entropy uniqueness`() {
        val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toSet()

        // 1. Kiểm tra tiền tố mặc định và độ dài mã (prefix + 6 ký tự)
        val defaultCode = com.hiepnguyen.tripfinance.domain.model.TripCodeGenerator.generateCode()
        assertTrue("Mã mặc định phải bắt đầu bằng 'TRIP-'", defaultCode.startsWith("TRIP-"))
        assertEquals("Độ dài mã mặc định phải là 11 (5 ký tự prefix + 6 ký tự ngẫu nhiên)", 11, defaultCode.length)

        // 2. Kiểm tra tiền tố tùy chỉnh
        val customCode = com.hiepnguyen.tripfinance.domain.model.TripCodeGenerator.generateCode("DN2026-")
        assertTrue("Mã tùy chỉnh phải bắt đầu bằng 'DN2026-'", customCode.startsWith("DN2026-"))
        assertEquals("Độ dài mã tùy chỉnh phải đúng prefix + 6", 13, customCode.length)

        // 3. Kiểm tra tập ký tự cho phép (Base32 không gây nhầm lẫn 0/O, 1/I)
        val randomPart = defaultCode.removePrefix("TRIP-")
        assertEquals(6, randomPart.length)
        for (char in randomPart) {
            assertTrue("Ký tự '$char' phải nằm trong tập ký tự an toàn", char in allowedChars)
            assertFalse("Không được chứa số 0 dễ nhầm lẫn", char == '0')
            assertFalse("Không được chứa chữ O dễ nhầm lẫn", char == 'O')
            assertFalse("Không được chứa số 1 dễ nhầm lẫn", char == '1')
            assertFalse("Không được chứa chữ I dễ nhầm lẫn", char == 'I')
        }

        // 4. Kiểm tra tính ngẫu nhiên và không trùng lặp (High Entropy Uniqueness: 1.000 mã khác nhau đôi một)
        val generatedCodes = HashSet<String>()
        val count = 1000
        for (i in 0 until count) {
            val code = com.hiepnguyen.tripfinance.domain.model.TripCodeGenerator.generateCode("T-")
            assertTrue("Mã sinh ra phải duy nhất, không trùng lặp", generatedCodes.add(code))
        }
        assertEquals(count, generatedCodes.size)
    }

    @Test
    fun `test FinancialInputValidator comprehensive parsing rules and limits`() {
        // --- 1. Tiền tệ VND ---
        // Chuỗi rỗng -> Báo lỗi
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("   ", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)

        // Số nguyên hợp lệ
        val v1 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("50000", "VND")
        assertTrue(v1 is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success)
        assertEquals(50000.0, (v1 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)
        assertTrue(v1.isWholeNumber)

        // Phân cách hàng nghìn bằng dấu chấm chuẩn Việt Nam: 1.500 -> 1500; 25.450 -> 25450; 1.500.000 -> 1500000
        val v2 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("1.500", "VND")
        assertEquals(1500.0, (v2 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        val v3 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("25.450", "VND")
        assertEquals(25450.0, (v3 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        val v4 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("1.500.000", "VND")
        assertEquals(1500000.0, (v4 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        // Phân cách hàng nghìn bằng dấu phẩy: 1,500,000 -> 1500000
        val v5 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("1,500,000", "VND")
        assertEquals(1500000.0, (v5 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        // Chặn số thập phân trong VND: 1.5, 1,5, 25.4, 1500.5
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("1.5", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("1,5", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("1.500.000,50", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)

        // Chặn ký tự chữ hoặc ký hiệu lạ trong VND: 50k, 100k, 100$, 50a
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("50k", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("abc", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)

        // Chặn số tiền <= 0
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("0", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("-50000", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)

        // Chặn vượt hạn mức trần 100 tỷ VND
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("100000000001", "VND") is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)

        // --- 2. Ngoại tệ (USD, EUR,...) ---
        val usd1 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("12.50", "USD")
        assertTrue(usd1 is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success)
        assertEquals(12.50, (usd1 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)
        assertFalse(usd1.isWholeNumber)

        val usd2 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("100", "USD")
        assertTrue(usd2 is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success)
        assertTrue((usd2 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).isWholeNumber)

        // Ngoại tệ với dấu phẩy thập phân: 12,50 -> 12.50
        val eur1 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("12,50", "EUR")
        assertEquals(12.50, (eur1 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        // --- 3. Tỷ giá quy đổi (parseRate) ---
        // Cùng đồng tiền với đồng tiền gốc (VND) -> luôn trả về 1.0
        val rBase = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("bất kỳ", "VND", "VND")
        assertTrue(rBase is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success)
        assertEquals(1.0, (rBase as com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success).rate, 0.001)

        // Tỷ giá chuẩn
        val rUsd = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("25450", "USD", "VND")
        assertTrue(rUsd is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success)
        assertEquals(25450.0, (rUsd as com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success).rate, 0.001)

        // Tỷ giá dạng phân cách hàng nghìn 25.450
        val rUsdGrouping = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("25.450", "USD", "VND")
        assertEquals(25450.0, (rUsdGrouping as com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success).rate, 0.001)

        // Chặn chữ cái "abc" trong tỷ giá
        val rBad = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("abc", "USD", "VND")
        assertTrue(rBad is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Error)

        // Chặn tỷ giá <= 0 hoặc rỗng
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("", "USD", "VND") is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Error)
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("0", "USD", "VND") is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Error)
        assertTrue(com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("-25000", "USD", "VND") is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Error)

        // --- 4. Quy đổi an toàn (convertToVnd / convertToBase) ---
        assertEquals(254500L, com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.convertToVnd(10.0, 25450.0))
        assertEquals(318125L, com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.convertToVnd(12.50, 25450.0))
        assertEquals(0L, com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.convertToVnd(0.0, 25450.0))
        assertEquals(0L, com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.convertToVnd(-10.0, 25450.0))
        assertEquals(0L, com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.convertToVnd(Double.NaN, 25450.0))
        assertEquals(0L, com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.convertToVnd(10.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `test generateXlsxReport produces valid workbook with 5 sheets and zero bank details`() {
        val hiep = createMember("member_hiep", "Nguyễn Hồng Hiệp", "ADMIN")
        val mai = createMember("member_mai", "Trần Tuyết Mai", "TREASURER")
        val trip = com.hiepnguyen.tripfinance.data.entity.TripEntity(
            id = "trip_test",
            title = "=HYPERLINK(\"http://evil.com\", \"Đoàn Đà Nẵng\")",
            description = "Công tác",
            joinCode = "DN2026",
            startDate = 1000L,
            endDate = 2000L
        )
        val expense = com.hiepnguyen.tripfinance.data.entity.ExpenseEntity(
            id = "exp_1",
            tripId = "trip_test",
            title = "Ăn tối",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = "member_hiep",
            totalAmount = 500000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 500000L,
            splitType = "EQUAL",
            note = "Ăn hải sản",
            timestamp = 1500L,
            createdMemberId = "member_hiep"
        )
        val fund = com.hiepnguyen.tripfinance.data.entity.FundContributionEntity(
            id = "fund_1",
            tripId = "trip_test",
            memberId = "member_mai",
            amount = 1000000L,
            currency = "VND",
            exchangeRate = 1.0,
            convertedAmount = 1000000L,
            note = "Đợt 1",
            timestamp = 1200L,
            recordedByMemberId = "member_hiep"
        )
        val summary = com.hiepnguyen.tripfinance.domain.model.FinancialSummary(
            totalExpenses = 500000L,
            personalPaidExpenses = 500000L,
            fundPaidExpenses = 0L,
            totalFundCollected = 1000000L,
            remainingFund = 1000000L,
            isBalanced = true,
            balanceDiscrepancy = 0L
        )
        val statusHiep = MemberFinancialStatus(hiep, 500000L, 500000L, 0L, 250000L, 250000L, BalanceStatus.RECEIVE)
        val statusMai = MemberFinancialStatus(mai, 1000000L, 0L, 1000000L, 250000L, -250000L, BalanceStatus.PAY)
        val transfer = com.hiepnguyen.tripfinance.domain.model.SettlementTransfer(
            fromMember = mai,
            toMember = hiep,
            amount = 250000L,
            transferNote = "Trần Tuyết Mai chuyển cho Nguyễn Hồng Hiệp"
        )

        val baos = java.io.ByteArrayOutputStream()
        com.hiepnguyen.tripfinance.domain.export.ReportGenerator.generateXlsxReport(
            outputStream = baos,
            trip = trip,
            members = listOf(hiep, mai),
            summary = summary,
            statuses = listOf(statusHiep, statusMai),
            settlementTransfers = listOf(transfer),
            expenses = listOf(expense),
            funds = listOf(fund)
        )

        val bytes = baos.toByteArray()
        assertTrue("File XLSX phải có dung lượng lớn hơn 0", bytes.isNotEmpty())

        val entries = mutableMapOf<String, String>()
        var firstEntryName: String? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (firstEntryName == null) firstEntryName = entry.name
                val content = zis.bufferedReader(Charsets.UTF_8).readText()
                entries[entry.name] = content
                entry = zis.nextEntry
            }
        }

        // 1. [Content_Types].xml phải là phần tử đầu tiên
        assertEquals("[Content_Types].xml", firstEntryName)

        // 2. Phải có đủ 5 sheet
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet3.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet4.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet5.xml"))

        // 3. Tên 5 sheet trong workbook.xml
        val wbXml = entries["xl/workbook.xml"] ?: ""
        assertTrue(wbXml.contains("name=\"Tổng quan\""))
        assertTrue(wbXml.contains("name=\"Chi tiêu\""))
        assertTrue(wbXml.contains("name=\"Theo thành viên\""))
        assertTrue(wbXml.contains("name=\"Quỹ chung\""))
        assertTrue(wbXml.contains("name=\"Gợi ý chuyển khoản\""))

        // 4. Tuyệt đối không chứa số tài khoản, tên ngân hàng trong toàn bộ file
        val allContent = entries.values.joinToString("\n")
        assertFalse("Không được chứa tên ngân hàng", allContent.contains("Bank Nguyễn Hồng Hiệp"))
        assertFalse("Không được chứa số tài khoản", allContent.contains("123456789"))

        // 5. Chống formula injection: chuỗi bắt đầu bằng '=' được vô hiệu hóa bằng dấu nháy đơn
        assertTrue("Tiêu đề chứa công thức phải có tiền tố dấu nháy đơn", allContent.contains("'=HYPERLINK"))

        // 6. Số liệu khớp
        val sheet1Xml = entries["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue("Sheet 1 phải chứa tổng chi tiêu 500000", sheet1Xml.contains("<v>500000</v>"))
        assertTrue("Sheet 1 phải chứa tổng quỹ 1000000", sheet1Xml.contains("<v>1000000</v>"))
    }

    @Test
    fun `test BackupCryptoUtils PBKDF2 iterations configuration meets 600_000 OWASP standard`() {
        // Xác nhận hằng số số vòng lặp PBKDF2 chuẩn OWASP là 600.000 vòng
        assertEquals(
            "Số vòng lặp PBKDF2 tiêu chuẩn OWASP phải là 600.000 vòng",
            600_000,
            com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.OWASP_RECOMMENDED_ITERATIONS
        )

        // Xác nhận ngưỡng kiểm soát an toàn chống DoS CPU
        assertEquals(1_000, com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.MIN_ITERATIONS)
        assertEquals(1_000_000, com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.MAX_ITERATIONS)
    }
}

