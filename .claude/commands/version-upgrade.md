---
argument-hint: [version-number]
description: Complete version upgrade process including build, tag, and release
allowed-tools: Bash, Edit, Write, Read
---

On passe à la version $1

- build release pour tester sur device réel. Si ok on continue :
- modif versionName + versionCode dans build.gradle.kts
- modif CURRENT_APP_VERSION dans AppVersionManager
- commite (sans mentionner claude)
- tag
- push develop + tag
- écrire notes de version qui tiennent compte de tous les changements depuis version précédente, dans un fichier, pour correction par moi
- release (gh) avec notes de version (fichier corrigé) et apk
- suppression fichier notes de version
- merge dans main pour refléter cette nouvelle version.
- push main

Pas d'emoji et sois précis mais simple pour les notes de version - et sans mentionner claude.
