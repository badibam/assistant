# Sources de Données Externes

Guide technique pour le système de sources de données externes configurables par l'utilisateur (APIs web, capteurs, fichiers).

## ═══════════════════════════════════
## Vue d'Ensemble

### Principe
Système générique permettant à l'IA d'utiliser diverses sources de données externes (APIs web, capteurs physiques, fichiers) pour alimenter automatiquement les outils de l'application. L'utilisateur configure les sources, l'IA orchestre la collecte et met à jour les données.

### Exemple d'Usage : Météo
```
1. Utilisateur configure API "meteofrance"
2. Crée un outil Tracking "Météo Paris"
3. Programme tâche quotidienne : "Utilise l'API meteofrance pour mettre à jour le suivi Météo Paris toutes les 6h"
4. L'IA exécute automatiquement : API call → parsing → mise à jour tracking
```

### Exemple d'Usage Complexe : Nutrition
```
Architecture en 2 couches pour éviter les appels API répétés :

COUCHE 1 - Base de données statique (Tool type "Données structurées") :
1. IA analyse nouveaux aliments du "Tracking alimentaire"
2. IA vérifie correspondance avec "Recettes configurées" (salade césar = tomate + laitue + parmesan)
3. Pour recettes trouvées : calcule nutrition via recette → "Données structurées nutrition"
4. Pour aliments inconnus : USDA Search → USDA Details → "Données structurées nutrition"

COUCHE 2 - Calcul quotidien (Tool type "Tracking nutrition") :
5. IA quotidienne analyse "Tracking alimentaire" du jour
6. IA fait correspondances avec "Données structurées nutrition" (cache local)
7. IA calcule agrégations (calories totales, vitamines, minéraux)
8. IA met à jour "Tracking nutrition" avec totaux quotidiens

Avantages :
- Pas d'API calls répétés (données USDA en cache local)
- Performance (calcul instantané depuis cache)
- Base nutritionnelle s'enrichit automatiquement
- Recettes utilisateur prioritaires sur données génériques
```

**Avantage** : Aucun tool type spécialisé nécessaire - réutilise l'architecture existante.

## ═══════════════════════════════════
## Architecture Configuration

### Localisation
**Module Core** : `core/modules/apis/` avec écran dédié "Sources de Données" accessible depuis page d'accueil

### Types de Sources Supportés

1. **APIs Web** (`http_api`) - Services REST externes
2. **Capteurs Physiques** (`bluetooth_device`, `android_sensor`) - IoT et capteurs mobiles
3. **Fichiers Locaux** (`file_reader`) - CSV, logs, données exportées
4. **Bases de données** (`database`) - Connexions SQL externes

### Structure de Configuration

