# L'outil Séance — conception

Conçu le 2026-10-06. Une séance est une suite d'étapes préparée à l'avance, puis faite en suivant l'écran : un minuteur ou un bouton « Fait » par étape, les valeurs prévues déjà remplies, corrigées par ce qui a été fait. Elle sert à la musculation, au fractionné, au yoga, à la méditation, au pomodoro, à une routine du matin, à l'instrument, à une recette ; ce qu'aucun outil ne fait aujourd'hui, c'est le plan, son exécution en direct et l'écart entre les deux.

## Nom

`sequence` dans le code (tooltype, opérations, clés) ; « Séance » à l'écran en français, « Session » en anglais. `session` est déjà pris par les sessions d'IA (`ai_sessions`).

## Un outil = une séance type

- Sa config porte le déroulé ; chaque passage est une entrée. Plusieurs séances (A, B, C d'un programme) sont plusieurs outils d'une zone.
- Le déroulé s'écrit dans le formulaire de config commun (`SettingsForm`, une page par niveau) : pas d'éditeur dédié. L'IA l'écrit et l'ajuste comme toute config.

## Le déroulé

- **Les mesures se déclarent une fois, au niveau de la séance** : une liste de définitions de champ (`fieldDefinitions`), comme les champs supplémentaires — nom attribué par l'app, libellé, type, unité. Tous les types de `FieldType` sauf DATE, TIME et DATETIME.
- **Le déroulé est une liste d'éléments**, chacun une étape ou un bloc (`Variant`).
- **Une étape** : un identifiant stable, un nom, une consigne facultative, une façon de finir, les mesures qu'elle utilise avec leur valeur prévue (`{"reps": 10, "charge": 60}`).
- **Les façons de finir** : au temps (le minuteur descend, puis on passe d'office) ; à la main (un chrono monte, on touche « Fait ») ; au temps puis à la main (le minuteur sonne, le chrono continue en dépassement jusqu'à « Fait »).
- **Un bloc** se répète N fois. Deux niveaux au plus : un bloc contient des étapes ou des blocs, ces derniers des étapes seulement.
- **Une mesure ne se retire pas tant qu'une étape l'utilise** : le refus nomme les étapes.

## L'entrée

- **Elle garde le déroulé tel qu'il a été fait, déplié et complet** : la config change (charge montée, étape renommée), l'entrée dit ce qui était prévu ce jour-là. Retirer une mesure ou une étape de la config ne touche aucune entrée — à l'inverse d'un champ supplémentaire, dont le retrait efface les valeurs (`docs/TOOLS.md`) ; à dire dans `docs/TOOLS.md` une fois codé.
- `state` : `status` (`planned`, `running`, `done`, `stopped`, `ignored`), `started_at`, `ended_at`, l'étape en cours tant qu'elle court.
- `data` : les champs de séance (`duration`, `paused`, `steps_done`, `steps_skipped`, `steps_not_done`), les définitions des mesures recopiées une fois, puis `steps`, la liste des étapes dans l'ordre où elles ont été faites, un élément par tour :

```json
{ "step_id": "s2", "name": "Squat", "round": 2, "end": "manual", "planned_duration": null,
  "status": "done", "started_at": 1760028480000, "duration": 70000,
  "measures": { "reps": {"planned": 10, "actual": 8}, "charge": {"planned": 60, "actual": 60} } }
```

- Un élément est `done`, `skipped` ou `not_done` (une séance arrêtée en route).
- **Le déroulé d'une entrée n'est jamais écrit de l'extérieur** : le service le recopie de la config. L'écriture générique (`tool_data.create` / `update`) le refuse ; les champs supplémentaires de l'entrée s'écrivent normalement.

## Les opérations

- Pour l'écran, la notification et l'IA : `start` (crée l'entrée ou reprend une entrée prévue, recopie le déroulé), `log_after` (une entrée terminée, datée au choix, toutes ses étapes faites avec leurs valeurs prévues, sans temps), `correct_step` (les valeurs réelles et l'état d'un élément).
- Pour l'écran et la notification seulement : `done`, `skip`, `back`, `pause`, `resume`, `extend` (+15 s), `stop`. Refusées à l'IA : elles n'ont de sens que pour qui fait la séance.
- `back` sur une étape au temps relance son minuteur ; le temps de la première fois s'ajoute à la seconde.
- Pas d'ajout d'étape ni de tour pendant une séance.

## Pendant la séance

- **L'entrée est créée au démarrage**, `running`, et complétée à chaque geste : une séance interrompue n'est jamais perdue.
- **Le temps se calcule depuis des heures enregistrées**, jamais d'un compteur : une app tuée retombe juste à la réouverture.
- **Un service au premier plan tourne du début à la fin**, écran allumé ou non. Il émet seul les signaux, et sa notification (imposée par Android) montre l'étape et le temps restant, avec « Fait » et « Pause ». Il démarre avec la séance : Android refuse de démarrer un tel service depuis l'arrière-plan.
- **Une seule séance en cours dans toute l'app** : `start` refusé ailleurs, en nommant l'outil.
- Un décompte de 5 s avant la première étape. Une étape au temps passe à la suite avec ses valeurs prévues, corrigées ensuite dans le déroulé déplié.
- L'écran reste allumé tant que la séance en cours est affichée.
- Une séance laissée en cours (app tuée, rouverte trois jours après) n'est pas close d'office : l'écran propose de la reprendre ou de l'arrêter ; l'intervalle compte comme une pause.

## Les signaux

- Quatre moments : le début et la fin de séance (un gong doux, le même), le changement d'étape (un gong léger), le décompte (un bip court sur les 3 dernières secondes d'une étape au temps).
- Réglages de l'outil : le signal au changement d'étape (son, vibration, les deux, rien), le décompte (oui / non), l'annonce vocale de l'étape qui commence et de ses valeurs prévues (oui / non, synthèse vocale d'Android).
- Le son passe par le canal du média, la musique baissée un instant : le mode silencieux est respecté, la vibration reste.
- Les sons sont des sons libres trouvés (CC0 ou licence libre acceptée par F-Droid, jamais « NC »), choisis pour aller ensemble, copiés dans `third_party/sounds/` avec leur licence et leur provenance. Pas de choix de son pour l'instant ; un lien au thème, plus tard peut-être.

## Le planning

Calqué sur le Questionnaire : un planning facultatif (`ScheduleSettings`) ; à chaque heure, une entrée `planned` et une notification, les heures manquées rattrapées, « Tout ignorer », le point d'attente sur la tuile. `start` reprend l'entrée prévue. Une séance prévue jamais faite reste `planned` : « manquée » se déduit.

## L'écran

- **Sans séance en cours** : « Commencer » (qui dit pour quand une entrée prévue attendait) et « Noter après coup » ; le déroulé en lecture, une ligne par étape, les blocs « × N » en retrait ; l'historique, une ligne par séance (date, état, durée, « 14 / 15 étapes »), qui ouvre la séance passée pour la corriger.
- **En cours** : l'étape en grand (nom, tour « 2 / 4 », minuteur ou « Fait », consigne, mesures préremplies modifiables), l'étape suivante en petit, la barre des gestes, le déroulé replié.
- **La tuile** : la dernière séance faite ou la prochaine prévue, le point d'attente ; pendant une séance, l'étape en cours et le temps restant.

## La lecture des séances par les autres outils

- Les champs de séance de `data` sont à une place fixe : la Lecture les lit déjà.
- **Une mesure d'étape** (« Squat › charge ») est dans la liste `steps`, plusieurs fois par entrée. La Lecture apprend à prendre pour lignes les éléments d'une liste d'une entrée, que la brique Filtre trie (`step_id` = s2, `status` = skipped, `round` = 1, `charge` > 50) ; la réduction s'applique à toutes les valeurs retenues. Ce que ça ouvre aux autres outils : `TODO.md`.
- La séance déclare ses champs par `getEntryFields`, comme tout type, depuis sa config ; le sélecteur reste le générique (`ToolFields.filterable`).
- Une mesure ou une étape retirée de la config reste dans les entrées, et un Graphique déjà fait la lit toujours ; mais le sélecteur, qui part de la config, ne la propose plus, et un Graphique rouvert qui la lit serait refusé. Proposé : la séance ajoute à ses champs ceux que gardent les entrées, lus par une requête seulement quand la config ne les a plus.

## À revoir avant de coder

- **Les changements du sélecteur de la Lecture et de la brique Filtre** : la forme d'un champ pris dans une liste d'une entrée, sa place dans le sélecteur générique, ce que deviennent les filtres SQL (`EntryFilters`), la validation d'un tel champ, les mesures et étapes retirées. Rien de cette section n'est arrêté.

## Hors de cette spec

- Dupliquer un élément d'une liste du formulaire de config (une étape) : vaut pour toute liste, à part si l'usage le réclame.
- Un réglage de signal par étape, un signal à mi-étape, le choix du son.
