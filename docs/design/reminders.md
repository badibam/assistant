# Rappels

Un type d'outil (`reminders`) : une liste de choses à faire à un moment. Chaque entrée porte son échéance ; à l'heure, une notification ; passé l'heure, le rappel attend qu'on le coche.

## Sa place parmi les outils

| | Liste | Rappels | Messages |
|---|---|---|---|
| Une entrée | une chose | une chose à faire à un moment | un envoi d'un flux |
| Le temps | aucun | une échéance par entrée | le rythme de la config |
| Après l'heure | — | en retard jusqu'à être cochée | lue ou non lue |
| Notification | aucune | à l'échéance | à l'envoi |

La Liste reste simple : aucune échéance n'y entre. Un événement qui passe de lui-même (un rendez-vous) n'est pas un rappel : c'est l'Agenda, en attente dans `TODO.md`.

## Config de l'instance

- Base commune (nom, description, icône, mode d'affichage) et champs à soi.
- Notifications (oui/non) et priorité (normale, haute, basse) : comment ce canal a le droit de déranger. Au niveau de l'instance seul, jamais sur l'entrée.
- Pas de délai de prévenance : l'échéance est le moment de la notification.

## Une entrée

- Données : texte, échéance (un instant, la brique d'instant), répétition facultative (la brique de planning, telle qu'elle est), champs à soi.
- État : fait (oui/non), fait à (instant), notification envoyée pour l'échéance (instant de l'échéance notifiée).
- Le timestamp de l'entrée est son échéance : le temps est le seul axe d'ordre de `tool_data`.

## Cocher

- Rappel ponctuel : il est marqué fait, avec le moment.
- Rappel répété : une entrée close est écrite — texte, échéance qu'il avait, fait à, champs à soi — puis le rappel avance à la première échéance du planning après maintenant. Les échéances manquées ne s'empilent pas.
- Les deux formes donnent la même entrée close : l'historique se lit par la brique de lecture comme n'importe quelles entrées (un graphique, un critère d'Objectif, l'IA), et « fait en retard » se lit en comparant l'échéance au moment coché.
- Une opération de service `check` fait ce geste entier, pour l'écran comme pour l'IA ; une modification ordinaire de l'état ne peut pas le faire à moitié. Décocher une entrée close la rouvre comme un rappel ponctuel ; le rappel répété dont elle est sortie n'est pas touché.

## L'attente et le planificateur

- Un rappel ouvert dont l'échéance est passée attend : le point sur la tuile et sur la zone ; toucher la tuile ouvre le plus ancien en retard — le mécanisme déclaré par type d'outil.
- Le planificateur passe comme celui de Messages : à chaque rappel ouvert dont l'échéance est arrivée et pas encore notifiée, il envoie la notification (si l'instance l'autorise) et note l'échéance notifiée. Jamais deux notifications pour la même échéance ; une échéance avancée par « cocher » est neuve.

## Écran et tuile

- Écran : les rappels ouverts en haut, par échéance, ceux en retard marqués ; les entrées closes dessous, repliées, en historique. Ajout, modification, cocher.
- Tuile : les rappels en retard d'abord, puis le prochain avec son heure (« Relancer la Librairie · en retard depuis 9 h », « Prochain : Rempoter le basilic, sam. 10 h ») ; un bouton « + » ajoute un rappel sans ouvrir l'outil, comme la Liste.

## Les garanties, en tests

- Cocher un rappel répété écrit une entrée close et place le rappel à la première échéance du planning après l'instant du geste, même après plusieurs échéances manquées.
- Cocher un rappel ponctuel ne crée aucune entrée.
- Une échéance n'est notifiée qu'une fois, sur plusieurs passages du planificateur.
- Un rappel ouvert attend dès son échéance passée, et cesse d'attendre une fois coché.
