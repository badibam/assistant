# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre.

## Chantier en cours

- **Alignement du pipeline TOOL_DATA** — la doc IA (`ai_prompt_chunks.xml`) est une interface que rien ne teste, et chaque divergence doc↔code produit un échec silencieux côté IA. Détail des sept points dans `docs/design/post-refactor-audit.md`, partie A. Le plus grave : l'objet `period: {start, end}` est documenté mais ignoré par le transformer, ce qui peut rendre tout l'historique là où l'IA croit lire un mois.
- **Conformité F-Droid** — spec dans `docs/design/fdroid-compliance.md`. Premier bloquant : `LICENSE.txt` est en CC BY-NC-SA, non libre au sens DFSG ; la décision actée est GPL-3.0.

## Dette constatée

Les huit points de `docs/design/post-refactor-audit.md`, partie B, chacun avec son statut (vérifié ou soupçon). Les deux à trancher en priorité :

- Vérifier si l'event sourcing existe réellement : `docs/DATA.md` l'annonce obligatoire, aucun event store n'a été trouvé dans les chemins d'écriture lus. Si c'est une aspiration, corriger la doc.
- Renommer un custom field détruit les valeurs historiques (`Removed + Added` → `STRIP_FIELD`). Migration rename-aware à écrire avant que ça morde sur des données réelles.

Et, du même audit : `strings_generated.xml` est un fichier généré, versionné dans le dépôt.

## Divers

- Compte des tokens avant envoi, via `/v1/messages/count_tokens` chez Claude — à voir pour les autres providers. `TokenCalculator` (171 lignes) et son bloc de strings existent déjà mais ne sont appelés de nulle part : soit ce chantier les reprend, soit ils partent.
- Filtrage des requêtes à opérateurs (`where`, `orderBy`, jointures) — spec dans `docs/design/query-parameters.md`, écrite en 2025 et jamais implémentée. Trois de ses idées ont été livrées autrement : `select` est devenu `fields`, `offset` est devenu `page`, les agrégations sont devenues l'opération `stats`. À reprendre ou à jeter, pas à appliquer telle quelle.
- Outils prévus par la vision produit mais jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste. C'est la chaîne de valeur annoncée par `README.md`, et donc le différenciateur non prouvé du projet.

## Sagesse

- Pull des canaux restants : `universel` (21 commits), `android` (8), `fdroid` (6). `dev_base` est conforme, sans dette.
- Revoir l'organisation des dossiers de `/mnt/data/OUTILS/assistant` : le dépôt est le sous-dossier `App/`, ce qui oblige le registry à pointer un sous-chemin. Décider si le dépôt remonte d'un cran.
