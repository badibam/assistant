# Custom Fields - Document de Conception

## Vue d'ensemble

Système de champs personnalisables ajoutables dynamiquement aux instances d'outils. Réutilisable pour Journal, Données structurées, et potentiellement autres outils futurs.

## Architecture générale

### Stockage des données

**Namespace dédié** :
```json
{
  "id": "entry_123",
  "name": "Salade César",
  "timestamp": 1234567890,
  "data": { /* données spécifiques au tooltype */ },
  "custom_fields": {
    "calories": 350,
    "proteines": 25,
    "glucides": 40
  }
}
```

### Définition des champs (dans config instance)

```json
"custom_fields": [
  {
    "name": "notes_repas",
    "display_name": "Notes sur le repas",
    "description": "Commentaires libres sur le repas",
    "type": "TEXT_UNLIMITED",
    "always_visible": true,
    "config": null
  }
]
```

**Champs FieldDefinition** :
- `name` : Identifiant technique stable (clé dans `custom_fields` des données), généré automatiquement depuis `display_name`, **immutable après création**
- `display_name` : Nom affiché à l'utilisateur (éditable)
- `description` : Documentation du champ (optionnel, pour schéma + UI config)
- `type` : FieldType enum (immutable après création)
- `always_visible` : Boolean - afficher le champ en lecture même s'il est vide
- `config` : Configuration spécifique au type (null pour TEXT_UNLIMITED, requis pour types comme SCALE)

## Validation

### Stratégie : Génération de schéma enrichi

Le schéma de données est enrichi dynamiquement avec les custom fields pour validation via SchemaValidator existant.

**Avantages** :
- Réutilise SchemaValidator (une seule logique)
- Schéma enrichi retournable à l'IA
- Support natif contraintes JSON Schema complexes
- Maintenabilité : pas de système de validation séparé

### API Schemas

**Modification ToolTypeContract** :
```kotlin
fun getSchema(schemaId: String, context: Context, toolInstanceId: String? = null): Schema?
```

**Pattern dans ToolTypes** :
```kotlin
override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
    return when (schemaId) {
        "journal_config" -> createJournalConfigSchema(context)
        "journal_data" -> {
            requireNotNull(toolInstanceId) { "toolInstanceId required for data schema" }
            createJournalDataSchema(context, toolInstanceId)
        }
        else -> null
    }
}

private fun createJournalDataSchema(context: Context, toolInstanceId: String): Schema {
    val content = BaseSchemas.createExtendedDataSchema(
        BaseSchemas.getBaseDataSchema(context),
        specificSchema,
        toolInstanceId,
        context
    )
    return Schema(id = "journal_data", content = content)
}
```

### Enrichissement centralisé

**Nouvelle fonction BaseSchemas** :
```kotlin
fun createExtendedDataSchema(
    baseSchema: String,
    specificSchema: String,
    toolInstanceId: String,
    context: Context
): String
```

**Logique** :
1. Merge base + specific (fonction existante)
2. Charge config instance via Coordinator
3. Appelle `CustomFieldsSchemaGenerator.enrichSchema(merged, config)`
4. Retourne schéma enrichi avec propriétés custom_fields

**Pas de cache pour V1** : Génération à chaque validation. Optimisation future si nécessaire.

### Validation config custom fields

**Point de validation** : Modification config instance (ToolInstanceService.update)

**Validateur** :
```kotlin
object FieldConfigValidator {
    fun validate(fieldDef: FieldDefinition, existingFields: List<FieldDefinition>): ValidationResult
}
```

**Règles métier** :
- **Unicité du `name`** : Pas de collision avec autres fields de l'instance
- **Contraintes inter-champs** : min < max pour SCALE/NUMERIC (types futurs)
- **Cohérence type/config** : CHOICE requiert options[], SCALE requiert min/max/labels, etc.
- **Normalisation `name`** : Validation du format généré (snake_case, ASCII)

**Philosophie** : Validation unique - confiance totale dans validation config, pas de re-validation lors génération schéma.

## Système core/fields/

### Composants mutualisés

**FieldType enum** :
```kotlin
enum class FieldType {
    TEXT_UNLIMITED  // V1 - seul type exposé
    // Extensions futures à ajouter : TEXT, TEXT_LONG, NUMERIC, SCALE, CHOICE, BOOLEAN, RANGE, DATE, TIME, FILE, IMAGE, AUDIO
}
```

