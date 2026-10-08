# La navigation en pile — conception

Conçu le 2026-10-08, en partie. Aujourd'hui, ce qui est affiché découle d'une vingtaine d'interrupteurs indépendants dans `MainScreen.kt` (`showSettings`, `selectedZoneId`, `showAIChat`…) et d'autres dans les écrans qu'il ouvre : 19 fichiers tiennent un morceau de la navigation, et l'app n'a pas de notion d'écran ouvert. Ce chantier passe avant le parcours de l'utilisateur (`docs/design/user-journey.md`), dont la bande et le Guide en ont besoin.

## La pile

Une seule liste d'écrans, l'accueil en bas, l'écran visible en haut (Accueil › zone Course › outil Sorties › config). Ouvrir un écran l'ajoute, revenir le retire. Chaque sorte d'écran connaît son parent et la phrase qui y mène, pour le chemin du Guide.

## Ce qu'elle apporte

- L'écran ouvert, que lit la bande d'un tutoriel.
- Un fil d'Ariane, pour tous et pas seulement pendant un tutoriel.
- N'importe quel écran ouvert de l'extérieur : une notification (seul l'outil l'est aujourd'hui, `openToolId`), un pointeur touché dans un message, l'IA qui montre quelque chose.
- Un retour arrière réglé à un endroit : il l'est aujourd'hui par 10 gestionnaires dans 6 fichiers.
- Pas de combinaison impossible de deux écrans qui se croient ouverts.
- La navigation testable sans téléphone : la pile est une liste, les chemins des tutoriels se vérifient dans `./run test`.

## Les sons

Les sons d'aller et retour se jouent dans la pile, plus dans les gestes. Aujourd'hui ENTER ne se joue qu'à quatre endroits (carte de zone, carte générique `core/ui/UI.kt:737`, entrées des Réglages, boutons configurer et voir), BACK seulement dans un des 10 gestionnaires du retour (`core/ui/UI.kt:695`), et le bouton de conversation joue OPEN pour un écran entier : un même écran sonne différemment selon le chemin, ou pas du tout.

- Un écran ajouté joue ENTER, un écran retiré BACK ; un écran posé par-dessus le précédent joue OPEN et CLOSE.
- Les gestes de navigation ne jouent plus rien (`ButtonAction.signal()` perd BACK, CANCEL, CONFIGURE, VIEW, AI_CHAT), sinon deux sons se suivent.
- CONFIRM, TOGGLE, STEP, SCROLL_END et REFUSE restent aux gestes.

## Ce qui reste ouvert

- Quels écrans se posent par-dessus le précédent et lesquels le remplacent ; si un dialogue est dans la pile.
- La forme du fil d'Ariane et sa place.
- Ce que l'ouverture depuis l'extérieur accepte (quels écrans, quels paramètres), et qui peut la demander.
- La survie de la pile à la mort du processus (les interrupteurs sont aujourd'hui `rememberSaveable`).
- L'ordre de la migration, écran par écran, et sa recette sur le téléphone.
