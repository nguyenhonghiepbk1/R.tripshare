# CHÍNH SÁCH QUYỀN RIÊNG TƯ / PRIVACY POLICY - TRIPFINANCE

*Ngày có hiệu lực: 02 tháng 11 năm 2026*  
*Phiên bản: 20.0 (Mã gói ứng dụng: `com.hiepnguyen.tripfinance.oddnis`)*

---

# PHẦN 1: BẢN TIẾNG VIỆT (VIETNAMESE)

## 1. Giới Thiệu
Chào mừng bạn đến với **TripFinance** ("chúng tôi", "ứng dụng"). Chúng tôi cam kết tôn trọng và bảo vệ quyền riêng tư cũng như dữ liệu quản lý tài chính của người dùng. Chính sách quyền riêng tư này giải thích cách TripFinance xử lý thông tin khi bạn sử dụng ứng dụng trên thiết bị di động Android.

TripFinance được xây dựng trên triết lý **"Privacy-by-Design" (Bảo vệ quyền riêng tư ngay từ thiết kế)**: ứng dụng hoạt động 100% ngoại tuyến (offline-only), lưu trữ dữ liệu hoàn toàn trên thiết bị của bạn, không thu thập, không theo dõi và không chia sẻ dữ liệu cá nhân cho bất kỳ bên thứ ba nào.

---

## 2. Thông Tin Ứng Dụng Xử Lý & Lưu Trữ Cục Bộ

### 2.1. Dữ liệu chuyến đi và chi tiêu
- **Dữ liệu bao gồm:** Tên chuyến đi, mô tả, danh sách thành viên (tên hiển thị do bạn tự đặt, vai trò), các khoản chi tiêu (tiêu đề, số tiền, ngày giờ, danh mục chi tiêu, hình thức phân bổ nợ), lịch sử đóng góp quỹ đoàn, ghi chú chuyến đi và tỷ giá ngoại tệ tùy chỉnh.
- **Phạm vi dữ liệu tài chính & ngân hàng:**
  - Ứng dụng **KHÔNG** lưu, không yêu cầu nhập và không xuất bất kỳ số tài khoản ngân hàng, thông tin tài khoản hay dữ liệu thẻ ngân hàng nào của các thành viên.
  - Ứng dụng **KHÔNG** tạo mã QR chuyển khoản ngân hàng hay ví điện tử.
  - Ứng dụng **KHÔNG** thực hiện, không điều hướng và không xử lý bất kỳ giao dịch chuyển tiền hoặc thanh toán tài chính nào. Các thông tin "gợi ý chuyển khoản" chỉ là kết quả tính toán bù trừ số học hiển thị trên màn hình để các thành viên tự tham khảo.
- **Lưu trữ trên thiết bị:** Toàn bộ dữ liệu được lưu trữ trực tiếp và duy nhất trong vùng nhớ bảo mật nội bộ của ứng dụng (App Sandbox) trên thiết bị của bạn trong cơ sở dữ liệu SQLite được mã hóa cục bộ bằng công nghệ **SQLCipher (tiêu chuẩn AES-256)** với khóa quản lý qua **Android KeyStore**.
- **Không gửi ra máy chủ:** Ứng dụng không có máy chủ trung gian, không tải dữ liệu lên đám mây, không lưu trữ bản sao từ xa và nhà phát triển không thể truy cập vào dữ liệu tài chính của bạn.

### 2.2. Phân tích & Gợi ý chi tiêu ngoại tuyến (Local Spending Advisor)
- **100% Cục bộ trên máy:** Tính năng phân tích chi tiêu hoạt động hoàn toàn bằng thuật toán nội bộ trên thiết bị (`LocalSpendingAdvisor`), chạy ngoại tuyến 100%, không cần kết nối mạng và không gửi dữ liệu ra ngoài.
- **Xử lý trên thiết bị:** Việc đối soát số liệu, phân loại chi phí theo danh mục và đánh giá số dư quỹ đoàn được thực hiện trực tiếp trên vi xử lý của điện thoại.