**IMPORTANT** : Architecture extensible dès V1. Tous les composants utilisent `when(type)` pour gérer les types, même si seul TEXT_UNLIMITED est implémenté.

**FieldDefinition data class** :
```kotlin
data class FieldDefinition(
    val name: String,                      // Identifiant technique stable snake_case (généré une fois, immutable)
    val displayName: String,               // Nom affiché à l'utilisateur (éditable)
    val description: String?,              // Documentation (optionnel)
    val type: FieldType,                   // Type de champ (immutable après création)
    val alwaysVisible: Boolean,            // Afficher en lecture même si vide (UI décide)
    val config: Map<String, Any>?          // Config spécifique au type (null pour TEXT_UNLIMITED)
)
```

**FieldConfigValidator** :
```kotlin
object FieldConfigValidator {
    fun validate(fieldDef: FieldDefinition, existingFields: List<FieldDefinition>): ValidationResult
}
```

**CustomFieldsSchemaGenerator** :
```kotlin
object CustomFieldsSchemaGenerator {
    fun enrichSchema(baseSchemaJson: String, configJson: String): String
}
```

**FieldInputRenderer** :
```kotlin
// Composant bas niveau : un field individuel en édition
@Composable
fun FieldInput(
    fieldDef: FieldDefinition,
    value: Any?,
    onChange: (Any) -> Unit
)

// Composant haut niveau : tous les custom fields en édition
@Composable
fun CustomFieldsInput(
    fields: List<FieldDefinition>,
    values: Map<String, Any?>,
    onValuesChange: (Map<String, Any?>) -> Unit
)

// Composant haut niveau : tous les custom fields en lecture
@Composable
fun CustomFieldsDisplay(
    fields: List<FieldDefinition>,
    values: Map<String, Any?>
)
```

**FieldInput** : Composant de base pour un field individuel (wrapper autour de UI.FormField).

**CustomFieldsInput** : Itère sur les fields et appelle FieldInput() pour chaque, gère l'état global des valeurs.

**CustomFieldsDisplay** : Affiche label + valeur pour chaque field rempli, gère `alwaysVisible` ("Aucune valeur" si vide).

**Intégration tooltype** : Chaque tooltype décide où placer ces composants, quel style/wrapper utiliser, section collapsible ou non.

**FieldNameGenerator** :
```kotlin
object FieldNameGenerator {
    fun generateName(displayName: String, existingFields: List<FieldDefinition>): String
}
```

Génère `name` depuis `display_name` avec normalisation et gestion collisions.

### Réutilisation

- **Tracking** : Garde son système actuel (pas de refacto)
- **Journal** : Premier outil de test, utilise core/fields/, affiche les fields en lecture + édition
- **Notes** : Utilise core/fields/, affiche les fields en lecture + édition (comme Journal)
- **Données structurées** : Utilise core/fields/ (futur)
- **Futurs outils** : Chaque tooltype décide de l'intégration UI

## Génération automatique du `name`

### Normalisation depuis `display_name`

**Règles de génération** :
1. Conversion en minuscules
2. Translittération accents → ASCII (é→e, ç→c, etc.)
3. Espaces → underscores
4. Suppression caractères non-alphanumériques (sauf underscore)
5. Trim underscores début/fin
6. Caractères non translittérables → fallback "field"

**Exemples** :
- "Calories totales" → "calories_totales"
- "Temp. (°C)" → "temp_c"
- "温度" → "field"

### Gestion des collisions

Si le `name` généré existe déjà (collision avec autre field), ajout suffixe numérique automatique :
- Premier field : "calories"
- Collision : "calories_2"
- Collision : "calories_3"

### Immutabilité du `name`

Le `name` est généré **une seule fois** à la création du field et reste **immutable**. Modifier le `display_name` ultérieurement ne régénère pas le `name`.

**Rationale** : Stabilité des clés JSON, pas de migration lourde. Le `display_name` est la couche d'affichage, le `name` est la couche technique.

## Migration données lors changement config

### Opérations et impacts

