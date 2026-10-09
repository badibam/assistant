# Le parcours de l'utilisateur — conception

Conçu le 2026-10-08. Ce que voit une personne qui découvre l'app, du README à ses premiers outils, puis à l'IA et aux automations. Le public visé : une personne curieuse qui arrive par F-Droid ou GitHub, à l'aise avec un téléphone, pas développeuse, sans clé d'API. L'IA rend l'app fluide et fait partie du parcours, mais elle n'est pas demandée avant d'avoir quelque chose à lui confier.

## La promesse

Elle ouvre le README, la fiche F-Droid et l'écran d'accueil, dans cet ordre :

1. Suivez ce qui compte pour vous (sport, repas, tâches, humeur) avec des outils que vous assemblez vous-même, et une IA qui peut les lire, les remplir et les régler avec vous.
2. L'exemple : vos repas calculent vos calories, qu'un objectif juge et qu'un graphique dessine.

L'IA qui agit avec les mêmes gestes que l'utilisateur, ce qui distingue l'app, vient ensuite : en tête, elle ne parle qu'à qui a déjà compris le reste.

## Le README

- `README.md` en anglais, `README.fr.md` en français, chacun lié à l'autre en première ligne. Le français s'écrit d'abord, l'anglais en est la traduction.
- Plan, dans l'ordre des questions de qui découvre l'app :
  1. En-tête : icône, nom, la promesse et son exemple.
  2. Trois ou quatre captures côte à côte : l'accueil, une zone de la démo, un graphique, une conversation avec l'IA.
  3. Ce que vous pouvez faire, en quatre blocs courts : Noter (les types d'outils, une ligne chacun), Relier (variables, objectifs, graphiques), Avec une IA (elle lit, remplit, règle, demande votre accord, tourne sur un planning ; votre clé ; Claude branché de l'extérieur par le connecteur), À votre goût (zones, disposition, thèmes).
  4. Commencer : installer, puis la démo et le Guide au premier lancement.
  5. Vos données : tout reste sur le téléphone, rien ne part sauf vers le fournisseur d'IA choisi quand on lui parle ou qu'une automation tourne, aucune télémétrie, sauvegarde et export.
  6. État du projet : version, développement actif, Android minimal, langues.
  7. Pour les développeurs : un paragraphe, le lien vers `docs/reference.md`, `./run`.
  8. Licence : GPL-3.0.
- Sortent du README : les trois principes en tête (leur contenu passe dans 1 et 3) et la liste technique, que `docs/` porte déjà.
- Les captures vivent dans `fastlane/metadata/android/en-US/images/phoneScreenshots/`, là où F-Droid les attend : la fiche et le README partagent les mêmes images. Faites à la main ; des captures générées sans téléphone (Roborazzi) le jour où elles se périment trop vite.
- Blocs générés : la version (`versionName`), les types d'outils (nom et description d'une ligne, tirés de leurs textes dans chaque langue), les fournisseurs d'IA, les thèmes, les langues et l'Android minimal (`minSdk`). Un script (`scripts/make_readme.py`) les remplit entre des commentaires (`<!-- tools -->` … `<!-- /tools -->`) ; `./run test` vérifie qu'ils sont à jour et échoue sinon, sans jamais réécrire le README. Pas de badge.

## Le premier lancement

Après l'installation de la démo, un écran, une seule fois : l'icône et le nom, la promesse, une ligne sur la démo (« la vie de Camille sur douze semaines, pour voir à quoi ça ressemble une fois construit »), et deux boutons :

- « Commencer : Premiers pas » lance la bande ;
- « Explorer seul » mène à l'accueil, où l'icône du Guide porte son point, et un message bref dit où est le Guide.

Pas de diaporama, rien sur l'IA.

## Les mots

- Un **chapitre** du Guide : une introduction, des étapes, une conclusion. Un chapitre qui a des étapes à faire est un **tutoriel**.
- Une **étape** : un but, une explication, un écran cible, ce qu'elle attend.
- Le **chemin** : les gestes qui mènent à l'écran cible, depuis l'écran ouvert (bande) ou depuis l'accueil (Guide).

## Le départ d'un tutoriel

