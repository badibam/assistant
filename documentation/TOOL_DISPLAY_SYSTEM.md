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

**ICON** (1/4 × 1/4) :
- Icône (titre inclus dessous)

**MINIMAL** (1/2 × 1/4) :
- Icône + Titre +  bouton "+" (autres items)

**CONDENSED** (1/2 × 1/2) :
- titre + bouton "+" (autres items)
- 3 boutons de saisie

**EXTENDED** (1 × 1/2) :
- titre
- Ligne 2 : boutons de saisie + champ libre si configuré

**FULL** (1 × ∞) :
- Titre
- Tous les boutons de saisie/champ libre configurés

**Page dédiée** :
- Tous les boutons + données récentes avec dates, option de suppression

### AUTRES OUTILS
Les autres types d'outils (OBJECTIFS, GRAPHIQUE, LISTE, JOURNAL, NOTE, MESSAGE, ALERTE, CALCUL, STATISTIQUE, DONNÉES STRUCTURÉES) seront spécifiés ultérieurement.
