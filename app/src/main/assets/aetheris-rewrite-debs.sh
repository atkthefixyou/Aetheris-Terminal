#!@PREFIX@/bin/bash
# Aetheris Terminal: package prefix compatibility layer.
#
# Official Termux .deb files install to /data/data/com.termux/files/usr and embed that
# path in payload (tar entries, ELF RUNPATH, shebangs, configs, symlinks) and maintainer scripts.
# Aetheris uses a different application id of the SAME length, so each package is rewritten
# after apt has verified its signature/hash and just before dpkg unpacks it:
#   - tar entry paths and symlink targets are moved to the Aetheris prefix
#   - same-length string replacement in every file that contains the old prefix
#   - md5sums regenerated; the package is rebuilt in place
# Used as apt hook (DPkg::Pre-Install-Pkgs, reads package list on stdin) or manually:
#   aetheris-rewrite-debs file.deb [file2.deb ...]   (then: dpkg -i file.deb)
# Fails closed: if a package cannot be rewritten, apt aborts before dpkg touches anything.

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
# match the old prefix only as a whole path component (not /data/data/com.termux.api)
DETECT="${OLDRE}([^.[:alnum:]_]|\$)|/${OLDPKG//./\\.}/files"
rewrite_one() {
  local deb="$1" work pkg
  work="$(mktemp -d "${TMPDIR:-$PREFIX_DIR/tmp}/aetheris-deb.XXXXXX")" || return 1
  pkg="$work/pkg"
  dpkg-deb -R "$deb" "$pkg" || { rm -rf "$work"; return 1; }

  local has_dir=0 has_str=0 has_link=0
  [ -d "$pkg/data/data/$OLDPKG" ] && has_dir=1
  grep -rqaE "$DETECT" "$pkg" 2>/dev/null && has_str=1
  if [ -n "$(find "$pkg" -type l -lname "*$OLDPKG*" -print -quit 2>/dev/null)" ]; then has_link=1; fi
  if [ $has_dir -eq 0 ] && [ $has_str -eq 0 ] && [ $has_link -eq 0 ]; then
    rm -rf "$work"; return 0   # nothing to do (already rewritten or prefix-independent)
  fi

  # 1. move tar entries to the new prefix
  if [ $has_dir -eq 1 ]; then
    if [ -e "$pkg/data/data/$NEWPKG" ]; then
      cp -a "$pkg/data/data/$OLDPKG/." "$pkg/data/data/$NEWPKG/" && rm -rf "$pkg/data/data/$OLDPKG" || { rm -rf "$work"; return 1; }
    else
      mv "$pkg/data/data/$OLDPKG" "$pkg/data/data/$NEWPKG" || { rm -rf "$work"; return 1; }
    fi
  fi

  # 2. same-length replacement inside files (not touching e.g. /data/data/com.termux.api)
  grep -rlaEZ "$DETECT" "$pkg" 2>/dev/null | xargs -0 -r sed -i \
    -e "s|${OLDRE}\\([^.[:alnum:]_]\\)|$NEW\\1|g" \
    -e "s|${OLDRE}\$|$NEW|" \
    -e "s|/${OLDPKG//./\\.}/files|/$NEWPKG/files|g" || { rm -rf "$work"; return 1; }

  # 3. absolute symlink targets
  local l t
  while IFS= read -r -d '' l; do
    t="$(readlink "$l")"
    t="${t//"$OLD"\//"$NEW"/}"
    t="${t//\/"$OLDPKG"\/files/\/"$NEWPKG"\/files}"
    ln -sfn "$t" "$l" || { rm -rf "$work"; return 1; }
  done < <(find "$pkg" -type l -lname "*$OLDPKG*" -print0)

  # 4. checksums
  if [ -f "$pkg/DEBIAN/md5sums" ]; then
    ( cd "$pkg" && find . -path ./DEBIAN -prune -o -type f -print0 | sort -z | xargs -0 -r md5sum | sed 's|  \./|  |' ) > "$work/md5sums" \
      && cp "$work/md5sums" "$pkg/DEBIAN/md5sums" || { rm -rf "$work"; return 1; }
  fi

  # 5. rebuild, verify, replace atomically
  dpkg-deb "${DEB_OPTS[@]}" -b "$pkg" "$work/new.deb" >/dev/null || { rm -rf "$work"; return 1; }
  dpkg-deb -I "$work/new.deb" >/dev/null 2>&1 || { rm -rf "$work"; return 1; }
  cp "$work/new.deb" "$deb.aetheris-tmp" && mv -f "$deb.aetheris-tmp" "$deb" || { rm -f "$deb.aetheris-tmp"; rm -rf "$work"; return 1; }
  rm -rf "$work"
  echo "aetheris: rewrote $(basename "$deb") for prefix $NEWPKG"
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
  rewrite_one "$d" || { echo "aetheris: failed to rewrite $d" >&2; rc=1; }
done
exit $rc
