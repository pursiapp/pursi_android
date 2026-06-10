#!/usr/bin/env bash
set -e

APK="app/build/outputs/apk/debug/app-debug.apk"
IP="YOUR_DEVICE_IP"

echo "→ Checking ADB device..."
adb get-state 2>/dev/null || {
    echo "→ No device found, trying $IP..."
    adb connect "$IP:5555"
}

echo "→ Installing $APK"
adb install -r "$APK"
echo "✓ Done"