| Opération | Impact données | Action service |
|-----------|----------------|----------------|
| **Ajout field** | Aucun | Aucune (nouvelles entrées peuvent l'utiliser) |
| **Suppression field** | Perte valeurs | Suppression clé dans toutes les entrées |
| **Changement `display_name`** | Aucun | Aucune (`name` reste stable) |
| **Changement `description`** | Aucun | Aucune (schéma uniquement) |
| **Changement `always_visible`** | Aucun | Aucune (UI uniquement) |
| **Changement `config`** | Invalidation | Suppression valeurs de toutes les entrées |
| **Changement `type`** | **INTERDIT** | Type immutable après création |

### UI : Confirmation pour suppressions

Quand utilisateur modifie custom fields via UI et supprime un field :
- Affichage confirmation générique : "Ce champ sera supprimé de toutes les entrées. Continuer ?"
- Pas de comptage exact (évite query coûteuse)
- Si confirmé : service supprime le champ dans toutes les données

### IA : Suppression automatique

Quand l'IA modifie la config et supprime un custom field :
- Suppression automatique acceptée (pas de blocage)
- Service supprime le champ dans toutes les données automatiquement
- Aucune traçabilité spéciale (opération normale)
- Cohérent avec la symétrie IA-humain de l'architecture

### Service de migration

**Méthode ToolDataService** :
```kotlin
suspend fun removeCustomFieldFromAllEntries(toolInstanceId: String, fieldName: String)
```

Appelée par ToolInstanceService après modification config.

## Validation et comportement des données

### Tous les custom fields sont optionnels

**Règle** : Un custom field absent des données (clé manquante dans `custom_fields`) est toujours valide.

**Rationale** : Pas de champ `required` dans FieldDefinition (problème insurmontable avec données préexistantes). Flexibilité maximale.

### Validation lors modification config pendant session IA

Si l'utilisateur modifie la config d'une instance pendant qu'une session IA est active (schéma déjà envoyé dans le prompt), la validation avec le schéma actuel peut échouer naturellement.

**Comportement** : Échec de validation → SystemMessage d'erreur → l'IA s'adapte. Pas de logique spéciale, robuste.

### Affichage des custom fields vides

Le champ `alwaysVisible` contrôle l'affichage en lecture :
- `alwaysVisible: true` → Field affiché avec "Aucune valeur" si vide
- `alwaysVisible: false` → Field masqué si vide

**IMPORTANT** : Chaque tooltype décide de l'intégration UI complète (afficher ou non, où placer, comment organiser).

## Documentation pour l'IA

### Découverte des custom fields

L'IA découvre les custom fields **via commandes explicites** :
- Demande le schéma data (commande SCHEMA) → voit les custom fields dans le schéma enrichi
- Demande la config d'une instance (commande TOOL_CONFIG) → voit la définition des custom fields

**Pas d'inclusion automatique** dans Level 1 ou Level 2 des prompts. L'IA interroge à la demande.

### Requêtes POINTER et tool_instance_id

Les enrichissements POINTER doivent passer le `tool_instance_id` dans les commandes SCHEMA pour que `getSchema()` puisse enrichir avec les custom fields.

**Implémentation** : Ajouter `tool_instance_id` aux commandes SCHEMA générées par EnrichmentProcessor pour les enrichments POINTER.

## UI gestion custom fields

### Placement dans la config d'instance

**Section dédiée visible** "Champs supplémentaires" :
- Placée après les paramètres standards de l'outil
- Toujours visible (pas de collapse par défaut)
- Pattern identique à d'autres sections de config

### CustomFieldsEditor composable

**Structure** :
- Liste des fields existants avec Cards
- Chaque card : display_name, description (tronquée), type, boutons UP/DOWN/EDIT/DELETE
- Bouton "Ajouter un champ" en bas de liste
- Ordre géré par position dans l'array (boutons up/down)

### Dialog création/édition field

**Champs du formulaire** :
- `display_name` : UI.FormField, **requis**
- `description` : UI.FormField TEXT_MEDIUM, optionnel
- `type` : UI.FormSelection, **requis**, liste déroulante avec seul item TEXT_UNLIMITED pour V1
- `always_visible` : Toggle, **requis**, défaut false
- `config` : (pas affiché pour TEXT_UNLIMITED, requis pour types futurs comme SCALE)

**Affichage `name` généré** : Optionnel - pourrait afficher le `name` en read-only pendant la saisie du `display_name` pour transparence.

**Type immutable** : En mode édition, le champ `type` est read-only (pas modifiable).

## Ordre d'implémentation

**Séquence validée** :
1. **FieldDefinition** + extensions JSON (parsing/sérialisation)
2. **FieldType enum** + strings display_name des types
3. **CustomFieldsSchemaGenerator** (critique - tester isolément)
4. **BaseSchemas.createExtendedDataSchema()** (critique - tester avec mock)
5. **FieldNameGenerator** + FieldConfigValidator (tester cas edge)
6. **Modification ToolTypeContract** signature + UN SEUL tooltype pour validation concept
7. **ToolDataService** méthode removeCustomFieldFromAllEntries() + modification create/update
8. **ToolInstanceService** logique processCustomFields() + transaction atomique
9. **Strings** pour UI composants
10. **FieldInput** + **CustomFieldsInput** + **CustomFieldsDisplay** composables
11. **UI gestion custom fields** (CustomFieldsEditor composable + Dialog dans config)
12. **Journal avec custom fields** (premier outil de test complet - lecture + édition)
13. Autres tooltypes après validation

**Approche test** : Validation incrémentale des composants critiques (2-3-4) avant intégration.

## Points d'attention implémentation

**🔴 Risque ÉLEVÉ :**
- **CustomFieldsSchemaGenerator** : Manipulation JSON Schema complexe, nombreux cas edge
- **BaseSchemas.createExtendedDataSchema()** : Point d'intégration central, impact cascade si erreur
- **Transaction atomique ToolInstanceService** : Orchestration multi-étapes, gestion rollback (mitigé par query SQL directe)
- **Modification getSchema() partout** : Impact TOUS les tooltypes, risque régression

**🟡 Risque MOYEN :**
- **ToolDataService create/update** : Merger temporaire + persist séparé, logique non triviale
- **FieldNameGenerator normalisation** : Accents, Unicode, caractères spéciaux
- **UI CustomFieldsEditor** : Pattern connu mais Compose a toujours des surprises
- **Génération name dans ToolInstanceService** : Logique spécifique système-managed à implémenter

**🟢 Risque FAIBLE :**
- Extensions JSON, FieldType enum, FieldConfigValidator basique

**Incohérences résolues** :
- ✅ Custom fields dans executions (pas de validation stricte)
- ✅ Génération name par service (logique définie)
- ✅ Notes affichage (lecture + édition comme Journal)
- ✅ Performance transaction (query SQL directe)
- ✅ Collision suffix (automatique)
- ✅ Backup types inconnus (try-catch avec message)

**Recommandation** : Tester isolément les composants critiques (schéma generator, enrichissement) avant intégration complète.

## Décisions finalisées

### 1. Structure FieldDefinition simplifiée

**Décision** : Un seul identifiant `name` (pas d'`id` séparé), pas de champ `required`.

**Rationale** :
- `id` séparé = sur-ingénierie, `name` stable suffit
- `required` = insurmontable avec données préexistantes lors toggle on

### 2. Comportement IA lors suppression custom fields

**Décision** : Suppression automatique, aucune traçabilité spéciale.

L'IA peut supprimer des custom fields. Le service supprime automatiquement le champ dans toutes les données existantes. Cohérent avec la symétrie IA-humain.

### 3. Types de champs - Architecture extensible

**Décision** : Enum avec uniquement TEXT_UNLIMITED pour V1, mais architecture extensible dès le départ.

Tous les composants (validator, generator, renderer) utilisent `when(type)` pour faciliter l'ajout de nouveaux types. Extensions futures : TEXT, TEXT_LONG, NUMERIC, SCALE, CHOICE, BOOLEAN, RANGE, DATE, TIME, FILE, IMAGE, AUDIO.

### 4. Config spécifique par type

**Décision** : Champ `config` présent dans FieldDefinition, null pour TEXT_UNLIMITED, requis si applicable.

Extensions futures :
- TEXT : `{max_length?}`
- NUMERIC : `{unit?, min?, max?, decimals?}`
- SCALE : `{min, max, min_label, max_label}` (tous requis)
- CHOICE : `{options: string[], multiple?: boolean}`

### 5. Intégration UI décidée par chaque tooltype

**Décision** : Infrastructure technique partagée (core/fields/), intégration contextuelle.

- **Journal** : Affiche custom fields en lecture + édition (composant inline)
- **Notes** : Custom fields jamais affichés (gestion IA uniquement)
- **Autres outils** : Décision au cas par cas

### 6. Pas de limite sur nombre de custom fields

**Décision** : Pas de limite artificielle.

Flexibilité maximale, évite frustration. Si cas pathologique avec 100+ fields, c'est un problème d'usage, pas de l'architecture.

### 7. Pas de cache des schémas enrichis

**Décision** : Génération à chaque validation pour V1.

Optimisation prématurée évitée. Cache centralisé dans BaseSchemas envisageable plus tard si nécessaire.

### 8. Pas de stats sur custom fields

**Décision** : Pas de gestion des custom fields dans les stats pour le moment.

Avec TEXT_UNLIMITED uniquement en V1, les stats ne sont pas pertinentes. À réévaluer lors ajout de types numériques.

### 9. Description dans schéma + config uniquement

**Décision** : Le champ `description` est visible dans le schéma JSON (pour l'IA) et lors de la configuration du field. Pas affiché lors de l'édition des données.

### 10. Schéma de base avec toolInstanceId obligatoire

**Décision** : `getSchema(schemaId, context, toolInstanceId)` avec `toolInstanceId=null` pour un schéma data **doit échouer explicitement** (`requireNotNull`).

Force l'IA à toujours passer toolInstanceId pour les schémas data enrichis. Détection immédiate des erreurs.

### 11. Passage custom_fields aux services

**Décision** : `custom_fields` passé séparément (comme `data`) dans les paramètres de service.

```kotlin
val params = mapOf(
    "toolInstanceId" to toolInstanceId,
    "tooltype" to "journal",
    "schema_id" to "journal_data",
    "data" to JSONObject(...),           // Données spécifiques tooltype
    "custom_fields" to JSONObject(...)   // Custom fields séparés
)
```

**Validation** : Le service merge temporairement pour validation via schéma enrichi, puis persiste séparément en DB.

**Rationale** : Cohérent avec le stockage DB (custom_fields est un champ séparé dans tool_data), facilite les migrations.

### 12. Accès Coordinator depuis BaseSchemas

**Décision** : Instancier Coordinator dans BaseSchemas.createExtendedDataSchema().

```kotlin
fun createExtendedDataSchema(..., context: Context): String {
    val coordinator = Coordinator.getInstance(context)
    // Charger config instance via coordinator
}
```

**Rationale** : Pattern standard, simple et cohérent avec le reste du code.

### 13. Parsing FieldDefinition ↔ JSON

**Décision** : Extension functions dans FieldDefinition.kt.

```kotlin
fun FieldDefinition.toJson(): JSONObject
fun JSONObject.toFieldDefinition(): FieldDefinition
fun JSONArray.toFieldDefinitions(): List<FieldDefinition>
fun List<FieldDefinition>.toJsonArray(): JSONArray
```

**Rationale** : Pattern Kotlin idiomatique, concis, réutilisable partout.

### 14. Ordre d'exécution lors modification config

**Décision** : Logique dans ToolInstanceService.update() avec transaction atomique.

**Séquence** :
```kotlin
database.withTransaction {
    1. Charger ancienne config
    2. Parser anciens/nouveaux custom_fields
    3. Valider nouveaux fields (FieldConfigValidator)
    4. Détecter suppressions/changements config
    5. Appeler ToolDataService.removeCustomFieldFromAllEntries()
    6. Sauvegarder nouvelle config
    // Si erreur → rollback automatique
}
```

**Rationale** : Transaction garantit cohérence config + données. Pas d'état incohérent si erreur.

### 15. Sérialisation FieldType enum

**Décision** : String uppercase avec `FieldType.valueOf()`, pas de fallback.

```json
"type": "TEXT_UNLIMITED"
```

```kotlin
// Parsing
val type = FieldType.valueOf(json.getString("type"))  // Throws si invalide
```

**Downgrade/versions futures** : Crash explicite acceptable. Import backup détecte version incompatible et bloque. Pas de fallback silencieux.

**Rationale** : Type invalide = erreur critique. Crash explicite préférable à comportement incorrect silencieux.

### 16. Custom fields dans tool_executions

**Décision** : Pas de validation stricte des snapshots dans tool_executions.

Les executions avec `supportsExecutions()` capturent des snapshots de données incluant custom_fields. Ces snapshots sont stockés tels quels **sans validation JSON Schema**.

**Rationale** : Les executions sont de l'historique. Les données étaient valides au moment de l'exécution. Un snapshot peut contenir des custom_fields qui n'existent plus dans la config actuelle. Valider strictement les snapshots avec le schéma actuel n'a pas de sens.

### 17. Génération name par service (system-managed)

**Décision** : Le champ `name` est toujours généré par le service, jamais fourni par l'appelant (IA ou UI).

**Logique dans ToolInstanceService** :
```kotlin
// ToolInstanceService.update() pour config instance
private fun processCustomFields(config: JSONObject): JSONObject {
    val customFields = config.optJSONArray("custom_fields") ?: return config
    val existingFields = mutableListOf<FieldDefinition>()
    val processedFields = JSONArray()

    for (i in 0 until customFields.length()) {
        val fieldJson = customFields.getJSONObject(i)

        // IGNORER le name fourni par l'IA/User (si présent)
        val displayName = fieldJson.getString("display_name")

        // GÉNÉRER le name automatiquement
        val generatedName = FieldNameGenerator.generateName(displayName, existingFields)

        // Reconstruire le field avec le name généré
        fieldJson.put("name", generatedName)
        processedFields.put(fieldJson)

        existingFields.add(fieldJson.toFieldDefinition())
    }

    config.put("custom_fields", processedFields)
    return config
}
```

**Rationale** : Symétrie IA-humain parfaite. Le `name` est un détail d'implémentation que ni l'utilisateur ni l'IA ne devraient gérer. Logique centralisée dans FieldNameGenerator.

**Note** : Le système `systemManaged` actuel (via schéma JSON) ne peut pas gérer les champs imbriqués dans des arrays. Logique spécifique nécessaire.

### 18. Notes affichage custom fields

**Décision** : Notes affiche les custom fields en lecture ET édition (comme Journal).

Pas de zone aveugle. L'utilisateur voit et peut éditer les custom fields dans les Notes, exactement comme dans Journal.

**Rationale** : Transparence et cohérence. Pas de raison de cacher les custom fields à l'utilisateur.

### 19. Performance transaction migration données

**Décision** : Transaction simple avec query SQL directe pour la migration.

**Implémentation ToolDataService** :
```kotlin
suspend fun removeCustomFieldFromAllEntries(toolInstanceId: String, fieldName: String) {
    // Query SQL directe, pas de chargement en mémoire
    database.execSQL(
        "UPDATE tool_data SET data = json_remove(data, ?) WHERE toolInstanceId = ?",
        arrayOf("$.custom_fields.$fieldName", toolInstanceId)
    )
}
```

**Rationale** : Query SQL directe évite chargement en mémoire et est très performante. Transaction simple suffit pour V1. Optimisation si problèmes détectés en pratique.

### 20. Collision automatic suffix

**Décision** : Suffixe numérique ajouté automatiquement par FieldNameGenerator lors collision.

Si `generateName("Calories", existingFields)` détecte que "calories" existe déjà, retourne automatiquement "calories_2".

**Rationale** : Le `name` est un détail d'implémentation. Collision gérée automatiquement sans échec. Simple et robuste, pas de round-trip perdu pour l'IA.

### 21. Backup import avec types inconnus

**Décision** : Try-catch lors parsing avec message d'erreur custom.

**Extension function** :
```kotlin
fun JSONObject.toFieldDefinition(): FieldDefinition {
    return try {
        FieldDefinition(
            name = getString("name"),
            displayName = getString("display_name"),
            description = optString("description", null),
            type = try {
                FieldType.valueOf(getString("type"))
            } catch (e: IllegalArgumentException) {
                throw ValidationException(
                    "Custom field type '${getString("type")}' not supported. Please update the app."
                )
            },
            alwaysVisible = optBoolean("always_visible", false),
            config = optJSONObject("config")?.let { /* parse */ }
        )
    } catch (e: Exception) {
        throw ValidationException("Failed to parse custom field: ${e.message}", e)
    }
}
```

**Rationale** : Message clair pour l'utilisateur, pas de pre-scan coûteux du backup. Import backup dans transaction donc rollback automatique si erreur.

### 22. Backup/restore sérialisation

**Points vérifiés** :
- **Export** : Config instances avec custom_fields exportée automatiquement ✓
- **Import** : Parsing avec try-catch pour types inconnus (décision 21) ✓
- **Custom_fields dans données** : Namespace custom_fields dans tool_data exporté/importé ✓
- **JsonTransformers** : À ajouter uniquement si structure FieldDefinition change (ajout champs non-optionnels)

**Note future** : Si FieldDefinition évolue (nouveaux champs), ajouter transformers dans JsonTransformers.kt pour migrer anciens backups.

---

**Document Version** : 3.3
**Date** : 2025-11-10
**Status** : Spécifications finales complètes, prêt pour implémentation
