# Un seul système de champs — ce qui reste

Conception commencée le 2026-09-25. Les blocs A à C sont en place : le modèle (types de champ, `data` / `extra` / `state`, chronomètre, valeurs par défaut portées par un champ, CHOICE, réglages déclarés en champs) est décrit dans `docs/DATA.md`, section « Champs et entrées », et les modules de communication dans `docs/AI.md`. Reste ce qui suit, qui n'est pas encore dans le code.

## À mettre en œuvre

- **D. Le pointeur** : fait pour une zone et un outil (`docs/DATA.md`) ; l'entrée comme cible attend un besoin (`pointer.md`).

## Ouvert

- Un champ obligatoire dans `extra` : une saisie rapide ouvrirait alors la fenêtre d'édition préremplie.
- Ce que `name` veut dire pour chaque outil (nom du raccourci dans le tracking, titre d'une note).
- Réglage d'un champ : label affiché ou non.
- Réglage d'un champ : réservé à l'utilisateur. L'IA le lit, ne l'écrit jamais ; sa commande est refusée avec une erreur qui nomme le champ, contrôlée à l'exécution selon la provenance ; son schéma le marque non modifiable. Premier cas : la valeur saisie d'un critère d'Objectif (`missing-tools.md`). La validation des actions de l'IA ne le couvre pas : elle porte sur tout un outil et demande à chaque fois.
- Les quasi-doublons d'un CHOICE ouvert (« Travail » / « travail » / « boulot ») : aujourd'hui chacun devient une option.
- Corriger une proposition de l'IA avant de la valider, avec le composant de saisie : l'IA doit apprendre ce qui a été changé.
- Vérification sur papier restante : Graphique.
