# -------- #
# Treelune #
# -------- #

- **Améliorer la vie dans toutes ses dimensions**
- **Collaboration IA-humain symétrique**
- **Outil personnalisable et extensible**

## 1. Améliorer la vie dans toutes ses dimensions

### Outils variés pour enregistrer, structurer et présenter toutes sortes de données
L'assistant propose divers outils (Suivi, Objectif, Graphique, Journal, Liste, Note, Message, Alerte, ...) pour capturer et organiser n'importe quelle information personnelle. Chaque outil transforme les données brutes en insights exploitables.

### Les outils se combinent et s'enrichissent mutuellement
Les outils créent des chaînes de valeur automatiques : un Suivi alimentaire nourrit des variables nutritionnelles (kcal, protéines du jour), qu'un Objectif compare à sa cible, qu'un Graphique dessine et qui déclenchent - par exemple - des Alertes personnalisées. L'IA orchestre ces connexions pour transformer les habitudes en système d'amélioration continue.


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
git clone git@github.com:badibam/treelune.git
cd treelune
./gradlew assembleDebug
```

## Documentation Technique

- **CORE.md** : Architecture système, coordination, services
- **DATA.md** : Navigation hiérarchique, validation, patterns de données
- **UI.md** : Composants interface, formulaires, thèmes
- **TOOLS.md** : Architecture des outils
- **AI.md** : Architecture du système IA



## État du développement

**Version 0.3.15**

### Systèmes de base

- **CommandDispatcher** : Architecture `resource.operation` unifiée pour UI/IA/Scheduler/System
- **Validation** : JSON Schema avec messages traduits
- **Internationalisation** : Système `s.shared()`/`s.tool()` avec génération automatique
- **Discovery pattern** : Extension d'outils sans modification du core
- **UI** : Composants réutilisables, thèmes personnalisables, patterns standardisés avec highlight
- **Versioning** : Migrations SQL + transformations JSON centralisées
- **Backup/Restore** : Export/import/reset avec gestion versions et détection erreurs
- **Pointeur** : désigne une zone ou un outil dans un message à l'IA, joint sa config ou ses entrées, restreintes par période, filtres par valeur et champs
- **Logging** : Système de logs in-app avec filtres (niveau, durée, tag) et purge automatique
- **Démo** : un groupe de zones « Démo » réinstallé à chaque mise à jour, ses données à flot au moment de l'installation (douze semaines d'une vie fictive, chronomètre en cours, échéances, objectifs, automations désactivées), en français ou en anglais selon le téléphone ; réinstallable ou supprimable depuis ses réglages

### Système IA

- **Architecture** : Event-driven avec machine à états et orchestrateur centralisé
- **Sessions** : CHAT, SEED, AUTOMATION avec messages unifiés
- **Prompts** : Multi-niveaux (documentation L1 + données utilisateur L2)
- **Automations** : Scheduling, triggers, exécution autonome avec limites
- **Validation** : Hiérarchie App > Zone > Tool > Session > Request
- **Communication** : questions de l'IA à l'utilisateur sous forme de champs (choix, texte, nombre, date…), ou simple confirmation
- **Providers** : Abstraction extensible (Claude, OpenAI, DeepSeek)
- **Composer** : Architecture multi-blocs avec enrichments alternés ; un pointeur nomme sa cible comme elle s'appelle au moment où le message est lu, à l'écran comme pour l'IA

### Outils

- **Tracking** : Suivi avec 7 types de données (numeric, text, scale, choice, timer, audio, multi-audio)
- **Journal** : Entrées textuelles/audio avec templates
- **Note** : Notes individuelles avec titre et contenu
- **Liste** : Ce qui reste à faire (courses, tâches, check-list) : un élément est un nom et les champs de la liste, coché avec sa date, réordonné en glissant ; les cochés se décochent d'un geste, ou disparaissent dès qu'on les coche si la liste le demande ; avec l'option Échéances, un élément peut porter une échéance, notifiée à son heure, et reste en retard tant qu'il n'est pas coché
- **Objectif** : Un objectif jugé par le compte de ses critères, une tentative par période : critères lus dans vos entrées ou vos variables, ou saisis ; au moins N, indispensables ; validé en réussite ou en échec, expiré sinon, rouvrable
- **Questionnaire** : Des questions une par écran, à la demande ou sur invitation planifiée, par vous ou avec l'IA dans une conversation ; à remplir, rempli ou ignoré
- **Graphique** : Un graphique des entrées de vos outils et de vos variables, qui dessine sans jamais calculer : couches superposées, vues l'une sous l'autre ou par catégorie, lignes, points, barres empilées, aires, camemberts, calendriers ; une grille lit à chaque jour (ou semaine, mois…) des variables et des lectures ; une valeur manquante est un trou qui mène aux entrées à corriger
- **Données structurées** : Des fiches faites de vos champs, chacune retrouvée par son nom (aliments, livres, contacts) : un tableau trié, une fiche par écran, une recherche et des filtres
- **Messages** : Une instance = un message. Sa config porte la part commune de chaque envoi et sa récurrence ; ses entrées sont les envois (à venir, partis, expirés, annulés)



