package com.hiepnguyen.tripfinance.domain.export

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWriterTest {

    @Test
    fun `test XlsxWriter generates valid ZIP structure with Content_Types first and all mandatory parts`() {
        val bos = ByteArrayOutputStream()
        XlsxWriter(bos).use { writer ->
            writer.sheet("Expenses") {
                headerRow("STT", "Khoản chi", "Số tiền")
                row(1L, "Ăn trưa hải sản", 1500000L)
            }
            writer.sheet("Summary") {
                headerRow("Chỉ số", "Giá trị")
                row("Tổng chi", 1500000L)
            }
        }

        val zipBytes = bos.toByteArray()
        assertTrue("Zip archive must not be empty", zipBytes.isNotEmpty())

        val zis = ZipInputStream(ByteArrayInputStream(zipBytes))
        val entryNames = mutableListOf<String>()

        var entry = zis.nextEntry
        while (entry != null) {
            entryNames.add(entry.name)
            zis.closeEntry()
            entry = zis.nextEntry
        }

        // 1. Kiểm tra [Content_Types].xml phải là phần tử ĐẦU TIÊN
        assertEquals("[Content_Types].xml must be the first entry", "[Content_Types].xml", entryNames.first())

        // 2. Kiểm tra tất cả các phần bắt buộc phải có mặt đầy đủ
        val expectedEntries = listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml",
            "xl/worksheets/sheet2.xml"
        )
        for (expected in expectedEntries) {
            assertTrue("Archive must contain $expected", entryNames.contains(expected))
        }
    }

    @Test
    fun `test XML validity of all generated OpenXML parts`() {
        val bos = ByteArrayOutputStream()
        XlsxWriter(bos).use { writer ->
            writer.sheet("Chi Tiêu Đoàn") {
                headerRow("STT", "Mục chi", "Số tiền (VND)", "Tỷ giá", "Ghi chú")
                row {
                    cell(1L)
                    cell("Vé tham quan & Cáp treo Bà Nà")
                    currencyCell(950000L)
                    decimalCell(1.0)
                    cell("Đã thanh toán bằng quỹ")
                }
            }
        }

        val zis = ZipInputStream(ByteArrayInputStream(bos.toByteArray()))
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        val db = dbf.newDocumentBuilder()

        var count = 0
        var entry = zis.nextEntry
        while (entry != null) {
            val content = zis.readBytes()
            assertTrue("Entry ${entry.name} should not be empty", content.isNotEmpty())

            // Mọi file .xml và .rels đều phải parse thành công không lỗi cú pháp
            val doc = db.parse(ByteArrayInputStream(content))
            assertNotNull("Document parsed from ${entry.name} should not be null", doc.documentElement)

            count++
            zis.closeEntry()
            entry = zis.nextEntry
        }
        assertTrue("Should have validated at least 6 XML parts", count >= 6)
    }

    @Test
    fun `test Formula injection prevention and inlineStr storage`() {
        val bos = ByteArrayOutputStream()
        val maliciousFormulas = listOf(
            "=HYPERLINK(\"http://malicious.site\",\"Click Me\")",
            "+1+2",
            "-SUM(A1:A10)",
            "@cmd|'/C calc'!A0"
        )

        XlsxWriter(bos).use { writer ->
            writer.sheet("SecurityTest") {
                headerRow("Test Case", "Input")
                maliciousFormulas.forEachIndexed { index, formula ->
                    row {
                        cell("Attack_${index + 1}")
                        cell(formula)
                    }
                }
            }
        }

        val zis = ZipInputStream(ByteArrayInputStream(bos.toByteArray()))
        var sheetXml: String? = null
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/worksheets/sheet1.xml") {
                sheetXml = String(zis.readBytes(), Charsets.UTF_8)
                break
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        assertNotNull("sheet1.xml must exist", sheetXml)

        // 1. Kiểm tra thuộc tính t="inlineStr" luôn được áp dụng cho văn bản
        assertTrue("Cells must use t=\"inlineStr\"", sheetXml!!.contains("t=\"inlineStr\""))

        // 2. Không được xuất hiện thẻ <f> (thẻ công thức)
        assertFalse("Must never output <f> formula tag", sheetXml.contains("<f>"))

        // 3. Parse XML và kiểm tra các chuỗi formula đã được chèn dấu nháy đơn ' ở đầu
        val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = db.parse(ByteArrayInputStream(sheetXml.toByteArray(Charsets.UTF_8)))
        val tNodes = doc.getElementsByTagName("t")

        val texts = mutableListOf<String>()
        for (i in 0 until tNodes.length) {
            texts.add(tNodes.item(i).textContent)
        }

        for (formula in maliciousFormulas) {
            val expectedSanitized = "'$formula"
            assertTrue(
                "Text content should contain sanitized leading single quote for $formula",
                texts.contains(expectedSanitized)
            )
        }
    }

    @Test
    fun `test Special characters XML escaping and invalid control character stripping`() {
        val bos = ByteArrayOutputStream()
        // Ký tự đặc biệt XML + Tiếng Việt có dấu + Ký tự điều khiển XML 1.0 không hợp lệ (\u0000, \u0008, \u000B, \u001F)
        val rawSpecialText = "Khoản & Mục: <Ăn \"Uống\" 'Đặc Sản'> \u0000\u0008\u000B\u001F"
        val expectedCleanedText = "Khoản & Mục: <Ăn \"Uống\" 'Đặc Sản'> "

        XlsxWriter(bos).use { writer ->
            writer.sheet("Escaping") {
                headerRow("Mô tả")
                row(rawSpecialText)
            }
        }

        val zis = ZipInputStream(ByteArrayInputStream(bos.toByteArray()))
        var sheetXml: String? = null
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/worksheets/sheet1.xml") {
                sheetXml = String(zis.readBytes(), Charsets.UTF_8)
                break
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        assertNotNull("sheet1.xml must exist", sheetXml)

        // Kiểm tra XML thô phải được escape chuẩn: &amp;, &lt;, &gt;, &quot;, &apos;
        assertTrue(sheetXml!!.contains("&amp;"))
        assertTrue(sheetXml.contains("&lt;"))
        assertTrue(sheetXml.contains("&gt;"))
        assertTrue(sheetXml.contains("&quot;"))
        assertTrue(sheetXml.contains("&apos;"))
        assertFalse("Invalid XML control char 0x00 must be stripped", sheetXml.contains("\u0000"))
        assertFalse("Invalid XML control char 0x08 must be stripped", sheetXml.contains("\u0008"))

        // Parse lại bằng XML DOM chuẩn: Text parsed phải khớp với text mong muốn (đã lọc control char)
        val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = db.parse(ByteArrayInputStream(sheetXml.toByteArray(Charsets.UTF_8)))
        val tNodes = doc.getElementsByTagName("t")

        var foundText = false
        for (i in 0 until tNodes.length) {
            if (tNodes.item(i).textContent == expectedCleanedText) {
                foundText = true
                break
            }
        }
        assertTrue("DOM parser should accurately recover escaped text: $expectedCleanedText", foundText)
    }

    @Test
    fun `test Numeric values preserved exactly with appropriate styles`() {
        val bos = ByteArrayOutputStream()
        val vndAmount = 25000000L
        val usdAmount = 1050.75
        val smallDouble = 0.005

        XlsxWriter(bos).use { writer ->
            writer.sheet("Numbers") {
                headerRow("Mục", "Số tiền VND", "Ngoại tệ USD", "Số nhỏ")
                row {
                    cell("Thu chi")
                    cell(vndAmount) // style CURRENCY_INT (index 2)
                    cell(usdAmount) // style CURRENCY_DECIMAL (index 3)
                    cell(smallDouble)
                }
            }
        }

        val zis = ZipInputStream(ByteArrayInputStream(bos.toByteArray()))
        var sheetXml: String? = null
        var stylesXml: String? = null
        var entry = zis.nextEntry
        while (entry != null) {
            when (entry.name) {
                "xl/worksheets/sheet1.xml" -> sheetXml = String(zis.readBytes(), Charsets.UTF_8)
                "xl/styles.xml" -> stylesXml = String(zis.readBytes(), Charsets.UTF_8)
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        assertNotNull(sheetXml)
        assertNotNull(stylesXml)

        // 1. Kiểm tra định dạng số trong styles.xml
        assertTrue("styles.xml must contain numFmt #,##0", stylesXml!!.contains("#,##0"))
        assertTrue("styles.xml must contain numFmt #,##0.00", stylesXml.contains("#,##0.00"))

        // 2. Kiểm tra giá trị số thật trong sheet1.xml
        assertTrue("Must contain exact Long value in <v>", sheetXml!!.contains("<v>25000000</v>"))
        assertTrue("Must contain Double value in <v>", sheetXml.contains("<v>1050.75</v>"))
        assertTrue("Must apply style s=\"2\" for integer currency", sheetXml.contains("s=\"2\""))
        assertTrue("Must apply style s=\"3\" for decimal currency", sheetXml.contains("s=\"3\""))
    }

    @Test
    fun `test Multiple sheets with name sanitization and uniqueness`() {
        val invalidSheetName = "Báo cáo: 2026 [Đà Nẵng] / Chuyến đi *?" // chứa : [ ] / * ?
        val bos = ByteArrayOutputStream()

        XlsxWriter(bos).use { writer ->
            writer.sheet(invalidSheetName) {
                row("Dữ liệu 1")
            }
            // Thử thêm sheet cùng tên để kiểm tra xử lý trùng lặp
            writer.sheet(invalidSheetName) {
                row("Dữ liệu 2")
            }
            // Sheet tên dài vượt quá 31 ký tự
            writer.sheet("Tên sheet này rất dài và chắc chắn vượt quá giới hạn ba mươi mốt ký tự của Excel") {
                row("Dữ liệu 3")
            }
        }

        val zis = ZipInputStream(ByteArrayInputStream(bos.toByteArray()))
        var workbookXml: String? = null
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/workbook.xml") {
                workbookXml = String(zis.readBytes(), Charsets.UTF_8)
                break
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        assertNotNull(workbookXml)

        val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = db.parse(ByteArrayInputStream(workbookXml!!.toByteArray(Charsets.UTF_8)))
        val sheetNodes = doc.getElementsByTagName("sheet")

        assertEquals("Must have 3 sheets", 3, sheetNodes.length)

        val sheetNames = mutableListOf<String>()
        for (i in 0 until sheetNodes.length) {
            val name = (sheetNodes.item(i) as Element).getAttribute("name")
            sheetNames.add(name)

            // Mỗi tên sheet không được dài hơn 31 ký tự
            assertTrue("Sheet name '$name' must be <= 31 chars (was ${name.length})", name.length <= 31)

            // Không được chứa các ký tự cấm của Excel
            for (c in charArrayOf(':', '\\', '/', '?', '*', '[', ']')) {
                assertFalse("Sheet name '$name' must not contain forbidden char '$c'", name.contains(c))
            }
        }

        // Tên các sheet phải độc nhất
        assertEquals("Sheet names must be unique", sheetNames.toSet().size, sheetNames.size)
    }

    @Test
    fun `test Freeze header row and column width bounds`() {
        val bos = ByteArrayOutputStream()
        XlsxWriter(bos).use { writer ->
            writer.sheet("AutoWidthAndFreeze", freezeHeader = true) {
                headerRow("ID", "Tên ngắn", "Mô tả nội dung chi tiết rất dài nhằm kiểm tra độ rộng cột tự động của bảng tính")
                row(1L, "Ngắn", "Chi tiết...")
            }
        }

        val zis = ZipInputStream(ByteArrayInputStream(bos.toByteArray()))
        var sheetXml: String? = null
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/worksheets/sheet1.xml") {
                sheetXml = String(zis.readBytes(), Charsets.UTF_8)
                break
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        assertNotNull(sheetXml)

        // 1. Kiểm tra freeze pane
        assertTrue("Must contain pane freeze", sheetXml!!.contains("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"))

        // 2. Parse cols và kiểm tra độ rộng cột nằm trong 8.0 đến 60.0
        val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = db.parse(ByteArrayInputStream(sheetXml.toByteArray(Charsets.UTF_8)))
        val colNodes = doc.getElementsByTagName("col")

        assertTrue("Must have <col> definitions", colNodes.length >= 3)
        for (i in 0 until colNodes.length) {
            val colElem = colNodes.item(i) as Element
            val widthStr = colElem.getAttribute("width")
            val width = widthStr.toDouble()
            assertTrue("Column width $width must be >= 8.0", width >= 8.0)
            assertTrue("Column width $width must be <= 60.0", width <= 60.0)
        }
    }

    @Test
    fun `test getColumnName conversion helper`() {
        assertEquals("A", XlsxWriter.getColumnName(1))
        assertEquals("B", XlsxWriter.getColumnName(2))
        assertEquals("Z", XlsxWriter.getColumnName(26))
        assertEquals("AA", XlsxWriter.getColumnName(27))
        assertEquals("AB", XlsxWriter.getColumnName(28))
        assertEquals("AZ", XlsxWriter.getColumnName(52))
        assertEquals("BA", XlsxWriter.getColumnName(53))
    }
}
