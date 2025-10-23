# Versioning & Migrations - Spécifications

## Principes fondamentaux

### Sources de vérité uniques

**build.gradle.kts** :
- `versionCode` = entier monotone (9, 10, 11...) pour comparaisons
- `versionName` = string SemVer ("0.3.0") pour affichage utilisateur

**AppDatabase** :
- `@Database(version = 10)` = version schéma SQL
- Indépendante de versionCode (peut rester stable entre releases)

**Règle** : Utiliser `BuildConfig.VERSION_CODE` et `BuildConfig.VERSION_NAME` partout (générés par Gradle)

**Suppression** : `CURRENT_APP_VERSION` et `CURRENT_CONFIG_VERSION` d'AppVersionManager (doublons inutiles)

---

## Types de migrations

### Type 1 - Structure DB (SQL)

**Quand** : Schéma SQL change (ALTER TABLE, CREATE TABLE, etc.)

**Géré par** : Room Migrations manuelles

**Exemple** : Ajout colonne `zones.color`

**Flow** :
1. Incrémenter `@Database(version = 11)`
2. Écrire `MIGRATION_10_11` avec SQL
3. Room détecte et exécute automatiquement au démarrage

**Règle** : Retirer `.fallbackToDestructiveMigration()` (dangereux)

### Type 2 - Format JSON (config, data)

**Quand** : Format JSON non rétrocompatible (renommage champs, restructuration)

**Géré par** : JsonTransformers centralisé

**Exemple** : `{"unit": "kg"}` → `{"measurement_unit": "kg"}`

**Flow** :
1. Écrire transformer dans `JsonTransformers`
2. Appliqué lors import backup + optionnel lors migration Room

---

## Architecture JsonTransformers

### Organisation centralisée

**Fichier unique** : `core/versioning/JsonTransformers.kt`

**Fonctions publiques** :
- `transformToolConfig(json, tooltype, from, to)`
- `transformToolData(json, tooltype, from, to)`
- `transformAppConfig(json, from, to)`

**Fonctions privées par tooltype** :
- `transformTracking(json, version)` → cases 9, 19, 29...
- `transformJournal(json, version)` → cases spécifiques

**Application séquentielle** : Boucle `for (v in from until to)` applique tous les transformers intermédiaires

### Pas de discovery pattern

**Raison** : Nombre limité de tooltypes, dev solo, simplicité > pureté architecturale

**Évolution future** : Si fichier dépasse 1000 lignes, découper en sous-fichiers dans même package (pas de discovery)

---

## Format export/import

### Metadata dans fichiers d'export

**Tous les exports incluent metadata** :
```json
{
  "metadata": {
    "export_version": 10,
    "export_timestamp": 1234567890,
    "export_type": "full|zone|tool_instance",
    "db_schema_version": 10
  },
  "data": { ... }
}
```

**En DB** : JSONs purs sans metadata (version déterminée par fichier d'export)

**Avantage** : Import partiel supporte versioning, DB reste propre

### Process d'import

**Vérification version** :
- Si `export_version > VERSION_CODE` → Erreur "version trop récente"
- Si `export_version < VERSION_CODE` → Appliquer transformations
- Si `export_version == VERSION_CODE` → Insertion directe

**Transformation** :
- Appliquer JsonTransformers à tous les JSONs du fichier
- Séquentiel : v9→v10→v11 si nécessaire

**Insertion** :
- Wipe DB (si full) ou validation conflits (si partiel)
- Insert données transformées

---

## Réutilisation transformers - Zéro duplication

### 3 points d'utilisation

**Migration Room (SQL)** :
- ALTER TABLE + appel JsonTransformers pour transformer JSONs en DB

**Import backup full** :
- Appel JsonTransformers sur JSON du fichier avant insertion

**Import partiel** (futur) :
- Appel JsonTransformers sur zone/outil importé

### Code écrit une fois

Transformations dans JsonTransformers → réutilisées partout

Pas de duplication logique entre migration Room et import

---

## Lifecycle transformers

### Accumulation historique

**Chaque transformer conservé indéfiniment** :
- Support import de vieux backups
- Cases `when (version)` avec 9, 19, 29...
- Jamais supprimés

**Numérotation** :
- Transformer numéroté selon version SOURCE
- v9→v10 = case "9"
- v19→v20 = case "19"

### Politique de rétention (optionnel futur)

Possibilité de définir `OLDEST_SUPPORTED_VERSION = 15`

Erreur explicite si import backup trop ancien

Permet de supprimer vieux transformers

---

## Cohérence versions

### État cible après Phase 1

**build.gradle.kts** :
- `versionCode = 10`
- `versionName = "0.3.0"`

**AppDatabase** :
- `@Database(version = 10)`
- `companion object { const val VERSION = 10 }`
- Pas de fallbackToDestructiveMigration

**BackupService** :
- `BuildConfig.VERSION_NAME` pour export
- `AppDatabase.VERSION` pour db_schema_version

**AppVersionManager** :
- Suppression CURRENT_APP_VERSION
- Suppression CURRENT_CONFIG_VERSION
- Garde isFirstLaunch() et tracking install

---

## Implémentation progressive

### Phase 1 - Cleanup (immédiat)

**Objectif** : Cohérence sources de vérité

**Actions** :
- Supprimer constantes doublons
- Utiliser BuildConfig partout
- Retirer fallbackToDestructiveMigration
- Ajouter AppDatabase.VERSION

**Livrable** : Versions cohérentes, pas de duplication

### Phase 2 - Infrastructure transformers

**Objectif** : Système de transformation prêt

**Actions** :
- Créer JsonTransformers.kt (vide au début)
- Modifier BackupService pour appeler transformers à l'import
- Tests avec backup actuel (pas de transformation nécessaire)

**Livrable** : Architecture extensible pour futures migrations

### Phase 3 - Première migration réelle (v11)

**Objectif** : Valider pattern avec vraie migration

**Actions** :
- Écrire MIGRATION_10_11 si changement DB
- Écrire transformers si changement JSON
- Tester upgrade v10→v11
- Tester import backup v10 sur app v11

**Livrable** : Pattern validé en production

### Phase 4 - Import partiel (futur lointain)

**Objectif** : Partage configs entre users

**Actions** :
- API exportZone/importZone
- Réutilisation JsonTransformers
- UI sélection zone à importer

**Livrable** : Feature complète import/export granulaire

---

## Règles d'or

1. **versionCode = source unique** pour version app (via BuildConfig)
2. **dbVersion indépendante** de versionCode (change si SQL change)
3. **Pas de metadata en DB** (version dans fichiers export uniquement)
4. **Transformers centralisés** dans JsonTransformers.kt
5. **Transformers réutilisés** partout (migrations + imports)
6. **Transformers conservés** indéfiniment (historique complet)
7. **Pas de fallback destructive** (migrations explicites obligatoires)

---

*Architecture pensée pour simplicité et maintenabilité à long terme.*
