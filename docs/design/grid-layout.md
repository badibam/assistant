# La grille des outils d'une zone

Conçue le 2026-09-29. Aujourd'hui, l'écran d'une zone dessine chaque outil en tuile LINE, l'un sous l'autre, quel que soit le mode d'affichage réglé. Cette spec dit comment les tuiles se placent selon leur mode, et comment l'utilisateur les déplace.

## La grille

- **Une grille par groupe.** Les sections de groupe restent ; chacune range ses outils dans sa grille. La carte des variables et les cartes d'automation restent sous la grille, en pleine largeur.
- **Quatre colonnes, des cases carrées** d'un quart de la largeur. Le thème fixe une largeur maximale de la grille (par exemple 480 dp), centrée au-delà : un grand écran qui ignore le verrou en portrait (`TODO.md`) garde le même affichage, jamais un de plus de quatre colonnes.
- **La taille d'une tuile vient de son mode**, en cases (largeur × hauteur) : ICON 1×1, MINIMAL 2×1, LINE 4×1, CONDENSED 2×2, EXTENDED 4×2, SQUARE 4×4, FULL 4 × la hauteur de son contenu. Le mode reste un réglage de l'outil, dans sa config ; le mode d'édition ne le change pas.
- **Une tuile de pleine largeur est seule sur ses lignes** : seules ICON, MINIMAL et CONDENSED partagent une ligne. Une FULL compte donc comme une ligne à elle seule, quelle que soit sa hauteur.
- **Les tailles fixes du thème disparaissent** (`ToolCardContainer`, 64 dp pour ICON, 256 dp pour SQUARE…) : la grille donne sa taille à chaque tuile.

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

- Ce que chaque type d'outil montre dans chaque mode (`TileContent`) : la Liste les remplit tous ; l'Objectif, le Questionnaire et les Données structurées n'ont que LINE ; ICON affiche encore un « T » provisoire (`UI.ToolCard`). Les modes de Messages : `messages-display-modes.md`.
- L'aperçu dessiné d'un Graphique sur sa tuile (`missing-tools.md`).
- Changer un outil de zone (`NOTES.md`).
