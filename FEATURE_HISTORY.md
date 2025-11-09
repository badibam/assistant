# Feature: Historique des Discussions

Spécifications pour l'implémentation de l'historique des sessions CHAT avec reprise de discussions.

## Vue d'ensemble

Permet aux utilisateurs de consulter toutes leurs discussions passées, les rechercher, les renommer, et les reprendre là où elles se sont arrêtées.

## Point d'entrée UI

**Menu MainScreen** : Item "Historique des discussions" → Navigation vers HistoryScreen

## Backend

### 1. Fonction de reprise (DÉJÀ IMPLÉMENTÉE)

```kotlin
// AIOrchestrator.kt
suspend fun resumeChatSession(sessionId: String)
```

**Flow** :
1. Valider session (existe, type = CHAT)
2. Request activation via scheduler (même logique que startNewChatSession)
3. Gérer éviction si slot occupé (enqueue + auto-suspend AUTOMATION)

### 2. Opération: Lister les sessions

```kotlin
// AISessionService.kt
operation: "ai_sessions.list"

params: {
  "type": "CHAT",              // Obligatoire
  "hasEndReason": true,        // true = terminées seulement
  "search": "text",            // Optionnel (recherche nom + messages)
  "period": "THIS_WEEK",       // Optionnel (TODAY, YESTERDAY, THIS_WEEK, etc.)
  "offset": 0,                 // Pagination
  "limit": 20                  // Sessions par page
}

return: {
  "sessions": [
    {
      "id": "...",
      "name": "...",
      "createdAt": timestamp,
      "lastActivity": timestamp,
      "messageCount": 5,
      "firstUserMessage": "..." // Preview truncated à 60 chars
    }
  ],
  "total": 42  // Total sessions matchant critères
}
```

**Query SQL** :
```sql
SELECT DISTINCT s.* FROM ai_sessions s
JOIN ai_messages m ON m.sessionId = s.id
WHERE s.type = 'CHAT' AND s.endReason IS NOT NULL
  AND (
    s.name LIKE '%' || :search || '%' COLLATE NOCASE
    OR m.richContentJson LIKE '%' || :search || '%' COLLATE NOCASE
    OR m.textContent LIKE '%' || :search || '%' COLLATE NOCASE
    OR m.aiMessageJson LIKE '%' || :search || '%' COLLATE NOCASE
  )
ORDER BY s.createdAt DESC
LIMIT :limit OFFSET :offset
```

**Note** : Recherche LIKE sans FTS pour MVP (performance acceptable jusqu'à ~5000 messages).

### 3. Opération: Renommer session

```kotlin
operation: "ai_sessions.rename"

params: {
  "sessionId": "...",
  "name": "nouveau nom"  // Max 60 chars, non vide
}
```

Simple UPDATE du champ `name` existant dans `ai_sessions`.

### 4. Opération: Supprimer session

```kotlin
operation: "ai_sessions.delete"

params: {
  "sessionId": "..."
}
```

**Implémentation** : CASCADE DELETE via contraintes FK en DB (messages supprimés automatiquement).

## UI

### HistoryScreen Structure

```
Column (scrollable)
├── UI.PageHeader("Historique des discussions", BACK)
├── SearchBar (TextField avec 🔍)
├── PeriodSelector (filtres temporels)
├── Sessions List (Column)
│   └── SessionCard × N
└── UI.Pagination (si totalPages > 1)
```

### SessionCard

**Affichage** :
- Titre (session.name)
- Preview (firstUserMessage, truncated)
- Métadonnées (date création, nb messages)

**Actions (3 boutons icône - via ACTIONBUTTONS)** :
- Reprendre → `resumeChatSession(sessionId)` + ferme HistoryScreen + ouvre AIFloatingChat
- Renommer → Dialog avec TextField → `ai_sessions.rename`
- Supprimer → Dialog confirmation → `ai_sessions.delete`

### Filtres

**Temporels** : Comme dans AutomationScreen

**Recherche** : TextField au-dessus des filtres temporels
- Recherche dans `name` (session) + contenu messages
- COLLATE NOCASE (insensible à la casse)
- Debounce recommandé (~300ms)

### Pagination

**Pattern** : Même système qu'AutomationScreen
- `UI.Pagination(currentPage, totalPages, onPageChange)`
- 20 sessions par page
- Visible si `totalPages > 1`

### Dialog Renommer

```kotlin
UI.FormField(
  label = "Nouveau nom",
  value = newName,
  fieldType = FieldType.TEXT // 60 chars max
)
```

### Dialog Supprimer

Message : "Cette action est irréversible. Supprimer la discussion '{name}' ?"

Boutons : Annuler / Supprimer (SECONDARY)

## Critères de sélection

**Sessions affichées** : Toutes sessions CHAT avec `endReason != null` (terminées)
- Inclut sessions normales ET sessions depuis automations (avec seedId)

**Tri** : `createdAt DESC` (plus récent d'abord)

## Ordre d'implémentation

1. **Backend** :
   - `ai_sessions.list` (avec recherche SQL + pagination)
   - `ai_sessions.rename`
   - `ai_sessions.delete` (cascade delete)
   - Vérifier `resumeChatSession()` (déjà fait)

2. **UI** :
   - HistoryScreen (structure + filtres)
   - SessionCard composant
   - Recherche textuelle
   - Dialogs (rename, delete)
   - Navigation depuis MainScreen

3. **Test et commit**

## Notes techniques

### Performance recherche

- **LIKE sans FTS** : OK jusqu'à ~5000 messages (~100-200ms)
- **Migration FTS** : Si performance devient problème (>200ms)
- **COLLATE NOCASE** : Surcoût ~5%, négligeable

### Nommage sessions

- Champ `name` déjà existant (auto-généré "Chat HH:mm")
- Pas besoin de nouveau champ
- Renommage modifie champ existant

### Cascade delete

- Contraintes FK déjà en place normalement
- Messages supprimés automatiquement via `ON DELETE CASCADE`
- Pas besoin de suppression manuelle des messages

## Migrations

Aucune migration DB nécessaire (utilise champs existants).

## Strings à ajouter

```xml
<!-- shared.xml -->
<string name="history_title">Historique des discussions</string>
<string name="history_search_placeholder">Rechercher...</string>
<string name="history_empty">Aucune discussion</string>
<string name="history_rename_title">Renommer la discussion</string>
<string name="history_rename_label">Nouveau nom</string>
<string name="history_delete_confirm">Cette action est irréversible. Supprimer la discussion '%1$s' ?</string>
<string name="history_session_messages">%1$d messages</string>
```

---

*Document créé pour implémentation future - Toutes décisions validées*
