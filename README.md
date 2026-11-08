# TripFinance (R.Trip share)

Ứng dụng quản lý tài chính nhóm dành cho đoàn công tác, du lịch và sự kiện gia đình. Thiết kế theo kiến trúc **Local-First**, bảo vệ dữ liệu cục bộ với mã hóa **SQLCipher (AES-256)** và vận hành **100% ngoại tuyến (Zero-Internet)**.

- **Mã gói ứng dụng (Application ID):** `com.hiepnguyen.tripfinance.oddnis`
- **Phiên bản (Version):** `20.0` (VersionCode: `20`)
- **Quyền mạng:** Không yêu cầu quyền `android.permission.INTERNET`.

---

## 🌟 Chức Năng Cốt Lõi

1. **Ghi Nhận Thu - Chi & Quản Lý Quỹ Đoàn:**
   - Ghi nhận chi tiết từng khoản chi tiêu: thời gian, danh mục, người thanh toán, số tiền và ghi chú.
   - Hỗ trợ đa tiền tệ (VND, USD, EUR, JPY, THB, SGD,...) với tỷ giá quy đổi tham chiếu.
   - Quản lý quỹ chung (Group Fund): theo dõi các đợt nộp quỹ, chi tiêu trực tiếp từ quỹ hoặc thành viên chi hộ.

2. **Phân Bổ Chi Phí Linh Hoạt:**
   - Chia đều cho tất cả thành viên hoặc chia theo nhóm thành viên thực tế tham gia khoản chi.
   - Chia theo số tiền cố định hoặc theo tỷ lệ phần trăm tùy chỉnh với thuật toán phân bổ phần dư tự động.

3. **Đối Soát Công Nợ & Gợi Ý Chuyển Khoản:**
   - Tự động tính toán số dư ròng của từng cá nhân: tổng tiền đã chi hộ + tiền đã nộp quỹ - chi phí phải chịu.
   - Thuật toán bù trừ công nợ tối ưu (Min-transfers Debt Netting) đưa ra **gợi ý chuyển khoản** giữa các thành viên với số lượt chuyển ít nhất.
   - Hỗ trợ checkbox **"Đã chuyển"** trên từng dòng gợi ý để người dùng đánh dấu tiến độ thanh toán thực tế (hoạt động bình thường cả khi đoàn đã khóa sổ).
   - **Lưu ý quan trọng về phạm vi:**
     - Ứng dụng **KHÔNG** lưu, không nhập và không xuất số tài khoản ngân hàng của thành viên.
     - Ứng dụng **KHÔNG** tạo mã QR chuyển khoản.
     - Ứng dụng **KHÔNG** thực hiện hay xử lý bất kỳ giao dịch tiền tệ/thanh toán nào. Mọi giao dịch chuyển khoản do các thành viên tự thực hiện ngoài ứng dụng qua các kênh ngân hàng cá nhân.

4. **Xuất Báo Cáo Tài Chính & Sao Lưu Dữ Liệu:**
   - Xuất báo cáo bảng tính Microsoft Excel (`.xlsx`) đa sheet chuẩn OpenXML (Tổng quan, Chi tiêu, Theo thành viên, Quỹ chung, Gợi ý chuyển khoản) tích hợp cơ chế phòng chống tiêm công thức (Formula Injection Protection).
   - Xuất báo cáo tài liệu PDF khổ A4 trình bày trang trọng, có phân trang và bảng biểu thu chi chi tiết.
   - Xuất tệp văn bản tóm tắt nhanh để sao chép qua ứng dụng nhắn tin.
   - Sao lưu và khôi phục dữ liệu định dạng JSON, hỗ trợ tùy chọn mã hóa mật khẩu bằng thuật toán AES-GCM kết hợp hàm dẫn xuất khóa PBKDF2 (600.000 vòng lặp) theo khuyến nghị bảo mật.

5. **Phân Tích Chi Tiêu Cục Bộ (`LocalSpendingAdvisor`):**
   - Phân tích cơ cấu danh mục chi tiêu và trạng thái quỹ đoàn bằng thuật toán nội bộ chạy 100% trên thiết bị, không cần mạng internet.

---

## 🏗️ Kiến Trúc Công Nghệ

| Thành phần | Công nghệ sử dụng |
| :--- | :--- |
| **Hệ điều hành mục tiêu** | Android 7.0 (API 24) ➔ Android 15+ (API 36) |
| **Giao diện (UI)** | Jetpack Compose + Material Design 3 (M3) |
| **Ngôn ngữ** | Kotlin 2.0+ (Coroutines, StateFlow) |
| **Cơ sở dữ liệu** | Room SQLite + SQLCipher AES-256 (`net.zetetic:sqlcipher-android:4.6.1`) |
| **Bảo mật khóa** | Android KeyStore (`AES/GCM/NoPadding`) |
| **Kiến trúc** | MVVM + Clean Architecture |
| **Mạng** | **Không sử dụng mạng (Zero-Internet)** |
| **Ngôn ngữ giao diện** | Tiếng Việt là ngôn ngữ chính (hỗ trợ chuyển đổi giao diện Tiếng Anh/Tiếng Việt trên các màn hình chức năng và xuất báo cáo) |

---

## 🚀 Hướng Dẫn Biên Dịch (Build & Run)

Dự án sử dụng Gradle Wrapper:

### 1. Biên dịch bản Debug (APK)
```bash
./gradlew :app:assembleDebug
```
Tệp APK debug được tạo tại:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Chạy toàn bộ Unit Tests & Robolectric Tests
```bash
./gradlew :app:testDebugUnitTest
```

### 3. Biên dịch bản Release (APK)
```bash
./gradlew :app:assembleRelease
```
Tệp APK release được tạo tại:
`app/build/outputs/apk/release/app-release.apk`

### 4. Biên dịch Android App Bundle (AAB) cho Google Play Store
```bash
./gradlew :app:bundleRelease
```
Tệp AAB phát hành được tạo tại:
`app/build/outputs/bundle/release/app-release.aab`

---

## 📄 Bản Quyền & Giấy Phép
Phát triển bởi Nguyễn Hồng Hiệp. Bản quyền thuộc về tác giả.
