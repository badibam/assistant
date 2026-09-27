# Le pointeur — conception

Conçu le 2026-09-26. Bloc D de `unified-fields.md`. Le pointeur est le bloc qu'on insère dans un message à l'IA (chat, ou message de départ d'une automation) pour dire « regarde ça ». Aujourd'hui il désigne une zone ou un outil par un chemin et des noms, et joint selon un contexte (GENERIC, CONFIG, DATA) des ressources cochées une à une, sur une période.

## Deux choix indépendants

Un pointeur, c'est **ce qu'on désigne** et **ce qu'on en joint**.

### Ce qu'on désigne

- **Une cible** : l'app, une zone, un outil ou une entrée. Pas de niveau « champ » : une colonne, c'est un outil avec un choix de champs ; un champ d'une seule entrée, c'est l'entrée.
- **Une adresse faite d'identifiants seulement**, l'identifiant le plus précis : `{"kind": "TOOL", "id": "t_4c0"}`, `{"kind": "ENTRY", "id": "e_91f"}`, `{"kind": "ZONE", "id": …}`, `{"kind": "APP"}`. Le chemin (zone, outil) se retrouve à la lecture, les noms aussi : renommer ou déplacer un outil de zone ne casse rien. Une cible supprimée garde son adresse et s'affiche « supprimé ». Cette forme est celle que reprendra le champ RÉFÉRENCE (`unified-fields.md`), conçu avec le premier outil qui désigne autre chose.
- **Une étiquette pour toute entrée** : son nom, sinon sa date et le début de son texte. Chaque type d'outil dit comment s'intitule une de ses entrées (une note n'a pas de nom).
- **Pour un ensemble d'entrées** :
  - **Période** : un filtre sur `timestamp`, montré en tête sous ce nom.
  - **Filtres par valeur** et **choix des champs** : au niveau outil seulement ; à ajouter à l'entrée si le besoin apparaît.

### Ce qu'on joint

Deux cases, **config** et **entrées**, cumulables ; aucune cochée = une **mention**. Le schéma part d'office avec ce qu'il décrit (config ou entrées), l'app ne renvoie jamais deux fois le même dans une session.

| Cible | Config | Entrées |
|---|---|---|
| app | réglages de l'app | entrées de tous les outils |
| zone | config de la zone | entrées de ses outils |
| outil | config de l'outil | ses entrées |
| entrée | config de son outil (décochée par défaut) | l'entrée |

La config n'est jamais filtrée. Une **mention d'entrées filtrées** transmet à l'IA la requête toute prête sans l'exécuter : elle décide quoi en lire (décompte, échantillon, croisement). « Joindre » exécute la même requête avant l'envoi.

## Filtres

- **Sur tout champ du schéma** : `name`, `timestamp`, `created_at`, `updated_at`, `data.*`, `extra.*`, et les clés d'état déclarées filtrables (`StateField.filterable`).
- **Conditions selon le type** :

  | Type | Conditions |
  |---|---|
  | NUMERIC, SCALE, DURÉE | <, ≤, =, ≥, >, entre |
  | TEXT | contient, est |
  | CHOICE | est l'une de |
  | BOOLEAN | oui, non |
  | DATE, DATETIME | avant, après, entre |
  | TIME | avant, après, entre (une heure) |
  | tous | sans réponse, avec réponse |

- **Combinaison** : tous les filtres doivent être vrais (ET). Le OU n'existe qu'à l'intérieur d'un champ (« est l'une de », « entre ») ; un OU entre champs se fait avec deux pointeurs.
- **Saisie de la valeur** : le composant de saisie du champ (`FieldInput`) ; pour TEXT, la liste des valeurs présentes (`tool_data.values`) en plus ; un CHOICE a déjà ses options.
- **Dates** : tout filtre sur une DATE ou un DATETIME prend le sélecteur de période — dates fixes dans le chat, valeurs relatives dans une automation, recalculées à chaque exécution (« échéance dans les 7 prochains jours »). Une TIME prend un choix d'heure.

## Le côté IA

Le pointeur écrit une requête `tool_data.get` avec `filters`, la capacité que l'IA utilise aussi (voir `docs/DATA.md`). Les entrées d'une zone ou de l'app demandent une lecture sur plusieurs outils, qui n'existe pas encore.

## L'écran

Un seul écran :

- **En haut**, le fil d'Ariane (App › Zone › Outil › entrée), qui permet de remonter.
- **Au milieu**, la liste des enfants de la cible ; au niveau outil, « Une entrée précise… » ouvre la liste de ses entrées, avec recherche.
- **En bas**, un panneau fixe :
  - les deux cases ;
  - la Période ;
  - « Filtres et champs… », qui ouvre une fenêtre à part, avec le résumé de ce qui y est réglé ;
  - la phrase qui dit ce qui part : « Mention seule : l'IA voit le nom, rien n'est joint », « Mention : les entrées de Sommeil des 7 derniers jours où durée < 6 h — rien n'est joint, l'IA peut les demander », « Joint : ces entrées (2 champs) ».

Changer de niveau efface ce qui n'y a plus de sens, et le résumé le montre. La pastille du pointeur dans le message dit la même chose en un mot.

## Fait

Le 2026-09-26 : `filters` dans `tool_data.get`, la forme enregistrée (`PointerConfig`, migration v44) et l'écran (`PointerSelector`) pour une zone (config ou mention) et un outil (config, entrées, période, filtres, champs, mention d'entrées restreintes). Le code et `docs/DATA.md` en sont le registre. Le 2026-09-27 : les noms relus à chaque lecture, à l'écran comme pour l'IA (`EnrichmentText`, migration v45), les valeurs présentes d'un champ texte proposées dans ses filtres (`tool_data.values`), chaque borne d'une période dite par son côté (« entre le début de « 2 jours avant » et la fin de « le jour-même » »), et les entrées d'une zone : tous ses outils sur la seule période, une lecture `tool_data.get` par outil avec son schéma ; une zone mentionnée avec une période donne à l'IA ses filtres, à appliquer outil par outil.

## Reste

Conçu le 2026-09-27 :

- **Une entrée** : la cible `{"kind": "ENTRY", "id": …}`, lue par `tool_data.get_single` ; joint l'entrée et son schéma, la config de son outil en option, décochée. Supprimée, elle se lit « supprimé » comme toute cible (`EnrichmentText` relit les entrées pointées).
- **Son étiquette** : une règle commune — la date et l'heure, puis le nom de l'entrée, sinon le début de son premier champ texte — qu'un type d'outil peut remplacer par une fonction de son contrat. Le suivi la remplace pour montrer sa valeur : « 26/09 08:12 — Pesée : 72,4 kg ».
- **La choisir** : au niveau d'un outil, « Une entrée précise… » ouvre la liste de ses entrées, les plus récentes d'abord, 30 par 30 (« Voir plus »), restreinte à la période du sélecteur quand il en a une. Pas de recherche texte : elle demande un « ou » entre champs que les filtres n'ont pas ; à reprendre si le besoin se confirme.
- **L'app** : ni ses entrées (tout l'historique de tous les outils), ni ses réglages tant que l'IA ne sait pas les lire — ils n'ont ni commande ni schéma. La cible APP attend ce chantier-là.
