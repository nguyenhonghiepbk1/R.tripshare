package com.hiepnguyen.tripfinance.domain.export

import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Các kiểu định dạng ô (Cell Styles) được hỗ trợ trong file .xlsx.
 * Chỉ số index tương ứng với thứ tự khai báo trong `xl/styles.xml`.
 */
enum class CellStyle(val index: Int) {
    NORMAL(0),           // Văn bản / Số thường, không viền, font Calibri 11pt
    HEADER(1),           // Tiêu đề bảng: Chữ đậm, nền xám nhạt (#E5E7EB), viền mỏng bao quanh, căn giữa
    CURRENCY_INT(2),     // Số nguyên / Tiền VND định dạng: #,##0
    CURRENCY_DECIMAL(3)  // Số thực / Ngoại tệ định dạng: #,##0.00
}

/**
 * Đại diện cho một ô dữ liệu trong bảng tính.
 */
sealed class CellValue {
    abstract val style: CellStyle

    data class Text(val text: String, override val style: CellStyle = CellStyle.NORMAL) : CellValue()
    data class IntNumber(val value: Long, override val style: CellStyle = CellStyle.CURRENCY_INT) : CellValue()
    data class DecNumber(val value: Double, override val style: CellStyle = CellStyle.CURRENCY_DECIMAL) : CellValue()
}

/**
 * Builder để xây dựng các ô trong một hàng tính (Row).
 */
class RowBuilder {
    internal val cells = mutableListOf<CellValue>()

    /**
     * Thêm ô văn bản (inlineStr). Tự động làm sạch chống Formula Injection và escape XML.
     */
    fun cell(text: String?, style: CellStyle = CellStyle.NORMAL): RowBuilder {
        cells.add(CellValue.Text(text ?: "", style))
        return this
    }

    /**
     * Thêm ô số nguyên (định dạng tiền tệ VND #,##0).
     */
    fun cell(number: Long, style: CellStyle = CellStyle.CURRENCY_INT): RowBuilder {
        cells.add(CellValue.IntNumber(number, style))
        return this
    }

    fun cell(number: Int, style: CellStyle = CellStyle.CURRENCY_INT): RowBuilder {
        return cell(number.toLong(), style)
    }

    /**
     * Thêm ô số thực (định dạng số thập phân / ngoại tệ #,##0.00).
     */
    fun cell(number: Double, style: CellStyle = CellStyle.CURRENCY_DECIMAL): RowBuilder {
        cells.add(CellValue.DecNumber(number, style))
        return this
    }

    fun cell(number: Float, style: CellStyle = CellStyle.CURRENCY_DECIMAL): RowBuilder {
        return cell(number.toDouble(), style)
    }

    /**
     * Thêm ô với kiểu dữ liệu tự động nhận diện.
     */
    fun cell(value: Any?, style: CellStyle? = null): RowBuilder {
        when (value) {
            null -> cell("", style ?: CellStyle.NORMAL)
            is Long -> cell(value, style ?: CellStyle.CURRENCY_INT)
            is Int -> cell(value.toLong(), style ?: CellStyle.CURRENCY_INT)
            is Double -> cell(value, style ?: CellStyle.CURRENCY_DECIMAL)
            is Float -> cell(value.toDouble(), style ?: CellStyle.CURRENCY_DECIMAL)
            is Number -> cell(value.toLong(), style ?: CellStyle.CURRENCY_INT)
            else -> cell(value.toString(), style ?: CellStyle.NORMAL)
        }
        return this
    }

    /**
     * Thêm ô tiêu đề (Header cell) với chữ đậm và nền xám.
     */
    fun headerCell(title: String): RowBuilder {
        cells.add(CellValue.Text(title, CellStyle.HEADER))
        return this
    }

    /**
     * Thêm ô tiền tệ nguyên (#,##0).
     */
    fun currencyCell(amount: Long): RowBuilder {
        cells.add(CellValue.IntNumber(amount, CellStyle.CURRENCY_INT))
        return this
    }

    /**
     * Thêm ô tiền tệ / số thực thập phân (#,##0.00).
     */
    fun decimalCell(amount: Double): RowBuilder {
        cells.add(CellValue.DecNumber(amount, CellStyle.CURRENCY_DECIMAL))
        return this
    }
}

