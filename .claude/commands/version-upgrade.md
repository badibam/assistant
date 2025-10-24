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
3. **README.md** à mettre à jour : avec numéro de version actuel + état actuel de l'app , sans mettre en valeur les changements récents. Il s'agit d'une vue d'ensemble concise de toutes les fonctionnalités présentes.
4. **Commiter** (sans mentionner claude) : "Version $1"
5. **Tag** : `git tag v$1`
6. **Push develop + tag** : `git push origin develop --tags`
7. **Écrire notes de version** :
   - Analyser tous les commits depuis version précédente
   - Créer fichier temporaire `release-notes-$1.txt`
   - Style : précis, simple, sans emoji, sans mentionner claude. Uniquement changements depuis dernière version.
   - Présenter pour correction avant release
8. **Release GitHub** :
   - Utiliser `gh release create v$1`
   - Ajouter notes de version (fichier corrigé)
   - Attacher APK : `app/build/outputs/apk/release/assistant-v$1.apk`
9. **Cleanup** : Supprimer fichier notes de version temporaire
10. **Merge main** :
   - `git checkout main`
   - `git merge develop`
   - `git push origin main`
11. **Retour develop** : `git checkout develop`

**Note** : Cette commande gère uniquement la release de l'app. Pour les changements de DB, utiliser `/db-upgrade` avant.
