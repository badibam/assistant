# Système de Groupes - Documentation d'Implémentation

## Vue d'ensemble

Deux systèmes analogues :
- **Groupes d'outils** : définis au niveau Zone, assignables aux tool instances et automations
- **Groupes de zones** : définis au niveau App, assignables aux zones

## 1. Modifications DB

### Migration 17 → 18

```sql
-- Zones: add tool_groups field (JSON array of group names)
ALTER TABLE zones ADD COLUMN tool_groups TEXT;

-- Tool instances: add group field (nullable string)
ALTER TABLE tool_instances ADD COLUMN `group` TEXT;

-- AI sessions (automations): add group field (nullable string)
ALTER TABLE ai_sessions ADD COLUMN `group` TEXT;

-- App config: add zone_groups in existing app_config JSON
-- No ALTER needed, just JSON update in AppConfigService
```

### Entités modifiées

**Zone.kt**
```kotlin
data class Zone(
    // ... existing fields
    val tool_groups: String? = null  // JSON array: ["Group1", "Group2"]
)
```

**ToolInstance.kt** (via config JSON)
```kotlin
// In config JSON only - no entity change needed
// Handled by BaseSchemas
```

**AISessionEntity.kt**
```kotlin
data class AISessionEntity(
    // ... existing fields
    val group: String? = null
)
```

## 2. JSON Schemas

### Zone tool_groups

Stocké dans `Zone.tool_groups` (nouvelle colonne DB):
```json
["Santé", "Productivité", "Loisirs"]
```

### Tool Instance group

Dans config JSON (BaseSchemas), nouveau champ commun:
```kotlin
// BaseSchemas.createBaseConfigProperties()
"group": {
    "type": "string",
    "description": "Tool group for organization"
}
```

### App config zone_groups

Dans existing AppConfig JSON:
```json
{
    "zone_groups": ["Vie perso", "Travail"],
    // ... existing fields
}
```

## 3. Composants UI

### GroupListEditor (réutilisable)

Pattern identique à tracking items predefined.

```kotlin
@Composable
fun GroupListEditor(
    groups: List<String>,
    onUpdate: (List<String>) -> Unit,
    label: String,
    emptyMessage: String
)
```

**Features:**
- Add button (+ icon)
- List with UP/DOWN/EDIT/DELETE buttons
- Edit dialog for group name
- Ordre preserved (array index = display order)

### Group Selection Dropdown

```kotlin
@Composable
fun GroupSelector(
    availableGroups: List<String>,
    selectedGroup: String?,
    onSelect: (String?) -> Unit,
    label: String
)
```

Options: ["Hors groupe"] + availableGroups

## 4. Services Updates

### ZoneService

```kotlin
// Operation: zones.get
// Returns zone with tool_groups field

// Operation: zones.create / zones.update
// Accepts tool_groups in params
// Validates: JSON array of strings
```

### AppConfigService

```kotlin
// Get zone_groups
operation = "get_zone_groups"
returns: List<String>

// Set zone_groups
operation = "set_zone_groups"
params: { "zone_groups": ["Group1", "Group2"] }
```

### AISessionService (automations)

```kotlin
// Add 'group' to create/update operations
// Filter by group for display
```

## 5. Screens Updates

### CreateZoneScreen / EditZoneScreen

Nouvelle section après description/icon:

```kotlin
UI.Card {
    UI.Text("Groupes d'outils", TextType.SUBTITLE)
    GroupListEditor(
        groups = toolGroups,
        onUpdate = { toolGroups = it },
        label = s.shared("label_tool_groups"),
        emptyMessage = s.shared("message_no_tool_groups")
    )
}
```

### ToolGeneralConfigSection

Ajout après `always_send`:

```kotlin
GroupSelector(
    availableGroups = loadToolGroups(zoneId),
    selectedGroup = config.optString("group", null),
    onSelect = { updateConfig("group", it) },
    label = s.shared("label_group")
)
```

### ZoneScreen

**Logic de groupement:**

```kotlin
// Load zone's tool_groups
val toolGroups = zone.tool_groups?.parseJSON() ?: emptyList()

// Group tools and automations
val groupedTools = tools.groupBy { it.config.group }
val groupedAutomations = automations.groupBy { it.group }

// Display sections
if (toolGroups.isEmpty()) {
    // Section "Outils" (comportement actuel)
    // Section "Automatisations" (comportement actuel)
} else {
    // Pour chaque groupe
    toolGroups.forEach { groupName ->
        ToolGroupSection(
            groupName = groupName,
            tools = groupedTools[groupName] ?: emptyList(),
            automations = groupedAutomations[groupName] ?: emptyList(),
            onAddTool = { /* with group preset */ }
        )
    }

    // Section "Outils hors groupe"
    ToolGroupSection(
        groupName = s.shared("label_ungrouped"),
        tools = groupedTools[null] ?: emptyList(),
        automations = groupedAutomations[null] ?: emptyList(),
        onAddTool = { /* with group=null */ }
    )

    // Section "Automatisations hors groupe"
    AutomationUngroupedSection(
        automations = groupedAutomations[null] ?: emptyList()
    )
}
```

