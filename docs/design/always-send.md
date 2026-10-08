# Les outils envoyés d'office — conception

Conçu le 2026-10-08. Un outil marqué « toujours envoyer » (`always_send`) a ses données dans chaque prompt, au Level 2 (`PromptManager.buildLevel2Content`), pour l'IA de l'app comme pour une IA extérieure (`app_context` du connecteur MCP). Depuis le 2025-11-02 (`f278f3cc`), le Level 2 sort toujours vide : `tools.list_all` ne rend plus la config des outils sans `include_config`, et `buildLevel2Commands` sautait en silence chaque outil sans config.

## La lecture des outils marqués

- `buildLevel2Commands` demande `tools.list_all` avec `include_config: true`. La config ne sert qu'à trouver les outils marqués : elle ne part pas à l'IA, seules leurs données (`TOOL_DATA`) partent.
- Une config absente ou illisible arrête la construction du prompt avec une erreur qui nomme l'outil, au lieu de le sauter. L'erreur arrête le tour (`AIEventProcessor.stopRoundOnError`), sans tuer l'app.
- Leurs schémas, dans une session, entrent dans la session comme tout schéma reçu : avant l'appel à l'IA, un message de schéma ordinaire (`CommandExecutor.schemasForAlwaysSent`) apporte ceux qu'elle n'a pas encore, une fois par session. Le Level 2 ne porte que leurs données, et les lectures et écritures de l'IA sur ces outils trouvent leur schéma dans la session, sans aller-retour. Hors session (le connecteur), `app_context` donne le schéma avec les données.

## Le seuil

- Un réglage propre (`always_send_max_chars`, ajouté aux réglages existants par la migration 63 → 64, `AILimitsAtV64`), dans Réglages › IA › Limites : la taille au-delà de laquelle les données envoyées d'office demandent une confirmation. 15 000 caractères par défaut, pour tous les types de session.
- Il compte le total du Level 2 (le texte de ses données tel que l'IA le reçoit), pas un outil à la fois.
- Il est distinct du seuil des données (`maxDataChars`), qui porte sur ce que l'IA demande dans une réponse : celui-ci protège des données face à l'IA, l'autre d'un coût qui grandit sans qu'on le voie, sur des données que l'utilisateur a choisi d'envoyer.

## En discussion, au-delà du seuil

- Avant l'appel à l'IA (phase `CALLING_AI`), si le Level 2 dépasse le seuil et que la session n'a pas encore de réponse à ce sujet, l'app ne l'appelle pas : elle range un message en attente (`ALWAYS_SEND_AWAITING_CONFIRMATION`, hors du prompt) et passe en `WAITING_DATA_CONFIRMATION`, la même attente que pour les données au-delà du seuil, avec la même carte et les mêmes boutons.
- La carte dit la taille totale, le seuil, la taille de chaque outil, et que **le choix vaut pour toute la session** : envoyés à chaque message, ou plus du tout, l'IA pouvant les lire à la demande ; une nouvelle session le redemandera.
- La réponse change le type du message en attente : `ALWAYS_SEND_ACCEPTED` ou `ALWAYS_SEND_REFUSED`, toujours hors du prompt. C'est lui que l'app relit pour savoir où en est la session (le dernier des deux) : pas de colonne, pas de migration. Puis l'appel à l'IA reprend.
- Accepté : le Level 2 part à chaque appel de la session, même s'il grandit encore.
- Refusé : le Level 2 dit à la place « outils marqués mais non envoyés : X (21 000 caractères), Y (2 400) — à lire à la demande » ; une lecture `TOOL_DATA` passe alors par le seuil des données, comme toute lecture.

## En automation, au-delà du seuil

Personne pour confirmer : le Level 2 dit les outils non envoyés, comme après un refus, et un message `DATA_REFUSED` dans l'historique de l'exécution le dit une fois par session, avec la taille et le seuil.

## Pour une IA extérieure

`app_context` n'a pas de session ni d'écran : au-delà du seuil, il dit les outils marqués et non envoyés avec leur taille, comme après un refus. La lecture qu'elle en fera ensuite est coupée au seuil des données (`AppMcpBackend.call`). Une vraie validation du connecteur est au `TODO.md`.

## Ce que garantissent les tests

- La décision (`PromptManager.alwaysSendOutcome`) : sous le seuil, envoyés quel que soit le type de session ; au-delà, une discussion sans choix demande, une discussion suit le choix fait, une automation et une IA extérieure reçoivent la liste.
- La migration 63 → 64 et l'import d'une sauvegarde : le seuil ajouté à 15 000, une valeur déjà là gardée.
- La lecture des outils marqués et l'arrêt sur une config illisible passent par la base : ils sont vérifiés sur l'appareil, la suite de tests n'ayant pas de base.

## Sur l'appareil

- Marquer un outil notes « toujours envoyer » : l'IA de l'app le connaît sans le lire ; `app_context` du connecteur le montre.
- Abaisser le seuil sous sa taille : au message suivant, la carte, sa phrase sur la session ; « Ne pas envoyer », puis un autre message : pas de nouvelle carte, l'IA dit ne pas avoir les données et peut les lire ; une nouvelle session redemande.
