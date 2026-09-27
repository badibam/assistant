# Outils manquants

Conception commencée le 2026-09-27, après la refonte des champs (`docs/DATA.md`, « Champs et entrées ») : un type d'outil ne porte plus que ses façons rapides de créer une entrée et ses calculs sur plusieurs entrées.

## Tri (provisoire)

- **Liste** : un outil. Une entrée : un nom, une case cochée dans `state`, une position tenue comme l'ordre des notes (`settleEntries`).
- **Objectif** : un outil ; ce qu'est une entrée reste à dire (critère, point d'étape, évaluation).
- **Calcul** et **Graphique** : deux outils, pas un. Calcul est une source (ses résultats sont lus par les autres outils), Graphique une vue (sa sortie s'affiche). Ce qu'ils partagent vit au cœur : la désignation des entrées (le pointeur) et les agrégations.
- **Alerte** : probablement pas un outil, mais un cas des événements du cœur (`NOTES.md`, « Events et badges ») — à confirmer.
- **Données structurées**, **questionnaire** : candidats au même rang.

## Calcul

- **Outil actif** (`docs/TOOLS.md`) : il s'exécute selon sa planification (celle des Messages et des automations, rattrapage compris) et écrit ses résultats comme des entrées ordinaires, datées, lisibles par `tool_data.get`, le pointeur, les graphiques, les alertes et l'IA.
- **Trois périodes indépendantes** : la fréquence d'exécution (la planification), la portée (ce qu'une exécution lit : les 30 derniers jours, la semaine écoulée, tout l'historique) et le découpage (un résultat pour toute la portée, ou un par jour, semaine, mois).
- **Un résultat porte la période qu'il couvre** quand sa formule lit une période : sa période de découpage, ou sa portée sans découpage. Un calcul qui lit un état n'a qu'une date.
- **Même période de découpage = même résultat** : une nouvelle exécution le remplace. Rattraper une saisie tardive, c'est une portée plus large que le découpage ; recalculer un historique, une exécution à la main sur une portée élargie. Pas de recalcul automatique quand une source change : il pourra s'ajouter sans migrer les résultats.
- Un remplacement qui change la valeur d'un résultat garde la trace de l'ancienne — à préciser.