```json
{
  "data_sources": {
    "meteofrance": {
      "name": "Météo France",
      "source_type": "http_api",
      "description": "API officielle française de prévisions météorologiques",
      "enabled": true,
      "request_template": {
        "method": "GET",
        "url": "https://webservice.meteofrance.com/forecast/daily?lat={{latitude}}&lon={{longitude}}&token={{api_key}}",
        "headers": {
          "Accept": "application/json",
          "User-Agent": "Treelune/1.0"
        }
      },
      "parameters": {
        "latitude": {
          "description": "Latitude du lieu en degrés décimaux",
          "type": "number",
          "example": "48.8566",
          "required": true
        },
        "longitude": {
          "description": "Longitude du lieu en degrés décimaux",
          "type": "number",
          "example": "2.3522",
          "required": true
        },
        "api_key": {
          "description": "Clé d'authentification API",
          "type": "string",
          "source": "credentials",
          "required": true
        }
      },
      "filter_query": "SELECT temperature, humidity, condition FROM response WHERE forecast_date >= NOW()",
      "response_mapping": {
        "temperature": {
          "path": "data.forecast[0].temperature",
          "description": "Température actuelle en degrés Celsius",
          "type": "number",
          "unit": "°C"
        },
        "humidity": {
          "path": "data.forecast[0].humidity",
          "description": "Taux d'humidité relative en pourcentage",
          "type": "number",
          "unit": "%"
        },
        "condition": {
          "path": "data.forecast[0].weather.description",
          "description": "Description textuelle des conditions météorologiques",
          "type": "string"
        },
        "precipitation": {
          "path": "data.forecast[0].rain.1h",
          "description": "Précipitations sur la dernière heure en millimètres",
          "type": "number",
          "unit": "mm"
        }
      },
      "example_response": {
        "data": {
          "forecast": [{
            "temperature": 22.347,
            "humidity": 65,
            "weather": {
              "description": "Partiellement nuageux"
            },
            "rain": {
              "1h": 0.2
            }
          }]
        }
      },
      "credentials": {
        "api_key": "user_provided_encrypted_key"
      },
      "rate_limit": {
        "requests_per_hour": 1000,
        "requests_per_day": 10000
      }
    },
    "greenhouse_sensors": {
      "name": "Capteurs Serre",
      "source_type": "file_reader",
      "description": "Logger température et humidité serre",
      "enabled": true,
      "connection": {
        "path": "/sdcard/sensors/greenhouse.csv",
        "format": "csv",
        "delimiter": ",",
        "header_row": true,
        "poll_interval": 300
      },
      "filter_query": "SELECT temperature, humidity FROM data WHERE timestamp >= NOW() - INTERVAL 24 HOURS AND temperature BETWEEN -10 AND 50",
      "response_mapping": {
        "temperature": {
          "column": "temp",
          "type": "number",
          "unit": "°C"
        },
        "humidity": {
          "column": "humid",
          "type": "number",
          "unit": "%"
        }
      }
    },
    "xiaomi_scale": {
      "name": "Balance Xiaomi",
      "source_type": "bluetooth_device",
      "description": "Balance connectée Bluetooth",
      "enabled": true,
      "connection": {
        "protocol": "bluetooth_le",
        "device_address": "AA:BB:CC:DD:EE:FF",
        "service_uuid": "181b",
        "characteristic_uuid": "2a9c"
      },
      "filter_query": "SELECT weight FROM data WHERE timestamp >= NOW() - INTERVAL 1 HOUR",
      "response_mapping": {
        "weight": {
          "bytes": "0-2",
          "type": "uint16",
          "unit": "kg",
          "scale": 0.01
        }
      }
    },
    "openweather": {
      "name": "OpenWeather",
      "source_type": "http_api",
      "description": "Service météorologique international",
      "enabled": false,
      "request_template": {
        "method": "GET",
        "url": "https://api.openweathermap.org/data/2.5/weather?lat={{latitude}}&lon={{longitude}}&appid={{api_key}}&units=metric"
      },
      "parameters": {
        "latitude": {
          "description": "Latitude du lieu",
          "type": "number",
          "required": true
        },
        "longitude": {
          "description": "Longitude du lieu",
          "type": "number",
          "required": true
        },
        "api_key": {
          "description": "Clé API OpenWeather",
          "type": "string",
          "source": "credentials",
          "required": true
        }
      },
      "response_mapping": {
        "temperature": {
          "path": "main.temp",
          "description": "Température en degrés Celsius",
          "type": "number",
          "unit": "°C"
        },
        "humidity": {
          "path": "main.humidity",
          "description": "Humidité en pourcentage",
          "type": "number",
          "unit": "%"
        }
      },
      "example_response": {
        "main": {
          "temp": 22.34,
          "humidity": 65
        }
      },
      "credentials": {
        "api_key": ""
      }
    }
  }
}
```

## ═══════════════════════════════════
## Intégration PromptManager

### Documentation Générique Incluse
Le PromptManager inclut automatiquement dans les prompts IA :

