# Le thème cosy

Conçu à partir du 2026-10-06. Un troisième thème, à côté du défaut et du rétro : l'app comme un téléphone dans un jeu doux (Animal Crossing, Stardew Valley). Tout est rond et dodu, et c'est le seul thème dont le mouvement est un ressort : là où le rétro saute d'une cellule, le cosy s'écrase et rebondit.

Maquettes (écran d'une zone, à côté des autres pistes de thème écartées) : https://claude.ai/artifact/XjCGGf19j1ZFGuDmhF2GbZ, planche « Cosy ». Lien privé, ouvert par son propriétaire ; Claude le relit avec l'outil Artifact (`read`).

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

## Ce qui reste ouvert

- Le mouvement : l'écrasement au toucher, le rebond au lâcher, l'arrivée d'une fenêtre ; leurs durées.
- Les sons : quelle source libre, pour quels signaux.
- Le cran de taille (0 à 3) : ce qu'il agrandit dans ce thème.
