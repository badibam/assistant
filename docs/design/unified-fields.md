# Un seul système de champs — ce qui reste

Conception commencée le 2026-09-25. Les blocs A à C sont en place : le modèle (types de champ, `data` / `extra` / `state`, chronomètre, valeurs par défaut portées par un champ, CHOICE, réglages déclarés en champs) est décrit dans `docs/DATA.md`, section « Champs et entrées », et les modules de communication dans `docs/AI.md`. Reste ce qui suit, qui n'est pas encore dans le code.

## À mettre en œuvre

- **D. Le pointeur** : fait pour une zone et un outil (`docs/DATA.md`) ; l'entrée comme cible attend un besoin (`pointer.md`).
- **RÉFÉRENCE**, un type de champ dont la valeur est l'identifiant d'une autre chose de l'app, jamais son nom : renommer la cible ne casse rien, et l'affichage comme la vue de l'IA montrent son nom actuel. Cas d'origine : une entrée « Pomme, 150 g » du suivi Alimentation mène à la fiche « Pomme » des Données structurées pour en calculer les calories. Ce n'est qu'un premier cas : les outils de la chaîne de la vision produit (README) désignent chacun un autre outil dans leur config — un Graphique ce qu'il trace, un Calcul ses entrées, une Alerte ce qu'elle surveille, un Objectif le suivi qu'il mesure — et un réglage se déclare avec les mêmes types de champ. RÉFÉRENCE attend donc le premier outil qui désigne autre chose, quel qu'il soit.
  - **L'adresse** est celle du pointeur (`pointer.md`, `{kind, id}`) : une référence désigne une chose, instance ou entrée, jamais un champ. Un champ se choisit dans ce qu'on lit d'une instance (la lecture du cœur, `missing-tools.md`), par sa clé, figée à sa création quand son libellé se renomme. Une cible supprimée garde son adresse et s'affiche « supprimé ».
  - **La cible du pointeur est une RÉFÉRENCE** : le pointeur n'a plus de notion de cible à lui (`PointerTarget` disparaît), il ajoute à une RÉFÉRENCE ce qu'on en lit (filtres, champs) et ce qu'on en envoie (joindre ou mentionner). Un seul sélecteur, le fil d'Ariane du pointeur (`PointerSelector`), est la saisie d'une RÉFÉRENCE. `TOOL` devient `TOOL_INSTANCE` partout, pointeurs enregistrés et sauvegardes compris.
  - **Ce qu'elle accepte, déclaré par l'usage** : `target: { kinds, tool_instances? }`. `kinds` parmi APP, ZONE, TOOL_INSTANCE, ENTRY (un pointeur les prend tous, une lecture du cœur TOOL_INSTANCE) ; `tool_instances`, avec ENTRY seulement, restreint aux entrées de ces instances, et la saisie devient la liste de leurs entrées, groupées, avec recherche (« aliment » : les entrées d'Aliments et de Recettes). Une restriction de plus attend un cas réel.
  - **Retirer une instance de `tool_instances`** retire la valeur des entrées qui la visaient, refusé sans `confirm_migration` et compté, comme une option de CHOICE retirée ; une instance ou une entrée supprimée ne touche à rien.

## Ouvert

- Un champ obligatoire dans `extra` : une saisie rapide ouvrirait alors la fenêtre d'édition préremplie.
- Ce que `name` veut dire pour chaque outil (nom du raccourci dans le tracking, titre d'une note).
- Réglage d'un champ : label affiché ou non.
- Réglage d'un champ : réservé à l'utilisateur. L'IA le lit, ne l'écrit jamais ; sa commande est refusée avec une erreur qui nomme le champ, contrôlée à l'exécution selon la provenance ; son schéma le marque non modifiable. Premier cas : la valeur saisie d'un critère d'Objectif (`missing-tools.md`). La validation des actions de l'IA ne le couvre pas : elle porte sur tout un outil et demande à chaque fois.
- Les quasi-doublons d'un CHOICE ouvert (« Travail » / « travail » / « boulot ») : aujourd'hui chacun devient une option.
- Corriger une proposition de l'IA avant de la valider, avec le composant de saisie : l'IA doit apprendre ce qui a été changé.
- Vérification sur papier restante : Graphique.
