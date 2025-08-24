# TODO : Système de validation utilisateur dans le Coordinateur

## Vision générale

Intégrer un système de validation utilisateur dans le Coordinateur pour les actions sensibles, en gardant une architecture simple où toutes les sources d'actions (User, IA, Scheduler) passent par la même queue unifiée.

## 1. Architecture unifiée des actions

### Principe de base
- **Une seule API d'entrée** : `coordinator.processAction(action, params, initiatedBy)`
- **Une seule queue** : Toutes les actions passent par le même mécanisme
- **Pas de distinction spéciale** : IA, User, Scheduler sont traités équitablement
- **Pas de priorités** : FIFO strict pour simplicité

### Modification du Coordinateur
- Étendre `processAction()` pour accepter le paramètre `initiatedBy`
- Identifier les actions nécessitant validation selon des critères configurables
- Mettre en pause les actions sensibles pour demander validation utilisateur

## 2. Système de validation utilisateur

### Critères de validation (configurables)
- **Actions de suppression** : delete->*, suppression de données
- **Actions critiques IA** : toute action `initiatedBy = "ai"` modifiant des configurations
- **Actions système sensibles** : modification de zones, suppression d'outils
- **Seuils configurables** : suppression de plus de X entrées, etc.

### Mécanisme de validation
1. **Détection** : Action identifiée comme sensible lors de `processAction()`
2. **Suspension** : Action stockée temporairement avec état "pending_validation"
3. **Notification UI** : Interface utilisateur informée avec contexte explicatif
4. **Attente réponse** : Coordinateur en attente de validation utilisateur
5. **Exécution/Annulation** : Selon réponse utilisateur

### Structure des demandes de validation
```
ValidationRequest {
    actionId: String
    action: String (ex: "delete->tracking_data")
    initiatedBy: String ("ai", "user", "scheduler")
    description: String (ex: "Supprimer 15 entrées nutritionnelles anciennes")
    reasoning: String? (ex: "Pour libérer l'espace et améliorer les performances")
    canCancel: Boolean
    contextData: Map<String, Any>
}
```

## 3. Interface utilisateur

### Types d'interface de validation
- **Modal simple** : Approuver/Refuser avec description claire
- **Modal détaillée** : Avec possibilité de voir les données concernées
- **Notification persistante** : Pour actions moins urgentes

### Messages explicatifs
- **Langage naturel** : "L'IA souhaite supprimer 15 entrées de suivi nutritionnel datées de plus de 6 mois"
- **Contexte métier** : Expliquer pourquoi l'action est proposée
- **Impact** : "Cette action est irréversible" / "Cela va améliorer les performances"

### Réponses utilisateur
- **Approuver** : Action exécutée immédiatement
- **Refuser** : Action annulée, source informée du refus
- **Détails** : Afficher plus d'informations sur l'action
- **Reporter** : Remettre en queue pour plus tard (optionnel)

## 4. Rôle de l'IA Manager

### Gestion stratégique des validations
- **L'IA Manager orchestre** ses demandes au coordinateur
- **Le coordinateur valide** action par action sans logique complexe
- **Pas de regroupement** au niveau coordinateur

### Stratégies IA Manager
1. **Séquentiel adaptatif** : Envoyer actions une par une, adapter selon validations
2. **Dépendances conditionnelles** : "Si action A validée, alors proposer action B"
3. **Alternatives** : Si validation refusée, proposer solution alternative
4. **Abandon intelligent** : Annuler actions dépendantes si prérequis refusé

### Exemple de flow IA
1. IA Manager : "Analyser données nutritionnelles"
2. Demande 1 : "access->tracking_data" → validé automatiquement (lecture)
3. Demande 2 : "delete->old_entries" → nécessite validation utilisateur
4. Si refusé : IA Manager adapte, propose alternative sans suppression
5. Demande 3 : "create->nutrition_graph" → selon stratégie adaptée

## 5. Implémentation technique

### Extensions du Coordinateur
- Ajouter état `ValidationPending` aux opérations en queue
- Méthode `requestUserValidation()` pour générer demandes
- Méthode `handleUserValidation()` pour traiter réponses
- Storage temporaire des actions en attente de validation

### Communication avec UI
- Event système pour notifier interface des validations requises
- Callbacks pour recevoir réponses utilisateur
- État UI reflétant validations en attente

### Gestion des timeouts
- Timeout configurable pour validations (ex: 5 minutes)
- Action par défaut si pas de réponse (refuser par sécurité)
- Nettoyage automatique des validations expirées

## 6. Avantages de cette approche

### Simplicité architecturale
- **Queue unique** : Pas de systèmes parallèles complexes
- **Coordinateur focus** : Reste centré sur orchestration et validation
- **IA Manager autonome** : Gère sa logique métier indépendamment

### Sécurité et contrôle
- **Validation systématique** : Toute action sensible passe par utilisateur
- **Traçabilité** : Origine et raison de chaque action documentées
- **Flexibilité** : Critères de validation configurables par utilisateur

### Expérience utilisateur
- **Transparence** : Utilisateur voit et contrôle ce que fait l'IA
- **Contexte** : Explications claires pour chaque demande
- **Gradation** : Peut ajuster niveau de validation selon préférences

## 7. Extensions futures possibles

### Apprentissage des préférences
- Mémoriser patterns de validation utilisateur
- Suggérer automatisations : "Toujours approuver ce type d'action ?"
- Profils de validation (strict/modéré/permissif)

### Validations par catégorie
- Règles spécifiques par type d'outil
- Seuils différents selon importance des données
- Validation par lot pour actions similaires

### Intégration notifications
- Validations non-urgentes via système de notifications
- Rappels pour validations en attente
- Historique des validations pour audit

## Étapes de développement

1. **Phase 1** : Étendre Coordinateur avec système validation de base
2. **Phase 2** : Interface UI pour demandes validation simples  
3. **Phase 3** : Intégration IA Manager avec gestion stratégique
4. **Phase 4** : Critères de validation configurables
5. **Phase 5** : Interface validation avancée avec détails/contexte
6. **Phase 6** : Extensions apprentissage et automatisation

