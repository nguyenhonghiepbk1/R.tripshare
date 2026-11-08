# Bộ Thông Tin & Mô Tả Ứng Dụng Đăng Tải Google Play (Store Listing) - TripFinance

Tài liệu này cung cấp nội dung niêm yết theo chuẩn quy định của **Google Play Console**, sử dụng **Tiếng Việt (`vi-VN`) làm ngôn ngữ niêm yết duy nhất** trên Cửa hàng (phù hợp với ngôn ngữ giao diện chính của ứng dụng).

- **Mã gói ứng dụng (Application ID):** `com.hiepnguyen.tripfinance.oddnis`
- **Phiên bản (Version):** `20.0` (VersionCode: `20`)
- **Quyền mạng (Permissions):** Không có quyền INTERNET (`Zero-Internet`), hoạt động 100% ngoại tuyến.
- **Phạm vi tính năng:** Quản lý chi tiêu đoàn, chia tiền nhóm, đối soát công nợ và gợi ý chuyển khoản; KHÔNG lưu trữ/xử lý tài khoản ngân hàng, KHÔNG tạo mã QR, KHÔNG thực hiện giao dịch chuyển tiền.

> **Ghi chú về ngôn ngữ niêm yết:** Hiện tại, ứng dụng chỉ duy trì bản niêm yết Tiếng Việt (`vi-VN`) trên Google Play Store để phản ánh chính xác ngôn ngữ giao diện chính của ứng dụng. Khi hoàn thiện bản địa hóa song ngữ toàn diện trong các phiên bản tiếp theo, bản niêm yết Tiếng Anh (`en-US`) sẽ được bổ sung sau.

---

## 1. Thông Tin Niêm Yết Tiếng Việt (`vi-VN`)

### 🏷 Tên ứng dụng (App Name)
> *Quy định Google Play: Tối đa 30 ký tự, không viết hoa toàn bộ, không chứa từ khóa quảng cáo.*

```text
TripFinance - Chia tiền đoàn
```
*(Độ dài: 28/30 ký tự)*

---

### 📝 Mô tả ngắn (Short Description)
> *Quy định Google Play: Tối đa 80 ký tự, nêu bật giá trị cốt lõi của ứng dụng.*

```text
Quản lý chi tiêu đoàn, chia tiền nhóm, theo dõi quỹ chung, gợi ý chuyển khoản.
```
*(Độ dài: 79/80 ký tự)*

---

### 📄 Mô tả đầy đủ (Full Description)
> *Quy định Google Play: Tối đa 4000 ký tự.*

