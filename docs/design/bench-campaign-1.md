# Banc des modèles libres — campagne 1

Ce que la campagne 1 du banc (`docs/design/local-models.md`) a mesuré, et ce que chaque modèle fait quand il échoue. Jouée le 2026-10-04 sur l'émulateur, à travers OpenRouter : 5 modèles, chacun sans forçage puis au schéma exact, sur les 19 scénarios, 190 jeux en 5 h 15. Les copies de la base et les verdicts sont dans `tmp/bench/2026-10-04_1326/` (non versionné) ; `scripts/bench.py show <dossier d'un jeu>` y relit une conversation.

Chaque case est un seul jeu : la campagne ne dit pas ce qu'un modèle réussit une fois sur deux. Les jeux ont été rejugés après la correction de deux défauts du jugement (plus bas) ; aucun n'a été rejoué.

## Résultat

| | sans forçage | au schéma | coupés (sans / au schéma) | durée médiane d'un jeu | coût compté |
|---|---|---|---|---|---|
| `ministral-3b-2512` (téléphone) | 2/19 | 8/19 | 8 / 0 | 67 s / 20 s | 0,13 $ |
| `qwen3.6-35b-a3b` (portable 32 Go) | 8/19 | 9/19 | 9 / 8 | 165 s / 201 s | 0,49 $ |
| `gemma-4-31b-it` (GPU 24 Go) | 18/19 | 16/19 | 0 / 0 | 36 s / 49 s | 0,29 $ |
| `gpt-oss-120b` (serveur 64-128 Go) | 14/19 | 14/19 | 0 / 0 | 42 s / 47 s | 0,17 $ |
| `deepseek-v4-flash` (hébergé seulement) | 19/19 | 4/19 | 0 / 0 | 31 s / 89 s | 0,28 $ |

« Coupé » : une réponse du modèle a atteint la limite de longueur de sortie (16 000 tokens, le défaut de l'app, que le banc ne change pas). L'app n'en garde ni le texte ni les tokens : le coût des colonnes qui en ont est un minimum. Le tableau par scénario est dans `summary.md` du dossier de la campagne.

## Ce qui ne vient pas des modèles

Ces causes font échouer plusieurs modèles de la même façon ; elles sont dans l'app, son prompt ou le banc.

- **`period` refusé dans une lecture d'entrées.** Le prompt présente `period` comme paramètre de `TOOL_DATA` (avec un exemple), `CommandTransformer` le traite, mais la liste des paramètres acceptés (`AICommandProcessor.TOOL_DATA_PARAMS`) ne le contient pas : l'app répond « Paramètre 'period' inconnu ». 195 refus dans 54 jeux sur 190, dont 26 échoués ; tous les modèles le rencontrent. gemma, deepseek et gpt-oss passent alors à un filtre sur `timestamp` ; ministral renvoie la même requête jusqu'à 20 fois.
- **Une lecture de variable au début d'hier vaut 0.** Pour « calories d'hier », les modèles qui lisent `kcal_jour_demo` à la fin d'hier (`edge: END`) trouvent 902, ceux qui la lisent au début (`edge: START`) trouvent 0 et le disent : gemma sans forçage, gpt-oss sans forçage, qwen au schéma. La lecture calcule la journée qui se termine à cet instant, donc une journée vide au début d'hier. Le prompt donne un exemple en `END` sans dire pourquoi.
- **`"validation_request": false` refusé.** L'app refuse le champ dès qu'il est présent sans `action_commands`, même à `false` : 177 refus dans 34 jeux. 157 viennent de deepseek au schéma (ci-dessous) ; les autres modèles le corrigent au tour suivant.
- **Le schéma forcé n'a pas `completed`.** En automation, l'app demande au modèle de finir par le drapeau `completed` (« Si tu as terminé, utilise le flag 'completed' »), mais le schéma de réponse que le forçage impose (`AIMessageSchemas`, envoyé par `OpenAICompatibleProviderCore.responseSchemaForModel`) n'a pas ce champ et refuse tout champ de plus. Au schéma, gemma et gpt-oss finissent leurs automations, puis renvoient 10 à 13 fois un module de communication vide, l'app répétant la consigne à chaque fois, avant que la session se close ; sans forçage, ils écrivent `completed` et s'arrêtent. Le jugement ne regarde pas comment la session se termine : ces jeux comptent comme réussis.
- **Les réponses coupées** : 25 jeux, chez ministral sans forçage et qwen ; souvent au tour où le modèle doit calculer, juste après des données ou un message d'erreur.
- **Une erreur technique au lieu d'un outil inconnu** : une écriture dans un outil qui n'existe pas (`"demo-work‑*"`, gpt-oss au schéma, et deepseek au schéma) reçoit « Schema keyword 'error' at $ has no notation for the model » (`SchemaNotation`), qui ne dit pas que l'outil n'existe pas.
- **Un message d'exception brut** : « Erreur ToolInstanceService: Failed to parse custom field: No value for display_name », 8 fois dans un jeu de ministral, ne dit pas quel champ corriger.
- **Moins fréquent** :
  - Un filtre `between` écrit `{"constant": [début, fin]}` au lieu de la paire `[{"constant": début}, {"constant": fin}]` que donne le prompt : deepseek et gpt-oss, 5 refus dans 4 jeux. La forme du prompt, une paire hors de `constant`, est celle qu'ils ratent.
  - Un filtre sur `id` ou sur `state.running` refusé (« les entrées de cet outil n'ont pas ce champ à filtrer »), alors que le prompt présente les deux comme des champs à demander : 6 refus dans 5 jeux. gpt-oss cherchait ainsi le départ du chronomètre en cours, que le paramètre `running` donne.
  - Une date seule (`2026-10-04`) là où l'app attend une date et une heure : 2 refus.
  - Un module de communication vide (`{}` ou sans champ) est accepté : gemma termine ainsi plusieurs lectures après avoir répondu, et c'est la forme des boucles de fin d'automation au schéma.
- **Le banc**, deux défauts corrigés avant de rejuger : une note dont le texte est dans le nom de l'entrée (« Tomates cerises » : « commencent à rougir ») n'était pas lue ; une durée écrite « 3 heures 38 minutes » non plus. Cinq jeux passent d'échec à réussite. Reste fragile : le contrôle de « cette nuit » refuse une entrée datée après le début de la session, ce qui juge juste ici mais par accident.
- **Un silence de 5 minutes** : gemma au schéma, Point du matin, aucune réponse après la lecture des données, jusqu'au délai du banc. Cause non établie (fournisseur ou modèle).

## Ce que fait chaque modèle

Des heuristiques tirées de la relecture des conversations échouées, pas des mesures.

**ministral-3b.** Comprend la demande et le plan, ne tient pas la langue de commandes.
- Sans forçage : écrit son JSON entre des balises ```` ```json ```` (36 refus dans 15 jeux), met plusieurs types de commandes dans une réponse, et renvoie une requête refusée telle quelle au lieu de la corriger ; tourne jusqu'à la coupure (8 jeux) ou à la limite de tours.
- Au schéma : le forçage supprime les balises et le JSON invalide, d'où 8 réussites au lieu de 2, sur les saisies simples et les lectures par variable. Il mélange encore les types (21 refus) et répète `period` (47 refus). Échoue sur les dates relatives (le footing « de ce matin » daté de maintenant), les repas (aucune entrée), les automations (n'obtient jamais ses données), et toute configuration au-delà de changer une valeur.

