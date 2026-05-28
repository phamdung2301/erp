# e-Rescue Platform 🚗🛠️
> **Hệ thống Cứu hộ Giao thông Thông minh & Định giá Động Thời gian thực (Commercial-Grade)**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg?style=flat-square&logo=spring)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Build Status](https://img.shields.io/badge/Tests-136%20Passed-success?style=flat-square&logo=github)](https://github.com/phamdung2301/erp)

**e-Rescue Platform** là một nền tảng cứu hộ giao thông đường bộ thông minh, kết nối khách hàng gặp sự cố xe cộ với các thợ cứu hộ gần nhất trong thời gian thực. Hệ thống tích hợp chẩn đoán sự cố bằng AI, định giá động tự động, lộ trình di chuyển thực tế uốn lượn và cốp đồ nghề di động đồng bộ hóa hóa đơn tức thì.

---

## 🚀 5 Phân Hệ Thương Mại Go-Live Nổi Bật

### 1. Định giá Động & Phụ phí Đêm (Surge Pricing)
* Tự động điều chỉnh giá cơ bản dựa trên thời gian thực.
* Từ **22:00 đêm đến 05:00 sáng**, hệ thống tự động đặt phụ phí **+50,000 ₫** (`surcharge`) và nhân hệ số **1.2x** (`surgeMultiplier`).
* Đảm bảo tính minh bạch, hiển thị rõ ràng phí dịch vụ đêm trên app khách hàng.

### 2. Định vị Lộ trình Bezier & ETA Countdown Widget
* **Lộ trình uốn lượn**: Sử dụng thuật toán đường cong Bezier bậc 2 nội suy tọa độ, vẽ đường di chuyển thực tế của thợ cứu hộ len lỏi qua các con phố thay vì đường thẳng thô kệch.
* **ETA Widget**: Thiết kế dạng Glassmorphism cao cấp nổi trên bản đồ, đếm ngược thời gian thợ đến thực tế và tính toán khoảng cách đường bộ (`chim_bay * 1.35`).

### 3. Nhắn tin Real-time qua WebSocket (In-App Chat)
* Tích hợp hộp chat **Glassmorphic** bong bóng chat cực kỳ mượt mà trên Dashboard của cả Khách & Thợ khi cuốc xe ở trạng thái hoạt động.
* Truyền tin qua WebSocket session, đồng bộ lưu trữ xuống database qua REST API `/api/requests/{id}/chat-history` giúp khôi phục tin nhắn ngay cả khi F5 trang.

### 4. Cốp Đồ Nghề Di Động Thợ (Rescuer Mobile Inventory)
* Quản lý kho hàng di động mang theo của thợ cứu hộ (`RescuerInventory`).
* Trong tiến trình sửa chữa, thợ lựa chọn phụ tùng thực tế sử dụng từ dropdown cốp đồ nghề.
* Hệ thống tự động trừ 1 sản phẩm tồn kho và cộng đơn giá niêm yết trực tiếp vào hóa đơn phát sinh (`extraPrice`), đồng bộ hóa chi phí sang màn hình khách hàng thời gian thực qua WebSocket.

### 5. Chính Sách Phạt Hủy Cuốc & Đền Bù Thợ (Cancellation Policy)
* Khách hàng hủy cuốc khi thợ đã nhận cuốc xe và đang di chuyển / sửa chữa sẽ bị phạt **20,000 ₫** đền bù chi phí xăng xe và thời gian.
* Số tiền phạt này được **cộng trực tiếp vào ví hoạt động (`walletBalance`) của thợ cứu hộ** theo thời gian thực.

---

## 🛠️ Công Nghệ Sử Dụng

### Backend
* **Ngôn ngữ**: Java 21 (OpenJDK)
* **Framework**: Spring Boot 4.0.6 (Spring MVC, JPA, Spring Security, Spring WebSocket)
* **Cơ sở dữ liệu**: PostgreSQL (Môi trường Product), H2 In-Memory (Môi trường Test & Local)
* **Thư viện phụ trợ**: Lombok, Spring Data JPA, SecureRandom

### Frontend
* **Giao diện**: HTML5, Thymeleaf Template Engine
* **Phong cách**: Vanilla CSS3, thiết kế **Glassmorphism** tối tân (Blur background, HSL customized palettes, micro-animations)
* **Bản đồ**: Leaflet JS (sử dụng tile Dark Mode của CartoDB vô cùng sang trọng)

---

## 💻 Hướng Dẫn Cài Đặt & Khởi Chạy Local

### Yêu Cầu Hệ Thống
* **Java**: JDK 21 trở lên.
* **Maven**: Phiên bản 3.8.x trở lên.

### Các Bước Khởi Chạy

**1. Clone dự án và truy cập thư mục**
```bash
git clone git@github.com:phamdung2301/erp.git
cd erp
```

**2. Biên dịch dự án và tải dependencies**
```bash
mvn clean install
```

**3. Khởi chạy ứng dụng**
```bash
mvn spring-boot:run
```
Ứng dụng sẽ chạy tại cổng mặc định: `http://localhost:8080`

**4. Chạy bộ kiểm thử tự động (Unit / Integration Tests)**
```bash
mvn test
```

---

## 🧪 Chất Lượng Kiểm Thử (Test Suite)

Dự án sở hữu bộ kiểm thử tự động vô cùng chặt chẽ và bảo vệ tuyệt đối logic nghiệp vụ cốt lõi:

* **Tổng số test cases**: **136 / 136** Passed.
* **Kết quả build**: `BUILD SUCCESS` 🟢
* **Mức độ bao phủ**: Bao gồm đầy đủ unit test cho định giá động ban đêm, phí hủy cuốc đền bù thợ, trừ kho phụ tùng cốp đồ nghề di động, chẩn đoán sự cố bằng AI, rút tiền ví thợ, bảo mật OTP, khóa tài khoản khi nhập sai mật khẩu,...

---

## 👤 Tài Khoản Demo Hệ Thống

Dữ liệu demo được hệ thống tự động khởi tạo sẵn trong database H2 khi khởi chạy:

| Vai trò | Số điện thoại | Mật khẩu mặc định | Ghi chú |
| :--- | :--- | :--- | :--- |
| **Khách hàng** | `0901111111` | `password` | Khách hàng demo tạo yêu cầu cứu hộ |
| **Thợ cứu hộ** | `0902222222` | `password` | Đối tác thợ cứu hộ (Đã xác minh, có ví tiền) |
| **Thợ cứu hộ** | `0903333333` | `password` | Đối tác thợ cứu hộ (Chờ duyệt tài khoản) |
| **Quản trị viên** | `0909999999` | `password` | Admin phê duyệt rút tiền, cài đặt hệ thống |

---

## 📝 Giấy Phép (License)
Dự án được phân phối dưới giấy phép **MIT License**. Vui lòng tham khảo tệp `LICENSE` để biết thêm chi tiết.
