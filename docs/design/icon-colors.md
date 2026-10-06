# Les couleurs des icônes — conception

Conçu et codé le 2026-10-06, rien n'a tourné sur un téléphone (`device-checks.md`). Une zone et un outil peuvent donner une couleur à leur icône, pour distinguer les outils d'une zone et les zones de l'accueil d'un coup d'œil.

## La couleur

- Une couleur est un **nom de teinte**, jamais un code : `RED`, `ORANGE`, `YELLOW`, `GREEN`, `TEAL`, `BLUE`, `PURPLE`, `PINK` — les noms de `TagColor`, sans `GREY`, qui à l'écran ne se distinguerait pas d'une icône sans couleur. Le thème résout le nom, dans ses deux modes ; le décalage de teinte ne la tourne pas, comme celle d'un tag.
- `icon_color` est absent ou `null` : l'icône est **neutre**, à l'encre du texte comme aujourd'hui. C'est un état normal, pas un repli ; aucune donnée existante ne change.
- Un outil sans couleur ne prend pas celle de sa zone : la couleur sert à distinguer les outils d'une zone, que l'héritage peindrait tous pareil.

## Où elle vit

- **Zone** : une colonne `icon_color` de `Zone`, à côté de `icon_name` (migration 62 → 63, l'import d'une sauvegarde la lisant absente comme `null`) ; un champ de `ZoneSettings`.
- **Outil** : une clé `icon_color` de sa config, à côté de `icon_name`, dans le schéma de config commun à tous les types : une liste fermée des huit noms. `null` ou vide, la clé est retirée.
- Les noms, leur lecture et leur refus sont dans `IconColor`.

## Comment chaque thème la dessine

Le thème dessine l'icône d'une chose, sa couleur avec (`ThemeContract.ItemIcon`) : le cœur lui passe l'icône et la `TagColor` ou `null`. L'en-tête d'une page (`PageHeader`) reçoit la couleur de la même façon.

- **Par défaut : une pastille teintée.** L'icône sur une pastille arrondie de la couleur du tag (`getTagColor`), dans un ton profond de la même teinte ; en sombre, une pastille sombre de la teinte et l'icône à la couleur du tag. Une icône neutre a aussi sa pastille, grise (`surfaceVariant`), pour que les titres des tuiles commencent tous au même endroit : toutes les tuiles changent donc un peu, couleur posée ou non.
- **Rétro : l'icône en pixels colorés**, à la teinte du tag, à la clarté et à la saturation des états de la surface où elle est dessinée (`RetroColors.icon`) : celle d'un cadre sur une tuile, celle de l'écran dans l'en-tête d'une page. Sans pastille : une pastille ferait un cadre dans le cadre.

Les tons de la pastille du thème par défaut (`itemIconDeepTone`) sont montrés sur son banc (`./run themes`) et tenus à ceux du thème par `DefaultColorsBenchTest`.

La couleur s'affiche sur les tuiles (`TileHeader`, `TileIcon`) et dans l'en-tête de l'écran d'une zone ou d'un outil. Pas sur une notification, qui garde le dessin Lucide sans couleur (`docs/reference.md`, « Icônes »).

## Le choix

- **À l'écran** : sous `IconSelector`, dans la config d'une zone et dans celle d'un outil, `IconColorSelector` offre neuf choix sur deux rangées — « aucune » puis les huit couleurs, chacun montrant l'icône de la chose comme le thème la dessine dans cette couleur, le choix en cours marqué. Un toucher change la couleur, et l'icône à côté de son sélecteur se dessine aussitôt dans la nouvelle. L'icône et sa couleur restent deux choix séparés.
- **Par l'IA** : CREATE_ZONE et UPDATE_ZONE prennent `icon_color` ; un outil la reçoit dans sa config. `null` retire la couleur. Un nom inconnu est refusé par le service, qui donne la liste des noms valides. L'aperçu de l'app n'en dit rien : la couleur ne change rien à ce que l'IA doit savoir pour agir.

## Tests

- `IconColorTest` : les huit noms dans leur ordre, `GREY`, un nom inconnu ou en minuscules refusés, « aucune » acceptée.
- `DefaultColorsBenchTest` : les pastilles du banc sont celles du thème.
- Le refus par les services, la migration et l'import passent par la recette sur l'appareil.