### 2.3. Quyền Thiết Bị & Khai Báo Hệ Thống
- **Không yêu cầu quyền INTERNET:** Ứng dụng hoàn toàn không khai báo và không yêu cầu quyền `android.permission.INTERNET` trong tệp `AndroidManifest.xml`. Ứng dụng không thể kết nối mạng dưới bất kỳ hình thức nào.
- **Không yêu cầu quyền đọc/ghi bộ nhớ ngoài diện rộng:** Ứng dụng không sử dụng `READ_EXTERNAL_STORAGE` hay `WRITE_EXTERNAL_STORAGE`. Khi người dùng chủ động xuất báo cáo (Excel `.xlsx`, tài liệu PDF hoặc văn bản tóm tắt), tệp được tạo trong thư mục bộ nhớ tạm bảo mật của ứng dụng và chia sẻ qua cơ chế an toàn tiêu chuẩn của hệ điều hành Android (`androidx.core.content.FileProvider`).

---

## 3. Bảo Mật Dữ Liệu & Sao Lưu Mã Hóa

- **Mã hóa cơ sở dữ liệu:** Toàn bộ bảng dữ liệu Room SQLite được mã hóa cấp trang bằng thư viện SQLCipher AES-256. Khóa mã hóa cơ sở dữ liệu được tạo ngẫu nhiên bảo mật bằng `SecureRandom` và lưu trữ an toàn trong vùng nhớ ứng dụng được bảo vệ bởi Android KeyStore.
- **Sao lưu mã hóa (Encrypted Backup):** Khi bạn xuất tệp sao lưu dữ liệu chuyến đi định dạng JSON, bạn có thể tùy chọn mã hóa tệp bằng thuật toán AES-GCM với khóa dẫn xuất từ mật khẩu người dùng thông qua hàm PBKDF2 (600.000 vòng lặp HMAC-SHA256 theo khuyến nghị bảo mật). Tệp sao lưu chỉ có thể mở lại khi nhập chính xác mật khẩu đã đặt.
- **Không tự động sao lưu đám mây:** Thuộc tính `android:allowBackup="false"` được kích hoạt trong tệp cấu hình ứng dụng nhằm ngăn ngừa việc hệ điều hành tự động đưa tệp cơ sở dữ liệu lên các dịch vụ lưu trữ đám mây chưa được kiểm soát.

---

## 4. Quyền Kiểm Soát & Xóa Dữ Liệu

- **Quyền tự quyết:** Bạn có toàn quyền xem, thêm, chỉnh sửa hoặc xóa bỏ từng khoản chi tiêu, lượt nộp quỹ, thành viên hoặc toàn bộ chuyến đi trực tiếp trong giao diện ứng dụng.
- **Xóa vĩnh viễn:** 
  - Khi bạn chọn xóa một chuyến đi, toàn bộ các bản ghi liên quan (chi tiêu, phân bổ, quỹ, gợi ý chuyển khoản) sẽ được xóa hoàn toàn khỏi cơ sở dữ liệu trên máy.
  - Khi bạn gỡ cài đặt (Uninstall) ứng dụng TripFinance khỏi thiết bị, hệ điều hành Android sẽ tự động xóa sạch toàn bộ thư mục sandbox chứa cơ sở dữ liệu mã hóa và khóa bảo mật tương ứng. Không còn bản lưu nào tồn tại trên thiết bị.

---

## 5. Quyền Riêng Tư Của Trẻ Em
Ứng dụng TripFinance là công cụ hỗ trợ ghi nhận và phân chia chi phí chuyến đi, không thu thập bất kỳ thông tin nhận dạng cá nhân nào từ bất kỳ ai, bao gồm cả trẻ em dưới 13 tuổi.

---

## 6. Thay Đổi Đối Với Chính Sách Này
Chúng tôi có thể cập nhật Chính sách quyền riêng tư này khi phát hành các phiên bản mới của ứng dụng. Mọi cập nhật sẽ được ghi rõ số phiên bản và ngày có hiệu lực ở đầu tài liệu.

---

## 7. Thông Tin Liên Hệ
Nếu bạn có bất kỳ câu hỏi hoặc góp ý nào về Chính sách quyền riêng tư của TripFinance, vui lòng liên hệ:
- **Nhà phát triển:** Nguyễn Hồng Hiệp
- **Email:** `nguyenhonghiepbk1@gmail.com`
- **Tên gói ứng dụng:** `com.hiepnguyen.tripfinance.oddnis`

---
---

