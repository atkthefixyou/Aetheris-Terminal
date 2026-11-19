#!/usr/bin/env bash
# Local tests for aetheris-rewrite-debs.sh using synthetic .deb files (raw ar/tar, so any script mode can be
# represented). The hook never runs package scripts; neither does this test.
# Requires: dpkg-deb, ar, tar, GNU sed/grep/find. Usage: tests/prefix-compat/test_rewrite.sh
# HOOK_SRC=/path/to/other/version.sh runs the same tests against another version of the hook.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="${HOOK_SRC:-$HERE/../../app/src/main/assets/aetheris-rewrite-debs.sh}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mkdir -p "$T/prefix/tmp"
sed -e "s|@PREFIX@|$T/prefix|g" -e 's|@NEWPKG@|com.aether|g' "$SRC" > "$T/hook.sh"
export TMPDIR="$T/prefix/tmp"
fail=0; ok() { echo "PASS: $1"; }; bad() { echo "FAIL: $1"; fail=1; }

# mkdeb <srcdir> <out.deb>: control = srcdir/DEBIAN, data = everything else; modes taken from the filesystem.
mkdeb() {
  local d="$1" out="$2" w; w="$(mktemp -d)"
  tar -C "$d/DEBIAN" --owner=0 --group=0 -czf "$w/control.tar.gz" .
  tar -C "$d" --exclude=./DEBIAN --owner=0 --group=0 -czf "$w/data.tar.gz" .
  echo 2.0 > "$w/debian-binary"
  ( cd "$w" && ar rc "$out" debian-binary control.tar.gz data.tar.gz ); rm -rf "$w"
}
# newpkg <name>: base package tree with the official prefix
newpkg() {
  local d="$T/$1"; rm -rf "$d"; mkdir -p "$d/DEBIAN" "$d/data/data/com.termux/files/usr"/{bin,lib,etc}
  local U="$d/data/data/com.termux/files/usr"
  printf '#!/data/data/com.termux/files/usr/bin/sh\necho hi\n' > "$U/bin/tool"; chmod 755 "$U/bin/tool"
  printf 'ELF\0\0/data/data/com.termux/files/usr/lib\0tail\0/data/data/com.termux\0end' > "$U/lib/libx.so"; chmod 644 "$U/lib/libx.so"
  printf 'api=/data/data/com.termux.api/files\nkeep=com.termux.app\nend=/data/data/com.termux' > "$U/etc/keep.conf"; chmod 640 "$U/etc/keep.conf"
  ln -s /data/data/com.termux/files/usr/bin/tool "$U/bin/lnk"
  printf '\x1f\x8b\x08\x00/data/data/com.termux/files/usr/fake-container' > "$U/lib/fake.gz"
  printf 'Package: %s\nVersion: 1.0\nArchitecture: aarch64\nMaintainer: t <t@t>\nDescription: demo\n' "$1" > "$d/DEBIAN/control"
  printf '/data/data/com.termux/files/usr/etc/keep.conf\n' > "$d/DEBIAN/conffiles"; chmod 644 "$d/DEBIAN/conffiles"
  ( cd "$d" && find data -type f -print0 | xargs -0 md5sum > DEBIAN/md5sums )
  echo "$d"
}
perm() { stat -c %a "$1"; }
run_hook() { ( umask 077; printf 'VERSION 3\nAPT::Architecture=aarch64\n\n' ; printf '%s - < 1.0 %s\n' x "$1" ) | bash "$T/hook.sh"; }
leftovers() { ls "$TMPDIR" | grep -c aetheris-deb; }

