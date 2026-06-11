# Chantiers post-refonte : alignement pipeline + dette constatée

**Origine** : audit architecture 2026-06-10/11 (même session que `SPECS_REFONTE_EXECUTIONS.md`).
**Périmètre** : tout ce qui a été constaté pendant l'audit et qui ne fait PAS partie de la refonte executions. Deux parties : un chantier concret et prioritaire (A), et une liste de dette/choix discutables à arbitrer (B).

---

# Partie A — Chantier « alignement pipeline TOOL_DATA »

Ces problèmes survivent à la refonte (leurs équivalents TOOL_EXECUTIONS meurent avec elle). Cause commune : la doc IA (`ai_prompt_chunks.xml`) est une *interface* écrite en XML que rien ne teste — chaque divergence doc↔code produit un échec **silencieux** côté IA.

## A.1 Objet `period: {start, end}` documenté mais totalement ignoré — GRAVE

**Symptôme** : la liste de paramètres TOOL_DATA (`ai_prompt_chunks.xml` ~l.252) et les Exemples 3 et 6 (~l.612-700) documentent un objet imbriqué `period: {start, end}`. Or `CommandTransformer.applyTemporalParameters()` (~l.328) ne lit que `period_start`/`period_end`/`startTime`/`endTime` **à la racine des params**. Un `period` imbriqué est ignoré sans erreur → **aucun filtre temporel, toutes les données retournées**.

**Scénario de risque** : l'Exemple 6 documente précisément ce pattern pour un workflow de suppression (« récupérer les IDs de la période, puis DELETE_DATA »). L'IA croit recevoir les IDs de janvier, reçoit les IDs de TOUT l'historique → **suppression de masse possible**.

**Incohérences internes de la doc** : la Partie E (~l.920-992) documente le bon format (`period_start`/`period_end` à la racine) — la doc se contredit. Et la liste de paramètres dit `start (number) : timestamp` alors que les exemples utilisent des strings ISO.

**Correctif** :
1. Doc : supprimer toute mention de l'objet `period` ; aligner la liste de paramètres et les Exemples 3 et 6 sur `period_start`/`period_end` (formats Partie E).
2. Code : dans la validation `AICommandProcessor`, **rejeter explicitement** un param `period` avec une erreur renvoyée à l'IA (le pattern a pu être appris ; règle projet : jamais d'ignorance silencieuse).

**Vérification** : requête avec `period` imbriqué → erreur explicite ; les 3 exemples de la Partie E fonctionnent tels quels.

## A.2 ISO 8601 annoncé pour les périodes, non supporté

**Symptôme** : Partie E, format 3 (~l.935) : « Timestamps ISO 8601 pour dates fixes ». En réalité : `period_start` en ISO → `periodStart.split("_")` + destructuring → exception → erreur cryptique (« Index out of bounds ») renvoyée à l'IA. Pire : un ISO passé en `startTime`/`endTime` traverse le transformer puis `params.optLong()` côté service → **0 silencieux** → plage temporelle fausse → résultats faux sans erreur.

**Correctif recommandé : implémenter le support ISO** (pas le retirer de la doc) — l'app a standardisé ISO 8601 sur toutes ses interfaces externes (chantier DATETIME, `DateTimeConverter`), la doc promet ce format à juste titre. Dans `applyTemporalParameters()` : si la valeur contient « T », parser via `DateTimeConverter.isoToTimestamp()` avec la timezone AppConfig ; si parsing impossible → **erreur explicite** (pas de fallback). Couvrir aussi le cas `startTime`/`endTime` non numérique côté service (erreur, pas optLong silencieux).

**Vérification** : les 3 formats documentés (relatif, NOW, ISO) fonctionnent ; ISO invalide → erreur lisible par l'IA.

## A.3 `offset` documenté, `page` implémenté

**Symptôme** : doc TOOL_DATA (~l.256) : « offset (number) : décalage pour pagination ». `CommandTransformer` ne transmet que `limit`/`page` (l.179-180) → `offset` ignoré silencieusement, pagination par défaut appliquée.

**Correctif** : doc → `page` (1-based, sémantique du service). Ne PAS ajouter le support `offset` : tous les services paginent par page/limit, pas de double convention.

## A.4 Messages d'erreur de validation hardcodés

**Symptôme** : `AICommandProcessor` l.46-58, erreurs TOOL_DATA (`fields` manquant/vide) hardcodées en anglais ; `CommandTransformer` l.67 et 77, erreurs hardcodées en français (« Type inconnu », « paramètres invalides ou manquants »). Double violation de la règle strings.

**Correctif** : strings `s.shared()` + `generateStringResources`. À faire en même temps que A.5b (c'est le même bloc de code).

## A.5 Grammaire des fields et forme des résultats

**a) `custom_fields` non parsé dans le prompt** : `CommandExecutor.formatResultData()` (l.937-963) re-parse `entry.data` (string JSON → objet) pour la lisibilité du prompt, mais **pas `entry.custom_fields`** → l'IA voit `data` en objet et `custom_fields` en string JSON échappée. L'Exemple 3 de la doc montre les deux en objets. Correctif : parser `custom_fields` au même endroit, même pattern. (Au passage : la forme de réponse documentée `"total": 42` ne correspond pas à la réalité — objet `pagination` ; corriger l'exemple.)

**b) Source unique pour la grammaire des fields** : le `SPECS_TOOL_DATA_PATTERN_MATCHING_REFACTOR.md` suspendu prévoyait la validation pattern matching des fields TOOL_DATA (root / `data.*` / `custom_fields.*`, rejet de `data` et `custom_fields` seuls). À implémenter ici, MAIS sans recréer le défaut d'origine (grammaire dupliquée en 4 endroits : validation, filtrage, doc, strings). **Un helper unique** (ex. objet `FieldPatternGrammar` dans core) utilisé par la validation (`AICommandProcessor`) ET le filtrage (`ToolDataService.filterEntryFields`) ; doc et strings d'erreur dérivées de cette source. Après ça, supprimer le SPECS suspendu.

