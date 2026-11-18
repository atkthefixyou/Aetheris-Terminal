#!/usr/bin/env bash
# Local test of aetheris-rewrite-debs.sh using a synthetic .deb with the official Termux prefix.
# Requires: dpkg-deb, GNU sed/grep/find. Usage: tests/prefix-compat/test_rewrite.sh
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../../app/src/main/assets/aetheris-rewrite-debs.sh"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mkdir -p "$T/prefix/tmp" "$T/pkg/DEBIAN" "$T/pkg/data/data/com.termux/files/usr"/{bin,lib,etc}
sed -e "s|@PREFIX@|$T/prefix|g" -e 's|@NEWPKG@|com.aether|g' "$SRC" > "$T/hook.sh"
U="$T/pkg/data/data/com.termux/files/usr"
printf '#!/data/data/com.termux/files/usr/bin/sh\necho hi\n' > "$U/bin/tool"; chmod 755 "$U/bin/tool"
printf 'ELF\0\0/data/data/com.termux/files/usr/lib\0tail\0/data/data/com.termux\0end' > "$U/lib/libx.so"
printf 'api=/data/data/com.termux.api/files\nkeep=com.termux.app\nend=/data/data/com.termux' > "$U/etc/keep.conf"
ln -s /data/data/com.termux/files/usr/bin/tool "$U/bin/lnk"
printf 'Package: demo\nVersion: 1.0\nArchitecture: aarch64\nMaintainer: t <t@t>\nDescription: demo\n' > "$T/pkg/DEBIAN/control"
printf '#!/bin/sh\nmkdir -p /data/data/com.termux/files/usr/var/demo\n' > "$T/pkg/DEBIAN/postinst"; chmod 755 "$T/pkg/DEBIAN/postinst"
printf '/data/data/com.termux/files/usr/etc/keep.conf\n' > "$T/pkg/DEBIAN/conffiles"
( cd "$T/pkg" && find data -type f -print0 | xargs -0 md5sum > DEBIAN/md5sums )
# second package without prefix usage
mkdir -p "$T/pkg2/DEBIAN" "$T/pkg2/usr/share/x"; echo x > "$T/pkg2/usr/share/x/f"
printf 'Package: plain\nVersion: 1.0\nArchitecture: all\nMaintainer: t <t@t>\nDescription: plain\n' > "$T/pkg2/DEBIAN/control"
dpkg-deb -Zgzip -b "$T/pkg" "$T/demo.deb" >/dev/null && dpkg-deb -Zgzip -b "$T/pkg2" "$T/plain.deb" >/dev/null || { echo "FAIL: cannot build fixtures"; exit 1; }
cp "$T/plain.deb" "$T/plain.orig"
size_before=$(stat -c %s "$U/lib/libx.so")

fail=0; ok() { echo "PASS: $1"; }; bad() { echo "FAIL: $1"; fail=1; }
printf 'VERSION 3\nAPT::Architecture=aarch64\n\ndemo - < 1.0 %s\nplain - < 1.0 %s\n' "$T/demo.deb" "$T/plain.deb" | bash "$T/hook.sh" && ok "hook exit 0" || bad "hook exit"

X="$T/x"; mkdir "$X"; dpkg-deb -x "$T/demo.deb" "$X"; C="$T/c"; mkdir "$C"; dpkg-deb -e "$T/demo.deb" "$C"
N="$X/data/data/com.aether/files/usr"
[ -x "$N/bin/tool" ] && [ ! -e "$X/data/data/com.termux" ] && ok "tar paths moved, old dir gone" || bad "paths"
grep -q '^#!/data/data/com.aether/files/usr/bin/sh' "$N/bin/tool" && ok "shebang rewritten" || bad "shebang"
[ "$(stat -c %s "$N/lib/libx.so")" = "$size_before" ] && grep -qa '/data/data/com.aether/files/usr/lib' "$N/lib/libx.so" && ! grep -qa 'com.termux' "$N/lib/libx.so" && ok "binary: same size, RUNPATH + NUL-terminated path rewritten" || bad "binary"
grep -q 'api=/data/data/com.termux.api/files' "$N/etc/keep.conf" && grep -q 'keep=com.termux.app' "$N/etc/keep.conf" && grep -q 'end=/data/data/com.aether$' "$N/etc/keep.conf" && ok "boundary: com.termux.api untouched, EOL path rewritten" || bad "boundary"
[ "$(readlink "$N/bin/lnk")" = "/data/data/com.aether/files/usr/bin/tool" ] && ok "symlink target rewritten" || bad "symlink"
grep -q 'com.aether' "$C/postinst" && ! grep -q 'com.termux' "$C/postinst" && grep -q '^/data/data/com.aether/files/usr/etc/keep.conf' "$C/conffiles" && ok "maintainer scripts + conffiles rewritten" || bad "control files"
( cd "$X" && sed 's|  |  |' "$C/md5sums" | md5sum -c --quiet - ) && ok "md5sums valid for new payload" || bad "md5sums"
cmp -s "$T/plain.deb" "$T/plain.orig" && ok "prefix-independent package left untouched" || bad "plain package changed"
cp "$T/demo.deb" "$T/demo.once"; printf '%s\n' "$T/demo.deb" | bash "$T/hook.sh" && cmp -s "$T/demo.deb" "$T/demo.once" && ok "idempotent" || bad "idempotent"
exit $fail