echo "== maintainer script modes (hook run under umask 077, like a Termux session)"
for mode in 644 700 755 555 777; do
  d="$(newpkg m$mode)"
  printf '#!/bin/sh\nmkdir -p /data/data/com.termux/files/usr/var/demo\n' > "$d/DEBIAN/postinst"; chmod $mode "$d/DEBIAN/postinst"
  printf '#!/bin/sh\nexit 0\n' > "$d/DEBIAN/prerm"; chmod $mode "$d/DEBIAN/prerm"
  mkdeb "$d" "$T/m$mode.deb"
  run_hook "$T/m$mode.deb" >"$T/out.txt" 2>&1; rc=$?
  C="$T/c$mode"; rm -rf "$C"; mkdir "$C"; dpkg-deb -e "$T/m$mode.deb" "$C" 2>/dev/null
  want=755   # dpkg-deb -b itself writes valid scripts as 0755
  if [ $rc -eq 0 ] && [ "$(perm "$C/postinst")" = "$want" ] && [ "$(perm "$C/prerm")" = "$want" ] && [ "$(perm "$C/conffiles")" = "644" ]; then
    ok "script mode $mode accepted, written as $want (conffiles 644 untouched)"; else bad "script mode $mode (rc=$rc postinst=$(perm "$C/postinst" 2>/dev/null)): $(tail -n 2 "$T/out.txt")"; fi
done

echo "== invalid maintainer scripts are rejected, nothing modified"
d="$(newpkg symlink)"; ln -s /bin/sh "$d/DEBIAN/postinst"; mkdeb "$d" "$T/symlink.deb"; cp "$T/symlink.deb" "$T/symlink.ref"
run_hook "$T/symlink.deb" >"$T/out.txt" 2>&1; rc=$?
[ $rc -ne 0 ] && cmp -s "$T/symlink.deb" "$T/symlink.ref" && [ ! -e "$T/symlink.deb.orig" ] && [ ! -e "$T/symlink.deb.aetheris-tmp" ] && [ "$(leftovers)" = 0 ] && grep -q 'symlink.deb: maintainer script .postinst. is not a regular file' "$T/out.txt" \
  && ok "symlink postinst rejected (named in error, .deb identical, temp cleaned)" || bad "symlink postinst: rc=$rc $(cat "$T/out.txt")"
d="$(newpkg dirscript)"; mkdir "$d/DEBIAN/postrm"; mkdeb "$d" "$T/dirscript.deb"; cp "$T/dirscript.deb" "$T/dirscript.ref"
run_hook "$T/dirscript.deb" >"$T/out.txt" 2>&1; rc=$?
[ $rc -ne 0 ] && cmp -s "$T/dirscript.deb" "$T/dirscript.ref" && [ "$(leftovers)" = 0 ] && ok "directory postrm rejected" || bad "directory postrm rc=$rc"
head -c 200 "$T/m644.deb" > "$T/trunc.deb"; cp "$T/trunc.deb" "$T/trunc.ref"
run_hook "$T/trunc.deb" >"$T/out.txt" 2>&1; rc=$?
[ $rc -ne 0 ] && cmp -s "$T/trunc.deb" "$T/trunc.ref" && [ "$(leftovers)" = 0 ] && grep -q 'trunc.deb:' "$T/out.txt" && ok "corrupt package rejected with its name, temp cleaned" || bad "corrupt package rc=$rc"

