# UI Guidelines

## Règle unique : TOUS les composants UI passent par `UI.*`

## ✅ AUTORISÉ
```kotlin
UI.Column { }
UI.Row { }  
UI.Box { }
UI.Spacer(modifier)
UI.Button(type = ButtonType.PRIMARY, onClick = { }) { }
UI.Text(text = "Hello", type = TextType.BODY)
UI.Card(type = CardType.SYSTEM) { }
```

## ❌ INTERDIT
```kotlin
Column { }          // → UI.Column
Row { }             // → UI.Row  
Button(onClick = {}) // → UI.Button
Text("Hello")       // → UI.Text
Card { }            // → UI.Card
LazyColumn { }      // → créer UI.LazyColumn si besoin
```

## Code Review Checklist
- [ ] Aucun import `androidx.compose.foundation.layout.Column/Row`
- [ ] Aucun import `androidx.compose.material3.*` (sauf dans themes/)
- [ ] Tous les composants utilisent `UI.*`

## Vérification rapide
```bash
grep -r "Column\s*{" app/src/main/java/ --exclude-dir=themes
grep -r "Button\s*(" app/src/main/java/ --exclude-dir=themes
```

**Pourquoi ?** Permet des thèmes extrêmes (animations, glassmorphism, etc.) en gardant le même code métier.