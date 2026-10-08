# Le thème cosy

Conçu à partir du 2026-10-06. Un troisième thème, à côté du défaut et du rétro : l'app comme un téléphone dans un jeu doux (Animal Crossing, Stardew Valley). Tout est rond et dodu, et c'est le seul thème dont le mouvement est un ressort : là où le rétro saute d'une cellule, le cosy s'écrase et rebondit.

Maquettes (écran d'une zone, à côté des autres pistes de thème écartées) : `docs/theme-tracks.html`, carte « Cosy ».

## Le registre, d'après la maquette

- Un fond sable semé de petits points ; des tuiles crème très arrondies, posées sur une ombre pleine et épaisse sous elles, jamais floue.
- Le titre d'un écran dans une pastille pleine de la couleur principale (vert feuille), posée sur sa propre ombre ; les boutons ronds, sur leur ombre aussi.
- Un titre de groupe sur le fond, suivi d'un trait en pointillés arrondis.
- Les tags en pilules pastel ; une case cochée est un rond plein marqué d'une coche, une case vide un rond creux ; la jauge est une pilule rayée.
- Une icône seule sur sa tuile est posée dans un rond de couleur pâle.
- La marque « en attente » est une bulle orange avec un « ! », sur sa propre ombre.
- Une police ronde et grasse (Baloo 2 dans la maquette).

## Ce qui est décidé

- **Le thème dessine tout lui-même**, comme le rétro : chaque composant du contrat, dialogues et sélecteurs de date et d'heure compris, rien repris du thème par défaut ni de Material (2026-10-06).
- **La police est Baloo 2, seule, pour tout le thème** (SIL Open Font License, embarquée dans l'app), du 400 du texte courant au 800 des titres et des chiffres (2026-10-06).
- **Deux palettes, à la méthode du rétro** : la claire de la maquette (fond sable, tuiles crème, vert feuille) et une nuit d'été (fond bleu nuit profond, tuiles bleu ardoise sur leur ombre plus sombre, encre crème, le vert, les pastels et l'orange éclaircis comme des lampions) ; pas de brun, qui est déjà la nuit du rétro. Chacune est une poignée de nombres en OKLCH dont tout dérive, que le réglage « Teinte » tourne, états et tags gardés, et qui se règlent au banc (`./run themes`) (2026-10-06).
- **Le mouvement est un ressort** (2026-10-06). Au toucher, un élément s'enfonce dans son ombre, qui disparaît sous lui ; au lâcher, il remonte sur un ressort qui dépasse un peu sa place puis s'y pose, en un quart de seconde environ. Une fenêtre (dialogue, chat) arrive un peu plus petite et en fondu, puis gonfle jusqu'à sa taille avec le même dépassement ; elle se ferme d'un coup. Une tuile soulevée en mode d'édition grossit un peu, penche de quelques degrés, son ombre s'allonge ; posée, elle rebondit à sa place. Les autres tuiles s'effacent à demi pendant ce temps, laissant voir les cases dessous (`GridTile`). Un bloc qui se déplie glisse, sans rebond. Les animations coupées dans Android, tout arrive à sa place d'un coup.
- **Les sons viennent de Kenney** (CC0, crédités dans `sounds.json` comme ceux du rétro), choisis à l'oreille sur une planche d'écoute de tout Kenney (2026-10-06) : confirmer `drop_004`, entrer `select_008`, revenir `select_007`, ouvrir `maximize_001`, fermer `minimize_001`, cocher `select_001`, le cran `tick_001`, le bout d'une liste `bong_001`, le refus `error_006`, tous d'Interface Sounds. Leur niveau : 13 dB sous les originaux de Kenney, 3 sous le rétro, à ajuster à l'oreille sur le téléphone. Les familles retenues pour une retouche : back, bong, click, drop, error, maximize, minimize, pluck, select, switch, tick et toggle d'Interface Sounds ; click, rollover et switch d'UI Audio.
- **La taille** (2026-10-06) : la taille de texte d'Android est suivie, pour le texte seul ; le cran (0 à 3) agrandit tout ensemble, texte, marges, arrondis, ombres et icônes, par ×1, ×1,12, ×1,25 et ×1,4, le cran 0 étant la maquette.

## Ce qui est codé

`themes/cosy/` : la palette (`CosyPalette`, ses nombres dans `palettes.json`), les tailles et la police par cran (`CosySize`), la pièce posée sur son ombre, l'enfoncement et le ressort, la fenêtre qui gonfle, l'élément soulevé (`CosyPieces`), chaque composant du contrat (`CosyTheme`), les sons (`res/raw/cosy_*.flac`, crédits dans `sounds.json`). La police vient de `third_party/baloo2/` par `scripts/make_baloo_fonts.py`. `CosyPaletteTest` tient les contrastes des deux palettes. Choisi dans Réglages › Interface, sous le nom « Cosy ».

## Ce qui reste ouvert

- Rien n'a tourné sur un téléphone : le thème entier s'y juge, le ressort et le niveau des sons compris, et ce que deviennent les ressorts quand les animations sont coupées dans Android (Compose est censé les sauter, non vérifié).
- Le banc des palettes (`./run themes`) n'a pas encore le cosy : ses nombres ont été lus sur la maquette et la nuit d'été est posée sans l'avoir vue.
- Le Graphique passe par le dessin du thème par défaut (`DefaultDrawing`), ses couleurs comprises, dans la police du cosy : à dessiner dans le registre, comme le rétro a fait le sien (`RetroDrawing`).
- Un bloc qui se déplie n'est pas au contrat des thèmes : son glissement reste celui du cœur.
- Baloo 2 n'a pas l'espace fine insécable (U+202F) que le français met devant `; : ! ?` : Android la prend dans une police système, comme tout caractère qu'elle n'a pas.
