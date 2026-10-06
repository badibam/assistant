# Des groupes qui existent — conception

Conçu le 2026-10-06. Un groupe est un nom retenu par ce qu'il range ; aujourd'hui, rien ne garantit que ce nom existe encore. Un groupe renommé ou supprimé laisse ses éléments sur l'ancien nom, affichés hors groupe sans que rien ne le dise, et l'IA peut donner un nom qui n'a jamais existé.

## La règle

Un élément rangé a pour groupe `null` ou un groupe qui existe. Deux sortes de groupes :

- **les groupes de l'accueil** (`zone_groups`, réglages `main_screen`), retenus par les zones (`zones.group`) ;
- **les groupes d'outils d'une zone** (`zones.tool_groups`), retenus par ses outils (`group` de leur config), ses automations (`automations.group`) et ses variables (`group` d'une variable).

## Ce qui la tient

1. **Les données existantes.** La migration 61 → 62 remet à `null` tout groupe absent de sa liste ; l'import d'une sauvegarde fait la même chose (un `GroupsAtV62` partagé). L'écran n'en change pas : ces éléments s'affichent déjà hors groupe.
2. **Un groupe inconnu est refusé** par le service qui l'écrit (`zones`, `tools`, `automations`, `variables`), et le refus nomme les groupes qui existent.
3. **Supprimer un groupe utilisé est refusé** : une zone retient le groupe de l'accueil, un outil, une automation ou une variable le groupe d'outils. Le refus nomme ce qui l'utilise.
4. **Renommer met à jour tout ce qui retient l'ancien nom**, dans la même écriture que la liste.
5. **Changer de zone vide le groupe**, pour un outil, une automation ou une variable. À l'écran, choisir une autre zone retire aussitôt le groupe, et le sélecteur propose ceux de la zone d'arrivée. Au service, un déplacement sans groupe donné vide le groupe et le résultat le dit ; un groupe donné doit exister dans la zone d'arrivée.

Une fois la règle tenue, `ZonePositions.section` et `ToolPositions.section` n'ont plus de groupe inconnu à ranger hors groupe : ce cas sort du code.

## Le renommage

Le service ne voit que l'ancienne liste et la nouvelle : il ne sait pas distinguer un renommage d'une suppression suivie d'un ajout. C'est l'éditeur de liste qui le sait.

- **L'éditeur de liste** (`ListForm`, `SettingsForm`) retient le nom d'origine de chaque élément d'une liste de valeurs, en état d'écran : un élément existant garde son origine à travers les déplacements, un élément ajouté n'en a pas, un élément supprimé l'emporte. Modifier le texte d'un élément est un renommage ; supprimer puis ajouter le même nom ne l'est pas.
- **L'écran** envoie avec la liste les paires de ses éléments dont le nom a changé : `renames`, par nom de liste — `{"zone_groups": {"Santé": "Corps"}}` à `app_config.update`, `{"tool_groups": {...}}` à `zones.update`. Deux noms échangés donnent deux paires, appliquées ensemble.
- **Le service** applique d'abord les renommages à ce qui retient les anciens noms, puis refuse une suppression encore utilisée, puis écrit la liste — une seule transaction.

## Côté IA

- L'aperçu de l'app (`PromptManager`, niveau 3) porte la liste des groupes de l'accueil, dans leur ordre.
- CREATE_ZONE et UPDATE_ZONE refusent un groupe inconnu ; UPDATE_ZONE prend `renames` pour les groupes d'outils. L'IA n'a pas de commande des réglages de l'app : elle ne renomme pas les groupes de l'accueil.
- UPDATE_TOOL et UPDATE_VARIABLE avec `zone_id` disent dans leur texte qu'un déplacement vide le groupe, sauf groupe donné de la zone d'arrivée.

## Tests

- `GroupsAtV62` : un groupe absent de sa liste devient `null`, un groupe présent reste, pour les quatre sortes d'éléments.
- Un groupe inconnu refusé, avec la liste des groupes existants, à chacun des quatre services.
- Une suppression utilisée refusée et nommée ; un renommage qui suit chez tous ceux qui retiennent le nom ; deux noms échangés.
- Les origines de l'éditeur de liste à travers une modification, un déplacement, une suppression, un ajout.
- Un outil, une automation, une variable déplacés : groupe vidé, ou gardé s'il est donné et existe dans la zone d'arrivée.
