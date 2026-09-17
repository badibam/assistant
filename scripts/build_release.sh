#!/bin/bash

# Automated release build
# Usage: ./build_release.sh [version]

set -e

VERSION=${1:-"auto"}
KEYSTORE_ENV="../keystore/.env"

echo "Release build for Assistant"

# The keystore must exist
if [ ! -f "../keystore/assistant-release.keystore" ]; then
    echo "Keystore missing. Run ./generate_keystore.sh first."
    exit 1
fi

# Load the environment variables when they are there
if [ -f "$KEYSTORE_ENV" ]; then
    echo "Loading environment variables..."
    export $(cat "$KEYSTORE_ENV" | xargs)
else
    echo ".env missing. Falling back to the default values."
fi

# Settle the version
if [ "$VERSION" = "auto" ]; then
    # Read it off build.gradle.kts
    VERSION=$(grep "versionName" ../app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
    echo "Version detected: $VERSION"
else
    echo "Version given: $VERSION"

    # Write the new version into build.gradle.kts
    if [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        # versionCode derived from the version (1.2.3 -> 10203)
        IFS='.' read -ra ADDR <<< "$VERSION"
        VERSION_CODE=$((${ADDR[0]} * 10000 + ${ADDR[1]} * 100 + ${ADDR[2]}))

        echo "Updating the versions in build.gradle.kts..."
        sed -i "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" ../app/build.gradle.kts
        sed -i "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" ../app/build.gradle.kts
    fi
fi

# Clean before building
echo "Cleaning..."
cd ..
./gradlew clean

# Release build
echo "Building the release..."
./gradlew assembleRelease

# The APK must be there
APK_PATH="app/build/outputs/apk/release/assistant-v$VERSION.apk"
if [ -f "$APK_PATH" ]; then
    echo "APK built."
    echo "Location: $APK_PATH"

    SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo "Size: $SIZE"

    echo "Checking the signature..."
    ../scripts/verify_signature.sh "$APK_PATH"

else
    echo "APK build failed"
    exit 1
fi

echo ""
echo "Build complete."
echo "APK: $APK_PATH"
echo "Version: $VERSION"

echo ""
echo "Next steps:"
echo "1. Test the APK on a device"
echo "2. Tag it: git tag v$VERSION"
echo "3. Create the GitHub release with this APK"
echo "4. Push the tag: git push origin v$VERSION"
