#!@PREFIX@/bin/bash
# Aetheris Terminal: package prefix compatibility layer.
#
# Official Termux .deb files install to /data/data/com.termux/files/usr and embed that
# path in the payload (tar entries, ELF RUNPATH, shebangs, configs, symlinks) and in maintainer
# scripts. Aetheris uses an application id of the SAME length, so each package is rewritten after
# apt has verified its signature/hash and just before dpkg unpacks it:
#   - tar entry paths and symlink targets are moved to the Aetheris prefix
#   - same-length replacement of the old prefix (whole path component only) in text/binary files
#   - maintainer scripts (preinst postinst prerm postrm config) are validated and, if their mode
#     is outside what dpkg-deb accepts (0555..0775), set to 0755; nothing else is chmod'ed
#   - md5sums regenerated; package rebuilt, verified, original kept as <deb>.orig, then replaced
# Package scripts are NEVER executed here. Fails closed: on any error apt aborts before dpkg runs.
#
# Hook mode (apt DPkg::Pre-Install-Pkgs, package list on stdin) or manual:
#   rewrite-debs file.deb [file2.deb ...]
# Env: AETHERIS_KEEP_ORIG=0 disables the <deb>.orig backup. AETHERIS_VERBOSE=1 lists changes.

OLDPKG="com.termux"
NEWPKG="@NEWPKG@"
PREFIX_DIR="@PREFIX@"

[ "$OLDPKG" = "$NEWPKG" ] && exit 0
if [ "${#OLDPKG}" -ne "${#NEWPKG}" ]; then
  echo "aetheris: package id '$NEWPKG' must have the same length as '$OLDPKG'" >&2
  exit 1
fi
OLD="/data/data/$OLDPKG"
NEW="/data/data/$NEWPKG"
export LC_ALL=C

command -v dpkg-deb >/dev/null 2>&1 || { echo "aetheris: dpkg-deb not found" >&2; exit 1; }

DEB_OPTS=(-Zgzip -z3)
dpkg-deb --help 2>&1 | grep -q -- '--root-owner-group' && DEB_OPTS=(--root-owner-group "${DEB_OPTS[@]}")

OLDRE="${OLD//./\\.}"
OLDPKGRE="${OLDPKG//./\\.}"
# old prefix only as a whole path component (never /data/data/com.termux.api)
DETECT="${OLDRE}([^.[:alnum:]_]|\$)|/${OLDPKGRE}/files"
MAINT_SCRIPTS=(preinst postinst prerm postrm config)

WORK=""
cleanup() { [ -n "$WORK" ] && rm -rf "$WORK"; WORK=""; }
trap cleanup EXIT
trap 'cleanup; exit 130' INT TERM HUP

CUR=""
fail() { echo "aetheris: $(basename "$CUR"): $*" >&2; cleanup; return 1; }
say() { [ "${AETHERIS_VERBOSE:-0}" = 1 ] && echo "aetheris: $(basename "$CUR"): $*"; return 0; }

