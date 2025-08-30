# 📋 Plan d'Implémentation Progressive

  Phase 1⃣ : Architecture Core (Base Sans Casser)

  Objectif : Créer la base sans toucher au code existant

  1. Créer l'interface TrackingTypeHandler
  2. Migrer TrackingUtils → NumericTrackingType (garder TrackingUtils en wrapper pour compatibilité)
  3. Créer TrackingTypeFactory
  4. Tester que NUMERIC fonctionne avec la nouvelle architecture

  Phase 2⃣ : Refactoring TrackingService

  Objectif : Généraliser la logique de sauvegarde

  1. Modifier TrackingService.handleCreate() :
  val handler = TrackingTypeFactory.getHandler(type)
  val valueJson = handler?.createValueJson(extractProperties(params))
  2. Généraliser l'extraction des propriétés :
  fun extractPropertiesFromParams(params: JSONObject, type: String): Map<String, Any>
  3. Tester NUMERIC avec nouveau service

  Phase 3⃣ : Refactoring TrackingInputManager

  Objectif : Signature unifiée onSave: (String, Map<String, Any>) -> Unit

  1. Modifier la signature saveEntry
  2. Adapter NumericTrackingInput pour utiliser Map au lieu de quantity/unit
  3. Généraliser la logique de sauvegarde dans TrackingInputManager
  4. Tester NUMERIC avec nouvelle interface

  Phase 4⃣ : Nouveaux Composants UI

  Objectif : Composants manquants pour les nouveaux types

  1. Créer UI.ToggleField (pour BOOLEAN)
  2. Créer UI.SliderField (pour SCALE)
  3. Étendre UI.FormField pour COUNTER (boutons +/-)
  4. Créer composants TIMER (état + boutons)

  Phase 5⃣ : Implémentation par Type

  Objectif : Un type à la fois pour validation incrémentale

  Ordre recommandé :
  1. SCALE (simple, pas de dépendances)
  2. TEXT (trivial)
  3. CHOICE (réutilise UI.FormSelection)
  4. BOOLEAN (nouveau composant)
  5. COUNTER (logique simple)
  6. TIMER (le plus complexe, état persistant)

  Phase 6⃣ : TrackingItemDialog Générique

  Objectif : Dialogue unifié pour tous les types

  1. Conditionner les champs selon le type dynamiquement
  2. Généraliser la logique de validation
  3. Adapter les appelants (History, Input components)

  Phase 7⃣ : Nettoyage & Optimisation

  Objectif : Supprimer le code legacy

  1. Supprimer l'ancien TrackingUtils (remplacé par NumericTrackingType)
  2. Nettoyer les switch/when hardcodés
  3. Supprimer duration → timer dans les commentaires
  4. Tests de régression complets

  ---
  🔧 Stratégie Anti-Duplication

  Logique Commune Externalisée :

  - TrackingInputManager → Logique de sauvegarde générique
  - TrackingService → Factory pattern pour tous types
  - TrackingItemDialog → Rendu conditionnel selon type
  - TrackingTypeHandler → Encapsulation par type (validation, JSON, config)

  Composants UI Réutilisables :

  - Étendre les composants existants quand possible
  - Créer seulement les manquants (Toggle, Slider spécialisé)
  - Garder la cohérence avec le système UI existant

  Configuration Centralisée :

  - Chaque type gère sa propre config par défaut
  - Factory pour l'instanciation
  - Validation unifiée via les handlers

  ---
  ⚡ Points d'Attention

  1. Compatibilité : Garder NUMERIC fonctionnel à chaque étape
  2. Tests : Valider chaque phase avant la suivante
  3. Performance : Factory pattern léger (pas de reflection)
  4. UI : Respecter les patterns existants (UI.FormField, etc.)
  5. Migration : Données existantes compatibles

  Estimation : ~7 phases, chacune testable indépendamment, montée en charge progressive.



# Spécifications des Types de Tracking

## Architecture Générale

- **onSave** : `(String, Map<String, Any>) -> Unit`
- **value JSON** : Champ `raw` standardisé obligatoire pour affichage unifié
- **Pattern factory** : `NumericTrackingType`, `ScaleTrackingType`, etc.

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
- Saisie libre => ajout auto aux prédéfinis (option correspondante cochée readonly)

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
