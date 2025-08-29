# Grid Layout System - Système de grille pour les Tool Instances

## Vue d'ensemble

Système de placement et réorganisation des tool instances sur une grille 4×4 dans les zones, avec mode édition pour repositionnement interactif.

## Architecture

### 1. Structure de données

#### Table `tool_grid_positions`
```sql
CREATE TABLE tool_grid_positions (
    zone_id INTEGER NOT NULL,
    tool_instance_id INTEGER NOT NULL,
    grid_x INTEGER NOT NULL,
    grid_y INTEGER NOT NULL,
    PRIMARY KEY (zone_id, tool_instance_id),
    FOREIGN KEY (zone_id) REFERENCES zones(id),
    FOREIGN KEY (tool_instance_id) REFERENCES tool_instances(id)
);
```

#### Grille logique
- **Largeur** : 4 colonnes (chaque colonne = 1/4 de largeur écran)
- **Hauteur** : extensible par scroll vertical
- **Coordonnées** : (0,0) en haut à gauche
- **Occupation** : selon DisplayMode de chaque tool instance

### 2. DisplayModes et occupation grille

```kotlin
enum class DisplayMode(val width: Int, val height: Int) {
    ICON(1, 1),         // 1×1 case
    MINIMAL(2, 1),      // 2×1 cases
    LINE(4, 1),         // 4×1 cases
    CONDENSED(2, 2),    // 2×2 cases  
    EXTENDED(4, 2),     // 4×2 cases
    SQUARE(4, 4),       // 4×4 cases
    FULL(4, -1)         // 4×∞ cases (hauteur variable)
}
```

## Comportements

### 1. Mode Normal

#### Affichage
- Tool instances positionnées selon leurs coordonnées stockées
- Scroll vertical si contenu dépasse l'écran
- Affichage complet du contenu des tool instances

#### Changement de DisplayMode
**Logique de repositionnement automatique** :

1. **Vérifier espace disponible** à la position actuelle
2. **Si espace suffisant** : agrandir sur place
3. **Si espace insuffisant** :
   - Ajouter des lignes vides en dessous si nécessaire
   - Repositionner l'outil à la nouvelle taille
4. **Sauvegarder** les nouvelles coordonnées

```kotlin
fun handleDisplayModeChange(tool: ToolInstance, newMode: DisplayMode) {
    val currentPos = getGridPosition(tool.id)
    val newSize = newMode.getDimensions()
    
    if (isSpaceAvailable(currentPos, newSize)) {
        // Agrandir sur place
        updateDisplayMode(tool.id, newMode)
    } else {
        // Chercher nouvelle position + ajouter lignes si besoin
        val newPos = findAvailablePosition(newSize) ?: addRowsAndPlace(newSize)
        updateGridPosition(tool.id, newPos)
        updateDisplayMode(tool.id, newMode)
    }
}
```

### 2. Mode Édition

#### Activation
- **Bouton "Réorganiser"** dans ZoneScreen
- Passage en mode édition : `editMode = true`

#### Interface
- **Grille infinie** : scroll vertical sans limite
- **Tool instances en silhouette** : titre + contour selon DisplayMode
- **Grille visible** : lignes de division 4×4 affichées

#### Interaction : Tap to Select + Tap to Place

**Phase 1 - Sélection** :
```kotlin
fun onToolTap(tool: ToolInstance) {
    selectedTool = tool
    showValidPositions = true
    showCancelButton = true
}
```

**Phase 2 - Placement** :
```kotlin  
fun onGridTap(x: Int, y: Int) {
    if (selectedTool != null && isValidPosition(x, y, selectedTool.displayMode)) {
        moveToolToPosition(selectedTool.id, x, y)
        clearSelection()
    }
}
```

**Annulation** :
```kotlin
fun onCancelSelection() {
    selectedTool = null
    showValidPositions = false  
    showCancelButton = false
}
```

#### Feedback visuel

**Tool sélectionné** :
- Surbrillance de l'outil
- Point d'ancrage haut-gauche visible
- Bouton "Annuler" qui apparaît

