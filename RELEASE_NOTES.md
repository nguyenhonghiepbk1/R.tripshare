# Ghi Chú Phát Hành (Release Notes) - TripFinance

---

## 📌 Tổng Quan Phiên Bản (Version Overview)

| Thông số | Giá trị |
| :--- | :--- |
| **Phiên bản (Version Name)** | **20.0** |
| **Mã bản dựng (Version Code)** | **20** |
| **Tên gói ứng dụng (Package Name)** | `com.hiepnguyen.tripfinance.oddnis` |
| **Target SDK** | **Android 15+ (API Level 36)** |
| **Minimum SDK** | Android 7.0 (API Level 24) |
| **Kiến trúc cơ sở dữ liệu** | Room SQLite + SQLCipher AES-256 (`net.zetetic:sqlcipher-android:4.6.1`) |
| **Trạng thái phát hành** | **Sẵn sàng cho Google Play Store Production** |

---

## 🚀 Tóm Tắt Phát Hành Cho Google Play Console (Copy-Paste Ready)

### 🇻🇳 Tiếng Việt (`vi-VN`) — Tối đa 500 ký tự:
```text
TripFinance v20.0:
• Hỗ trợ toàn diện Android 15+ và bộ nhớ trang 16 KB với SQLCipher 4.6.1 mới nhất.
• Bảo mật đa lớp: Kiểm tra tính hợp lệ thành viên đa đoàn, loại bỏ sai lệch dữ liệu.
• Thuật toán quyết toán phi trạng thái, tính toán nợ tối ưu an toàn đa luồng.
• Cố vấn chi tiêu bảo mật: Minh bạch xin ý kiến người dùng, phân tích cục bộ ngoại tuyến 100%.
• Tinh gọn mã nguồn, tối ưu hóa hiệu năng và tăng tốc độ khởi chạy ứng dụng.
```

### 🇺🇸 Tiếng Anh (`en-US`) — Max 500 characters:
```text
TripFinance v19.0 - Major Release:
• Full 16 KB page-size compatibility on Android 15+ with modern SQLCipher 4.6.1.
• Cross-trip data integrity: Multi-layer validation for members and expense splits.
• Thread-safe & stateless settlement engine for flawless zero-discrepancy balancing.
• Privacy-first AI insights: Explicit user consent dialog & 100% offline local engine.
• Cleaned unused code, optimized ProGuard/R8 shrinking, and improved startup speed.
```

---

## 🌟 Chi Tiết Các Cải Tiến & Tính Năng Mới Trong Phiên Bản 19.0

### 1. Tương Thích Bộ Nhớ Trang 16 KB trên Android 15+ (TargetSdk = 36)
- **Nâng cấp thư viện chính thức:** Chuyển đổi toàn bộ từ thư viện cũ đã bị thay thế (`android-database-sqlcipher:4.5.4`) sang phiên bản chính thức hiện đại **`net.zetetic:sqlcipher-android:4.6.1`**.
- **Căn chỉnh nhị phân 16 KB ELF:** Tất cả các tệp `.so` native (`libsqlcipher.so`) được căn chỉnh 16 KB boundary, đảm bảo ứng dụng chạy mượt mà, không gặp lỗi bộ nhớ trên Android 15, Android 16 và các dòng chip thế hệ mới.
- **Mã hóa CSDL cấp trang AES-256:** Bảo vệ toàn bộ dữ liệu Room SQLite với khóa bảo mật phần cứng Android KeyStore.

### 2. Toàn Vẹn Dữ Liệu Nghiệp Vụ Đa Đoàn (Cross-Trip Integrity)
- **Khắc phục triệt để lỗ hổng Foreign Key thông thường:** Trước đây, khóa ngoại chỉ kiểm tra xem ID thành viên có tồn tại trong bảng thành viên hay không, mà không đối chiếu ngữ cảnh đoàn.
- **Bảo vệ Đa Tầng (Defense-in-Depth):**
  - **Tầng Repository:** Kiểm tra nghiêm ngặt `payerMemberId`, `createdMemberId`, người nộp quỹ và toàn bộ danh sách `splits`. Nếu bất kỳ thành viên nào không thuộc chuyến đi hiện tại (`tripId`), thao tác sẽ bị từ chối ngay lập tức.
  - **Tầng CSDL (SQLite Triggers):** Thiết lập hệ thống trigger tự động chặn các thao tác INSERT/UPDATE vi phạm ràng buộc chéo giữa các đoàn.

