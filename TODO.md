# TODO - Architecture des Services ✅ COMPLÉTÉ

## Services Core (structure app)

- **ZoneService** ✅ existe et intégré coordinateur
- **ToolInstanceService** ✅ créé et intégré coordinateur
- **AIService** ❌ à créer (futur)
- **SchedulerService** ❌ à créer (futur, déclencheur pas exécutant)

## Services par type d'outil (données métier) - DISCOVERY PURE

- **TrackingService** ✅ créé avec discovery via ToolTypeManager
- **JournalService** ❌ futur (pattern identique)
- **ObjectiveService** ❌ futur (pattern identique)
- etc.

## Services système

- **BackupService** ✅ existe déjà (pas encore intégré coordinateur)

## Architecture Discovery Implémentée ✅

### Pattern Discovery
- **ToolTypeContract** étendu : `getService()`, `getDao()`, `getDatabaseEntities()`
- **ToolTypeManager** : discovery centralisée services + DAOs
- **ServiceManager** : routing générique `"xxx_service"` → découverte via ToolTypeManager
- **Coordinator** : utilise reflection pour services découverts

### Standalone Databases
- **TrackingDatabase** : database séparée pour tracking data
- **Foreign keys supprimées** : discovery pure sans dépendances cross-database
- **Index ajouté** : performance maintenue sans FK
- **Pattern extensible** : 1 database par tool type

### Flux Complet
1. UI → `coordinator.processUserAction("create->tracking_data", params)`
2. Coordinator → `ServiceManager.getService("tracking_service")`  
3. ServiceManager → `ToolTypeManager.getServiceForToolType("tracking", context)`
4. TrackingService → `ToolTypeManager.getDaoForToolType("tracking", context)`
5. TrackingDao → `TrackingDatabase.getDatabase(context).trackingDao()`

**Extensibilité** : Nouveau tool type = implémenter ToolTypeContract, ajouter au scanner → automatiquement découvert