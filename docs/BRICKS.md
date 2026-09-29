# Briques

Une brique est une notion que l'utilisateur, l'IA et le code manipulent à plusieurs endroits : un instant, une chose de l'app, un champ, une condition… Elle a **un modèle** (une classe du cœur), **une forme stockée** lue par **un seul parseur**, et **un sélecteur** à l'écran. Ce qui varie d'un usage à l'autre, c'est le contexte qui le lui fournit ; jamais une copie de la brique.

## La règle

- Toute saisie, tout stockage, toute lecture d'une notion qui a sa brique passe par elle : son modèle, son parseur, son sélecteur. Un écran ne réécrit pas une liste de champs, un choix d'opérateur ou une saisie de date.
- Une notion nouvelle devient une brique avant son premier usage, et entre dans ce catalogue.
- Une brique composée s'écrit avec les briques dont elle est faite, jamais à côté d'elles.

## Les compositions

Briques de base : **Instant**, **Chose**, **Champ**, **Réduction**, **Valeur d'un champ**, **Planification**. Les autres s'écrivent avec elles :

```
Période             = Instant + Instant
Lecture             = Sélection d'entrées + Champ (ou aucun, pour compter) + Réduction
Terme               = Valeur d'un champ (une constante) | Chose (une variable) | Lecture
Côté                = Terme | Champ (seulement si la condition est posée à chaque entrée)
Condition           = Côté + opérateur + Côté
Sélection d'entrées = Chose (un outil, ou une zone) + Période + Conditions posées à chaque entrée + Champs gardés
```

Un filtre n'est pas une brique à part : c'est une Condition posée à chaque entrée.

## Ce que le contexte fournit

Une brique ne connaît pas l'écran qui l'utilise ; il lui donne ce dont elle a besoin :

