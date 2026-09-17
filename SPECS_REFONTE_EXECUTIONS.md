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
- **Une seule population par collection** : le tool_data d'une instance contient un seul type d'objet, validé par un seul schéma data. Un objet à cycle de vie (l'occurrence Messages, `pending` → `sent`/`expired`) reste une seule population : c'est un schéma unique dont les exigences dépendent de l'état, pas deux populations cohabitant. L'invariant visait la cohabitation de types distincts, pas les états d'un même type. *(Précisé le 2026-09-17, cf. §4.4.)*
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

**Amendé le 2026-09-17** (raisonnement complet en §4.4). La version initiale supposait que chaque occurrence était une copie conforme du modèle ; l'usage réel a montré le contraire.

**Ce qu'est une instance Messages** : un modèle de notification — sa part invariante, sa récurrence, ses réglages de canal. Les envois successifs sont ses occurrences, et chacune porte une part propre, écrite au jour le jour par l'IA ou par l'utilisateur.

**Config de l'instance** (= le modèle) :
- Champs généraux standard (name, description, icône, management, display_mode, validateConfig/validateData, always_send)
- `enabled` — interrupteur du modèle. Suspendu, plus rien de cette instance ne part, occurrence posée à la main comprise. Il vit **à la racine de la config** et non dans `schedule` : il couvre tout ce que l'instance doit, et il existe même sans récurrence. Le champ `enabled` propre à `ScheduleConfig` est retiré du schéma de récurrence embarqué pour qu'il n'y ait jamais deux interrupteurs dont un ignoré. *(Tranché le 2026-09-18.)*
- `common_title` (facultatif) — titre commun affiché sur chaque notification. Distinct de `name` : `name` sert à se repérer dans la zone, `common_title` est ce qu'on lit sur l'écran verrouillé ; rien n'oblige les deux à coïncider. Absent, il ne contribue rien.
- `common_content` (facultatif) — corps commun à chaque notification. Sans lui, un rappel qui ne varie jamais devrait faire tenir tout son texte dans un titre de 60 caractères. *(Ajouté le 2026-09-18.)*
- `priority` (default|high|low) — caractéristique du canal, PAS de l'occurrence. Un seul domicile (voir §4.4).
- `external_notifications` (boolean, existant)
- `schedule` (ScheduleConfig, déplacé depuis entry.data) — génère les occurrences. Ne porte plus d'interrupteur : voir `enabled` ci-dessus
- `creation_horizon_days` — combien de jours d'occurrences le scheduler crée à l'avance (défaut 2). Exprimé en temps et non en nombre : selon le motif de récurrence, « 5 occurrences » vaut un jour ou six mois.
- `validity_window_minutes` — au-delà de ce retard, une occurrence non partie passe `expired` au lieu d'être envoyée. Le seuil dépend du message (« Attention du matin » à 15h n'a aucun sens, « pense à boire » à 15h en a encore), donc il est réglable par instance et non constant dans le code.
- `custom_fields` (définitions, mécanisme standard)

**tool_data de l'instance** (= les envois de CE message) — une entrée par occurrence, quatre états :
- `pending` : l'occurrence existe, elle n'est pas partie. Créée à l'avance par le scheduler. Elle ne porte QUE sa part propre — `title` (titre du jour, facultatif), `content`, `custom_fields` (valeurs brutes). Se remplit par `tool_data.update` ordinaire.
- `sent` : l'occurrence est partie. Le scheduler y a copié la part commune (`common_title`, `priority`) et le résultat (`notification_sent`, `read`, `archived`, `triggered_by`).

**Le `timestamp` de l'entrée est son heure prévue**, pas son heure de création. *(Tranché le 2026-09-17 en attaquant l'étape 2.)* Le temps est le seul axe d'ordre dont dispose `tool_data` : horodater une occurrence en attente à sa création les empilerait toutes au même instant et brouillerait l'inbox. Le moment où l'entrée a réellement été écrite vit dans `updated_at`, ce qui fait d'un champ `scheduled_time` séparé un pur doublon — il n'existe donc pas. Pour un envoi manuel immédiat, prévu et effectif coïncident.
- `expired` : l'heure est passée au-delà de `validity_window_minutes` sans que l'app tourne. Jamais envoyée, conservée comme trace.
- `cancelled` : l'heure est arrivée alors que le modèle était désactivé. N'avait pas à partir. Distinct d'`expired` : l'un est une décision, l'autre un raté — les confondre ferait passer une suspension délibérée pour un message manqué, et rien ne permettrait de les démêler après coup.