1. **Instructions générales d'utilisation des APIs** :
```
Pour utiliser une API externe configurée :
1. Exécute la commande 'api.call' avec les paramètres requis
2. Parse la réponse selon le response_mapping fourni
3. Convertis les données si nécessaire selon le format du tool cible
4. Met à jour le tool avec les données formatées
```

2. **Configuration spécifique** de l'API utilisée (JSON complet)

3. **Configuration du tool cible** (schéma et structure attendue)

### Conversion Intelligente par l'IA
L'IA analyse automatiquement :
- **Format source** : `temperature: 22.347` (API)
- **Format cible** : Configuration du tracking (entier/décimal/texte)
- **Conversion nécessaire** : Arrondit, convertit unités, formate selon besoin

**Exemple** :
- API retourne `22.347°C`
- Tracking configuré pour valeurs entières
- IA convertit automatiquement : `22`

## ═══════════════════════════════════
## Pattern d'Usage

### 1. Configuration API (Utilisateur)
```
Paramètres → APIs Externes → Ajouter
- Nom : "Météo France"
- URL template avec placeholders
- Documentation des paramètres
- Mapping des réponses
- Clé API
```

### 2. Création Tool (Utilisateur)
```
Nouvelle zone → Tracking "Météo Paris"
- Champs : température, humidité, condition
- Fréquence : Toutes les 6h
```

### 3. Automatisation (Scheduler + IA)
```
Tâche programmée quotidienne :
"Utilise l'API meteofrance configurée pour récupérer les données météo de Paris (latitude: 48.8566, longitude: 2.3522). Met à jour l'outil tracking 'Météo Paris' avec une entrée toutes les 6h en utilisant les champs temperature, humidity, condition."
```

### 4. Exécution Automatique
```
IA → api.call("meteofrance", {latitude: 48.8566, longitude: 2.3522})
API → Retourne données JSON
IA → Parse selon response_mapping
IA → Convertit format si nécessaire
IA → tool_data.create("meteoParis", donnéesFormatées)
```

## ═══════════════════════════════════
## Architecture Module

### Structure Core Module
```
core/modules/apis/
├── DataSourceManager.kt       # Service principal (ExecutableService)
├── DataSourceConfigService.kt # CRUD configurations sources
├── QueryEngine.kt             # Moteur requêtes de filtrage
├── connectors/
│   ├── HttpApiConnector.kt    # APIs REST
│   ├── FileReaderConnector.kt # Fichiers CSV/logs
│   ├── BluetoothConnector.kt  # Capteurs Bluetooth
│   └── AndroidSensorConnector.kt # Capteurs Android
└── ui/
    └── DataSourcesScreen.kt   # Écran module accessible depuis accueil
```

### ServiceRegistry
```kotlin
// Services core
"data_sources" → DataSourceConfigService  // CRUD configs
"data_source_call" → DataSourceManager    // Exécution collecte

// Pattern discovery identique aux autres services core
```

## ═══════════════════════════════════
## Composants Techniques

### Service DataSourceManager
```kotlin
class DataSourceManager(private val context: Context) : ExecutableService {
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        return when (operation) {
            "call" -> callDataSource(params, token)
            "test_connection" -> testDataSource(params, token)
            "list" -> listAvailableDataSources()
            else -> OperationResult.error("Unknown operation: $operation")
        }
    }

    private suspend fun callDataSource(params: JSONObject, token: CancellationToken): OperationResult {
        val sourceName = params.getString("source_name")
        val sourceConfig = getDataSourceConfig(sourceName)
        val requestParams = params.getJSONObject("parameters")

        // Sélectionne le bon connecteur selon source_type
        val connector = when (sourceConfig.source_type) {
            "http_api" -> HttpApiConnector()
            "file_reader" -> FileReaderConnector()
            "bluetooth_device" -> BluetoothConnector()
            "android_sensor" -> AndroidSensorConnector()
            else -> return OperationResult.error("Unknown source type: ${sourceConfig.source_type}")
        }

        // Exécute collecte via connecteur approprié
        val rawData = connector.collect(sourceConfig, requestParams)

        // Applique requête de filtrage configurée
        val filteredData = QueryEngine.executeFilter(rawData, sourceConfig.filter_query)

        // Applique response_mapping pour extraction champs
        val mappedData = applyResponseMapping(filteredData, sourceConfig.response_mapping)

        // Retourne seulement données filtrées et mappées
        return OperationResult.success(mapOf(
            "filtered_data" to mappedData,
            "source_info" to mapOf(
                "name" to sourceConfig.name,
                "type" to sourceConfig.source_type
            )
        ))
    }
}
```