**Zones de placement** :
- **Zones vertes continues** : toutes les positions où l'outil peut être placé
- **Points d'ancrage** : positions top-left possibles visibles dans les zones vertes

```kotlin
fun calculateValidZones(toolSize: DisplayMode): List<GridZone> {
    val zones = mutableListOf<GridZone>()
    
    for (startY in 0..maxY) {
        for (startX in 0..maxX) {
            if (canPlaceAt(startX, startY, toolSize)) {
                // Calculer la zone continue à partir de cette position
                val zone = expandZone(startX, startY, toolSize)
                if (!zones.any { it.overlaps(zone) }) {
                    zones.add(zone)
                }
            }
        }
    }
    
    return zones
}
```

## Implémentation

### 1. Modifications Base de Données

#### Nouvelle table
```kotlin
@Entity(
    tableName = "tool_grid_positions",
    primaryKeys = ["zone_id", "tool_instance_id"],
    indices = [
        Index(value = ["zone_id"]),
        Index(value = ["tool_instance_id"])
    ]
)
data class ToolGridPosition(
    val zone_id: Long,
    val tool_instance_id: Long,
    val grid_x: Int,
    val grid_y: Int
)
```

#### DAO operations
```kotlin
@Dao
interface ToolGridPositionDao {
    @Query("SELECT * FROM tool_grid_positions WHERE zone_id = :zoneId")
    suspend fun getPositionsForZone(zoneId: Long): List<ToolGridPosition>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(position: ToolGridPosition)
    
    @Delete
    suspend fun deletePosition(position: ToolGridPosition)
    
    @Query("DELETE FROM tool_grid_positions WHERE tool_instance_id = :toolId")
    suspend fun deletePositionsForTool(toolId: Long)
}
```

### 2. Composants UI

#### ZoneScreen étendu
```kotlin
@Composable
fun ZoneScreen(
    zone: Zone,
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit = {},
    viewModel: ZoneViewModel = hiltViewModel()
) {
    // État du mode édition
    val selectedTool by viewModel.selectedTool.collectAsState()
    val validZones by viewModel.validZones.collectAsState()
    
    Column {
        // Header avec bouton Réorganiser
        ZoneHeader(
            zone = zone,
            editMode = editMode,
            onEditModeToggle = onEditModeChange
        )
        
        // Grille des tool instances
        GridLayout(
            tools = viewModel.toolsWithPositions,
            editMode = editMode,
            selectedTool = selectedTool,
            validZones = validZones,
            onToolClick = if (editMode) viewModel::selectTool else viewModel::openTool,
            onGridClick = if (editMode) viewModel::placeTool else { _, _ -> }
        )
        
        // Bouton annuler (si outil sélectionné)
        if (editMode && selectedTool != null) {
            CancelButton(onClick = viewModel::cancelSelection)
        }
    }
}
```

#### UI.ToolCard modifié
```kotlin
@Composable
fun UI.ToolCard(
    tool: ToolInstance,
    displayMode: DisplayMode,
    editMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = { },
    contentDescription: String? = null
) {
    Card(
        modifier = Modifier
            .size(
                width = (displayMode.width * 0.25f).dp,
                height = if (displayMode.height > 0) (displayMode.height * 0.25f).dp else Dp.Unspecified
            )
            .conditional(selected) { 
                border(2.dp, Color.Blue) 
            }
            .clickable { onClick() },
        content = {
            if (editMode) {
                // Mode silhouette : titre + contour
                ToolSilhouette(tool, displayMode)
            } else {
                // Mode normal : contenu complet
                ToolContent(tool, displayMode)
            }
        }
    )
}
```

### 3. ViewModel Extensions

