# Chính Sách Bảo Mật (Security Policy) - TripFinance

Dự án **TripFinance** áp dụng các biện pháp bảo vệ dữ liệu kỹ thuật minh bạch, thực tế và chặt chẽ nhằm bảo vệ thông tin quản lý tài chính của người dùng trên nền tảng Android.

- **Mã gói ứng dụng (Application ID):** `com.hiepnguyen.tripfinance.oddnis`
- **Phiên bản hiện tại:** `20.0`
- **Quyền mạng:** Không khai báo quyền `android.permission.INTERNET`.

---

## 1. Phạm Vi Dữ Liệu & Ranh Giới Nghiệp Vụ Tài Chính

- **Phạm vi tính năng:** TripFinance là công cụ ghi chép chi tiêu, tính toán phân bổ chi phí, theo dõi quỹ chung đoàn và đưa ra **gợi ý chuyển khoản bù trừ công nợ nội bộ**.
- **Giới hạn an toàn:**
  - Ứng dụng **KHÔNG** lưu trữ, không yêu cầu nhập và không xuất bất kỳ số tài khoản ngân hàng, thông tin thẻ hay thông tin ngân hàng nào của các thành viên.
  - Ứng dụng **KHÔNG** tạo mã QR chuyển khoản ngân hàng hay ví điện tử.
  - Ứng dụng **KHÔNG** thực hiện, không kết nối và không xử lý bất kỳ giao dịch tiền tệ/thanh toán nào. Mọi giao dịch chuyển khoản do các thành viên tự thực hiện ngoài ứng dụng qua các ứng dụng ngân hàng riêng biệt.

---

## 2. Lưu Trữ Dữ Liệu An Toàn & Mã Hóa Cục Bộ (SQLCipher AES-256)

- **Mã Hóa CSDL Cấp Trang (Database Encryption at Rest):**
  - Toàn bộ cơ sở dữ liệu Room SQLite (`trip_finance_database`), bao gồm các bản ghi chi tiêu, phân bổ, quỹ đoàn, nhật ký kiểm toán và gợi ý chuyển khoản, được mã hóa cấp trang bằng thư viện **SQLCipher** tiêu chuẩn AES-256 (`net.zetetic:sqlcipher-android:4.6.1`).
  - **Tương thích trang nhớ 16 KB (Android 15+ / TargetSdk = 36):** Các thư viện liên kết động native (`.so`) của SQLCipher được biên dịch với căn chỉnh ELF 16 KB (16,384 bytes), đảm bảo tương thích ổn định khi vận hành trên Android 15/16 và các thiết bị phần cứng hỗ trợ kích thước trang bộ nhớ 16 KB.
- **Quản Lý Khóa Phần Cứng (Android KeyStore):**
  - Khóa mã hóa 256-bit được sinh ngẫu nhiên bảo mật bằng `SecureRandom`, được bảo vệ qua thuật toán `AES/GCM/NoPadding` và lưu trữ trong vùng bảo mật phần cứng Android KeyStore.
- **Kiến Trúc Thất Bại An Toàn (Fail-Closed Architecture):**
  - Không cho phép cơ chế fallback mở CSDL dạng văn bản thô (Plaintext). Nếu SQLCipher hoặc KeyStore không thể nạp hoặc giải mã khóa, ứng dụng từ chối mở CSDL và thông báo lỗi rõ ràng, ngăn ngừa rò rỉ dữ liệu chưa mã hóa ra bộ nhớ flash.

---

## 3. Vận Hành Ngoại Tuyến 100% (Zero-Internet)

- **Không Yêu Cầu Quyền INTERNET:**
  - Ứng dụng không khai báo quyền `android.permission.INTERNET` trong `AndroidManifest.xml`. Về mặt kỹ thuật, ứng dụng không có khả năng gửi dữ liệu ra môi trường mạng hoặc máy chủ từ xa.
