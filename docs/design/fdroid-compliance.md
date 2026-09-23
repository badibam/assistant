# Conformité F-Droid

Spec d'implémentation pour rendre le dépôt publiable sur F-Droid, au sens de la facette `fdroid` de wisdom (`/mnt/data/OUTILS/socle/modules/fdroid.md`, qui s'abonne en plus de `android`).

Analyse faite le 2026-08-01. À élaguer une fois chaque point traité (le code + les commits deviennent le registre).

## 1. Anti-features — à déclarer, pas à corriger

- **`NonFreeNet`** : deux providers IA appellent des API commerciales propriétaires — `ClaudeProviderCore.kt` (`api.anthropic.com`) et `OpenAIProviderCore.kt`. Usage légitime pour un assistant IA (l'utilisateur fournit sa propre clé, rien de codé en dur — vérifié). À déclarer dans la fiche F-Droid au moment de la soumission, aucun changement de code requis.
- Repasser la grille complète (`Ads`, `DisabledAlgorithm`, `KnownVuln`, `NonFreeAdd`, `NonFreeAssets`, `NonFreeDep`, `NonFreeNet`, `NoSourceSince`, `TetheredNet`, `Tracking`) juste avant la soumission réelle — pas avant, la codebase évolue.
- `NonFreeAssets` : vérifier la licence des médias embarqués (icônes du thème par défaut, sons éventuels) au moment de la soumission — pas d'audit fait ici.

## 2. Fiche versionnée (fastlane)

Entièrement à créer, aucun contenu existant. Arborescence `fastlane/metadata/android/en-US/` (locale de repli obligatoire = `en-US`, cohérent avec `[grand public]` d'`android.md`) :

- `short_description.txt` — obligatoire, ≤ 80 caractères.
- `full_description.txt` — obligatoire, ≤ 4000 caractères.
- `title.txt` — ≤ 50 caractères.
- `changelogs/<versionCode>.txt` — ≤ 500 caractères, un fichier par release, nommé du `versionCode` (25 actuellement — cf. `defaultConfig.versionCode` dans `app/build.gradle.kts`), pas du `versionName`.
- `images/icon.png`, `images/featureGraphic.png` (paysage), `images/phoneScreenshots/` au minimum.
- Autres locales = traductions optionnelles, une fois l'anglais complet.

## 3. Discipline de release

- Taguer chaque release sur le commit exact, nom = `versionName` (ex. `v0.3.15`) — pas encore de tag dans l'historique actuel à vérifier/instaurer.
- Construire depuis le tag, arbre propre, jamais de modification locale non commitée.
- `versionCode` déjà monotone (25) — rien à changer, juste à maintenir la discipline à chaque bump.

## Ordre recommandé

1. Fiche fastlane — au moment de préparer la première release candidate pour soumission.
2. Grille anti-features — juste avant la soumission réelle, pas avant.