Un tutoriel qui commence à sa première étape (de l'écran d'accueil, d'une fin de tutoriel, d'un chapitre, de « Recommencer ») ouvre un dialogue : son titre, son introduction, et la bande montrée (« elle donne l'étape à faire, et se déplie d'un toucher pour l'expliquer »). « C'est parti » ; « Plus tard » masque le tutoriel et dit où il attend. Une reprise ne l'ouvre pas.

## La bande

Posée au-dessus de tous les écrans, en bas, à la place que tient `LongOperationBar` pour une opération longue.

- Repliée, deux lignes et une flèche : le titre du tutoriel ; « 2/6 · Ajoutez une sortie dans le Suivi ». La flèche dit qu'elle se déplie ; toute la bande se touche.
- Dépliée, en plus : l'explication de l'étape ; le chemin depuis l'écran ouvert (« D'ici : ouvrez la zone Course », ou « Vous êtes dans Cuisine : revenez à l'accueil ») ; « Masquer le tutoriel ».
- Pas de bouton qui emmène à l'écran : la personne fait les gestes elle-même. Un bouton introuvable à l'usage pourra clignoter, au cas par cas.
- Une étape faite se coche, la suivante prend sa place.

## Le chemin

Chaque sorte d'écran de la pile connaît son parent et la phrase qui y mène (une zone : l'accueil, « ouvrez la zone {nom} » ; un outil : sa zone, « touchez {nom} » ; les fournisseurs d'IA : les Réglages). Depuis l'écran ouvert, le chemin remonte à l'écran commun avec la cible, puis redescend, une phrase par écran. Dans le Guide, il part toujours de l'accueil (« Accueil › Démo › Course › Sorties › + »). Une même donnée pour les deux. Dans un écran, un bouton se nomme comme on le voit : « l'engrenage en haut à droite ».

## Les étapes

Trois sortes :

- **aller** : faite quand l'écran cible est en haut de la pile ;
- **faire** : faite quand l'opération attendue passe par le dispatcher, avec son origine (`currentOrigin()`) — `tool_data.create` sur un outil, d'origine utilisateur ; « demandez à l'IA de créer un outil » attend `tools.create` d'origine IA, et ne se coche pas si l'utilisateur le crée lui-même ;
- **lire** : une explication et « Suivant ».

Une étape attend une opération faite pendant qu'elle est en cours, pas un état déjà là. Elle peut :

- viser ce qu'une étape précédente a créé (l'entrée dans l'outil créé deux étapes plus tôt) ;
- demander que sa cible soit hors démo (une zone créée dans le groupe Démo disparaît à la mise à jour suivante).

Une étape s'écrit en données, pas en code. Un contrôle de `./run test` vérifie que chaque opération attendue existe au registre des services et chaque écran cible dans la pile.

## Le Guide

- Une page, ouverte par une icône fixe en haut de l'accueil (`book-open`). Elle se lit d'un seul défilement, en trois parties par degré (`GuidePart`) : « Découverte » et « Prise en main », dont les chapitres sont numérotés et forment le parcours, puis « Approfondissement ».
- Chaque chapitre a la même ligne dans les trois parties : icône, titre, durée, état (« à faire », « 3/6 · reprendre », « fait »). Ouvert, il se lit en entier (toutes ses étapes, chemins depuis l'accueil), avec « Faire en interactif » s'il a des étapes à faire et « Recommencer » s'il est fini.
- Rien n'est verrouillé : tout chapitre s'ouvre à tout moment.
- Tant qu'un tutoriel du parcours est proposé et pas fait, ou qu'un tutoriel est masqué en cours, l'icône du Guide porte le point de `WaitingMark`.

## Parcours

Découverte : 1 et 2 ; Prise en main : 3 et 4.

1. **Premiers pas**, sans IA.
2. **Brancher une IA** : choisir un fournisseur, coller sa clé, demander une vue d'ensemble sans rien joindre, puis désigner son outil par un pointeur en simple mention, chaque geste nommé (la cible, la zone, l'outil, Confirmer).
3. **Construire avec l'IA** : lui faire créer un outil, valider ce qu'elle propose, la laisser relier deux outils (une variable, un Objectif).
4. **Automatiser** : une automation planifiée (« chaque dimanche, le bilan de la semaine »), puis lire son exécution.

Le contenu des tutoriels 2 à 4 s'écrit à leur construction : il nomme des écrans que la pile n'a pas encore.

### Premiers pas

1. **Aller** : ouvrez le groupe Démo, puis la zone Course. L'explication présente Camille, sa zone, ses outils rangés par groupes.
2. **Faire** : ajoutez une sortie dans *Sorties* (`tool_data.create` sur `demo-course-runs`, d'origine utilisateur).
3. **Aller, puis lire** : ouvrez *Km par semaine* — votre sortie y est, et l'objectif *Semaine d'entraînement* l'a comptée. Les outils se nourrissent entre eux.
4. **Faire** : créez votre propre zone, hors démo : l'explication envoie à la section « Hors groupe », tout en bas de l'accueil, et à son +, le groupe Démo étant à Camille.
5. **Faire** : ajoutez-y un outil ; l'explication guide vers une Liste, où une entrée n'est qu'un nom à cocher, et dit que seul le nom est à remplir (le Suivi et ses raccourcis sont vus à l'étape 2, dans la démo ; sans raccourci, sa saisie intimide).
6. **Faire** : ajoutez-y une première entrée, dans l'outil de l'étape 5 : un élément de la Liste.

## Approfondissement

Un chapitre par sujet, lu dans l'ordre qu'on veut, tutoriel facultatif :

- Organiser : groupes de zones sur l'accueil, groupes d'outils dans une zone, disposition des tuiles — le même geste à deux niveaux, dans un seul chapitre.
- Les outils : un chapitre par type, fourni par le type d'outil dans son dossier (`tools/<type>/`), à côté de ses textes, et découvert comme l'outil l'est ; un nouvel outil arrive avec son chapitre.
- Relier : variables, lectures, pointeurs.
- Parler à l'IA : le pointeur, les questions et validations, le coût.
- Apparence : thèmes, palettes, couleurs des icônes.
- Vos données : sauvegarde et restauration, import, export.
- Accès externe : le connecteur.
- Quand ça coince : journaux, rapport de bug.

Leur contenu s'écrit à leur construction.

## L'enchaînement

- Tant que le parcours n'est pas fini, la fin de tout tutoriel (un chapitre d'Approfondissement fait en avance compris) ouvre un dialogue qui propose le tutoriel suivant du parcours : « Continuer : Brancher une IA » / « Plus tard ».
- Le parcours fini, la fin de chaque tutoriel propose « Ouvrir le Guide », qui ouvre la page au titre Approfondissement, ou « Plus tard ».
- « Plus tard » dit où est la suite : « dans le Guide, le livre en haut de l'accueil ».

