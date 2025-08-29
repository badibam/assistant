# Processus de Release et Mise à Jour

Documentation complète du processus de release pour l'Assistant personnel.

## 🎯 Vue d'ensemble

L'application utilise un système de release complet avec :
- Versioning sémantique (0.1.0, 0.1.1, 0.2.0...)
- Migrations automatiques de base de données
- Système de mise à jour semi-automatique
- Distribution via GitHub Releases

## 📋 Prérequis

### 1. Environnement de développement
```bash
# Outils requis
- Android SDK 34+
- Kotlin 1.9+
- Git
- keytool (JDK)
```

### 2. Configuration initiale
```bash
# 1. Générer le keystore de signature (une seule fois)
cd scripts
./generate_keystore.sh

# 2. Configurer les variables d'environnement
cp keystore/.env.example keystore/.env
# Éditer keystore/.env avec les mots de passe
```

## 🚀 Processus de Release

### Étape 1: Préparation
```bash
# 1. Vérifier que develop est à jour
git checkout develop
git status
git log --oneline -5

# 2. Tester l'application
./gradlew assembleDebug
# Test manuel sur émulateur/appareil

# 3. Vérifier les migrations si nécessaire
# Consulter AppVersionManager.kt pour CURRENT_APP_VERSION
```

### Étape 2: Versioning
```bash
# Déterminer le type de release
# - 0.1.0 → 0.1.1 : Correctif (patch)
# - 0.1.0 → 0.2.0 : Nouvelles fonctionnalités (minor)  
# - 0.1.0 → 1.0.0 : Changements majeurs (major)

# Mettre à jour les versions
# 1. Dans app/build.gradle.kts:
versionCode = 2         # Incrémenter
versionName = "0.1.1"   # Version sémantique

# 2. Dans AppVersionManager.kt si nécessaire:
const val CURRENT_APP_VERSION = 2  # Si migrations nécessaires
```

### Étape 3: Build Release
```bash
# Build automatisé avec script
cd scripts
./build_release.sh 0.1.1

# Ou build manuel
cd ..
./gradlew clean
./gradlew assembleRelease
```

### Étape 4: Tests Release
```bash
# 1. Vérifier la signature
./scripts/verify_signature.sh app/build/outputs/apk/release/assistant-v0.1.1.apk

# 2. Test d'installation
adb install -r app/build/outputs/apk/release/assistant-v0.1.1.apk

# 3. Test de mise à jour (si app déjà installée)
# Vérifier que les migrations s'exécutent correctement
```

### Étape 5: Merge vers main
```bash
# 1. Merger develop → main
git checkout main
git merge develop

# 2. Créer le tag de version  
git tag v0.1.1
git push origin main
git push origin v0.1.1
```

### Étape 6: Publication GitHub
```bash
# 1. Créer la release sur GitHub
gh release create v0.1.1 \
  --title "Version 0.1.1" \
  --notes "$(cat CHANGELOG.md)" \
  app/build/outputs/apk/release/assistant-v0.1.1.apk

# 2. Vérifier que l'APK est attaché à la release
```

## 🔄 Système de Mise à Jour

### Architecture
```
UpdateManager ←→ UpdateChecker → GitHub API
     ↓
UpdateDownloader → DownloadManager → Installation
```

### Configuration
- **Vérification**: Automatique toutes les 24h au démarrage
- **Source**: GitHub Releases API
- **Installation**: Semi-automatique (requiert confirmation utilisateur)

### API GitHub utilisée
```
GET https://api.github.com/repos/badibam/assistant/releases/latest
```

## 🗂️ Structure de Versioning

### Versions de l'application
| Composant | Format | Exemple | Usage |
|-----------|--------|---------|--------|
| versionCode | Entier | 10203 | Google Play, migrations |
| versionName | Sémantique | "1.2.3" | Utilisateur |
| Git Tag | v + sémantique | "v1.2.3" | GitHub releases |

### Mapping versions
```kotlin
// build.gradle.kts
versionCode = 10203      // 1.2.3 → 10203
versionName = "1.2.3"    // Version publique

// AppVersionManager.kt  
CURRENT_APP_VERSION = 10203  // = versionCode
```

## 🛠️ Migrations

### Types de migrations
1. **Database**: Schéma Room (découverte automatique par outil)
2. **Configuration**: Format JSON des outils
3. **Application**: Données globales

### Ajout de migrations
```kotlin
// Dans un ToolType (ex: TrackingToolType)
val TRACKING_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tracking_data ADD COLUMN category TEXT DEFAULT ''")
    }
}

override fun getDatabaseMigrations(): List<Migration> {
    return listOf(TRACKING_MIGRATION_2_3)
}
```

## 📁 Scripts Disponibles

### `scripts/generate_keystore.sh`
Génère le keystore de signature (exécuter une seule fois)

### `scripts/build_release.sh [version]`
Build automatisé avec mise à jour des versions

### `scripts/verify_signature.sh <apk>`
Vérifie la signature d'un APK

## ⚠️ Points d'Attention

### Sécurité
- **Keystore**: Jamais dans Git, sauvegarde séparée obligatoire
- **Mots de passe**: Variables d'environnement uniquement
- **APK**: Toujours vérifier la signature avant distribution

### Migrations
- **Test**: Toujours tester les migrations sur vraies données
- **Backup**: Base sauvegardée automatiquement avant migration
- **Rollback**: Impossible, migration = transformation définitive

### Git
- **main**: Code stable uniquement
- **develop**: Développement actif
- **Tags**: Obligatoires pour chaque release

## 🐛 Résolution de Problèmes

### Build échoue
```bash
# 1. Nettoyer le build
./gradlew clean

# 2. Vérifier le keystore
ls -la keystore/
cat keystore/.env

# 3. Vérifier les permissions
keytool -list -keystore keystore/assistant-release.keystore
```

### Migrations échouent
```bash
# 1. Vérifier les logs
adb logcat | grep -i migration

# 2. Réinitialiser la base (développement uniquement)
adb shell pm clear com.assistant.debug
```

### Mise à jour échoue
```bash
# 1. Vérifier la connectivité
# 2. Vérifier l'URL GitHub Releases
# 3. Vérifier les permissions d'installation
```

## 🎉 Checklist Release

### Pré-release
- [ ] Code stable sur develop
- [ ] Tests manuels OK
- [ ] Migrations testées
- [ ] Version incrémentée
- [ ] Changelog mis à jour

### Release
- [ ] Build release généré
- [ ] APK signé et vérifié
- [ ] Test d'installation OK
- [ ] Tag Git créé
- [ ] GitHub Release créée
- [ ] APK attaché à la release

### Post-release
- [ ] Mise à jour testée sur appareil
- [ ] Migration testée si applicable
- [ ] Documentation mise à jour
- [ ] develop synchronisé avec main