```text
TripFinance là ứng dụng quản lý tài chính ngoại tuyến dành cho các chuyến du lịch nhóm, đoàn công tác, sự kiện gia đình hoặc hội bạn bè. Ứng dụng giúp bạn xóa bỏ phiền toái khi ghi chép sổ sách thủ công, phân chia chi phí phức tạp và đối soát công nợ sau mỗi chuyến đi.

CÁC TÍNH NĂNG CHÍNH:

1. GHI NHẬN THU CHI & PHÂN CHIA CHI PHÍ LINH HOẠT
• Ghi nhận các khoản chi tiêu theo danh mục, người thanh toán, số tiền và thời gian.
• Chia đều cho tất cả mọi người hoặc chỉ những người cùng tham gia khoản chi.
• Chia theo số tiền cụ thể hoặc theo tỷ lệ phần trăm tùy chỉnh với thuật toán phân bổ phần dư tự động.
• Hỗ trợ một người chi hộ nhiều người hoặc chi trực tiếp từ Quỹ chung của đoàn.

2. QUẢN LÝ QUỸ CHUNG ĐOÀN
• Theo dõi chi tiết từng đợt đóng quỹ của các thành viên.
• Chi tiêu trực tiếp từ quỹ đoàn với tính năng kiểm tra số dư khả dụng tức thời.
• Tự động tính toán phần quỹ còn dư để hoàn lại khi kết thúc chuyến đi.

3. ĐỐI SOÁT CÔNG NỢ & GỢI Ý CHUYỂN KHOẢN TỐI ƯU
• Thuật toán bù trừ công nợ tự động tính toán số dư ròng của từng thành viên, rút gọn số lượt giao dịch cần thiết xuống mức tối thiểu.
• Đưa ra gợi ý chuyển khoản rõ ràng: người gửi, người nhận, số tiền và nội dung đối soát.
• Hỗ trợ checkbox "Đã chuyển" trên từng dòng gợi ý để theo dõi tiến độ thanh toán (hoạt động bình thường cả khi đoàn đã khóa sổ).
• Lưu ý: Ứng dụng chỉ đưa ra gợi ý tính toán trên màn hình, KHÔNG lưu/nhập số tài khoản ngân hàng, KHÔNG tạo mã QR và KHÔNG thực hiện hay xử lý bất kỳ giao dịch tiền tệ nào.

4. HỖ TRỢ ĐA TIỀN TỆ & TỶ GIÁ TÙY CHỈNH
• Nhập chi tiêu bằng nhiều loại tiền tệ (USD, EUR, JPY, KRW, THB, SGD...).
• Quy đổi ra VND theo tỷ giá tham khảo hoặc tỷ giá người dùng tự thiết lập.

5. BẢO VỆ DỮ LIỆU CỤC BỘ & 100% NGOẠI TUYẾN
• Hoàn toàn không cần kết nối Internet: Ứng dụng không yêu cầu quyền INTERNET trong hệ thống, hoạt động bình thường trên máy bay hoặc vùng không có sóng di động.
• Cơ sở dữ liệu SQLite được mã hóa cục bộ bằng SQLCipher (AES-256) kết hợp quản lý khóa Android KeyStore.
• Tính năng xuất/nhập sao lưu định dạng JSON với tùy chọn mã hóa mật khẩu (sử dụng thuật toán AES-GCM và PBKDF2 600.000 vòng lặp).

6. XUẤT BÁO CÁO TÀI CHÍNH ĐA DẠNG
• Bảng tính Microsoft Excel (.xlsx) đa sheet chuẩn OpenXML (Tổng quan, Chi tiêu, Theo thành viên, Quỹ chung, Gợi ý chuyển khoản) tích hợp cơ chế chống tiêm công thức (Formula Injection Protection).
• Tệp báo cáo PDF chuẩn A4 trình bày trang trọng, đầy đủ bảng kê thu chi và phân bổ công nợ.
• Báo cáo tóm tắt văn bản dễ dàng sao chép để gửi nhanh qua tin nhắn.

7. CỐ VẤN CHI TIÊU NỘI BỘ (LOCAL SPENDING ADVISOR)
• Phân tích cơ cấu chi tiêu theo danh mục (ăn uống, đi lại, lưu trú, vé tham quan...).
• Tính toán trực tiếp trên vi xử lý của thiết bị, dữ liệu không rời khỏi điện thoại.

TripFinance đồng hành cùng bạn để mọi chuyến đi luôn vui vẻ và tài chính luôn minh bạch, rõ ràng!
```

---

## 2. Cấu Hình Phân Loại & Cài Đặt Cửa Hàng (Store Settings)

Khi điền thông tin trong **Cài đặt cửa hàng (Store settings)** trên Google Play Console:

| Mục thiết lập | Giá trị cấu hình |
| :--- | :--- |
| **Loại ứng dụng (App category)** | **Ứng dụng (Apps)** |
| **Danh mục (Category)** | **Tài chính (Finance)** *(hoặc Năng suất / Productivity)* |
| **Thẻ phân loại (Tags)** | `Quản lý chi tiêu`, `Tài chính cá nhân`, `Du lịch`, `Công cụ`, `Quản lý tiền` |
| **Xếp hạng nội dung (Content Rating)** | **3+ (Mọi lứa tuổi / Everyone)** — Không có nội dung người lớn, cờ bạc hay bạo lực |
| **Quảng cáo (Ads)** | Chọn **"Không, ứng dụng của tôi không chứa quảng cáo"** *(No ads)* |
| **Truy cập ứng dụng (App access)** | Chọn **"Tất cả chức năng đều dùng được mà không cần thông tin đăng nhập đặc biệt"** |
| **Đối tượng mục tiêu (Target audience)** | **18 tuổi trở lên (18+)** |

---

## 3. Khai Báo An Toàn Dữ Liệu (Data Safety Declaration)

Biểu mẫu **An toàn dữ liệu (Data Safety)** trên Google Play Console:

1. **Thu thập dữ liệu (Data collection):**
   - Chọn **Không (No)** — Ứng dụng không thu thập bất kỳ dữ liệu người dùng nào.
   - Toàn bộ dữ liệu được lưu trữ cục bộ trong vùng nhớ bảo mật nội bộ của ứng dụng.
2. **Chia sẻ dữ liệu (Data sharing):**
   - Chọn **Không (No)** — Không có dữ liệu nào được gửi lên máy chủ hay chia sẻ với bên thứ ba.
3. **Thực hành bảo mật (Security practices):**
   - **Dữ liệu được mã hóa khi lưu trữ (Data is encrypted at rest):** Đánh dấu **CÓ (Yes)** (Mã hóa CSDL SQLCipher AES-256).
   - **Cơ chế xóa dữ liệu (Data deletion mechanism):** Đánh dấu **CÓ (Yes)** (Người dùng có thể xóa từng bản ghi, xóa chuyến đi trong ứng dụng hoặc gỡ cài đặt ứng dụng để xóa toàn bộ dữ liệu khỏi thiết bị).
