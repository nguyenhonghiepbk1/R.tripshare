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

## 🚀 Tóm Tắt Phát Hành Cho Google Play Console (Dưới 500 ký tự)

```text
TripFinance v20.0:
• Ghi nhận chi tiêu đoàn, chia tiền linh hoạt, đối soát công nợ và gợi ý chuyển khoản tối ưu.
• Thêm checkbox "Đã chuyển" cho từng gợi ý chuyển khoản (hoạt động cả khi đoàn đã khóa sổ).
• Xuất báo cáo Excel (.xlsx) đa sheet có chống chèn công thức và báo cáo PDF chuẩn A4.
• Sao lưu định dạng JSON với tùy chọn mã hóa mật khẩu AES-GCM kết hợp PBKDF2 600.000 vòng lặp.
• Vận hành ngoại tuyến 100%, không quyền INTERNET, mã hóa CSDL cục bộ SQLCipher AES-256.
```
*(Độ dài: 489/500 ký tự)*

---

## 🌟 Chi Tiết Các Cải Tiến & Tính Năng Trong Phiên Bản 20.0

### 1. Quản Lý Chi Tiêu Đoàn & Gợi Ý Chuyển Khoản
- **Ghi nhận & phân chia chi phí:** Hỗ trợ chia đều, chia theo người tham gia thực tế, chia theo số tiền cố định hoặc tỷ lệ phần trăm tùy chỉnh.
- **Quản lý Quỹ đoàn:** Theo dõi chi tiết các đợt nộp quỹ, chi tiêu trực tiếp từ quỹ hoặc cá nhân chi hộ.
- **Đối soát công nợ & Gợi ý chuyển khoản:** Tự động tính toán số dư ròng và đưa ra gợi ý chuyển khoản tối ưu giữa các thành viên.
- **Checkbox "Đã chuyển":** Cho phép đánh dấu trạng thái đã hoàn thành chuyển khoản cho từng dòng gợi ý (lưu trong cơ sở dữ liệu Room v6, không làm thay đổi số liệu tài chính đối soát, cho phép đánh dấu ngay cả khi đoàn đã khóa sổ).
- **Phạm vi tài chính minh bạch:** Ứng dụng **KHÔNG** lưu/nhập/xuất số tài khoản ngân hàng, **KHÔNG** tạo mã QR chuyển khoản, và **KHÔNG** thực hiện hay xử lý bất kỳ giao dịch tiền tệ nào.

### 2. Xuất Báo Cáo Tài Chính & Sao Lưu Dữ Liệu
- **Báo cáo Excel (.xlsx) chuẩn OpenXML:** Xuất 5 sheet chi tiết (Tổng quan, Chi tiêu, Theo thành viên, Quỹ chung, Gợi ý chuyển khoản). Tích hợp cơ chế tự động ngăn chặn tiêm công thức (Formula Injection Protection) bằng cách thêm dấu nháy đơn `'` cho các ô bắt đầu bằng `=`, `+`, `-`, `@`.
- **Báo cáo PDF chuẩn A4:** Định dạng trang trọng, có phân trang, bảng biểu thu chi và bảng đối soát công nợ rõ ràng.
- **Sao lưu & Khôi phục JSON:** Hỗ trợ tùy chọn mã hóa tệp sao lưu bằng thuật toán AES-GCM kết hợp hàm dẫn xuất khóa PBKDF2 (600.000 vòng lặp) theo khuyến nghị bảo mật. Tương thích ngược hoàn toàn với các bản sao lưu từ các phiên bản trước.

### 3. Bảo Vệ Dữ Liệu Cục Bộ & Cơ Sở Dữ Liệu
- **Không có quyền INTERNET:** Hoàn toàn không khai báo quyền mạng trong `AndroidManifest.xml`, mọi dữ liệu được lưu trữ cục bộ trong sandbox ứng dụng.
- **Mã hóa CSDL SQLCipher AES-256:** Bảo vệ cơ sở dữ liệu SQLite cục bộ với khóa ngẫu nhiên lưu trữ an toàn trong Android KeyStore.
- **Toàn vẹn dữ liệu qua SQLite Triggers:** Hệ thống triggers kiểm tra ràng buộc thành viên đa đoàn và niêm phong dữ liệu tài chính khi đoàn đã khóa sổ (`isSettled = 1`).

### 4. Giao Diện & Trải Nghiệm Người Dùng
- **Material Design 3:** Thiết kế giao diện hiện đại, trực quan, hỗ trợ đầy đủ chế độ Sáng và Tối (Light/Dark mode).
- **Điều hướng & Khả năng tiếp cận:** Tích hợp `BackHandler` cho màn hình Hướng dẫn sử dụng và các hộp thoại toàn màn hình để nút quay lại đóng đúng màn hình thay vì thoát ứng dụng. Bổ sung mô tả ngắn cho các biểu tượng tương tác (`contentDescription`).

---

## 🛠 Hướng Dẫn Biên Dịch & Kiểm Thử

1. **Chạy kiểm thử đơn vị cục bộ (Unit Tests & Robolectric Tests):**
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

2. **Biên dịch bản Debug (APK):**
   ```bash
   ./gradlew :app:assembleDebug
   ```

3. **Biên dịch bản Release (APK):**
   ```bash
   ./gradlew :app:assembleRelease
   ```

4. **Biên dịch Android App Bundle (AAB):**
   ```bash
   ./gradlew :app:bundleRelease
   ```
   Tệp xuất ra tại: `app/build/outputs/bundle/release/app-release.aab`
