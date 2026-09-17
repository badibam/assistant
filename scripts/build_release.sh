#!/bin/bash

# Script de build release automatisé
# Usage: ./build_release.sh [version]

set -e

VERSION=${1:-"auto"}
KEYSTORE_ENV="../keystore/.env"

echo " Build de release pour Assistant"

# Vérifier que le keystore existe
if [ ! -f "../keystore/assistant-release.keystore" ]; then
    echo " Keystore manquant. Exécutez d'abord: ./generate_keystore.sh"
    exit 1
fi

# Charger les variables d'environnement si disponibles
if [ -f "$KEYSTORE_ENV" ]; then
    echo " Chargement des variables d'environnement..."
    export $(cat "$KEYSTORE_ENV" | xargs)
else
    echo " Fichier .env manquant. Utilisation des valeurs par défaut."
fi

# Déterminer la version
if [ "$VERSION" = "auto" ]; then
    # Extraire la version du build.gradle.kts
    VERSION=$(grep "versionName" ../app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
    echo " Version détectée: $VERSION"
else
    echo " Version spécifiée: $VERSION"

    # Mettre à jour build.gradle.kts avec la nouvelle version
    if [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        # Calculer versionCode depuis la version (ex: 1.2.3 -> 10203)
        IFS='.' read -ra ADDR <<< "$VERSION"
        VERSION_CODE=$((${ADDR[0]} * 10000 + ${ADDR[1]} * 100 + ${ADDR[2]}))

        echo " Mise à jour des versions dans build.gradle.kts..."
        sed -i "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" ../app/build.gradle.kts
        sed -i "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" ../app/build.gradle.kts
    fi
fi

# Clean avant build
echo " Nettoyage..."
cd ..
./gradlew clean

# Build release
echo " Build release en cours..."
./gradlew assembleRelease

# Vérifier que l'APK a été généré
APK_PATH="app/build/outputs/apk/release/assistant-v$VERSION.apk"
if [ -f "$APK_PATH" ]; then
    echo " APK généré avec succès!"
    echo " Emplacement: $APK_PATH"

    # Afficher la taille du fichier
    SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo " Taille: $SIZE"

    # Vérifier la signature
    echo " Vérification de la signature..."
    ../scripts/verify_signature.sh "$APK_PATH"

else
    echo " Échec de la génération de l'APK"
    exit 1
fi

echo ""
echo " Build terminé avec succès!"
echo " APK: $APK_PATH"
echo " Version: $VERSION"

# Instructions pour la suite
echo ""
echo " Étapes suivantes:"
echo " 1. Tester l'APK sur un appareil"
echo " 2. Créer un tag Git: git tag v$VERSION"
echo " 3. Créer une release GitHub avec cet APK"
echo " 4. Pousser le tag: git push origin v$VERSION"