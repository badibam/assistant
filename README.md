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
L'IA et l'utilisateur disposent des mêmes capacités d'action sur les données via des commandes bidirectionnelles. Une action possible depuis l'interface graphique l'est aussi via l'interface de commandes IA.

### Communication bidirectionnelle et riche
L'utilisateur dispose de raccourcis conversationnels qui intègrent automatiquement instructions et données pertinentes dans ses prompts (analyser tel graphique, configurer cet outil, modifier cette zone). En retour, l'IA utilise des modules de communication intégrés (validation temps réel, propositions, feedback) pour faciliter le dialogue.


## 3. Outil personnalisable et extensible

### Zones et outils personnalisables
L'utilisateur nomme et organise librement ses zones thématiques (Santé, Productivité, etc.) et y intègre et configure les outils de son choix selon ses besoins spécifiques.

### Évolution avec l'usage
L'assistant s'affine au fil du temps grâce aux données accumulées et aux interactions avec l'IA. Les outils deviennent plus pertinents et les données générées plus précises.


# ------------------ #
# Aspects techniques #
# ------------------ #

## Technologie

**Stack** : Android natif (Kotlin + Jetpack Compose)
**Base de données** : Room (SQLite) avec event sourcing
**IA** : Via différentes API (extensible)


## Installation

```bash
git clone [repository]
cd assistant
./gradlew assembleDebug
```

## Structure du Code

```
app/src/main/java/com/assistant/
├── core/                           # Architecture centrale
│   ├── ai/                         # Système IA (future intégration)
│   │   └── prompts/                # Templates de prompts
│   ├── commands/                   # Système de commandes
│   ├── coordinator/                # CommandDispatcher et orchestration
│   ├── database/                   # Event sourcing et entités
│   │   ├── dao/                    # Data Access Objects  
│   │   └── entities/               # Entités core (zones, tools, etc.)
│   ├── schemas/                    # Schémas JSON pour validation
│   ├── services/                   # Services core (Zone, Tool, AppConfig, etc.)
│   ├── strings/                    # Système d'internationalisation
│   │   └── sources/                # Sources XML des strings
│   ├── themes/                     # Système de thèmes core
│   ├── tools/                      # Discovery et métadonnées d'outils
│   │   └── ui/                     # Composants UI génériques pour outils
│   ├── ui/                         # Interface utilisateur centrale
│   │   ├── components/             # Composants UI réutilisables
│   │   ├── schemas/                # Schémas UI pour formulaires
│   │   └── Screens/                # Écrans principaux (Main, Zone, Create)
│   ├── update/                     # Système de mise à jour
│   ├── utils/                      # Utilitaires (formatting, locale)
│   ├── validation/                 # SchemaValidator V3
│   └── versioning/                 # Migrations et gestion versions
├── tools/                          # Outils spécialisés
│   └── tracking/                   # Tool type Suivi
│       ├── handlers/               # Types de données (numeric, text, etc.)
│       ├── timer/                  # Système timer intégré
│       └── ui/                     # Interface tracking spécifique
│           └── components/         # Composants tracking dédiés
└── themes/                         # Thèmes visuels
    ├── default/                    # Thème par défaut
    │   └── icons/                  # Icônes vectorielles thème
    └── dflt/                       # Thème alternatif
        └── icons/                  # Icônes vectorielles alternatives

app/src/main/res/                   # Ressources Android
├── drawable/                       # Ressources graphiques
├── mipmap-*/                       # Icônes app (densités)
├── values/                         # Strings, couleurs, dimensions
└── xml/                           # Configurations XML
```


## Documentation Technique

- **CORE.md** : Architecture système, coordination, services
- **DATA.md** : Navigation hiérarchique, validation, patterns de données
- **UI.md** : Composants interface, formulaires, thèmes
- **TOOLS.md** : Architecture des outils


## État du développement

**Version 0.2** (septembre 2025)

La majorité des systèmes de base sont finalisés :

- **CommandDispatcher unifié** : Architecture `resource.operation` pour UI/IA/Scheduler/System
- **Validation JSON Schema** : Validation centralisée avec messages traduits
- **Internationalisation modulaire** : Système `s.shared()`/`s.tool()` avec génération automatique
- **Discovery pattern** : Extension d'outils sans modification du core
- **Interface UI cohérente** : Composants réutilisables et patterns standardisés
- **Système de thèmes** : Ajout de thème possible (personnalisation en profondeur) + palettes
- **Tool type Tracking** : Outil de suivi complet avec 7 types de données

## Roadmap

- **Prompt manager** : Génération de prompts contextuels pour requêtes IA
- **API IA** : Couche d'abstraction pour connexion à différentes IA
- **Grille UI** : Affichage différencié des outils dans les zones
- **Event sourcing** : Historique automatique avec migrations de base
- **Navigateur de données** : logique commune et spécifique IA/UI d'exploration des données via Schémas JSON + données réelles
- **Outils supplémentaires**



