# Système de Groupes - État d'Implémentation

## État Actuel

### ✅ COMPLÉTÉ - Backend (commit: 5cd068f)
- **Migration DB 17→18** : zones.tool_groups, automations.group
- **Schémas** : ZoneSchemaProvider (tool_groups array), BaseSchemas (group string)
- **Services** : ZoneService (tool_groups CRUD), AppConfigService (zone_groups CRUD, catégorie MAIN_SCREEN)
- **Backup/Export** : zones.tool_groups et automations.group inclus
- **Strings** : Tous ajoutés et générés

### ✅ COMPLÉTÉ - UI Composants & Config (commit: 965ee5f)
- **GroupListEditor** : Add/delete/reorder groups avec validation
- **GroupSelector** : Dropdown avec option "Hors groupe"
- **CreateZoneScreen** : Intégration GroupListEditor pour tool_groups
- **ToolGeneralConfigSection** : 9ème champ "group" avec GroupSelector
- **Config screens** : Tous mis à jour (tracking, journal, notes, messages) avec paramètre zoneId

### ⏳ EN COURS - UI Affichage
- ZoneScreen : Affichage groupé + boutons "+"
- MainScreen : Config button + zones groupées
- MainScreenConfigScreen : Gestion zone_groups
- Automation screens : Group selection

---

## Décisions d'Implémentation (vs spec initiale)

### Backend
1. **Validation via schémas uniquement** : Pas de validation manuelle dans ZoneService, tout via SchemaValidator
2. **Catégorie MAIN_SCREEN dédiée** : zone_groups dans AppConfigService.MAIN_SCREEN (pas FORMAT)
3. **CreateZoneScreen gère création** : Callback onCreate simplifié en `() -> Unit`, CreateZoneScreen appelle coordinator directement

### UI
4. **Section unique "Hors groupe"** : Une section pour outils ET automations hors groupe (pas deux séparées)
5. **Bouton + unifié** : Chaque section a un "+" qui ouvre dialog "Outil ou Automation?" puis navigation avec groupe pré-rempli

---

## Architecture Détaillée

### DB Schema (Migration 17→18)
```sql
ALTER TABLE zones ADD COLUMN tool_groups TEXT DEFAULT NULL;
ALTER TABLE automations ADD COLUMN `group` TEXT DEFAULT NULL;
```

### Entités
- **Zone** : `tool_groups: String?` (JSON array)
- **AutomationEntity** : `group: String?`
- **ToolInstance** : `group` dans config JSON (via BaseSchemas)

### Services
**ZoneService**
- `zones.create/update` : Accepte tool_groups (JSONArray)
- Validation via ZoneSchemaProvider

**AppConfigService**
- `app_config.get_zone_groups` : Retourne List<String>
- `app_config.set_zone_groups` : Accepte zone_groups (List)
- Stockage : category MAIN_SCREEN, défaut []

### Schémas JSON
**zone_config** (ZoneSchemaProvider)
```json
{
  "tool_groups": {
    "type": "array",
    "items": {"type": "string", "minLength": 1, "maxLength": 60},
    "uniqueItems": true
  }
}
```

**config base** (BaseSchemas)
```json
{
  "group": {
    "type": "string",
    "maxLength": 60
  }
}
```

---

## UI TODO Détaillé

### ZoneScreen (⏳)
**Structure:**
1. Pour chaque groupe (ordre tool_groups) :
   - Header groupe + bouton "+"
   - Grid tools du groupe + automations inline
2. Section "Hors groupe" :
   - Tools + automations sans groupe + bouton "+"

**Bouton "+" :**
- Dialog avec 2 options : "Créer un outil" / "Créer une automation"
- Navigation avec groupe pré-rempli dans les params

### MainScreen (⏳)
- Ajouter bouton config (engrenage) dans header → MainScreenConfigScreen
- Afficher zones par groupe (via zone_groups)
- Section "Hors groupe" pour zones sans groupe

### MainScreenConfigScreen (⏳ - nouveau fichier)
Simple écran avec :
- GroupListEditor pour zone_groups
- Load : `coordinator.processUserAction("app_config.get_zone_groups")`
- Save : `coordinator.processUserAction("app_config.set_zone_groups", mapOf("zone_groups" to list))`

### Automation screens (⏳)
- Ajouter GroupSelector aux formulaires
- Charger tool_groups depuis zone (comme ToolGeneralConfigSection)
- Sauvegarder `group` dans AutomationEntity

---

## Composants Réutilisables

### GroupListEditor
```kotlin
GroupListEditor(
    groups: List<String>,
    onGroupsChange: (List<String>) -> Unit,
    label: String
)
```
Features : Add, Delete, Move Up/Down avec validation inline

### GroupSelector
```kotlin
GroupSelector(
    availableGroups: List<String>,
    selectedGroup: String?,
    onGroupSelected: (String?) -> Unit,
    label: String
)
```
Dropdown avec option "Hors groupe" (null)