echo "== content, metadata, structure (package m644, rewritten above)"
X="$T/x"; rm -rf "$X"; mkdir "$X"; ( umask 0; dpkg-deb -x "$T/m644.deb" "$X" ); C="$T/c644"
N="$X/data/data/com.aether/files/usr"
[ -x "$N/bin/tool" ] && [ ! -e "$X/data/data/com.termux" ] && ok "tar paths moved, old dir gone" || bad "paths"
grep -q '^#!/data/data/com.aether/files/usr/bin/sh' "$N/bin/tool" && ok "shebang rewritten" || bad "shebang"
[ "$(stat -c %s "$N/lib/libx.so")" = "$(stat -c %s "$T/m644/data/data/com.termux/files/usr/lib/libx.so")" ] && grep -qa '/data/data/com.aether/files/usr/lib' "$N/lib/libx.so" && ! grep -qa 'com.termux' "$N/lib/libx.so" && ok "binary: same size, RUNPATH + NUL-terminated path rewritten" || bad "binary"
grep -q 'api=/data/data/com.termux.api/files' "$N/etc/keep.conf" && grep -q 'keep=com.termux.app' "$N/etc/keep.conf" && grep -q 'end=/data/data/com.aether$' "$N/etc/keep.conf" && ok "boundary: com.termux.api untouched, EOL path rewritten" || bad "boundary"
grep -qa 'com.termux/files/usr/fake-container' "$N/lib/fake.gz" && grep -q 'warning: container left unpatched' "$T/out.txt" 2>/dev/null || grep -qa 'com.termux/files/usr/fake-container' "$N/lib/fake.gz" && ok "gzip-magic container not patched" || bad "container"
[ "$(readlink "$N/bin/lnk")" = "/data/data/com.aether/files/usr/bin/tool" ] && ok "symlink target rewritten" || bad "symlink"
[ "$(perm "$N/bin/tool")" = 755 ] && [ "$(perm "$N/lib/libx.so")" = 644 ] && [ "$(perm "$N/etc/keep.conf")" = 640 ] && ok "payload modes preserved under umask 077 (755/644/640)" || bad "payload modes $(perm "$N/bin/tool") $(perm "$N/lib/libx.so") $(perm "$N/etc/keep.conf")"
grep -q 'com.aether' "$C/postinst" && ! grep -q 'com.termux' "$C/postinst" && grep -q '^/data/data/com.aether/files/usr/etc/keep.conf' "$C/conffiles" && ok "maintainer scripts + conffiles rewritten" || bad "control files"
( cd "$X" && md5sum -c --quiet "$C/md5sums" ) && ok "md5sums valid for new payload" || bad "md5sums"
dpkg-deb -I "$T/m644.deb" >/dev/null 2>&1 && dpkg-deb -c "$T/m644.deb" >/dev/null 2>&1 && ok "package structure valid (dpkg-deb -I / -c)" || bad "structure"
[ -f "$T/m644.deb.orig" ] && dpkg-deb -c "$T/m644.deb.orig" | grep -q 'data/data/com.termux/' && ok "original kept as .deb.orig (restorable)" || bad "backup"

echo "== other behaviours"
d="$T/pkg2"; mkdir -p "$d/DEBIAN" "$d/usr/share/x"; echo x > "$d/usr/share/x/f"
printf 'Package: plain\nVersion: 1.0\nArchitecture: all\nMaintainer: t <t@t>\nDescription: plain\n' > "$d/DEBIAN/control"
mkdeb "$d" "$T/plain.deb"; cp "$T/plain.deb" "$T/plain.ref"; run_hook "$T/plain.deb" >/dev/null 2>&1
cmp -s "$T/plain.deb" "$T/plain.ref" && [ ! -e "$T/plain.deb.orig" ] && ok "prefix-independent package untouched" || bad "plain package changed"
cp "$T/m755.deb" "$T/m755.once"; run_hook "$T/m755.deb" >"$T/out.txt" 2>&1; cmp -s "$T/m755.deb" "$T/m755.once" && ! grep -q rewrote "$T/out.txt" && ok "idempotent (second run: no rewrite, no change)" || bad "idempotent"
d="$(newpkg noback)"; mkdeb "$d" "$T/noback.deb"; ( export AETHERIS_KEEP_ORIG=0; run_hook "$T/noback.deb" >/dev/null 2>&1 ); [ ! -e "$T/noback.deb.orig" ] && ok "AETHERIS_KEEP_ORIG=0 skips backup" || bad "keep_orig"
[ "$(leftovers)" = 0 ] && ok "no temp directories left behind" || bad "temp leftovers"
if [ -n "${KEEP:-}" ]; then cp -r "$T" /tmp/kept-test; fi
exit $fail
