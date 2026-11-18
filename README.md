::: {align="center"}
# ◈ AETHERIS TERMINAL

### Your shell. Your workflow. Your universe.

**Một không gian terminal Android được thiết kế cho những người thích
kiểm soát từng dòng lệnh.**

*Tokyo Night · Cascadia Code · Developer-first · Built on the Termux
foundation*
:::

------------------------------------------------------------------------

> **Aetheris Terminal đang trong quá trình phát triển.** README này mô
> tả tầm nhìn và các mục tiêu thiết kế của dự án; những tính năng chưa
> được triển khai hoặc kiểm thử không được xem là đã hoạt động.

## ✦ Giới thiệu

**Aetheris Terminal** là dự án terminal Android hướng đến trải nghiệm
hiện đại, tinh gọn và có thể cá nhân hóa sâu --- kết hợp nền tảng dòng
lệnh quen thuộc của Termux với một giao diện mang dấu ấn riêng của
Aetheris.

Mục tiêu không phải là biến terminal thành một món đồ trang trí. Mục
tiêu là làm cho công việc trong terminal **rõ ràng hơn, dễ thao tác hơn
và dễ đi theo hơn**, trong khi vẫn tôn trọng những hành vi cốt lõi mà
người dùng Termux dựa vào mỗi ngày.

-   **Terminal là trung tâm:** giao diện phục vụ công việc, không cản
    trở công việc.
-   **Tương thích là ưu tiên:** giữ nguyên các hành vi nền tảng trước
    khi thêm lớp trải nghiệm mới.
-   **Thiết kế có chủ đích:** phong cách neon tương lai vừa đủ, không
    rối mắt.
-   **Minh bạch và có thể bảo trì:** thay đổi theo từng bước nhỏ, có
    kiểm thử và có đường lui.

## ✧ Triết lý thiết kế

  -----------------------------------------------------------------------
  Thành phần                          Định hướng
  ----------------------------------- -----------------------------------
  **Visual language**                 Tokyo Night --- nền tối, tương phản
                                      rõ, điểm nhấn neon tiết chế

  **Typography**                      Cascadia Code --- ưu tiên khả năng
                                      đọc mã và lệnh

  **Layout**                          Phân tách khu vực terminal, thông
                                      tin thư mục hiện tại và thao tác
                                      nhanh

  **Interaction**                     Các nút tiện ích hữu dụng, trạng
                                      thái dễ nhận biết, hạn chế hiệu ứng
                                      thừa

  **Philosophy**                      Đẹp để dùng lâu dài, không chỉ đẹp
                                      trong ảnh chụp màn hình
  -----------------------------------------------------------------------

## ⚡ Định hướng tính năng

> Danh sách dưới đây là mục tiêu thiết kế, không phải cam kết rằng tất
> cả đã có mặt trong bản hiện tại.

### Terminal experience

-   Giữ trải nghiệm shell tương tác và luồng nhập/xuất lệnh của nền tảng
    gốc.
-   Hiển thị rõ vị trí làm việc hiện tại, bao gồm thư mục đang `cd` tới.
-   Phân biệt trực quan khu vực nhập lệnh và phần đầu ra.
-   Thao tác sao chép đầu ra nhanh chóng khi cần chia sẻ log hoặc gỡ
    lỗi.
-   Hướng đến khả năng tùy biến prompt, màu sắc, phông chữ, bố cục và
    Extra Keys.

### Developer workflow

-   Tiếp tục hỗ trợ quy trình làm việc với shell, script và các công cụ
    dòng lệnh tương thích.
-   Hướng đến trải nghiệm thuận tiện cho Git, SSH, chỉnh sửa mã và các
    workflow phát triển trên Android.
-   Ưu tiên thay đổi nhỏ, dễ rà soát và dễ chẩn đoán khi phát sinh lỗi.

### File access

