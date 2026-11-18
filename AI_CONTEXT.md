# AI_CONTEXT — Aetheris Terminal

Fork Termux (`termux-app` 8629e63, v0.118.0 baseline). Mục tiêu: cài song song Termux gốc, giao diện riêng, giữ tương thích.

## Đã xác minh
- Module: `app`, `terminal-emulator`, `terminal-view`, `termux-shared`. JDK 17, compileSdk 36, targetSdk 28, minSdk 21, NDK 29.0.14206865.
- Bootstrap tải lúc build từ `termux-packages` (`apt-android-7` / `apt-android-5`).
- CI: `.github/workflows/debug_build.yml`, chạy trên `main`, build thành công (run 44280955871), APK ở Artifacts.

## Đã đổi (commit 23652df)
- `applicationId` = `com.aetheris.terminal` (namespace Java vẫn `com.termux`).
- `TERMUX_PACKAGE_NAME`, `TERMUX_APP_NAME`=Aetheris: `app/build.gradle`, `TermuxConstants.java`, 2 file `strings.xml`, `shortcuts.xml`.
- Prefix: `/data/data/com.aetheris.terminal/files/usr`.

## Rủi ro mở
- Bootstrap chính thức dựng cho prefix `com.termux`: chưa kiểm chứng bootstrap/`pkg` chạy với prefix mới (có thể hỏng shebang, RPATH, config).
- Chưa cài/chạy APK trên thiết bị; chưa có test hồi quy.
- `sharedUserId` = package mới; chưa kiểm tra plugin Termux:* (khác package/chữ ký).

## Bước tiếp theo
1. Tải APK `apt-android-7` universal từ Actions, cài trên HONOR, mở app, chạy shell, thử `pkg update`.
2. Nếu bootstrap lỗi: quyết định dựng bootstrap riêng cho prefix mới.
3. Sau khi ổn: giai đoạn 2 (theme).
