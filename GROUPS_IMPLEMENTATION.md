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

### ✅ COMPLÉTÉ - ZoneScreen Affichage Groupé (commit actuel)
- **ButtonType** : Ajout SECONDARY et TERTIARY avec couleurs thème
- **ZoneScreen** : Sections par groupe + section "Hors groupe" toujours visible
- **Bouton "+" par section** : Liste avec Automatisation (SECONDARY) + outils (PRIMARY)
- **Affichage vide** : Messages "Aucun élément dans ce groupe"
- **MainScreen** : Chargement tool_groups dans zones list
- **AutomationService** : Gestion field group (create/update)
- **CreateAutomationDialog** : GroupSelector avec preSelectedGroup
- **ToolTypeContract** : Paramètre initialGroup dans getConfigScreen()
- **Tool config screens** : State group + derivedState config + chargement/sauvegarde
- **ToolGeneralConfigSection** : LaunchedEffect pour initialisation initialGroup
- **CreateZoneScreen** : Conversion JSONArray.toString() avant envoi

### ⏳ TODO - Reste à implémenter
- MainScreen : Config button + zones groupées
- MainScreenConfigScreen : Gestion zone_groups
- Automation screens : Group selection integration

---

## Décisions d'Implémentation (vs spec initiale)

### Backend
1. **Validation via schémas uniquement** : Pas de validation manuelle dans ZoneService, tout via SchemaValidator
2. **Catégorie MAIN_SCREEN dédiée** : zone_groups dans AppConfigService.MAIN_SCREEN (pas FORMAT)
3. **CreateZoneScreen gère création** : Callback onCreate simplifié en `() -> Unit`, CreateZoneScreen appelle coordinator directement

### UI
4. **Section unique "Hors groupe"** : Une section pour outils ET automations hors groupe (pas deux séparées)
5. **Bouton + unifié dans liste** : Chaque section a un "+" qui ouvre liste avec Automatisation (SECONDARY) + tous les outils (PRIMARY)
6. **initialGroup via contrat** : Ajout paramètre initialGroup à ToolTypeContract.getConfigScreen() (pas de valeur par défaut car @Composable)
7. **Group state dans config screens** : Chaque config screen gère state group + l'ajoute au derivedStateOf config JSONObject

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

### ZoneScreen (✅ COMPLÉTÉ)
**Structure:**
1. Pour chaque groupe (ordre tool_groups) :
   - Header groupe + bouton "+"
   - Liste outils du groupe + automations
   - Message vide si rien dans le groupe
2. Section "Hors groupe" (toujours visible) :
   - Tools + automations sans groupe + bouton "+"
   - Message vide si aucun élément

**Bouton "+" :**
- Toggle liste avec Automatisation (SECONDARY, premier) + tous les outils (PRIMARY)
- Pré-sélection groupe via initialGroup/preSelectedGroup
- Fermeture liste après sélection

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
