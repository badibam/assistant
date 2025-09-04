# --------- #
# Assistant #
# --------- #

- **Améliorer la vie dans toutes ses dimensions**
- **Collaboration IA-humain symétrique** 
- **Outil personnalisable et extensible**

## 1. Améliorer la vie dans toutes ses dimensions

### Outils variés pour enregistrer, structurer et présenter toutes sortes de données
L'assistant propose divers outils (Suivi, Objectif, Graphique, Journal, Liste, Note, Message, Alerte, ...) pour capturer et organiser n'importe quelle information personnelle. Chaque outil transforme les données brutes en insights exploitables.

### Les outils se combinent et s'enrichissent mutuellement
Les outils créent des chaînes de valeur automatiques : un Suivi alimentaire nourrit des Calculs nutritionnels qui génèrent des Graphiques et déclenchent - par exemple - des Alertes personnalisées. L'IA orchestre ces connexions pour transformer les habitudes en système d'amélioration continue.


## 2. Collaboration IA-humain symétrique

### Symétrie fonctionnelle (mêmes capacités d'action)
L'IA et l'utilisateur disposent des mêmes capacités d'action sur les données via des commandes bidirectionnelles. Une action possible depuis l'interface graphique l'est aussi via l'interface de commandes IA, garantissant une égalité fonctionnelle.

### Communication bidirectionnelle et riche
L'utilisateur dispose de raccourcis conversationnels qui intègrent automatiquement instructions et données pertinentes dans ses prompts (analyser tel graphique, configurer cet outil, modifier cette zone). En retour, l'IA utilise des modules de communication intégrés (validation temps réel, propositions, feedback) pour maintenir un dialogue fluide et fructueux.


## 3. Outil personnalisable et extensible

### Zones et outils personnalisables
L'utilisateur nomme et organise librement ses zones thématiques (Santé, Productivité, etc.) et y intègre et configure les outils de son choix selon ses besoins spécifiques.

### Évolution avec l'usage
L'assistant s'affine au fil du temps grâce aux données accumulées et aux interactions avec l'IA. Les outils deviennent plus pertinents, les données générées plus précises, et l'IA propose automatiquement de nouvelles configurations optimisées.


# ------------------ #
# Aspects techniques #
# ------------------ #

## Technologie

**Stack** : Android natif (Kotlin + Jetpack Compose)
**Base de données** : Room (SQLite) avec event sourcing
**IA** : Interface de chat + système de commandes


## Principes de Développement

**Modularité** : Chaque outil/thème = dossier indépendant avec structure standardisée
**Discovery** : Pas d'imports hardcodés, découverte automatique des outils et thèmes via interfaces
**Validation** : 2 couches seulement - UI légère + Service robuste
**Conventions** : Suivre les patterns existants, pas de réinvention


## Installation

```bash
git clone [repository]
cd assistant
./gradlew assembleDebug
```

## Structure du Code

```
app/
├── core/           # Architecture centrale
│   ├── ai/         # IA et prompts
│   ├── commands/   # Système de commandes
│   ├── coordinator/# Coordination centrale
│   ├── database/   # Event sourcing, schémas
│   ├── services/   # Services core
│   ├── tools/      # Système de découverte d'outils
│   ├── ui/         # Composants UI centraux
│   ├── update/     # Système de mise à jour
│   ├── utils/      # Utilitaires
│   ├── validation/ # Validation système
│   └── versioning/ # Gestion versions
├── tools/
│   ├── tracking/   # Outil de suivi
│   └── ...         # Autres outils
└── themes/
    ├── default/    # Theme par défaut
    └── ...         # Autres thèmes
```


## Documentation Technique

- **CORE.md** : Architecture système, coordination, données
- **UI.md** : Composants interface, formulaires, thèmes
- **TOOLS.md** : Création d'outils, extensibilité


## État du développement

**Version 0.1.3** (septembre 2025)
- Architecture finale stabilisée
- Système de zones et outils fonctionnel
- Interface UI propre avec patterns établis
- **Validation JSON Schema V3** centralisée avec traduction automatique
- Tool type Tracking complet et opérationnel


## Roadmap

- Ajout d'un deuxième type d'outil
- Généralisation UI
- Internationalisation complète (passage de strings.xml partiels à 100%)
- Intégration de l'architecture et outils IA
- Personnalisation visuelle des zones
- Développement des autres types d'outils


