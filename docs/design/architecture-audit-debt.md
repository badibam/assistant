# Dette constatée à l'audit d'architecture

Constats faits en passant pendant l'audit du 2026-06-10/11, classés par taxe estimée sur le projet. Rien ici n'est urgent : ce sont des choix à arbitrer, pas des pannes.

Ce qui restait à l'état de soupçon a été vérifié. Ce qui a été traité, ou s'est révélé sans objet, est sorti d'ici — les commits en sont le registre. La numérotation d'origine est conservée pour que les trous se lisent comme des points fermés.

## B.3 Doc IA dans le système de strings — discutable par nature

Le prompt L1 vit dans `ai_prompt_chunks.xml`, traité comme de l'i18n alors que c'est un **contrat d'interface** — le seul du projet ni compilé ni testé. Conséquences : échappement bruyant, diffs illisibles, et les divergences doc↔code du pipeline TOOL_DATA installées sans bruit.

**Traité le 2026-09-22, en partie.** `scripts/check_prompt_examples.py` lit les 37 exemples JSON du prompt et les confronte au code : chaque exemple doit être du JSON, chaque type de champ nommé doit exister dans `FieldType`, et chaque clé déclarée par le schéma de base doit porter le type que l'IA reçoit — une chaîne ISO pour une propriété marquée `epoch-millis`, le type déclaré sinon. Il tourne avec `./run test`. Il a trouvé du premier coup que le prompt enseignait encore `TEXT_UNLIMITED`, retiré par la migration v25→v26.

Ce qu'il ne couvre pas : les dix blocs en pseudo-code (`{ name?, timestamp?, data }`), qui décrivent une forme en prose et n'ont rien à analyser, et la prose elle-même. Le re-test manuel du L1 à chaque modif reste la règle de `docs/AI.md`. Le format dédié (markdown source → génération) reste une option de confort : il n'attraperait rien de plus par lui-même.

## B.8 Mention rapide

- `validateConfig`/`validateData` par défaut `false` (`ValidationResolver.kt:182`, `:209`) : l'IA modifie sans validation par défaut. Re-choisi le 2026-10-09 : désactivé par défaut, on protège ce qui est sensible (`docs/design/validation.md`).
