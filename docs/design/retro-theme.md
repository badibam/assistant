# Le thème rétro

Conçu à partir du 2026-09-29. Un second thème, à côté du thème par défaut, dans le registre de Saylune (`/mnt/data/OUTILS/saylune/docs/ui.md`) : la console en doux, pixel art de sous-écran de Zelda, cadres épais à deux tons, police carrée et dense. Ce qu'il reprend de Saylune y est lu et non recopié ; ce qui vaut pour toute app rétro deviendra une norme de sagesse.

## Ce qui est décidé

- **Le registre de Saylune, avec une palette à l'assistant.** Le prune de Saylune vient de ses mesures, qui n'existent pas ici.
- **Le thème donne les espacements.** Un petit jeu de valeurs nommées (`UI.Space`) remplace les dp écrits en dur dans les écrans du cœur et des outils (492 au 2026-09-29, dans 66 fichiers) : le thème par défaut les traduit en dp comme aujourd'hui, le thème rétro en cellules entières. Migration quand la session des briques ne touche plus ces écrans.
- **Le thème donne la taille d'une case de tuile** (`grid-layout.md`), et non plus le cœur. Thème par défaut : un quart de la largeur. Thème rétro : le plus grand nombre pair de cellules qui tient dans un quart de la largeur, la grille centrée, le reste en marge. Pair, parce qu'une ligne est une demi-case et qu'une cellule fait onze pixels de dessin : une case impaire mettrait les lignes entre deux pixels.
