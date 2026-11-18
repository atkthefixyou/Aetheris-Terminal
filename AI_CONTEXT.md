# AI_CONTEXT — Aetheris Terminal

Fork Termux (`termux-app` 8629e63, v0.118.0 baseline). Mục tiêu: cài song song Termux gốc, giao diện riêng, giữ tương thích.

## Đã xác minh
- Module: `app`, `terminal-emulator`, `terminal-view`, `termux-shared`. JDK 17, compileSdk 36, targetSdk 28, minSdk 21, NDK 29.0.14206865.
- Bootstrap tải lúc build từ `termux-packages`, tự patch prefix `com.termux`→`com.aether` (cùng độ dài) trong `app/build.gradle` (`patchBootstrap`).
- CI: `.github/workflows/debug_build.yml` → Release `latest` → `aetheris.apk` (direct link, không zip).

## Đã đổi (commit 23652df)
- `applicationId` = `com.aether` (namespace Java vẫn `com.termux`).
- `TERMUX_PACKAGE_NAME`, `TERMUX_APP_NAME`=Aetheris: `app/build.gradle`, `TermuxConstants.java`, 2 file `strings.xml`, `shortcuts.xml`.
- Prefix: `/data/data/com.aether/files/usr`.

## Rủi ro mở
- Gói cài thêm qua `pkg` từ repo Termux vẫn hardcode `com.termux` → có thể hỏng. Chưa kiểm chứng.
- Chưa cài/chạy APK trên thiết bị; chưa có test hồi quy.
- `sharedUserId` = package mới; chưa kiểm tra plugin Termux:* (khác package/chữ ký).

## Bước tiếp theo
1. Tải APK `apt-android-7` universal từ Actions, cài trên HONOR, mở app, chạy shell, thử `pkg update`.
2. Nếu bootstrap lỗi: quyết định dựng bootstrap riêng cho prefix mới.
3. Sau khi ổn: giai đoạn 2 (theme).

## Giao diện (commit 556794b, 5f75f60, 793a804)
- Header banner, theme màu, Extra Keys 2 hàng, footer, font Cascadia Mono mặc định (assets/fonts; `~/.termux/font.ttf` vẫn ưu tiên).
- INPUT/OUTPUT: `AetherisPanel.java` nhận OSC 133 (A/B/C/D) và OSC 6973 (cwd;branch) qua `TerminalSessionClient.onShellIntegrationEvent` (hook trong `TerminalEmulator.doOsc`).
- Script shell: `assets/aetheris-shell-integration.sh` → `$PREFIX/etc/profile.d/aetheris.sh`. zsh cần thêm vào `~/.zshrc`: `. $PREFIX/etc/profile.d/aetheris.sh`.
- Chưa kiểm thử trên thiết bị: profile.d có được source không, Copy Output với output dài/cuộn.

## Nhập lệnh qua thẻ INPUT (commit a83478b)
- `AetherisPanel`: EditText gửi `line\r` vào PTY; terminal không focus (trừ khi alt-buffer: vim/htop → gõ trực tiếp). Extra keys UP/DOWN/LEFT/RIGHT/HOME/END/DEL điều khiển ô INPUT; TAB bị bỏ qua; CTRL+ký tự gửi mã điều khiển.
- Script `aetheris.sh` (source cuối `.zshrc`/`.bashrc`): prompt rỗng, tắt echo, tắt zle/readline. `AETHERIS_PLAIN=1` để giữ prompt thường.
- Font Cascadia Code (assets), chữ mặc định `#86A9DC`.
- Chưa kiểm thử trên thiết bị: focus/IME, Ctrl+C, history, chuyển chế độ vim.