**qwen3.6-35b.** Raisonne juste mais longtemps : médiane de 165 à 201 s par jeu, 17 de ses 38 jeux coupés à 16 000 tokens.
- Ses plans sont bons, il lit le schéma avant d'écrire et corrige ses champs. La plupart de ses échecs sont une coupure au tour du calcul ; son score dit peu tant que les coupures ne sont pas levées.
- Ailleurs : répond une fois en texte nu (« 70,1 kg », la bonne valeur, refusée faute de JSON) ; recrée la zone Lecture en corrigeant un outil, d'où deux zones ; demande l'heure de départ du footing « de ce matin » au lieu de la supposer ; bute sur la syntaxe d'un graphique par semaine.

**gemma-4-31b.** Le plus régulier aux deux niveaux.
- Lit les schémas, corrige une erreur en un tour en disant ce qu'il change (« le paramètre 'period' n'est pas supporté, je filtre moi-même »), regroupe ses commandes. Termine souvent par une question à l'utilisateur après avoir répondu, ce qui ne gêne pas le jugement.
- Ses échecs : la lecture au début d'hier (0 kcal) ; au schéma, des repas écrits sous le nom de l'aliment sans le lier à sa fiche d'Aliments ; un graphique « par semaine » qui trace chaque entrée sans les grouper par semaine ; le silence du Point du matin.

**gpt-oss-120b.** Rapide et le moins cher, juste sur la structure, faible sur le temps et les noms de champs.
- Date au moment présent ce qui est passé : « ce matin » et « cette nuit » enregistrés à l'heure de la session, 4 fois sur 4.
- Devine un nom de champ (`data.distance` au lieu de `data.value`), et, ne trouvant rien, demande à l'utilisateur de saisir les distances au lieu de relire le schéma.
- Cherche « lait » par nom exact, ne le trouve pas, crée un « lait » et le coche ; une fois, écrit un identifiant terminé par `????`.
- Pour la zone Lecture, crée la zone et s'arrête là (sans `keep_control`), ou demande quelle icône choisir.
- Au schéma, au Point du matin (réussi), écrit « Je ne dois plus créer ou modifier de données » dans la même réponse qu'une création d'entrée dans un outil qui n'existe pas.
- Son total d'heures facturables sans forçage (« environ 4 heures 19 ») passe au jugement, mais la conversation ne montre pas d'où il tient la durée en cours : il n'a jamais lu le départ du chronomètre.

**deepseek-v4-flash.** Le meilleur sans forçage, cassé par le forçage.
- Sans forçage, 19/19 : contourne `period`, lit la variable à la fin d'hier, trouve les fiches des aliments.
- Au schéma, 157 de ses 201 réponses sont exactement `pre_text` et `validation_request: false` : il annonce ce qu'il va faire (« Je vais lire la variable kcal_jour_demo… ») et s'arrête avant la commande. L'app refuse, il recommence à l'identique, 12 jeux finissent à la limite de tours. C'était déjà vu sur un scénario à la passe de rodage du 2026-10-01. Cause non vérifiée : que le forçage fasse écrire les clés dans l'ordre du schéma, où `validation_request` vient juste après `pre_text`.
- Au schéma aussi, des écritures que personne n'a demandées : au Point du matin, il coche la tâche « Rappeler Studio Brume » ; à la question des heures facturables, il joint un `UPDATE_DATA` à sa réponse (le bon total, 3 h 38), sans que rien ne change dans la base.

## Ce que la campagne ne dit pas

- La variance : un jeu par case. Une réussite ou un échec isolé peut ne pas se reproduire.
- La vitesse sur une machine à soi : les durées sont celles des hébergeurs d'OpenRouter, et leurs modèles peuvent être compressés autrement qu'en 4 bits.
- Ce que donnent qwen et ministral sans coupure.
- Ce que deviendraient les scores une fois `period` accepté : 26 échecs l'ont rencontré, mais d'autres causes s'y mêlent.