## La démo

- Au lancement d'un tutoriel qui en a besoin, si elle est absente : la réinstaller en un bouton, ou sauter ses étapes. Pas de version sans démo de ces étapes.
- Le dialogue qui la supprime nomme les tutoriels qui l'utilisent, dit que leurs étapes dans la démo seront sautées, et qu'elle se réinstalle depuis Réglages › Démo.
- Dans le Guide, tant qu'elle est absente, un chapitre qui en a besoin porte « Demande la démo » et « Réinstaller ».
- Un chapitre a besoin de la démo si une de ses étapes vise un écran ou un outil de la démo : déduit, jamais déclaré.

## La progression

Dans les réglages de l'app : pour chaque chapitre, l'étape en cours ou « fait » ; la bande masquée ou non. Elle survit aux mises à jour et à la réinstallation de la démo, part avec la sauvegarde, disparaît à la remise à zéro (ce qui relance Premiers pas). « Recommencer » par chapitre, pas de remise à zéro globale.

## Où ça vit

- Les chapitres : `assets/guide/chapters.json`, une étape par objet (sorte, adresse du lieu, opération attendue avec son origine et ses paramètres, ce qu'elle garde). « Hors démo » n'a pas de forme à lui : une étape vise la zone créée à l'étape d'avant (`{zone}`), jamais une zone de la démo. Un chapitre par type d'outil s'ajoute après « Organiser », tiré du nom et de la `tagline` de l'outil.
- Les textes : `core/strings/sources/guide.xml` et `guide-fr.xml`, nommés d'après le chapitre et l'étape.
- Le moteur : `core/guide/Guide.kt`, qui écoute les opérations réussies demandées de l'extérieur (`PassedOperations`, émises par le coordinateur) et la pile ; la progression dans la catégorie de réglages `guide`.
- Le contrôle : `scripts/check_guide.py`, dans `./run test`.

## Ce qui reste ouvert

- Le contenu des chapitres d'Approfondissement : des ébauches (titre et une ligne), à écrire ensemble dans une séance dédiée ; un chapitre sans étape s'affiche « À écrire ».
- Ce qui marque « fait » un chapitre sans étape à faire (ouvert, ou lu jusqu'au bout).
- Un message envoyé à l'IA ne passe pas par le dispatcher (`AIOrchestrator.sendMessage` le range lui-même) : l'étape « demandez-lui son avis » de Brancher une IA se lit, au lieu d'attendre l'envoi.
- Le dialogue « la démo n'est pas installée » : le fermer sans choisir (retour, toucher à côté) saute les étapes de la démo, comme son bouton.
