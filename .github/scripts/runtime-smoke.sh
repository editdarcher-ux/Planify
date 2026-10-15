#!/usr/bin/env bash
set -euo pipefail

APK="clean-app/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.planify.app"

test -f "$APK"
adb install -r "$APK"
adb logcat -c

# Cold start and verify that the app stays alive.
adb shell am force-stop "$PKG" || true
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 5

if ! adb shell pidof "$PKG" >/dev/null; then
  echo 'Planify did not remain alive after cold start.'
  adb logcat -d -v threadtime | tail -n 300
  exit 20
fi

# Exercise the app with package-scoped input events. This avoids relying on
# accessibility labels that can vary across Android/emulator versions.
adb shell monkey \
  -p "$PKG" \
  --throttle 150 \
  --pct-syskeys 0 \
  --pct-appswitch 0 \
  --ignore-security-exceptions \
  30 >/tmp/planify-monkey.txt 2>&1 || {
    cat /tmp/planify-monkey.txt
    exit 21
  }

sleep 3

if ! adb shell pidof "$PKG" >/dev/null; then
  echo 'Planify process died during runtime smoke test.'
  cat /tmp/planify-monkey.txt
  adb logcat -d -v threadtime | tail -n 300
  exit 22
fi

# Fail if AndroidRuntime recorded a fatal crash for Planify during the test.
adb logcat -d -v threadtime AndroidRuntime:E '*:S' >/tmp/planify-androidruntime.txt || true
if grep -q "Process: $PKG" /tmp/planify-androidruntime.txt; then
  echo 'Fatal AndroidRuntime crash detected for Planify.'
  cat /tmp/planify-androidruntime.txt
  exit 23
fi

# Confirm a second cold launch also works.
adb shell am force-stop "$PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
adb shell pidof "$PKG" >/dev/null

echo 'Runtime smoke passed: install, cold launch, input exercise, crash check and relaunch succeeded.'
