# Ứng dụng Quản lý Chi tiêu (Expense Tracker) 💰

Đây là Đồ án cuối kỳ môn **Lập Trình Thiết Bị Di Động**. 
Ứng dụng giúp người dùng ghi chép, theo dõi các khoản thu/chi cá nhân một cách tiện lợi và nhanh chóng thông qua giao diện trực quan và tính năng nhận diện giọng nói.

## ✨ Chức năng nổi bật

* **Giao diện hiện đại:** Thiết kế theo phong cách Dark Mode / Aurora Glass (Kính mờ cực quang) mang lại cảm giác sang trọng, chuẩn Fintech.
* **Quản lý Thu/Chi:** Thêm, sửa, xóa các giao dịch hàng ngày một cách dễ dàng.
* **Thống kê số dư:** Tự động tính toán và hiển thị tổng thu nhập, tổng chi tiêu và số dư hiện tại.
* **Nhập liệu bằng giọng nói (Voice Input):** Tích hợp Google Speech-to-Text, cho phép người dùng đọc câu lệnh (VD: *"chi 50k tiền ăn sáng"*) để app tự động bóc tách và điền vào biểu mẫu.

## 🛠 Công nghệ sử dụng

* **Ngôn ngữ:** Java (Android SDK)
* **Giao diện:** Android XML thuần (không dùng thư viện UI bên thứ ba để đảm bảo nhẹ và tối ưu).
* **Cơ sở dữ liệu:** SQLite (Lưu trữ dữ liệu cục bộ an toàn, không cần kết nối mạng).
* **API:** `RecognizerIntent` (Google Speech-to-Text).

## 🚀 Cài đặt và Chạy thử

1. Clone repository này về máy: `git clone https://github.com/mailehuyhai-lab/Project_ExpenseTracker.git`
2. Mở dự án bằng **Android Studio**.
3. Đợi Gradle đồng bộ (Sync) xong.
4. Kết nối điện thoại thật hoặc chạy máy ảo (Emulator) và bấm **Run**.

---
**Tác giả:** Mai Lê Huy Hải (mailehuyhai-lab)  
**MSSV:** 25TH2505