-   Giữ môi trường làm việc chính trong vùng dữ liệu riêng của ứng dụng.
-   Khi cần truy cập tệp bên ngoài, hướng đến tích hợp trình chọn tệp
    Android (Storage Access Framework) thay vì chuyển toàn bộ môi trường
    Linux sang bộ nhớ dùng chung.
-   Có thể xem xét cách đưa các thư mục dự án ra vùng chia sẻ theo nhu
    cầu, nhưng không đánh đổi tính đúng đắn của môi trường terminal.

## 🛡️ Nguyên tắc tương thích

Aetheris Terminal được định hướng phát triển dựa trên nền tảng Termux,
vì vậy khả năng tương thích là yêu cầu nền tảng --- không phải tính năng
phụ.

Các nguyên tắc quan trọng:

1.  **Giữ nguyên vai trò của `$HOME` và `$PREFIX`.** Hai đường dẫn này
    phải tiếp tục hoạt động trong sandbox riêng của ứng dụng, phù hợp
    với cách các gói và công cụ dòng lệnh mong đợi.
2.  **Không phá vỡ nền tảng thực thi.** Shell, PTY, nhập/xuất, tiến
    trình, quyền tệp, symlink, script và package manager cần được bảo
    toàn.
3.  **Ưu tiên lớp giao diện.** Branding và UI nên được tùy biến mà không
    viết lại logic cốt lõi nếu không thực sự cần thiết.
4.  **Cài song song khi có thể.** Package/application ID riêng được định
    hướng để Aetheris có thể tồn tại bên cạnh Termux gốc, không buộc
    người dùng bỏ môi trường đang sử dụng.
5.  **Kiểm thử trước khi tuyên bố tương thích.** Không suy ra rằng mọi
    gói, plugin hoặc workflow đều hoạt động chỉ vì ứng dụng khởi chạy
    thành công.

## 🧰 Công nghệ & nền tảng

-   **Nền tảng:** Android
-   **Cơ sở dự án:** Termux
-   **Phong cách giao diện:** Tokyo Night
-   **Phông chữ định hướng:** Cascadia Code
-   **Môi trường shell:** kế thừa và bảo toàn hành vi của nền tảng gốc
    trong phạm vi tương thích

Chi tiết ngôn ngữ lập trình, phiên bản SDK, hệ thống build và các phụ
thuộc cần được xác nhận theo cấu hình thực tế của repository.

## 🚧 Trạng thái phát triển

Aetheris được phát triển theo hướng **bảo toàn trước, cải tiến sau**.

-   [ ] Xác lập bản nền có thể build và chạy ổn định
-   [ ] Đổi branding và application ID mà không làm mất dữ liệu ngoài ý
    muốn
-   [ ] Xác minh `$HOME`, `$PREFIX`, package manager và quyền tệp
-   [ ] Hoàn thiện bố cục terminal: input, output, thư mục hiện tại
-   [ ] Bổ sung thao tác sao chép output
-   [ ] Hoàn thiện màu sắc, typography và khả năng tùy biến giao diện
-   [ ] Kiểm thử shell tương tác, tiến trình dài hạn, script, Git và SSH
-   [ ] Viết hướng dẫn build, cài đặt, cập nhật và khôi phục rõ ràng

*Đánh dấu từng mục khi đã triển khai và kiểm thử thực tế.*

## 🏗️ Build từ source

Quy trình build chính xác phụ thuộc vào cấu trúc repository và phiên bản
Android/Gradle đang dùng. Trước khi build, hãy đọc hướng dẫn trong
repository và kiểm tra các yêu cầu môi trường.

Quy trình tổng quát:

1.  Clone repository về máy.
2.  Cài đặt đúng JDK, Android SDK và các phụ thuộc mà dự án yêu cầu.
3.  Mở dự án bằng Android Studio hoặc dùng Gradle Wrapper nếu repository
    cung cấp.
4.  Build biến thể phù hợp theo cấu hình dự án.
5.  Cài trên thiết bị thử nghiệm và chạy checklist tương thích trước khi
    dùng làm môi trường chính.

