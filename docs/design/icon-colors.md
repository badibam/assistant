# Les couleurs des icônes — conception

Conçu le 2026-10-06. Une zone et un outil peuvent donner une couleur à leur icône, pour distinguer les outils d'une zone et les zones de l'accueil d'un coup d'œil.

## La couleur

- Une couleur est un **nom de teinte**, jamais un code : `RED`, `ORANGE`, `YELLOW`, `GREEN`, `TEAL`, `BLUE`, `PURPLE`, `PINK` — les noms de `TagColor`, sans `GREY`, qui à l'écran ne se distinguerait pas d'une icône sans couleur. Le thème résout le nom, dans ses deux modes ; le décalage de teinte ne la tourne pas, comme celle d'un tag.
- `icon_color` est absent ou `null` : l'icône est **neutre**, à l'encre du texte comme aujourd'hui. C'est un état normal, pas un repli ; aucune donnée existante ne change.
- Un outil sans couleur ne prend pas celle de sa zone : la couleur sert à distinguer les outils d'une zone, que l'héritage peindrait tous pareil.

## Où elle vit

- **Zone** : une colonne `icon_color` de `Zone`, à côté de `icon_name` (migration 62 → 63, l'import d'une sauvegarde la lisant absente comme `null`) ; un champ de `ZoneSettings`.
- **Outil** : une clé `icon_color` de sa config, à côté de `icon_name`, dans le schéma de config commun à tous les types : une liste fermée des huit noms.

## Comment chaque thème la dessine

Le thème dessine l'icône d'une chose, sa couleur avec : le cœur lui passe le nom de l'icône et la `TagColor` ou `null`.

- **Par défaut : une pastille teintée.** L'icône sur une pastille arrondie de la couleur du tag (`getTagColor`), dans un ton profond de la même teinte ; en sombre, une pastille sombre de la teinte et l'icône à la couleur du tag. Une icône neutre a aussi sa pastille, grise (`surfaceVariant`), pour que les titres des tuiles commencent tous au même endroit : toutes les tuiles changent donc un peu, couleur posée ou non.
- **Rétro : l'icône en pixels colorés**, à la couleur de la teinte dans un cadre (`drawing_<teinte>` de la palette), sans pastille : une pastille ferait un cadre dans le cadre.

Les tons de la pastille du thème par défaut se règlent sur son banc (`./run themes`), comme ses autres couleurs.

La couleur s'affiche partout où l'icône représente sa zone ou son outil : les tuiles (`TileHeader`, `TileIcon`), l'en-tête de l'écran d'une zone ou d'un outil, les sélecteurs qui les listent. Pas sur une notification, qui garde le dessin Lucide sans couleur (`docs/reference.md`, « Icônes »).

## Le choix

- **À l'écran** : `IconSelector`, commun à la config d'une zone et à celle d'un outil, ajoute sous l'icône une rangée de neuf pastilles — « aucune » puis les huit couleurs, sur deux rangées si la largeur manque. Un toucher change la couleur, et l'icône de la ligne se dessine aussitôt comme sur la tuile. L'icône et sa couleur restent deux choix séparés.
- **Par l'IA** : CREATE_ZONE et UPDATE_ZONE prennent `icon_color` ; un outil la reçoit dans sa config. `null` retire la couleur. Un nom inconnu est refusé par le service, qui donne la liste des noms valides. L'aperçu de l'app n'en dit rien : la couleur ne change rien à ce que l'IA doit savoir pour agir.

## Tests

- Un nom inconnu refusé, avec les noms valides, par `zones` et par `tools` ; `GREY` refusé ; `null` qui retire la couleur.
- La migration 62 → 63 et l'import d'une sauvegarde sans `icon_color` : les zones gardent une icône neutre.