### Stockage Sécurisé
- **Clés API** : Chiffrées via Android Keystore
- **Configuration** : Base de données locale standard
- **Cache réponses** : Temporaire, respecte rate limits

### Interface Utilisateur
```kotlin
@Composable
fun DataSourcesScreen() {
    var dataSources by remember { mutableStateOf(loadDataSources()) }

    Column {
        UI.PageHeader(
            title = "Sources de Données",
            subtitle = "Configuration des APIs, capteurs et fichiers de données"
        )

        dataSources.forEach { source ->
            UI.Card {
                DataSourceCard(
                    source = source,
                    onEdit = { editDataSource(it) },
                    onTest = { testConnection(it) },
                    onToggle = { toggleDataSource(it) }
                )
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            onClick = { showAddDataSourceDialog = true }
        )
    }
}
```

## ═══════════════════════════════════
## Extensibilité

### Ajout Nouvelle Source
1. **Template prêt** : Structure JSON standard
2. **Test intégré** : Bouton "Tester connexion"
3. **Documentation** : Champs description obligatoires
4. **Validation** : Schéma JSON pour config sources
5. **Query Builder** : Assistant IA pour écrire requêtes de filtrage

### Types de Sources Supportés
- **REST JSON** : GET, POST avec paramètres et authentification
- **Fichiers** : CSV, logs avec parsing et filtrage temporel
- **Capteurs Bluetooth** : Devices IoT avec protocoles standards
- **Capteurs Android** : Accéléromètre, GPS, caméra
- **Bases de données** : Connexions SQL avec requêtes personnalisées

### Filtrage Unifié
- **Query Language** : SQL-like pour tous types de sources
- **Performance** : Filtrage côté source avant transmission à l'IA
- **Expressivité** : Conditions complexes, agrégations, jointures

### Exemples de Cas d'Usage Prévus
- **Météo** : Météo France, OpenWeather, AccuWeather
- **Nutrition** : USDA FoodData Central, Open Food Facts
- **Finance** : Taux de change, cours bourse
- **Transport** : Horaires trains, trafic routier
- **Santé** : Qualité de l'air, pollens
- **Réseaux sociaux** : Statistiques, mentions
- **IoT/Capteurs** : Données domotique, capteurs personnels

## ═══════════════════════════════════
## Tool Types Complets

Mise à jour de la liste complète après l'analyse du système API :

**✅ Implémentés :**
- **Tracking** - Données temporelles + APIs automatiques
- **Notes** - Notes individuelles

**🎯 Priorité immédiate :**
- **Liste** - Items à cocher thématiques
- **Objectif** - Système hiérarchique objectifs → sous-objectifs → critères
- **Journal** - Entrées libres datées avec speech-to-text différé

**📊 Outils d'analyse :**
- **Graphique** - Visualisations basées sur données existantes
- **Calcul** - Formules et agrégations automatiques

**🔔 Outils d'interaction :**
- **Message** - Notifications/rappels planifiés
- **Alerte** - Déclenchement automatique sur seuils

**🌐 Système transversal :**
- **API** - Configuration et utilisation d'APIs externes (intégré dans architecture existante)
- **Données structurées** - Base de données statique pour cache local (ex: nutrition USDA)

---

*Le système API transforme l'application en hub de données automatisé, l'IA orchestrant la collecte et la mise à jour via les outils existants.*
