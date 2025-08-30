



# Spécifications des Types de Tracking

## Architecture Générale

- **onSave** : `(String, Map<String, Any>) -> Unit`
- **value JSON** : Champ `raw` standardisé obligatoire pour affichage unifié
- **Pattern factory** : `NumericTrackingType`, `ScaleTrackingType`, etc.

---

## Interface Utilisateur - Principes Généraux

### Modes du Dialog UniversalTrackingDialog

Le dialog utilise 3 dimensions orthogonales :

```kotlin
enum class ItemType { FREE, PREDEFINED }      // Origine : libre ou item prédéfini
enum class InputType { ENTRY, CONFIG }        // Usage : saisie donnée ou config item
enum class ActionType { CREATE, UPDATE }      // Action : création ou modification
```

**Cas spécial édition historique** : `ItemType` non défini (null)

### Logique d'Affichage des Champs

**Ordre des champs :**
1. **Date et heure** (en premier si présent)
2. **Nom**
3. **Champs spécifiques au type**
4. **Case "Ajouter aux raccourcis"** (en dernier si présente)

**Conditions d'affichage :**
- **Nom éditable** : uniquement si `ItemType.FREE`
- **Date/heure présents** : uniquement si `InputType.ENTRY`
- **Case "ajouter aux raccourcis"** : uniquement si `ItemType.FREE && InputType.ENTRY`
- **Bouton validation** : texte selon `ActionType` (Créer/Modifier/Sauvegarder)

### Patterns d'Interface par Type

**NUMERIC** :
- Items prédéfinis : Bouton + qui ajoute rapidement (si qté par défaut est configurée, sinon ouvre dialogue) et bouton "crayon" ✎ → ouvre dialog

**SCALE, TEXT, CHOICE** :
- Les items prédéfinis sont des boutons cliquqbles qui ouvrent dialog.

**BOOLEAN** :
- Boutons "👍" + "👎" (sauvegarde directe) + bouton "crayon" ✎ → dialog

**COUNTER** :
- Boutons "+X(valeur incrément configurée)" + "-X" (sauvegarde directe) + bouton "crayon" ✎ → dialog

**TIMER** :
- Boutons prédéfinis (auto-switch, pas de dialog pour saisie)
- Dialog uniquement pour édition d'entrées existantes (historique)

---

## Type NUMERIC ✅ (Déjà implémenté)

**Config :**
```json
{"type": "numeric"}
```

**Items prédéfinis :**
```json
{"name": "Poids", "unit": "kg", "default_value": "70"}
```

**Value JSON :**
```json
{"quantity": 75, "unit": "g", "type": "numeric", "raw": "75g"}
```

**Interface :** Existante (boutons prédéfinis + "Autre")

---

## Type SCALE

**Config :**
```json
{
  "type": "scale", 
  "min": 1, 
  "max": 10, 
  "min_label": "Très mal", 
  "max_label": "Parfait"
}
```

**Items prédéfinis :**
```json
{"name": "Humeur"}
```

**Value JSON :**
```json
{"value": 7, "min": 1, "max": 10, "type": "scale", "raw": "7 (1 à 10)"}
```

**Interface :** 
- Slider horizontal avec valeur affichée
- Pas de valeur par défaut (utilisateur doit choisir)
- Labels min/max configurables au niveau instance

---

## Type TEXT

**Config :**
```json
{"type": "text"}
```

**Items prédéfinis :**
```json
{"name": "Réflexion du jour"}
```

**Value JSON :**
```json
{"text": "Mon texte saisi", "type": "text", "raw": "Mon texte saisi"}
```

**Interface :** 
- TextField simple (1 ligne extensible)
- Pas de prompts/questions dans les items

---

## Type CHOICE

**Config :**
```json
{
  "type": "choice", 
  "options": ["Marche", "Course", "Vélo", "Natation"]
}
```

**Items prédéfinis :**
```json
{"name": "Sport du jour"}
```

**Value JSON :**
```json
{"selected_option": "Vélo", "type": "choice", "raw": "Vélo"}
```

**Interface :** 
- UI.FormSelection (dropdown/liste)
- Options globales dans config, items simples

---

## Type BOOLEAN

**Config :**
```json
{
  "type": "boolean", 
  "true_label": "Pris", 
  "false_label": "Pas pris"
}
```

**Items prédéfinis :**
```json
{"name": "Médicament du matin"}
```

**Value JSON :**
```json
{"state": true, "type": "boolean", "raw": "Pris"}
```

**Interface :** 
- Switch/Toggle (composant à créer utilisable à d'autres endroits de l'app)
- Labels configurables au niveau instance
- `raw` utilise le label approprié selon `state`

---

## Type COUNTER

**Config :**
```json
{
  "type": "counter", 
  "allow_decrement": true
}
```

**Items prédéfinis :**
```json
{"name": "Followers", "increment": 100}
```

**Value JSON :**
```json
{"increment": -100, "type": "counter", "raw": "-100"}
```
```json
{"increment": 100, "type": "counter", "raw": "+100"}
```

**Interface :** 
- Boutons affichant l'incrément : "+5", "-3", etc.
- Décrément optionnel au niveau instance
- Items définissent leur incrément

---

## Type TIMER

**Config :**
```json
{"type": "timer"}
```

**Items prédéfinis :**
```json
{"name": "Travail"}
{"name": "Pause"}
{"name": "Sport"}
```

**Value JSON :**
```json
{"duration_minutes": 45, "type": "timer", "raw": "45 min"}
```

**Interface :** 
- Ligne d'état : "En cours : -" ou "En cours : Sport depuis 34 min"
- Boutons par activité avec mise en valeur visuelle (PRIMARY pour actif)
- Auto-switch : cliquer une activité arrête l'actuelle et démarre la nouvelle
- Possibilité d'arrêter sans démarrer autre chose

**Comportement :**
- Chronométrage en temps réel
- Une seule activité active à la fois

---

## À Nettoyer dans le Code Existant

- Ancien nom : duration ; nouveau nom : timer
- Supprimer les références à l'option switch auto (maintenant toujours actif) 

---

## Pattern d'Implémentation

Chaque type aura sa classe factory :
- `NumericTrackingType` ✅ (déjà fait via TrackingUtils)
- `ScaleTrackingType`
- `TextTrackingType`
- `ChoiceTrackingType` 
- `BooleanTrackingType`
- `CounterTrackingType`
- `TimerTrackingType`

Méthodes communes :
- `createValueJson(properties: Map<String, Any>): String?`
- `validateInput(properties: Map<String, Any>): Boolean`
- `getDefaultConfig(): JSONObject`