# dpkg-deb -b accepts a maintainer script only if (mode & 0555) == 0555 and mode has no 0002/setuid/setgid/sticky.
script_mode_ok() { local m=$((8#$1)); (( (m & 8#555) == 8#555 && (m & 8#7002) == 0 )); }

# Compressed/archive containers: a plain-text match inside would be coincidence and patching would corrupt them.
is_container() {
  local magic
  magic="$(head -c 4 "$1" 2>/dev/null | od -An -tx1 | tr -d ' \n')"
  case "$magic" in 1f8b*|fd377a58|504b0304|28b52ffd|425a68*) return 0 ;; esac
  return 1
}

# Fill TODO (patchable) and SKIPPED (archive containers) with files that mention the old prefix.
TODO=(); SKIPPED=()
collect() {
  local f; TODO=(); SKIPPED=()
  while IFS= read -r -d '' f; do
    [ -f "$f" ] && [ ! -L "$f" ] || continue
    if is_container "$f"; then SKIPPED+=("$f"); else TODO+=("$f"); fi
  done < <(grep -rlaEZ "$DETECT" "$1" 2>/dev/null)
}

rewrite_one() {
  local deb="$1" pkg
  CUR="$deb"
  [ -f "$deb" ] && [ ! -L "$deb" ] || { fail "not a regular file"; return 1; }
  WORK="$(mktemp -d "${TMPDIR:-$PREFIX_DIR/tmp}/aetheris-deb.XXXXXX")" || { fail "cannot create temp dir"; return 1; }
  pkg="$WORK/pkg"

  # umask 0: extract with the exact modes stored in the package (Termux sessions use umask 077,
  # which would otherwise turn 0755 into 0700 and 0644 into 0600 for every file).
  ( umask 0; dpkg-deb -R "$deb" "$pkg" ) || { fail "dpkg-deb -R failed (corrupt or unreadable package)"; return 1; }
  [ -f "$pkg/DEBIAN/control" ] || { fail "package has no DEBIAN/control"; return 1; }

  collect "$pkg"

  local has_dir=0 has_link=0
  [ -d "$pkg/data/data/$OLDPKG" ] && has_dir=1
  [ -n "$(find "$pkg" -type l -lname "*$OLDPKG*" -print -quit 2>/dev/null)" ] && has_link=1
  if [ $has_dir -eq 0 ] && [ ${#TODO[@]} -eq 0 ] && [ $has_link -eq 0 ]; then
    cleanup; return 0   # already rewritten or prefix-independent: leave the .deb untouched
  fi

  # 1. maintainer scripts: validate type, normalize ONLY a mode dpkg-deb would reject
  local s m
  for s in "${MAINT_SCRIPTS[@]}"; do
    f="$pkg/DEBIAN/$s"
    if [ -L "$f" ] || { [ -e "$f" ] && [ ! -f "$f" ]; }; then
      fail "maintainer script '$s' is not a regular file (symlink/directory): refusing"; return 1
    fi
    [ -f "$f" ] || continue
    m="$(stat -c %a "$f")" || { fail "cannot stat maintainer script '$s'"; return 1; }
    if ! script_mode_ok "$m"; then
      chmod 0755 "$f" || { fail "cannot fix mode of '$s'"; return 1; }
      say "maintainer script $s mode $m -> 755"
    fi
  done
  # control directory itself must be 0755..0775
  chmod 0755 "$pkg/DEBIAN" || { fail "cannot fix DEBIAN directory mode"; return 1; }

  # 2. move tar entries to the new prefix
  if [ $has_dir -eq 1 ]; then
    if [ -e "$pkg/data/data/$NEWPKG" ]; then
      cp -a "$pkg/data/data/$OLDPKG/." "$pkg/data/data/$NEWPKG/" && rm -rf "$pkg/data/data/$OLDPKG" \
        || { fail "cannot merge $OLDPKG tree into $NEWPKG"; return 1; }
    else
      mv "$pkg/data/data/$OLDPKG" "$pkg/data/data/$NEWPKG" || { fail "cannot move payload to new prefix"; return 1; }
    fi
  fi

  # 3. same-length replacement in files that contain the old prefix (containers skipped)
  collect "$pkg"
  local n=0 f
  for f in "${TODO[@]}"; do
    sed -i \
      -e "s|${OLDRE}\\([^.[:alnum:]_]\\)|$NEW\\1|g" \
      -e "s|${OLDRE}\$|$NEW|" \
      -e "s|/${OLDPKGRE}/files|/$NEWPKG/files|g" "$f" || { fail "sed failed on ${f#$pkg/}"; return 1; }
    n=$((n + 1)); say "patched ${f#$pkg/}"
  done
  for f in "${SKIPPED[@]}"; do echo "aetheris: $(basename "$CUR"): warning: container left unpatched: ${f#$pkg/}" >&2; done

  # 4. absolute symlink targets
  local l t
  while IFS= read -r -d '' l; do
    t="$(readlink "$l")"
    t="${t//"$OLD"\//"$NEW"/}"
    t="${t//\/"$OLDPKG"\/files/\/"$NEWPKG"\/files}"
    ln -sfn "$t" "$l" || { fail "cannot rewrite symlink ${l#$pkg/}"; return 1; }
  done < <(find "$pkg" -type l -lname "*$OLDPKG*" -print0)

  # 5. nothing (except skipped containers) may still reference the old prefix
  collect "$pkg"
  if [ ${#TODO[@]} -gt 0 ]; then
    fail "old prefix still present after rewrite in: $(for f in "${TODO[@]:0:3}"; do printf '%s ' "${f#$pkg/}"; done)"; return 1
  fi

  # 6. checksums
  if [ -f "$pkg/DEBIAN/md5sums" ]; then
    ( cd "$pkg" && find . -path ./DEBIAN -prune -o -type f -print0 | sort -z | xargs -0 -r md5sum | sed 's|  \./|  |' ) > "$WORK/md5sums" \
      && cp "$WORK/md5sums" "$pkg/DEBIAN/md5sums" || { fail "cannot regenerate md5sums"; return 1; }
  fi

  # 7. rebuild (dpkg-deb's own validation stays ON), verify, back up, replace atomically
  local err
  err="$(dpkg-deb "${DEB_OPTS[@]}" -b "$pkg" "$WORK/new.deb" 2>&1 >/dev/null)" \
    || { fail "dpkg-deb -b rejected the rewritten package: $(echo "$err" | head -n 2 | tr '\n' ' ')"; return 1; }
  dpkg-deb -I "$WORK/new.deb" >/dev/null 2>&1 && dpkg-deb -c "$WORK/new.deb" >/dev/null 2>&1 \
    || { fail "rewritten package failed verification"; return 1; }
  if [ "${AETHERIS_KEEP_ORIG:-1}" = 1 ] && [ ! -e "$deb.orig" ]; then
    cp -p "$deb" "$deb.orig" || { fail "cannot back up original"; return 1; }
  fi
  cp "$WORK/new.deb" "$deb.aetheris-tmp" && chmod --reference="$deb" "$deb.aetheris-tmp" 2>/dev/null
  mv -f "$deb.aetheris-tmp" "$deb" || { rm -f "$deb.aetheris-tmp"; fail "cannot replace package"; return 1; }
  cleanup
  echo "aetheris: rewrote $(basename "$deb") for prefix $NEWPKG ($n files patched)"
}

debs=()
if [ $# -gt 0 ]; then
  debs=("$@")
else
  while IFS= read -r line; do
    for tok in $line; do
      case "$tok" in *.deb) [ -f "$tok" ] && debs+=("$tok") ;; esac
    done
  done
fi

rc=0
for d in "${debs[@]}"; do
  rewrite_one "$d" || rc=1
done
exit $rc
