# TripFinance (R.Trip share)

Ứng dụng quản lý tài chính chuyên nghiệp dành cho đoàn công tác, du lịch nhóm và sự kiện gia đình. Thiết kế theo kiến trúc **Local-First**, bảo mật dữ liệu tuyệt đối với mã hóa **SQLCipher AES-256** và vận hành **100% ngoại tuyến (Zero-Internet)**.

---

## 🌟 Điểm Nổi Bật (Key Highlights)

- 🔒 **Bảo mật & Quyền riêng tư tuyệt đối (Local-First & Zero-Internet):**
  - Không khai báo quyền `INTERNET` trong `AndroidManifest.xml`.
  - Toàn bộ cơ sở dữ liệu Room SQLite được mã hóa cấp trang bằng **SQLCipher (AES-256)** với khóa bảo vệ phần cứng **Android KeyStore**.
  - Không nhúng bất kỳ API key nào (hoàn toàn không cần `GEMINI_API_KEY`), không chia sẻ dữ liệu với máy chủ hay bên thứ ba.
  - Tương thích hoàn toàn kích thước trang bộ nhớ 16 KB trên Android 15+ (`net.zetetic:sqlcipher-android:4.6.1`).

- 💡 **Gợi ý chi tiêu ngoại tuyến (`LocalSpendingAdvisor`):**
  - Thuật toán phân tích cơ cấu chi tiêu, số dư nợ và sức khỏe quỹ đoàn chạy 100% cục bộ trên thiết bị, đưa ra nhận định hữu ích ngay lập tức mà không cần mạng.

- ⚡ **Thuật toán quyết toán thông minh & phi trạng thái (Stateless Settlement Engine):**
  - Tối ưu hóa số lượng giao dịch chuyển khoản giữa các thành viên đoàn theo nguyên tắc cân bằng nợ trực tiếp hoặc hoàn tiền qua quỹ chung.
  - Dung sai đối soát bằng không (Zero Discrepancy Tolerance) – chính xác đến từng 1 đồng VND.
  - Tự động sinh mã chuyển khoản VietQR chuẩn Napas với ghi chú cú pháp rõ ràng.

- 📊 **Theo dõi & Phân bổ chi phí linh hoạt:**
  - Hỗ trợ chia đều (Equal Split) và chia theo tỷ lệ phần trăm (Ratio Split) với cơ chế phân bổ phần dư thông minh.
  - Hỗ trợ đa tiền tệ (VND, USD, EUR, JPY, THB, SGD, CNY,...) với tỷ giá quy đổi tham chiếu an toàn, chống tràn số.
  - Quản lý quỹ chung (Group Fund): theo dõi các đợt đóng quỹ, chi tiêu từ quỹ hoặc thành viên chi hộ.

- 📑 **Báo cáo & Sao lưu an toàn (Zero-Permission SAF):**
  - Xuất báo cáo tài chính sổ cái dạng CSV / PDF qua Android Storage Access Framework (SAF).
  - Cơ chế chống tấn công tiêm công thức bảng tính (CSV/Formula Injection Protection).
  - Sao lưu và khôi phục dữ liệu JSON với mã hóa mật khẩu AES-256-GCM kết hợp PBKDF2 600.000 vòng theo chuẩn bảo mật OWASP.
  - Cơ chế bảo vệ niêm phong sổ cái: các đoàn đã khóa sổ (`isSettled = true`) không bị hạ trạng thái khi khôi phục bản sao lưu cũ.

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

---

## 🚀 Hướng Dẫn Biên Dịch (Build & Run)

Dự án sử dụng Gradle Kotlin DSL:

### 1. Biên dịch bản Debug
```bash
gradle :app:assembleDebug
```
Tệp APK debug sẽ được tạo tại:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Chạy toàn bộ Unit Tests & Robolectric
```bash
gradle :app:testDebugUnitTest
```

### 3. Biên dịch bản Release (Tối ưu hóa ProGuard/R8)
```bash
gradle :app:assembleRelease
```
Tệp APK release sẽ được tạo tại:
`app/build/outputs/apk/release/app-release.apk`

---

## 📄 Bản Quyền & Giấy Phép
Phát triển bởi Nguyễn Hồng Hiệp. Bản quyền thuộc về tác giả.