- **Phân Tích Chi Tiêu Cục Bộ (`LocalSpendingAdvisor`):**
  - Toàn bộ quá trình phân tích danh mục chi tiêu, đánh giá tỷ trọng và tính toán số dư quỹ đoàn được thực hiện 100% trên thiết bị qua thuật toán nội bộ. Dữ liệu chuyến đi và thành viên không bao giờ rời khỏi thiết bị.

---

## 4. Cơ Chế Chống Chèn Công Thức Khi Xuất Tệp Excel (.xlsx)

Khi xuất báo cáo tài chính sang định dạng Microsoft Excel (`.xlsx`), ứng dụng triển khai cơ chế chống tấn công tiêm công thức (Formula Injection / Spreadsheet Injection) trong bộ tạo tệp OpenXML độc lập (`XlsxWriter`):

### 4.1. Nguy cơ an ninh (Threat Model)
Tấn công Formula Injection xảy ra khi dữ liệu người dùng nhập (như tên chuyến đi, tiêu đề khoản chi, tên thành viên, ghi chú) bắt đầu bằng các ký tự điều khiển công thức (`=`, `+`, `-`, `@`). Khi người dùng mở tệp `.xlsx` trên các ứng dụng bảng tính (Microsoft Excel, LibreOffice Calc, Google Sheets), trình đọc bảng tính có thể diễn giải ô văn bản đó thành một công thức thực thi, kích hoạt các hàm truy xuất dữ liệu ngoài hoặc lệnh DDE (Dynamic Data Exchange).

### 4.2. Cơ chế phòng vệ trong `XlsxWriter`:
1. **Tự động tiền tố hóa ký tự thoát (Escaping Prefix):**
   Trong hàm `sanitizeTextForXml()`, trước khi đưa chuỗi văn bản vào ô tính (`CellValue.Text`), ứng dụng kiểm tra ký tự bắt đầu của chuỗi:
   ```kotlin
   if (input.startsWith("=") || input.startsWith("+") ||
       input.startsWith("-") || input.startsWith("@")
   ) {
       "'$input"
   } else {
       input
   }
   ```
   Việc chèn thêm dấu nháy đơn `'` ở đầu chuỗi ép buộc phần mềm bảng tính hiểu rằng nội dung ô là một **chuỗi ký tự thuần túy (String Literal)**, vô hiệu hóa hoàn toàn việc thực thi công thức, trong khi dấu nháy đơn này không hiển thị gây khó chịu cho người xem bảng tính.

2. **Phân tách kiểu dữ liệu số nguyên thủy (Native Numeric Types):**
   Các ô chứa số tiền (tiền VND, số tiền thập phân ngoại tệ, tỷ giá, số lượng thành viên, số thứ tự) được lưu trữ dưới dạng phần tử số học `<v>...</v>` với thuộc tính kiểu số và `numFmtId` riêng biệt (`#,##0` hoặc `#,##0.00`), hoàn toàn không ghép chuỗi dạng công thức.

3. **Mã hóa thực thể XML 1.0 (XML Entity Encoding):**
   Toàn bộ văn bản ô được mã hóa qua hàm `escapeXml()` để chuyển đổi 5 ký tự đặc biệt theo chuẩn XML:
   - `&` ➔ `&amp;`
   - `<` ➔ `&lt;`
   - `>` ➔ `&gt;`
   - `"` ➔ `&quot;`
   - `'` ➔ `&apos;`
   Đồng thời, hàm chủ động loại bỏ các ký tự điều khiển không hợp lệ trong đặc tả XML 1.0 (ví dụ `\u0000..\u0008`, `\u000B`, `\u000C`, `\u000E..\u001F`) nhằm ngăn ngừa lỗi hỏng cấu trúc tệp (XML Parsing Failure).

