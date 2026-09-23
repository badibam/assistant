# Conformité F-Droid

Spec d'implémentation pour rendre le dépôt publiable sur F-Droid, au sens de la facette `fdroid` de wisdom (`/mnt/data/OUTILS/socle/modules/fdroid.md`, qui s'abonne en plus de `android`).

Analyse faite le 2026-08-01. À élaguer une fois chaque point traité (le code + les commits deviennent le registre).

## 1. Auto-updater — le mécanisme n'a jamais été branché

**Bloquant de conception, mais pas celui qu'on croyait.** `app/src/main/java/com/assistant/core/update/UpdateChecker.kt` interroge bien l'API GitHub (`api.github.com/repos/badibam/assistant/releases/latest`) et résout une URL de téléchargement d'APK, et F-Droid rejette systématiquement les apps qui téléchargent du code exécutable venu d'ailleurs que lui-même — motif de rejet direct, qu'aucune déclaration d'anti-feature ne couvre.

Mais le mécanisme ne tourne pas. Relevé le 2026-09-23 : tout le paquet `core/update/` (3 fichiers) n'est atteint depuis l'extérieur que par `MainActivity`, en deux endroits — la construction ligne 105, et l'appel ligne 108 :

```kotlin
updateManager.scheduleUpdateCheck { updateInfo ->
    LogManager.service("Update available: ${updateInfo.version}")
    // TODO: Show notification or dialog with UpdateInfo
}
```

Ce qui s'exécute donc réellement : un appel à l'API GitHub une fois par jour, dont le résultat part dans une ligne de journal. Aucun écran, aucune notification, aucun dialogue. `checkForUpdatesManually`, `downloadAndInstallUpdate`, `openInstallPermissionSettings`, `ignoreVersion`, `setAutoCheckEnabled` et `resetUpdatePreferences` n'ont aucun appelant hors du paquet : rien ne peut jamais télécharger ni installer quoi que ce soit.

Ce que le mécanisme mort porte avec lui dans le manifeste, et qui contredit « aucune permission déclarée sans usage effectif » d'`android.md` :

- `REQUEST_INSTALL_PACKAGES` et `WRITE_EXTERNAL_STORAGE` (maxSdk 28), déclarées en toutes lettres « for the in-app update mechanism » ;
- le `FileProvider` et son `res/xml/file_paths.xml`, dont `UpdateDownloader` est le seul utilisateur.

**Décision à prendre avant d'écrire quoi que ce soit** (elle porte sur le canal de distribution, pas sur le code) :

1. **Supprimer le paquet.** Le plus court et le plus conforme : trois fichiers, deux lignes de `MainActivity`, deux permissions, le `FileProvider` et `file_paths.xml` partent ensemble, et il n'y a pas de *flavor* à maintenir. La mise à jour se fait alors par F-Droid pour qui l'installe de là, et à la main pour qui prend l'APK sur GitHub.
2. **Finir la fonction, puis l'isoler dans une *flavor*.** C'est ce que la spec prévoyait, mais ça veut dire écrire d'abord l'interface qui manque — c'est un chantier neuf, pas une mise en conformité.

L'option 1 est celle qui se recommande : une *flavor* Gradle existe pour isoler une fonction qui marche, et on n'en isole pas une qui n'a jamais été atteinte. L'option 2 reste ouverte si le canal GitHub doit vraiment porter une mise à jour automatique.

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

1. Trancher le sort de l'auto-updater (cf. §1) : les appelants sont localisés, il n'en reste que deux, et la question qui reste est celle du canal de distribution, pas du code.
2. Fiche fastlane — au moment de préparer la première release candidate pour soumission.
3. Grille anti-features — juste avant la soumission réelle, pas avant.
