# Dette constatée à l'audit post-refonte

**Origine** : audit architecture 2026-06-10/11.
**Périmètre** : tout ce qui a été constaté pendant l'audit et qui ne fait PAS partie de la refonte executions.

La partie A (alignement du pipeline TOOL_DATA) est implémentée et a été retirée : le code et les commits en sont le registre. Le critère qui la fermait — chaque exemple du L1 exécutable tel quel — vit désormais dans `docs/AI.md`.

---

# Dette constatée / choix discutables (à arbitrer, pas urgents)

Constats faits en passant pendant l'audit. Classés par taxe estimée sur le projet. Ce qui restait à l'état de soupçon a été vérifié ; ce qui a été traité ou s'est révélé sans objet est sorti d'ici, les commits en étant le registre.

## B.1 JSON-string aux frontières, institutionnalisé — vérifié

`data` et `custom_fields` circulent comme strings JSON entre toutes les couches, re-parsés/re-stringifiés à chaque frontière. TOOLS.md documente même l'ambiguïté (« entity.data peut être String ou Map ») et prescrit du parsing défensif partout (« Patterns de Parsing Robuste »). C'est une rente de bugs : double-stringification du filtrage, `custom_fields` qui arrivait à l'IA en string échappée, `optString` qui avale un objet — tous de la même famille.

**Recommandation** : convention unique — les services parlent en objets ; la sérialisation n'existe qu'au bord DB. Chantier transversal, à faire par couche.

## B.2 Conventions de nommage gérées par avertissement — vérifié

CORE.md : « ATTENTION : chaque service utilise ses propres conventions » (`tool_instance_id` vs `toolInstanceId`, `tool_type` vs `tooltype`). Documenter un piège au lieu de l'unifier le normalise — et il a mordu (commentaire-rustine dans CommandExecutor, bug snake_case/camelCase du pipeline executions).

**Recommandation** : unifier (une seule convention pour les params de service), en une passe dédiée. Ingrat, fort rendement.

## B.3 Doc IA dans le système de strings — discutable par nature

Le prompt L1 vit dans `ai_prompt_chunks.xml`, traité comme de l'i18n alors que c'est un **contrat d'interface** — le seul du projet ni compilé ni testé. Conséquences : échappement bruyant, diffs illisibles, et les divergences doc↔code du pipeline TOOL_DATA installées sans bruit.

**Recommandation** : le re-test manuel à chaque modif du L1 est désormais une règle de `docs/AI.md` — c'est le minimum, et il repose sur la discipline. À terme, envisager un format dédié (markdown source → génération) avec exemples extraits et exécutables automatiquement.

## B.5 `verbalize()` synchrone forçant `runBlocking` — vérifié, répandu

`ExecutableService.verbalize(operation, params, context): String` est synchrone ; pour résoudre des noms d'outil ou de zone, le service fait `runBlocking { coordinator.processUserAction(...) }`, avec un `Coordinator(context)` instancié à la volée. Appel bloquant imbriqué dans des contextes coroutine.

Quatre sites, dans trois services qui tous survivent à la refonte : `ToolDataService.kt:1024`, `ToolInstanceService.kt:487` et `:737`, `ZoneService.kt:313`. Le pattern est donc bien installé, pas isolé au service disparu.

**Recommandation** : passer `verbalize` en `suspend` dans l'interface, et retirer les quatre `runBlocking`.

## B.8 Mentions rapides

- Filtres de requête mutuellement exclusifs (status OU période OU template) là où la doc suggère qu'ils se combinent.
- `validateConfig`/`validateData` par défaut `false` : l'IA modifie sans validation par défaut. Posture probablement délibérée — à re-choisir consciemment un jour, pas par défaut hérité.
