# La grille des outils d'une zone

Conçue le 2026-09-29. Aujourd'hui, l'écran d'une zone dessine chaque outil en tuile LINE, l'un sous l'autre, quel que soit le mode d'affichage réglé. Cette spec dit comment les tuiles se placent selon leur mode, et comment l'utilisateur les déplace.

## La grille

- **Une grille par groupe.** Les sections de groupe restent ; chacune range ses outils dans sa grille. La carte des variables et les cartes d'automation restent sous la grille, en pleine largeur.
- **Quatre colonnes, des cases carrées** d'un quart de la largeur. Le thème fixe une largeur maximale de la grille (par exemple 480 dp), centrée au-delà : un grand écran qui ignore le verrou en portrait (`TODO.md`) garde le même affichage, jamais un de plus de quatre colonnes.
- **La taille d'une tuile vient de son mode**, en cases (largeur × hauteur) : ICON 1×1, MINIMAL 2×1, LINE 4×1, CONDENSED 2×2, EXTENDED 4×2, SQUARE 4×4, FULL 4 × la hauteur de son contenu. Le mode reste un réglage de l'outil, dans sa config ; le mode d'édition ne le change pas.
- **Une tuile de pleine largeur est seule sur ses lignes** : seules ICON, MINIMAL et CONDENSED partagent une ligne. Une FULL compte donc comme une ligne à elle seule, quelle que soit sa hauteur.
- **Les tailles fixes du thème disparaissent** (`ToolCardContainer`, 64 dp pour ICON, 256 dp pour SQUARE…) : la grille donne sa taille à chaque tuile.

## Les modes d'affichage

- **Sept modes, sans ajout ni retrait.**
- **Une case fait deux lignes** : une ligne est un huitième de la largeur de la grille, et toute hauteur dans une tuile se compte en lignes. Le thème dimensionne texte et boutons pour qu'une ligne en tienne une rangée.
- **Les zones d'une tuile suivent les cases** : aucune case n'est partagée entre deux zones. Le cœur dessine le cadre et l'en-tête (icône et nom, 2×1) ; le type d'outil dessine deux choses (`TileContent`) : son **résumé**, un seul composant de 2×1, le même partout où il a sa place, actions comprises ; et son **corps**, sur les rangées qu'on lui donne.

  | Mode | En-tête (cœur) | Résumé (outil) | Corps (outil) |
  |---|---|---|---|
  | ICON | l'icône seule, 1×1 | — | — |
  | MINIMAL | 2×1 | — | — |
  | LINE | 2×1 à gauche | 2×1 à droite | — |
  | CONDENSED | 2×1 en haut | 2×1 dessous | — |
  | EXTENDED | 2×1 en haut à gauche | 2×1 en haut à droite | 4×1 |
  | SQUARE | 2×1 en haut à gauche | 2×1 en haut à droite | 4×3 |
  | FULL | 2×1 en haut à gauche | 2×1 en haut à droite | 4 × ce qu'il faut, arrondi à la case |
