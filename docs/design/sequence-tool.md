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
    { "id": "s1", "name": "Échauffement", "end": "manual" },
    { "block": true, "repeat": 8, "skip_last": "s3", "steps": [
      { "id": "s2", "name": "Course", "end": "timed", "duration": 30000, "instruction": "Allure 5 km" },
      { "id": "s3", "name": "Récup", "end": "timed", "duration": 90000 }
    ]},
    { "id": "s4", "name": "Retour au calme", "end": "timed_then_manual", "duration": 300000 }
  ],
  "signals": { "step_change": "sound_and_vibration", "countdown": true, "voice": false },
  "schedule": null
}
```

- **Une étape** : un identifiant stable, un nom, une consigne facultative, une façon de finir — `timed` (le minuteur descend, puis on passe d'office), `manual` (un chrono monte, on touche « Fait »), `timed_then_manual` (le minuteur sonne, le chrono continue en dépassement jusqu'à « Fait »).
- **Un bloc** (`Variant`) répète ses éléments N fois. Deux niveaux au plus : un bloc contient des étapes ou des blocs, ces derniers des étapes seulement.
- **`skip_last`** : l'étape d'un bloc sautée à son dernier tour, le repos qui ne précède plus rien.
- **Les signaux** valent pour toute la séance : au changement d'étape (son, vibration, les deux, rien), le décompte (oui / non), l'annonce vocale de l'étape qui commence et de sa consigne (oui / non, synthèse vocale d'Android).
- **Le planning**, facultatif (`ScheduleSettings`), calqué sur le Questionnaire : à chaque heure, une entrée `planned` et une notification, les heures manquées rattrapées, « Tout ignorer », le point d'attente sur la tuile. Une séance prévue jamais faite reste `planned` : « manquée » se déduit.

## L'entrée

- `state` : `status` (`planned`, `running`, `done`, `stopped`, `ignored`), `started_at`, `ended_at` ; tant qu'elle court, l'étape en cours, l'heure de son début, et le déroulé déplié recopié de la config au démarrage — une config modifiée pendant la séance ne la change pas. Le déroulé et l'étape en cours s'effacent à la fin.
- `data` : `duration`, `paused`, `steps_done`, `steps_skipped`, `steps_not_done` (les étapes qu'une séance arrêtée n'a pas atteintes).
- Ces champs s'écrivent par les opérations de la séance, jamais par l'écriture générique (`tool_data.create` / `update`), qui les refuse ; les champs supplémentaires de l'entrée s'écrivent normalement. La Lecture les lit déjà, à leur place fixe.

## Les opérations

- `start` (crée l'entrée ou reprend une entrée prévue) : écran et notification seulement — Android refuse de démarrer un service au premier plan depuis l'arrière-plan, où tourne l'IA d'une automation.
- `log_after` : une entrée `done`, datée au choix, toutes ses étapes comptées faites, sa durée facultative. Écran et IA.
- `done`, `skip`, `back`, `pause`, `resume`, `extend` (+15 s), `stop` : écran et notification seulement ; elles n'ont de sens que pour qui fait la séance.
- `back` revient à l'étape précédente, qui ne compte plus comme faite ; son minuteur repart du début.
- Pas d'ajout d'étape ni de tour pendant une séance.

## Pendant la séance

- **L'entrée est créée au démarrage**, `running`, et mise à jour à chaque geste : une séance interrompue n'est jamais perdue.
- **Le temps se calcule depuis des heures enregistrées**, jamais d'un compteur : une app tuée retombe juste à la réouverture.
- **Un service au premier plan tourne du début à la fin**, écran allumé ou non. Il émet seul les signaux, et sa notification (imposée par Android) montre l'étape et le temps restant, avec « Fait » et « Pause ».
- **Une seule séance en cours dans toute l'app** : `start` refusé ailleurs, en nommant l'outil.
- Un décompte de 5 s avant la première étape.
- L'écran reste allumé tant que la séance en cours est affichée.
- Une séance laissée en cours (app tuée, rouverte trois jours après) n'est pas close d'office : l'écran propose de la reprendre ou de l'arrêter ; l'intervalle compte comme une pause.

## Les signaux

- Quatre moments : le début et la fin de séance (un gong doux, le même), le changement d'étape (un gong léger), le décompte (un bip court sur les 3 dernières secondes d'une étape au temps).
- Le son passe par le canal du média, la musique baissée un instant : le mode silencieux est respecté, la vibration reste.

## L'écran

- **Sans séance en cours** : « Commencer » (qui dit pour quand une entrée prévue attendait) et « Noter après coup » ; le déroulé en lecture, une ligne par étape, les blocs « × N » en retrait ; l'historique, une ligne par séance (date, état, durée, « 14 / 15 étapes »).
- **En cours** : l'étape en grand (nom, tour « 2 / 4 », minuteur ou « Fait », consigne), l'étape suivante en petit, la barre des gestes, le déroulé replié.
- **La tuile** : la dernière séance faite ou la prochaine prévue, le point d'attente ; pendant une séance, l'étape en cours et le temps restant.

## Ce qui reste ouvert

- **Garder le processeur éveillé** : le service au premier plan empêche Android de tuer l'app, pas le processeur de s'endormir écran éteint, et un signal arriverait en retard. Un verrou d'éveil tenu toute la séance ? Les alarmes exactes ne suffisent pas : écran éteint, Android ne les laisse sonner qu'environ toutes les 9 minutes. Le type du service, qu'Android exige, est à nommer : `specialUse` (`health` demande des permissions de capteurs).
- **Les sons** : trouvés (libres, CC0 ou licence acceptée par F-Droid, jamais « NC », dans `third_party/sounds/` avec leur provenance), ou fabriqués par un script dont la sortie est commitée, comme `scripts/make_launcher_icon.py` dessine l'icône.

## Hors de cette spec

- Les mesures d'une étape (prévu et fait) : la Séance guide, un Suivi garde les chiffres. Une étape qui ouvre un Suivi prérempli viendra si le double geste pèse.
- Plusieurs minuteurs en même temps.
- Dupliquer un élément d'une liste du formulaire de config (une étape) : vaut pour toute liste, à part si l'usage le réclame.
- Un réglage de signal par étape, un signal à mi-étape, le choix du son.