/**
 * Builder để tạo các hàng và cấu hình cho một Sheet.
 */
class SheetBuilder internal constructor(val name: String) {
    internal val rows = mutableListOf<List<CellValue>>()
    var freezeHeader: Boolean = true

    /**
     * Thêm một hàng dữ liệu tùy biến qua DSL.
     */
    fun row(builder: RowBuilder.() -> Unit): SheetBuilder {
        val rb = RowBuilder()
        rb.builder()
        rows.add(rb.cells)
        return this
    }

    /**
     * Thêm một hàng dữ liệu từ danh sách giá trị.
     */
    fun row(vararg values: Any?): SheetBuilder {
        val rb = RowBuilder()
        values.forEach { rb.cell(it) }
        rows.add(rb.cells)
        return this
    }

    /**
     * Thêm một hàng tiêu đề (tự động áp dụng style HEADER cho tất cả các cột).
     */
    fun headerRow(vararg headers: String): SheetBuilder {
        val rb = RowBuilder()
        headers.forEach { rb.headerCell(it) }
        rows.add(rb.cells)
        return this
    }

    /**
     * Thêm một hàng tiêu đề từ danh sách chuỗi.
     */
    fun headerRow(headers: List<String>): SheetBuilder {
        val rb = RowBuilder()
        headers.forEach { rb.headerCell(it) }
        rows.add(rb.cells)
        return this
    }
}

/**
 * Bộ tạo file Microsoft Excel (.xlsx) chuẩn OpenXML (ECMA-376) tối giản,
 * 100% tự lập trình không sử dụng thư viện bên ngoài và không yêu cầu quyền INTERNET.
 *
 * Tính năng chính:
 * 1. Đóng gói chuẩn ZIP với `[Content_Types].xml` đặt ở vị trí đầu tiên.
 * 2. Hỗ trợ nhiều Sheet với tên sheet tự động làm sạch (tối đa 31 ký tự, không chứa ký tự cấm).
 * 3. Văn bản ghi dạng `inlineStr` (t="inlineStr"), triệt tiêu nguy cơ bị phần mềm bảng tính hiểu nhầm là công thức.
 * 4. Tự động chèn dấu nháy đơn `'` ở đầu chuỗi bắt đầu bằng `=`, `+`, `-`, `@` để chống Spreadsheet Formula Injection.
 * 5. Thoát (Escape) 5 ký tự đặc biệt XML (&, <, >, ", ') và loại bỏ ký tự điều khiển không hợp lệ XML 1.0.
 * 6. Ô số lưu dạng số thật (<v>), định dạng `#,#0` cho tiền VND và `#,##0.00` cho ngoại tệ.
 * 7. Tự động tính toán độ rộng cột (Column Width) theo độ dài nội dung (giới hạn trong khoảng 8–60).
 * 8. Tự động cố định (freeze) hàng đầu tiên để cuộn dữ liệu dễ dàng.
 */
class XlsxWriter(private val outputStream: OutputStream) : Closeable, AutoCloseable {

    private val sheets = mutableListOf<SheetBuilder>()
    private val existingSheetNames = mutableSetOf<String>()
    private var isClosed = false

    /**
     * Thêm một sheet mới vào workbook.
     * Tên sheet sẽ được tự động chuẩn hóa để đảm bảo hợp lệ theo quy định của Excel:
     * - Tối đa 31 ký tự.
     * - Không chứa các ký tự cấm: `:` `\` `/` `?` `*` `[` `]`.
     * - Tên không trùng lặp giữa các sheet.
     */
    fun sheet(name: String, freezeHeader: Boolean = true, builder: SheetBuilder.() -> Unit): XlsxWriter {
        check(!isClosed) { "XlsxWriter đã được đóng, không thể thêm sheet." }
        val sanitizedName = sanitizeSheetName(name, existingSheetNames)
        existingSheetNames.add(sanitizedName.lowercase(Locale.ROOT))

        val sheetBuilder = SheetBuilder(sanitizedName).apply {
            this.freezeHeader = freezeHeader
            this.builder()
        }
        sheets.add(sheetBuilder)
        return this
    }

