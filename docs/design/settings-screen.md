# L'écran Réglages — conception

Conçu le 2026-10-06. Les réglages de l'app s'ouvrent aujourd'hui dans une fenêtre (`SettingsDialog`) : dix entrées, chacune un titre et une description, qui défilent sans repère. Ils deviennent un écran, rangé en sections de tuiles.

## L'entrée

- Le bouton de gauche de l'accueil ouvre l'écran Réglages, en plein écran, avec un retour. Il prend l'icône `sliders-horizontal` : il portait celle de « configurer » (`settings`), la même que le bouton de droite.
- Le bouton de droite ne change pas : il configure l'écran où il est, comme sur chaque écran (ici, les groupes de zones de l'accueil).

## L'écran

Quatre sections titrées ; dans chacune, des tuiles sur la grille de l'app (`GridLayout`), dessinées par le thème : une icône et un nom court.

| Section | Tuiles |
|---|---|
| App | Interface, Format et dates |
| IA | Fournisseurs, Limites, Validation |
| Données | Gestion des données, Accès externe |
| Système | Démo, Journaux |

- La Démo passe dans Système ; l'Accès externe dans Données : comme la sauvegarde et l'import, il fait entrer et sortir les données de l'app.
- La description d'une entrée sort de la tuile et va en haut de l'écran qu'elle ouvre.
- `SettingsDialog` est supprimé.

## L'historique des discussions

L'historique n'est pas un réglage : il sort de l'écran Réglages et s'ouvre depuis le chat, par une icône `rotate-ccw-clock` dans son en-tête, avant Arrêter. Il s'ouvre par-dessus le chat ; reprendre une discussion l'ouvre dans le chat, le quitter ramène au chat.

## Tests

Rien que la suite puisse vérifier : c'est de l'écran. La recette sur l'appareil, dans les deux thèmes : chaque tuile ouvre son écran et le retour ramène aux Réglages ; l'historique s'ouvre depuis le chat, une discussion reprise s'y ouvre.
