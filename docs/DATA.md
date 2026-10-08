# Gestion des Données

Guide technique pour la navigation, validation et manipulation des données dans l'architecture Treelune.

##
## Navigation Hiérarchique

### La navigation vers une chose

Un seul chemin mène à une chose de l'app, pour le pointeur comme pour un champ RÉFÉRENCE : `ThingBrowser`, le fil d'Ariane App › zone › outil › entrée (`ThingPath`), chaque étape un retour, et les lieux un niveau plus bas, parmi ceux qui mènent à ce que la cible accepte (`ReferenceTarget`). Il les lit par `references.choices` : à l'app, les zones — ou, quand seules les entrées de certains outils sont acceptées, ces outils directement ; dans une zone, ses outils ; dans un outil, ses entrées, cherchées par leur libellé. Le navigateur ne fait que déplacer le chemin ; ce que vaut le lieu atteint appartient à qui l'utilise.

### Le pointeur

Un bloc POINTER d'un message porte une sélection du cœur (`EntrySelection` : une référence, jamais un nom, sa période, ses filtres, ses champs) et dit ce qui part avec le message (`PointerConfig`, `{"selection", "attach"}`) : la config, les entrées, ou rien (une mention). Pour un outil, une période, des filtres par valeur et un choix de champs restreignent ses entrées, jointes ou seulement mentionnées ; une mention d'entrées restreintes donne à l'IA la requête qui les lit. À l'envoi, `EnrichmentProcessor` en tire des requêtes `tool_data.get` ordinaires ; la période d'une automation est relative, recalculée à chaque exécution. L'écran est `PointerSelector` : le navigateur (zone ou outil), les cases et ce qui restreint les entrées, et la phrase qui dit ce qui partira.

##
## Champs et entrées

Toute valeur de l'app est un champ d'un type de champ (`core/fields`, `FieldType`) : ce qu'on saisit dans une entrée, l'état qu'y écrivent l'app et ses actions, un réglage, une question de l'IA. Le type de champ porte la forme de la valeur, ses contraintes, son schéma, sa saisie (`FieldInput`), son affichage (`FieldValue`, partout où une valeur se montre) et sa description pour l'IA ; un outil ne porte que ses façons rapides de créer une entrée et ses calculs sur plusieurs entrées.

