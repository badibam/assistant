# Historique des écritures et annulation

Chaque écriture dans la base est gardée dans un historique, pour pouvoir l'annuler pas à pas, sur toute l'app ou dans un seul outil (ou une zone). Conçu le 2026-10-05.

## Ce qui est couvert

- Couvert : zones, outils (leur config), entrées, variables, automations, réglages de l'app.
- Hors historique : sessions et messages de l'IA (une conversation a eu lieu et a été facturée), configs des fournisseurs (la clé d'API ne se recopie pas), journal de l'app, fichiers joints.
- L'historique ne revient que sur la base : une notification envoyée, un appel à l'IA facturé restent faits.

## Le pas

- Un pas = une opération demandée de l'extérieur : un geste à l'écran, une commande de l'IA, une action du planificateur. Il regroupe toutes les lignes qu'elle écrit, y compris par les opérations qu'elle appelle en chemin.
- Une commande de l'IA est un pas à elle seule ; le pas garde l'id de sa session, pour les montrer regroupés par réponse.
- Un pas s'annule entier ou pas du tout.
- Le numéro du pas voyage dans le contexte de la coroutine, comme l'origine (`Origin`, `currentOrigin()`) : le nombre d'instances du coordinateur qui tournent en même temps n'y change rien. Une écriture hors de toute opération est une erreur.
- Un pas garde : son origine, son heure, l'id de session s'il vient de l'IA, sa phrase, son état (fait / annulé, avec qui et quand), et pour chaque ligne touchée : la table, l'id, l'outil et la zone concernés, la ligne entière avant (vide pour une création) et après (vide pour une suppression).
- La phrase est écrite au moment du pas par le `verbalize()` du service, et figée : recalculée à l'affichage, elle mentirait après un renommage et ne dirait rien d'une chose supprimée.

## Où il est écrit

- Toute écriture d'une table couverte passe par une seule classe, qui lit la ligne d'avant, écrit, puis écrit l'historique, dans la même transaction.
- Un script de contrôle lancé par `./run test` refuse tout appel d'écriture de ces tables ailleurs. Les écritures qui contournent aujourd'hui un service (`GoalService`, `DefaultExtendedToolDataDao`…) y passent.
- Les suppressions en cascade se font dans SQLite (`onDelete = CASCADE` sur outils, entrées, variables) : avant une suppression, la classe lit elle-même les lignes qui partent avec, sinon elles ne seraient pas restaurables. Une restauration remet les parents avant les enfants.
- Pas de déclencheurs SQLite : ils ne savent pas à quel pas appartient une écriture sans une transaction ouverte pendant toute l'opération, qui bloquerait les autres écritures pendant un appel de l'IA.

## Migrations

- L'historique est toujours à la version courante de la base : chaque migration le transforme avec les mêmes transformations que les données et l'import d'une sauvegarde.

## Profondeur

- On garde les pas des 30 derniers jours, réglable dans les réglages de l'app. Le tick de `CoreScheduler` supprime les plus vieux.
- Pas de limite de taille : un gros import (estimé, non mesuré : 65 à 130 Mo d'historique pour les 64 659 lignes du 2026-10-01) reste annulable 30 jours. L'écran montre la place prise.

## Annuler et rétablir

- Annuler : le pas le plus récent encore fait. Rétablir : le pas le plus récemment annulé. Dans un outil ou une zone, on ne regarde que les pas qui les touchent.
- Une annulation n'est pas un pas : elle change l'état du pas annulé.
- Avant d'annuler ou de rétablir, chaque ligne touchée doit être encore dans l'état où le pas l'a laissée (sa copie « après » pour annuler, « avant » pour rétablir). Sinon : refus, en nommant le pas qui l'a changée depuis. Jamais de fusion ni d'écrasement. Conséquence : après une nouvelle écriture, un pas annulé qu'elle touche ne se rétablit plus ; il reste visible, annulé.
- Une annulation a les droits du geste qu'elle équivaut à faire : celle qui supprime une ligne passe par le même contrôle « qui dépend de ça ? » qu'une suppression par le service. Ce contrôle est écrit une fois, appelé par les deux ; il n'existe pas encore (le refus conçu dans `docs/design/typed-conditions.md`), et s'appliquera aux deux le jour où il sera codé.
- Elle ne repasse pas par la validation par schéma : la ligne remise était valide à son écriture, et les migrations l'ont tenue à jour.

## Le service `history`

- `history.list` (filtre par outil, zone, période ; origine, heure, phrase, état), `history.undo` et `history.redo` (un pas par son id). L'écran et l'IA appellent les mêmes opérations ; `history.list` est documenté dans le prompt.
- Pas de règle à part pour l'IA qui annule un pas de l'utilisateur : elle peut déjà écrire les mêmes lignes ; c'est la validation des actions de l'IA qui en décide.

## Sauvegardes

- L'export n'emporte pas l'historique.
- La restauration vide l'historique, et sa confirmation le dit ; l'écran de restauration propose d'exporter avant.
- La démo et l'import sont des pas ordinaires.

## Écran

- Un écran Historique, ouvert depuis les Réglages (toute l'app) et depuis le menu d'un outil ou d'une zone (filtré).
- Les pas du plus récent au plus ancien, avec leur état ; Annuler et Rétablir en haut ; toucher un pas déplie ses lignes avant et après.
- Pas de bandeau « annuler » après chaque geste pour l'instant — à l'usage, s'il manque.
