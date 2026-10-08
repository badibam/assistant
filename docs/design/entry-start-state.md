# L'état de départ d'une entrée

Conception, à relire avant de coder. Une fois codée, ses garanties deviennent des tests et elle s'élague.

## Le problème

L'état d'une entrée (`state`) dit où elle en est : un message à envoyer ou envoyé, un questionnaire à remplir ou rempli, une tentative en cours ou à valider. Les outils qui en ont un ne regardent que lui : l'envoi de Messages ne prend que les entrées « à envoyer » (`pending`).

Une entrée créée sans état est donc un fantôme. Mesuré le 2026-10-08 : l'IA a créé un message « Test » pour 12 h 51 par `CREATE_DATA`, sans état ; l'envoi ne l'a jamais vu, sans erreur nulle part. L'IA ne pouvait pas faire mieux : le schéma lui dit que l'état est « écrit par l'app, jamais envoyé ».

Le même trou ailleurs : un questionnaire passé par l'IA dans un chat est écrit sans état ; l'écran le lit « rempli » (`QuestionnaireScreen`, absent = rempli), la tuile et les filtres sur `state.status` ne le voient pas.

Et l'inverse : le service prend aujourd'hui l'état de tout appelant tel quel (`ToolDataService`, création) ; seule la consigne du schéma l'interdit à l'IA.

## Le principe

Celui qui crée l'entrée dit son statut de départ ; l'outil répond par l'état complet qu'il pose, ou refuse. Rien n'est deviné d'après le contenu : un questionnaire peut être terminé avec des réponses vides.

- Un outil dont l'état a un statut déclare sa réponse : une fonction du contrat des types d'outils, qui reçoit le statut demandé, l'entrée et l'origine de l'appel (`Source`), et rend l'état à écrire, ou un refus avec sa raison.
- Le reste de l'état (envoyé ou non, heure de remplissage, fin de période) est posé par l'outil dans sa réponse, jamais par l'appelant.
- Un outil sans statut (Liste, Notes) ne déclare rien : rien ne change pour lui.

## Qui passe par la réponse de l'outil

- Toute création venue d'un écran, de l'IA, d'un client MCP ou d'un import : elle donne au plus un `status` ; tout autre champ d'état qu'elle envoie est refusé.
- Les écrans de l'outil lui-même aussi (l'écran Questionnaire, l'opération « envoyer » de Messages) : ils donnent le statut et l'outil pose le reste, pour qu'un même geste n'ait qu'une façon de s'écrire.
- Ne passent pas par elle : ce que l'app écrit elle-même (`byTheApp` : un programmateur par `processScheduledTask`, la démo, une migration), qui écrit l'état entier comme aujourd'hui.

Trois programmateurs créent aujourd'hui leurs entrées par `processUserAction`, comme un écran : Messages, Questionnaire, Séance. Ils passent à `processScheduledTask`, ce qu'ils sont.

## Ce que répond chaque outil

| Outil | Statuts de départ permis | État posé | Refus |
|---|---|---|---|
| Messages | `pending` | `pending`, `triggered_by` = `MANUAL` | tout autre statut |
| Questionnaire | `to_fill`, `filled` | le statut ; `filled_at` = maintenant si `filled` | `ignored` et tout autre |
| Objectif | aucun | — | toujours : une tentative s'ouvre en activant l'objectif (`UPDATE_TOOL`, `enabled`, `start`), son programmateur calcule la période et copie la définition |
| Séance | aucun | — | toujours : une séance se lance par son opération |

Une entrée créée sans statut, dans un outil qui en a, est refusée ; l'erreur liste les statuts permis.

## Ce que voit l'IA

- Le schéma d'une entrée montre `state.status` comme écrivable à la création, avec ses valeurs permises ; le reste de l'état reste « écrit par l'app ».
- Un outil qui refuse toute création le dit dans son schéma, avec le geste à faire à la place.
- Le prompt L1 (`ai_prompt_chunks.xml`) dit la règle une fois, avec un exemple de message programmé ; `scripts/check_prompt_examples.py` vérifie l'exemple.

## Ce qui change ailleurs

- Questionnaire : la lecture « absent = rempli » de l'écran disparaît ; une migration donne `filled` aux entrées sans état.
- L'import : une table importée dans un outil à statut doit porter une colonne `state.status`, sinon ses lignes sont refusées en le disant (à rapprocher de l'item du `TODO.md` sur les colonnes `state.*` de l'importeur).

## Tests

- Une création sans statut dans Messages, Questionnaire : refusée, l'erreur liste les statuts permis.
- Messages, `pending` demandé par l'IA : l'entrée est `pending` et `MANUAL`, et l'envoi la prend à son heure.
- Questionnaire, `filled` : `filled_at` posé ; `to_fill` : la reprise commence à la première question sans réponse.
- Objectif, Séance : toute création refusée, l'app la leur faisant passer par leur programmateur.
- Un champ d'état autre que `status` envoyé par l'IA ou un écran : refusé.
- Un programmateur (`byTheApp`) écrit son état entier sans passer par la réponse.

## Ce qui reste ouvert

- La démo écrit des tentatives d'Objectif avec un verdict (`DemoService`) : vérifier qu'elle passe bien comme écrite par l'app.
- Une tentative passée d'un Objectif, enregistrée à la main ou importée : refusée pour l'instant ; le jour du besoin, la réponse d'Objectif posera un état au lieu de refuser (la période où tombe la date, une tentative déjà là, le verdict, la définition à copier).