### 3. Động Cơ Quyết Toán Phi Trạng Thái & An Toàn Đa Luồng (Stateless Settlement Engine)
- **Loại bỏ biến toàn cục khả biến:** Xóa bỏ hoàn toàn biến `SettlementEngine.lastReconciliationError`.
- **Thiết kế bất biến (Immutable Result):** Kết quả đối soát và thông tin sai số (nếu có) được đóng gói độc lập trong `SettlementCalculationResult`, loại trừ rủi ro tương tranh (race conditions) khi chạy đa luồng.
- **Dung sai đối soát bằng không (Zero Discrepancy Tolerance):** Thuật toán chia tiền phân bổ chính xác đến từng 1 đồng VND, đảm bảo tổng chênh lệch thu chi luôn cân bằng tuyệt đối.

### 4. Gợi Ý Chi Tiêu & Bảo Mật Quyền Riêng Tư (100% Offline Local Engine)
- **Không nhúng API Key trong APK & Không cần quyền INTERNET:** Loại bỏ hoàn toàn mã kết nối AI Cloud và quyền INTERNET trong AndroidManifest, ngăn chặn triệt để mọi rủi ro về an toàn mạng.
- **Động cơ phân tích cục bộ 100% ngoại tuyến (`LocalSpendingAdvisor`):** Người dùng nhận phân tích tài chính chi tiêu chuyên sâu hoàn toàn ngoại tuyến ngay trên thiết bị, không cần mạng hay gửi dữ liệu ra máy chủ.

### 5. Tinh Gọn Mã Nguồn & Loại Bỏ Mã Chết (Dead Code Elimination)
- Loại bỏ hoàn toàn phương thức `joinTripByCode` và `deleteMemberDirect` không còn sử dụng.
- Dọn dẹp chuỗi tài nguyên và giao diện không khả dụng, giúp ứng dụng nhẹ hơn, giảm thiểu nguy cơ lỗi tiềm ẩn.

### 6. Tối Ưu Hóa R8 / ProGuard
- **Làm rõ ranh giới bảo mật:** Ghi nhận thực tế R8 là công cụ thu gọn mã và tối ưu hóa kích thước (shrinking), không phải rào chắn bảo mật chống đảo ngược mã.
- **Quy tắc tinh gọn:** Bỏ các chỉ thị `-keep` giữ lại toàn bộ package. Chỉ giữ lại các trường và hàm khởi tạo tối thiểu phục vụ ORM Room Database và JNI của SQLCipher.
- Kích hoạt `isShrinkResources = true` và `isMinifyEnabled = true` cho bản phát hành Release.

### 7. Tái Định Danh Package Cá Nhân Hóa
- Chuẩn hóa toàn bộ cấu trúc mã nguồn, manifest và cấu hình Gradle theo định dạng định danh cá nhân: **`com.hiepnguyen.tripfinance`**.

---

## 🛠 Hướng Dẫn Nâng Cấp & Triển Khai (Deployment Guide)

1. **Biên dịch bản phát hành:**
   ```bash
   gradle :app:bundleRelease
   ```
   Tệp AAB xuất ra tại: `app/build/outputs/bundle/release/app-release.aab`

2. **Tải lên Google Play Console:**
   - Vào **Google Play Console** > Chọn ứng dụng **TripFinance** > **Production** (hoặc **Testing**).
   - Chọn **Create new release** và tải lên tệp `.aab`.
   - Sao chép nội dung ở mục **🚀 Tóm Tắt Phát Hành Cho Google Play Console** phía trên và dán vào phần **Release notes**.
   - Bấm **Review release** và hoàn tất đăng tải.
