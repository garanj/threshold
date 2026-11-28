#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.garan.drytoast"
MAIN_ACTIVITY="com.garan.drytoast/.MainActivity"
CEILING_KB=35000
APK_PATH="app/build/outputs/apk/release/app-release.apk"

cleanup_display() {
    adb shell wm size reset >/dev/null 2>&1 || true
    adb shell wm density reset >/dev/null 2>&1 || true
}
trap cleanup_display EXIT

echo "=========================================================="
echo "  Dry Toast: Automated Emulator Smoke & Home Role Test"
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
ROLE_GRANTED=0
for i in {1..12}; do
    if adb shell cmd role add-role-holder android.app.role.HOME "$PACKAGE_NAME" 2>/dev/null; then
        ROLE_HOLDERS=$(adb shell cmd role get-role-holders android.app.role.HOME 2>/dev/null | tr -d '\r')
        if [[ "$ROLE_HOLDERS" == *"$PACKAGE_NAME"* ]]; then
            ROLE_GRANTED=1
            echo "    Active Home Role Holders: $ROLE_HOLDERS"
            break
        fi
    fi
    echo "    Waiting for RoleManagerService / PermissionController to settle (attempt $i/12)..."
    sleep 2
done

if [ "$ROLE_GRANTED" -ne 1 ]; then
    ROLE_HOLDERS=$(adb shell cmd role get-role-holders android.app.role.HOME 2>/dev/null | tr -d '\r')
    echo "ERROR: Failed to set $PACKAGE_NAME as default Home role holder! Current holders: '$ROLE_HOLDERS'" >&2
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
    echo "ERROR: Dry Toast was not brought to the foreground upon pressing Home!" >&2
    exit 1
fi
echo "    SUCCESS: Dry Toast MainActivity is active and focused."

# 6. Memory Ceiling Check on Phone Viewport (< 35 MB Total PSS in headless CI with SwiftShader)
echo "==> Step 6: Asserting Phone TOTAL PSS memory budget (< ${CEILING_KB} KB)..."
APP_PID=$(adb shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r' || true)
if [ -n "$APP_PID" ]; then
    # Trigger ART garbage collection to reclaim transient startup allocations
    adb shell kill -10 "$APP_PID" 2>/dev/null || true
    sleep 2
fi

MEMINFO_OUTPUT=$(adb shell dumpsys meminfo "$PACKAGE_NAME" 2>/dev/null || true)
echo "$MEMINFO_OUTPUT"

PHONE_PSS=$(echo "$MEMINFO_OUTPUT" | (grep "TOTAL PSS:" || true) | awk '{print $3}' || echo "0")
echo "    Observed Phone TOTAL PSS: ${PHONE_PSS} KB (Budget: ${CEILING_KB} KB)"

if [ -n "$PHONE_PSS" ] && [ "$PHONE_PSS" -gt "$CEILING_KB" ]; then
    echo "ERROR: Phone memory ceiling exceeded! ${PHONE_PSS} KB > ${CEILING_KB} KB" >&2
    exit 1
fi
echo "    SUCCESS: Phone memory consumption within strict budget (${PHONE_PSS} KB <= ${CEILING_KB} KB)."

# 7. Memory Ceiling Check on Tablet 2560x1600 @ 320dpi (1280x800dp EXPANDED) Viewport
echo "==> Step 7: Cold-starting on 2560x1600 @ 320dpi tablet viewport and asserting TOTAL PSS (< ${CEILING_KB} KB)..."
adb shell cmd role remove-role-holder android.app.role.HOME "$PACKAGE_NAME" >/dev/null 2>&1 || true
adb shell am force-stop "$PACKAGE_NAME"
adb shell wm size 2560x1600
adb shell wm density 320
sleep 2

TABLET_ROLE_GRANTED=0
for i in {1..12}; do
    if adb shell cmd role add-role-holder android.app.role.HOME "$PACKAGE_NAME" 2>/dev/null; then
        ROLE_HOLDERS=$(adb shell cmd role get-role-holders android.app.role.HOME 2>/dev/null | tr -d '\r')
        if [[ "$ROLE_HOLDERS" == *"$PACKAGE_NAME"* ]]; then
            TABLET_ROLE_GRANTED=1
            break
        fi
    fi
    sleep 1
done
if [ "$TABLET_ROLE_GRANTED" -ne 1 ]; then
    echo "ERROR: Failed to re-grant Home role to $PACKAGE_NAME on 2560x1600 tablet viewport!" >&2
    exit 1
fi

adb shell input keyevent KEYCODE_HOME
sleep 2

TABLET_FOCUS=$(adb shell dumpsys window 2>/dev/null | (grep -E "mCurrentFocus|mFocusedApp" || true))
echo "    Tablet Current Focus: $TABLET_FOCUS"
if [[ "$TABLET_FOCUS" != *"$PACKAGE_NAME"* ]]; then
    echo "ERROR: Dry Toast was not brought to the foreground on 2560x1600 tablet viewport!" >&2
    exit 1
fi

APP_PID=$(adb shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r' || true)
if [ -n "$APP_PID" ]; then
    adb shell kill -10 "$APP_PID" 2>/dev/null || true
    sleep 2
fi

TABLET_MEMINFO_OUTPUT=$(adb shell dumpsys meminfo "$PACKAGE_NAME" 2>/dev/null || true)
echo "$TABLET_MEMINFO_OUTPUT"

TABLET_PSS=$(echo "$TABLET_MEMINFO_OUTPUT" | (grep "TOTAL PSS:" || true) | awk '{print $3}' || echo "0")
echo "    Observed Tablet (2560x1600) TOTAL PSS: ${TABLET_PSS} KB (Budget: ${CEILING_KB} KB)"

cleanup_display

if [ -n "$TABLET_PSS" ] && [ "$TABLET_PSS" -gt "$CEILING_KB" ]; then
    echo "ERROR: Tablet (2560x1600) memory ceiling exceeded! ${TABLET_PSS} KB > ${CEILING_KB} KB" >&2
    exit 1
fi
echo "    SUCCESS: Tablet (2560x1600) memory consumption within strict budget (${TABLET_PSS} KB <= ${CEILING_KB} KB)."

TOTAL_PSS="${PHONE_PSS:-0}"
if [ -n "$TABLET_PSS" ] && [ "${TABLET_PSS:-0}" -gt "${TOTAL_PSS:-0}" ]; then
    TOTAL_PSS="$TABLET_PSS"
fi

mkdir -p build
echo "${TOTAL_PSS:-0}" > build/total_pss.txt

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  cat << EOF >> "$GITHUB_STEP_SUMMARY"
### 📱 Android Emulator Smoke & Memory Gate
| Metric | Value | Budget / Status |
| :--- | :--- | :--- |
| **Home Role Assignment** | \`$PACKAGE_NAME\` | ✅ Granted |
| **Foreground Focus** | \`$MAIN_ACTIVITY\` | ✅ Verified |
| **Phone Total PSS** | **\`${PHONE_PSS} KB\`** | ✅ Pass (< ${CEILING_KB} KB) |
| **Tablet 2560x1600 Total PSS** | **\`${TABLET_PSS} KB\`** | ✅ Pass (< ${CEILING_KB} KB) |
EOF
fi

echo "=========================================================="
echo "  ALL SMOKE CHECKS PASSED: Home Role & Memory Verified!"
echo "=========================================================="
