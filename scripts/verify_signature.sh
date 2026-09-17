#!/bin/bash

# Script de vérification de signature APK
# Usage: ./verify_signature.sh path/to/app.apk

set -e

APK_PATH=$1

if [ -z "$APK_PATH" ]; then
    echo "Usage: $0 <path-to-apk>"
    exit 1
fi

if [ ! -f "$APK_PATH" ]; then
    echo " APK non trouvé: $APK_PATH"
    exit 1
fi

echo " Vérification de la signature: $APK_PATH"

# Vérifier avec jarsigner (détaillé)
echo ""
echo " Informations détaillées de signature:"
jarsigner -verify -verbose -certs "$APK_PATH"

# Vérifier avec apksigner si disponible (Android SDK)
if command -v apksigner &> /dev/null; then
    echo ""
    echo " Vérification apksigner:"
    apksigner verify --verbose "$APK_PATH"
else
    echo " apksigner non disponible (Android SDK non trouvé)"
fi

# Afficher le contenu du certificat
echo ""
echo " Certificat utilisé:"
keytool -printcert -jarfile "$APK_PATH"

echo ""
echo " Vérification terminée!"