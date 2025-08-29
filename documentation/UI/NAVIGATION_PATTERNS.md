# Navigation Patterns

## Pattern utilisé : Navigation conditionnelle

```kotlin
var showCreateZone by remember { mutableStateOf(false) }

if (showCreateZone) {
    CreateZoneScreen(onCancel = { showCreateZone = false })
} else {
    // Écran principal
}
```

**Avantage** : Simple, pas de NavController nécessaire pour ce cas.