#!/bin/bash

# APK signature check
# Usage: ./verify_signature.sh path/to/app.apk

set -e

APK_PATH=$1

if [ -z "$APK_PATH" ]; then
    echo "Usage: $0 <path-to-apk>"
    exit 1
fi

if [ ! -f "$APK_PATH" ]; then
    echo "APK not found: $APK_PATH"
    exit 1
fi

# apksigner is the only tool that reads the v2 and v3 schemes. It ships with the SDK build
# tools rather than on PATH, so it is looked for where the SDK puts it, newest first.
find_apksigner() {
    if command -v apksigner &> /dev/null; then
        command -v apksigner
        return
    fi
    for root in "$ANDROID_HOME" "$ANDROID_SDK_ROOT" "$HOME/android-sdk" "$HOME/Android/Sdk"; do
        [ -n "$root" ] || continue
        local found
        found=$(ls -1d "$root"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)
        if [ -n "$found" ]; then
            echo "$found"
            return
        fi
    done
}

APKSIGNER=$(find_apksigner)

echo "Checking the signature of: $APK_PATH"
echo ""

if [ -n "$APKSIGNER" ]; then
    "$APKSIGNER" verify --verbose --print-certs "$APK_PATH"
    exit $?
fi

# Without apksigner there is no honest answer, only jarsigner's, which knows the v1 scheme
# alone. At minSdk 26 the build signs with v2 and not v1, so jarsigner calls a perfectly
# good APK unsigned. Reporting that as a failure would be worse than reporting nothing.
echo "apksigner not found: install the Android SDK build tools, or set ANDROID_HOME."
echo "jarsigner, the only tool at hand, reads the v1 scheme alone, which this build does"
echo "not use -- its verdict would say nothing about whether the APK is signed."
exit 1
