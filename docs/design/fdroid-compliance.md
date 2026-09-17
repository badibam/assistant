# Conformité F-Droid

Spec d'implémentation pour rendre le dépôt publiable sur F-Droid, au sens de la facette `fdroid` de wisdom (`/mnt/data/OUTILS/socle/modules/fdroid.md`, qui s'abonne en plus de `android`).

Analyse faite le 2026-08-01. Décision actée : licence GNU (GPL-3.0).

À élaguer une fois chaque point traité (le code + les commits deviennent le registre).

## 1. Licence — GPL-3.0

**Bloquant de fond, à traiter en premier** : `LICENSE.txt` actuel = CC BY-NC-SA 4.0, non reconnue DFSG/OSI/FSF (clause NonCommercial). F-Droid exige une licence FLOSS reconnue.

- Remplacer `LICENSE.txt` par le texte complet de la GPL-3.0 (texte officiel FSF, pas de résumé).
- Vérifier qu'aucune dépendance actuelle n'a une licence incompatible avec la GPL-3.0 (peu probable : les libs identifiées — AndroidX, Room, OkHttp, Gson, kotlinx, json-schema-validator, WorkManager — sont toutes Apache-2.0/MIT, compatibles).
- Pas de dépendance propriétaire trouvée (aucun GMS/Firebase/Play Services) — rien à isoler de ce côté.

## 2. Chaîne de build 100 % libre

**Bloquant réel identifié** : `app/build.gradle.kts:399`, la tâche `generateThemeResources` appelle `npx svg2vectordrawable`. Cette tâche est accrochée à `preBuild` (ligne ~388 : `tasks.named("preBuild") { dependsOn("generateThemeResources", "generateStringResources") }`), donc déclenchée à **chaque** build, y compris `assembleRelease`. Le serveur de build F-Droid n'a ni Node ni npm → échec de build garanti.

Point rassurant : les sorties de cette tâche (drawables `default_*.xml` sous `app/src/main/res/drawable/`, et `GeneratedThemeResources.kt`) sont déjà **committées** dans git. L'appel npx n'est donc utile qu'au développeur qui ajoute/modifie une icône — jamais à la reconstruction depuis les sources déjà versionnées.

- Retirer `generateThemeResources` de la liste `dependsOn` de `preBuild`. La rendre invocable manuellement (`./gradlew generateThemeResources`) pour le développeur qui ajoute une icône.
- Garder `generateStringResources` dans `preBuild` — elle ne fait aucun appel externe (juste du parsing XML), pas de souci de reproductibilité.
- Vérifier qu'aucune autre tâche Gradle n'appelle un outil hors de l'arbre (recherche faite : aucun autre `exec(`, `ProcessBuilder` ou appel `npx` trouvé dans `app/build.gradle.kts` / `build.gradle.kts`).

## 3. Reproductibilité du build

