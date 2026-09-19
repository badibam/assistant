# Conformité F-Droid

Spec d'implémentation pour rendre le dépôt publiable sur F-Droid, au sens de la facette `fdroid` de wisdom (`/mnt/data/OUTILS/socle/modules/fdroid.md`, qui s'abonne en plus de `android`).

Analyse faite le 2026-08-01. À élaguer une fois chaque point traité (le code + les commits deviennent le registre).

## 1. Auto-updater — à désactiver pour la variante F-Droid

**Bloquant de conception** : `app/src/main/java/com/assistant/core/update/UpdateChecker.kt` interroge l'API GitHub (`api.github.com/repos/badibam/assistant/releases/latest`) et résout une URL de téléchargement d'APK. F-Droid rejette systématiquement les apps qui téléchargent/installent du code exécutable venu d'ailleurs que lui-même — même en usage opt-in explicite côté utilisateur, ce n'est pas couvert par une simple déclaration d'anti-feature dans la grille (`Ads`, `Tracking`, etc.), c'est un motif de rejet direct.

- Créer une *flavor* Gradle dédiée (conforme au principe `fdroid.md` : « isoler la dépendance dans une flavor Gradle plutôt que la rendre optionnelle à l'exécution ») qui exclut `UpdateChecker` et tout point d'entrée UI qui l'invoque (bouton « vérifier les mises à jour », écran Settings, etc. — à localiser).
- Pour les autres canaux de distribution (GitHub direct, APK hors F-Droid), le mécanisme peut rester actif dans la flavor par défaut.
- Localiser tous les appelants de `UpdateChecker` avant de trancher le point d'exclusion exact (recherche non faite à ce stade — à faire à l'implémentation).

## 2. Anti-features — à déclarer, pas à corriger

- **`NonFreeNet`** : deux providers IA appellent des API commerciales propriétaires — `ClaudeProviderCore.kt` (`api.anthropic.com`) et `OpenAIProviderCore.kt`. Usage légitime pour un assistant IA (l'utilisateur fournit sa propre clé, rien de codé en dur — vérifié). À déclarer dans la fiche F-Droid au moment de la soumission, aucun changement de code requis.
- Repasser la grille complète (`Ads`, `DisabledAlgorithm`, `KnownVuln`, `NonFreeAdd`, `NonFreeAssets`, `NonFreeDep`, `NonFreeNet`, `NoSourceSince`, `TetheredNet`, `Tracking`) juste avant la soumission réelle — pas avant, la codebase évolue.
- `NonFreeAssets` : vérifier la licence des médias embarqués (icônes du thème par défaut, sons éventuels) au moment de la soumission — pas d'audit fait ici.

## 3. Fiche versionnée (fastlane)

Entièrement à créer, aucun contenu existant. Arborescence `fastlane/metadata/android/en-US/` (locale de repli obligatoire = `en-US`, cohérent avec `[grand public]` d'`android.md`) :

- `short_description.txt` — obligatoire, ≤ 80 caractères.
- `full_description.txt` — obligatoire, ≤ 4000 caractères.
- `title.txt` — ≤ 50 caractères.
- `changelogs/<versionCode>.txt` — ≤ 500 caractères, un fichier par release, nommé du `versionCode` (25 actuellement — cf. `defaultConfig.versionCode` dans `app/build.gradle.kts`), pas du `versionName`.
- `images/icon.png`, `images/featureGraphic.png` (paysage), `images/phoneScreenshots/` au minimum.
- Autres locales = traductions optionnelles, une fois l'anglais complet.

## 4. Discipline de release

- Taguer chaque release sur le commit exact, nom = `versionName` (ex. `v0.3.15`) — pas encore de tag dans l'historique actuel à vérifier/instaurer.
- Construire depuis le tag, arbre propre, jamais de modification locale non commitée.
- `versionCode` déjà monotone (25) — rien à changer, juste à maintenir la discipline à chaque bump.

## Ordre recommandé

1. Isolation de l'auto-updater (flavor Gradle) — nécessite de localiser tous les appelants, un peu plus de travail.
2. Fiche fastlane — au moment de préparer la première release candidate pour soumission.
3. Grille anti-features — juste avant la soumission réelle, pas avant.
