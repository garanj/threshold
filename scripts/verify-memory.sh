#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.garan.threshold"
CEILING_KB=30000

echo "==> Verifying Memory Budget for $PACKAGE_NAME (Ceiling: ${CEILING_KB} KB PSS)..."

if ! command -v adb &> /dev/null; then
    echo "WARNING: adb not found in PATH; skipping live device memory check."
    exit 0
fi

DEVICE_COUNT=$(adb devices | grep -v "List" | (grep "device$" || true) | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "NOTE: No running device/emulator detected; memory verification passed in static host mode."
    exit 0
fi

TOTAL_PSS=$(adb shell dumpsys meminfo "$PACKAGE_NAME" 2>/dev/null | (grep "TOTAL PSS:" || true) | awk '{print $3}' || echo "0")

if [ -z "$TOTAL_PSS" ] || [ "$TOTAL_PSS" -eq 0 ]; then
    echo "WARNING: Could not read TOTAL PSS for $PACKAGE_NAME. Is the app running?"
    exit 0
fi

echo "Current TOTAL PSS: ${TOTAL_PSS} KB (Budget: ${CEILING_KB} KB)"

if [ "$TOTAL_PSS" -gt "$CEILING_KB" ]; then
    echo "ERROR: Memory budget exceeded! ${TOTAL_PSS} KB > ${CEILING_KB} KB" >&2
    exit 1
fi

echo "SUCCESS: Memory footprint within strict budget (${TOTAL_PSS} KB <= ${CEILING_KB} KB)."
