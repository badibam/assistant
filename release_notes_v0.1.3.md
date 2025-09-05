## Version 0.1.3

### Correctifs
- Fix validation inconsistance Dialog vs Service : résolution bug où données validées différaient des données envoyées
- Pattern Single Source of Truth : une seule source de données validées utilisée partout
- Filtrage silencieux des items prédéfinis complètement vides

### Améliorations
- Externalisation des schémas JSON vers objets Kotlin pour meilleure maintenabilité
- Validation centralisée V3 avec messages d'erreur traduits en français  
- Amélioration détection schémas conditionnels avec regex précise
- Optimisation TrackingService : suppression redondances JSON
- Toast d'erreurs automatiques pour validation formulaires

### Code
- Suppression code redondant (-70 lignes dupliquées)
- Nettoyage validation V1 : finalisation V3
- Documentation mise à jour : refonte validation V1→V3
- Patterns UI standardisés : hiérarchie boutons, extensions FieldType
