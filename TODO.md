# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque.

## En cours

- Un seul système de champs : blocs A à C faits, rejeu du prompt L1 passé le 2026-09-25 ; reste `docs/design/unified-fields.md` — la fin du pointeur, la valeur par défaut portée par un champ, les détails des commandes dans le chat, RÉFÉRENCE. Le pointeur est fait pour une zone et un outil ; ce qui en reste est dans `docs/design/pointer.md`. À vérifier sur l'appareil : le nouveau sélecteur (chat et automation), la migration v44 sur une base qui a des pointeurs, l'historique d'un suivi par période, l'écran Messages, et le rejeu L1 (`docs/ai-prompt-replay.md`, étape 6).
- Le thème par défaut écrit encore des textes en dur, hors du système de textes : les boutons de ses dialogues (« Confirm », « Confirmer », « Annuler » selon le type, `DefaultTheme.kt`, dialogues et sélecteurs de date et d'heure) — les passer par `s.shared()`.

- Conformité F-Droid (`docs/design/fdroid-compliance.md`) : reste la fiche fastlane et la grille d'anti-features, au moment de la première release candidate — pas avant, la codebase bouge.

## En attente d'un déclencheur

- `ToolTypeContract.getAvailableOperations` n'a aucun appelant (le tracking y liste des opérations qui n'existent pas) — à supprimer avec ses quatre implémentations au prochain passage sur le contrat.

- Les exécutions d'une automation décrites comme des entrées (champs déclarés, schéma généré, dates en ISO par ce schéma) — le jour où l'IA les lit ; ce ne sont pas des réglages.
- Une sauvegarde emporte-t-elle la clé d'API d'un fournisseur (réglage secret) ? — à trancher avant d'ouvrir l'export à un usage partagé.
- Défilement saccadé à la réouverture d'une longue session CHAT — à mesurer (recompositions de la liste, défilements automatiques successifs).
- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Validation désactivée par défaut, que l'IA contourne donc sans rien demander (`docs/design/architecture-audit-debt.md`) — décision reportée le 2026-09-22.
- Outils prévus par la vision produit, jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste — quand tu décides de les concevoir.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.

## Recette sur l'appareil

- `docs/design/device-checks.md`, classée par écran.