**Désactiver ne cascade pas, et suspend tout le modèle.** `enabled = false` est une simple modification de config, elle ne touche à aucune occurrence. Ce qui change est ce que fait le scheduler : il cesse de créer **et** de supprimer (l'ensemble en attente se vide de lui-même) et, quand l'heure d'une occurrence en attente arrive, il la passe `cancelled` au lieu de l'envoyer. L'interrupteur couvre **tout ce que l'instance doit**, y compris une occurrence posée à la main — ce n'est pas l'interrupteur de la seule récurrence. *(Tranché le 2026-09-18.)* Réactiver avant que leur heure soit passée les fait partir normalement, avec le contenu que l'IA y avait écrit. Suspendre ne doit pas être destructif, sinon on hésite à s'en servir et on finit par supprimer l'instance — ce qui est pire.

**Changer la récurrence — ou la retirer — réconcilie, sans cas particulier.** Le scheduler compare en permanence l'ensemble des occurrences `pending` à l'ensemble attendu dans la fenêtre `creation_horizon_days` : il supprime celles qui ne correspondent plus à aucun créneau, crée celles qui manquent, et ne touche pas aux autres. Modifier un horaire ne déclenche donc pas un traitement dédié, seulement plus de travail à la réconciliation suivante. L'UI de config calcule et annonce les suppressions avant de valider (même geste que le dialogue de migration des champs personnalisés). Ne PAS tenter de faire correspondre les anciennes occurrences aux nouveaux créneaux : toute règle de correspondance (par ordre, par index) devine, et deviner est interdit ici. **Retirer entièrement la récurrence suit ce chemin-là et non celui de la désactivation** : plus rien n'est attendu, donc tout ce que la récurrence avait engendré est orphelin et supprimé. *(Tranché le 2026-09-18.)*

**Envoi au coup par coup** : trois chemins, aucun mécanisme supplémentaire. `messages.execute` sur l'instance crée une occurrence et l'envoie immédiatement (`triggered_by: "MANUAL"`, cf. §4.5). Créer une occurrence `pending` avec son heure et son contenu donne un envoi ponctuel programmé. Et une instance dont `schedule` est vide ne fait jamais rien toute seule : c'est un **pur canal de notification**, alimenté uniquement à la demande par l'utilisateur ou l'IA. Ce dernier cas était l'une des architectures alternatives envisagées pendant la séance ; il survit comme un réglage de l'instance, pas comme un autre design — le même tooltype porte un rappel récurrent et un canal piloté par l'IA.

**Composition** : la notification joint `common_title` et le titre du jour pour son titre, `common_content` et le contenu du jour pour son corps. Une partie absente ne contribue rien — c'est de la concaténation, jamais une valeur de repli qui prend la main. Un rappel dont le texte ne varie jamais n'a donc rien à faire écrire par envoi : sa seule part commune part telle quelle. Si **rien** n'existe nulle part — modèle sans texte et occurrence jamais remplie — rien ne part, et l'occurrence reste `pending` jusqu'à ce que sa fenêtre de validité l'expire.

**Copie de la part commune : à l'envoi, jamais à la création.** Une occurrence en attente est une intention, pas un événement : il n'existe donc à aucun moment deux exemplaires concurrents de la part commune. Corollaire gratuit — modifier le modèle affecte tout ce qui n'est pas encore parti, comportement évident qui ne demande aucune règle à expliquer. L'insight §3.3 n°5 (« créer une entrée = copier les valeurs du moment ») tient toujours ; le moment est celui de l'envoi.

**Le schéma data est conditionnel à l'état** : la part commune est exigée à `sent`, absente à `pending`. Sans ce marqueur explicite, on aurait des entrées à moitié vides sans moyen de les distinguer des complètes — exactement le genre d'ambiguïté que l'audit reproche au reste du pipeline.

**nextExecutionTime** : NE PAS stocker d'état mutable de scheduling dans la config. L'ancre est l'existence des occurrences elles-mêmes — le scheduler crée celles qui manquent dans la fenêtre `creation_horizon_days`, ce qui rend l'opération idempotente par période sans compteur ni écriture de config.

**UI MessagesScreen** : les `sent`/`expired` forment l'historique, les `pending` s'affichent à part (ce qui va partir). L'édition du modèle passe par l'écran de config. Plus d'inbox multi-messages au niveau instance ; une vue agrégée « tous les messages reçus » se construirait au niveau zone (hors périmètre).

### 4.2 Calcul (futur tooltype — HORS périmètre de cette refonte)

Mentionné ici uniquement comme validation du pattern : config = formule + sources + planning ; tool_data = résultats (timestamp = période calculée) ; échec de calcul = log technique. Première implémentation = premier test grandeur nature du principe.

### 4.3 tool_executions : suppression totale

Voir périmètre de démolition (§6).

### 4.4 Amendement du 2026-09-17 : les occurrences portent une part propre

Origine : session de reprise, question soulevée en attaquant l'étape 1. Consigné ici pour la même raison que la §3.3 — ce débat a déjà dû être re-déroulé une fois faute de trace.

**Le point aveugle de la version initiale** : elle écrivait partout que l'occurrence est une copie conforme du modèle (§4.1 d'origine, §3.3 n°5). La question « et si le contenu variait d'un envoi à l'autre ? » n'apparaissait nulle part dans le document. Elle n'avait pas été écartée, elle n'avait pas été posée.

