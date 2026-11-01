# CHÍNH SÁCH QUYỀN RIÊNG TƯ / PRIVACY POLICY - TRIPFINANCE

*Ngày có hiệu lực: 01 tháng 10 năm 2026*  
*Phiên bản: 1.0 (Áp dụng cho ứng dụng TripFinance - Mã gói: `com.hiepnguyen.tripfinance`)*

---

# PHẦN 1: BẢN TIẾNG VIỆT (VIETNAMESE)

## 1. Giới Thiệu
Chào mừng bạn đến với **TripFinance** ("chúng tôi", "ứng dụng"). Chúng tôi cam kết tôn trọng và bảo vệ tuyệt đối quyền riêng tư cũng như dữ liệu tài chính của người dùng. Chính sách quyền riêng tư này giải thích rõ ràng cách TripFinance xử lý thông tin khi bạn sử dụng ứng dụng trên thiết bị di động Android.

TripFinance được xây dựng trên triết lý **"Privacy-by-Design" (Bảo vệ quyền riêng tư ngay từ thiết kế)**: ứng dụng hoạt động chủ yếu ngoại tuyến (offline-first), lưu trữ dữ liệu tại chỗ và không thu thập dữ liệu cá nhân của bạn để phục vụ quảng cáo hay thương mại.

---

## 2. Thông Tin Ứng Dụng Thu Thập & Cách Thức Sử Dụng

### 2.1. Dữ liệu tài chính và chuyến đi (Được lưu trữ 100% CỤC BỘ trên thiết bị)
- **Dữ liệu bao gồm:** Tên chuyến đi, danh sách thành viên (tên hiển thị do bạn tự đặt), các khoản chi tiêu, số tiền, ngày giờ, phân bổ chi phí, tiền đóng quỹ và số tài khoản ngân hàng (nếu bạn tự nhập để tạo mã chuyển khoản).
- **Cách xử lý:** Toàn bộ dữ liệu này được lưu trữ **trực tiếp và duy nhất trên bộ nhớ thiết bị của bạn** trong cơ sở dữ liệu SQLite đã được mã hóa toàn bộ cấp độ trang (Full Database Encryption at Rest) bằng công nghệ **SQLCipher (tiêu chuẩn AES-256)** với khóa bảo mật phần cứng **Android KeyStore**.
- **Chúng tôi KHÔNG:** Chúng tôi không lưu trữ, không sao lưu và không có quyền truy cập vào dữ liệu tài chính này của bạn trên bất kỳ máy chủ bên ngoài nào.

### 2.2. Phân tích & Gợi ý chi tiêu ngoại tuyến (Local-First Spending Insights)
- **100% Cục bộ trên thiết bị:** Ứng dụng tích hợp công cụ phân tích cục bộ hoạt động hoàn toàn trên thiết bị (`LocalSpendingAdvisor`), chạy ngoại tuyến 100%, không sử dụng kết nối mạng Internet và không gửi bất kỳ dữ liệu nào ra bên ngoài.
- **Bảo mật tuyệt đối:** Toàn bộ việc đối soát số liệu, cơ cấu chi tiêu và sức khỏe quỹ đoàn được tính toán trực tiếp trên máy bằng thuật toán nội bộ.

### 2.3. Thông tin kỹ thuật & Quyền thiết bị
- **Không yêu cầu quyền INTERNET:** Ứng dụng hoạt động hoàn toàn ngoại tuyến (Zero-Internet Permission).
- **Quyền bộ nhớ:** Ứng dụng **hoàn toàn không yêu cầu** các quyền truy cập bộ nhớ diện rộng (`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`). Khi bạn xuất báo cáo (PDF, Excel, hình ảnh), ứng dụng sử dụng cơ chế chia sẻ an toàn tiêu chuẩn của hệ điều hành Android (`FileProvider`).

---

## 3. Lưu Trữ, Bảo Mật Dữ Liệu & Sao Lưu
- **Mã hóa AES-256:** Cơ sở dữ liệu ứng dụng được bảo vệ bởi chuẩn mã hóa tiên tiến nhất hiện nay, tương thích chuẩn bộ nhớ trang 16 KB trên Android 15+.
- **Không tự động tải lên đám mây:** Ứng dụng tắt tính năng tự động sao lưu của hệ điều hành (`android:allowBackup="false"`), đảm bảo tệp dữ liệu không bị tự ý tải lên các dịch vụ đám mây chưa được mã hóa độc lập.
- **Toàn vẹn dữ liệu:** Hệ thống áp dụng các ràng buộc kiểm tra nghiêm ngặt (Triggers & Repository Validation) để ngăn ngừa dữ liệu bị xáo trộn hoặc thất thoát.

---

