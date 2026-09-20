# Dette constatée à l'audit post-refonte

**Origine** : audit architecture 2026-06-10/11.
**Périmètre** : tout ce qui a été constaté pendant l'audit et qui ne fait PAS partie de la refonte executions.

La partie A (alignement du pipeline TOOL_DATA) est implémentée et a été retirée : le code et les commits en sont le registre. Le critère qui la fermait — chaque exemple du L1 exécutable tel quel — vit désormais dans `docs/AI.md`. Reste la partie B.

---

# Dette constatée / choix discutables (à arbitrer, pas urgents)

Constats faits en passant pendant l'audit. Classés par taxe estimée sur le projet. Statut indiqué : **vérifié** (lu dans le code/docs) ou **soupçon** (à confirmer avant d'agir).

## B.1 JSON-string aux frontières, institutionnalisé — vérifié

`data` et `custom_fields` circulent comme strings JSON entre toutes les couches, re-parsés/re-stringifiés à chaque frontière. TOOLS.md documente même l'ambiguïté (« entity.data peut être String ou Map ») et prescrit du parsing défensif partout (« Patterns de Parsing Robuste »). C'est une rente de bugs : double-stringification du filtrage, `custom_fields` qui arrivait à l'IA en string échappée, `optString` qui avale un objet — tous de la même famille.

**Recommandation** : convention unique — les services parlent en objets ; la sérialisation n'existe qu'au bord DB. Chantier transversal, à faire par couche.

## B.2 Conventions de nommage gérées par avertissement — vérifié

CORE.md : « ATTENTION : chaque service utilise ses propres conventions » (`tool_instance_id` vs `toolInstanceId`, `tool_type` vs `tooltype`). Documenter un piège au lieu de l'unifier le normalise — et il a mordu (commentaire-rustine dans CommandExecutor, bug snake_case/camelCase du pipeline executions).

**Recommandation** : unifier (une seule convention pour les params de service), en une passe dédiée. Ingrat, fort rendement.

## B.3 Doc IA dans le système de strings — discutable par nature

Le prompt L1 vit dans `ai_prompt_chunks.xml`, traité comme de l'i18n alors que c'est un **contrat d'interface** — le seul du projet ni compilé ni testé. Conséquences : échappement bruyant, diffs illisibles, et les divergences doc↔code du pipeline TOOL_DATA installées sans bruit.

**Recommandation** : le re-test manuel à chaque modif du L1 est désormais une règle de `docs/AI.md` — c'est le minimum, et il repose sur la discipline. À terme, envisager un format dédié (markdown source → génération) avec exemples extraits et exécutables automatiquement.

## B.4 Renommer un custom field = perte de données — vérifié (design doc)

`custom-fields-migration.md` : un renommage est détecté comme `Removed + Added`, et `Removed → STRIP_FIELD` → renommer un champ (intention cosmétique) **supprime les valeurs historiques** (avec dialogue, mais destructif). `NameChanged → ERROR` interdit l'autre chemin. Aucun moyen sûr de renommer.

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
