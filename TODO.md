# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque.

## En cours

- DeepSeek fait précéder son JSON de texte (une phrase, ou `<thinking>…</thinking>`), malgré l'interdit du L1 : un appel perdu par `FORMAT_ERROR` à chaque fois (3 sur 9 réponses le 2026-09-28). Essayer dans le L1 « ta réponse commence par `{` », et un `FORMAT_ERROR` qui nomme le texte trouvé avant le `{` au lieu de « format invalide ».
- L'IA utilise dans une réponse un identifiant qu'une action de cette même réponse crée (`"zone_id": "PLACEHOLDER_ZONE"` avec le `CREATE_ZONE` qui précède) : les actions échouent et se refont au tour suivant. Dire dans le L1 qu'un identifiant créé n'est connu qu'au tour suivant.
- Deux messages `FORMAT_ERROR` écrits en dur en français dans `AIEventProcessor.parseAIResponse` (« Erreurs de format JSON : », « Erreur technique lors du parsing : ») : les passer au système de strings, comme celui de l'échec de lecture du JSON.
- `LogsScreen` a sa copie privée de `formatRelativeTime`, qui fait ce que fait `FormatUtils.formatRelativeTimePast` : passer par cette dernière et supprimer la copie.
- Conformité F-Droid (`docs/design/fdroid-compliance.md`) : reste la fiche fastlane et la grille d'anti-features, au moment de la première release candidate — pas avant, la codebase bouge.

## En attente d'un déclencheur

- Le type de champ RÉFÉRENCE (`docs/design/unified-fields.md`) — au premier outil qui désigne autre chose (Données structurées, Graphique, Calcul, Alerte, Objectif) ; le reste du système de champs est fait. La cible du pointeur devient alors une RÉFÉRENCE, `TOOL` y devenant `TOOL_INSTANCE`.
- Une entrée précise comme cible du pointeur (conçue dans `docs/design/pointer.md` : étiquette par type d'outil, liste dans le sélecteur, relecture de l'entrée) — si le besoin apparaît : peut-être superflu, un pointeur d'outil filtré couvre la plupart des cas. La cible APP attend que l'IA sache lire les réglages de l'app.

- Les exécutions d'une automation décrites comme des entrées (champs déclarés, schéma généré, dates en ISO par ce schéma) — le jour où l'IA les lit ; ce ne sont pas des réglages.
- Une sauvegarde emporte-t-elle la clé d'API d'un fournisseur (réglage secret) ? — à trancher avant d'ouvrir l'export à un usage partagé.
- Défilement saccadé à la réouverture d'une longue session CHAT — à mesurer (recompositions de la liste, défilements automatiques successifs).
- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Validation désactivée par défaut, que l'IA contourne donc sans rien demander (`docs/design/architecture-audit-debt.md`) — décision reportée le 2026-09-22.
- Outils jamais livrés : conception en cours dans `docs/design/missing-tools.md` (Liste livrée, Objectif spécifié le 2026-09-27, la lecture du cœur, le Calcul lu à la demande, les Données structurées et l'import du cœur le 2026-09-28, Graphique esquissé) ; restent le questionnaire, la spec de Calcul (points ouverts dans sa section), le sous-ensemble du Graphique, et Alerte, laissée pour plus tard (outil ou event du cœur, sans doute une lecture du cœur qui passe son test).
- Une automation sans IA : des commandes que l'app exécute elle-même, dont une écriture qui accepte une valeur lue à l'exécution (un Suivi qui garde un chiffre de Calcul, `docs/design/missing-tools.md`) ; à trancher avec : la date de l'entrée écrite, le doublon d'une exécution relancée. Au premier besoin de garder un chiffre dans le temps ; sans doute la brique des events du cœur (`NOTES.md`, « Events et badges »).
- L'IA crée et modifie des automations — aujourd'hui seul l'utilisateur le peut ; l'historique des exécutions est à trancher avec (`NOTES.md`, « Automations et planification »).
- L'écran d'une zone dessine toute tuile en LINE, quel que soit le mode d'affichage réglé (`ZoneScreen`) : la tuile d'une Liste en CONDENSED, EXTENDED, SQUARE ou FULL n'apparaîtra qu'avec la grille (`docs/design/grid-layout.md`).
- Le Suivi (tableau de l'historique) et Messages (cartes des envois) n'affichent pas les champs personnalisés de leurs entrées, qu'on ne voit qu'en modification — à trancher : où, et dans quelle disposition (`CustomFieldsDisplay`, `docs/DATA.md`).
- Les incarnations (`docs/design/incarnations.md`, identités de personae) — au premier export publié par personae.
- Supprimer un fournisseur d'IA ne regarde ni les automations ni les sessions qui le nomment (`AIProviderConfigService.deleteProviderConfig`) : refuser tant qu'une automation l'utilise, en la nommant ; une session passée reste lisible mais ne peut plus continuer, message à l'appui — avec les identités incarnées, qui demandent le même contrôle, écrit une fois pour les deux.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.

## Recette sur l'appareil

- `docs/design/device-checks.md`, classée par écran.
