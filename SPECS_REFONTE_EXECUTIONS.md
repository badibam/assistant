# Refonte : Suppression du plan tool_executions

**Statut** : Design validé, prêt pour implémentation
**Origine** : Session de design 2026-06-10/11 (audit architecture + archéologie git)
**Exécutant prévu** : Claude Sonnet — ce doc contient TOUT le contexte nécessaire, ne pas improviser au-delà

---

## 1. Décision en une phrase

Les occurrences (messages reçus, résultats de calculs) deviennent des **entrées tool_data ordinaires**, les définitions (template de message, formule) deviennent la **config de l'instance**, et la table `tool_executions` ainsi que toute sa machinerie sont **supprimées**.

---

## 2. Le principe architectural final

> **Config** = ce que l'outil *est* (sa définition : template, formule, planning).
> **tool_data** = tout ce que l'outil *enregistre ou produit* (saisies utilisateur, occurrences, résultats).
> **Il n'y a pas de troisième plan de données.** L'audit purement technique (échecs, durées) relève du système de logs existant.

Corollaires :

- **Une instance = un concept**, strictement : 1 message planifié = 1 instance, 1 calcul = 1 instance. (C'est un retour à la doctrine originale de TOOLS.md : « Message : 1 message/rappel planifié » — l'implémentation avait dérivé vers des instances multi-templates.)
- **Une seule population par collection** : le tool_data d'une instance contient un seul type d'objet, validé par un seul schéma data.
- **Critère pour les futurs tooltypes** : si les définitions d'un outil sont nombreuses et éditées fréquemment, c'est qu'elles sont en réalité son *produit* — elles vont alors en tool_data (et ses occurrences éventuelles aussi, discriminées autrement). Sinon, définition → config.
- **Ne jamais exposer un choix de représentation à l'IA** : pas de variantes brut/formaté dans la grammaire de requête. Le système décide, l'IA reçoit une seule forme.

---

## 3. Cheminement de pensée (à conserver — c'est la justification de tout le reste)

Cette section retrace les insights de la session de design. Le but : que ce débat ne soit jamais rejoué faute de trace (c'est arrivé une fois, voir 3.3).

### 3.1 Le symptôme initial

Sentiment diffus que les chantiers récents (tool_executions, snapshots, custom fields formatés, field filtering IA) « ne s'intégraient pas naturellement ». L'audit a confirmé : la chaîne doc IA → validation → transformation → service est désalignée (noms de champs snake_case dans la doc vs camelCase dans le service → champs silencieusement omis ; objet `period` documenté mais ignoré par le code ; `offset` documenté, `page` implémenté ; ISO 8601 documenté, non supporté). Et surtout : **tool_executions a dupliqué en miroir toute la machinerie de tool_data** (field filtering, vérification de schémas, doc, validation) — ~130 lignes dupliquées rien que pour `checkRequiredDataSchemas`/`checkRequiredExecutionSchemas`.

### 3.2 L'archéologie (sources : commits + docs supprimés, récupérables via `git show`)

- **Avant oct 2025** : les exécutions vivaient en **array JSON à l'intérieur de chaque entrée message** (tool_data). Problèmes documentés dans `EXECUTIONS_IMPLEMENTATION.md` (commit `ef4b43a`) : croissance illimitée, coût tokens IA, non-réutilisable, non-requêtable.
- **Oct 2025** (`ef4b43a`) : création de la table `tool_executions` pour les en extraire. Choix raisonnable face au problème posé.
- **Nov 2025** (`0216628`) : snapshots « self-contained » — valeurs brutes + `custom_fields_metadata` archivées (définitions figées avec la donnée).
- **Déc 2025** (`ab357ba`) : remplacement par des **valeurs formatées** ("Élevé", "5/10") — plus simple, lisible, moins de données. Rationale du commit : « tool_data remains unchanged (raw values for AI analysis) ».
- **Même jour** (`c0f58f9`) : field filtering TOOL_EXECUTIONS pour l'IA — contredisant la prémisse du commit précédent (l'IA requête bien les exécutions).

