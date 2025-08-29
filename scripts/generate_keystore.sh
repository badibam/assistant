#!/bin/bash

# Script de génération du keystore pour signature des APK
# Usage: ./generate_keystore.sh

set -e

KEYSTORE_DIR="../keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/assistant-release.keystore"
KEY_ALIAS="assistant-release"

echo "🔐 Génération du keystore pour Assistant"

# Créer le dossier keystore
mkdir -p "$KEYSTORE_DIR"

# Vérifier si le keystore existe déjà
if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️  Le keystore existe déjà: $KEYSTORE_FILE"
    read -p "Voulez-vous le remplacer? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ Opération annulée"
        exit 0
    fi
    rm "$KEYSTORE_FILE"
fi

# Demander les mots de passe
echo "📝 Entrez les informations de signature:"
read -s -p "Mot de passe keystore: " KEYSTORE_PASSWORD
echo
read -s -p "Mot de passe clé: " KEY_PASSWORD
echo

# Informations du certificat
echo "📋 Informations du certificat:"
read -p "Nom complet: " CN
read -p "Organisation: " O
read -p "Ville: " L
read -p "État/Province: " ST
read -p "Code pays (2 lettres): " C

# Générer le keystore
echo "🔨 Génération du keystore..."
keytool -genkeypair \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 25000 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=$CN, OU=Android Development, O=$O, L=$L, ST=$ST, C=$C"

# Vérifier le keystore
echo "✅ Keystore généré avec succès!"
echo "📍 Emplacement: $KEYSTORE_FILE"

# Afficher les informations
echo "📄 Informations du keystore:"
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS"

echo ""
echo "🔒 IMPORTANT: Sauvegardez ces mots de passe en sécurité!"
echo "   Keystore: $KEYSTORE_PASSWORD"
echo "   Clé:      $KEY_PASSWORD"
echo ""
echo "🚫 N'oubliez pas d'ajouter le keystore au .gitignore !"
echo "   echo 'keystore/' >> .gitignore"

# Créer un fichier d'environnement exemple
cat > "$KEYSTORE_DIR/.env.example" << EOF
# Variables d'environnement pour la signature
# Copiez ce fichier vers .env et remplissez les valeurs
KEYSTORE_PASSWORD=your_keystore_password_here
KEY_PASSWORD=your_key_password_here
EOF

echo ""
echo "✅ Setup terminé! Vous pouvez maintenant:"
echo "   1. Copier keystore/.env.example vers keystore/.env"
echo "   2. Remplir les mots de passe dans keystore/.env"
echo "   3. Lancer: ./gradlew assembleRelease"