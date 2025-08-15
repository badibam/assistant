# Reactive Data Patterns

## Pattern utilisé : Flow direct depuis DAO

```kotlin
// UI observe directement le DAO
val database = remember { AppDatabase.getDatabase(context) }
val zones by database.zoneDao().getAllZones().collectAsState(initial = emptyList())

// Affichage conditionnel
if (zones.isEmpty()) {
    // État vide
} else {
    // Liste des zones
    zones.forEach { zone -> /* afficher */ }
}
```

## Flow utilisé
- `DAO.getAllZones()` → `Flow<List<Zone>>`
- `collectAsState()` → conversion Flow → State Compose
- Mise à jour automatique après création/modification

**Avantage** : Réactivité immédiate, pas de refresh manuel nécessaire.