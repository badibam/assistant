# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre.

## Chantier en cours

- **Alignement du pipeline TOOL_DATA** — la doc IA (`ai_prompt_chunks.xml`) est une interface que rien ne teste, et chaque divergence doc↔code produit un échec silencieux côté IA. Détail des sept points dans `docs/design/post-refactor-audit.md`, partie A. Le plus grave : l'objet `period: {start, end}` est documenté mais ignoré par le transformer, ce qui peut rendre tout l'historique là où l'IA croit lire un mois.
- **Conformité F-Droid** — spec dans `docs/design/fdroid-compliance.md`. Premier bloquant : la tâche `generateThemeResources` appelle `npx` à chaque build.
- **Exécutions d'automation manquées** — une automation rattrape sans limite chaque exécution manquée, et résout ses dates relatives sur l'heure actuelle au lieu de l'heure prévue. Spec dans `docs/design/automation-missed-executions.md`, prête à implémenter.

## Dette constatée

- Une erreur de l'API IA qui ne contient pas « provider » ou « configured » (429, 529, crédit épuisé, délai dépassé) est classée erreur réseau : une automation réessaie alors toutes les 30 s sans fin, prompt complet à chaque fois (`AIEventProcessor.kt:599`, `:648`, `:972`). Pas la cause de l'incident du 2026-09-18, mais le même genre de facture.

Les huit points de `docs/design/post-refactor-audit.md`, partie B, chacun avec son statut (vérifié ou soupçon). Les deux à trancher en priorité :

- Vérifier si l'event sourcing existe réellement : `docs/DATA.md` l'annonce obligatoire, aucun event store n'a été trouvé dans les chemins d'écriture lus. Si c'est une aspiration, corriger la doc.
- Renommer un custom field détruit les valeurs historiques (`Removed + Added` → `STRIP_FIELD`). Migration rename-aware à écrire avant que ça morde sur des données réelles.

Et, du même audit : `strings_generated.xml` est un fichier généré, versionné dans le dépôt.

## Divers

- Compte des tokens avant envoi, via `/v1/messages/count_tokens` chez Claude — à voir pour les autres providers. `TokenCalculator` (171 lignes) et son bloc de strings existent déjà mais ne sont appelés de nulle part : soit ce chantier les reprend, soit ils partent.
- Outils prévus par la vision produit mais jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste.

## Sagesse

- Pull des canaux restants : `universel` (21 commits), `android` (8), `fdroid` (6). `dev_base` est conforme, sans dette.
- Revoir l'organisation des dossiers de `/mnt/data/OUTILS/assistant` : le dépôt est le sous-dossier `App/`, ce qui oblige le registry à pointer un sous-chemin. Décider si le dépôt remonte d'un cran.