Không nên tự ý thay thế package, di chuyển `$HOME`/`$PREFIX` hoặc xóa dữ
liệu môi trường hiện có trong quá trình thử nghiệm.

## 🧪 Checklist kiểm thử đề xuất

Trước mỗi bản phát hành, nên xác minh tối thiểu:

-   [ ] Ứng dụng khởi chạy và mở được phiên terminal.
-   [ ] Lệnh tương tác hoạt động đúng; đầu vào và đầu ra không bị mất
    hoặc trộn sai.
-   [ ] Lệnh chạy lâu, tiến trình nền và thao tác ngắt tiến trình hoạt
    động như mong đợi.
-   [ ] `pwd`, `cd`, quyền tệp, symlink và script hoạt động đúng.
-   [ ] Package manager có thể chạy trong môi trường được hỗ trợ.
-   [ ] Git và SSH hoạt động với cấu hình hợp lệ.
-   [ ] Sao chép output không làm thay đổi nội dung gốc.
-   [ ] Dữ liệu người dùng được giữ nguyên qua cập nhật.
-   [ ] Cài đặt song song không ghi đè dữ liệu của Termux gốc.

## 🤝 Đóng góp

Đóng góp giúp Aetheris trở nên tốt hơn --- đặc biệt là các báo cáo lỗi
có thể tái hiện, cải tiến khả năng sử dụng và kiểm thử tương thích.

Khi gửi issue hoặc pull request, hãy cố gắng cung cấp:

-   Thiết bị và phiên bản Android.
-   Các bước tái hiện lỗi.
-   Kết quả mong đợi và kết quả thực tế.
-   Log liên quan đã loại bỏ token, khóa riêng, mật khẩu và dữ liệu cá
    nhân.
-   Phạm vi thay đổi cùng cách kiểm thử.

**Một nguyên tắc quan trọng:** tránh những thay đổi lớn không cần thiết
vào logic cốt lõi chỉ để đạt được một hiệu ứng giao diện.

## 🔐 An toàn & dữ liệu

Terminal có thể truy cập tệp và chạy lệnh theo quyền mà Android cấp cho
ứng dụng. Chỉ chạy script và cài gói từ nguồn bạn tin tưởng. Không chia
sẻ log chứa access token, private key, mật khẩu hoặc dữ liệu nhạy cảm.

Aetheris không nên yêu cầu người dùng chuyển toàn bộ môi trường Linux
sang bộ nhớ dùng chung chỉ để thuận tiện truy cập tệp. Hãy dùng cơ chế
cấp quyền truy cập tệp phù hợp với Android khi cần.

## 🗺️ Roadmap

**Phase 01 --- Foundation**\
Ổn định bản nền, build được, chạy được và xác minh môi trường terminal
cốt lõi.

**Phase 02 --- Identity**\
Hoàn thiện thương hiệu Aetheris, bảng màu Tokyo Night, typography và bố
cục giao diện.

**Phase 03 --- Workflow**\
Cải thiện hiển thị thư mục hiện tại, phân tách input/output và các thao
tác tiện ích.

**Phase 04 --- Reliability**\
Kiểm thử tương thích, bảo toàn dữ liệu, cải thiện chẩn đoán lỗi và tài
liệu hóa quy trình phát hành.

Roadmap có thể thay đổi theo kết quả kiểm thử và mức độ ổn định của từng
giai đoạn.

## 📜 License

Giấy phép của Aetheris Terminal cần được xác định theo giấy phép của mã
nguồn nền và các thành phần được phân phối cùng ứng dụng. Trước khi phát
hành, hãy giữ lại các thông báo bản quyền, giấy phép và yêu cầu ghi công
bắt buộc; không mặc định rằng toàn bộ dự án có thể đổi sang một giấy
phép khác.

------------------------------------------------------------------------

::: {align="center"}
**AETHERIS TERMINAL**

*Less friction. More flow.*
:::