```kotlin
class ZoneViewModel : ViewModel() {
    // États existants...
    
    // États mode édition
    private val _editMode = MutableStateFlow(false)
    val editMode = _editMode.asStateFlow()
    
    private val _selectedTool = MutableStateFlow<ToolInstance?>(null)
    val selectedTool = _selectedTool.asStateFlow()
    
    private val _validZones = MutableStateFlow<List<GridZone>>(emptyList())
    val validZones = _validZones.asStateFlow()
    
    // Actions mode édition
    fun toggleEditMode() {
        _editMode.value = !_editMode.value
        if (!_editMode.value) {
            cancelSelection()
        }
    }
    
    fun selectTool(tool: ToolInstance) {
        _selectedTool.value = tool
        _validZones.value = calculateValidZones(tool.displayMode)
    }
    
    fun placeTool(x: Int, y: Int) {
        val tool = _selectedTool.value ?: return
        
        viewModelScope.launch {
            coordinator.processUserAction(
                "move_tool_to_position",
                mapOf(
                    "tool_id" to tool.id,
                    "x" to x,
                    "y" to y
                )
            )
            cancelSelection()
        }
    }
    
    fun cancelSelection() {
        _selectedTool.value = null
        _validZones.value = emptyList()
    }
}
```

### 4. Service Layer

#### GridLayoutService
```kotlin
class GridLayoutService(private val context: Context) : ExecutableService {
    
    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        return when (operation) {
            "move_tool_to_position" -> moveToolToPosition(params)
            "auto_reposition_on_resize" -> autoRepositionOnResize(params)
            "get_valid_positions" -> getValidPositions(params)
            else -> OperationResult.error("Unknown operation: $operation")
        }
    }
    
    private suspend fun moveToolToPosition(params: JSONObject): OperationResult {
        val toolId = params.getLong("tool_id")
        val x = params.getInt("x")
        val y = params.getInt("y")
        
        // Vérifier validité de la position
        if (!isValidPosition(toolId, x, y)) {
            return OperationResult.error("Position invalide")
        }
        
        // Sauvegarder nouvelle position
        val position = ToolGridPosition(
            zone_id = getZoneIdForTool(toolId),
            tool_instance_id = toolId,
            grid_x = x,
            grid_y = y
        )
        
        gridPositionDao.insertPosition(position)
        
        return OperationResult.success()
    }
}
```

## Cas d'usage

### Scénario 1 : Changement de DisplayMode automatique
1. Utilisateur change MINIMAL → EXTENDED sur un outil
2. Système vérifie si espace disponible à position actuelle
3. Si oui : agrandissement sur place
4. Si non : ajout de lignes et repositionnement automatique

### Scénario 2 : Réorganisation manuelle
1. Utilisateur appuie sur "Réorganiser"
2. Interface passe en mode édition (silhouettes + grille)
3. Utilisateur sélectionne un outil (surbrillance + zones vertes)
4. Utilisateur tape dans une zone verte
5. Outil se repositionne, sélection se désactive

### Scénario 3 : Annulation de sélection
1. En mode édition, outil sélectionné
2. Utilisateur appuie sur "Annuler"
3. Sélection se désactive, zones vertes disparaissent

## Contraintes et règles

### Contraintes techniques
- **Largeur max** : 4 colonnes (limitation écran)
- **Pas de chevauchement** : deux outils ne peuvent occuper les mêmes cases
- **Intégrité données** : suppression outil → suppression position
- **Performance** : calcul zones valides optimisé

### Règles métier
- **Mode FULL** : toujours placé en début de ligne (x=0)
- **Positions par défaut** : nouveaux outils placés automatiquement
- **Sauvegarde automatique** : toute modification de position sauvegardée immédiatement

## Migration et déploiement

### Phase 1 : Infrastructure
- Création table `tool_grid_positions`
- Ajout DAO et service
- Positions par défaut pour outils existants

### Phase 2 : Interface
- Extension ZoneScreen avec mode édition
- Modification UI.ToolCard pour mode silhouette
- Implémentation interactions tap-to-place

### Phase 3 : Optimisations
- Performances calcul zones valides
- Animations de transition
- Gestion des cas edge

## Tests

### Tests unitaires
- Calcul zones valides selon DisplayMode
- Détection de chevauchement
- Logique de repositionnement automatique

### Tests d'intégration  
- Sauvegarde/chargement positions
- Synchronisation avec changements DisplayMode
- Cohérence après suppression d'outils

### Tests UI
- Interactions mode édition
- Feedback visuel (sélection, zones vertes)
- Transitions entre modes normal/édition