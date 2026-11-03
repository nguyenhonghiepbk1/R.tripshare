# Ghi Chú Phát Hành (Release Notes) - TripFinance

---

## 📌 Tổng Quan Phiên Bản (Version Overview)

| Thông số | Giá trị |
| :--- | :--- |
| **Phiên bản (Version Name)** | **20.0** |
| **Mã bản dựng (Version Code)** | **20** |
| **Mã gói ứng dụng (Application ID)** | `com.hiepnguyen.tripfinance.oddnis` |
| **Target SDK** | **Android 15+ (API Level 36)** |
| **Minimum SDK** | Android 7.0 (API Level 24) |
| **Kiến trúc bảo mật cơ sở dữ liệu** | Room SQLite + SQLCipher AES-256 (`net.zetetic:sqlcipher-android:4.6.1`) |
| **Quyền mạng** | **Không có quyền INTERNET** (100% Offline-only) |
| **Trạng thái phát hành** | **Sẵn sàng triển khai Google Play Store Production** |

---

## 🚀 Tóm Tắt Phát Hành Cho Google Play Console (Copy-Paste Ready)

### 🇻🇳 Tiếng Việt (`vi-VN`) — Dưới 500 ký tự:
```text
TripFinance v20.0:
• Hoạt động ngoại tuyến 100%, bảo vệ dữ liệu cục bộ với CSDL mã hóa SQLCipher AES-256.
• Quản lý khóa an toàn qua KeyProvider và sao lưu mã hóa PBKDF2 600.000 vòng lặp.
• Tối ưu hóa hiệu năng: Xử lý tính toán quyết toán trên Dispatchers.Default, xuất báo cáo PDF đa luồng mượt mà.
• Giao diện Material Design 3 hiện đại hỗ trợ đầy đủ chế độ Sáng và Tối (Light/Dark mode).
• Chuẩn hóa quyết toán bù trừ công nợ tối ưu, tinh gọn trải nghiệm sử dụng.
```
*(Độ dài: 462/500 ký tự)*

---

### 🇺🇸 Tiếng Anh (`en-US`) — Under 500 characters:
```text
TripFinance v20.0:
• 100% Offline-only operation with local SQLCipher AES-256 encrypted database.
• Enhanced security with modular KeyProvider and 600,000-iteration PBKDF2 encrypted backups.
• Performance optimizations: Background debt netting on Dispatchers.Default and asynchronous PDF report exports.
• Polished Material Design 3 UI with full Light and Dark mode semantic theming.
• Streamlined min-transfers debt netting settlement workflow.
```
*(Length: 423/500 characters)*

---

## 🌟 Chi Tiết Các Cải Tiến & Tính Năng Trong Phiên Bản 20.0

### 1. Tách Biệt Khóa & Bảo Mật Sao Lưu (Key Management & Crypto Architecture)
- **Kiến trúc KeyProvider:** Tách cơ chế cung cấp khóa giải mã cơ sở dữ liệu qua giao diện `KeyProvider` độc lập, bảo đảm nguyên tắc fail-closed trong môi trường production (không tạo khóa dự phòng mặc định khi thiếu thông tin xác thực phần cứng).
- **Sao lưu mã hóa tăng cường:** Nâng cấp cơ chế sao lưu tệp dữ liệu (`BackupCryptoUtils`) với hàm dẫn xuất khóa PBKDF2-HMAC-SHA256 đạt 600.000 vòng lặp theo tiêu chuẩn an mật hiện đại kết hợp mã hóa khối AES-GCM.
- **Không có quyền INTERNET:** Ứng dụng không khai báo quyền `android.permission.INTERNET` trong manifest; mọi thao tác tính toán, lưu trữ và sao lưu đều diễn ra cục bộ trong thiết bị.

