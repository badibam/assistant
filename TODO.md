# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque.

## En cours

- Un seul système de champs (`docs/design/unified-fields.md`) : blocs A et B faits, bloc C commencé (dates et durées en ISO, `state` et chronomètre pour l'IA). Prochain : les configs en champs (`docs/design/config-fields.md`) — blocs A et B faits, bloc C (l'écran généré et la migration déduite) ensuite —, puis la fin du bloc C des champs.

- Conformité F-Droid (`docs/design/fdroid-compliance.md`) : reste la fiche fastlane et la grille d'anti-features, au moment de la première release candidate — pas avant, la codebase bouge.

## En attente d'un déclencheur

- `ToolTypeContract.getAvailableOperations` n'a aucun appelant (le tracking y liste des opérations qui n'existent pas) — à supprimer avec ses quatre implémentations au prochain passage sur le contrat.

- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Validation désactivée par défaut, que l'IA contourne donc sans rien demander (`docs/design/architecture-audit-debt.md`) — décision reportée le 2026-09-22.
- Outils prévus par la vision produit, jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste — quand tu décides de les concevoir.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.

## Recette sur l'appareil

- `docs/design/device-checks.md`, classée par écran.
