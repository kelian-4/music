#!/bin/bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
fw="$repo/AppleApp/Frameworks/AmberShared.xcframework/macos-arm64"
bin="$(mktemp -d)/playback-settings-verify"
xcrun swiftc -module-cache-path /tmp/amber-swift-module-cache -o "$bin" \
  -F "$fw" -framework AmberShared \
  -Xlinker -rpath -Xlinker "$fw/AmberShared.framework/Versions/A" \
  "$repo/scripts/settings-verify/main.swift"
for mode in missing off on; do "$bin" "$mode"; done