    /**
     * Ghi toàn bộ nội dung workbook vào luồng xuất theo cấu trúc chuẩn OpenXML ZIP.
     */
    fun finish() {
        if (isClosed) return

        // Nếu không có sheet nào, tạo một sheet mặc định rỗng
        if (sheets.isEmpty()) {
            sheet("Sheet1") {}
        }

        val zos = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml (BẮT BUỘC ĐẶT ĐẦU TIÊN THEO YÊU CẦU)
        writeContentTypes(zos, sheets.size)

        // 2. _rels/.rels
        writeGlobalRels(zos)

        // 3. xl/workbook.xml
        writeWorkbook(zos, sheets)

        // 4. xl/_rels/workbook.xml.rels
        writeWorkbookRels(zos, sheets.size)

        // 5. xl/styles.xml
        writeStyles(zos)

        // 6. xl/worksheets/sheetN.xml cho từng sheet
        sheets.forEachIndexed { index, sheetBuilder ->
            writeWorksheet(zos, sheetBuilder, index + 1)
        }

        zos.finish()
        zos.flush()
    }

    override fun close() {
        if (!isClosed) {
            try {
                finish()
            } finally {
                isClosed = true
                outputStream.close()
            }
        }
    }

    // =========================================================================
    // CÁC HÀM XUẤT CÁC PHẦN XML CỦA OPENXML ARCHIVE
    // =========================================================================

