# API Configuration System

Guide technique pour le système d'APIs externes configurables par l'utilisateur.

## ═══════════════════════════════════
## Vue d'Ensemble

### Principe
Système générique permettant à l'IA d'utiliser des APIs externes pour alimenter automatiquement les outils de l'application. L'utilisateur configure les APIs, l'IA orchestre les appels et met à jour les données.

### Exemple d'Usage : Météo
```
1. Utilisateur configure API "meteofrance"
2. Crée un outil Tracking "Météo Paris"
3. Programme tâche quotidienne : "Utilise l'API meteofrance pour mettre à jour le suivi Météo Paris toutes les 6h"
4. L'IA exécute automatiquement : API call → parsing → mise à jour tracking
```

**Avantage** : Aucun tool type spécialisé nécessaire - réutilise l'architecture existante.

## ═══════════════════════════════════
## Architecture Configuration

### Localisation
**Écran global** : Paramètres → "APIs Externes"

### Structure de Configuration

```json
{
  "external_apis": {
    "meteofrance": {
      "name": "Météo France",
      "description": "API officielle française de prévisions météorologiques",
      "enabled": true,
      "request_template": {
        "method": "GET",
        "url": "https://webservice.meteofrance.com/forecast/daily?lat={{latitude}}&lon={{longitude}}&token={{api_key}}",
        "headers": {
          "Accept": "application/json",
          "User-Agent": "Assistant-App/1.0"
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
    "openweather": {
      "name": "OpenWeather",
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
## Composants Techniques

### Service APIManager
```kotlin
class APIManager(private val context: Context) : ExecutableService {
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        return when (operation) {
            "call" -> callAPI(params, token)
            "test_connection" -> testAPI(params, token)
            "list" -> listAvailableAPIs()
            else -> OperationResult.error("Unknown operation: $operation")
        }
    }

    private suspend fun callAPI(params: JSONObject, token: CancellationToken): OperationResult {
        val apiName = params.getString("api_name")
        val apiConfig = getAPIConfig(apiName)
        val requestParams = params.getJSONObject("parameters")

        // Remplace placeholders dans URL template
        val finalUrl = replacePlaceholders(apiConfig.request_template.url, requestParams)

        // Exécute requête HTTP
        val response = httpClient.call(finalUrl, apiConfig.request_template.method)

        // Retourne réponse brute + mapping pour que l'IA puisse parser
        return OperationResult.success(mapOf(
            "response" to response,
            "mapping" to apiConfig.response_mapping
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
fun APIConfigScreen() {
    var apis by remember { mutableStateOf(loadAPIs()) }

    Column {
        UI.PageHeader(
            title = "APIs Externes",
            subtitle = "Configuration des sources de données automatiques"
        )

        apis.forEach { api ->
            UI.Card {
                APIConfigCard(
                    api = api,
                    onEdit = { editAPI(it) },
                    onTest = { testConnection(it) },
                    onToggle = { toggleAPI(it) }
                )
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            onClick = { showAddAPIDialog = true }
        )
    }
}
```

## ═══════════════════════════════════
## Extensibilité

### Ajout Nouvelle API
1. **Template prêt** : Structure JSON standard
2. **Test intégré** : Bouton "Tester connexion"
3. **Documentation** : Champs description obligatoires
4. **Validation** : Schéma JSON pour config API

### Types d'APIs Supportés
- **REST JSON** : GET, POST avec paramètres
- **Authentication** : API key, Bearer token
- **Rate limiting** : Respect automatique des limites
- **Error handling** : Retry et fallback configurables

### Cas d'Usage Prévus
- **Météo** : Météo France, OpenWeather, AccuWeather
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

---

*Le système API transforme l'application en hub de données automatisé, l'IA orchestrant la collecte et la mise à jour via les outils existants.*