4. **Chuẩn hóa tên Sheet an toàn (`sanitizeSheetName`):**
   Tên các trang tính được giới hạn tối đa 31 ký tự, thay thế các ký tự cấm của định dạng Excel (`:`, `\`, `/`, `?`, `*`, `[`, `]`) thành dấu gạch dưới `_`, và tự động đánh số phân biệt nếu có tên trùng lặp.

---

## 5. Toàn Vẹn Dữ Liệu & Ràng Buộc Khóa Sổ

- **Kiểm Tra Ràng Buộc Đa Đoàn (Cross-Trip Validation):**
  - Hệ thống kiểm tra nghiêm ngặt ở cả tầng Repository và tầng cơ sở dữ liệu (SQLite Triggers) để đảm bảo người chi, người tạo, người nộp quỹ và các thành viên cùng chịu chi trong một khoản chi phải thuộc cùng một chuyến đi (`tripId`).
- **Niêm Phong Sổ Cái (Settlement Freezing):**
  - Khi chuyến đi được khóa sổ (`isSettled = 1`), hệ thống 15 SQLite triggers và tầng Repository chặn việc sửa hoặc xóa các khoản chi, phân bổ, quỹ chung, tỷ giá hoặc thông tin chuyến đi.
  - Riêng với bảng gợi ý chuyển khoản (`settlement_transfers`), trigger `trg_prevent_settlement_transfer_update_when_settled` chỉ cho phép cập nhật trạng thái `isCompleted` ("Đã chuyển") và `updatedAt` để phục vụ theo dõi thanh toán, trong khi chặn tuyệt đối mọi hành vi sửa đổi `amount`, `fromMemberId`, `toMemberId`, `tripId` hoặc xóa bản ghi.
- **Thuật Toán Quyết Toán Phi Trạng Thái:**
  - Logic tính toán số dư và bù trừ nợ được đóng gói trong đối tượng bất biến `SettlementCalculationResult`, đảm bảo an toàn khi xử lý đồng thời đa luồng.
  - Thuật toán chia tiền phân bổ phần dư theo thứ tự ưu tiên nhất quán, đảm bảo tổng số dư thành viên cân bằng với quỹ còn lại mà không có sai lệch.

---

## 6. Chính Sách Sao Lưu & Ngăn Ngừa Rò Rỉ Dữ Liệu

- **Vô Hiệu Hóa Sao Lưu Tự Động Của Hệ Điều Hành (`android:allowBackup="false"`):**
  - Ngăn hệ điều hành Android tự động đưa bản sao lưu CSDL lên các dịch vụ đám mây chưa được kiểm soát.
- **Quy Tắc Trích Xuất Dữ Liệu (`data_extraction_rules.xml` & `backup_rules.xml`):**
  - Loại trừ toàn bộ thư mục CSDL (`<exclude domain="database" path="."/>`) khỏi các cơ chế sao lưu đám mây và truyền dữ liệu qua cáp ADB.
- **Sao Lưu Mã Hóa Tùy Chọn:**
  - Khi xuất tệp sao lưu JSON, người dùng có thể đặt mật khẩu mã hóa. Ứng dụng sử dụng thuật toán AES-GCM với hàm dẫn xuất khóa PBKDF2 (600.000 vòng lặp HMAC-SHA256 theo khuyến nghị bảo mật).

---

## 7. Phiên Bản Được Hỗ Trợ (Supported Versions)

| Phiên bản | Trạng thái hỗ trợ cập nhật bảo mật |
| :--- | :--- |
| **20.0 (Hiện tại)** | :white_check_mark: Được hỗ trợ đầy đủ |
| < 20.0 | :x: Không còn hỗ trợ |

---

## 8. Quy Trình Báo Cáo Lỗ Hổng Bảo Mật (Vulnerability Disclosure)

Nếu bạn phát hiện vấn đề bảo mật hoặc rủi ro tiềm ẩn trong ứng dụng:
1. **Không mở Issue công khai** trên diễn đàn hoặc kho lưu trữ công cộng.
2. **Gửi thông tin chi tiết** kèm theo bằng chứng chứng minh (PoC) qua email bảo mật của dự án.
3. Đội ngũ kỹ thuật cam kết:
   - Tiếp nhận và xác nhận thông tin trong vòng **48 giờ**.
   - Phân tích và phát hành bản vá khắc phục trong vòng **7 ngày làm việc**.

- **Email liên hệ bảo mật:** `nguyenhonghiepbk1@gmail.com`