**Section component:**

```kotlin
@Composable
fun ToolGroupSection(
    groupName: String,
    tools: List<ToolInstance>,
    automations: List<Automation>,
    onAddTool: (groupId: String?) -> Unit
) {
    UI.Card {
        Row {
            UI.Text(groupName, TextType.SUBTITLE)
            UI.ActionButton(ADD, onClick = { onAddTool(groupName) })
        }

        // Tools
        tools.forEach { /* Display tool card */ }

        // Automations (at the end, no title)
        automations.forEach { /* Display automation card */ }
    }
}
```

### MainScreen

**Changes:**

```kotlin
// Remove top-right + button
// Add config button top-right instead

UI.PageHeader(
    rightButton = ButtonAction.CONFIGURE,
    onRightClick = { navigateToMainConfig = true }
)

// Zones section with title
UI.Card {
    Row {
        UI.Text(s.shared("label_zones"), TextType.SUBTITLE)
        UI.ActionButton(ADD, onClick = { /* create zone */ })
    }

    // Grouped zones display
    DisplayGroupedZones(zones, zoneGroups)
}
```

### MainScreenConfigScreen (nouveau)

```kotlin
@Composable
fun MainScreenConfigScreen(
    onNavigateBack: () -> Unit
) {
    // Load zone_groups from AppConfig
    var zoneGroups by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val result = coordinator.processUserAction("app_config.get_zone_groups", emptyMap())
        zoneGroups = result.data?.get("zone_groups") as? List<String> ?: emptyList()
    }

    Column {
        UI.PageHeader("Configuration", leftButton = BACK, onLeftClick = onNavigateBack)

        UI.Card {
            GroupListEditor(
                groups = zoneGroups,
                onUpdate = { newGroups ->
                    coordinator.processUserAction(
                        "app_config.set_zone_groups",
                        mapOf("zone_groups" to newGroups)
                    )
                    zoneGroups = newGroups
                },
                label = s.shared("label_zone_groups"),
                emptyMessage = s.shared("message_no_zone_groups")
            )
        }
    }
}
```

### AutomationScreen (création/édition)

Ajout du GroupSelector similaire aux tool instances:

```kotlin
GroupSelector(
    availableGroups = loadToolGroups(zoneId),
    selectedGroup = automation.group,
    onSelect = { selectedGroup = it },
    label = s.shared("label_group")
)
```

## 6. Strings à ajouter

**Shared strings (core/strings/sources/shared.xml):**

```xml
<!-- Groups -->
<string name="label_group">Groupe</string>
<string name="label_tool_groups">Groupes d'outils</string>
<string name="label_zone_groups">Groupes de zones</string>
<string name="label_ungrouped">Hors groupe</string>
<string name="label_ungrouped_automations">Automatisations hors groupe</string>
<string name="message_no_tool_groups">Aucun groupe d'outils défini</string>
<string name="message_no_zone_groups">Aucun groupe de zones défini</string>
<string name="label_zones">Zones</string>
<string name="action_configure">Configurer</string>
<string name="dialog_edit_group">Modifier le groupe</string>
<string name="dialog_create_group">Créer un groupe</string>
<string name="label_group_name">Nom du groupe</string>
```

## 7. Ordre d'implémentation

1. **DB Migration 17→18** (AppDatabase.kt)
2. **Update entities** (Zone.kt, AISessionEntity.kt)
3. **Update BaseSchemas** (add 'group' field)
4. **Update ZoneService** (handle tool_groups)
5. **Update AppConfigService** (zone_groups operations)
6. **Create GroupListEditor** (reusable component)
7. **Create GroupSelector** (reusable component)
8. **Update CreateZoneScreen** (tool groups management)
9. **Update ToolGeneralConfigSection** (group selection)
10. **Update ZoneScreen** (grouped display logic)
11. **Update MainScreen** (config button + zones section)
12. **Create MainScreenConfigScreen**
13. **Update automation screens** (group selection)
14. **Add strings**
15. **Test**

## 8. Règles de validation

### Group invalide

Si `tool.config.group` ne fait pas partie de `zone.tool_groups`:
- Apparaît dans section "Hors groupe"
- Lors de l'édition, le dropdown montre "Hors groupe" sélectionné
- Pas d'erreur, comportement silencieux

### Group null vs empty string

- `null` = "Hors groupe"
- Empty string = invalide, traité comme null
- Validation: group doit être null OU dans la liste des groupes disponibles

### Suppression de groupe

Si un groupe est supprimé de `zone.tool_groups`:
- Les outils avec ce groupe passent automatiquement en "Hors groupe"
- Pas de migration de données nécessaire (logic applicative)

---

**Pattern clé**: Copier-coller le système de tracking items predefined pour GroupListEditor, adapter pour simple string list au lieu d'objets complexes.
