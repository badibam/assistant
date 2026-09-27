# Les outils jamais livrés

Conception reprise le 2026-09-27, après la refonte des champs (`docs/DATA.md`, « Champs et entrées ») : un outil ne porte que ses façons rapides de créer une entrée et ses calculs sur plusieurs entrées ; tout le reste vient des champs.

## Tri

- **Liste** : un outil. Une entrée = un nom, une case cochée dans `state`, une position tenue comme l'ordre des notes (`settleEntries`).
- **Objectif** : un outil, le moins défini — ce qu'est une de ses entrées reste à trouver.
- **Calcul et Graphique** fusionnent en un outil d'analyse : des entrées désignées comme le fait le pointeur (outil, champ, période, filtres), un calcul sur elles, un affichage en nombre ou en courbe. Le résultat se calcule à l'affichage et ne s'enregistre pas ; seule une photo datée voulue comme telle serait une entrée.
- **Alerte** n'est pas un outil : « quand telle condition devient vraie, faire telle chose » relève des événements du cœur de l'app, à concevoir à part.
- **Données structurées** et **questionnaire** sont des candidats du même rang.
