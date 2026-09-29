# Plan d'action : finir les briques

Écrit le 2026-09-29, en fin de séance. Le modèle est dans `docs/BRICKS.md` (catalogue, compositions, terme et condition, exceptions) ; ce plan dit l'ordre et le contenu de chaque étape. Il s'élague étape par étape : une étape faite sort d'ici, ses garanties deviennent des tests.

## Règles de conduite

- Une étape = une brique (modèle, parseur, sélecteur) **et** la réécriture de tous ses usages existants, dans le même geste : rien ne reste en double.
- Les détails ouverts de chaque étape (listés sous elle) se valident avec l'utilisateur **avant** de coder, une question à la fois, avec un avis.
- Chaque étape finit verte (`./run test`), commitée, avec ses lignes dans `docs/design/device-checks.md` et `docs/BRICKS.md` mis à jour (colonne « État »).
- Rappel : rien de ce qui a été codé depuis la base 46 n'a tourné sur le téléphone (`device-checks.md`).
- Ailleurs, et à relire avant l'étape qui les cite : dans `docs/design/missing-tools.md`, « Le temps relatif » (la référence, les libellés relatifs, « = » refusé sur un DATETIME) et « Les formes enregistrées » (Instant, période, sélection, pointeur) ; sa section « Objectif » ; dans `docs/design/unified-fields.md`, le réglage de champ réservé à l'utilisateur.

## 4. Chose : les variables

- `ReferenceKind.VARIABLE` ; le service `references` les nomme et les liste dans leur zone ; `ThingBrowser` les montre sous la zone, à côté des outils ; la vérification des champs RÉFÉRENCE les accepte selon `kinds`.
- Le critère d'Objectif qui tape le nom d'une variable passe à une RÉFÉRENCE (réécrit en entier à l'étape 8).
- À valider : l'ordre dans une zone (outils puis variables, ou mêlés selon leurs groupes).
- En passant : choisir une entrée comme cible du pointeur devient possible (`docs/design/pointer.md`), et reste en attente d'un besoin (`TODO.md`).

## 5. Terme

- `Term` sort de `core/variables` pour le cœur ; sa constante devient de tout type ; un sélecteur commun (constante, variable, Lecture) remplace `TermEditor`.
- Une formule refuse une constante qui n'est pas un nombre, en le disant.
- À valider : la forme stockée d'une constante typée — sans doute `{"constant": <valeur stockée>, "field": {"type", "config"}}`, comme la constante d'une variable —, et si les termes constants déjà enregistrés (des nombres nus) migrent ou se lisent comme NUMERIC.

## 6. Condition

- Le modèle : côté, opérateur, côté ; un côté est un terme, ou un champ quand la condition est posée à chaque entrée ; `{"left", "op", "right"}`. Un parseur ; deux évaluations : une fois (en Kotlin, par type, dates relatives comprises) et sur chaque entrée (en SQL, `EntryFilters` générant sa clause depuis la Condition, ses termes lus d'abord à la référence du contexte).
- Un sélecteur commun (côté, opérateur selon le type, côté) remplace `FilterValueInput` et l'intérieur de `PointerFiltersDialog`.
- **Migration des filtres** `{"field", "op", "value"}` → `{"left": {"field"}, "op", "right": {"constant"}}` : base 51 → 52 et sauvegardes (`JsonTransformers`), sur le modèle de `PointerAtV46` ; les endroits : pointeurs des messages et des départs d'automation (`rich_content_json`), sélections des termes de variables (`definition_json`). Les filtres de l'écran des Données structurées ne sont qu'un état d'écran.
- Côté IA : la forme dans le prompt (TOOL_DATA, pointeurs, variables) et ses exemples (`check_prompt_examples.py`), `FilterValues` et `CommandTransformer` qui lisent la nouvelle forme.
- À valider : le nom des clés (`left`, `op`, `right`) face à ce que l'IA écrit le plus ; ce que devient `between` (une cible, deux termes ?).
- Les opérateurs permis sur une date suivent « Le temps relatif » (`missing-tools.md`) : pas de « = » sur un DATETIME, « entre » dit ce qu'on veut.

## 7. Lecture et Sélection d'entrées

- Deux sélecteurs composés : Sélection (Chose + Période + Conditions posées à chaque entrée + champs gardés) et Lecture (Sélection + Champ + Réduction).
- Remplacent : l'assemblage à la main du pointeur (`PointerSelector`, `AttachPanel`) et `ReadingEditor`.

## 8. Critères d'Objectif

- Un critère : sa clé, son nom, indispensable ou non, et une Condition — jugée une fois pour un critère lu (« Par rapport à : la fin de la tentative (maintenant tant qu'elle court) », la période d'une Lecture préremplie à celle de la tentative), posée à la tentative pour un critère saisi (son champ déclaré d'un côté).
- Disparaissent : `kind`, `target`, `target_unit`, `TargetUnit`, `Criterion.meets`, le CHOICE de réduction ; le formulaire du critère devient un éditeur fait des sélecteurs, que l'Objectif branche sur son formulaire de réglages (le crochet est à recréer : sans usage, il a été retiré).
- Pas de migration : aucun Objectif n'existe encore (confirmé le 2026-09-29).
- Ce qui ne change pas vient de `missing-tools.md`, « Objectif » (comptage, indispensables, verrouillage, `goal.validate` et `goal.reopen`) ; le verdict réservé à l'utilisateur, du réglage de champ de `unified-fields.md`.
- Côté IA : le schéma de config, la doc et les exemples de l'Objectif.

## 9. Attente

- Un type d'outil déclare ce qui attend : une Condition jugée une fois, une Lecture d'un côté (« compte de ses entrées où … `>` 0 ») ; Questionnaire `state.status = TO_FILL`, Objectif `TO_VALIDATE`, Messages `status = sent` et `read = false`.
- L'indicateur sur la tuile de l'outil et la somme sur celle de sa zone, dessinés par le thème ; toucher la tuile ouvre la plus ancienne entrée qui attend (l'écran d'un type d'outil reçoit une entrée à ouvrir, chacun dit ce que « ouvrir » veut dire) ; une notification désigne une chose, que l'app ouvre par le même chemin (l'intent, puis zone, outil, entrée).
- À valider : comment un type d'outil déclare une sélection sur « sa propre instance », que le contexte fournit ; la forme de l'indicateur (un nombre, une pastille) dans le contrat du thème.
- Écartés le 2026-09-29, à ne pas reproposer : l'échéance d'une Liste (un champ de l'utilisateur, qu'il faudrait interpréter), l'absence d'une entrée de Suivi ou de Journal, les erreurs du journal.

## Hors de ce plan, en TODO

- D'autres sources de l'attente : une automation qui attend une validation, un message de l'IA arrivé ailleurs.
- Joindre une image ; joindre un fichier au message de départ d'une automation (le composeur n'y a pas toujours de session).
