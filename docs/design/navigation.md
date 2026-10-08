# La navigation en pile — conception

Conçu le 2026-10-08, en partie. Aujourd'hui, ce qui est affiché découle d'une vingtaine d'interrupteurs indépendants dans `MainScreen.kt` (`showSettings`, `selectedZoneId`, `showAIChat`…) et d'autres dans les écrans qu'il ouvre : 19 fichiers tiennent un morceau de la navigation, et l'app n'a pas de notion d'écran ouvert. Ce chantier passe avant le parcours de l'utilisateur (`docs/design/user-journey.md`), dont la bande et le Guide en ont besoin.

## La pile

Une seule liste d'écrans, l'accueil en bas, l'écran visible en haut (Accueil › zone Course › outil Sorties › config). Ouvrir un écran l'ajoute, revenir le retire. Chaque sorte d'écran connaît son parent et la phrase qui y mène, pour le chemin du Guide.

## Lieux et fenêtres

- Entre dans la pile un **lieu** : un écran où l'on peut vouloir envoyer quelqu'un (tutoriel, notification, IA) ou revenir. L'accueil, une zone, un outil, la config d'une zone ou d'un outil, chaque page des Réglages, une session d'IA, une automation et ses exécutions, la conversation.
- Reste hors de la pile une **fenêtre** de passage : confirmation, saisie d'une entrée, sélecteur de date, image agrandie. Elle appartient à l'écran qui l'ouvre et joue déjà son son à son apparition (`UI.Dialog`, `ConfirmDialog`, `FullScreenDialog`). Une étape de tutoriel n'en a pas besoin : elle attend l'opération, sa cible reste l'écran.
- Le fil d'Ariane est la pile : il montre par où l'on est passé, et le retour en retire le dernier lieu.

## Le fil d'Ariane

- Une petite ligne au-dessus du titre : les lieux d'avant (« … › Course › Sorties »), le titre restant le lieu actuel. Absente sur l'accueil.
- Une seule ligne, coupée par la gauche ; le titre aussi coupé à une ligne : l'entête garde une hauteur fixe, et ses boutons leur place.
- Chaque nom se touche et ramène à ce lieu, en retirant tout ce qui est au-dessus.
- Un lieu empilé sans son parent juste en dessous porte le nom du parent après le sien : « … › Conversation › Sorties (Course) ».
- Dessinée par le thème, comme le reste de l'entête (`ThemeContract`).

## L'ouverture d'un lieu

- Chaque lieu a une adresse (`zone/<id>`, `tool/<id>`, `settings/ai_providers`…), une seule forme pour les notifications, les pointeurs, le Guide et les tests.
- Rien d'ouvert (une notification, app fermée) : la pile se reconstruit par les parents, « Accueil › Course › Sorties », et le retour remonte la hiérarchie au lieu de quitter l'app.
- Quelque chose d'ouvert (un pointeur touché dans la conversation) : la cible s'empile seule au-dessus, et un seul retour ramène à la conversation.
- Ouvrent une adresse : les notifications, qui remplacent `openToolId` ; l'utilisateur par un pointeur touché. Ni l'IA, qui changerait l'écran pendant qu'on lit sa réponse — elle montre par un pointeur que l'utilisateur touche —, ni un client extérieur par le connecteur.

## La conversation

- Un lieu posé par-dessus celui d'où on l'ouvre : « Accueil › Course › Sorties › Conversation », et la fermer ramène à Sorties. Seule elle flotte ainsi ; tout autre lieu remplace le précédent.
- Son bouton est sur tous les écrans, plus seulement l'accueil.
- Une conversation demandée depuis un écran (`ChatRequests`) ne ferme plus la zone ouverte (`selectedZoneId = null` dans `MainScreen`) : elle s'empile au-dessus.
- Elle reste unique et commune à l'app, et montre toujours une automation en cours.

## Ce qu'elle apporte

- L'écran ouvert, que lit la bande d'un tutoriel.
- Un fil d'Ariane, pour tous et pas seulement pendant un tutoriel.
- N'importe quel écran ouvert de l'extérieur : une notification (seul l'outil l'est aujourd'hui, `openToolId`), un pointeur touché dans un message, l'IA qui montre quelque chose.
- Un retour arrière réglé à un endroit : il l'est aujourd'hui par 10 gestionnaires dans 6 fichiers.
- Pas de combinaison impossible de deux écrans qui se croient ouverts.
- La navigation testable sans téléphone : la pile est une liste, les chemins des tutoriels se vérifient dans `./run test`.

## Les sons

Les sons d'aller et retour se jouent dans la pile, plus dans les gestes. Aujourd'hui ENTER ne se joue qu'à quatre endroits (carte de zone, carte générique `core/ui/UI.kt:737`, entrées des Réglages, boutons configurer et voir), BACK seulement dans un des 10 gestionnaires du retour (`core/ui/UI.kt:695`), et le bouton de conversation joue OPEN pour un écran entier : un même écran sonne différemment selon le chemin, ou pas du tout.

- Un lieu ajouté joue ENTER, un lieu retiré BACK ; la conversation, posée par-dessus, joue OPEN et CLOSE.
- Les gestes de navigation ne jouent plus rien (`ButtonAction.signal()` perd BACK, CANCEL, CONFIGURE, VIEW, AI_CHAT), sinon deux sons se suivent.
- CONFIRM, TOGGLE, STEP, SCROLL_END et REFUSE restent aux gestes.

## Ce qui reste ouvert

- La survie de la pile à la mort du processus (les interrupteurs sont aujourd'hui `rememberSaveable`).
- L'ordre de la migration, écran par écran, et sa recette sur le téléphone.
