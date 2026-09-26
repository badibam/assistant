# Un seul système de champs — ce qui reste

Conception commencée le 2026-09-25. Les blocs A à C sont en place : le modèle (types de champ, `data` / `extra` / `state`, chronomètre, valeurs par défaut, CHOICE, réglages déclarés en champs) est décrit dans `docs/DATA.md`, section « Champs et entrées », et les modules de communication dans `docs/AI.md`. Reste ce qui suit, qui n'est pas encore dans le code.

## À mettre en œuvre

- **D. Le pointeur** : fait pour une zone et un outil (`docs/DATA.md`) ; le reste dans `pointer.md` (l'app et l'entrée comme cibles, les noms relus à l'affichage).
- **Valeur par défaut d'un champ** : `FieldDefinition` ne la porte pas encore. Elle portera sa valeur par défaut, vérifiée contre le champ lui-même ; le formulaire la préremplira, la saisie rapide du tracking remplira `extra` avec elle, l'IA la lira dans le schéma, et une migration qui manque d'une valeur obligatoire la proposera.
- **Détails des commandes dans le chat** : les valeurs qu'une commande a écrites s'y affichent par les composants de leurs champs, comme dans la demande de validation (`ProposedEntries`).
- **RÉFÉRENCE**, un type de champ dont la valeur est l'identifiant d'une autre chose de l'app, jamais son nom : renommer la cible ne casse rien, et l'affichage comme la vue de l'IA montrent son nom actuel. Cas d'origine : une entrée « Pomme, 150 g » du suivi Alimentation mène à la fiche « Pomme » des Données structurées pour en calculer les calories. À spécifier : l'adresse est celle du pointeur (`pointer.md`, `{kind, id}`) ; la saisie par sélecteur ; une cible supprimée (l'identifiant reste, affiché « supprimé »).

## Ouvert

- Un champ obligatoire dans `extra` : une saisie rapide ouvrirait alors la fenêtre d'édition préremplie.
- Ce que `name` veut dire pour chaque outil (nom du raccourci dans le tracking, titre d'une note).
- Réglage d'un champ : label affiché ou non.
- Les quasi-doublons d'un CHOICE ouvert (« Travail » / « travail » / « boulot ») : aujourd'hui chacun devient une option.
- Corriger une proposition de l'IA avant de la valider, avec le composant de saisie : l'IA doit apprendre ce qui a été changé.
- Données structurées exprimées avec le modèle : propriétés = champs de `extra`, objets imbriqués à plat (un groupement éventuel est de l'affichage), colonnes de liste, recherche et tri à l'outil. La spec `structured-data-tooltype.md` et son schéma JSON propre sont à réécrire en conséquence.
- Vérification sur papier restante : Graphique.