    private fun writeContentTypes(zos: ZipOutputStream, sheetCount: Int) {
        val entry = ZipEntry("[Content_Types].xml")
        zos.putNextEntry(entry)

        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("\n")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            for (i in 1..sheetCount) {
                append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            append("""</Types>""")
        }

        zos.write(xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun writeGlobalRels(zos: ZipOutputStream) {
        val entry = ZipEntry("_rels/.rels")
        zos.putNextEntry(entry)

        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        zos.write(xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun writeWorkbook(zos: ZipOutputStream, sheetsList: List<SheetBuilder>) {
        val entry = ZipEntry("xl/workbook.xml")
        zos.putNextEntry(entry)

        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("\n")
            append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            append("""<sheets>""")
            sheetsList.forEachIndexed { index, sheet ->
                val sheetId = index + 1
                val rId = "rId$sheetId"
                val escapedName = escapeXml(sheet.name)
                append("""<sheet name="$escapedName" sheetId="$sheetId" r:id="$rId"/>""")
            }
            append("""</sheets>""")
            append("""</workbook>""")
        }

        zos.write(xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun writeWorkbookRels(zos: ZipOutputStream, sheetCount: Int) {
        val entry = ZipEntry("xl/_rels/workbook.xml.rels")
        zos.putNextEntry(entry)

        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("\n")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            for (i in 1..sheetCount) {
                append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
            }
            val stylesRid = "rId${sheetCount + 1}"
            append("""<Relationship Id="$stylesRid" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            append("""</Relationships>""")
        }

        zos.write(xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun writeStyles(zos: ZipOutputStream) {
        val entry = ZipEntry("xl/styles.xml")
        zos.putNextEntry(entry)

        // Stylesheet định nghĩa:
        // numFmt 164: #,##0
        // numFmt 165: #,##0.00
        // Font 0: regular 11pt Calibri
        // Font 1: bold 11pt Calibri
        // Fill 0: none (chuẩn OOXML bắt buộc)
        // Fill 1: gray125 (chuẩn OOXML bắt buộc)
        // Fill 2: solid xám nhạt FFE5E7EB (Header)
        // Border 0: none
        // Border 1: thin viền xám FFD1D5DB (Header)
        // CellXfs:
        // 0 -> NORMAL
        // 1 -> HEADER (font 1, fill 2, border 1, center/wrap)
        // 2 -> CURRENCY_INT (numFmt 164)
        // 3 -> CURRENCY_DECIMAL (numFmt 165)
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="2">
    <numFmt numFmtId="164" formatCode="#,##0"/>
    <numFmt numFmtId="165" formatCode="#,##0.00"/>
  </numFmts>
  <fonts count="2">
    <font>
      <sz val="11"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFE5E7EB"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
  </fills>
  <borders count="2">
    <border>
      <left/><right/><top/><bottom/><diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="FFD1D5DB"/></left>
      <right style="thin"><color rgb="FFD1D5DB"/></right>
      <top style="thin"><color rgb="FFD1D5DB"/></top>
      <bottom style="thin"><color rgb="FFD1D5DB"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>"""

        zos.write(xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun writeWorksheet(zos: ZipOutputStream, sheet: SheetBuilder, sheetNumber: Int) {
        val entry = ZipEntry("xl/worksheets/sheet$sheetNumber.xml")
        zos.putNextEntry(entry)

        // Tính độ rộng cột tự động dựa trên độ dài nội dung lớn nhất của cột
        val columnLengths = mutableMapOf<Int, Int>()
        sheet.rows.forEach { rowCells ->
            rowCells.forEachIndexed { colIdx, cellValue ->
                val colNumber = colIdx + 1
                val length = when (cellValue) {
                    is CellValue.Text -> cellValue.text.length
                    is CellValue.IntNumber -> formatIntLength(cellValue.value)
                    is CellValue.DecNumber -> formatDecLength(cellValue.value)
                }
                val currentMax = columnLengths[colNumber] ?: 0
                if (length > currentMax) {
                    columnLengths[colNumber] = length
                }
            }
        }

        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("\n")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

            // Cố định (freeze) hàng đầu tiên nếu có yêu cầu và bảng có dữ liệu
            if (sheet.freezeHeader && sheet.rows.isNotEmpty()) {
                append("""<sheetViews>""")
                append("""<sheetView tabSelected="${if (sheetNumber == 1) "1" else "0"}" workbookViewId="0">""")
                append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
                append("""</sheetView>""")
                append("""</sheetViews>""")
            }

            // Định nghĩa độ rộng các cột tự động (giới hạn từ 8.0 đến 60.0)
            if (columnLengths.isNotEmpty()) {
                append("""<cols>""")
                val sortedCols = columnLengths.keys.sorted()
                sortedCols.forEach { colNum ->
                    val maxLen = columnLengths[colNum] ?: 5
                    // Thêm khoảng đệm padding 4 đơn vị ký tự để không bị che khuất
                    val computedWidth = (maxLen + 4.0).coerceIn(8.0, 60.0)
                    val widthFormatted = String.format(Locale.US, "%.1f", computedWidth)
                    append("""<col min="$colNum" max="$colNum" width="$widthFormatted" customWidth="1"/>""")
                }
                append("""</cols>""")
            }

            // Dữ liệu bảng (sheetData)
            append("""<sheetData>""")
            sheet.rows.forEachIndexed { rowIndex, rowCells ->
                val rowNum = rowIndex + 1
                append("""<row r="$rowNum">""")
                rowCells.forEachIndexed { colIndex, cellValue ->
                    val colNum = colIndex + 1
                    val cellRef = "${getColumnName(colNum)}$rowNum"
                    val styleAttr = if (cellValue.style.index != 0) " s=\"${cellValue.style.index}\"" else ""

                    when (cellValue) {
                        is CellValue.Text -> {
                            val sanitizedXml = sanitizeTextForXml(cellValue.text)
                            append("""<c r="$cellRef"$styleAttr t="inlineStr"><is><t xml:space="preserve">$sanitizedXml</t></is></c>""")
                        }
                        is CellValue.IntNumber -> {
                            append("""<c r="$cellRef"$styleAttr><v>${cellValue.value}</v></c>""")
                        }
                        is CellValue.DecNumber -> {
                            if (!cellValue.value.isFinite()) {
                                // Nếu giá trị không hữu hạn (NaN/Infinity) thì ghi ô trống
                                append("""<c r="$cellRef"$styleAttr/>""")
                            } else {
                                val formattedVal = String.format(Locale.US, "%.4f", cellValue.value).trimEnd('0').trimEnd('.')
                                append("""<c r="$cellRef"$styleAttr><v>$formattedVal</v></c>""")
                            }
                        }
                    }
                }
                append("""</row>""")
            }
            append("""</sheetData>""")
            append("""</worksheet>""")
        }

        zos.write(xml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun formatIntLength(value: Long): Int {
        // Chiều dài chuỗi khi hiển thị với dấu phẩy phân tách hàng nghìn (#,##0)
        val s = value.toString()
        val commas = (s.length - 1) / 3
        return s.length + commas
    }

    private fun formatDecLength(value: Double): Int {
        if (!value.isFinite()) return 0
        val s = String.format(Locale.US, "%,.2f", value)
        return s.length
    }

    companion object {
        /**
         * Chuyển đổi số thứ tự cột 1-based (1, 2, 27, ...) thành ký tự cột Excel (A, B, AA, ...).
         */
        fun getColumnName(colIndex: Int): String {
            require(colIndex > 0) { "Column index must be positive (1-based)" }
            var num = colIndex
            val sb = StringBuilder()
            while (num > 0) {
                val rem = (num - 1) % 26
                sb.append(('A'.code + rem).toChar())
                num = (num - 1) / 26
            }
            return sb.reverse().toString()
        }

        /**
         * Chuẩn hóa tên Sheet:
         * - Tối đa 31 ký tự.
         * - Thay thế các ký tự cấm: `:` `\` `/` `?` `*` `[` `]` thành `_`.
         * - Đảm bảo tên duy nhất không bị trùng lặp.
         */
        fun sanitizeSheetName(name: String, existingNames: Set<String> = emptySet()): String {
            val invalidChars = charArrayOf(':', '\\', '/', '?', '*', '[', ']')
            var sanitized = name
            for (c in invalidChars) {
                sanitized = sanitized.replace(c, '_')
            }
            sanitized = sanitized.trim().ifEmpty { "Sheet" }
            if (sanitized.length > 31) {
                sanitized = sanitized.take(31)
            }

            var unique = sanitized
            var counter = 2
            while (existingNames.contains(unique.lowercase(Locale.ROOT))) {
                val suffix = " ($counter)"
                val maxBaseLen = (31 - suffix.length).coerceAtLeast(1)
                val base = if (sanitized.length > maxBaseLen) sanitized.take(maxBaseLen) else sanitized
                unique = "$base$suffix"
                counter++
            }
            return unique
        }

        /**
         * Làm sạch chuỗi văn bản cho ô:
         * 1. Phòng chống Formula Injection: Nếu chuỗi bắt đầu bằng `=`, `+`, `-`, `@`, `\t` (tab), `\r` (CR), tự động thêm dấu nháy đơn `'` ở đầu.
         * 2. Thoát 5 ký tự đặc biệt của XML: `&`, `<`, `>`, `"`, `'`.
         * 3. Loại bỏ các ký tự điều khiển không hợp lệ trong đặc tả XML 1.0 (ví dụ \u0000..\u0008, \u000B, \u000C, \u000E..\u001F).
         */
        fun sanitizeTextForXml(input: String): String {
            if (input.isEmpty()) return ""

            // 1. Chống Formula Injection (kể cả bắt đầu bằng tab hoặc CR)
            val formulaSafe = if (input.startsWith("=") || input.startsWith("+") ||
                input.startsWith("-") || input.startsWith("@") ||
                input.startsWith("\t") || input.startsWith("\r")
            ) {
                "'$input"
            } else {
                input
            }

            // 2. Escape XML và loại bỏ ký tự điều khiển
            return escapeXml(formulaSafe)
        }

        /**
         * Thoát ký tự đặc biệt XML chuẩn 1.0 và lọc bỏ control characters không hợp lệ.
         */
        fun escapeXml(text: String): String {
            if (text.isEmpty()) return ""
            val sb = StringBuilder(text.length + 16)
            for (i in text.indices) {
                val c = text[i]
                // Kiểm tra ký tự hợp lệ theo chuẩn XML 1.0:
                // #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD]
                if (c == '\t' || c == '\n' || c == '\r' || (c in ' '..'\uD7FF') || (c in '\uE000'..'\uFFFD')) {
                    when (c) {
                        '&' -> sb.append("&amp;")
                        '<' -> sb.append("&lt;")
                        '>' -> sb.append("&gt;")
                        '"' -> sb.append("&quot;")
                        '\'' -> sb.append("&apos;")
                        else -> sb.append(c)
                    }
                } else if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                    // Giữ lại các cặp surrogate hợp lệ (Emoji, chữ tượng hình mở rộng...)
                    sb.append(c)
                }
                // Các control character không hợp lệ khác bị loại bỏ an toàn
            }
            return sb.toString()
        }
    }
}
