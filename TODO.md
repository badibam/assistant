# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque.

## En cours

- Un seul système de champs : blocs A à C faits, rejeu du prompt L1 passé le 2026-09-25 ; reste `docs/design/unified-fields.md` — RÉFÉRENCE, qui attend le premier outil qui désigne autre chose (Données structurées, Graphique, Calcul, Alerte, Objectif). À vérifier sur l'appareil : le pointeur (chat et automation, zone et outil), les migrations v44 et v45 sur une base qui a des pointeurs, et le rejeu L1 (`docs/ai-prompt-replay.md`) après les changements de `keep_control` et des modules.

- Conformité F-Droid (`docs/design/fdroid-compliance.md`) : reste la fiche fastlane et la grille d'anti-features, au moment de la première release candidate — pas avant, la codebase bouge.

## En attente d'un déclencheur

- Une entrée précise comme cible du pointeur (conçue dans `docs/design/pointer.md` : étiquette par type d'outil, liste dans le sélecteur, relecture de l'entrée) — si le besoin apparaît : peut-être superflu, un pointeur d'outil filtré couvre la plupart des cas. La cible APP attend que l'IA sache lire les réglages de l'app.

- Les exécutions d'une automation décrites comme des entrées (champs déclarés, schéma généré, dates en ISO par ce schéma) — le jour où l'IA les lit ; ce ne sont pas des réglages.
- Une sauvegarde emporte-t-elle la clé d'API d'un fournisseur (réglage secret) ? — à trancher avant d'ouvrir l'export à un usage partagé.
- Défilement saccadé à la réouverture d'une longue session CHAT — à mesurer (recompositions de la liste, défilements automatiques successifs).
- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Validation désactivée par défaut, que l'IA contourne donc sans rien demander (`docs/design/architecture-audit-debt.md`) — décision reportée le 2026-09-22.
- Outils jamais livrés : conception en cours dans `docs/design/missing-tools.md` (tri fait le 2026-09-27).
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.

## Recette sur l'appareil

- `docs/design/device-checks.md`, classée par écran.
