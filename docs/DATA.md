# Gestion des Données

Guide technique pour la navigation, validation et manipulation des données dans l'architecture Assistant.

##
## Navigation Hiérarchique

### DataNavigator

Architecture pour navigation dans les données via schémas avec chargement à la demande et résolution conditionnelle.

```kotlin
// DataNavigator.kt
class DataNavigator(private val context: Context) {
    suspend fun getRootNodes(): List<SchemaNode>
    suspend fun getChildren(path: String): List<SchemaNode>
}
```

**Structure** : App → Zones → Outils → Champs avec navigation à la demande.

**Usage** : Permet de naviguer dans la structure des données sans accès direct aux données métier.

### Le pointeur

Un bloc POINTER d'un message désigne une zone ou un outil par son identifiant (`PointerConfig`, jamais un nom) et dit ce qui part avec le message : la config, les entrées, ou rien (une mention). Pour un outil, une période, des filtres par valeur et un choix de champs restreignent ses entrées, jointes ou seulement mentionnées ; une mention d'entrées restreintes donne à l'IA la requête qui les lit. À l'envoi, `EnrichmentProcessor` en tire des requêtes `tool_data.get` ordinaires ; la période d'une automation est relative, recalculée à chaque exécution. L'écran est `PointerSelector` : le fil d'Ariane App › zone › outil, la liste des lieux un niveau plus bas, les cases et ce qui restreint les entrées, et la phrase qui dit ce qui partira.

##
## Champs et entrées

Toute valeur de l'app est un champ d'un type de champ (`core/fields`, `FieldType`) : ce qu'on saisit dans une entrée, l'état qu'y écrivent l'app et ses actions, un réglage, une question de l'IA. Le type de champ porte la forme de la valeur, ses contraintes, son schéma, sa saisie (`FieldInput`), son affichage (`FieldValue`, partout où une valeur se montre) et sa description pour l'IA ; un outil ne porte que ses façons rapides de créer une entrée et ses calculs sur plusieurs entrées.

