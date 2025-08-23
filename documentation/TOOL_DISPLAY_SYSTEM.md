# Système d'affichage des outils - Récapitulatif

## Structure par instance d'outil

**Chaque instance d'outil possède :**
- **6 modes d'affichage** dans la zone :
  - **ICON** (1/4 × 1/4) - Icône seule (standardisé)
  - **MINIMAL** (1/2 × 1/4) - Icône + Titre côte à côte (standardisé)
  - **LINE** (1 × 1/4) - Icône + Titre à gauche, contenu libre à droite
  - **CONDENSED** (1/2 × 1/2) - Icône + Titre en haut à gauche, reste libre
  - **EXTENDED** (1 × 1/2) - Icône + Titre en haut à gauche, reste libre
  - **FULL** (1 × ∞) - Icône + Titre en haut à gauche, reste libre
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

## Structure générale des modes d'affichage

**ICON** (1/4 × 1/4) :
- Icône seule, centrée

**MINIMAL** (1/2 × 1/4) :
- Icône + Titre côte à côte (layout horizontal standardisé)

**LINE** (1 × 1/4) :
- Icône + Titre à gauche (layout horizontal standardisé)
- Partie droite : contenu libre défini par le tool type

**CONDENSED** (1/2 × 1/2) :
- Icône + Titre en haut à gauche (layout horizontal standardisé)
- Reste de l'espace : contenu libre défini par le tool type

**EXTENDED** (1 × 1/2) :
- Icône + Titre en haut à gauche (layout horizontal standardisé)  
- Reste de l'espace : contenu libre défini par le tool type

**FULL** (1 × ∞) :
- Icône + Titre en haut à gauche (layout horizontal standardisé)
- Reste de l'espace : contenu libre défini par le tool type

**Page dédiée** :
- Contenu entièrement libre défini par le tool type

## Configuration des icônes

**Système d'icônes thématiques** :
- Convention nommage : `{theme}_{icon_name}` (ex: `default_activity.xml`)
- Chaque tool type définit une icône par défaut via `getDefaultIconName()`
- L'utilisateur peut personnaliser l'icône via le champ `icon_name` dans la config JSON
- Sélection d'icônes dans l'écran de configuration : icône par défaut affichée + clic pour choisir parmi les icônes du thème
**Interface de sélection** :
- Fin de ligne titre config → Icône par défaut affichée
- Clic sur icône → Liste des icônes disponibles du thème courant
- Sélection → Mise à jour `icon_name` dans config JSON


## Spécifications par type d'outil

### SUIVI (Tracking) - Exemple d'implémentation

**LINE** - Partie libre :
- Bouton "+" et "New" (en cas de mode MIXED)

**CONDENSED** - Partie libre :
- Bouton "+" et "New" (en cas de mode MIXED)

**EXTENDED** - Partie libre :
- Premiers boutons de saisie + champ libre si configuré

**FULL** - Partie libre :
- Tous les boutons de saisie/champ libre configurés

**Page dédiée** :
- Tous les boutons + historique complet avec dates, options de suppression

### AUTRES OUTILS
Les autres types d'outils (OBJECTIFS, GRAPHIQUE, LISTE, JOURNAL, NOTE, MESSAGE, ALERTE, CALCUL, STATISTIQUE, DONNÉES STRUCTURÉES) seront spécifiés ultérieurement.