- **Deux pastilles sur l'icône**, dessinées par le thème dans tous les modes, chacune dans son coin : l'attente (`bricks-plan.md`, étape 9), et un chronomètre en cours sur une entrée de l'outil (un champ durée qui tourne, lu par `tool_data.get` avec `running`, pour tout type d'outil).
- **Jamais de défilement à l'intérieur d'une tuile.** Ce qui ne tient pas, et comment le signaler, est l'affaire du type d'outil ; FULL grandit avec ce que le type d'outil y montre.
- **Une tuile porte ses raccourcis d'utilisation** : un élément tactile de la tuile fait son action, un toucher ailleurs ouvre l'outil (ou l'entrée qui attend), l'appui long sa config. La taille minimale d'un élément tactile est celle du thème. En édition, rien ne réagit.
- **Tous les modes sont proposés pour tout outil**, et chaque type d'outil remplit chacun : `TileContent` n'a plus de rendu par défaut. S'il a trop peu à montrer dans un mode, l'utilisateur en change.

## Les tuiles, par type d'outil

Une tuile se décrit par son résumé (sa LINE) et son corps : ce que montre chaque rangée gagnée, et sa FULL. La CONDENSED n'est que le résumé ; l'EXTENDED et la SQUARE se déduisent (une rangée de corps pour l'une, trois pour l'autre). Un toucher sur une entrée montrée l'ouvre.

### Suivi

- **La dernière entrée** : celle dont le chronomètre tourne s'il y en a une, sinon la plus récente.
- **Résumé** : la dernière entrée en cours → son nom, son temps qui défile, un bouton d'arrêt. Sinon → sa valeur et le temps écoulé depuis, et deux boutons : **rapide** (la même entrée maintenant : même nom, valeur, unité ; pour un compteur le même pas, une occurrence le fait seul, une durée un chronomètre relancé ; les champs personnalisés à leur valeur par défaut), pour toute sorte de Suivi, et **personnalisé** (la fenêtre de saisie, préremplie du nom et de la valeur de la dernière).
- **Corps** : les raccourcis de la config dans leur ordre, quatre par rangée sur deux colonnes, chacun avec son bouton comme sur l'écran de l'outil. La dernière entrée peut s'y retrouver parmi les raccourcis : ce doublon est accepté.
- **FULL** : tous les raccourcis.

  ```
  ┌─────────┬─────────┬─────────┬─────────┐
  │ ◉ Boissons        │ Eau 250 ml        │
  │                   │ il y a 2 h [⚡][✎] │
  ├─────────┼─────────┼─────────┼─────────┤
  │ Eau 250 ml    [+] │ Café 1 tasse  [+] │
  │ Thé 300 ml    [+] │ Jus 200 ml    [+] │
  └─────────┴─────────┴─────────┴─────────┘
  ```

### Journal

- **Résumé** : le bouton « Écrire une entrée », qui crée une entrée et l'ouvre en modification ; sur la deuxième ligne, la date de la dernière.
- **Corps** : deux entrées par rangée, une par ligne, titre et date relative, les plus récentes d'abord.
- **FULL** : les dix dernières entrées.

### Notes

- **Résumé** : le bouton « Nouvelle note », qui ouvre l'outil sur une note neuve ; sur la deuxième ligne, le nombre de notes.
- **Corps** : deux notes par rangée, côte à côte, chacune en carte de 2×1 (le début de son texte sur deux lignes), dans l'ordre manuel.
- **FULL** : toutes les notes.

### Liste

- **Résumé** : le nombre de non cochés (« 3 non cochés ») ; sur le total si la config garde les cochés (« 3 / 8 »).
- **Corps** : quatre non cochés par rangée, sur deux colonnes, chacun avec sa case, qui se coche sans ouvrir l'outil.
- **FULL** : tous les non cochés ; les cochés restent dans l'outil.

### Messages

- **Résumé** : « 5 non lus » et, sur la deuxième ligne, le titre du plus ancien non lu (ce qu'ouvre le toucher de la tuile) ; « Tous lus » et le prochain envoi (« Prochain : demain 8:00 », ou « Aucun envoi prévu »).
- **Corps** : deux messages par rangée, un par ligne, titre (en gras s'il n'est pas lu) et date relative : les non lus du plus ancien au plus récent, puis les derniers lus. Ouvrir un message le marque lu.
- **FULL** : tous les non lus, puis les derniers lus, jusqu'à dix au total.

### Objectif

- **Résumé** : la tentative en cours comptée, « 4 / 5 atteints » (`goal.evaluate` : les éléments du premier niveau, critères ou sous-objectifs, atteints sur ceux exigés), et « 4 j restants », ou « À valider » quand elle attend. Sans tentative en cours : « Entre deux tentatives » et « Prochaine dans 2 j » ; arrêté par son interrupteur : « Arrêté » et le dernier verdict ; ponctuel terminé : « Réussi » ou « Échoué » et sa date.
- **Corps** : deux critères par rangée, un par ligne, dans l'ordre de la config : son nom, sa valeur face à sa condition (« Calories  1204 / ≤ 2100 »), et s'il est rempli. En lecture seule.
- **FULL** : tous les critères, rangés par sous-objectif. Rien ne s'y valide : on valide dans l'outil.

### Questionnaire

- **Résumé** : « 2 à remplir » et le moment prévu de la plus ancienne (« il y a 13 h », ce qu'ouvre le toucher de la tuile) ; rien à remplir : « À jour » et, planifié, le prochain (« Prochain : ce soir 20:00 »), sinon la dernière passation (« Dernier : il y a 2 j »).
- **Corps** : deux réponses de la dernière entrée remplie par rangée, une par ligne, la question et sa réponse, dans l'ordre des questions.
- **FULL** : toutes les réponses de la dernière entrée remplie.

### Données structurées

- **Résumé** : le nombre de fiches (« 124 fiches ») et le bouton « Nouvelle fiche », qui ouvre l'outil sur une fiche neuve.
- **Corps** : deux fiches par rangée, une par ligne, leur nom seul, les plus récemment modifiées d'abord.
- **FULL** : les dix dernières modifiées.

## Positions

- **Chaque outil garde sa place** : `grid_x` (0 à 3) et `grid_y` (sa ligne dans son groupe), deux colonnes de `tool_instances`. `order_index` disparaît des outils : rien ne le change aujourd'hui.
- **Migration** : les outils existants sont posés un par un, dans leur ordre actuel, par la règle d'arrivée ci-dessous. Les sauvegardes suivent (`JsonTransformers`).
- **Un outil qui arrive dans un groupe** (créé par l'utilisateur ou l'IA, passé dans ce groupe par sa config, venu d'une autre zone plus tard) se pose en bas, sur une ligne neuve, en colonne 0.
- **Un outil qui part** (supprimé, changé de groupe) laisse un trou ; une ligne restée vide se referme.
- **Un interstice** est la limite entre deux lignes qu'aucune tuile ne traverse : on n'ouvre jamais une ligne au milieu d'une tuile haute de plusieurs lignes.
- **Une tuile qui grandit** (son mode changé) reste à sa place, sa colonne ramenée à gauche si elle dépasse le bord droit. Les tuiles qu'elle recouvre descendent dans des lignes neuves, ouvertes au premier interstice sous elle : elles gardent leur colonne et leur disposition entre elles, et les lignes du dessous descendent d'autant, en bloc. On ne cherche pas de trou ailleurs.

  ```
  avant                     a passe de MINIMAL à CONDENSED
  [ a  a ][ b  b ]          [ A  A ][ b  b ]
  [ c ][  ][ d  d ]         [ A  A ][ d  d ]
  [ e  e ][      ]          [ c ][         ]   ← ligne ouverte, c garde sa colonne
                            [ e  e ][      ]
  ```
- **Une tuile qui rétrécit** reste à sa place et laisse un trou.
- **L'IA ne voit ni ne change les positions** : ni dans `APP_STATE`, ni par une opération. Elle peut changer le mode d'un outil, par sa config ; la règle de la tuile qui grandit s'applique alors.

## Le mode d'édition

- **Un bouton en icône, à droite de la ligne de titre du groupe**, allume l'édition du groupe ; il se voit activé tant qu'elle dure, et la ferme quand on le rappuie. Cet état « activé » n'existe pas encore : un paramètre `active` d'`UI.ActionButton`, que chaque thème dessine (le thème par défaut, en fond plein de la couleur principale).
- **Un seul groupe à la fois.** Le reste de l'écran (autres groupes, variables, automations) est atténué et ne réagit pas, sauf les boutons d'édition des autres groupes : en appuyer un ferme la séance en cours et ouvre celle-là.
- **En édition, un quadrillage léger** dessiné par le thème montre les cases, donc les trous. Les tuiles gardent leur contenu mais ne réagissent plus à leurs propres gestes (cocher une Liste, défiler dans une SQUARE) : un toucher sélectionne.
- **Toucher un outil le sélectionne** ; les autres tuiles s'atténuent, et une barre apparaît en bas de l'écran, en pleine largeur : une croix de quatre flèches, un bouton de validation en icône au centre, « Annuler » à côté.
- **Une flèche saute à la prochaine place où la tuile tient**, dans sa direction :
  - ← et → avancent d'un quart sur la même ligne, jusqu'à la prochaine colonne où elle tient ;
  - ↑ et ↓ gardent la colonne : la première ligne où elle tient, ou le premier interstice, ce qui vient en premier ; à un interstice, une ligne neuve s'ouvre et les lignes suivantes descendent ;
  - sans place dans sa direction, la flèche se grise ;
  - la ligne que la tuile laisse vide se referme aussitôt.
- **La validation** enregistre la place de cet outil et les lignes ouvertes ou refermées par son déplacement, en une écriture, et le désélectionne ; on reste en édition. **« Annuler »** remet le groupe comme il était quand l'outil a été touché, et le désélectionne.
- **Toucher un autre outil**, ou fermer l'édition par son bouton, valide le déplacement en cours.
- **Le bouton retour du téléphone** quitte la zone. Avec un déplacement en cours, il demande d'abord : « Quitter la zone ? Le déplacement en cours sera annulé. »
- **L'écran ne défile que s'il le faut** : quand la tuile passerait sous la barre ou au-dessus du haut, juste assez pour la garder visible avec une ligne de marge. Un espace est ajouté sous le contenu pour qu'une tuile tout en bas puisse se tenir au-dessus de la barre.

## Hors de cette spec

- L'aperçu dessiné d'un Graphique sur sa tuile (`missing-tools.md`).
- Changer un outil de zone (`NOTES.md`).
