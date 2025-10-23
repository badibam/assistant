---
argument-hint: [version-number]
description: Complete version upgrade process including build, tag, and release
allowed-tools: Bash, Edit, Write, Read
---

On passe à la version $1

**Séquence complète :**

1. **Build release** : Tester sur device réel avec `./gradlew assembleRelease`
2. Si OK, **modifier les versions** dans `app/build.gradle.kts` :
   - Incrémenter `versionCode`
   - Mettre à jour `versionName` (actuel → "$1")
3. **Commiter** (sans mentionner claude) : "Version $1"
4. **Tag** : `git tag v$1`
5. **Push develop + tag** : `git push origin develop --tags`
6. **Écrire notes de version** :
   - Analyser tous les commits depuis version précédente
   - Créer fichier temporaire `release-notes-$1.txt`
   - Style : précis, simple, sans emoji, sans mentionner claude
   - Présenter pour correction avant release
7. **Release GitHub** :
   - Utiliser `gh release create v$1`
   - Ajouter notes de version (fichier corrigé)
   - Attacher APK : `app/build/outputs/apk/release/assistant-v$1.apk`
8. **Cleanup** : Supprimer fichier notes de version temporaire
9. **Merge main** :
   - `git checkout main`
   - `git merge develop`
   - `git push origin main`
10. **Retour develop** : `git checkout develop`

**Note** : Cette commande gère uniquement la release de l'app. Pour les changements de DB, utiliser `/db-upgrade` avant.