# PART 2: ENGLISH VERSION (PRIVACY POLICY)

## 1. Introduction
Welcome to **TripFinance** ("we", "our", or "the app"). We are committed to respecting and protecting your privacy and financial data. This Privacy Policy outlines how TripFinance handles information when you use the app on Android devices.

TripFinance is built upon a strict **Privacy-by-Design** architecture: the app operates 100% offline (offline-only), stores data exclusively on your local device, and does not collect, track, or share personal data with third parties.

---

## 2. Information Handled & On-Device Storage

### 2.1. Trip and Financial Records
- **Data Handled:** Trip titles, descriptions, member names (custom labels defined by you, roles), recorded expenses (titles, amounts, timestamps, categories, debt allocation splits), group fund contributions, trip notes, and custom foreign currency exchange rates.
- **Scope of Financial & Banking Data:**
  - The app **DOES NOT** store, request input of, or export any member bank account numbers, bank credentials, or payment card details.
  - The app **DOES NOT** generate QR codes for bank transfers or electronic wallets.
  - The app **DOES NOT** execute, route, or process any monetary transactions or payments. Settlement suggestions are purely calculated arithmetic recommendations displayed on screen for informational reference among group members.
- **Local On-Device Storage:** All data is stored directly and solely within the application's local sandbox on your device inside a SQLite database encrypted at rest with **SQLCipher (AES-256 standard)** and managed via **Android KeyStore**.
- **No Remote Transmission:** The application does not maintain external backend servers, does not sync to cloud platforms, does not create remote copies, and the developer cannot view or access your data.

### 2.2. Offline Spending Insights (Local Spending Advisor)
- **100% On-Device:** The built-in spending analysis feature operates entirely using local algorithms on the device (`LocalSpendingAdvisor`), functioning 100% offline without any internet connection.
- **On-Device Computation:** Financial reconciliations, category aggregations, and fund balance calculations are executed entirely on your device's processor.

### 2.3. System Permissions & Declarations
- **No INTERNET Permission:** The app does not declare or request the `android.permission.INTERNET` permission in its `AndroidManifest.xml`. It cannot establish internet connections under any circumstance.
- **No Broad Storage Permissions:** The app does not request `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`. When you choose to export financial reports (Excel `.xlsx`, PDF documents, or summary text), files are generated within the app's secure cache directory and shared via standard Android `androidx.core.content.FileProvider`.

---

## 3. Data Protection & Encrypted Backup Practices

- **Database Encryption at Rest:** The entire Room SQLite database is encrypted at the page level using SQLCipher with AES-256. Database encryption keys are generated using `SecureRandom` and stored in application-private storage protected by Android KeyStore.
- **Passphrase-Protected Encrypted Backup:** When you export a trip backup file in JSON format, you can optionally encrypt the archive using AES-GCM with a cryptographic key derived from your passphrase via PBKDF2 (600,000 HMAC-SHA256 iterations in compliance with security guidelines). The backup can only be decrypted when providing the correct passphrase.
- **Disabled Automatic Cloud Backups:** We configure `android:allowBackup="false"` to prevent operating system mechanisms from uploading unencrypted database snapshots to external cloud backups.

---

## 4. User Control & Data Deletion

- **Full Control:** You have complete authority to view, add, edit, or delete any expense record, fund contribution, participant, or entire trip directly in the app.
- **Permanent Purge:**
  - Deleting a trip purges all corresponding entries (expenses, splits, funds, settlement suggestions) immediately from the on-device database.
  - Uninstalling the TripFinance application from your device causes Android to permanently delete the entire app sandbox, including the encrypted database file and encryption keys. No residual data remains on the device.

---

## 5. Children's Privacy
TripFinance is an expense recording and splitting utility for group travel and team activities. It does not collect or process personally identifiable information from anyone, including children under 13 years of age.

---

## 6. Updates to This Policy
We may revise this Privacy Policy when releasing updated application versions. Any revisions will state the updated version number and effective date at the top of the document.

---

## 7. Contact Us
For questions, feedback, or inquiries regarding this Privacy Policy:
- **Developer:** Hiep Nguyen
- **Email:** `nguyenhonghiepbk1@gmail.com`
- **Application Package:** `com.hiepnguyen.tripfinance.oddnis`
