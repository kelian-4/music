#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Build an unsigned Release IPA for sideloading or signing with your own team.

Usage:
  scripts/package-ios-ipa.sh <version> [--output-dir DIR] [--overwrite]

The script archives the iOS app with code signing disabled, removes embedded
provisioning profiles, and writes Amber-VERSION-buildBUILD-ios-unsigned.ipa.
USAGE
}
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }
if [[ $# -eq 1 && ( "$1" == '-h' || "$1" == '--help' ) ]]; then usage; exit 0; fi
[[ $# -ge 1 ]] || { usage >&2; exit 2; }
version="$1"; shift
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail 'version must look like 1.0.0'
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="$repo_root/build/release"
overwrite=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) [[ $# -ge 2 ]] || fail '--output-dir needs a path'; output_dir="$2"; shift 2 ;;
    --overwrite) overwrite=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "Unknown option: $1" ;;
  esac
done
command -v xcodebuild >/dev/null 2>&1 || fail 'xcodebuild is required'
command -v ditto >/dev/null 2>&1 || fail 'ditto is required'
command -v zipinfo >/dev/null 2>&1 || fail 'zipinfo is required'
command -v codesign >/dev/null 2>&1 || fail 'codesign is required'
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
stage="$(mktemp -d "${TMPDIR:-/tmp}/amber-ipa.XXXXXX")"
trap 'rm -rf "$stage"' EXIT
archive="$stage/Amber.xcarchive"
app="$archive/Products/Applications/Amber.app"
staged_app="$stage/package/Payload/Amber.app"

# scripts/release-apple.sh passes the revision it checked clean, so the IPA and
# the Mac archive carry the same stamp even if the tree changes mid-build.
build_revision="${AMBER_BUILD_REVISION:-}"
if [[ -z "$build_revision" ]]; then
build_revision="$(git -C "$repo_root" rev-parse --short=12 HEAD)"
if ! git -C "$repo_root" diff --quiet HEAD -- || [[ -n "$(git -C "$repo_root" ls-files --others --exclude-standard)" ]]; then build_revision="$build_revision-dirty"; fi
fi
xcodebuild \
  -project "$repo_root/AppleApp/Amber.xcodeproj" \
  -scheme Amber \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -archivePath "$archive" \
  -derivedDataPath "$stage/DerivedData" \
  archive \
  AMBER_BUILD_REVISION="$build_revision" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY=
[[ -d "$app" ]] || fail "archive did not contain Amber.app: $app"
plist="$app/Info.plist"
short_version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$plist")"
[[ "$short_version" == "$version" ]] || fail "archive is version $short_version, not requested version $version"
build_version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$plist")"
[[ "$build_version" =~ ^[0-9]+$ ]] || fail 'archive has no numeric build number'
ipa="$output_dir/Amber-$version-build$build_version-ios-unsigned.ipa"
[[ ! -e "$ipa" || "$overwrite" -eq 1 ]] || fail "IPA already exists: $ipa (pass --overwrite to replace it)"
mkdir -p "$stage/package/Payload"
ditto "$app" "$stage/package/Payload/Amber.app"
find "$stage/package/Payload/Amber.app" -name embedded.mobileprovision -type f -delete
# A release IPA is deliberately unsigned; don't leave export/archive profiles
# behind even if Xcode emitted one for a nested extension.
find "$stage/package/Payload/Amber.app" -name embedded.provisionprofile -type f -delete
entitlements_plist="$stage/app-groups.entitlements"
cat > "$entitlements_plist" <<'ENTITLEMENTS'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.security.application-groups</key>
  <array>
    <string>group.app.amber.Amber</string>
  </array>
</dict>
</plist>
ENTITLEMENTS
# Sideloading tools read the app-group entitlement from the binaries to
# register the group and re-sign the widget with it; an unsigned build carries
# none, so the widget and the app end up with no shared container.
while IFS= read -r -d '' nested; do
  codesign --force --sign - "$nested"
done < <(find "$staged_app" \( -name '*.framework' -o -name '*.dylib' \) -not -path '*/PlugIns/*' -print0)
if [[ -d "$staged_app/PlugIns/AmberWidget.appex" ]]; then
  codesign --force --sign - --entitlements "$entitlements_plist" "$staged_app/PlugIns/AmberWidget.appex"
fi
codesign --force --sign - --entitlements "$entitlements_plist" "$staged_app"
# zip updates an existing archive instead of clearing entries that disappeared
# from a rebuilt app, so start fresh when the caller explicitly allows overwrite.
rm -f "$ipa"
(cd "$stage/package" && /usr/bin/zip -qry "$ipa" Payload)
zipinfo -1 "$ipa" > "$stage/ipa-list.txt"
grep -Fxq 'Payload/Amber.app/Info.plist' "$stage/ipa-list.txt" || fail 'IPA archive is missing Payload/Amber.app'
grep -Fxq 'Payload/Amber.app/Amber' "$stage/ipa-list.txt" || fail 'IPA archive is missing the Amber executable'
if [[ -d "$app/PlugIns/AmberWidget.appex" ]]; then
  grep -E '^Payload/Amber\.app/PlugIns/AmberWidget\.appex(/|$)' "$stage/ipa-list.txt" >/dev/null || fail 'IPA archive is missing AmberWidget.appex'
fi
if grep -E '(^|/)embedded\.(mobileprovision|provisionprofile)$' "$stage/ipa-list.txt" >/dev/null; then
  fail 'IPA archive contains an embedded provisioning profile'
fi
signature_info="$stage/codesign-info.txt"
if codesign -dv --verbose=4 "$staged_app" >"$signature_info" 2>&1; then
  grep -Fq 'Signature=adhoc' "$signature_info" || fail 'staged app has a non-ad-hoc signature'
  if grep -q '^Authority=' "$signature_info"; then fail 'staged app unexpectedly has a signing authority'; fi
else
  grep -Fq 'code object is not signed at all' "$signature_info" || fail 'could not confirm staged app is unsigned or ad-hoc signed'
fi
printf 'Created unsigned IPA: %s\n' "$ipa"
printf 'No developer signature or provisioning profile is included. iCloud sync requires signing with your own team and the relevant iCloud capabilities.\n'
