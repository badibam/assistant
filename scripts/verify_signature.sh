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

echo "Checking the signature of: $APK_PATH"

# jarsigner gives the detailed view
echo ""
echo "Detailed signature information:"
jarsigner -verify -verbose -certs "$APK_PATH"

# apksigner when the Android SDK provides it
if command -v apksigner &> /dev/null; then
    echo ""
    echo "apksigner check:"
    apksigner verify --verbose "$APK_PATH"
else
    echo "apksigner unavailable (Android SDK not found)"
fi

# The certificate itself
echo ""
echo "Certificate used:"
keytool -printcert -jarfile "$APK_PATH"

echo ""
echo "Check complete."
