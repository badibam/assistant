# Système IA - Architecture et Design

## Points validés lors de l'échange

### Architecture générale
✅ **Interface de commandes JSON exclusive** : L'IA communique uniquement via l'interface de commandes JSON structurées, aucun accès direct aux données

✅ **Navigation par métadonnées** : L'IA commence par naviguer dans les métadonnées/schémas JSON (zones, outils, configurations) comme une carte, puis demande les données réelles spécifiques

✅ **Intégration avec le Coordinateur** : Toutes les actions IA passent par le Coordinateur avec `initiated_by: "ai"`, garantissant la cohérence

### Système de prompts
✅ **PromptManager modulaire** : Assemble dynamiquement les prompts en combinant fragments pertinents :
- Prompt de base + documentation interface de commandes
- Contexte actuel + métadonnées des objets concernés
- Fragments fournis par chaque type d'outil

✅ **Hiérarchie de contexte** : Cascade de prompts selon le contexte (général → type d'outil → instance → action spécifique)

✅ **Documentation par outil** : Chaque type d'outil fournit des fichiers de documentation standardisés pour l'IA

### Interactions et sessions
✅ **Requêtes contextuelles** : L'IA peut demander plus d'informations via commandes pendant une session

✅ **Persistance des sessions** : Sessions restent actives jusqu'à terminaison explicite par l'utilisateur, seul un résumé est conservé après

✅ **Gestion des erreurs** : Retours structurés permettent à l'IA de corriger et réessayer

### Validation et permissions
✅ **Permissions par cascade** : Système de permissions avec défauts modifiables à tous les niveaux

✅ **Validation sélective** : Actions critiques nécessitent validation explicite de l'utilisateur

✅ **Verbalisation des actions** : Actions IA verbalisées via templates pour historique et validation

### Fonctionnalités avancées
✅ **API abstraite multi-modèles** : Couche d'abstraction pour différents fournisseurs IA avec adaptateurs spécifiques

✅ **Gestion coûts/quotas** : Monitoring des coûts avec seuils configurables et basculement automatique

✅ **Économie des tokens** : Versions multiples des données (brute, résumé, mots-clés) gérées par chaque outil

✅ **Déclencheurs automatiques** : IA peut être déclenchée par événements via configuration utilisateur

✅ **Actions autonomes** : IA peut proposer/configurer nouveaux outils avec contrôle utilisateur

✅ **Gestion offline/online** : File d'attente des requêtes IA hors ligne, traitement au retour de connexion

## Modules de communication
✅ **Architecture modulaire extensible** : Interface `CommunicationModule` standardisée
- MultipleChoice, Slider, DataSelector, Priorisation, ValidationÉtapes, etc.
- Interactions éphémères locales à la discussion

## Configuration utilisateur
✅ **Écran de configuration générale IA** :
- Personnalité/style d'interaction de l'IA
- Paramètres génériques au niveau app
- Directives granulaires par scope : au niveau de chaque zone, chaque instance d'outil

✅ **Séquences de mises à jour** :
- Pour indiquer ordre dans lequel effectuer les mises à jour des instances.
- Dans chaque instance : définition de fréquence d'update (manuel/ponctuel, quotidien, hebdo, etc.)
- Configuration des priorités : permet de savoir, pour les updates quotidiennes par exemple, l'ordre dans lequel les faire.
- Définition de ces priorité dans la config de la zone.

## Décisions d'interface utilisateur finalisées

✅ **Interface unifiée** : Chat unique avec enrichissements intégrés, pas de mode composition séparé

✅ **Workflow d'enrichissement** : 
- Textarea pleine largeur extensible (80-200px)
- Boutons d'enrichissement icon-only en dessous : 🔍📝✨🔧📁📚
- Clic bouton → Overlay de configuration → Bloc ajouté dans textarea
- Clic bloc → Réouverture overlay pour édition

✅ **Catégories d'actions IA** :
- **🔍 Données** : Lecture/accès aux données existantes (zones, instances, période, détail, importance)
- **📝 Utiliser** : Ajouter enregistrements dans outil existant (instance, type, timestamp)
- **✨ Créer** : Nouvelle instance d'outil (type, zone, nom, configuration)
- **🔧 Modifier** : Changer config/paramètres outil existant (outil, aspect, description)
- **📁 Organiser** : Zones, déplacement, hiérarchie (action, élément, détails)
- **📝 Documenter** : Métadonnées, descriptions (élément, type doc, contenu)

✅ **Sessions et historique** :
- Sessions persistent jusqu'à terminaison explicite utilisateur
- Actions validées immédiatement dans conversation via modules de communication
- **Historique read-only** : Feed chronologique `[HH:MM] IA/USER: Action effectuée`
- Pas d'actions "en attente" persistantes, tout traité en temps réel

✅ **Validation et contrôles** :
- Boutons header : 📜 Historique, 📊 État session, ✕ Fermer
- Actions proposées via modules de communication dans conversation
- Validation immédiate, pas de file d'attente d'actions

✅ **Enrichissements séquentiels** : 
- L'IA comprend le contexte par l'ordre des enrichissements dans le message
- Pas d'attachements explicites, workflow naturel message + enrichissements

## Prochaines étapes prioritaires

1. **Architecture de base**
   - PromptManager avec assemblage de fragments
   - Interface de commandes IA ↔ App
   - ServiceManager intégrant discovery IA

2. **Modules de communication**
   - Interface CommunicationModule de base
   - 2-3 modules centraux (MultipleChoice, Slider, DataSelector)
   - Système de découverte automatique

3. **Configuration IA**
   - Écran configuration générale
   - Système de permissions par cascade
   - Templates de verbalisation des actions

4. **Integration Coordinateur**
   - Support actions `initiated_by: "ai"`
   - File d'attente requêtes offline
   - Terminal avec feedback IA

## Points techniques restants à implémenter

🔄 **Modules de communication** : Interface standardisée pour interactions IA (MultipleChoice, Slider, etc.) intégrées dans conversation

🔄 **PromptManager** : Assemblage dynamique des fragments de prompt selon contexte + enrichissements

🔄 **Interface commandes** : Parsing et exécution des commandes JSON IA ↔ App via Coordinateur

🔄 **File d'attente offline** : Requêtes IA en attente de connexion avec retry automatique

🔄 **Documentation outils** : Format standardisé des fichiers de doc IA par type d'outil

🔄 **Système permissions** : Granularité et cascade des autorisations par action/outil/zone
