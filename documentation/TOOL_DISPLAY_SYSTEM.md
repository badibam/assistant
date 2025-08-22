# Système d'affichage des outils - Récapitulatif

## Structure par instance d'outil

**Chaque instance d'outil possède :**
- **5 modes d'affichage** dans la zone, définis par le type d'outil :
  - **ICON** (1/4 × 1/4) (Auto-généré, pareil pour tous les types d'outil)
  - **MINIMAL** (1/2 × 1/4)
  - **CONDENSED** (1/2 × 1/2)
  - **EXTENDED** (1 × 1/2)
  - **FULL** (1 × ∞)
- **1 page dédiée** (comme FULL avec quelques fonctionnalités en +)
- **1 page de configuration** (paramétrage de l'instance)

## Placement dans les zones

- **Système de grille manuelle** basée sur fractions 1/4
- **Règle actuelle** : 1 nouvel outil = 1 nouvelle "ligne"
- **Évolution future** : placement manuel selon grille

## Navigation

- **Clic sur outil** → Page dédiée de l'instance (utilisation)
- **Clic long sur outil** → Page dédiée de l'instance (configuration)
- **Configuration du mode d'affichage** → Via page config de l'instance

## Spécifications par type d'outil

### SUIVI (Tracking)

**MINIMAL** (1/2 × 1/4) :
- "+\nPoids" *(titre + 1 bouton saisie rapide)*

**CONDENSED** (1/2 × 1/2) :
- "Poids" *(titre)*
- 3 boutons de saisie (ex: "70kg", "75kg", "+")

**EXTENDED** (1 × 1/2) :
- "Poids" *(titre)*
- 7 boutons (dont champ libre si configuré)

**FULL** (1 × ∞) :
- "Poids" *(titre)*
- Tous les boutons de saisie configurés

**Page dédiée** :
- Tous les boutons + données récentes avec dates

### AUTRES OUTILS
Les autres types d'outils (OBJECTIFS, GRAPHIQUE, LISTE, JOURNAL, NOTE, MESSAGE, ALERTE, CALCUL, STATISTIQUE, DONNÉES STRUCTURÉES) seront spécifiés ultérieurement.
