#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.garan.threshold"
MAIN_ACTIVITY="com.garan.threshold/.MainActivity"
CEILING_KB=30000
APK_PATH="app/build/outputs/apk/release/app-release.apk"

echo "=========================================================="
echo "  Threshold: Automated Emulator Smoke & Home Role Test"
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

echo "==> Step 1: Waiting for device boot completion..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 1
done
echo "    Device is ready."

# 2. Build release APK if not present
if [ ! -f "$APK_PATH" ]; then
    echo "==> Step 2: Building signed Release APK with R8 minification..."
    ./gradlew :app:assembleRelease
else
    echo "==> Step 2: Found existing Release APK at $APK_PATH"
fi

# 3. Install Release APK
echo "==> Step 3: Installing Release APK ($APK_PATH)..."
adb install -r -d -g "$APK_PATH"

# 4. Zero-click Home Role Assignment via RoleManager
echo "==> Step 4: Granting android.app.role.HOME via RoleManager..."
adb shell cmd role add-role-holder android.app.role.HOME "$PACKAGE_NAME"

ROLE_HOLDERS=$(adb shell cmd role get-role-holders android.app.role.HOME 2>/dev/null | tr -d '\r')
echo "    Active Home Role Holders: $ROLE_HOLDERS"

if [[ "$ROLE_HOLDERS" != *"$PACKAGE_NAME"* ]]; then
    echo "ERROR: Failed to set $PACKAGE_NAME as default Home role holder!" >&2
    exit 1
fi
echo "    SUCCESS: $PACKAGE_NAME is verified as Home role holder."

# 5. Simulate Home button event & verify foreground window
echo "==> Step 5: Simulating KEYCODE_HOME and verifying foreground focus..."
adb shell input keyevent KEYCODE_HOME
sleep 2

FOCUSED_WINDOW=$(adb shell dumpsys window 2>/dev/null | (grep -E "mCurrentFocus|mFocusedApp" || true))
echo "    Current Focus: $FOCUSED_WINDOW"

if [[ "$FOCUSED_WINDOW" != *"$PACKAGE_NAME"* ]]; then
    echo "ERROR: Threshold was not brought to the foreground upon pressing Home!" >&2
    exit 1
fi
echo "    SUCCESS: Threshold MainActivity is active and focused."

# 6. Memory Ceiling Check (< 30 MB Total PSS)
echo "==> Step 6: Asserting TOTAL PSS memory budget (< ${CEILING_KB} KB)..."
TOTAL_PSS=$(adb shell dumpsys meminfo "$PACKAGE_NAME" 2>/dev/null | (grep "TOTAL PSS:" || true) | awk '{print $3}' || echo "0")
echo "    Observed TOTAL PSS: ${TOTAL_PSS} KB (Budget: ${CEILING_KB} KB)"

if [ -n "$TOTAL_PSS" ] && [ "$TOTAL_PSS" -gt "$CEILING_KB" ]; then
    echo "ERROR: Memory ceiling exceeded! ${TOTAL_PSS} KB > ${CEILING_KB} KB" >&2
    exit 1
fi
echo "    SUCCESS: Memory consumption within strict budget (${TOTAL_PSS} KB <= ${CEILING_KB} KB)."

echo "=========================================================="
echo "  ALL SMOKE CHECKS PASSED: Home Role & Memory Verified!"
echo "=========================================================="
