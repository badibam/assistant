# Référence — Assistant

Point d'entrée de la documentation. Ce fichier dit ce qu'est le projet, les règles qu'on y tient, et quel doc ouvrir pour quoi. Les docs listés plus bas ne se chargent pas d'office : on les ouvre au besoin.

## Ce qu'est le projet

Application Android native (Kotlin + Jetpack Compose, persistance Room) : un assistant personnel où l'utilisateur et l'IA disposent des mêmes capacités d'action sur les données. Tout passe par le même dispatcher `resource.operation`, que l'action vienne de l'interface, de l'IA, du scheduler ou du système. `README.md` porte la vision produit et la liste des fonctionnalités livrées.

## Règles du projet

- Commits en anglais. Exception assumée aux règles universelles : les commits étaient en français jusqu'à la bascule, l'historique ancien reste tel quel.
- Commentaires et debug en anglais.
- Le système de strings est obligatoire : `s.tool()`, `s.shared()`. Aucune string affichée en dur dans le code.
- Jamais de mécanisme de repli (fallback) sans validation explicite. Un échec est explicite ou n'est pas.
- Vérifier le pattern dans la doc avant d'implémenter.
- Aucun code legacy laissé derrière : ce qui est remplacé est supprimé dans le même geste.
- Commenter abondamment, pour la relecture ultérieure.
- Compiler avec `./gradlew compileDebugKotlin` et lire la sortie via `grep '^e:|^Error:|^ERROR:|BUILD SUCCESSFUL|BUILD FAILED'`.
- Générer les strings avec `./gradlew generateStringResources`.
- Respecter l'architecture décrite dans les docs ci-dessous.

## Docs complémentaires

- `docs/CORE.md` — architecture système : dispatcher de commandes, registre de services, scheduling, pattern de découverte, strings, logs.
- `docs/DATA.md` — navigation hiérarchique dans les données, validation par schéma, event sourcing, versioning et migrations.
- `docs/UI.md` — composants d'interface, formulaires, thèmes, patterns Compose.
- `docs/TOOLS.md` — architecture des outils (tooltypes), extension sans toucher au core.
- `docs/AI.md` — système IA : machine à états, sessions, prompts, automations, providers.
- `docs/design/` — conception transitoire, écrite pour être implémentée puis élaguée. Le code et les commits deviennent le registre.

## Ressources hors dépôt

- `icons-source/lucide/` — bibliothèque d'icônes Lucide, gitignorée : c'est la source où l'on pioche. Ajouter une icône = copier son SVG dans `app/src/main/java/com/assistant/themes/default/icons/` et inscrire son nom dans `app/src/main/assets/standard_icons.txt` ; `app/build.gradle.kts` génère le drawable au build.