### 3.3 Les insights décisifs, dans l'ordre où ils sont apparus

1. **La duplication en miroir n'était pas un accident d'implémentation** : c'était la facture d'un choix de structure. Les occurrences de Messages ont une **double nature** — journal technique (envoyé à telle heure, succès/échec) ET produit consultable (l'inbox, lu/archivé). En les stockant dans le plan d'audit, il a fallu donner au plan d'audit toutes les capacités du plan de données.

2. **Les produits durables vont dans tool_data** (insight déclenché par l'exemple du futur tooltype Calcul : « somme des calories de la veille, chaque jour » → l'historique des résultats est une série temporelle exploitable). Si les résultats vivaient dans les exécutions, il faudrait y dupliquer stats, sources de Graphique, requêtes IA… L'alternative : le calcul **écrit son résultat en tool_data** (timestamp = la période calculée, PAS l'heure d'exécution), et la chaîne du README (SUIVI → CALCUL → GRAPHIQUE) fonctionne gratuitement.

3. **L'inversion finale** : si les occurrences sont le produit, ce sont *elles* qui méritent le plan de données (périodes, stats, pagination, IA). Les templates, eux, sont peu nombreux et rarement édités → config. Avec 1 définition = 1 instance, l'édition passe par le CRUD config existant (`tools.update` partiel).

4. **Plus aucun consommateur pour tool_executions** : occurrences Messages → tool_data ; résultats Calcul → tool_data ; échecs techniques → système de logs in-app (existant, avec filtres et purge). Les futurs Alertes/Objectifs suivent le même raisonnement (leurs occurrences sont des produits consultables).

5. **Le débat snapshot se dissout entièrement.** Le « contenu figé » n'a jamais nécessité de mécanisme : créer une entrée = copier les valeurs du moment (c'est ce que toute création d'entrée fait). Éditer le template ensuite n'affecte que les occurrences futures.

6. **La distinction figé/migré était une décision délibérée** (pas un accident) : `CUSTOM_FIELDS_MIGRATION.md` définissait deux régimes — Phase 1 : exécutions self-contained (immunisées contre les changements de config), Phase 2 : tool_data co-évolue avec la config (migration). La refonte **abandonne consciemment le régime figé** : les occurrences adoptent le contrat tool_data standard. Conséquence assumée : supprimer un custom field d'une définition strippe ses valeurs de l'historique des occurrences (avec dialogue d'avertissement, comme partout). C'est le même contrat que les trackings ; l'inbox n'est pas plus sacrée que les pesées. **Ne PAS inventer de régime hybride** (« tool_data exempté de migration ») — ce serait recréer la distinction sous une autre forme.

7. **Pourquoi les custom fields étaient traités différemment des champs standard dans les snapshots** : la définition d'un champ standard vit *dans le code* (stable, versionnée par JsonTransformers) ; celle d'un custom field vit *dans la config* (mutable, supprimable). Une valeur brute n'est interprétable que si son interprète survit. Trois stratégies possibles : archiver la définition avec la donnée (nov), cuire l'interprétation dans la valeur (déc), ou **forcer donnée et définition à co-évoluer** (= le système de migration de tool_data). La refonte adopte la troisième — qui gouverne déjà tout le reste de l'app.

8. **Leçon de méthode** (pour les futures décisions) : le choix de décembre était bon *sous une condition jamais formulée* (« les produits durables ne vivent jamais dans les exécutions »). Faute de trace écrite du raisonnement, le débat a dû être entièrement re-déroulé six mois plus tard. D'où la richesse de ce document.

---

## 4. Architecture cible

### 4.1 Messages (refonte)

**Config de l'instance** (= la définition du message) :
- Champs généraux standard (name = titre du message, description, icône, management, display_mode, validateConfig/validateData, always_send)
- `content` (texte du message)
- `priority` (default|high|low)
- `schedule` (pattern de récurrence — déplacé depuis entry.data)
- `external_notifications` (boolean, existant)
- `custom_fields` (définitions, mécanisme standard) + **valeurs du template** pour ces champs (à stamper sur chaque occurrence — voir 4.4)

**tool_data de l'instance** (= l'inbox de CE message) — une entrée par occurrence :
- `timestamp` = moment de l'envoi
- `data` : `{ "title": <copié>, "content": <copié>, "priority": <copié>, "read": false, "archived": false, "notification_sent": true|false, "scheduled_time": <ISO, optionnel>, "triggered_by": "SCHEDULE"|"MANUAL" }`
- `custom_fields` : valeurs **brutes** copiées du template au moment de l'envoi (validées par le schéma data enrichi, couvertes par la migration — régime standard)

**Création par le scheduler** : `tool_data.create` standard (validation par schéma comprise — les occurrences passent par la validation, contrairement aux exécutions actuelles qui ne validaient rien).

**Lu / archivé** : `tool_data.update` standard sur l'entrée.

**nextExecutionTime** : NE PAS stocker d'état mutable de scheduling dans la config (éviter un `tools.update` à chaque tir). **Recommandation : le calculer** — prochaine exécution = f(pattern de schedule, timestamp de la dernière entrée tool_data). État dérivé, zéro écriture de config par le scheduler. Si un cas ne le permet pas (premier tir, pattern complexe), le signaler plutôt que stocker silencieusement.

**UI MessagesScreen** : l'historique affiché = les entrées tool_data de l'instance (plus de lecture de tool_executions ni de `custom_fields_metadata`). L'édition du message = écran de config. Il n'y a plus d'inbox multi-messages au niveau instance ; si une vue agrégée « tous les messages reçus » devient nécessaire, elle se construira au niveau zone (hors périmètre).

### 4.2 Calcul (futur tooltype — HORS périmètre de cette refonte)

Mentionné ici uniquement comme validation du pattern : config = formule + sources + planning ; tool_data = résultats (timestamp = période calculée) ; échec de calcul = log technique. Première implémentation = premier test grandeur nature du principe.

### 4.3 tool_executions : suppression totale

Voir périmètre de démolition (§6).

### 4.4 Point de design à trancher à l'implémentation (signalé, pas bloquant)

Les **valeurs** des custom fields du template vivent dans la config (la définition ET la valeur à stamper). C'est inhabituel (ailleurs, les valeurs vivent dans data) mais cohérent : le template entier est de la config. Implémentation suggérée : objet `custom_field_values` dans la config, validé contre les définitions. Si cela crée une friction réelle avec le mécanisme d'enrichissement des schémas, remonter le problème plutôt que de contourner.

### 4.5 Exécution = opération d'instance

L'unité exécutable est **l'instance** (la définition étant sa config, il n'y a rien d'autre à viser). Conséquences :

- **"Exécuter" = opération de service tooltype standard** : `messages.execute` avec `tool_instance_id` — pattern resource.operation existant, découvert via ToolTypeManager, zéro infrastructure core. Remplace `supportsExecutions()` : un tooltype « actif » expose `execute` + éventuellement `getScheduler()`.
- **Déclenchement manuel et planifié = même chemin de code**, `triggered_by` en simple paramètre.
- **Symétrie UI/IA gratuite** : l'IA crée l'instance (`tools.create`), l'exécute (`{tooltype}.execute`), lit les résultats (`TOOL_DATA`) — boucle d'orchestration complète sans commande nouvelle. La hiérarchie de validation s'applique à `execute` au niveau tool.
- **`templateDataId` disparaît** : le lien occurrence→définition est `toolInstanceId`, déjà natif sur chaque entrée.
- **Le scheduler perd un niveau de boucle** : itérer les instances, lire la config — plus d'itération des entrées-templates.
- **État actif/suspendu dans la config** (ex: `schedule.enabled`) : suspendre = update de config, visible et event-sourcé.
- **Taxonomie émergente** : tooltypes *passifs* (Tracking, Journal, Note — l'utilisateur écrit dans tool_data) vs *actifs/exécutables* (Messages, Calcul, futurs Alertes/Objectifs — le système écrit dans tool_data, piloté par la config).

### 4.6 Validation du design : occurrences à cycle de vie (futur Objectif)

Cas testé pendant le design : un Objectif récurrent type « journée-type » — *exécuter* = activer pour la journée, l'occurrence *vit* (l'utilisateur coche des items via des updates ordinaires), puis on *valide* en fin de journée. Verdict : rentre nativement dans le design.

- Définition (sous-objectifs, critères, récurrence) → config ; exécution → entrée tool_data `status: "active"` avec structure copiée ; vie → `tool_data.update` standard (validés, event-sourcés) ; validation → update final `status: "completed"` + score, calculable via le pattern `enrichData()` existant ; historique des journées → série temporelle exploitable (stats, graphiques, IA).
- **Insight généralisant** : une occurrence n'est pas forcément un événement instantané (message envoyé) — c'est parfois un **processus** (créé → actif → validé). tool_data porte les deux nativement ; seul le schéma data du tooltype change. L'ancien plan tool_executions (journal quasi-immuable, sans validation) n'aurait pas pu porter une occurrence vivante.
- Vigilances pour le futur tooltype (pas maintenant) : idempotence par période (ne pas créer deux occurrences le même jour — ancre = existence d'une entrée pour la période courante, même mécanisme que le calcul de prochaine exécution) ; clôture des occurrences non validées par le scheduler (`status: "expired"`).

---

## 5. Migration des données existantes

Migration Room (nouvelle version DB) + JsonTransformers. **Ne pas modifier les migrations historiques existantes.**

1. **Éclater chaque instance Messages multi-templates** : pour chaque entrée template (tool_data actuel d'une instance Messages) → créer une nouvelle instance Messages (config = name/content/priority/schedule du template + custom field values), dans la même zone.
2. **Convertir les exécutions** : chaque ligne `tool_executions` → une entrée tool_data de la nouvelle instance correspondante (`templateDataId` fait le lien). Mapping : `executionTime` → timestamp ; `snapshotData.title/content/priority` → data ; `executionResult.read/archived/notification_sent` → data ; `triggeredBy`, `scheduledTime` → data. **Custom fields des snapshots : valeurs actuellement FORMATÉES (strings)** — les reconvertir en brut est impossible (information détruite par le formatage de déc 2025). Décision : les copier telles quelles dans `custom_fields` de l'entrée (strings), en acceptant l'impureté sur l'historique pré-refonte. Ne PAS construire de mécanisme de dé-formatage.
3. **Supprimer la table** `tool_executions` (DROP) et l'instance d'origine multi-templates après éclatement.
4. **Nettoyer les configs** : retirer `execution_schema_id` des configs existantes (JsonTransformer).
5. Tester le backup/restore après migration (BackupService exporte/importe tool_executions actuellement — à retirer).

---

## 6. Périmètre de démolition (liste exhaustive)

Code :
- `ToolExecutionEntity`, `BaseToolExecutionDao`, table + index
- `ToolExecutionService` + entrée dans `ServiceRegistry`/`ServiceFactory`
- `BaseSchemas.getBaseExecutionSchema()` + champ `execution_schema_id` du schéma config de base
- `MessageToolType.createMessagesExecutionSchema()` + `messages_execution` dans `getAllSchemaIds()`
- `supportsExecutions()` dans `ToolTypeContract` + tous ses usages
- `FieldMetadataSource` (sealed class — `SnapshotBased` n'a plus de raison d'être ; si `ConfigBased` reste utilisé, simplifier en conséquence) + lecture de `custom_fields_metadata` dans `MessagesScreen` (~l.839)
- `MessageScheduler.formatCustomFields()` + création d'exécution → remplacer par `tool_data.create`

Pipeline IA :
- Type `TOOL_EXECUTIONS` : enum AIMessageSchemas, bloc de validation pattern matching dans `AICommandProcessor` (l.60-121), `transformToolExecutionsCommand` dans `CommandTransformer`
- `checkRequiredExecutionSchemas` dans `CommandExecutor` (+ simplifier `getSchemaDeduplicationKey` : plus de patterns `_execution`)
- Contexte `EXECUTIONS` : `PointerContext`, `ZoneScopeSelector`, `NavigationConfig`, `EnrichmentProcessor` (bloc EXECUTIONS + `resolveExecutionSchemaId`)
- Doc IA : section TOOL_EXECUTIONS de `ai_prompt_chunks.xml`, placeholder `{TOOLTYPES_WITH_EXECUTIONS}`
- Strings : `ai_validation_tool_executions_*`, `schema_execution_*`, `tools_*_execution_schema_id`, verbalize executions, etc. (chercher exhaustivement, régénérer)

Docs projet : mettre à jour TOOLS.md (retirer supportsExecutions, graver le principe §2), DATA.md (PointerContext sans EXECUTIONS), AI.md si mention, README.md (description Messages).

Fichiers racine : supprimer `PLAN_TOOL_EXECUTIONS_FIELD_FILTERING.md` (obsolète). `SPECS_TOOL_DATA_PATTERN_MATCHING_REFACTOR.md` : suspendu, à réévaluer après la refonte (la validation `fields` de TOOL_DATA reste d'actualité mais devra être repensée dans le pipeline nettoyé).

**Rappel règle projet : aucun code legacy, aucun fallback silencieux.** Pas de « backward compatibility mode » résiduel.

---

## 7. Hors périmètre

Les problèmes du pipeline TOOL_DATA constatés au même audit (objet `period` ignoré, ISO non supporté, `offset` vs `page`, strings hardcodées, grammaire des fields, etc.) ainsi que la dette technique relevée en passant sont documentés séparément dans **`CHANTIERS_POST_REFONTE.md`** — à traiter après cette refonte. Ne PAS les corriger pendant la refonte (périmètres distincts), sauf si une démolition les fait disparaître naturellement.

---

## 8. Ordre d'implémentation suggéré

1. Refonte MessageToolType : nouveau schéma config (content/priority/schedule/custom values), nouveau schéma data (occurrences), suppression schéma execution
2. MessageScheduler : lire config, créer occurrences via `tool_data.create`, calcul de la prochaine exécution depuis la dernière occurrence
3. UI Messages : ConfigScreen (édition du message), MessagesScreen (inbox = tool_data)
4. Migration DB + JsonTransformers (§5) — tester sur données réelles + backup/restore
5. Démolition (§6) — dans cet ordre : pipeline IA, puis service/entité/table, puis strings (`generateStringResources`), puis docs
6. Compilation `compileDebugKotlin` à chaque étape ; vérifier zéro référence restante (`grep -r "tool_executions\|ToolExecution\|supportsExecutions\|EXECUTIONS"`)

---

## 9. Pièges connus pour l'implémentation

- Conventions de nommage incohérentes entre services : `tools.*` → `tool_instance_id`, `tool_data.*` → `toolInstanceId` ; le champ tooltype s'appelle `tool_type` dans les retours de `tools.get`. Vérifier chaque accès, ne pas deviner.
- Strings : TOUT via `s.shared()`/`s.tool()`, génération gradle obligatoire, placeholders `%1$s`.
- La validation des occurrences passe désormais par SchemaValidator (les exécutions actuelles ne validaient rien — ne pas reproduire).
- Timestamps : interface externe en ISO 8601 (DateTimeConverter), stockage en Long — suivre le pattern existant de ToolDataService.
- Ne pas toucher aux migrations Room historiques ; nouvelle migration uniquement.
- Si un choix de design imprévu apparaît, **s'arrêter et demander** plutôt qu'improviser un mécanisme (règle projet : jamais de fallback sans validation explicite).
