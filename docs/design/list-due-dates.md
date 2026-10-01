# Échéances dans la Liste

Une option de la Liste, `due_dates`, désactivée par défaut : chaque élément peut alors porter une échéance, notifiée à son heure, et un élément en retard attend qu'on le coche. Désactivée, la Liste est celle d'avant.

## Le partage entre outils

- Un rythme fixe (arroser, l'engrais du 1er, le point du lundi) : Messages, un flux et son planning.
- Une chose à faire une fois, avant un moment : la Liste avec ses échéances.
- La trace de ce qui a été fait : un suivi « occurrence ».

La Liste reste « l'état présent, sans historique » : une échéance est un attribut de ce qui reste à faire. Aucune répétition par élément.

## Config

- `due_dates` (oui/non, non par défaut). L'activer déclare le champ d'échéance des éléments ; la désactiver le retire, et ses valeurs avec, sous la confirmation de migration ordinaire d'une config qui retire un champ.

## Un élément

- `data.due_at` : l'échéance, un instant facultatif ; déclaré seulement quand `due_dates` est activé.
- `state.due_notified` : l'échéance dont la notification est partie, écrite par le planificateur. Toute écriture qui laisse `due_notified` différent de `due_at` l'efface (`settleEntries`) : une échéance déplacée ou retirée redevient à notifier, ou cesse de l'être.
- Cocher et décocher restent les gestes de la Liste ; cocher ne touche pas à l'échéance.

## Planificateur et attente

- À chaque passage, pour chaque liste dont `due_dates` est activé : chaque élément non coché dont l'échéance est passée et pas encore notifiée reçoit une notification (titre : la liste ; texte : l'élément) et `due_notified` = son échéance. Une échéance n'est notifiée qu'une fois ; un élément coché ne l'est pas.
- Un élément attend quand `due_notified` est présent et `checked_at` absent : le point sur la tuile et la zone, comme pour Messages et Questionnaire. Le planificateur marque donc le retard, puisque les conditions d'attente ne connaissent pas l'heure.

## Écran et tuile

- L'ordre manuel reste le seul ordre. Un élément montre son échéance sous son nom ; passée et non coché, marquée « en retard ».
- La saisie d'un élément, nouveau ou ouvert, porte l'échéance quand l'option est activée.
- La tuile montre les éléments en retard en tête, puis les autres dans leur ordre.

## Les garanties, en tests

- Une échéance passée, non cochée, non notifiée est à notifier ; notifiée, cochée, future ou absente, elle ne l'est pas.
- Déplacer ou retirer l'échéance d'un élément notifié efface `due_notified` ; une écriture qui ne la touche pas le garde.
- Sans l'option, aucun champ d'échéance n'est déclaré et rien n'attend.
