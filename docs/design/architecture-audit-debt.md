# Dette constatée à l'audit d'architecture

Constats faits en passant pendant l'audit du 2026-06-10/11, classés par taxe estimée sur le projet. Rien ici n'est urgent : ce sont des choix à arbitrer, pas des pannes.

Ce qui restait à l'état de soupçon a été vérifié. Ce qui a été traité, ou s'est révélé sans objet, est sorti d'ici — les commits en sont le registre. La numérotation d'origine est conservée pour que les trous se lisent comme des points fermés.

## B.3 Doc IA dans le système de strings — discutable par nature

Le prompt L1 vit dans `ai_prompt_chunks.xml`, traité comme de l'i18n alors que c'est un **contrat d'interface** — le seul du projet ni compilé ni testé. Conséquences : échappement bruyant, diffs illisibles, et les divergences doc↔code du pipeline TOOL_DATA installées sans bruit.

**Recommandation** : le re-test manuel à chaque modif du L1 est désormais une règle de `docs/AI.md` — c'est le minimum, et il repose sur la discipline. À terme, envisager un format dédié (markdown source → génération) avec exemples extraits et exécutables automatiquement.

## B.8 Mention rapide

- `validateConfig`/`validateData` par défaut `false` (`ValidationResolver.kt:182`, `:209`) : l'IA modifie sans validation par défaut. Posture probablement délibérée — à re-choisir consciemment un jour, pas à subir comme un défaut hérité.
