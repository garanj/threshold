#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "  Threshold: Automated Functional Instrumented Tests"
echo "=========================================================="

if ! command -v adb &> /dev/null; then
    echo "ERROR: adb command not found in PATH." >&2
    exit 1
fi

# 1. Check for running device/emulator
DEVICE_COUNT=$(adb devices | grep -v "List" | (grep "device$" || true) | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "ERROR: No active Android device or emulator detected via adb." >&2
    echo "Tip: Start an emulator first via 'android emulator start' or Android Studio." >&2
    exit 1
fi

echo "==> Step 1: Waiting for device boot and User 0 unlock completion..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 1
done
for _ in {1..30}; do
    HOME_HOLDERS=$(adb shell cmd role get-role-holders android.app.role.HOME 2>/dev/null | tr -d '\r' || true)
    FOCUSED_WIN=$(adb shell dumpsys window 2>/dev/null | (grep -E "mCurrentFocus|mFocusedApp" || true))
    if [ -n "$HOME_HOLDERS" ] && [[ "$FOCUSED_WIN" != *"FallbackHome"* ]]; then
        break
    fi
    sleep 2
done
adb shell am wait-for-broadcast-idle || true
adb shell settings put global window_animation_scale 0.0 || true
adb shell settings put global transition_animation_scale 0.0 || true
adb shell settings put global animator_duration_scale 0.0 || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
echo "    Device is ready."

echo "==> Step 2: Assembling fixture APK..."
./gradlew :fixtures:dummy-app:assembleDebug

echo "==> Step 3: Staging fixture APK to /data/local/tmp/dummy-app.apk..."
adb push fixtures/dummy-app/build/outputs/apk/debug/dummy-app-debug.apk /data/local/tmp/dummy-app.apk

echo "==> Step 4: Running connected Android instrumented tests..."
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --continue --info

echo "=========================================================="
echo "  ALL FUNCTIONAL INSTRUMENTED TESTS PASSED!"
echo "=========================================================="
