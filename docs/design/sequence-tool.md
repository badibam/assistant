# L'outil Séance — conception

Conçu le 2026-10-06, réduit le 2026-10-08. Une séance est une suite d'étapes préparée à l'avance, puis faite en suivant l'écran : un minuteur ou un bouton « Fait » par étape, un signal à chaque changement. Elle guide, elle ne mesure pas : une entrée dit qu'une séance a eu lieu, quand, combien de temps, jusqu'où. Les chiffres d'une séance (la charge d'un squat) vont dans un outil qui sait déjà les garder, un Suivi, que la Lecture et le Graphique lisent déjà.

## Nom

`sequence` dans le code (tooltype, opérations, clés) ; « Séance » à l'écran en français, « Session » en anglais. `session` est déjà pris par les sessions d'IA (`ai_sessions`).

## Les usages

| Usage | Déroulé |
|---|---|
| Méditation | 1 étape au temps (20 min), un gong au début et à la fin |
| Pomodoro | bloc × 4 [Travail 25 min, Pause 5 min, sautée au dernier tour], Grande pause 15 min |
| Yoga | postures au temps, une consigne chacune, annoncées à voix haute |
| Fractionné | Échauffement à la main, bloc × 8 [Course 30 s, Récup 90 s], Retour au calme |
| Routine du matin | étapes à la main : l'ordre et le temps passé, ce qu'une Liste ne donne pas |
| Instrument | Gammes 10 min, Morceau à la main, Déchiffrage 15 min |
| Musculation | bloc × 4 [Squat à la main, consigne « 10 × 60 kg », Repos 90 s] ; la charge montée se corrige dans la config, les chiffres faits vont dans un Suivi |
| Recette | étapes à la main ; une cuisson est une étape au temps puis à la main, le travail fait pendant qu'elle tourne écrit dans sa consigne (« Pendant ce temps : la sauce ») |

Une seule étape court à la fois : deux minuteurs en même temps (des pâtes et un four lancés à des moments différents) ne passent pas.

## La config

Un outil = une séance type ; plusieurs séances (A, B, C d'un programme) sont plusieurs outils d'une zone. Le déroulé s'écrit dans le formulaire de config commun (`SettingsForm`, une page par niveau) : pas d'éditeur dédié. L'IA l'écrit et l'ajuste comme toute config.

```json
{
  "steps": [
    { "kind": "step", "name": "Échauffement", "end": "manual" },
    { "kind": "block", "name": "Intervalles", "repeat": 8, "steps": [
      { "kind": "step", "name": "Course", "end": "timed", "duration": 30000, "instruction": "Allure 5 km" },
      { "kind": "step", "name": "Récup", "end": "timed", "duration": 90000, "skip_last_round": true }
    ]},
    { "kind": "step", "name": "Retour au calme", "end": "timed_then_manual", "duration": 300000 }
  ],
  "step_signal": "sound_and_vibration", "countdown": true, "voice": false,
  "schedule": null
}
```

- **Une étape** : un nom, une consigne facultative, une façon de finir — `timed` (le minuteur descend, puis on passe d'office), `manual` (un chrono monte, on touche « Fait »), `timed_then_manual` (le minuteur sonne, le chrono continue en dépassement jusqu'à « Fait »).
- **Un bloc** (`Variant`, `kind`) a un nom et répète ses éléments N fois. Deux niveaux au plus : un bloc contient des étapes ou des blocs, ces derniers des étapes seulement.
- **`skip_last_round`**, sur une étape d'un bloc : sautée à son dernier tour, le repos qui ne précède plus rien.
- **Les signaux** (`step_signal`, `countdown`, `voice`) valent pour toute la séance : au changement d'étape (son, vibration, les deux, rien), le décompte (oui / non), l'annonce vocale de l'étape qui commence et de sa consigne (oui / non, synthèse vocale d'Android).
- **Le planning**, facultatif (`ScheduleSettings`), calqué sur le Questionnaire : à chaque heure, une entrée `planned` et une notification, les heures manquées rattrapées, « Tout ignorer », le point d'attente sur la tuile. Une séance prévue jamais faite reste `planned` : « manquée » se déduit.

## L'entrée

- `state` : `status` (`planned`, `running`, `done`, `stopped`, `ignored`), `started_at`, `ended_at` ; tant qu'elle court, `run` (`SequenceRun`) : le déroulé déplié recopié de la config au démarrage — une config modifiée pendant la séance ne la change pas —, les étapes quittées, et les temps lus sur l'horloge de la séance, arrêtée pendant une pause. `run` s'efface à la fin.
- `data` : `duration`, `paused`, `steps_done`, `steps_skipped`, `steps_not_done` (les étapes qu'une séance arrêtée n'a pas atteintes).
- Ces champs s'écrivent par les opérations de la séance, jamais par l'écriture générique (`tool_data.create` / `update`), qui les écarte (`systemWritten`, l'état entier l'étant déjà) ; les champs supplémentaires de l'entrée s'écrivent normalement. La Lecture les lit déjà, à leur place fixe.

## Les opérations

- `start` (reprend la dernière entrée prévue, ou en crée une) : l'écran seulement — Android refuse de démarrer un service au premier plan depuis l'arrière-plan, où tourne l'IA d'une automation. La notification d'une séance prévue ouvre l'outil.
- `complete` : une séance faite sans suivre l'écran, toutes ses étapes comptées faites, sa durée facultative. Avec `id`, la séance prévue, à son heure prévue sauf si une heure est donnée ; sans `id`, une séance qui n'était pas prévue, à l'heure donnée, alors obligatoire. Écran (« Faite » sur une séance prévue) et IA.
- `correct` : une séance finie corrigée, son heure, sa durée et ses étapes faites, sautées, non atteintes, chacune gardée si absente ; les trois comptes font toujours le nombre d'étapes de la séance, et son état les suit — « faite » sans étape non atteinte, « arrêtée » sinon (`SequenceCorrection`). Les pauses ne se corrigent pas. Écran et IA, comme `ignore` et `ignore_all`.
- `done`, `skip`, `back`, `restart`, `pause`, `resume`, `extend` (+15 s), `stop` : écran et notification seulement ; elles n'ont de sens que pour qui fait la séance.
- `back` annule le dernier passage d'étape, comme si le geste n'avait pas eu lieu : l'étape précédente reprend où elle en était, le temps écoulé depuis lui revient, l'étape quittée n'a pas commencé, les compteurs reviennent en arrière. Une étape au temps finie d'elle-même revient arrêtée à zéro, en attente de « Fait » ou de `restart` — sinon elle repasserait aussitôt.
- `restart` relance l'étape en cours depuis le début.
- Pas d'ajout d'étape ni de tour pendant une séance.

## Pendant la séance

- **L'entrée est créée au démarrage**, `running`, et mise à jour à chaque geste : une séance interrompue n'est jamais perdue.
- **Le temps se calcule depuis des heures enregistrées**, jamais d'un compteur : une app tuée retombe juste à la réouverture.
- **Un service au premier plan tourne du début à la fin**, écran allumé ou non. Il émet seul les signaux, et sa notification (imposée par Android) montre l'étape et le temps restant, avec « Fait » et « Pause ».
- **Un verrou d'éveil garde le processeur allumé** (`PARTIAL_WAKE_LOCK`, permission `WAKE_LOCK`), l'écran restant éteint : le service le prend au démarrage, le rend en pause et à la fin ; Android le rend seul si l'app est tuée. Sans lui, le processeur s'endort écran éteint, service au premier plan ou non, et un signal arrive en retard. En veille profonde, Android n'honore que le verrou d'une app qui a un service au premier plan : à essayer sur le téléphone une fois codé (étapes de 30 s au temps, écran éteint, posé à plat 15 min, chaque gong à l'heure), avec ce qu'il coûte en batterie.
- Le service est de type `specialUse` : `health` demande des permissions de capteurs.
- **Une seule séance en cours dans toute l'app** : `start` refusé ailleurs, en nommant l'outil.
- Un décompte de 5 s avant la première étape.
- L'écran reste allumé tant que la séance en cours est affichée.
- Une séance laissée en cours (app tuée, rouverte trois jours après) n'est pas close d'office : l'écran propose de la reprendre ou de l'arrêter (`resume_interrupted`). Reprise, son étape recommence, et le temps depuis sa dernière écriture compte comme une pause.

## Les signaux

- Quatre moments : le début et la fin de séance (le même son), le changement d'étape, le décompte : un bip à 3, 2 et 1 seconde de la fin d'une étape au temps, puis à zéro le changement d'étape, ou la fin pour la dernière. Une étape plus courte ne bipe que les secondes qu'elle a.
- Les trois sons sont faits de trois notes d'un même marimba (VSCO 2 CE, CC0) : le décompte un do aigu bref, le changement d'étape un do grave puis le sol, le début et la fin les trois notes en accord. `scripts/make_sequence_sounds.py` les tire de `third_party/vsco2-marimba/` en `res/raw/sequence_*.flac`, crédités dans `tools/sequence/sounds.json`. Leur niveau est à juger sur le téléphone.
- Le son passe par le canal du média, la musique baissée un instant : le mode silencieux est respecté, la vibration reste.

## L'écran

- **Sans séance en cours** : « Commencer » (qui dit pour quand une entrée prévue attendait) ; les séances prévues, chacune avec « Faite » et « Ignorer » ; le déroulé en lecture, une ligne par étape, les blocs « × N » en retrait ; l'historique, une ligne par séance (date, état, durée, « 14 / 15 étapes »), qui ouvre la séance pour corriger son heure, sa durée et ses étapes.
- **En cours** : l'étape en grand (nom, tour « 2 / 4 », minuteur ou « Fait », consigne), l'étape suivante en petit, la barre des gestes, le déroulé replié.
- **La tuile** : la dernière séance faite ou la prochaine prévue, le point d'attente ; pendant une séance, l'étape en cours et le temps restant.

## Hors de cette spec

- Les mesures d'une étape (prévu et fait) : la Séance guide, un Suivi garde les chiffres. Une étape qui ouvre un Suivi prérempli viendra si le double geste pèse.
- Plusieurs minuteurs en même temps.
- Dupliquer un élément d'une liste du formulaire de config (une étape) : vaut pour toute liste, à part si l'usage le réclame.
- Un réglage de signal par étape, un signal à mi-étape, le choix du son.