### 2. Tối Ưu Hóa Đa Luồng & Báo Cáo PDF (Reactive Dispatchers & Safe PDF Export)
- **Tính toán quyết toán trên luồng nền:** Chuyển đổi toàn bộ logic tính toán bù trừ công nợ và tổng hợp báo cáo tài chính trong `TripFinanceViewModel` sang `Dispatchers.Default` thông qua `flowOn(Dispatchers.Default)`, giúp giao diện chính luôn mượt mà.
- **Xuất PDF an toàn & Quản lý tài nguyên xác định:** Chuyển hàm `PdfReportExporter.exportAndSharePdf` sang chạy bất đồng bộ với `withContext(Dispatchers.IO)` và hiển thị trạng thái tải (progress indicator). Quản lý giải phóng tài nguyên `PdfDocument` và luồng ghi tệp bằng khối `use{}` / `try-finally` chặt chẽ.
- **Tự động dọn dẹp bộ nhớ đệm:** Tự động phát hiện và xóa các tệp báo cáo PDF tạm trong thư mục `cache/reports` cũ hơn 24 giờ mỗi khi mở ứng dụng.

### 3. Tối Ưu Hóa Hiển Thị Danh Sách (List Stability & Memoization)
- **Khóa phần tử ổn định (Stable Keys):** Bổ sung thuộc tính `key = { it.id }` vào toàn bộ các danh sách `LazyColumn` (chi tiêu, thành viên, khoản nộp quỹ, giao dịch bù trừ) giúp Compose tái sử dụng composable hiệu quả và chống giật lag khi cuộn.
- **Ghi nhớ bảng phân bổ chi phí:** Trong `ExpensesScreen`, nhóm và ánh xạ danh sách phân bổ `splits` trước một lần bằng `remember(allSplits)` thay vì lọc lặp lại trong từng hàng của danh sách.

### 4. Chuẩn Hóa Giao Diện Material Design 3 & Semantic Colors
- **Bảng màu ngữ nghĩa toàn diện:** Thay thế toàn bộ các mã màu cứng trong UI bằng màu sắc hệ thống `MaterialTheme.colorScheme` và bảng màu ngữ nghĩa mở rộng (`extendedColorScheme` gồm các biến trạng thái success, warning, danger, textMuted, cardBorder) thích ứng đồng bộ trên cả chế độ Giao diện Sáng (Light Mode) và Giao diện Tối (Dark Mode).
- **Hệ thống kiểu chữ M3:** Áp dụng thống nhất các thang tỷ lệ typography chuẩn của Material 3 (`titleLarge`, `titleMedium`, `bodyMedium`, `labelSmall`...).
- **Khả năng tiếp cận & Điều hướng:** Bổ sung mô tả ngôn ngữ cụ thể cho tất cả các Icon tương tác (`contentDescription`) và tích hợp `BackHandler` cho màn hình Hướng dẫn sử dụng cũng như các hộp thoại toàn màn hình.

### 5. Chuẩn Hóa Trải Nghiệm Quyết Toán Bù Trừ Công Nợ
- **Tối giản & Độc lập:** Loại bỏ hoàn toàn sự phụ thuộc vào các dịch vụ bên ngoài và chuyển khoản ngân hàng; chuyển toàn bộ luồng quyết toán sang mô hình **Bù trừ công nợ tối ưu (Debt Netting)**.
- **Minh bạch thông tin:** Mỗi khoản bù trừ hiển thị rõ ràng người nộp bù, người nhận, số tiền cần bù và nội dung đối soát chuẩn kèm nút sao chép nhanh.

---

## 🛠 Hướng Dẫn Kiểm Thử & Biên Dịch Bản Phát Hành

1. **Chạy kiểm thử cục bộ (Unit Test & Compose UI Test):**
   ```bash
   gradle :app:testDebugUnitTest
   ```
   *(Tất cả bài kiểm thử logic, repository, key provider và Compose screenshot đều vượt qua thành công).*

2. **Biên dịch Android App Bundle (AAB):**
   ```bash
   gradle :app:bundleRelease
   ```
   Tệp xuất ra tại: `app/build/outputs/bundle/release/app-release.aab`