- **Rangement par auteur** : `name` et `timestamp`, déclarés par le core, vivent en colonnes ; les champs du type d'outil dans `data` ; ceux de l'utilisateur dans `extra` ; l'état dans `state`. Deux auteurs qui font évoluer leurs champs chacun de son côté ne partagent jamais un objet. `id`, `tool_instance_id`, `tooltype` servent à retrouver l'entrée et ne sont pas des champs.
- **Usage de `name` et `timestamp`** : chaque type d'outil les déclare obligatoires, facultatifs ou absents, au besoin selon la config (une note n'a de nom, son titre, que si sa config le demande) ; absent, rien ne les montre et le schéma refuse une valeur. En modification, un `name` envoyé à null efface le nom, comme une clé de `data`, `extra` ou `state`. À la création, et pour un objet écrit en entier (une config, des réglages), une clé à null est une valeur non donnée : l'écriture la retire avant de valider et d'enregistrer (`JsonNulls`), et le validateur vérifie ce qu'on lui donne, rien de moins — ce qu'il vérifie est ce qui s'enregistre. Les éléments d'une liste restent tels qu'envoyés.
- **État** : décrit par des champs pour ses libellés et ses filtres, jamais saisi. Un type d'outil dont les entrées vivent par un statut déclare comment elles commencent (`EntryStart`, dans `EntryFields`) : qui crée une entrée, un écran, l'IA, un client MCP, donne son statut seul parmi ceux déclarés, et le type d'outil pose le reste de l'état (`EntryStart.decide`, appelé par `ToolDataService`) ; ce que l'app écrit elle-même (un programmateur, l'opération de l'outil, la démo) garde l'état qu'elle donne ; une entrée sans statut est refusée, d'où qu'elle vienne. Messages commence « à envoyer » (posée à la main), Questionnaire « à remplir » ou « rempli » ; Objectif et Séance refusent toute autre création, leurs tentatives et séances étant faites par l'outil. Un champ DURÉE peut être « en cours » : `start_duration` écrit l'instant dans `state.running.<data|extra>.<champ>`, `stop_duration` ajoute le temps écoulé à la valeur. La vérité est en base, un chronomètre survit à l'app tuée.
- **Valeur par défaut** : une suggestion, appliquée par qui agit — le formulaire la préremplit, une action rapide l'applique, l'IA la lit dans le schéma. Une définition de champ la porte (`default_value`, tous les types sauf DATE et DATETIME), vérifiée contre le champ lui-même par `FieldConfigValidator` ; la config la saisit avec l'input du champ en cours de définition (`SettingNode.Field.valueOfDefined`). Elle préremplit une nouvelle entrée (`CustomFieldsInput` avec `newEntry`), un raccourci touché d'un suivi, un module de communication, et la valeur demandée par une migration. Le service n'écrit jamais une valeur qu'on ne lui a pas donnée : un champ absent veut dire « pas de réponse », et la saisie le montre ainsi (oui/non sans bouton choisi, curseur sans poignée, cases vides).
- **Affichage des champs de l'utilisateur** : un seul composant, `CustomFieldsDisplay`, que tout outil utilise. Il montre les champs qui ont une valeur et ceux réglés « Toujours afficher » (« Aucune valeur » s'ils sont vides), avec leur nom sauf si la config dit `show_field_labels: false` — un réglage commun à tous les champs d'une config, dont la valeur par défaut vient du type d'outil (`getDefaultShowFieldLabels`). L'écran choisit la disposition selon sa place : `EXPANDED` (chaque champ en bloc, le nom en grand au-dessus), `LINE` (un champ par ligne, le nom en grand puis la valeur sur le reste de la ligne) ou `COMPACT` (deux par ligne, « Nom : valeur » avec le nom en petit, une valeur longue revenant à la ligne dans sa moitié).
- **Copie de la config dans une entrée** : un fait quand l'entrée doit continuer de dire ce qu'elle disait (l'unité d'un suivi numérique, le titre d'un message envoyé) ; sinon une dérivation, calculée à la lecture et jamais stockée.
- **CHOICE** : ses options sont des groupes `{value, label, color}`, une couleur de la palette du thème faisant une pastille. Multiple, ordonné (un classement, saisi par glisser-déposer) ou ouvert : une valeur nouvelle rejoint les options dans la transaction qui écrit l'entrée — celles d'un champ de l'utilisateur dans `extra_fields`, celles d'un champ du type d'outil là où sa config les garde (`configWithOptionsAdded`) ; l'écran de l'outil relit alors sa config.
- **Unité** : l'unité fixe (`unit`, « km ») n'est pas un réglage de la valeur mais un réglage à part (`FieldTypeSettings.unit`), qu'ajoute qui déclare la valeur quand toutes ses valeurs la partagent : un nombre ou une plage de l'utilisateur, un compteur. Un suivi numérique n'en a pas : chaque entrée porte la sienne (`data.unit`), un choix ouvert sur la liste `units`.
- **DURÉE** : des millisecondes ; la précision et la forme de la config ne décident que de la saisie et de l'affichage.
- **RÉFÉRENCE** : une autre chose de l'app par sa sorte et son id, `{"kind": "ENTRY", "id": …}` (`Reference`, sans id pour l'app), jamais par son nom. La config dit ce qu'elle accepte, `target: {kinds, tool_instances?}` (`ReferenceTarget`), les outils restreignant une entrée aux leurs ; chacun est lui-même une RÉFÉRENCE à une instance d'outil. Le nom se lit à l'affichage par le service du cœur `references` (`names`), l'écran comme l'IA, qui la reçoit avec `name` ou `"deleted": true` et la renvoie sans (`ModelValues`) ; une cible supprimée garde son adresse. La saisie (`ReferencePicker`) passe par le navigateur et se confirme quand le lieu atteint est d'une sorte acceptée ; restreinte aux entrées d'un seul outil, elle s'ouvre dans cet outil. À l'écriture, `ToolDataService` vérifie qu'une valeur nouvelle ou changée désigne une chose qui existe, et pour une entrée un outil accepté ; restreindre la cible retire les valeurs qui n'y entrent plus, sous `confirm_migration` (`FieldChange.ReferenceTargetNarrowed`). Un filtre la compare par l'id qu'elle porte (`=`, `absent`, `present`).
- **Lire des entrées filtrées** : `tool_data.get` prend des `filters`, des conditions posées à chaque entrée (`Conditions`, `{"left": {"field"}, "op", "right": {"constant"}}`, une paire de constantes pour `between`), qui doivent toutes être vraies. Le champ est un chemin du schéma (`timestamp`, `data.x`, `extra.x`, une clé d'état filtrable) ; les conditions dépendent de son type (`EntryFilters.operatorsFor`, pas de `=` sur un instant), et `absent` / `present`, sans `right`, disent s'il a une réponse. Un filtre ne compare qu'à des valeurs écrites : une variable ou une lecture se lit d'abord. Une période y arrive en filtres sur `timestamp`. Une date écrite d'un filtre ou une borne de période est fixe (sa forme stockée) ou relative, un objet qui dit son bord (`{"relative": {"unit": "DAY", "offset": -1, "edge": "START"}}`, `{"relative": "NOW"}`, `TimePoint`), résolu par `TimeResolver` sur la référence du contexte ; l'opérateur ne choisit jamais de bord. Les filtres s'exécutent en SQL (`json_extract`), donc une page et son décompte sont ceux des entrées filtrées.
- **Lecture du cœur** : `readings.read` tire une valeur des entrées d'une seule instance d'outil — une `selection` (sa période et ses filtres, les dates relatives résolues sur la `reference` que donne le lecteur, en millisecondes), un `field` (absent pour compter) et une `reduction` permise par son type (`Reduction.forType` : dernière, somme, moyenne, min, max pour un nombre ou une durée ; dernière, la plus tôt, la plus tard pour une date ou une heure ; compte sans champ). Le résultat garde le type et les réglages de sa source, le compte est un nombre entier (`FieldReading`). Sans entrée, somme et compte valent 0 et le reste échoue ; une entrée sans réponse au champ fait échouer, en la nommant — le filtre `present` l'écarte si c'est ce qu'on veut dire ; une source supprimée échoue aussi. Un échec est une réponse (`failure` : raison, champ, entrées, texte), dont chaque lecteur décide. Une DURÉE en cours compte jusqu'à l'instant lu : sa valeur enregistrée plus le temps de son chronomètre. Avec `at` à la place de `reference`, la lecture se fait à plusieurs instants (une grille de Graphique, un pas chacun) et rend `values`, une valeur ou un échec par instant : sans date relative dans les filtres, une seule lecture des entrées les sert tous, chaque instant gardant celles de sa période (`EntrySelection.filtersMove`).
- **Variables** : une valeur nommée que tout lecteur lit, sans type d'outil entre eux (`core/variables`, table `variables`, service `variables`). Elle vit dans une zone (supprimée avec elle, rangée dans un groupe comme une automation) et lit n'importe quelle zone ; son nom est unique dans l'app et s'écrit dans une formule. Une constante (`{"kind": "CONSTANT", "value", "field"}`) ou une formule (`{"kind": "FORMULA", "formula", "terms", "field"}`) : le texte (`Formula`, `+ - × ÷`, parenthèses, `hours()`, `minutes()`, `seconds()`, noms anglais stockés et écrits par l'IA, montrés et acceptés à l'écran dans la langue de l'app) porte sur les noms de ses termes — une lecture du cœur sans test, éventuellement une formule par entrée qui lit à travers une RÉFÉRENCE (`quantité × aliment.kcal_100g / 100`), une constante, une autre variable — et sur d'autres variables nommées directement, enregistrées par leur id (`{var:ID}`) et montrées sous leur nom actuel. Le champ (NUMERIC, SCALE ou DURÉE) se déduit de ce qu'elle lit s'il n'est pas donné (`FormulaType`). Le service refuse un nom pris ou illisible, un nom inconnu dans la formule, un terme illisible, une boucle (en nommant le chemin). Rien n'est enregistré de la valeur : `variables.evaluate` (ou `readings.read` avec `variable` et `at`) la calcule à chaque instant demandé (`VariableEvaluator`), chaque période relative à cet instant ; un terme en échec, une division par zéro, une variable supprimée font échouer, avec leurs causes.
- **Import** : le service du cœur `imports`, pour tout outil, lit des lignes de valeurs texte nommées par leur colonne (un CSV : `CsvReader`, séparateur `,` `;` ou tabulation), donné en texte ou par l'id d'un fichier joint à un message (service `files`, table `attached_files`) — ce par quoi l'IA importe, avec `IMPORT_PLAN` et `IMPORT_DATA`, sans lire les lignes. Chaque type de champ porte la liste fermée et nommée de ses écritures (`Writing` : décimale point ou virgule, jour/mois/année, `1h25`, `85 min`, oui/non…), qui lit une cellule ou dit pourquoi ; une écriture absente de la liste n'est acceptée nulle part. `detect` propose une déclaration en lisant tout le fichier (`ImportPlanner.detect`) : le nom comme clé là où les noms sont uniques, un champ existant par son nom ou son libellé, sinon un nouveau champ du type le plus exigeant dont une écriture lit toutes les cellules (TEXT en dernier, CHOICE quand les valeurs sont peu nombreuses, ses options tirées du fichier ; un en-tête `kcal [NUMERIC]` fixe le type) ; deux écritures qui lisent tout différemment ne sont pas choisies. Elle dit aussi ce qui manque à sa proposition telle quelle (`missing`), avec les contrôles d'`apply` : une colonne à trancher, aucune colonne pour le nom quand le type d'outil l'exige — par sa clé si ses noms sont uniques, par le champ `name` sinon. `apply` n'applique qu'une déclaration complète, qui nomme sinon ce qui manque, et refuse un doublon de clé dans le fichier : les nouveaux champs se créent dans l'ordre des colonnes, puis les lignes s'écrivent par `tool_data`, en deux batch — mises à jour celles dont la clé trouve une entrée, créées les autres. Une cellule vide est une absence de réponse ; une cellule illisible ou une ligne que le service refuse refuse sa ligne seule, avec la raison ; le compte-rendu dit créées, mises à jour, refusées. Les nouveaux champs et toutes les lignes s'écrivent dans une même transaction : un échec en route n'écrit rien ; une ligne que le service refuse est une réponse, comptée, qui n'annule pas les autres. L'écran (`ImportDialog`) montre la déclaration colonne par colonne avec un exemple lu, les ambiguïtés à trancher et les lignes qui seraient refusées ; il refait ces contrôles à chaque changement, en dit les manques en tête, et n'importe qu'une déclaration complète.
- **Valeurs d'un champ texte** : `tool_data.values` (`tool_instance_id`, `field`, `limit` à 20 par défaut) rend les valeurs qu'un champ texte prend dans les entrées d'un outil, les plus fréquentes d'abord, sans les réponses absentes ni vides (`EntryFilters.values`). C'est ce qu'un filtre propose de choisir au lieu de taper ; un champ d'un autre type est refusé, sa saisie ayant déjà ses valeurs.

Un réglage se déclare avec les mêmes champs, assemblés par un ensemble fixe de formes (`SettingNode`) : champ, groupe, liste, variante (des réglages selon la valeur d'un CHOICE, stockés à plat à côté de lui), section (de l'affichage seul), et les briques qu'une config garde entières, chacune stockée sous la forme de sa brique et saisie par son sélecteur : condition (jugée une fois, un terme de chaque côté, `SettingNode.Condition`), terme (`SettingNode.Term`, parmi les sortes que le contexte prend), sélection d'entrées d'un outil sans sa période (`SettingNode.Selection`, `SelectionSetting`) et période (`SettingNode.Period`). Un réglage peut nommer un champ des lignes que la config décrit là où il se trouve (`rowField`, et une condition `onRow` posée sur chaque ligne, qui compare ces champs entre eux ou à une valeur écrite) : le propriétaire du formulaire donne ces champs selon l'endroit (`RowFields`) — les colonnes d'une couche de Graphique. Un groupe facultatif rempli se retire d'un bouton. Un réglage déclare sa valeur par défaut, qui est aussi le sens de son absence ; il peut être secret (saisi masqué, jamais envoyé à l'IA ni journalisé). L'écran d'une config est le formulaire de sa déclaration (`SettingsForm`), où le cœur branche ses propres éditeurs sur quelques réglages (l'icône, le groupe, une planification). Une liste de groupes déclare son résumé (`summary`), les réglages d'un élément qui le représentent : le formulaire montre chaque élément fermé, sur une ligne de ces valeurs, et l'ouvre au toucher de cette ligne ; un élément ajouté s'ouvre. Un groupe ou un élément qui contient un groupe, une liste ou une brique s'ouvre sur une page à lui, en pleine largeur sous le chemin depuis la racine ; sa ligne dans la page de son parent dit aussi ce que lisent ses termes et ses sélections, et compte ses listes. Le retour du téléphone remonte d'une page ; toutes les pages modifient le même brouillon.

##
## Validation par schéma

Aucun schéma n'est écrit à la main ni nommé dans une donnée : ils sont générés depuis des déclarations.

- **Entrées** : le schéma se génère depuis les champs que le type d'outil déclare (`getEntryFields`) et les champs de l'utilisateur (`extra_fields`), par `BaseSchemas.getEntrySchema`. `ToolDataService` valide toute écriture, quel que soit l'appelant, après avoir arrondi les nombres à leurs décimales (`NumericPrecision`) ; `FieldValueValidator` ajoute ce qu'un schéma ne sait pas dire (le début d'une plage avant sa fin).
- **Configs d'outil** : le schéma se génère depuis la partie commune (`ToolConfigSettings`) et la partie du type d'outil (`getConfigSettings`), par `SettingsSchemaGenerator` ; une variante (le `type` d'un suivi) devient un `oneOf`. `ToolInstanceService` valide toute écriture de config.
- **Changement de config** : ce qu'il fait aux entrées se déduit des champs d'entrée que donnent l'ancienne et la nouvelle config, `data` comme `extra` (`EntryMigration`) : une valeur qui perd son sens est retirée, une entrée qui perd une valeur obligatoire de `data` est supprimée, et le tout s'écrit dans la même transaction que la config. Retirer des valeurs ou supprimer des entrées est refusé sans `confirm_migration: true`, les comptes sous `migration` ; une entrée sans valeur pour un champ devenu obligatoire prend celle que l'appelant donne dans `fill_values` (`{"data": {"value": 3}}`), sinon le changement est refusé.
- **Zones** : le schéma `zone_config` se génère depuis `ZoneSettings` (nom, description, icône, groupe de l'écran d'accueil, groupes d'outils), et `ZoneService` valide toute écriture ; l'écran de zone est le formulaire de cette déclaration.
- **Groupes** : un élément rangé a pour groupe `null` ou un groupe qui existe (`Groups`) — une zone, un groupe de l'écran d'accueil ; un outil, une automation ou une variable, un groupe d'outils de sa zone. Les services refusent un groupe inconnu et la suppression d'un groupe encore tenu, en nommant ce qui le tient ; un renommage arrive avec la liste (`renames`, que l'éditeur de liste tire de ses noms d'origine, `ListOrigins`) et suit chez tout ce qui tient l'ancien nom ; changer de zone sans groupe donné vide le groupe.
- **Réglages de l'app** : chaque catégorie (format, limites IA, validation, écran d'accueil, démo, interface) est déclarée dans `AppSettings` ; `app_config.get` et `app_config.set` lisent et écrivent une catégorie entière, `AppConfigService` la vérifie contre le schéma généré, et l'écran de chaque catégorie est le formulaire de sa déclaration (`AppSettingsScreen`). Un réglage borné s'y déclare en SCALE, réglé au curseur.
- **Automations** : déclarées dans `AutomationSettings` (voir `docs/AI.md`).
- **Lecture** : une config se lit par `ToolConfigSettings.read` (`SettingValues`) ; un réglage absent vaut son défaut déclaré, jamais un repli écrit sur place.
- **Face à l'IA** : un schéma se demande par `tooltype` (config), `tool_instance_id` (entrées) ou `id` (les autres : `zone_config`, `field_type_TEXT`…). Son nom calculé (`tracking_config`, `tracking_data`) ne sert qu'à ne pas renvoyer deux fois le même schéma.
- **Ce que l'IA lit d'un schéma** : pas le JSON Schema, qui ne sert qu'à valider, mais sa notation (`SchemaNotation`) : une ligne par valeur, avec son libellé et sa description une seule fois, et les réglages communs d'une variante écrits une fois au lieu d'une par option. Une liste de définitions de champ y renvoie à la section Fields du prompt, qui la décrit une fois. Un mot-clé que la notation ne sait pas écrire fait échouer l'envoi plutôt que de disparaître.

Un champ marqué `"system_managed": true` est à l'app de le produire, jamais à l'appelant. Dans `data`, le service retire tout champ marqué de ce qu'envoie un écran ou l'IA, et le garde quand c'est l'app elle-même qui écrit — un service depuis sa propre opération, un planificateur, l'origine `SYSTEM` (`Origin.byTheApp`, `SystemManagedFields`) ; un import n'offre pas ces champs à ses colonnes. À la racine, il ne lit que des paramètres nommés.

La validation que font les écrans avant d'appeler le service sert à répondre tôt dans le formulaire ; elle n'est pas la garde.

**Champ `id`** : pas `system_managed`, puisqu'une modification le nomme pour désigner l'entrée. Une création le génère ; seule l'app elle-même en donne un, préfixé `demo-`, pour la démo (`GivenId`) — d'un écran ou de l'IA, un `id` donné à une création est refusé.

##
## Propagation des modifications

Une écriture passe par le service, qui écrit en base via son DAO puis signale le changement avec `DataChangeNotifier` — les écrans qui en dépendent se rechargent. Il n'y a pas de journal d'événements : aucune table n'enregistre les modifications, et l'état en base est la seule source. Ce que l'utilisateur et l'IA lisent comme un historique, ce sont les entrées elles-mêmes et les sessions, pas une suite d'événements rejouable.

### Verbalisation

Système de templates pour actions, états et résultats :

```kotlin
// Template
"[source] [verb] le titre [old_value] en [new_value]"

// Résultat
"L'IA a modifié le titre Blup en Blip"
```

**Usage** : Historique, validation utilisateur, feedback IA.

##
## Versioning et Migrations

### Sources de vérité uniques

**build.gradle.kts** :
- `versionCode` = entier monotone (10, 11, 12...) pour comparaisons
- `versionName` = string SemVer ("0.3.0", "0.4.0"...) pour affichage utilisateur
- Accessibles via `BuildConfig.VERSION_CODE` et `BuildConfig.VERSION_NAME`

**AppDatabase** :
- `@Database(version = 10)` = version schéma SQL
- `companion object { const val VERSION = 10 }` = accessible à runtime
- Indépendante de versionCode (peut rester stable entre releases)

### Types de migrations

#### Migration SQL (Type 1)

**Quand** : Schéma SQL change (ALTER TABLE, CREATE TABLE, etc.)

**Géré par** : Room Migrations manuelles dans AppDatabase.kt

**Exemple** :
```kotlin
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE zones ADD COLUMN icon TEXT")
    }
}
```

**Flow** :
1. Incrémenter `@Database(version = 11)` et `const val VERSION = 11`
2. Écrire migration SQL dans AppDatabase companion object
3. Ajouter à `.addMigrations()` dans getDatabase()
4. Room détecte et exécute automatiquement au démarrage

#### Migration JSON (Type 2)

**Quand** : Format JSON non rétrocompatible (renommage champs, restructuration)

**Géré par** : JsonTransformers centralisé

**Exemple** : `{"unit": "kg"}` → `{"measurement_unit": "kg"}`

**Flow** :
1. Écrire transformation dans JsonTransformers.kt
2. Appliquée lors import backup automatiquement
3. Optionnellement appelable dans migration Room si nécessaire

### JsonTransformers - Architecture centralisée

**Fichier unique** : `core/versioning/JsonTransformers.kt`

**Fonctions publiques** :
```kotlin
transformToolConfig(json, tooltype, fromVersion, toVersion): String
transformToolData(json, tooltype, fromVersion, toVersion): String
transformAppConfig(json, fromVersion, toVersion): String
```

**Application séquentielle** : Boucle `for (v in fromVersion until toVersion)` applique tous les transformers intermédiaires

**Exemple** :
```kotlin
private fun transformTrackingConfig(json: JSONObject, version: Int): JSONObject {
    return when (version) {
        10 -> {
            // Migrate from v10 to v11
            if (json.has("unit")) {
                json.put("measurement_unit", json.getString("unit"))
                json.remove("unit")
            }
            json
        }
        else -> json // No migration for this version
    }
}
```

**Réutilisation** : Mêmes transformers utilisés par :
- **Migration Room** : Transforme JSONs en DB au démarrage (une fois par version)
- **Import backup** : Transforme JSONs de vieux backups
- Import partiel futur

**Application au démarrage** : Changement de format JSON REQUIERT migration Room pour transformer les données existantes. Les transformers sont appelés dans la migration pour mettre à jour les JSONs stockés.

**Lifecycle** : Transformers conservés indéfiniment (support vieux backups)

### Format export/import

**Le fichier** : un zip, `backup.json` (les tables, ci-dessous) et `images/<id>.jpg`, les images jointes aux messages copiées fichier par fichier, jamais dans le JSON. `BackupService` écrit et lit lui-même le fichier choisi (son `uri`). Une image dont le fichier manque est une erreur que la sauvegarde recopie telle quelle : l'export part quand même, sa ligne sans fichier, nommée dans `missing_images`, et le dit ; l'import accepte une ligne sans fichier qu'elle nomme, et refuse toute autre, signe d'une sauvegarde abîmée. L'import accepte aussi l'ancien `.json` seul ; il pose les images du zip à part (`files/attachments.import/`), et remplace le dossier des images dans le même geste que les tables.

**Metadata dans `backup.json`** :
```json
{
  "metadata": {
    "export_version": 10,
    "export_timestamp": 1234567890,
    "db_schema_version": 10
  },
  "data": { ... }
}
```

**Process import** :
1. Vérifier `export_version` vs `BuildConfig.VERSION_CODE`
2. Si `export_version > VERSION_CODE` → Erreur "version trop récente"
3. Si `export_version < VERSION_CODE` → Appliquer JsonTransformers
4. Insertion données transformées, puis le dossier des images remplacé par celui de la sauvegarde

**En DB** : JSONs purs sans metadata (version dans fichiers export uniquement)

### Règles de développement

**Versioning** :
- `BuildConfig.VERSION_CODE` = source unique version app
- `AppDatabase.VERSION` = version DB (indépendante de app version)
- Pas de metadata en DB (version dans fichiers export uniquement)

**Migrations SQL** :
- Migrations manuelles explicites dans AppDatabase
- Pas de fallbackToDestructiveMigration (migrations obligatoires)
- Migrations conservées historiquement

**Transformations JSON** :
- Centralisées dans JsonTransformers.kt
- Réutilisées partout (imports + migrations Room)
- Conservées indéfiniment (historique complet)
- Application séquentielle automatique

##
## Règles de Développement

### Patterns d'Implémentation

#### Service Implementation
- Hériter `ExecutableService`
- Validation par le service contre le schéma généré (voir « Validation par schéma »)
- Logs d'erreur explicites et gestion token cancellation

#### Discovery Pattern
- Jamais d'imports hardcodés dans Core
- Services découverts via ToolTypeManager
- Extension automatique par ajout au Scanner
- ToolTypes déclarent `getEntryFields` et `getConfigSettings` ; leurs schémas sont générés

#### Data Consistency
- Écriture par le service, puis notification via `DataChangeNotifier` (pas de journal d'événements)
- Validation centralisée par les services, contre des schémas générés

---

*L'architecture de données garantit cohérence, validation automatique et extensibilité via patterns découplés.*
