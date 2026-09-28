# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque.

## En cours

- Rejeu du prompt L1 (`docs/ai-prompt-replay.md`) : adapter sa grille aux changements du 2026-09-27 (`keep_control` posé dès qu'il reste à faire, modules de communication, en-tête des données jointes, valeurs par défaut dans les schémas, `ids` rendus par une création, écritures retenues tant que le schéma des entrées n'a pas été reçu, `TOOL_OPERATION` et les opérations lues avec ce schéma), puis le lancer sur l'appareil.

- Conformité F-Droid (`docs/design/fdroid-compliance.md`) : reste la fiche fastlane et la grille d'anti-features, au moment de la première release candidate — pas avant, la codebase bouge.

## En attente d'un déclencheur

- Le type de champ RÉFÉRENCE (`docs/design/unified-fields.md`) — au premier outil qui désigne autre chose (Données structurées, Graphique, Calcul, Alerte, Objectif) ; le reste du système de champs est fait.
- Une entrée précise comme cible du pointeur (conçue dans `docs/design/pointer.md` : étiquette par type d'outil, liste dans le sélecteur, relecture de l'entrée) — si le besoin apparaît : peut-être superflu, un pointeur d'outil filtré couvre la plupart des cas. La cible APP attend que l'IA sache lire les réglages de l'app.

- Les exécutions d'une automation décrites comme des entrées (champs déclarés, schéma généré, dates en ISO par ce schéma) — le jour où l'IA les lit ; ce ne sont pas des réglages.
- Une sauvegarde emporte-t-elle la clé d'API d'un fournisseur (réglage secret) ? — à trancher avant d'ouvrir l'export à un usage partagé.
- Défilement saccadé à la réouverture d'une longue session CHAT — à mesurer (recompositions de la liste, défilements automatiques successifs).
- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Validation désactivée par défaut, que l'IA contourne donc sans rien demander (`docs/design/architecture-audit-debt.md`) — décision reportée le 2026-09-22.
- Outils jamais livrés : conception en cours dans `docs/design/missing-tools.md` (Liste livrée, Calcul et Graphique esquissés, Objectif spécifié le 2026-09-27) ; restent Alerte (outil ou event du cœur), Données structurées, questionnaire, RÉFÉRENCE et le sous-ensemble du Graphique.
- L'IA crée et modifie des automations — aujourd'hui seul l'utilisateur le peut ; l'historique des exécutions est à trancher avec (`NOTES.md`, « Automations et planification »).
- L'écran d'une zone dessine toute tuile en LINE, quel que soit le mode d'affichage réglé (`ZoneScreen`) : la tuile d'une Liste en CONDENSED, EXTENDED, SQUARE ou FULL n'apparaîtra qu'avec la grille (`docs/design/grid-layout.md`).
- Le Suivi (tableau de l'historique) et Messages (cartes des envois) n'affichent pas les champs personnalisés de leurs entrées, qu'on ne voit qu'en modification — à trancher : où, et dans quelle disposition (`CustomFieldsDisplay`, `docs/DATA.md`).
- Les incarnations (`docs/design/incarnations.md`, identités de personae) — au premier export publié par personae.
- Supprimer un fournisseur d'IA ne regarde ni les automations ni les sessions qui le nomment (`AIProviderConfigService.deleteProviderConfig`) : refuser tant qu'une automation l'utilise, en la nommant ; une session passée reste lisible mais ne peut plus continuer, message à l'appui — avec les identités incarnées, qui demandent le même contrôle, écrit une fois pour les deux.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.

## Recette sur l'appareil

- `docs/design/device-checks.md`, classée par écran.
