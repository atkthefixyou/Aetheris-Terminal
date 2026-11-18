# Tương thích prefix gói Termux (com.termux → com.aether)

## Nguyên nhân gốc (đã xác minh một phần)
- Đã xác minh: lỗi `dpkg: unable to stat './data/data/com.termux'` phát sinh khi dpkg giải nén `.deb` chính thức, vì các
  entry của tar payload là đường dẫn tuyệt đối `./data/data/com.termux/files/usr/...` (theo kiểm tra `.deb` của bạn).
  Android từ chối truy cập thư mục dữ liệu của app khác → `Permission denied`.
- Bootstrap của Aetheris đã được patch cùng độ dài (`app/build.gradle`, `patchBootstrap`) nên `apt`/`dpkg`/`bash` chạy đúng
  prefix `com.aether`. Gói cài thêm từ repo Termux thì chưa được patch: đây mới là thành phần gây lỗi, không phải `dpkg`.
- Chưa xác minh trên thiết bị: nội dung từng `.deb` cụ thể (`gh`, `openssh`) vì repo Termux không truy cập được từ môi trường dựng.

## Các phương án
| | Ưu | Nhược |
|---|---|---|
| A. Dùng `com.termux` | Gói chính thức chạy nguyên | Không cài song song được, xung đột chữ ký/ID với Termux gốc, trái mục tiêu dự án |
| B. Dựng lại toàn bộ gói cho prefix riêng (termux-packages) | Đúng kiến trúc nhất | Hàng nghìn gói, hạ tầng build + repo + khoá ký riêng, bảo trì rất nặng |
| C. Viết lại `.deb` lúc cài, sau khi apt đã xác thực (**chọn**) | Mọi gói, mọi bản cập nhật, giữ nguyên repo + chữ ký chính thức, không sửa dpkg/DB | Tốn thời gian rewrite; không bao phủ nội dung nén bên trong (jar/zip/whl) |

## Cơ chế (phương án C)
`etc/apt/apt.conf.d/99aetheris-prefix` đăng ký hook `DPkg::Pre-Install-Pkgs` → `libexec/aetheris/rewrite-debs`
(từ `app/src/main/assets/aetheris-rewrite-debs.sh`, app ghi lại mỗi lần mở). Với mỗi `.deb` đã tải và đã được apt kiểm chữ ký/hash:
1. `dpkg-deb -R`, chuyển `data/data/com.termux` → `data/data/com.aether`.
2. Thay chuỗi cùng độ dài trong mọi file chứa prefix cũ (RUNPATH, shebang, config, `preinst/postinst/...`, `conffiles`),
   chỉ khớp nguyên thành phần đường dẫn (không đụng `com.termux.api`).
3. Sửa đích symlink tuyệt đối, tạo lại `md5sums`.
4. `dpkg-deb -b` lại, kiểm `dpkg-deb -I`, thay file nguyên tử. Lỗi ở bất kỳ bước nào → apt dừng trước khi dpkg chạy (fail closed).
Không dùng `--force-*`, không sửa DB dpkg, không đổi repo, không tắt kiểm chữ ký. Cơ sở dữ liệu dpkg ghi đúng đường dẫn `com.aether`.

## Kiểm thử
- Đã chạy (máy dựng): `tests/prefix-compat/test_rewrite.sh` với `.deb` giả: đổi đường dẫn tar, shebang, binary (cùng kích thước,
  RUNPATH và chuỗi kết thúc NUL), ranh giới `com.termux.api`, symlink, maintainer scripts, `conffiles`, `md5sums`, gói không dùng prefix, idempotent. Tất cả PASS.
- Chưa chạy được (cần thiết bị): `apt update`, cài/gỡ gói thử, cài `gh` + `openssh` thật, chạy binary sau cài, cập nhật gói,
  khởi động lại app, khôi phục sau lỗi giữa chừng. Đừng coi `gh`/`openssh` là hoạt động cho tới khi chạy các bước này.

## Dùng trên thiết bị
```
pkg update
apt --fix-broken install -y     # dọn trạng thái dở dang của gh/openssh lần trước
dpkg --configure -a             # nếu apt báo còn gói chưa cấu hình
pkg install gh openssh -y
```
Gói thủ công (`dpkg -i x.deb`) không đi qua apt: chạy `$PREFIX/libexec/aetheris/rewrite-debs x.deb` trước.
Nếu lỗi: `ls $PREFIX/etc/apt/apt.conf.d/99aetheris-prefix`, rồi `apt -o Debug::pkgDPkgPM=1 install <gói>` và gửi 20 dòng đầu của lỗi.
Gói chứa trong jar/zip/whl có đường dẫn cũ bên trong file nén sẽ không được sửa.

## Sao lưu / khôi phục
Hook chỉ ghi vào `$PREFIX/var/cache/apt/archives` (xoá bằng `apt clean`, tải lại được) và thư mục tạm `$PREFIX/tmp`. Không đụng `$HOME`.
Sao lưu trước khi nâng cấp lớn: `tar -czf /sdcard/aetheris-home.tgz -C $HOME .` (cần `termux-setup-storage`).
Khôi phục: `tar -xzf /sdcard/aetheris-home.tgz -C $HOME`.

## GitHub CLI (sau khi `gh` cài được)
```
gh auth login
gh auth setup-git
gh auth status
```
`gh` lưu token vào `~/.config/gh/hosts.yml` (Android không có keyring) với quyền 600; không đặt token trong URL remote hay script.
Đăng nhập tồn tại qua các phiên chừng nào token chưa bị thu hồi/hết hạn và `$HOME` còn nguyên.