**L'usage réel qui l'a révélée** : le montage existant = 5 entrées-modèles associées chacune à un horaire de la journée, dont l'IA réécrit les champs chaque jour ; le contenu qui part est donc choisi le jour même. Défaut de ce montage : l'écriture du jour écrase celle de la veille, il ne reste aucun historique de ce qui est réellement parti.

**Décision** : une instance = un modèle (un *type* de notification) ; une occurrence = un envoi avec sa part propre. Les deux parts se **composent**, elles ne s'écrasent pas — il n'y a donc aucun arbitrage « qui gagne » à écrire nulle part.

**Ce que ça gagne au passage** : l'historique réel de ce qui est parti, sans que ce soit un objectif. Et la symétrie IA/humain de §4.5 sans commande nouvelle — l'IA lit les occurrences en attente (`tool_data.get`) et les remplit (`tool_data.update`).

**Ce que ça coûte** : l'état « en attente », et ses trois corollaires — ne pas créer deux occurrences pour la même période, clore celles qui n'ont jamais servi, distinguer les deux populations à l'écran. Coût accepté parce qu'il est dû de toute façon : pouvoir programmer à l'avance une notification au contenu propre est un besoin réel, et il fait exister l'état en attente quelle que soit l'option retenue par ailleurs.

**Options écartées, avec leur raison** (ne pas les rejouer sans raison neuve) :
- Modèle à contenu figé, occurrences toutes identiques (version initiale) : ne couvre pas l'usage réel.
- Contenu du modèle servant de défaut, écrasable par l'occurrence : paire défaut/écrasement, donc un arbitrage implicite à trancher partout, et impossible de distinguer « l'IA n'avait rien à dire » de « l'IA a échoué ».
- Contenu par défaut pour les occurrences non remplies : même raison. Règle retenue à la place — une occurrence sans part du jour part avec sa seule part commune si elle en a une, et ne part pas du tout sinon.
- Récurrence portée par l'occurrence qui se re-sème à chaque envoi : laisse le modèle sans domicile, donc la part commune serait recopiée partout et son édition ne se propagerait plus.
- Priorité présente en config ET sur l'occurrence : deux domiciles = écrasement. Retenu — config seule, copiée à l'envoi. Ré-ouvrable si l'usage prouve qu'une urgence varie au jour le jour ; ne pas la dédoubler « au cas où ».

