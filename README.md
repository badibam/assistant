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

## Documentation Technique

- **CORE.md** : Architecture système, coordination, services
- **DATA.md** : Navigation hiérarchique, validation, patterns de données
- **UI.md** : Composants interface, formulaires, thèmes
- **TOOLS.md** : Architecture des outils
- **AI.md** : Architecture du système IA



## État du développement

**Version 0.3.10**

### Systèmes de base

- **CommandDispatcher** : Architecture `resource.operation` unifiée pour UI/IA/Scheduler/System
- **Validation** : JSON Schema avec messages traduits
- **Internationalisation** : Système `s.shared()`/`s.tool()` avec génération automatique
- **Discovery pattern** : Extension d'outils sans modification du core
- **UI** : Composants réutilisables, thèmes personnalisables, patterns standardisés avec highlight
- **Versioning** : Migrations SQL + transformations JSON centralisées
- **Backup/Restore** : Export/import/reset avec gestion versions et détection erreurs
- **Navigation données** : DataNavigator hiérarchique + ZoneScopeSelector avec périodes
- **Transcription** : Provider pattern (offline/online) avec auto-retry, formatage segments Vosk
- **Logging** : Système de logs in-app avec filtres (niveau, durée, tag) et purge automatique

### Système IA

- **Architecture** : Event-driven avec machine à états et orchestrateur centralisé
- **Sessions** : CHAT, SEED, AUTOMATION avec messages unifiés
- **Prompts** : Multi-niveaux (documentation L1 + données utilisateur L2)
- **Automations** : Scheduling, triggers, exécution autonome avec limites
- **Validation** : Hiérarchie App > Zone > Tool > Session > Request
- **Communication** : Modules MultipleChoice et Validation
- **Providers** : Abstraction extensible (Claude supporté)
- **Composer** : Architecture multi-blocs avec enrichments alternés, double preview UI/Prompt

### Outils

- **Tracking** : Suivi avec 7 types de données (numeric, text, scale, choice, timer, audio, multi-audio)
- **Journal** : Entrées textuelles/audio avec templates
- **Note** : Notes individuelles avec titre et contenu
- **Messages** : Notifications et rappels planifiés avec scheduler et système d'exécution



