# Référence — Assistant

Point d'entrée de la documentation. Ce fichier dit ce qu'est le projet, les règles qu'on y tient, et quel doc ouvrir pour quoi. Les docs listés plus bas ne se chargent pas d'office : on les ouvre au besoin.

## Ce qu'est le projet

Application Android native (Kotlin + Jetpack Compose, persistance Room) : un assistant personnel où l'utilisateur et l'IA disposent des mêmes capacités d'action sur les données. Tout passe par le même dispatcher `resource.operation`, que l'action vienne de l'interface, de l'IA, du scheduler ou du système. `README.md` porte la vision produit et la liste des fonctionnalités livrées.

## Règles du projet

- Commits en anglais. Exception assumée aux règles universelles : les commits étaient en français jusqu'à la bascule, l'historique ancien reste tel quel.
- Commentaires et debug en anglais.
- Le système de strings est obligatoire : `s.tool()`, `s.shared()`. Aucune string affichée en dur dans le code.
- Jamais de mécanisme de repli (fallback) sans validation explicite. Un échec est explicite ou n'est pas.
- Une clé écrite dans une chaîne s'écrit en snake_case — paramètre de service, clé de résultat, champ de schéma, nom de réglage, colonne. Le camelCase est réservé aux identifiants Kotlin. Seule exception : le vocabulaire JSON Schema (`additionalProperties`, `minLength`…), qui n'est pas le nôtre. Le reliquat à renommer est listé dans `scripts/key_case_baseline.txt` et gardé par `./scripts/check_key_case.py`, qui échoue si une clé camelCase nouvelle apparaît.
- Vérifier le pattern dans la doc avant d'implémenter.
- Aucun code legacy laissé derrière : ce qui est remplacé est supprimé dans le même geste.
- Commenter abondamment, pour la relecture ultérieure.
- Compiler avec `./run compile` — type-check seul, qui n'affiche que les erreurs et le verdict.
- Lancer les tests avec `./run test`, et laisser la suite verte avant de committer. Un test mérite d'exister s'il remplace une vérification sur l'appareil : c'est le critère, pas un taux de couverture.
- Quand une spec de `docs/design/` est élaguée, ses garanties deviennent des tests. Le code dit comment ; le test dit ce qui avait été promis, et c'est la seule forme de documentation qui ne peut pas mentir.
- Générer les strings avec `./gradlew generateStringResources`.
- Respecter l'architecture décrite dans les docs ci-dessous.

## Docs complémentaires

- `docs/CORE.md` — architecture système : dispatcher de commandes, registre de services, scheduling, pattern de découverte, strings, logs.
- `docs/DATA.md` — navigation hiérarchique dans les données, validation par schéma, propagation des modifications, versioning et migrations.
- `docs/UI.md` — composants d'interface, formulaires, thèmes, patterns Compose.
- `docs/TOOLS.md` — architecture des outils (tooltypes), extension sans toucher au core.
- `docs/AI.md` — système IA : machine à états, sessions, prompts, automations, providers.
- `docs/design/` — conception transitoire, écrite pour être implémentée puis élaguée. Le code et les commits deviennent le registre.

## Ressources hors dépôt

- `icons-source/lucide/` — bibliothèque d'icônes Lucide, gitignorée : c'est la source où l'on pioche. Ajouter une icône = copier son SVG dans `app/src/main/java/com/assistant/themes/default/icons/` et inscrire son nom dans `app/src/main/assets/standard_icons.txt`, lancer `./gradlew generateThemeResources` (demande `npx`) et commiter le drawable et `GeneratedThemeResources.kt` produits. Le build ne les régénère pas : il doit tourner sans `npx`.