- **AGP** : actuellement 8.2.2 (`build.gradle.kts:3`). Passer à ≥ 8.3 pour pouvoir désactiver l'info VCS injectée dans l'APK (`vcsInfo`, cf. doc AGP — probablement via `androidResources { ... }` ou équivalent selon la version exacte, à vérifier au moment du bump).
- **`cruncherEnabled = false`** : pas déclaré explicitement dans `app/build.gradle.kts`. À ajouter (bloc `androidResources`/`aaptOptions` selon version AGP) pour éliminer la variation de compression PNG d'une machine à l'autre.
- **NDK / `abiFilters`** : `app/build.gradle.kts` déclare `ndk { abiFilters += listOf(...) }` en debug (`arm64-v8a`, `x86_64`) et en release (`arm64-v8a` seul), alors qu'aucun code natif n'a été trouvé dans le projet (pas de `.so`, pas de `CMakeLists.txt`). À vérifier : si ces filtres n'ont aucun effet réel (pas de lib native à filtrer), les supprimer — sinon, si un usage futur est prévu, épingler `ndkVersion` explicitement comme l'exige la facette.
- **Invocation** : déjà correct — `run` délègue au wrapper Gradle (`android.md` respecté), jamais à l'IDE.
- **Signature** : déjà correcte — `signingConfigs.release` dans `app/build.gradle.kts` lit le keystore et les mots de passe hors dépôt (`keystore/`, `.env` gitignorés), pilotée par Gradle (`apksigner` implicite). Rien à changer.
- **Wrapper Gradle** : déjà versionné (`gradle/wrapper/gradle-wrapper.jar` + `.properties` trackés dans git malgré la présence de `gradle/` dans `.gitignore` — les fichiers déjà trackés ne sont pas affectés par une règle d'ignore ajoutée après coup). Rien à changer, mais noter l'incohérence : la règle `gradle/` dans `.gitignore` (ligne 21) est trompeuse puisque son contenu est en réalité versionné — à nettoyer un jour pour la lisibilité, hors scope fdroid strict.
- **Timestamps** : gérés par AGP depuis 2.2.2, rien à faire tant qu'on ne réintroduit pas de timestamp custom (aucun trouvé).

## 4. Auto-updater — à désactiver pour la variante F-Droid

**Bloquant de conception** : `app/src/main/java/com/assistant/core/update/UpdateChecker.kt` interroge l'API GitHub (`api.github.com/repos/badibam/assistant/releases/latest`) et résout une URL de téléchargement d'APK. F-Droid rejette systématiquement les apps qui téléchargent/installent du code exécutable venu d'ailleurs que lui-même — même en usage opt-in explicite côté utilisateur, ce n'est pas couvert par une simple déclaration d'anti-feature dans la grille (`Ads`, `Tracking`, etc.), c'est un motif de rejet direct.

- Créer une *flavor* Gradle dédiée (conforme au principe `fdroid.md` : « isoler la dépendance dans une flavor Gradle plutôt que la rendre optionnelle à l'exécution ») qui exclut `UpdateChecker` et tout point d'entrée UI qui l'invoque (bouton « vérifier les mises à jour », écran Settings, etc. — à localiser).
- Pour les autres canaux de distribution (GitHub direct, APK hors F-Droid), le mécanisme peut rester actif dans la flavor par défaut.
- Localiser tous les appelants de `UpdateChecker` avant de trancher le point d'exclusion exact (recherche non faite à ce stade — à faire à l'implémentation).

## 5. Anti-features — à déclarer, pas à corriger

- **`NonFreeNet`** : deux providers IA appellent des API commerciales propriétaires — `ClaudeProviderCore.kt` (`api.anthropic.com`) et `OpenAIProviderCore.kt`. Usage légitime pour un assistant IA (l'utilisateur fournit sa propre clé, rien de codé en dur — vérifié). À déclarer dans la fiche F-Droid au moment de la soumission, aucun changement de code requis.
- Repasser la grille complète (`Ads`, `DisabledAlgorithm`, `KnownVuln`, `NonFreeAdd`, `NonFreeAssets`, `NonFreeDep`, `NonFreeNet`, `NoSourceSince`, `TetheredNet`, `Tracking`) juste avant la soumission réelle — pas avant, la codebase évolue.
- `NonFreeAssets` : vérifier la licence des médias embarqués (icônes du thème par défaut, sons éventuels) au moment de la soumission — pas d'audit fait ici.

## 6. Fiche versionnée (fastlane)

Entièrement à créer, aucun contenu existant. Arborescence `fastlane/metadata/android/en-US/` (locale de repli obligatoire = `en-US`, cohérent avec `[grand public]` d'`android.md`) :

- `short_description.txt` — obligatoire, ≤ 80 caractères.
- `full_description.txt` — obligatoire, ≤ 4000 caractères.
- `title.txt` — ≤ 50 caractères.
- `changelogs/<versionCode>.txt` — ≤ 500 caractères, un fichier par release, nommé du `versionCode` (25 actuellement — cf. `defaultConfig.versionCode` dans `app/build.gradle.kts`), pas du `versionName`.
- `images/icon.png`, `images/featureGraphic.png` (paysage), `images/phoneScreenshots/` au minimum.
- Autres locales = traductions optionnelles, une fois l'anglais complet.

## 7. Discipline de release

- Taguer chaque release sur le commit exact, nom = `versionName` (ex. `v0.3.15`) — pas encore de tag dans l'historique actuel à vérifier/instaurer.
- Construire depuis le tag, arbre propre, jamais de modification locale non commitée.
- `versionCode` déjà monotone (25) — rien à changer, juste à maintenir la discipline à chaque bump.

## Ordre recommandé

1. Licence (décision actée — remplacer le fichier).
2. Fix build (retrait `generateThemeResources` de `preBuild`) — petit, mécanique, à faire tôt pour ne pas casser un build F-Droid dès la première tentative.
3. Isolation de l'auto-updater (flavor Gradle) — nécessite de localiser tous les appelants, un peu plus de travail.
4. Réglages de reproductibilité (AGP, vcsInfo, cruncher, NDK) — à grouper, mécanique.
5. Fiche fastlane — au moment de préparer la première release candidate pour soumission.
6. Grille anti-features — juste avant la soumission réelle, pas avant.