## 4. Quyền Kiểm Soát & Xóa Dữ Liệu Của Bạn
- **Quyền chỉnh sửa và xóa:** Bạn có toàn quyền thêm, sửa hoặc xóa bất kỳ chuyến đi, khoản chi tiêu hoặc thành viên nào trực tiếp trong ứng dụng.
- **Xóa vĩnh viễn:** Khi bạn xóa một chuyến đi trong ứng dụng hoặc gỡ cài đặt (Uninstall) ứng dụng TripFinance khỏi điện thoại, toàn bộ cơ sở dữ liệu và khóa giải mã trên thiết bị sẽ bị xóa vĩnh viễn và không thể phục hồi.

---

## 5. Quyền Riêng Tư Của Trẻ Em
Ứng dụng TripFinance không nhắm tới đối tượng là trẻ em dưới 13 tuổi (hoặc độ tuổi tối thiểu theo quy định tại quốc gia của bạn). Chúng tôi không cố ý thu thập bất kỳ thông tin nhận dạng cá nhân nào từ trẻ em.

---

## 6. Thay Đổi Đối Với Chính Sách Này
Chúng tôi có thể cập nhật Chính sách quyền riêng tư này theo thời gian để phản ánh các cải tiến về tính năng hoặc yêu cầu pháp lý. Mọi thay đổi sẽ được cập nhật ngày hiệu lực ở đầu văn bản và hiển thị trong ứng dụng hoặc trang phát hành.

---

## 7. Thông Tin Liên Hệ
Nếu bạn có bất kỳ câu hỏi, thắc mắc hoặc yêu cầu nào liên quan đến Chính sách quyền riêng tư này, vui lòng liên hệ với nhà phát triển qua:
- **Email:** `nguyenhonghiepbk1@gmail.com`
- **Ứng dụng:** TripFinance (Phát triển bởi Hiệp Nguyễn)

---
---

# PART 2: ENGLISH VERSION (PRIVACY POLICY)

## 1. Introduction
Welcome to **TripFinance** ("we", "our", or "app"). We are fully dedicated to protecting your privacy and personal financial information. This Privacy Policy details how TripFinance handles your information when you use our Android mobile application (`com.hiepnguyen.tripfinance`).

TripFinance is engineered with a strict **Privacy-by-Design and Offline-First** architecture. We do not harvest, track, or sell your personal or financial data.

---

## 2. Information Handled & How It Is Used

### 2.1. Trip and Financial Records (Stored 100% Locally on Device)
- **Scope:** Trip titles, member names, logged expenses, split allocations, fund contributions, and bank account numbers (if entered for QR code transfer generation).
- **Storage & Encryption:** All information is stored **locally on your device** inside a SQLite database encrypted at rest using **SQLCipher (AES-256)** backed by the hardware-protected **Android KeyStore**.
- **No Remote Harvesting:** We do not transmit, copy, or maintain your financial records on external cloud servers.

### 2.2. Offline Spending Insights & Privacy Safeguards
- **100% On-Device Engine:** TripFinance features an on-device local analytical engine (`LocalSpendingAdvisor`) that operates 100% offline, requires zero Internet connection, and transmits zero data off your phone.
- **Zero Remote Harvesting:** Personal identities, member names, bank account numbers, and transaction logs are never transmitted to external services.

### 2.3. System Permissions
- **Zero-Internet Permission:** The application does not declare or request the `INTERNET` permission. All processing and storage are entirely local.
- **Zero Broad Storage Permissions:** We do not request `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`. Reports (PDF, Excel, images) are shared using Android's secure `FileProvider` sandboxing.

---

## 3. Data Protection & Security Practices
- **AES-256 Encryption:** Military-grade on-disk database encryption compatible with Android 15+ 16 KB memory pages.
- **Disabled Automatic Cloud Backups:** We configure `android:allowBackup="false"` to prevent untracked, unencrypted operating system backups to external clouds.
- **Defensive Data Integrity:** SQLite triggers and strict application validations guarantee that financial balances and member assignments remain consistent.

---

## 4. User Rights & Data Deletion
- **Full Control:** You can modify or delete individual expenses, members, or entire trips at any time within the app.
- **Complete Erasure:** Deleting a trip from the app or uninstalling the app permanently purges the local database and cryptographic keys from your device.

---

## 5. Children's Privacy
TripFinance does not knowingly collect or solicit personal information from children under the age of 13.

---

## 6. Changes to This Privacy Policy
We may update this policy periodically to reflect feature updates or regulatory compliance. Any revisions will be reflected with an updated effective date.

---

## 7. Contact Us
For questions, feedback, or privacy inquiries:
- **Developer:** Hiep Nguyen
- **Email:** `nguyenhonghiepbk1@gmail.com`
- **Application:** TripFinance (`com.hiepnguyen.tripfinance`)
