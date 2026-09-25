# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque.

## En cours

- Coûts IA : vérifier la méthode de calcul pour chaque fournisseur (Claude, OpenAI). Constat : `updateSessionTokensAndCost` recalcule tout le coût de la session sur les tokens cumulés, au tarif du jour et du modèle configuré au moment du dernier appel — un changement de tarif ou de modèle en cours de session réécrit le coût des appels passés.
- `max_tokens` de Claude a trois valeurs par défaut : 2000 dans le commentaire (`ClaudeProviderCore.kt`, schéma de config), 8000 dans le schéma, 32000 dans le code quand le champ manque (`ClaudeExtensions.kt:42`). Choisir, en tenant compte du délai de lecture de 10 min sans streaming.
- Conformité F-Droid (`docs/design/fdroid-compliance.md`) : reste la fiche fastlane et la grille d'anti-features, au moment de la première release candidate — pas avant, la codebase bouge.

## En attente d'un déclencheur

- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Validation désactivée par défaut, que l'IA contourne donc sans rien demander (`docs/design/architecture-audit-debt.md`) — décision reportée le 2026-09-22.
- Outils prévus par la vision produit, jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste — quand tu décides de les concevoir.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.

## Recette sur l'appareil

- `docs/design/device-checks.md`, classée par écran.
