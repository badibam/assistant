# L'export des entrées d'un outil — conception

Conçu le 2026-10-05. Sortir les entrées d'un outil qui passent un filtre, dans un fichier qu'on lit dans un tableur, qu'on envoie à quelqu'un, ou qu'une automation dépose chaque semaine.

## Ce qu'on exporte

- **Une Sélection d'entrées** (`EntrySelection`, `docs/BRICKS.md`) : un outil, une Période, des Filtres, les champs gardés. Un export porte sur un seul outil ; une zone, c'est un export par outil, demandé à l'IA. Un export de zone en un geste (un sous-dossier daté) viendra si le besoin se répète.
- **Tous les champs de l'entrée** sont proposés : le nom, la date, les champs du type d'outil et ceux de l'utilisateur, l'état (`state.*` : fait, statut, verdict, lu…), `created_at`, `updated_at`, les champs que l'app écrit seule. Les champs gardés de la sélection en retirent. Ordre : nom, date, champs du type d'outil, champs de l'utilisateur dans l'ordre de leur config, état, `created_at`, `updated_at`.

## Le format

Un CSV au format fixe, le même quelle que soit la langue du téléphone (RFC 4180, UTF-8). Un tableur réglé à la virgule décimale l'ouvre par son dialogue d'import, pas d'un double-clic : c'est le prix d'un fichier identique partout.

| Type | Écriture |
|---|---|
| Séparateur | `,`, guillemets autour d'une cellule qui en contient |
| En-tête | la clé du champ (`name`, `timestamp`, `quantity`, `kcal`, `state.checked_at`), jamais son libellé, traduit pour les champs d'un type d'outil |
| NUMERIC, SCALE | `72.4` |
| DATE | `2026-09-12` |
| DATETIME, dont la date de l'entrée | `2026-09-12T08:12:00+02:00`, au fuseau de l'app (`DateTimeConfig.getZoneId()`) |
| TIME | `08:12` |
| DURÉE | `1:25:00` |
| BOOLEAN | `true` / `false` |
| CHOICE à plusieurs valeurs | `a;b` |
| Absence de réponse | cellule vide |

Ces écritures sont toutes des `Writing` de l'importeur, qui relit donc ce fichier ; mais l'export est conçu pour être lu, pas réimporté, et aucun aller-retour n'est promis.

## Où va le fichier

- **Un dossier du téléphone choisi une fois**, dans Réglages › Données : l'app reçoit d'Android un droit d'écriture durable sur ce dossier (`OpenDocumentTree`, permission persistée). Tout export y est écrit sans dialogue, qu'il vienne de l'écran, de l'IA ou d'une automation ; le dossier se voit dans l'app Fichiers et se synchronise par l'outil qu'on veut.
- **Aucun dossier réglé** : l'écran de l'outil le demande sur le moment ; l'IA reçoit un refus qui le dit. Pas de dossier par défaut.
- **Droit retiré ou dossier disparu** : l'export échoue en le disant.
- **Nom** : `<nom de l'outil>_<AAAA-MM-JJ_HHMM>.csv`, le nom de l'outil passé par une fonction de nettoyage unique ; `_2`, `_3`… quand le nom existe. Rien n'est jamais écrasé. L'appelant ne choisit pas le nom.

## D'où on le lance

- **L'écran de l'outil** : « Exporter… » dans son menu ouvre `SelectionPicker` réglé sur cet outil (période, filtres, champs) ; le fichier écrit, un message dit son nom et le nombre d'entrées, avec un bouton Partager (menu de partage d'Android, par un `FileProvider`).
- **L'IA** : l'action `EXPORT_DATA`, aux paramètres de la requête `TOOL_DATA` (`tool_instance_id`, `period`, `filters`, `fields`), traduits de la même façon. Elle ne reçoit que le compte-rendu (fichier, nombre d'entrées), jamais le contenu. Sa carte dans le chat dit l'outil, le nombre d'entrées et le fichier, avec Partager. Pas de validation : rien n'est modifié dans l'app ni écrasé, et le fichier va où l'utilisateur a choisi. Une automation l'emploie de même, sa période relative résolue à l'exécution.
- **Pas le pointeur** : il dit à l'IA « regarde ça » ; exporter une sélection qu'on y a réglée se demande à l'IA.

## Le service

Une opération du cœur, `exports.csv` (Sélection d'entrées → fichier écrit, son nom et son compte), appelée par l'écran et par `EXPORT_DATA`. Une opération longue (`LongOperation`) : elle relit un tout, comme l'export d'une sauvegarde.