**La question d'origine de cette section se dissout.** Elle demandait où valider les valeurs de champs personnalisés du modèle, stockées en config à côté de leurs propres définitions — friction réelle, puisque le mécanisme d'enrichissement va chercher les définitions en base par identifiant d'instance, ce qui est circulaire pour une config et impossible à la création. Sous le nouveau modèle ces valeurs varient par occurrence : elles vivent donc dans `tool_data` et passent par l'enrichissement standard. Plus de `custom_field_values` en config, plus de friction. (Elle reviendrait si un champ personnalisé devait avoir une valeur commune à tous les envois — cas non rencontré, ne pas l'anticiper.)

**Tranché depuis** (même séance, cf. §4.1) : désactiver ne cascade pas et introduit l'état `cancelled` ; changer la récurrence passe par la réconciliation de l'ensemble en attente, avec dialogue de confirmation et sans règle de correspondance devinée.

**Tranché à l'étape 2** : une occurrence dont ni le modèle ni le jour n'ont rien écrit **reste `pending`**. Elle ne reçoit aucun état de sortie — celui qui devait la remplir peut encore le faire, et si personne ne le fait, la fenêtre de validité la passe `expired` d'elle-même. La nommer `expired` tout de suite dirait « trop tard » d'une chose qui n'a jamais été écrite, et il n'y a pas besoin d'un cinquième état pour le vide.

**Aussi tranché à l'étape 2** : retirer la récurrence de la config suit le chemin du *changement* de récurrence, pas celui de la désactivation — plus rien n'étant attendu, tout ce qu'elle avait engendré est supprimé. Et l'interrupteur, remonté à la racine de la config sous le nom `enabled`, suspend le modèle entier — occurrences posées à la main comprises, récurrence ou pas.

### 4.5 Exécution = opération d'instance

L'unité exécutable est **l'instance** (la définition étant sa config, il n'y a rien d'autre à viser). Conséquences :

- **"Exécuter" = opération de service tooltype standard** : `messages.execute` avec `tool_instance_id` — pattern resource.operation existant, découvert via ToolTypeManager, zéro infrastructure core. Remplace `supportsExecutions()` : un tooltype « actif » expose `execute` + éventuellement `getScheduler()`.
- **Déclenchement manuel et planifié = même chemin de code**, `triggered_by` en simple paramètre.
- **Symétrie UI/IA gratuite** : l'IA crée l'instance (`tools.create`), l'exécute (`{tooltype}.execute`), lit les résultats (`TOOL_DATA`) — boucle d'orchestration complète sans commande nouvelle. La hiérarchie de validation s'applique à `execute` au niveau tool.
- **`templateDataId` disparaît** : le lien occurrence→définition est `toolInstanceId`, déjà natif sur chaque entrée.
- **Le scheduler change de boucle** : itérer les instances et lire leur config, puis (a) créer les occurrences manquantes dans la fenêtre `creation_horizon_days`, (b) envoyer celles dont l'heure est venue, (c) marquer `expired` celles qui ont dépassé `validity_window_minutes` et `cancelled` celles dont l'heure arrive alors que le modèle est désactivé, (d) supprimer celles qui ne correspondent plus à aucun créneau de la récurrence. Plus d'itération des entrées-templates. *(Amendé le 2026-09-17, cf. §4.4.)*
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
2. **Convertir les exécutions** : chaque ligne `tool_executions` → une entrée tool_data de la nouvelle instance correspondante (`templateDataId` fait le lien). Mapping : `executionTime` → timestamp ; `snapshotData.title/content/priority` → data ; `executionResult.read/archived/notification_sent` → data ; `triggeredBy`, `scheduledTime` → data ; `status: "sent"` sur toutes les entrées migrées (l'historique ne contient que des envois déjà partis) ; `common_title` non renseigné sur ces entrées, la notion n'existait pas avant l'amendement du 2026-09-17 — ne rien reconstituer. **Custom fields des snapshots : valeurs actuellement FORMATÉES (strings)** — les reconvertir en brut est impossible (information détruite par le formatage de déc 2025). Décision : les copier telles quelles dans `custom_fields` de l'entrée (strings), en acceptant l'impureté sur l'historique pré-refonte. Ne PAS construire de mécanisme de dé-formatage.
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

1. Refonte MessageToolType : nouveau schéma config (common_title/priority/schedule/horizon/fenêtre de validité), nouveau schéma data (occurrences, conditionnel à `status`), suppression schéma execution
2. MessageScheduler : lire config, créer les occurrences `pending` manquantes dans l'horizon via `tool_data.create`, envoyer celles dont l'heure est venue (copie de la part commune à ce moment-là), marquer `expired` les retardataires. Y trancher les deux points laissés ouverts en §4.4.
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