## A.6 Hygiène (mineur, même passage)

- Logs DEBUG verbeux oubliés : dumps complets de `historicalSchemas` (`CommandExecutor` ~l.1221), previews SCHEMA_REQUIRED — à réduire.
- `com.assistant.core.utils.LogManager` en nom pleinement qualifié au lieu d'un import (plusieurs fichiers — trace de patchs successifs).

## A.7 Critère d'acceptation du chantier

**Chaque exemple de requête présent dans la doc L1 doit être exécutable tel quel et produire le comportement décrit.** Test manuel : une session CHAT qui exerce un par un les exemples de `ai_prompt_chunks.xml` (queries, périodes, pagination, fields). C'est le filet qui manquait : toutes les divergences ci-dessus auraient été détectées par ce simple critère.

---

# Partie B — Dette constatée / choix discutables (à arbitrer, pas urgents)

Constats faits en passant pendant l'audit. Classés par taxe estimée sur le projet. Statut indiqué : **vérifié** (lu dans le code/docs) ou **soupçon** (à confirmer avant d'agir).

## B.1 JSON-string aux frontières, institutionnalisé — vérifié

`data` et `custom_fields` circulent comme strings JSON entre toutes les couches, re-parsés/re-stringifiés à chaque frontière. TOOLS.md documente même l'ambiguïté (« entity.data peut être String ou Map ») et prescrit du parsing défensif partout (« Patterns de Parsing Robuste »). C'est une rente de bugs : double-stringification du filtrage, `custom_fields` non parsé dans les prompts (A.5a), `optString` qui avale un objet — tous de la même famille.

**Recommandation** : convention unique — les services parlent en objets ; la sérialisation n'existe qu'au bord DB. Chantier transversal, à faire par couche.

## B.2 Conventions de nommage gérées par avertissement — vérifié

CORE.md : « ATTENTION : chaque service utilise ses propres conventions » (`tool_instance_id` vs `toolInstanceId`, `tool_type` vs `tooltype`). Documenter un piège au lieu de l'unifier le normalise — et il a mordu (commentaire-rustine dans CommandExecutor, bug snake_case/camelCase du pipeline executions).

**Recommandation** : unifier (une seule convention pour les params de service), en une passe dédiée. Ingrat, fort rendement.

## B.3 Doc IA dans le système de strings — discutable par nature

Le prompt L1 vit dans `ai_prompt_chunks.xml`, traité comme de l'i18n alors que c'est un **contrat d'interface** — le seul du projet ni compilé ni testé. Conséquences : échappement bruyant, diffs illisibles, et toutes les divergences de la Partie A installées sans bruit.

**Recommandation** : à minima, rendre le critère A.7 récurrent (re-test à chaque modif de la doc L1). À terme, envisager un format dédié (markdown source → génération) avec exemples extraits et exécutables.

## B.4 Renommer un custom field = perte de données — vérifié (design doc)

`CUSTOM_FIELDS_MIGRATION.md` : un renommage est détecté comme `Removed + Added`, et `Removed → STRIP_FIELD` → renommer un champ (intention cosmétique) **supprime les valeurs historiques** (avec dialogue, mais destructif). `NameChanged → ERROR` interdit l'autre chemin. Aucun moyen sûr de renommer.

**Recommandation** : migration rename-aware (copie de clé `old_name → new_name` dans toutes les entrées). Simple, élimine un piège réel avant qu'il ne morde sur des données réelles.

## B.5 `verbalize()` synchrone forçant `runBlocking` — vérifié sur un service, soupçon ailleurs

L'interface `verbalize(operation, params, context): String` est synchrone ; pour résoudre noms d'outil/zone, le service fait `runBlocking { coordinator.processUserAction(...) }` (+ `Coordinator(context)` instancié à la volée). Appel bloquant imbriqué dans des contextes coroutine. Vu dans ToolExecutionService (qui meurt avec la refonte) — **vérifier les autres services** : si le pattern est répandu, passer l'interface en `suspend`.

## B.6 Compter en chargeant les lignes — vérifié localement, soupçon ailleurs

Pattern `dao.getByStatus(...).size` (3 chargements complets pour 3 entiers) au lieu de requêtes `COUNT`. Vu dans getStats executions (meurt avec la refonte) — vérifier si le réflexe existe ailleurs.

## B.7 Event sourcing : doc vs réalité — soupçon, à vérifier en priorité

DATA.md affirme « Event sourcing obligatoire pour modifications ». Dans tous les chemins d'écriture lus pendant l'audit : écritures directes via DAO, verbalisation par templates, DataChangeNotifier — **aucun event store croisé**. Soit il vit dans une couche non lue, soit la doc décrit une aspiration comme un acquis. Dix minutes de vérification ; si aspiration → corriger DATA.md (une doc d'architecture qui sur-promet est exactement ce qui a piégé le pipeline).

## B.8 Mentions rapides

- `limit` par défaut `Int.MAX_VALUE` → `(page-1)*limit` déborde dès page 2 (ToolDataService/getEntries et équivalents).
- Filtres de requête mutuellement exclusifs (status OU période OU template) là où la doc suggère qu'ils se combinent.
- `validateConfig`/`validateData` par défaut `false` : l'IA modifie sans validation par défaut. Posture probablement délibérée — à re-choisir consciemment un jour, pas par défaut hérité.
- `strings_generated.xml` (fichier généré) versionné dans le repo.