- **la référence** : l'instant de lecture, contre lequel se résolvent les dates relatives, les périodes des Lectures et les variables — l'heure prévue d'une exécution, maintenant, « maintenant, ou la fin de la tentative une fois finie » pour un Objectif, l'instant de sa ligne pour le Graphique. Le sélecteur l'affiche (« Par rapport à : l'instant lu »), il ne la fait jamais choisir. Sans référence (le chat, la saisie d'une entrée), l'horloge résout au moment du choix et c'est la date obtenue qui s'enregistre ;
- **l'entrée jugée**, quand une condition est posée à chaque entrée : ses champs deviennent des côtés possibles de la condition ;
- **les sortes permises** (une zone, un outil, une variable, une entrée…) ;
- **les types permis** (un champ numérique, une date…) et **la précision** (jour ou instant) ;
- **l'outil** dont on choisit un champ.

## Terme et condition

- **Un terme est une valeur** : une constante (de tout type : un nombre, une durée, une date — relative comprise —, des options, un oui/non, un texte), une variable, ou une Lecture. Ses périodes relatives se résolvent contre la référence. Un champ n'est pas un terme : c'est une place dans une entrée, qui n'a de valeur qu'une fois l'entrée donnée.
- **Une condition, c'est côté, opérateur, côté.** Un côté est un terme ; quand la condition est posée à chaque entrée, un côté peut aussi être un champ de l'entrée jugée. Les deux côtés sont de types comparables, et ce type dit les opérateurs permis (`EntryFilters.operatorsFor`) : `<` `<=` `=` `>=` `>` sur une valeur de ce type, `between` sur deux, `in` sur des options, `contains` sur un texte, `absent` et `present` sans rien en face.
- **Les deux côtés se lisent au même instant**, la référence du contexte : un côté qui doit regarder une autre période le dit dans son propre terme (« poids, moyenne, le mois précédent »), jamais par un autre instant de lecture.
- **Une condition se juge de deux façons :**
  - **une fois**, les deux côtés étant des termes : un critère lu d'Objectif (« Suivi Sommeil › durée, dernière `≥` 7 h »), l'attente (« compte des passations à remplir `>` 0 »), l'Alerte (« `poids_moyen_7j` `>` 80 ») ;
  - **sur chaque entrée**, un côté au moins étant un champ : un filtre (une Condition posée à chaque entrée d'une Sélection) (« `mangé` `>` `prévu` », « `durée` `<` 6 h »), un critère saisi, posé à la tentative (« `sommeil` `≥` 7 h »). Un filtre s'évalue en SQL et ne compare aujourd'hui un champ qu'à des valeurs écrites (`EntryFilters.parse` refuse une variable ou une Lecture en face) ; le jour où il en prend, elles se liront d'abord, une fois, à la référence du contexte — jamais à l'instant de chaque entrée, que la base ne saurait pas calculer ligne par ligne. Un critère saisi se juge en Kotlin, sur la seule tentative.
- **Une seule forme stockée** : `{"left", "op", "right"}`, chaque côté étant un terme (`{"constant": …}`, `{"variable": …}`, `{"reading": …}`) ou un champ (`{"field": "data.duration"}`), `right` absent pour `absent` et `present`, une paire de côtés pour `between`. « durée < 6 h » : `{"left": {"field": "data.duration"}, "op": "<", "right": {"constant": 21600000}}`.

## Exceptions et séparations voulues

- **La formule d'une variable** est la seule exception assumée : calculer demande un langage (`Formula` : `+ - × ÷`, parenthèses, fonctions), qui n'est pas un assemblage de briques. Ses noms sont des Termes ; sa variante par entrée (`per_entry`, `quantité × aliment.kcal_100g / 100`) lit les champs de chaque entrée à l'intérieur d'une Lecture.
- **La Période reste à côté des Conditions**, alors qu'elle pourrait s'écrire comme deux conditions sur `timestamp` : elle s'applique aussi à une zone entière, à chacun de ses outils, où des conditions sur des champs n'ont pas de sens (décidé le 2026-09-29). Ce n'est pas un doublon à fusionner.
- **Le Graphique écrit ses conditions avec la Condition**, pas avec les prédicats de Vega-Lite : les colonnes de la ligne dessinée sont ses Champs. Le reste de sa config de dessin est le sous-ensemble de Vega-Lite décrit dans `docs/design/missing-tools.md`.

## Catalogue

| Brique | Ce qu'elle choisit | Forme stockée | Modèle, parseur | Sélecteur | État |
|---|---|---|---|---|---|
| **Instant** | une date relative (unité, décalage, début ou fin), une date personnalisée, maintenant, sans limite | la forme stockée du champ (millisecondes, `"2026-09-15"`), `{"relative": {"unit", "offset", "edge"}}`, `{"relative": "NOW"}`, absente pour sans limite | `TimePoint`, `TimePoint.read`, résolu par `TimeResolver` | `InstantPicker` | complète |
| **Période** | deux Instants | `{"start", "end"}`, chaque borne facultative | `EntryPeriod` | `PeriodPicker` | complète |
| **Chose** | par le fil d'Ariane App › zone › outil ou variable › entrée | `{"kind", "id"}` ; ce qu'un champ accepte : `{"kinds", "tool_instances"}` | `Reference`, `ReferenceTarget`, `ThingPath` ; service `references` | `ThingBrowser`, une zone montrée groupe par groupe comme son écran | complète |
| **Valeur d'un champ** | une valeur d'un type de champ | celle du type (`FieldType`) | `FieldValueSchema`, `FieldValueValidator` | `FieldInput` (saisie), `FieldValue` (affichage) | complète |
| **Planification** | une récurrence : quotidienne, hebdomadaire, mensuelle, annuelle, dates précises | `ScheduleConfig` | `ScheduleSettings.group` | `ScheduleConfigEditor` ; `ScheduleSettingEditor` sur un formulaire de réglages | complète |
| **Champ** | un champ des entrées d'un outil, ou aucun pour compter | un chemin : `timestamp`, `name`, `data.x`, `extra.x`, une clé d'état filtrable | `ToolFields.filterable` | `FieldPicker` : le nom seul, le chemin en plus quand deux champs portent le même nom ; gardé par son chemin | complète |
| **Réduction** | dernière, somme, moyenne, min, max, compte, la plus tôt, la plus tard | `"SUM"` | `Reduction`, `Reduction.forType` | `ReductionPicker` : une seule permise est dite sans liste ; les réglages d'Objectif gardent un CHOICE jusqu'à la réécriture de ses critères | complète |
| **Terme** | une constante, une variable, une Lecture | `{"constant"}`, `{"variable"}`, `{"reading"}` ; la constante sans type à elle, lue dans celui de ce qu'on lui compare (un nombre dans une formule) | `Term`, dans `core/terms` | `TermPicker` | complet ; lu une fois par `TermReader` |
| **Condition** | côté, opérateur, côté | `{"left", "op", "right"}`, `right` une paire pour `between` | `Conditions` (forme stockée), `Condition` (ses côtés), `ConditionJudge` (jugée une fois), `EntryFilters` (posée à chaque entrée, en SQL) ; `SettingNode.Condition` pour la déclarer dans une config | `ConditionPicker` (un filtre) ; `ConditionSetting` (jugée une fois, un terme de chaque côté) | complète |
| **Filtre** (une Condition posée à chaque entrée) | un champ, un opérateur, une valeur écrite | la Condition, `{"left": {"field"}, "op", "right": {"constant"}}` | `EntryFilters`, évalué en SQL | `PointerFiltersDialog`, fait de `ConditionPicker` | complet |
| **Sélection d'entrées** | une Chose (un outil, ou une zone pour la période seule), une Période, des Filtres, les champs gardés | `{"target", "period", "filters", "fields"}` | `EntrySelection` | `SelectionPicker` : le navigateur replié en une ligne une fois la chose choisie | complète |
| **Lecture** | une Sélection d'entrées, un Champ, une Réduction | `{"selection", "field", "reduction"}` (+ `"per_entry"` dans un terme) | `readings.read`, `FieldReading` (un échec est une réponse) | `SelectionPicker` : le navigateur replié en une ligne une fois la chose choisie | complète |

## Qui assemble quoi

| Usage | Assemblage | État |
|---|---|---|
| Champ RÉFÉRENCE d'une entrée | Chose | fait |
| Champ DATE ou DATETIME d'une entrée | Instant, sans référence | fait |
| Pointeur d'un message à l'IA | Chose (zone, outil) + Période + Filtres + champs + joindre ou mentionner | fait, sa sélection assemblée à la main |
| Terme d'une variable | Terme, la période de sa Lecture relative à l'instant lu | fait, avec ses propres sélecteurs |
| Critère lu d'Objectif | Condition jugée une fois, « Par rapport à : la fin de la tentative (maintenant tant qu'elle court) » ; une Lecture sans période lit celle de la tentative, une période choisie la remplace | fait |
| Critère saisi d'Objectif | Condition posée à la tentative : le champ saisi d'un côté | fait |
| Attente (l'indicateur d'une tuile, ce qu'ouvrent la tuile et la notification) | les conditions de ses entrées qui attendent, comptées par le cœur (`getWaiting`, `tools.waiting`) ; un point du thème (`WaitingMark`) au coin de l'icône ; la tuile, et la notification qui désigne l'outil, l'ouvrent sur la plus ancienne (`getUsageScreen(openEntry)`) | fait |
| Graphique | par couche, une Sélection d'entrées ou une grille de Termes, chaque ligne lue à son instant ; une Période affichée ; ses conditions de dessin, des Conditions sur les colonnes de la ligne | en conception (`docs/design/missing-tools.md`) |
| Tentatives d'Objectif, invitations de Questionnaire, envois de Messages, automations | Planification | fait |
| Relevé (automation directe) | Terme + Chose (un Suivi) + Champ + Instant | à concevoir |
| Alerte | Condition jugée une fois | à concevoir |