- **Rangement par auteur** : `name` et `timestamp`, déclarés par le core, vivent en colonnes ; les champs du type d'outil dans `data` ; ceux de l'utilisateur dans `extra` ; l'état dans `state`. Deux auteurs qui font évoluer leurs champs chacun de son côté ne partagent jamais un objet. `id`, `tool_instance_id`, `tooltype` servent à retrouver l'entrée et ne sont pas des champs.
- **Usage de `name` et `timestamp`** : chaque type d'outil les déclare obligatoires, facultatifs ou absents (une note n'a pas de nom) ; absent, rien ne les montre et le schéma refuse une valeur.
- **État** : décrit par des champs pour ses libellés et ses filtres, jamais saisi. Un champ DURÉE peut être « en cours » : `start_duration` écrit l'instant dans `state.running.<data|extra>.<champ>`, `stop_duration` ajoute le temps écoulé à la valeur. La vérité est en base, un chronomètre survit à l'app tuée.
- **Valeur par défaut** : une suggestion, appliquée par qui agit — le formulaire la préremplit, une action rapide l'applique, l'IA la lit dans le schéma. Une définition de champ la porte (`default_value`, tous les types sauf DATE et DATETIME), vérifiée contre le champ lui-même par `FieldConfigValidator` ; la config la saisit avec l'input du champ en cours de définition (`SettingNode.Field.valueOfDefined`). Elle préremplit une nouvelle entrée (`CustomFieldsInput` avec `newEntry`), un raccourci touché d'un suivi, un module de communication, et la valeur demandée par une migration. Le service n'écrit jamais une valeur qu'on ne lui a pas donnée : un champ absent veut dire « pas de réponse », et la saisie le montre ainsi (oui/non sans bouton choisi, curseur sans poignée, cases vides).
- **Affichage des champs de l'utilisateur** : un seul composant, `CustomFieldsDisplay`, que tout outil utilise. Il montre les champs qui ont une valeur et ceux réglés « Toujours afficher » (« Aucune valeur » s'ils sont vides), avec leur nom sauf si la config dit `show_field_labels: false` — un réglage commun à tous les champs d'une config, dont la valeur par défaut vient du type d'outil (`getDefaultShowFieldLabels`). L'écran choisit la disposition selon sa place : `EXPANDED` (chaque champ en bloc, le nom en grand au-dessus), `LINE` (un champ par ligne, le nom en grand puis la valeur sur le reste de la ligne) ou `COMPACT` (deux par ligne, « Nom : valeur » avec le nom en petit ; une valeur longue — un texte de plus de quelques mots ou sur plusieurs lignes, un classement — seule sur sa rangée).
- **Copie de la config dans une entrée** : un fait quand l'entrée doit continuer de dire ce qu'elle disait (l'unité d'un suivi numérique, le titre d'un message envoyé) ; sinon une dérivation, calculée à la lecture et jamais stockée.
- **CHOICE** : ses options sont des groupes `{value, label, color}`, une couleur de la palette du thème faisant une pastille. Multiple, ordonné (un classement, saisi par glisser-déposer) ou ouvert : une valeur nouvelle rejoint les options dans la transaction qui écrit l'entrée — celles d'un champ de l'utilisateur dans `extra_fields`, celles d'un champ du type d'outil là où sa config les garde (`configWithOptionsAdded`) ; l'écran de l'outil relit alors sa config.
- **Unité** : l'unité fixe (`unit`, « km ») n'est pas un réglage de la valeur mais un réglage à part (`FieldTypeSettings.unit`), qu'ajoute qui déclare la valeur quand toutes ses valeurs la partagent : un nombre ou une plage de l'utilisateur, un compteur. Un suivi numérique n'en a pas : chaque entrée porte la sienne (`data.unit`), un choix ouvert sur la liste `units`.
- **DURÉE** : des millisecondes ; la précision et la forme de la config ne décident que de la saisie et de l'affichage.
- **Lire des entrées filtrées** : `tool_data.get` prend des `filters`, `{field, op, value}`, qui doivent tous être vrais. Le champ est un chemin du schéma (`timestamp`, `data.x`, `extra.x`, une clé d'état filtrable) ; les conditions dépendent de son type (`EntryFilters.operatorsFor`), et `absent` / `present` disent s'il a une réponse. Une période est un filtre sur `timestamp`. Les filtres s'exécutent en SQL (`json_extract`), donc une page et son décompte sont ceux des entrées filtrées.
- **Valeurs d'un champ texte** : `tool_data.values` (`tool_instance_id`, `field`, `limit` à 20 par défaut) rend les valeurs qu'un champ texte prend dans les entrées d'un outil, les plus fréquentes d'abord, sans les réponses absentes ni vides (`EntryFilters.values`). C'est ce qu'un filtre propose de choisir au lieu de taper ; un champ d'un autre type est refusé, sa saisie ayant déjà ses valeurs.

Un réglage se déclare avec les mêmes champs, assemblés par un ensemble fixe de formes (`SettingNode`) : champ, groupe, liste, variante (des réglages selon la valeur d'un CHOICE, stockés à plat à côté de lui) et section (de l'affichage seul). Un réglage déclare sa valeur par défaut, qui est aussi le sens de son absence ; il peut être secret (saisi masqué, jamais envoyé à l'IA ni journalisé). L'écran d'une config est le formulaire de sa déclaration (`SettingsForm`), où un type d'outil peut brancher son propre éditeur sur un réglage. Une liste de groupes déclare son résumé (`summary`), les réglages d'un élément qui le représentent : le formulaire montre chaque élément fermé, sur une ligne de ces valeurs, et l'ouvre au toucher de cette ligne ; plusieurs peuvent être ouverts, un élément ajouté s'ouvre.

##
## Validation par schéma

Aucun schéma n'est écrit à la main ni nommé dans une donnée : ils sont générés depuis des déclarations.

- **Entrées** : le schéma se génère depuis les champs que le type d'outil déclare (`getEntryFields`) et les champs de l'utilisateur (`extra_fields`), par `BaseSchemas.getEntrySchema`. `ToolDataService` valide toute écriture, quel que soit l'appelant, après avoir arrondi les nombres à leurs décimales (`NumericPrecision`) ; `FieldValueValidator` ajoute ce qu'un schéma ne sait pas dire (le début d'une plage avant sa fin).
- **Configs d'outil** : le schéma se génère depuis la partie commune (`ToolConfigSettings`) et la partie du type d'outil (`getConfigSettings`), par `SettingsSchemaGenerator` ; une variante (le `type` d'un suivi) devient un `oneOf`. `ToolInstanceService` valide toute écriture de config.
- **Changement de config** : ce qu'il fait aux entrées se déduit des champs d'entrée que donnent l'ancienne et la nouvelle config, `data` comme `extra` (`EntryMigration`) : une valeur qui perd son sens est retirée, une entrée qui perd une valeur obligatoire de `data` est supprimée, et le tout s'écrit dans la même transaction que la config. Retirer des valeurs ou supprimer des entrées est refusé sans `confirm_migration: true`, les comptes sous `migration` ; une entrée sans valeur pour un champ devenu obligatoire prend celle que l'appelant donne dans `fill_values` (`{"data": {"value": 3}}`), sinon le changement est refusé.
- **Zones** : le schéma `zone_config` se génère depuis `ZoneSettings` (nom, description, icône, groupe de l'écran d'accueil, groupes d'outils), et `ZoneService` valide toute écriture ; l'écran de zone est le formulaire de cette déclaration.
- **Réglages de l'app** : chaque catégorie (format, limites IA, validation, écran d'accueil) est déclarée dans `AppSettings` ; `app_config.get` et `app_config.set` lisent et écrivent une catégorie entière, `AppConfigService` la vérifie contre le schéma généré, et l'écran de chaque catégorie est le formulaire de sa déclaration (`AppSettingsScreen`). Un réglage borné s'y déclare en SCALE, réglé au curseur.
- **Automations** : déclarées dans `AutomationSettings` (voir `docs/AI.md`).
- **Lecture** : une config se lit par `ToolConfigSettings.read` (`SettingValues`) ; un réglage absent vaut son défaut déclaré, jamais un repli écrit sur place.
- **Face à l'IA** : un schéma se demande par `tooltype` (config), `tool_instance_id` (entrées) ou `id` (les autres : `zone_config`, `field_type_TEXT`…). Son nom calculé (`tracking_config`, `tracking_data`) ne sert qu'à ne pas renvoyer deux fois le même schéma.
- **Ce que l'IA lit d'un schéma** : pas le JSON Schema, qui ne sert qu'à valider, mais sa notation (`SchemaNotation`) : une ligne par valeur, avec son libellé et sa description une seule fois, et les réglages communs d'une variante écrits une fois au lieu d'une par option. Une liste de définitions de champ y renvoie à la section Fields du prompt, qui la décrit une fois. Un mot-clé que la notation ne sait pas écrire fait échouer l'envoi plutôt que de disparaître.

Un champ marqué `"system_managed": true` est à l'app de le produire, jamais à l'appelant. Dans `data`, le service retire tout champ marqué de ce qu'on lui envoie (`SystemManagedFields`) ; à la racine, il ne lit que des paramètres nommés.

La validation que font les écrans avant d'appeler le service sert à répondre tôt dans le formulaire ; elle n'est pas la garde.

**Champ `id`** : pas `system_managed`, puisqu'une modification le nomme pour désigner l'entrée. Une création l'ignore : le service génère l'identifiant.

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

**Metadata dans fichiers** :
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
4. Insertion données transformées

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
