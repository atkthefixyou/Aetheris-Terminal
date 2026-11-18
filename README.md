<div align="center">

<img src="assets/aetheris-banner.png" alt="Aetheris Terminal — Less friction. More flow." width="100%">

<br>

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Foundation](https://img.shields.io/badge/Foundation-Termux-222222?style=for-the-badge&logo=linux&logoColor=white)](https://termux.dev/)
[![UI](https://img.shields.io/badge/Design-Tokyo%20Night-7C3AED?style=for-the-badge)](#-thiết-kế)
[![Status](https://img.shields.io/badge/Status-In%20Development-00B8D9?style=for-the-badge)](#-trạng-thái-dự-án)

### **Less friction. More flow. ◈**

*Một terminal Android hiện đại — nơi sức mạnh của dòng lệnh gặp gỡ trải nghiệm được thiết kế có chủ đích.*

[Giới thiệu](#-aetheris-terminal) · [Tính năng](#-điểm-nhấn) · [Roadmap](#-lộ-trình) · [Build](#-build-từ-source) · [Đóng góp](#-đóng-góp)

</div>

---

## ◈ Aetheris Terminal

**Aetheris Terminal** là dự án terminal Android được phát triển trên nền tảng **Termux**, hướng đến một trải nghiệm làm việc hiện đại, gọn gàng và có thể cá nhân hóa — nhưng không đánh đổi những chức năng cốt lõi làm nên một terminal đáng tin cậy.

Aetheris không chỉ hướng đến việc thay áo cho giao diện. Tầm nhìn của dự án là tạo ra một không gian làm việc nơi người dùng có thể tập trung vào lệnh, mã nguồn và quy trình phát triển, với thông tin được sắp xếp rõ ràng và thao tác thường dùng nằm đúng nơi cần có.

> **Triết lý:** Giao diện nên làm terminal dễ sử dụng hơn — không được trở thành lý do khiến terminal kém tin cậy hơn.

## ✦ Điểm nhấn

<table>
<tr>
<td width="50%" valign="top">

### 🎨 Giao diện có bản sắc
Phong cách **Tokyo Night** với nền tối, điểm nhấn tím/xanh neon và độ tương phản được cân nhắc để tạo cảm giác hiện đại mà không gây nhiễu.

</td>
<td width="50%" valign="top">

### ⌨️ Trải nghiệm terminal
Ưu tiên luồng nhập lệnh, đầu ra và trạng thái phiên làm việc rõ ràng. Terminal vẫn là trung tâm của sản phẩm.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📁 Ngữ cảnh làm việc
Định hướng hiển thị thư mục hiện tại, phân tách khu vực input/output và giúp người dùng nắm được mình đang làm việc ở đâu.

</td>
<td width="50%" valign="top">

### 📋 Thao tác nhanh
Định hướng tích hợp các tiện ích như sao chép output, giúp chia sẻ log, lưu kết quả và gỡ lỗi thuận tiện hơn.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🧩 Nền tảng quen thuộc
Xây dựng trên cơ sở Termux, ưu tiên bảo toàn hành vi shell và công cụ dòng lệnh trong phạm vi tương thích đã được kiểm thử.

</td>
<td width="50%" valign="top">

### 🛠️ Dành cho người thích tùy biến
Định hướng phát triển khả năng điều chỉnh giao diện, typography và bố cục để phù hợp với workflow của từng người dùng.

</td>
</tr>
</table>

## 🎨 Thiết kế

| Thành phần | Định hướng |
|:--|:--|
| **Visual language** | Tokyo Night — nền tối, tím điện và xanh cyan |
| **Typography** | Cascadia Code — rõ ràng, phù hợp với nội dung kỹ thuật |
| **Layout** | Tách bạch khu vực terminal, thư mục hiện tại và thao tác nhanh |
| **Interaction** | Có mục đích, dễ hiểu, hạn chế hiệu ứng thừa |
| **Nguyên tắc** | Đẹp để dùng hằng ngày, không chỉ đẹp trong ảnh demo |

## 🛡️ Tương thích là ưu tiên

Vì Aetheris dựa trên nền tảng Termux, việc giữ ổn định môi trường dòng lệnh là nguyên tắc phát triển quan trọng.

- **Bảo toàn `$HOME` và `$PREFIX`:** tránh thay đổi đường dẫn nền tảng theo cách làm hỏng công cụ hoặc gói đã cài.
- **Tôn trọng hành vi shell:** nhập/xuất, PTY, tiến trình, script, quyền tệp và các hành vi liên quan cần được kiểm thử.
- **Ưu tiên lớp giao diện:** tránh viết lại logic cốt lõi chỉ để đạt được hiệu ứng thị giác.
- **Không tuyên bố tương thích khi chưa kiểm chứng:** việc ứng dụng khởi chạy không đồng nghĩa mọi package hay workflow đều hoạt động.
- **Cài song song nếu kiến trúc cho phép:** tránh ghi đè môi trường Termux hiện có; cần xác minh package ID và quy trình lưu trữ trước khi phát hành.

## 🧭 Lộ trình

<table>
<tr>
<td align="center" width="25%">

**01 · Foundation**

`Nền tảng`

Xác lập bản build ổn định, kiểm tra môi trường terminal và dữ liệu.

</td>
<td align="center" width="25%">

**02 · Identity**

`Bản sắc`

Hoàn thiện branding, bảng màu, typography và bố cục.

</td>
<td align="center" width="25%">

**03 · Workflow**

`Quy trình`

Cải thiện hiển thị thư mục, input/output và thao tác tiện ích.

</td>
<td align="center" width="25%">

**04 · Reliability**

`Độ tin cậy`

Kiểm thử tương thích, cập nhật an toàn và tài liệu hóa phát hành.

</td>
</tr>
</table>

> Roadmap thể hiện định hướng phát triển. Hãy cập nhật trạng thái theo những gì đã được triển khai và kiểm thử thực tế.

## 🏗️ Build từ source

Quy trình cụ thể phụ thuộc vào cấu trúc repository, phiên bản Android SDK, JDK và hệ thống build mà dự án đang sử dụng.

1. Clone repository về máy.
2. Đọc hướng dẫn build và kiểm tra các phiên bản công cụ được yêu cầu.
3. Mở dự án bằng Android Studio hoặc sử dụng Gradle Wrapper nếu repository cung cấp.
4. Build đúng variant theo cấu hình hiện tại.
5. Cài trên thiết bị thử nghiệm và chạy checklist tương thích trước khi dùng làm môi trường chính.

**Lưu ý:** Chưa đưa lệnh build cố định vào README vì cần đối chiếu cấu trúc source thực tế trước khi khẳng định lệnh nào chính xác.

## 🧪 Checklist phát hành

- [ ] Ứng dụng khởi chạy và mở được phiên terminal.
- [ ] Nhập lệnh, đầu ra và thao tác ngắt tiến trình hoạt động đúng.
- [ ] `pwd`, `cd`, quyền tệp, symlink và script hoạt động đúng.
- [ ] `$HOME` và `$PREFIX` được giữ nguyên theo thiết kế nền tảng.
- [ ] Package manager hoạt động trong môi trường được hỗ trợ.
- [ ] Git và SSH được kiểm tra với cấu hình hợp lệ.
- [ ] Copy output không làm thay đổi nội dung.
- [ ] Cập nhật không làm mất dữ liệu người dùng.
- [ ] Cài song song không ghi đè dữ liệu của Termux gốc, nếu được hỗ trợ.

## 🤝 Đóng góp

Aetheris Terminal có thể tốt hơn nhờ những báo cáo lỗi rõ ràng, đề xuất thiết thực và các đóng góp có thể kiểm thử.

Khi mở Issue hoặc Pull Request, hãy cung cấp nếu có thể:

- Thiết bị và phiên bản Android.
- Các bước tái hiện vấn đề.
- Kết quả mong đợi và kết quả thực tế.
- Log đã loại bỏ token, mật khẩu, private key và dữ liệu cá nhân.
- Mô tả thay đổi và cách bạn đã kiểm thử.

Vui lòng giữ các thay đổi tập trung, dễ rà soát và hạn chế tác động không cần thiết đến logic terminal cốt lõi.

## 🔐 An toàn & dữ liệu

Terminal có thể chạy lệnh và truy cập tệp trong phạm vi quyền được cấp cho ứng dụng. Chỉ chạy script và cài gói từ nguồn đáng tin cậy. Không đăng tải log chứa access token, mật khẩu, private key hoặc dữ liệu nhạy cảm.

Khi cần truy cập tệp bên ngoài vùng riêng của ứng dụng, nên sử dụng cơ chế cấp quyền phù hợp với Android thay vì tùy tiện di chuyển toàn bộ môi trường Linux sang bộ nhớ dùng chung.

## 📦 Trạng thái dự án

Aetheris Terminal đang được phát triển. Tính năng, khả năng tương thích, cách build và quy trình phát hành cần được xác nhận theo source code của từng phiên bản.

Nếu bạn muốn dùng Aetheris làm môi trường chính, hãy thử nghiệm trên bản sao lưu hoặc thiết bị thử nghiệm trước khi chuyển workflow quan trọng sang.

## 📜 Giấy phép

Giấy phép phát hành cần được xác định dựa trên giấy phép của mã nguồn nền và các thành phần được phân phối cùng ứng dụng. Trước khi phát hành, hãy giữ đầy đủ thông báo bản quyền, giấy phép và yêu cầu ghi công bắt buộc. Không mặc định rằng toàn bộ dự án có thể sử dụng một giấy phép khác với các thành phần nền.

---

<div align="center">

### **AETHERIS TERMINAL**

*Your shell. Your workflow. Your universe.*

**Less friction. More flow. ◈**

<sub>Built with care on the Termux foundation.</sub>

</div>
