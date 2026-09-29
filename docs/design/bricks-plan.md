# Plan d'action : finir les briques

Écrit le 2026-09-29, en fin de séance. Le modèle est dans `docs/BRICKS.md` (catalogue, compositions, terme et condition, exceptions) ; ce plan dit l'ordre et le contenu de chaque étape. Il s'élague étape par étape : une étape faite sort d'ici, ses garanties deviennent des tests.

## Règles de conduite

- Une étape = une brique (modèle, parseur, sélecteur) **et** la réécriture de tous ses usages existants, dans le même geste : rien ne reste en double.
- Les détails ouverts de chaque étape (listés sous elle) se valident avec l'utilisateur **avant** de coder, une question à la fois, avec un avis.
- Chaque étape finit verte (`./run test`), commitée, avec ses lignes dans `docs/design/device-checks.md` et `docs/BRICKS.md` mis à jour (colonne « État »).
- Rappel : rien de ce qui a été codé depuis la base 46 n'a tourné sur le téléphone (`device-checks.md`).
- Ailleurs, et à relire avant l'étape qui les cite : dans `docs/design/missing-tools.md`, « Le temps relatif » (la référence, les libellés relatifs, « = » refusé sur un DATETIME) et « Les formes enregistrées » (Instant, période, sélection, pointeur) ; sa section « Objectif » ; dans `docs/design/unified-fields.md`, le réglage de champ réservé à l'utilisateur.

## 9. Attente

- Un type d'outil déclare ce qui attend : une Condition jugée une fois, une Lecture d'un côté (« compte de ses entrées où … `>` 0 ») ; Questionnaire `state.status = TO_FILL`, Objectif `TO_VALIDATE`, Messages `status = sent` et `read = false`.
- L'indicateur sur la tuile de l'outil et la somme sur celle de sa zone, dessinés par le thème ; toucher la tuile ouvre la plus ancienne entrée qui attend (l'écran d'un type d'outil reçoit une entrée à ouvrir, chacun dit ce que « ouvrir » veut dire) ; une notification désigne une chose, que l'app ouvre par le même chemin (l'intent, puis zone, outil, entrée).
- À valider : comment un type d'outil déclare une sélection sur « sa propre instance », que le contexte fournit ; la forme de l'indicateur (un nombre, une pastille) dans le contrat du thème.
- Écartés le 2026-09-29, à ne pas reproposer : l'échéance d'une Liste (un champ de l'utilisateur, qu'il faudrait interpréter), l'absence d'une entrée de Suivi ou de Journal, les erreurs du journal.

## Hors de ce plan, en TODO

- D'autres sources de l'attente : une automation qui attend une validation, un message de l'IA arrivé ailleurs.
- Joindre une image ; joindre un fichier au message de départ d'une automation (le composeur n'y a pas toujours de session).
