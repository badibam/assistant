#!/bin/bash

# Generates the keystore used to sign the release APKs
# Usage: ./generate_keystore.sh

set -e

KEYSTORE_DIR="../keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/assistant-release.keystore"
KEY_ALIAS="assistant-release"

echo "Generating the keystore for Assistant"

mkdir -p "$KEYSTORE_DIR"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "The keystore already exists: $KEYSTORE_FILE"
    read -p "Replace it? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled"
        exit 0
    fi
    rm "$KEYSTORE_FILE"
fi

echo "Signing credentials:"
read -s -p "Keystore password: " KEYSTORE_PASSWORD
echo
read -s -p "Key password: " KEY_PASSWORD
echo

echo "Certificate details:"
read -p "Full name: " CN
read -p "Organization: " O
read -p "City: " L
read -p "State or province: " ST
read -p "Country code (2 letters): " C

echo "Generating the keystore..."
keytool -genkeypair \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 25000 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=$CN, OU=Android Development, O=$O, L=$L, ST=$ST, C=$C"

echo "Keystore generated."
echo "Location: $KEYSTORE_FILE"

echo "Keystore contents:"
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS"

echo ""
echo "IMPORTANT: store both passwords somewhere safe. They are not printed here,"
echo "and a lost keystore password means the app can never be updated again."
echo ""
echo "Keep the keystore out of git: echo 'keystore/' >> .gitignore"

# Template for the environment file the release build reads
cat > "$KEYSTORE_DIR/.env.example" << EOF
# Environment variables used for signing
# Copy this file to .env and fill in the values
KEYSTORE_PASSWORD=your_keystore_password_here
KEY_PASSWORD=your_key_password_here
EOF

echo ""
echo "Setup complete. Next:"
echo "1. Copy keystore/.env.example to keystore/.env"
echo "2. Fill in the passwords there"
echo "3. Run: ./gradlew assembleRelease"
