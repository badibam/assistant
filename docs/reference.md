# Référence — Assistant

Point d'entrée de la documentation. Ce fichier dit ce qu'est le projet, les règles qu'on y tient, et quel doc ouvrir pour quoi. Les docs listés plus bas ne se chargent pas d'office : on les ouvre au besoin.

## Ce qu'est le projet

Application Android native (Kotlin + Jetpack Compose, persistance Room) : un assistant personnel où l'utilisateur et l'IA disposent des mêmes capacités d'action sur les données. Tout passe par le même dispatcher `resource.operation`, que l'action vienne de l'interface, de l'IA, du scheduler ou du système. `README.md` porte la vision produit et la liste des fonctionnalités livrées.

## Règles du projet

- Commits en anglais. Exception assumée aux règles universelles : les commits étaient en français jusqu'à la bascule, l'historique ancien reste tel quel.
- Commentaires et debug en anglais.
- Le système de strings est obligatoire : `s.tool()`, `s.shared()`. Aucune string affichée en dur dans le code.
- Une string vit dans deux fichiers qui se nomment par leur langue : `shared.xml` et le `strings.xml` d'un outil portent l'anglais, qui est la langue par défaut des ressources ; `shared-fr.xml` et `strings-fr.xml` portent le français. Android résout la bonne langue tout seul — `StringsManager` passe par `context.resources`, donc rien dans le code ne connaît de locale. Les deux fichiers portent exactement les mêmes clés, et `scripts/check_string_locales.py` le vérifie à chaque `./run test`. Un fichier sans traduction ne part que dans la langue par défaut, où toutes les autres le rejoignent par repli : c'est le cas de `ai_prompt_chunks.xml`, qui part à l'IA et non à l'écran.
- Jamais de mécanisme de repli (fallback) sans validation explicite. Un échec est explicite ou n'est pas.
- Une date et une durée vivent en millisecondes partout — interface, dispatcher, services, base. L'ISO 8601 (`2025-03-15T14:30:00+01:00`, `PT1H25M`) et les périodes relatives n'existent qu'en face de l'IA : `AICommandProcessor` et `CommandTransformer` les lisent à l'entrée, `CommandExecutor` les produit à la sortie. Le schéma du stockage marque un instant par `"format": "epoch-millis"` et une durée par `"format": "duration-millis"` ; `SchemaModelView` en dérive le schéma que lit l'IA, et `ModelValues` convertit les valeurs d'une entrée aux endroits que ces marques désignent — jamais d'après le nom d'une clé ni l'allure d'une valeur. Les autres résultats (zones, outils, date courante) ne portent un instant que sous `timestamp`, `created_at` ou `updated_at`, que `DateTimeConverter` connaît. Les types DATE et TIME restent des chaînes — un jour et une heure ne sont pas des instants.
- Un seul fuseau : celui que renvoie `DateTimeConfig.getZoneId()` — le réglage de l'app s'il y en a un, celui du téléphone sinon. Tout calcul ou affichage de date passe par lui : `DateUtils` (dont `calendarAt()` pour un `Calendar`, et `format()` pour un motif libre) et `DateTimeConverter` le prennent en paramètre. `Calendar.getInstance()`, `SimpleDateFormat`, `ZoneId.systemDefault()` et un `now()` sans fuseau prennent celui du téléphone : `scripts/check_timezone.py` les refuse.
- Une clé s'écrit en snake_case — paramètre de service, clé de résultat, champ de schéma, nom de réglage, colonne. Le camelCase est réservé aux identifiants Kotlin. Une clé vit le plus souvent dans une chaîne, mais pas toujours : une propriété de classe `@Serializable` nomme aussi un champ JSON, et porte donc un `@SerialName` en snake_case — comme `@ColumnInfo` pour une colonne Room. Seule exception : le vocabulaire JSON Schema (`additionalProperties`, `minLength`…), qui n'est pas le nôtre. `./scripts/check_key_case.py` la garde : il ne reste aucune clé en camelCase, et le contrôle échoue dès qu'une nouvelle apparaît.
- Vérifier le pattern dans la doc avant d'implémenter.
- Aucun code legacy laissé derrière : ce qui est remplacé est supprimé dans le même geste.
- Commenter abondamment, pour la relecture ultérieure.
- Compiler avec `./run compile` — type-check seul, qui n'affiche que les erreurs et le verdict.
- Lancer les tests avec `./run test`, qui passe d'abord cinq contrôles de sources : la casse des clés (`scripts/check_key_case.py`), l'accord entre les exemples du prompt L1 et le code (`scripts/check_prompt_examples.py`), la parité des clés entre langues (`scripts/check_string_locales.py`), le fuseau (`scripts/check_timezone.py`), et l'existence de chaque clé de texte que le code demande (`scripts/check_string_keys.py`). Laisser la suite verte avant de committer. Un test mérite d'exister s'il remplace une vérification sur l'appareil : c'est le critère, pas un taux de couverture.
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

## Icônes

- Le vocabulaire est Lucide, en entier : un nom d'icône est un nom Lucide. La source est copiée dans `third_party/lucide/` (SVG, métadonnées, `LICENSE`, `VERSION`). `scripts/generate_icons.py` en tire les drawables `lucide_*`, l'index `assets/icons/index.json` (tags, catégories, anciens noms) et la licence embarquée ; sa sortie est commitée, le build ne génère rien. Mettre Lucide à jour = remplacer `third_party/lucide/`, relancer le script, commiter.
- Toute icône à l'écran est un nom de ce vocabulaire, affiché par `UI.Icon` ou par le thème — jamais un glyphe ni un emoji écrit dans un texte. `IconNamesInCodeTest` vérifie que chaque nom écrit dans le code existe.
- Un thème déclare `iconSource` : `LUCIDE`, ou `OWN` s'il dessine lui-même **toutes** les icônes dans son dossier `icons/` — le script refuse un thème incomplet. `Icons.drawable()` trouve l'image d'un nom, ancien nom compris ; un nom introuvable s'affiche en deux lettres.
