# La validation et les accès de l'IA — conception

Conçu le 2026-10-09, depuis une note du téléphone (« gérer la question de la validation, dans la config d'outil et sur tous les écrans »). Remplace le modèle actuel, où cinq interrupteurs de l'app, deux par outil, un par session et la demande de l'IA s'additionnent, et où rien ne vaut hors de la conversation.

## Ce qui ne va pas aujourd'hui

- La validation ne joue que dans la conversation : une automation écrit sans jamais rien demander (`AIStateMachine`, « Execute directly without validation »), une IA extérieure par le connecteur MCP non plus.
- Les interrupteurs de l'app valent pour toute l'app (« valider toutes les données ») : un levier que personne n'active, sans quoi chaque écriture demanderait un accord.
- « Valider les changements de config de l'app » ne sert jamais : aucune action de l'IA n'atteint les réglages de l'app (`UPDATE_APP_CONFIG` est attendu par `ValidationResolver`, inconnu d'`AICommandProcessor`).
- Les libellés disent « par IA » sans dire ce qu'ils couvrent, ni qu'une automation y échappe.

## Deux notions

- **La validation** : l'IA demande ton accord avant une écriture, au moment où elle la fait. Elle vaut quand quelqu'un est là pour répondre : la conversation de l'app, une IA extérieure par le connecteur.
- **Le masque d'accès** : ce qu'une IA qui agit sans toi a le droit de toucher, posé d'avance. Une automation, un client du connecteur. Ce qui est hors du masque est refusé, sans rien demander.

Rien n'est validé ni masqué par défaut : on protège ce qui est sensible.

## La validation : trois niveaux, chacun garde ce qu'il contient

| Niveau | Interrupteur | L'IA demande ton accord avant de… |
|---|---|---|
| App | Réglages › Validation | créer, modifier ou supprimer une zone ; changer les groupes de l'accueil ; changer un réglage de l'app ouvert à l'IA |
| Zone | config de la zone | créer, modifier (config comprise : champs, réglages) ou supprimer un outil de la zone ; changer ses groupes d'outils, ses automations, ses variables |
| Outil | config de l'outil | créer, modifier ou supprimer une entrée de l'outil |

- Un seul interrupteur par niveau, désactivé par défaut. Chacun ne garde que ce que son niveau contient directement : protéger une zone ne protège pas les données de ses outils.
- La config d'un outil est gardée par sa zone : la modifier change la structure de la zone, comme y créer un outil.
- La décision suit l'objet touché : une écriture est validée si le niveau qui le contient est protégé.

### Dans une conversation

- « Demander aussi mon accord pour » : trois cases, « L'accueil et les zones », « Le contenu des zones », « Les données des outils », chacune étendant son niveau à tous ses objets pour cette conversation. En petit dessous : « S'ajoute aux protections déjà réglées. »
- La demande de l'IA (`validation_request`) reste : l'IA peut demander d'elle-même avant d'agir. Corriger au passage le refus de `"validation_request": false` dans une réponse sans action (`docs/design/bench-campaign-1.md`).

### Par le connecteur MCP

- Une écriture qui demande validation attend ton accord par une notification qui la décrit (`ActionVerbalizerHelper` : « Ajouter 2 entrées dans Poids »), avec Accepter et Refuser, touchables sans quitter l'app où l'on parle à l'IA.
- La requête attend au plus ~90 s, sous la coupure de 120 s de l'hébergement du relais ; sans réponse, elle renvoie « pas d'accord à temps », et l'IA peut redemander.

### Pas pour les automations

Une automation fait ce que tu lui as écrit : elle ne demande rien. Elle est gardée en amont, sa création et sa modification passant par la validation de sa zone, et bornée par son masque.

## Le masque d'accès

- Une liste facultative de zones et d'outils, chacun avec un niveau. Vide : tout est permis (le défaut, rien ne change pour l'existant). Remplie : tout ce qui n'y figure pas est interdit, créer une zone ou changer les groupes de l'accueil compris.

| Niveau | Sur une zone | Sur un outil |
|---|---|---|
| Lecture | la zone, ses outils, leurs données, ses variables | l'outil et ses données |
| Utilisation | lecture + créer, modifier, supprimer ses outils et ses variables | lecture + écrire ses données |
| Complète | utilisation + la config de la zone elle-même (nom, icône, groupes d'outils, suppression) | utilisation + la config de l'outil (champs, réglages, suppression) |

- Un outil a le plus haut de son propre niveau et de celui que lui donne sa zone ; une zone en utilisation ou en complète donne « complète » à ses outils (qui peut les supprimer et les recréer peut les modifier).
- Vérifié à l'exécution de chaque commande, lecture comme écriture ; un refus part à l'IA avec la raison et se lit dans l'historique d'exécution. Le prompt dit le masque à l'IA, qu'elle n'essaie pas en vain.
- Choisi avec le sélecteur de choses existant (celui du pointeur).
- Pour une automation : dans sa config. Pour un client du connecteur : dans la liste des accès autorisés de l'écran « Accès externe ».

## Ce qui change dans les données

- `validation_config` : cinq clés → une (`validate_app`) ; migration : vrai si `validate_app_config_changes` ou `validate_zone_config_changes` l'était.
- Zone : une colonne `validate` (migration : vraie si un de ses outils avait `validate_config`).
- Outil : `validate_config` sort de la config commune, `validate_data` reste et devient l'interrupteur de l'outil.
- Session : `requireValidation` → trois booléens (migration : vrai → les trois).
- Automation et client MCP : un masque, liste de `{kind, id, level}`, vide par défaut.
- `UPDATE_APP_CONFIG` : l'action que l'IA n'a pas encore, limitée à une liste de catégories ouvertes (d'abord `main_screen`, jamais une catégorie à secret ni l'apparence), avec sa commande de lecture `APP_CONFIG`.

## Les textes de l'interface (brouillon, à relire ensemble)

- App, Réglages › Validation : « Protéger l'accueil » — « L'IA de la conversation et du connecteur demande votre accord avant de créer, modifier ou supprimer une zone, ou de changer les groupes de l'accueil. Les automations ne demandent pas : leur accès se règle dans chacune. »
- Zone : « Protéger le contenu de cette zone » — « L'IA demande votre accord avant de créer, modifier ou supprimer un outil de cette zone, ses groupes d'outils, ses automations ou ses variables. »
- Outil : « Protéger les données de cet outil » — « L'IA demande votre accord avant d'ajouter, modifier ou supprimer une entrée de cet outil. »
- Masque : « Accès de cette automation » — « Vide : elle peut tout faire. Sinon, elle ne touche qu'à ce qui est listé, au niveau choisi. »

## Ordre de réalisation

1. Le modèle à trois niveaux dans `ValidationResolver`, les migrations, les interrupteurs et leurs textes ; les trois cases de session ; la correction de `validation_request`.
2. `UPDATE_APP_CONFIG` et `APP_CONFIG` pour `main_screen` (la question du téléphone sur les groupes de l'accueil).
3. Le masque d'accès des automations.
4. Le connecteur : la validation par notification, puis le masque par client.

## Ouvert

- Le retrait du réglage « Gestion » des outils (`TODO.md`) touche la même config commune : à faire dans la même migration que `validate_config`.
