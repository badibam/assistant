# UI Guidelines

## Règle unique : TOUS les composants UI passent par `UI.*`

## ✅ AUTORISÉ
```kotlin
UI.Column { }
UI.Row { }  
etc.
```

## ❌ INTERDIT
```kotlin
Column { }          // → UI.Column
Row { }             // → UI.Row  
etc.
```
