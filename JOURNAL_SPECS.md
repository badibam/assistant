# Specs ToolType Journal

## Concept
Entrées de journal horodatées avec titre et contenu, triées chronologiquement. Support transcription audio.

## Différences vs Notes
- Tri chronologique automatique (pas de champ position, pas de move up/down)
- Champ sort_order configurable (ascending/descending)
- Navigation vers écran pleine page au lieu de dialog
- Support transcription audio intégré
- Titre obligatoire (via name de BaseSchemas)
- Contenu sans limite de longueur
- Timestamp modifiable
- Icône par défaut: book-open

## Configuration (journal_config)
- `sort_order`: "ascending" | "descending" (default: "descending")
- Hérite config de base (name, description, icon_name, display_mode, management, validateConfig, validateData)

## Données (journal_data)
- `name`: Titre de l'entrée (BaseSchemas, obligatoire)
- `timestamp`: Date/heure (BaseSchemas, modifiable)
- `data.content`: Contenu texte sans limite (obligatoire dans schema, peut contenir "...")

## UI Liste (JournalScreen)
- Affichage: cards cliquables avec Date + Titre + Preview (150 chars max)
- Format date: "Aujourd'hui à HH:MM" pour le jour actuel, "Le DD/MM/YYYY à HH:MM" pour les autres
- Tri selon sort_order de la config
- Bouton "+" déclenche création immédiate en DB puis navigation vers écran édition

## Création Immédiate
- Bouton "+" crée entrée en DB avec valeurs par défaut:
  - name: "Sans titre" (i18n)
  - content: "..."
  - timestamp: maintenant
- Navigation immédiate vers écran édition en mode isCreating=true
- Permet transcription audio (nécessite entry.id existant)

## UI Édition/Création (EditJournalScreen)
- Écran pleine page unifié pour création ET édition
- Ordre des champs:
  1. Date/heure (cliquable → DateTimePicker Android)
  2. Titre (FormField standard)
  3. Contenu (TranscribableTextField pour texte OU audio)
- Actions:
  - Save: update entry en DB
  - Cancel/Back: si isCreating=true → delete entry automatiquement (sans confirmation)
- Mode isCreating: flag pour distinguer création (entry avec valeurs par défaut : champs vides, à remplir) vs édition (vraies données, champs doivent récupérer les valeurs en DB)

## Schéma de Validation
- content obligatoire mais accepte toute string (y compris "...")
- Pas de minLength sur content
- Pas de maxLength sur content

## Icônes
- Défaut: "book-open"
- Suggestions: "book-open", "notebook", "pen-line", "calendar-days", "heart", "sparkles", "moon"

## Display Mode
- Défaut: "EXTENDED" (1×1/2)

## Opérations
- add_entry
- get_entries
- update_entry
- delete_entry
